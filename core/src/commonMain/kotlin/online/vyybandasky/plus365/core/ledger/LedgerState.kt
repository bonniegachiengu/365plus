package online.vyybandasky.plus365.core.ledger

import online.vyybandasky.plus365.core.domain.LoanDirection
import online.vyybandasky.plus365.core.domain.LoanId
import online.vyybandasky.plus365.core.domain.MemberId

/**
 * What one member has put in, and what they owe.
 *
 * [debtCents] is signed from the pool's point of view: negative means the member
 * owes the pool, positive means the pool owes the member (365PLUS_BRIEF.md §2a).
 */
data class MemberBalance(
    val stakeCents: Long = 0L,
    val debtCents: Long = 0L,
)

data class LoanOutstanding(
    val loanId: LoanId,
    val direction: LoanDirection,
    /** Principal still outstanding. Never negative — an overpayment clamps at zero. */
    val principalOutstandingCents: Long,
    /** Interest accrued to date, before any repayment allocation (M4). */
    val interestAccruedCents: Long,
)

/**
 * Derived, never stored (§1.2). Two devices holding the same entries must produce
 * an identical [LedgerState] — which is why the fold is shared code rather than
 * written once per platform.
 */
data class LedgerState(
    /** CONFIRMED entries only. */
    val perMember: Map<MemberId, MemberBalance> = emptyMap(),
    /** CONFIRMED entries only. */
    val poolCashCents: Long = 0L,
    val loans: Map<LoanId, LoanOutstanding> = emptyMap(),

    /**
     * PENDING entries, kept apart so an unconfirmed claim is never mistaken for
     * money (§2c).
     */
    val pendingPerMember: Map<MemberId, MemberBalance> = emptyMap(),
    val pendingPoolCashCents: Long = 0L,

    /** How many local DRAFT entries were folded in optimistically, if any. */
    val includedDraftCount: Int = 0,
) {
    fun balanceOf(memberId: MemberId): MemberBalance = perMember[memberId] ?: MemberBalance()

    fun pendingBalanceOf(memberId: MemberId): MemberBalance =
        pendingPerMember[memberId] ?: MemberBalance()
}
