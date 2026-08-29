package online.vyybandasky.plus365.core.book

import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.domain.Account
import online.vyybandasky.plus365.core.domain.AccountId
import online.vyybandasky.plus365.core.domain.ConfirmSource
import online.vyybandasky.plus365.core.domain.Entry
import online.vyybandasky.plus365.core.domain.EntryId
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.domain.InterestPeriod
import online.vyybandasky.plus365.core.domain.Loan
import online.vyybandasky.plus365.core.domain.LoanDirection
import online.vyybandasky.plus365.core.domain.LoanId
import online.vyybandasky.plus365.core.domain.Member
import online.vyybandasky.plus365.core.domain.Pocket
import online.vyybandasky.plus365.core.domain.PocketId
import online.vyybandasky.plus365.core.domain.MemberId
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.governance.Decision
import online.vyybandasky.plus365.core.governance.Refusal
import online.vyybandasky.plus365.core.governance.asConfirmedBy
import online.vyybandasky.plus365.core.governance.asRejectedBy
import online.vyybandasky.plus365.core.governance.checkConfirm
import online.vyybandasky.plus365.core.governance.checkRecord
import online.vyybandasky.plus365.core.governance.checkReject
import online.vyybandasky.plus365.core.domain.ChargeKind
import online.vyybandasky.plus365.core.domain.MemberKind
import online.vyybandasky.plus365.core.interest.interestCents
import online.vyybandasky.plus365.core.interest.rateFor
import online.vyybandasky.plus365.core.ledger.LedgerState
import online.vyybandasky.plus365.core.ledger.fold
import online.vyybandasky.plus365.core.sms.Assurance
import online.vyybandasky.plus365.core.sms.MatchResult
import online.vyybandasky.plus365.core.sms.SmsEvidence
import online.vyybandasky.plus365.core.sms.explain
import online.vyybandasky.plus365.core.sms.matchEvidence

/**
 * The book: members, loans, and the append-only entry log, plus the only two
 * doors that change it — [record] and [confirm].
 *
 * Immutable. Every operation returns a new book, so the UI holds one value and
 * replaces it, and there is no way to mutate an entry from outside this file.
 *
 * Pure: no I/O, no clock, no randomness. Ids and dates are passed in by the
 * caller, which is what makes the whole thing testable and what will let a real
 * store slot underneath it later without touching any of this logic.
 */
data class LedgerBook(
    val members: List<Member> = emptyList(),
    /** Where the pool's cash sits. Cash-at-hand is their sum. */
    val accounts: List<Account> = emptyList(),
    /**
     * What the cash is earmarked for. Sums to cash-at-hand as well — the same
     * money seen from the other side.
     */
    val pockets: List<Pocket> = emptyList(),
    val loans: List<Loan> = emptyList(),
    val entries: List<Entry> = emptyList(),
    val nextSeq: Long = 1L,
) {
    /** Balances. Derived on demand, never stored — see [fold]. */
    fun state(): LedgerState = fold(entries, loans)

    fun member(id: MemberId): Member? = members.firstOrNull { it.id == id }

    fun memberIds(): List<MemberId> = members.map { it.id }

    /** The three. Everyone who governs the pool. */
    fun founders(): List<Member> = members.filter { it.isFounder }

    /**
     * Whose ids may appear anywhere governance is decided.
     *
     * A Keshflo beneficiary borrows and nothing else. They never confirm, never
     * reject, never settle — every one of those is a say in the members' money,
     * and an outside borrower has no stake to back it.
     */
    fun founderIds(): List<MemberId> = founders().map { it.id }

    /** People Keshflo lends to. Not members of the pool. */
    fun beneficiaries(): List<Member> = members.filter { it.isBeneficiary }

    fun isFounder(id: MemberId): Boolean = member(id)?.isFounder == true

    fun entry(id: EntryId): Entry? = entries.firstOrNull { it.id == id }

    fun loan(id: LoanId): Loan? = loans.firstOrNull { it.id == id }

    /** Everything waiting on a second pair of eyes, oldest first. */
    fun pending(): List<Entry> =
        entries.filter { it.state == EntryState.PENDING }.sortedBy { it.seq ?: Long.MAX_VALUE }

    /** The confirmed log, newest first — what the history screen shows. */
    fun confirmed(): List<Entry> =
        entries.filter { it.state == EntryState.CONFIRMED }
            .sortedByDescending { it.seq ?: Long.MIN_VALUE }

    fun displayName(id: MemberId): String = member(id)?.displayName ?: id

    fun account(id: AccountId): Account? = accounts.firstOrNull { it.id == id }

    fun accountLabel(id: AccountId): String = account(id)?.label ?: id

    /** The default account — the first one declared. */
    fun defaultAccount(): AccountId? = accounts.firstOrNull()?.id

    /** The default earmark — the first pocket declared. */
    fun defaultPocket(): PocketId? = pockets.firstOrNull()?.id

    /** Entries recorded as one act, in ledger order. */
    fun group(groupId: String): List<Entry> =
        entries.filter { it.groupId == groupId }.sortedBy { it.seq ?: Long.MAX_VALUE }

    /** Entries thrown out. Kept, never deleted, and ignored by the fold. */
    fun rejected(): List<Entry> =
        entries.filter { it.state == EntryState.DISPUTED }
            .sortedByDescending { it.seq ?: Long.MIN_VALUE }

    /**
     * Pending work grouped into the acts a person actually decides on.
     *
     * A loan is three entries but one decision, so the confirm screen should ask
     * once. Entries with no group stand alone.
     */
    fun pendingActs(): List<List<Entry>> {
        val standalone = pending().filter { it.groupId == null }.map { listOf(it) }
        val grouped = pending().filter { it.groupId != null }
            .groupBy { it.groupId!! }
            .map { (_, es) -> es.sortedBy { it.seq ?: Long.MAX_VALUE } }
        return (standalone + grouped).sortedBy { it.first().seq ?: Long.MAX_VALUE }
    }
}

/** What a successful change produced. */
data class Recorded(val book: LedgerBook, val entries: List<Entry>) {
    val entry: Entry get() = entries.first()
}

/**
 * Record a money-moving entry.
 *
 * It lands [EntryState.PENDING] — never confirmed, never counted toward a
 * balance — because a single person cannot put money on the books alone. That is
 * the same rule as [confirm], seen from the other end.
 */
/** The entry types that move somebody's share of the pool up or down. */
private val STAKE_TYPES = setOf(EntryType.CONTRIBUTION, EntryType.PAYOUT)

fun LedgerBook.record(
    id: EntryId,
    type: EntryType,
    amountCents: Long,
    memberId: MemberId,
    recordedBy: MemberId,
    config: ActorConfig,
    loanId: LoanId? = null,
    accountId: AccountId? = null,
    pocketId: PocketId? = null,
    groupId: String? = null,
    note: String? = null,
    at: Instant? = null,
    evidence: SmsEvidence? = null,
    chargeKind: ChargeKind? = null,
): Decision<Recorded> {
    when (val gate = checkRecord(recordedBy, config)) {
        is Decision.Refused -> return gate
        is Decision.Allowed -> Unit
    }
    if (member(memberId) == null) {
        return Decision.Refused(Refusal.UnknownMember(memberId))
    }
    if (member(recordedBy) == null) {
        return Decision.Refused(Refusal.UnknownMember(recordedBy))
    }
    if (!isFounder(recordedBy)) {
        return Decision.Refused(Refusal.NotAMember(recordedBy))
    }
    // Who may act is one question; who an entry may be *about* is another. A
    // Keshflo borrower has no share of the pool, so there is nothing to pay into
    // and nothing to pay out. The phone hid this by offering only founders in
    // the picker — but a rule enforced by which buttons get drawn holds only
    // until somebody builds a second screen.
    if (type in STAKE_TYPES && !isFounder(memberId)) {
        return Decision.Refused(Refusal.NoStake(memberId))
    }
    if (amountCents <= 0L) {
        return Decision.Refused(Refusal.Invalid("Amount must be more than zero."))
    }
    if (type == EntryType.REVERSAL) {
        return Decision.Refused(Refusal.Invalid("Use reverse() to cancel an entry."))
    }
    if (entry(id) != null) {
        // Idempotency: the same id twice is the same fact, not a second one.
        return Decision.Allowed(Recorded(this, listOf(entry(id)!!)))
    }
    if (evidence != null && evidence.pastedBy != recordedBy) {
        return Decision.Refused(
            Refusal.BadEvidence("The message must be the one the recorder received."),
        )
    }
    // One transaction, one entry. A code already on the books cannot be reused
    // to back a second entry — otherwise one real transfer could justify any
    // number of them.
    entries.firstOrNull { it.recordedEvidence?.reference.equals(evidence?.reference, ignoreCase = true) && evidence != null }
        ?.let { existing ->
            return Decision.Refused(
                Refusal.BadEvidence(
                    "Code ${evidence!!.reference} is already on the books against another entry.",
                ),
            )
        }

    val appended = Entry(
        id = id,
        seq = nextSeq,
        type = type,
        amountCents = amountCents,
        memberId = memberId,
        loanId = loanId,
        accountId = accountId ?: defaultAccount(),
        pocketId = pocketId ?: defaultPocket(),
        groupId = groupId,
        recordedByMemberId = recordedBy,
        recordedAt = at,
        state = EntryState.PENDING,
        note = note,
        recordedEvidence = evidence,
        chargeKind = chargeKind,
    )
    return Decision.Allowed(
        Recorded(
            copy(entries = entries + appended, nextSeq = nextSeq + 1),
            listOf(appended),
        ),
    )
}

/**
 * Confirm every entry recorded as one act.
 *
 * A loan is three facts — principal, interest, transaction cost — but one
 * decision. This clears them together while still writing a separate
 * confirmation on each, so the log stays honest about what was agreed and by
 * whom. The rule is checked per entry; if any is refused, none are applied.
 */
fun LedgerBook.confirmGroup(
    groupId: String,
    confirmedBy: MemberId,
    config: ActorConfig,
    source: ConfirmSource = ConfirmSource.HUMAN,
    at: Instant? = null,
    evidence: SmsEvidence? = null,
): Decision<Recorded> {
    val members = group(groupId).filter { it.state == EntryState.PENDING }
    if (members.isEmpty()) {
        return Decision.Refused(Refusal.Invalid("Nothing pending in group $groupId."))
    }
    var book = this
    val confirmed = mutableListOf<Entry>()
    for (e in members) {
        // Only the leg that was recorded with a message needs one to clear it.
        // The interest and cost legs are consequences of the same act, not
        // separate transfers, and no SMS exists for them.
        val forThisLeg = if (e.recordedEvidence != null) evidence else null
        when (val step = book.confirm(e.id, confirmedBy, config, source, at, forThisLeg)) {
            is Decision.Refused -> return step
            is Decision.Allowed -> {
                book = step.value.book
                confirmed += step.value.entries
            }
        }
    }
    return Decision.Allowed(Recorded(book, confirmed))
}

/**
 * Move cash between the pool's own pockets.
 *
 * Cash-at-hand cannot change, only its split. Still pending, still needs a
 * second person — moving the float is exactly the kind of thing worth two sets
 * of eyes.
 */
fun LedgerBook.transfer(
    id: EntryId,
    fromAccount: AccountId,
    toAccount: AccountId,
    amountCents: Long,
    recordedBy: MemberId,
    config: ActorConfig,
    note: String? = null,
    at: Instant? = null,
    evidence: SmsEvidence? = null,
    chargeKind: ChargeKind? = null,
): Decision<Recorded> {
    when (val gate = checkRecord(recordedBy, config)) {
        is Decision.Refused -> return gate
        is Decision.Allowed -> Unit
    }
    if (account(fromAccount) == null || account(toAccount) == null) {
        return Decision.Refused(Refusal.Invalid("Unknown account."))
    }
    if (fromAccount == toAccount) {
        return Decision.Refused(Refusal.Invalid("A transfer needs two different accounts."))
    }
    if (amountCents <= 0L) {
        return Decision.Refused(Refusal.Invalid("Amount must be more than zero."))
    }

    val appended = Entry(
        id = id,
        seq = nextSeq,
        type = EntryType.TRANSFER,
        amountCents = amountCents,
        memberId = recordedBy,
        accountId = toAccount,
        counterAccountId = fromAccount,
        recordedByMemberId = recordedBy,
        recordedAt = at,
        state = EntryState.PENDING,
        note = note,
        recordedEvidence = evidence,
        chargeKind = chargeKind,
    )
    return Decision.Allowed(
        Recorded(copy(entries = entries + appended, nextSeq = nextSeq + 1), listOf(appended)),
    )
}

/**
 * Confirm a pending entry, moving it onto the balances.
 *
 * The single door. [checkConfirm] is the gate, and the confirmed copy of an
 * entry cannot be built anywhere else — `asConfirmedBy` is internal to the core
 * module and only called here.
 */
fun LedgerBook.confirm(
    entryId: EntryId,
    confirmedBy: MemberId,
    config: ActorConfig,
    source: ConfirmSource = ConfirmSource.HUMAN,
    at: Instant? = null,
    evidence: SmsEvidence? = null,
): Decision<Recorded> {
    val target = entry(entryId)
        ?: return Decision.Refused(Refusal.UnknownEntry(entryId))
    if (member(confirmedBy) == null) {
        return Decision.Refused(Refusal.UnknownMember(confirmedBy))
    }
    if (!isFounder(confirmedBy)) {
        return Decision.Refused(Refusal.NotAMember(confirmedBy))
    }
    when (val gate = checkConfirm(target, confirmedBy, config)) {
        is Decision.Refused -> return gate
        is Decision.Allowed -> Unit
    }

    // Where the recorder produced a message, the confirmer must produce their
    // own, and the two must describe one transaction. This is what makes the
    // control structural rather than procedural: a second member cannot wave an
    // entry through, because they have nothing to wave it through with.
    val recorded = target.recordedEvidence
    val assurance: Assurance
    if (recorded != null) {
        if (evidence == null) {
            return Decision.Refused(
                Refusal.BadEvidence(
                    "This entry was recorded with a transaction message, so confirming " +
                        "it needs yours for the same transaction.",
                ),
            )
        }
        if (evidence.pastedBy != confirmedBy) {
            return Decision.Refused(
                Refusal.BadEvidence("The message must be the one the confirmer received."),
            )
        }
        when (val m = matchEvidence(recorded, evidence)) {
            is MatchResult.Mismatch ->
                return Decision.Refused(
                    Refusal.EvidenceMismatch(m.reasons.map { it.explain(recorded, evidence) }),
                )
            MatchResult.Matched -> Unit
        }
        assurance = Assurance.CODE_MATCHED
    } else {
        // No message on either side: a cash handover, or a transaction where
        // only one party is texted. Still two people, but a person's word rather
        // than the network's receipt — and it says so wherever it is shown.
        assurance = Assurance.ATTESTED
    }

    val confirmed = target.asConfirmedBy(confirmedBy, source, at, evidence, assurance)
    return Decision.Allowed(
        Recorded(
            copy(entries = entries.map { if (it.id == entryId) confirmed else it }),
            listOf(confirmed),
        ),
    )
}

/**
 * Disburse a loan from the pool to a member at the house rate.
 *
 * Three entries, not one. The pool has always kept a loan in three parts —
 * principal, interest, and the M-Pesa cost of moving it — and burying the cost
 * inside the principal would put our running total a few shillings away from
 * theirs with no way to tell which was right.
 *
 * Cash leaves the pool for the principal and the transaction cost. It does not
 * leave for the interest: that is owed by the borrower, never money the pool
 * held. The borrower owes all three.
 *
 * They share a [groupId] so one decision can clear all three, while each still
 * records its own confirmation.
 */
fun LedgerBook.disburseLoan(
    loanId: LoanId,
    borrower: MemberId,
    principalCents: Long,
    recordedBy: MemberId,
    config: ActorConfig,
    mpesaChargeCents: Long = 0L,
    bankChargeCents: Long = 0L,
    rateBps: Int? = null,
    fromAccount: AccountId? = null,
    note: String? = null,
    at: Instant? = null,
    evidence: SmsEvidence? = null,
): Decision<Recorded> {
    if (loan(loanId) != null) {
        return Decision.Refused(Refusal.Invalid("Loan $loanId already exists."))
    }
    if (mpesaChargeCents < 0L || bankChargeCents < 0L) {
        return Decision.Refused(Refusal.Invalid("A transaction cost cannot be negative."))
    }
    val borrowerKind = member(borrower)?.kind
        ?: return Decision.Refused(Refusal.UnknownMember(borrower))

    // The rate follows who is borrowing, not who is recording. A founder borrows
    // their own pool at the founder rate; Keshflo lends outward at the other.
    val rate = rateBps ?: rateFor(borrowerKind)
    val interest = interestCents(principalCents, rate)
    val account = fromAccount ?: defaultAccount()

    val loan = Loan(
        id = loanId,
        direction = LoanDirection.POOL_TO_MEMBER,
        counterpartyMemberId = borrower,
        principalCents = principalCents,
        mpesaChargeCents = mpesaChargeCents,
        bankChargeCents = bankChargeCents,
        rateBps = rate,
        borrowerKind = borrowerKind,
        period = InterestPeriod.MONTHLY,
    )

    // Each leg of the loan, in the order the money is thought about.
    val legs = buildList {
        add(LoanLeg(EntryType.LOAN_OUT, principalCents, note, null))
        if (interest > 0L) {
            add(LoanLeg(EntryType.INTEREST_ACCRUAL, interest, "Interest at ${rate / 100.0}% flat", null))
        }
        if (mpesaChargeCents > 0L) {
            add(LoanLeg(EntryType.TXN_COST, mpesaChargeCents, "M-Pesa charge", ChargeKind.MPESA))
        }
        if (bankChargeCents > 0L) {
            add(LoanLeg(EntryType.TXN_COST, bankChargeCents, "Bank charge", ChargeKind.BANK))
        }
    }

    var book = copy(loans = loans + loan)
    val appended = mutableListOf<Entry>()
    for ((type, amount, legNote, charge) in legs) {
        val suffix = when {
            type == EntryType.LOAN_OUT -> "principal"
            type == EntryType.INTEREST_ACCRUAL -> "interest"
            charge == ChargeKind.BANK -> "bankcharge"
            else -> "mpesacharge"
        }
        when (
            val step = book.record(
                id = "$loanId-$suffix",
                type = type,
                amountCents = amount,
                memberId = borrower,
                recordedBy = recordedBy,
                config = config,
                loanId = loanId,
                accountId = account,
                groupId = loanId,
                note = legNote,
                at = at,
                chargeKind = charge,
                // The money that actually moved is the principal. Interest and
                // cost are owed, not transferred, so no message backs them.
                evidence = if (type == EntryType.LOAN_OUT) evidence else null,
            )
        ) {
            is Decision.Refused -> return step
            is Decision.Allowed -> {
                book = step.value.book
                appended += step.value.entries
            }
        }
    }
    return Decision.Allowed(Recorded(book, appended))
}

/**
 * Throw out a pending entry.
 *
 * The other answer to the question [confirm] asks, and it passes the same gate:
 * whoever recorded an entry cannot be the one who bins it. A rejected entry goes
 * to [EntryState.DISPUTED], which the fold ignores — so it never touched a
 * balance and never will — but it stays in the log with who rejected it and why.
 * Nothing is deleted.
 */
fun LedgerBook.reject(
    entryId: EntryId,
    rejectedBy: MemberId,
    config: ActorConfig,
    reason: String? = null,
    at: Instant? = null,
): Decision<Recorded> {
    val target = entry(entryId)
        ?: return Decision.Refused(Refusal.UnknownEntry(entryId))
    if (member(rejectedBy) == null) {
        return Decision.Refused(Refusal.UnknownMember(rejectedBy))
    }
    if (!isFounder(rejectedBy)) {
        return Decision.Refused(Refusal.NotAMember(rejectedBy))
    }
    when (val gate = checkReject(target, rejectedBy, config)) {
        is Decision.Refused -> return gate
        is Decision.Allowed -> Unit
    }

    val rejected = target.asRejectedBy(rejectedBy, at, reason)
    return Decision.Allowed(
        Recorded(
            copy(entries = entries.map { if (it.id == entryId) rejected else it }),
            listOf(rejected),
        ),
    )
}

/** Throw out every pending entry of one act — a loan's three legs together. */
fun LedgerBook.rejectGroup(
    groupId: String,
    rejectedBy: MemberId,
    config: ActorConfig,
    reason: String? = null,
    at: Instant? = null,
): Decision<Recorded> {
    val members = group(groupId).filter { it.state == EntryState.PENDING }
    if (members.isEmpty()) {
        return Decision.Refused(Refusal.Invalid("Nothing pending in group $groupId."))
    }
    var book = this
    val done = mutableListOf<Entry>()
    for (e in members) {
        when (val step = book.reject(e.id, rejectedBy, config, reason, at)) {
            is Decision.Refused -> return step
            is Decision.Allowed -> {
                book = step.value.book
                done += step.value.entries
            }
        }
    }
    return Decision.Allowed(Recorded(book, done))
}

/**
 * Cancel a confirmed entry by appending its inverse.
 *
 * Nothing is deleted: the mistake and the correction both stay in the log. The
 * reversal is itself PENDING, because undoing money is as consequential as
 * moving it and needs the same two people.
 */
fun LedgerBook.reverse(
    id: EntryId,
    reversesEntryId: EntryId,
    recordedBy: MemberId,
    config: ActorConfig,
    note: String? = null,
    at: Instant? = null,
    evidence: SmsEvidence? = null,
    chargeKind: ChargeKind? = null,
): Decision<Recorded> {
    when (val gate = checkRecord(recordedBy, config)) {
        is Decision.Refused -> return gate
        is Decision.Allowed -> Unit
    }
    val target = entry(reversesEntryId)
        ?: return Decision.Refused(Refusal.UnknownEntry(reversesEntryId))
    if (target.state != EntryState.CONFIRMED) {
        return Decision.Refused(
            Refusal.Invalid("Only a confirmed entry needs reversing; this one is ${target.state}."),
        )
    }

    val appended = Entry(
        id = id,
        seq = nextSeq,
        type = EntryType.REVERSAL,
        amountCents = target.amountCents,
        memberId = target.memberId,
        loanId = target.loanId,
        recordedByMemberId = recordedBy,
        state = EntryState.PENDING,
        reversesEntryId = reversesEntryId,
        note = note,
    )
    return Decision.Allowed(
        Recorded(
            copy(entries = entries + appended, nextSeq = nextSeq + 1),
            listOf(appended),
        ),
    )
}


/** One line of a loan, before it becomes an entry. */
private data class LoanLeg(
    val type: EntryType,
    val amountCents: Long,
    val note: String?,
    val charge: ChargeKind?,
)
