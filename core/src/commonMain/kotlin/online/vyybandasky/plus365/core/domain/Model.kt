package online.vyybandasky.plus365.core.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

typealias MemberId = String
typealias DeviceId = String
typealias EntryId = String
typealias LoanId = String

/**
 * The six money-moving types plus two system types (365PLUS_BRIEF.md §2a).
 *
 * Each has one fixed, unambiguous effect on stake, debt and pool cash. That
 * table is encoded exactly once, in [online.vyybandasky.plus365.core.ledger.effectOf].
 */
enum class EntryType {
    CONTRIBUTION,
    PAYOUT,
    LOAN_OUT,
    LOAN_REPAYMENT,
    MEMBER_LOAN_IN,
    POOL_REPAY_MEMBER,

    /** Interest for one period on an outstanding loan. Never moves cash. */
    INTEREST_ACCRUAL,

    /** Cancels a prior entry by applying the exact inverse of its effect. */
    REVERSAL,
}

/** DRAFT -> (sync) -> PENDING -> CONFIRMED | DISPUTED -> (if disputed) VOID via reversal. */
enum class EntryState { DRAFT, PENDING, CONFIRMED, DISPUTED, VOID }

enum class ConfirmSource { HUMAN, MPESA, SYSTEM }

enum class LoanDirection {
    /** The pool lent to a member: the member owes the pool. */
    POOL_TO_MEMBER,

    /** A member lent to the pool: the pool owes the member. */
    MEMBER_TO_POOL,
}

enum class LoanState { ACTIVE, SETTLED, WRITTEN_OFF }

enum class InterestPeriod { MONTHLY }

enum class InterestMethod { SIMPLE }

data class Member(
    val id: MemberId,
    val displayName: String,
    val phoneE164: String,
    val active: Boolean = true,
    val joinedAt: Instant? = null,
)

data class Device(
    val id: DeviceId,
    val memberId: MemberId,
    val publicKey: String,
    val fcmToken: String? = null,
    val enrolledAt: Instant? = null,
    val revokedAt: Instant? = null,
)

data class Loan(
    val id: LoanId,
    val direction: LoanDirection,
    val counterpartyMemberId: MemberId,
    val principalCents: Long,
    val rateBps: Int = 0,
    val period: InterestPeriod = InterestPeriod.MONTHLY,
    val interestMethod: InterestMethod = InterestMethod.SIMPLE,
    val startDate: LocalDate? = null,
    val dueDate: LocalDate? = null,
    val termsNote: String? = null,
    val state: LoanState = LoanState.ACTIVE,
)

/**
 * An immutable, append-only money record.
 *
 * Nothing here is ever edited or deleted — a correction is a new [EntryType.REVERSAL]
 * pointing at the original (§1.1). [amountCents] is always a positive magnitude;
 * direction comes from [type], never from the sign of the amount (§1.3).
 */
data class Entry(
    /** Client-generated UUID. Doubles as the idempotency key on sync push. */
    val id: EntryId,

    /** Server-assigned total order. Null until the master has accepted it. */
    val seq: Long? = null,

    val type: EntryType,
    val amountCents: Long,
    val currency: String = "KES",

    /** Whose stake/debt this touches. */
    val memberId: MemberId,

    /** Set for LOAN_OUT, LOAN_REPAYMENT, MEMBER_LOAN_IN, POOL_REPAY_MEMBER, INTEREST_ACCRUAL. */
    val loanId: LoanId? = null,

    /** When the money actually moved — not when it was typed in. */
    val eventDate: LocalDate? = null,

    val recordedByMemberId: MemberId? = null,
    val recordedByDeviceId: DeviceId? = null,
    val recordedAt: Instant? = null,

    val state: EntryState = EntryState.PENDING,
    val confirmedByMemberId: MemberId? = null,
    val confirmedAt: Instant? = null,
    val confirmSource: ConfirmSource? = null,

    /** UNIQUE where not null — the duplicate-entry defence (§2b). */
    val mpesaRef: String? = null,

    /** Set on REVERSAL, pointing at the entry being cancelled. */
    val reversesEntryId: EntryId? = null,

    val note: String? = null,

    /** Ed25519 over the canonical payload. Populated from M2. */
    val signature: String? = null,
) {
    init {
        require(amountCents >= 0) {
            "amountCents is a magnitude, never negative; direction comes from type. Got $amountCents"
        }
        require(type != EntryType.REVERSAL || reversesEntryId != null) {
            "a REVERSAL must name the entry it reverses"
        }
        require(type == EntryType.REVERSAL || reversesEntryId == null) {
            "only a REVERSAL may carry reversesEntryId"
        }
    }
}
