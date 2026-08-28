package online.vyybandasky.plus365.core.ledger

import online.vyybandasky.plus365.core.domain.AccountId
import online.vyybandasky.plus365.core.domain.LoanDirection
import online.vyybandasky.plus365.core.domain.LoanId
import online.vyybandasky.plus365.core.domain.MemberId

/**
 * What one member has put into the pool, and what they still have out on loan.
 *
 * [debtCents] is signed from the pool's point of view: negative means there is a
 * pending loan amount against them, positive means the pool owes them.
 */
data class MemberBalance(
    /** Their pool contribution. */
    val stakeCents: Long = 0L,
    val debtCents: Long = 0L,
)

/**
 * One loan, in the four parts the pool has always kept it in.
 *
 * Principal, interest and transaction cost are **gross** — everything ever
 * charged on this loan — and [repaidCents] is what has come back against it.
 * Every other figure here is derived from those four, so no two views of the
 * same loan can disagree.
 */
data class LoanOutstanding(
    val loanId: LoanId,
    val direction: LoanDirection,
    /** Everything ever lent on this loan, before repayments. */
    val principalCents: Long,
    /** Interest charged. Flat, at disbursement — not an accruing balance. */
    val interestAccruedCents: Long,
    /** The M-Pesa fee for moving it, kept apart from principal. */
    val mpesaChargeCents: Long,
    /** The bank's fee, once there is a bank account. Kept apart again. */
    val bankChargeCents: Long,
    /** Everything repaid against this loan. */
    val repaidCents: Long,
) {
    /** Both fees together. What it cost to move the money, whoever charged it. */
    val txnCostCents: Long get() = mpesaChargeCents + bankChargeCents

    /** What the loan cost the borrower: principal + interest + transaction cost. */
    val totalDueCents: Long
        get() = principalCents + interestAccruedCents + txnCostCents

    /** The pending loan amount. Never negative — an overpayment clamps at zero. */
    val outstandingCents: Long
        get() = (totalDueCents - repaidCents).coerceAtLeast(0L)

    /**
     * Principal still outstanding, ignoring interest and cost.
     *
     * The narrower view, kept because a repayment's allocation across the three
     * components is still an open question and this needs no answer to it.
     */
    val principalOutstandingCents: Long
        get() = (principalCents - repaidCents).coerceAtLeast(0L)

    val settled: Boolean get() = outstandingCents == 0L
}

/**
 * Derived, never stored. Two devices holding the same entries must produce an
 * identical [LedgerState] — which is why the fold is shared code rather than
 * written once per platform.
 */
data class LedgerState(
    /** CONFIRMED entries only. */
    val perMember: Map<MemberId, MemberBalance> = emptyMap(),
    /** CONFIRMED entries only. The pool's total cash. */
    val poolCashCents: Long = 0L,
    /**
     * The same cash, split by the pocket it sits in. Sums to [poolCashCents] by
     * construction — see the fold's account routing.
     */
    val perAccount: Map<AccountId, Long> = emptyMap(),
    val loans: Map<LoanId, LoanOutstanding> = emptyMap(),

    /**
     * PENDING entries, kept apart so an unconfirmed claim is never mistaken for
     * money.
     */
    val pendingPerMember: Map<MemberId, MemberBalance> = emptyMap(),
    val pendingPoolCashCents: Long = 0L,

    /** How many local DRAFT entries were folded in optimistically, if any. */
    val includedDraftCount: Int = 0,
) {
    fun balanceOf(memberId: MemberId): MemberBalance = perMember[memberId] ?: MemberBalance()

    fun pendingBalanceOf(memberId: MemberId): MemberBalance =
        pendingPerMember[memberId] ?: MemberBalance()

    /** The roll-up: every pocket added together. Equals [poolCashCents]. */
    val cashAtHandCents: Long get() = perAccount.values.sum()

    fun accountBalance(accountId: AccountId): Long = perAccount[accountId] ?: 0L

    /** Every pending loan amount owed to the pool, added up. */
    val totalOutstandingCents: Long
        get() = loans.values
            .filter { it.direction == LoanDirection.POOL_TO_MEMBER }
            .sumOf { it.outstandingCents }

    /** Everything the pool still owes its members. */
    val totalOwedToMembersCents: Long
        get() = loans.values
            .filter { it.direction == LoanDirection.MEMBER_TO_POOL }
            .sumOf { it.outstandingCents }
}
