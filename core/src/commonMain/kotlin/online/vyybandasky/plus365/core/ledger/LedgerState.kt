package online.vyybandasky.plus365.core.ledger

import online.vyybandasky.plus365.core.domain.AccountId
import online.vyybandasky.plus365.core.domain.LoanDirection
import online.vyybandasky.plus365.core.domain.LoanId
import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.domain.EntryId
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.domain.MemberId
import online.vyybandasky.plus365.core.domain.PocketId

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
 * An account went below zero, and everything needed to find out why.
 *
 * **Flagged, never refused.** An entry that overdraws an account is still
 * recorded, because the money did move and a ledger that refuses to write down
 * what happened is worse than one that writes down something awkward. Blocking
 * it would also lose the very thing worth having: the slip itself.
 *
 * So this carries enough to trace the source rather than just complain — which
 * account, which entry took it under, who was on the other side, who recorded
 * it, how far under it went, and when. One flag is a mistake; the same
 * counterparty across several is a pattern, and the pattern is the point.
 *
 * Derived by the fold like every other total, so it can never be stale and can
 * never be dismissed into non-existence.
 */
data class OverdrawFlag(
    /** Which account went under. */
    val accountId: AccountId,
    /** The entry that took it under — the one to go and look at. */
    val entryId: EntryId,
    val seq: Long?,
    /** When it happened, where the entry recorded a time. */
    val at: Instant?,
    val type: EntryType,
    /** What that entry moved. */
    val amountCents: Long,
    /** Where the account stood immediately afterwards. Negative, by definition. */
    val balanceAfterCents: Long,
    /** Who the money concerned — the counterparty side of the slip. */
    val memberId: MemberId,
    /** Who wrote it down. The other half of tracing a habit. */
    val recordedByMemberId: MemberId?,
    /** What the money was earmarked for when it went under. */
    val pocketId: PocketId?,
) {
    /** How far under. Always positive. */
    val shortfallCents: Long get() = -balanceAfterCents
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
    /**
     * The same cash, split by what it is earmarked for. Sums to
     * [poolCashCents] as well — two views of one balance, from different sides.
     */
    val perPocket: Map<PocketId, Long> = emptyMap(),
    val loans: Map<LoanId, LoanOutstanding> = emptyMap(),

    /**
     * PENDING entries, kept apart so an unconfirmed claim is never mistaken for
     * money.
     */
    val pendingPerMember: Map<MemberId, MemberBalance> = emptyMap(),
    val pendingPoolCashCents: Long = 0L,

    /** How many local DRAFT entries were folded in optimistically, if any. */
    val includedDraftCount: Int = 0,

    /**
     * Every point at which an account went below zero, oldest first.
     *
     * Not errors and not warnings to be cleared — a running record of where the
     * books and the real accounts drifted apart, kept so the drift can be traced
     * to a cause.
     */
    val overdrawFlags: List<OverdrawFlag> = emptyList(),
) {
    fun balanceOf(memberId: MemberId): MemberBalance = perMember[memberId] ?: MemberBalance()

    fun pendingBalanceOf(memberId: MemberId): MemberBalance =
        pendingPerMember[memberId] ?: MemberBalance()

    /** The roll-up: every pocket added together. Equals [poolCashCents]. */
    val cashAtHandCents: Long get() = perAccount.values.sum()

    fun accountBalance(accountId: AccountId): Long = perAccount[accountId] ?: 0L

    /** Whether any account has ever dipped below zero. */
    val hasOverdrawn: Boolean get() = overdrawFlags.isNotEmpty()

    /** Flags against one account, oldest first. */
    fun overdrawsFor(accountId: AccountId): List<OverdrawFlag> =
        overdrawFlags.filter { it.accountId == accountId }

    /** The roll-up over pockets. Equals [cashAtHandCents] and [poolCashCents]. */
    val allocatedCents: Long get() = perPocket.values.sum()

    fun pocketBalance(pocketId: PocketId): Long = perPocket[pocketId] ?: 0L

    /**
     * Whether the three views of the pool agree.
     *
     * Where the money is, what it is for, and how much there is are computed
     * from the same entries by three separate routes. If they ever disagree the
     * fold has a hole in it, and a money app whose own totals disagree is worse
     * than useless — so this is asserted rather than assumed.
     */
    val balances: Boolean
        get() = cashAtHandCents == poolCashCents && allocatedCents == poolCashCents

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
