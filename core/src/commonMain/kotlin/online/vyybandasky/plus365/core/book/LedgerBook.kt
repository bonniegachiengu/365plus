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
import online.vyybandasky.plus365.core.domain.MemberId
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.governance.Decision
import online.vyybandasky.plus365.core.governance.Refusal
import online.vyybandasky.plus365.core.governance.asConfirmedBy
import online.vyybandasky.plus365.core.governance.asRejectedBy
import online.vyybandasky.plus365.core.governance.checkConfirm
import online.vyybandasky.plus365.core.governance.checkRecord
import online.vyybandasky.plus365.core.governance.checkReject
import online.vyybandasky.plus365.core.interest.HOUSE_RATE_BPS
import online.vyybandasky.plus365.core.interest.interestCents
import online.vyybandasky.plus365.core.ledger.LedgerState
import online.vyybandasky.plus365.core.ledger.fold

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
    /** The pockets the pool's cash sits in. Cash-at-hand is their sum. */
    val accounts: List<Account> = emptyList(),
    val loans: List<Loan> = emptyList(),
    val entries: List<Entry> = emptyList(),
    val nextSeq: Long = 1L,
) {
    /** Balances. Derived on demand, never stored — see [fold]. */
    fun state(): LedgerState = fold(entries, loans)

    fun member(id: MemberId): Member? = members.firstOrNull { it.id == id }

    fun memberIds(): List<MemberId> = members.map { it.id }

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

    /** The default pocket — the first one declared. */
    fun defaultAccount(): AccountId? = accounts.firstOrNull()?.id

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
fun LedgerBook.record(
    id: EntryId,
    type: EntryType,
    amountCents: Long,
    memberId: MemberId,
    recordedBy: MemberId,
    config: ActorConfig,
    loanId: LoanId? = null,
    accountId: AccountId? = null,
    groupId: String? = null,
    note: String? = null,
    at: Instant? = null,
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

    val appended = Entry(
        id = id,
        seq = nextSeq,
        type = type,
        amountCents = amountCents,
        memberId = memberId,
        loanId = loanId,
        accountId = accountId ?: defaultAccount(),
        groupId = groupId,
        recordedByMemberId = recordedBy,
        recordedAt = at,
        state = EntryState.PENDING,
        note = note,
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
): Decision<Recorded> {
    val members = group(groupId).filter { it.state == EntryState.PENDING }
    if (members.isEmpty()) {
        return Decision.Refused(Refusal.Invalid("Nothing pending in group $groupId."))
    }
    var book = this
    val confirmed = mutableListOf<Entry>()
    for (e in members) {
        when (val step = book.confirm(e.id, confirmedBy, config, source, at)) {
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
): Decision<Recorded> {
    val target = entry(entryId)
        ?: return Decision.Refused(Refusal.UnknownEntry(entryId))
    if (member(confirmedBy) == null) {
        return Decision.Refused(Refusal.UnknownMember(confirmedBy))
    }
    when (val gate = checkConfirm(target, confirmedBy, config)) {
        is Decision.Refused -> return gate
        is Decision.Allowed -> Unit
    }

    val confirmed = target.asConfirmedBy(confirmedBy, source, at)
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
    txnCostCents: Long = 0L,
    rateBps: Int = HOUSE_RATE_BPS,
    fromAccount: AccountId? = null,
    note: String? = null,
    at: Instant? = null,
): Decision<Recorded> {
    if (loan(loanId) != null) {
        return Decision.Refused(Refusal.Invalid("Loan $loanId already exists."))
    }
    if (txnCostCents < 0L) {
        return Decision.Refused(Refusal.Invalid("Transaction cost cannot be negative."))
    }
    val interest = interestCents(principalCents, rateBps)
    val account = fromAccount ?: defaultAccount()

    val loan = Loan(
        id = loanId,
        direction = LoanDirection.POOL_TO_MEMBER,
        counterpartyMemberId = borrower,
        principalCents = principalCents,
        txnCostCents = txnCostCents,
        rateBps = rateBps,
        period = InterestPeriod.MONTHLY,
    )

    // Each leg of the loan, in the order the money is thought about.
    val legs = buildList {
        add(Triple(EntryType.LOAN_OUT, principalCents, note))
        if (interest > 0L) {
            add(Triple(EntryType.INTEREST_ACCRUAL, interest, "Interest at ${rateBps / 100.0}% flat"))
        }
        if (txnCostCents > 0L) {
            add(Triple(EntryType.TXN_COST, txnCostCents, "M-Pesa cost"))
        }
    }

    var book = copy(loans = loans + loan)
    val appended = mutableListOf<Entry>()
    for ((type, amount, legNote) in legs) {
        val suffix = when (type) {
            EntryType.LOAN_OUT -> "principal"
            EntryType.INTEREST_ACCRUAL -> "interest"
            else -> "txncost"
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
