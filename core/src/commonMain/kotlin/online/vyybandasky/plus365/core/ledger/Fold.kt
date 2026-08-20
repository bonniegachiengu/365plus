package online.vyybandasky.plus365.core.ledger

import online.vyybandasky.plus365.core.domain.Entry
import online.vyybandasky.plus365.core.domain.EntryId
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.domain.Loan
import online.vyybandasky.plus365.core.domain.LoanDirection
import online.vyybandasky.plus365.core.domain.LoanId
import online.vyybandasky.plus365.core.domain.MemberId

/** The effect of one entry on the three quantities the ledger tracks. */
data class Effect(
    val stakeCents: Long = 0L,
    val debtCents: Long = 0L,
    val poolCashCents: Long = 0L,
) {
    operator fun plus(other: Effect) = Effect(
        stakeCents + other.stakeCents,
        debtCents + other.debtCents,
        poolCashCents + other.poolCashCents,
    )

    fun negated() = Effect(-stakeCents, -debtCents, -poolCashCents)
}

/** How deep a reversal-of-a-reversal chain may go before we call it a cycle. */
private const val MAX_REVERSAL_DEPTH = 32

/**
 * The §2a table, encoded exactly once.
 *
 * REVERSAL is absent on purpose: its effect is the inverse of whatever it points
 * at, which only [fold] can resolve.
 */
fun effectOf(
    type: EntryType,
    amountCents: Long,
    loanDirection: LoanDirection? = null,
): Effect {
    val amt = amountCents
    return when (type) {
        EntryType.CONTRIBUTION -> Effect(stakeCents = amt, poolCashCents = amt)
        EntryType.PAYOUT -> Effect(stakeCents = -amt, poolCashCents = -amt)
        EntryType.LOAN_OUT -> Effect(debtCents = -amt, poolCashCents = -amt)
        EntryType.LOAN_REPAYMENT -> Effect(debtCents = amt, poolCashCents = amt)
        EntryType.MEMBER_LOAN_IN -> Effect(debtCents = amt, poolCashCents = amt)
        EntryType.POOL_REPAY_MEMBER -> Effect(debtCents = -amt, poolCashCents = -amt)

        // Interest changes what is owed and never moves cash. Which way it moves
        // depends on who lent to whom, so it needs the loan.
        EntryType.INTEREST_ACCRUAL -> when (loanDirection) {
            LoanDirection.POOL_TO_MEMBER -> Effect(debtCents = -amt)
            LoanDirection.MEMBER_TO_POOL -> Effect(debtCents = amt)
            null -> throw IllegalArgumentException(
                "INTEREST_ACCRUAL needs its loan to know the direction; pass loans to fold()"
            )
        }

        EntryType.REVERSAL -> throw IllegalArgumentException(
            "a REVERSAL effect is the inverse of its target and is resolved by fold()"
        )
    }
}

/**
 * Replays the log into balances. Pure — no I/O, no clock.
 *
 * Only [EntryState.CONFIRMED] entries move the real balances. PENDING is reported
 * separately so an unconfirmed claim is never mistaken for money. DISPUTED and
 * VOID are ignored entirely.
 *
 * Ordering is by seq; entries without one (not yet accepted by the master) sort
 * last, by id, so the result is deterministic for any input order.
 *
 * @param loans needed only to resolve INTEREST_ACCRUAL direction. The brief
 *   documents this as fold(entries), and that still works — loans is a default.
 * @param includeDrafts folds this device's own unsynced DRAFT entries in
 *   optimistically (§2c). Never do this for another device's drafts.
 */
fun fold(
    entries: List<Entry>,
    loans: List<Loan> = emptyList(),
    includeDrafts: Boolean = false,
): LedgerState {
    val byId: Map<EntryId, Entry> = entries.associateBy { it.id }
    val loansById: Map<LoanId, Loan> = loans.associateBy { it.id }

    // seq first (nulls last), then id — a total order regardless of input order.
    val ordered = entries.sortedWith(
        compareBy<Entry> { it.seq ?: Long.MAX_VALUE }.thenBy { it.id }
    )

    val confirmedMembers = mutableMapOf<MemberId, MemberBalance>()
    val pendingMembers = mutableMapOf<MemberId, MemberBalance>()
    var confirmedCash = 0L
    var pendingCash = 0L
    var draftCount = 0

    val loanTallies = mutableMapOf<LoanId, MutableLoanTally>()

    for (entry in ordered) {
        val counts = when (entry.state) {
            EntryState.CONFIRMED -> true
            EntryState.DRAFT -> includeDrafts
            else -> false
        }
        val isPending = entry.state == EntryState.PENDING
        if (!counts && !isPending) continue

        val effect = resolveEffect(entry, byId, loansById, depth = 0)

        if (counts) {
            if (entry.state == EntryState.DRAFT) draftCount++
            confirmedMembers.accumulate(entry.memberId, effect)
            confirmedCash += effect.poolCashCents
            tallyLoan(entry, byId, loansById, loanTallies)
        } else {
            pendingMembers.accumulate(entry.memberId, effect)
            pendingCash += effect.poolCashCents
        }
    }

    return LedgerState(
        perMember = confirmedMembers.toMap(),
        poolCashCents = confirmedCash,
        loans = loanTallies.mapValues { (_, tally) -> tally.toOutstanding() },
        pendingPerMember = pendingMembers.toMap(),
        pendingPoolCashCents = pendingCash,
        includedDraftCount = draftCount,
    )
}

private fun resolveEffect(
    entry: Entry,
    byId: Map<EntryId, Entry>,
    loansById: Map<LoanId, Loan>,
    depth: Int,
): Effect {
    if (entry.type != EntryType.REVERSAL) {
        return effectOf(entry.type, entry.amountCents, entry.loanDirection(loansById))
    }
    require(depth < MAX_REVERSAL_DEPTH) {
        "reversal chain deeper than " + MAX_REVERSAL_DEPTH + " at " + entry.id + "; likely a cycle"
    }
    val targetId = entry.reversesEntryId
        ?: throw IllegalArgumentException("REVERSAL " + entry.id + " has no target")
    val target = byId[targetId]
        ?: throw IllegalArgumentException(
            "REVERSAL " + entry.id + " points at " + targetId + ", which is not in the log"
        )
    return resolveEffect(target, byId, loansById, depth + 1).negated()
}

private fun Entry.loanDirection(loansById: Map<LoanId, Loan>): LoanDirection? =
    loanId?.let { loansById[it]?.direction }

private fun MutableMap<MemberId, MemberBalance>.accumulate(memberId: MemberId, effect: Effect) {
    val current = this[memberId] ?: MemberBalance()
    this[memberId] = MemberBalance(
        stakeCents = current.stakeCents + effect.stakeCents,
        debtCents = current.debtCents + effect.debtCents,
    )
}

private class MutableLoanTally(val loanId: LoanId, val direction: LoanDirection) {
    var principal = 0L
    var interest = 0L

    fun toOutstanding() = LoanOutstanding(
        loanId = loanId,
        direction = direction,
        principalOutstandingCents = if (principal < 0L) 0L else principal,
        interestAccruedCents = interest,
    )
}

/**
 * Loan-level running totals. Deliberately thin for M0 — repayment allocation
 * (interest first, then principal) is M4.
 */
private fun tallyLoan(
    entry: Entry,
    byId: Map<EntryId, Entry>,
    loansById: Map<LoanId, Loan>,
    into: MutableMap<LoanId, MutableLoanTally>,
) {
    // A reversal is tallied as the inverse of its target.
    val subject: Entry
    val sign: Long
    if (entry.type == EntryType.REVERSAL) {
        subject = byId[entry.reversesEntryId] ?: return
        sign = -1L
    } else {
        subject = entry
        sign = 1L
    }

    val loanId = subject.loanId ?: return
    val loan = loansById[loanId] ?: return
    val tally = into.getOrPut(loanId) { MutableLoanTally(loanId, loan.direction) }
    val amt = subject.amountCents * sign

    when (subject.type) {
        EntryType.LOAN_OUT, EntryType.MEMBER_LOAN_IN -> tally.principal += amt
        EntryType.LOAN_REPAYMENT, EntryType.POOL_REPAY_MEMBER -> tally.principal -= amt
        EntryType.INTEREST_ACCRUAL -> tally.interest += amt
        else -> Unit
    }
}
