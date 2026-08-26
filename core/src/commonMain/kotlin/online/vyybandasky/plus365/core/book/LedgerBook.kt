package online.vyybandasky.plus365.core.book

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
import online.vyybandasky.plus365.core.governance.checkConfirm
import online.vyybandasky.plus365.core.governance.checkRecord
import online.vyybandasky.plus365.core.interest.HOUSE_RATE_BPS
import online.vyybandasky.plus365.core.interest.houseInterestCents
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
    note: String? = null,
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
        recordedByMemberId = recordedBy,
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

    val confirmed = target.asConfirmedBy(confirmedBy, source)
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
 * Two entries, not one: the principal leaving the pool, and the interest the
 * borrower now owes. They are separate facts and each needs its own
 * confirmation — the interest charge is exactly the kind of thing two people
 * should have to agree on.
 */
fun LedgerBook.disburseLoan(
    loanId: LoanId,
    principalEntryId: EntryId,
    interestEntryId: EntryId,
    borrower: MemberId,
    principalCents: Long,
    recordedBy: MemberId,
    config: ActorConfig,
    rateBps: Int = HOUSE_RATE_BPS,
    note: String? = null,
): Decision<Recorded> {
    if (loan(loanId) != null) {
        return Decision.Refused(Refusal.Invalid("Loan $loanId already exists."))
    }
    val interest = houseInterestCents(principalCents).takeIf { rateBps == HOUSE_RATE_BPS }
        ?: online.vyybandasky.plus365.core.interest.interestCents(principalCents, rateBps)

    val loan = Loan(
        id = loanId,
        direction = LoanDirection.POOL_TO_MEMBER,
        counterpartyMemberId = borrower,
        principalCents = principalCents,
        rateBps = rateBps,
        period = InterestPeriod.MONTHLY,
    )
    val withLoan = copy(loans = loans + loan)

    val principalStep = withLoan.record(
        id = principalEntryId,
        type = EntryType.LOAN_OUT,
        amountCents = principalCents,
        memberId = borrower,
        recordedBy = recordedBy,
        config = config,
        loanId = loanId,
        note = note,
    )
    val afterPrincipal = when (principalStep) {
        is Decision.Refused -> return principalStep
        is Decision.Allowed -> principalStep.value
    }

    if (interest <= 0L) {
        return Decision.Allowed(afterPrincipal)
    }

    val interestStep = afterPrincipal.book.record(
        id = interestEntryId,
        type = EntryType.INTEREST_ACCRUAL,
        amountCents = interest,
        memberId = borrower,
        recordedBy = recordedBy,
        config = config,
        loanId = loanId,
        note = "Interest at ${rateBps / 100.0}% flat",
    )
    return when (interestStep) {
        is Decision.Refused -> interestStep
        is Decision.Allowed -> Decision.Allowed(
            Recorded(
                interestStep.value.book,
                afterPrincipal.entries + interestStep.value.entries,
            ),
        )
    }
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
