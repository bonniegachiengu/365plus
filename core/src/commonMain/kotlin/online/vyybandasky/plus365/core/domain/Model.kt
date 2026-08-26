package online.vyybandasky.plus365.core.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

typealias MemberId = String
typealias AccountId = String
typealias DeviceId = String
typealias EntryId = String
typealias LoanId = String

/**
 * The six money-moving types plus two system types (365PLUS_BRIEF.md §2a).
 *
 * Each has one fixed, unambiguous effect on stake, debt and pool cash. That
 * table is encoded exactly once, in [online.vyybandasky.plus365.core.ledger.effectOf].
 */
@Serializable
enum class EntryType {
    CONTRIBUTION,
    PAYOUT,
    LOAN_OUT,
    LOAN_REPAYMENT,
    MEMBER_LOAN_IN,
    POOL_REPAY_MEMBER,

    /** Interest on an outstanding loan. Never moves cash — it is owed, not held. */
    INTEREST_ACCRUAL,

    /**
     * The M-Pesa cost of moving a loan. Kept as its own component rather than
     * folded into principal: the pool has always tracked it separately, and
     * burying it would put our running total a few shillings off theirs with no
     * way to tell which was right.
     */
    TXN_COST,

    /** Cash moved between the pool's own accounts. Cash-at-hand is unchanged. */
    TRANSFER,

    /** Cancels a prior entry by applying the exact inverse of its effect. */
    REVERSAL,
}

/** DRAFT -> (sync) -> PENDING -> CONFIRMED | DISPUTED -> (if disputed) VOID via reversal. */
@Serializable
enum class EntryState { DRAFT, PENDING, CONFIRMED, DISPUTED, VOID }

@Serializable
enum class ConfirmSource { HUMAN, MPESA, SYSTEM }

@Serializable
enum class LoanDirection {
    /** The pool lent to a member: the member owes the pool. */
    POOL_TO_MEMBER,

    /** A member lent to the pool: the pool owes the member. */
    MEMBER_TO_POOL,
}

@Serializable
enum class LoanState { ACTIVE, SETTLED, WRITTEN_OFF }

@Serializable
enum class InterestPeriod { MONTHLY }

@Serializable
enum class InterestMethod { SIMPLE }

/**
 * Where the pool's money physically sits.
 *
 * Cash-at-hand is the sum of these and is derived, never stored — the same
 * discipline as every other total in this app.
 */
@Serializable
data class Account(
    val id: AccountId,
    val label: String,
)

object Accounts {
    /**
     * Cash whose account was never stated. Entries recorded before accounts
     * existed land here rather than vanishing from the roll-up.
     */
    const val UNASSIGNED: AccountId = "unassigned"
}

@Serializable
data class Member(
    val id: MemberId,
    val displayName: String,
    val phoneE164: String,
    val active: Boolean = true,
    val joinedAt: Instant? = null,
)

@Serializable
data class Device(
    val id: DeviceId,
    val memberId: MemberId,
    val publicKey: String,
    val fcmToken: String? = null,
    val enrolledAt: Instant? = null,
    val revokedAt: Instant? = null,
)

@Serializable
data class Loan(
    val id: LoanId,
    val direction: LoanDirection,
    val counterpartyMemberId: MemberId,
    val principalCents: Long,
    /** The M-Pesa cost of disbursing this loan. Tracked apart from principal. */
    val txnCostCents: Long = 0L,
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
@Serializable
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

    /** Set for LOAN_OUT, LOAN_REPAYMENT, MEMBER_LOAN_IN, POOL_REPAY_MEMBER, INTEREST_ACCRUAL, TXN_COST. */
    val loanId: LoanId? = null,

    /**
     * Which pocket the cash moved through. Null means [Accounts.UNASSIGNED].
     * On a TRANSFER this is the destination.
     */
    val accountId: AccountId? = null,

    /** The source account. TRANSFER only. */
    val counterAccountId: AccountId? = null,

    /**
     * Entries that describe one act and are confirmed together — a loan's
     * principal, its interest and its transaction cost are three facts but one
     * decision. Each still records its own confirmation.
     */
    val groupId: String? = null,

    /** When the money actually moved — not when it was typed in. */
    val eventDate: LocalDate? = null,

    val recordedByMemberId: MemberId? = null,
    val recordedByDeviceId: DeviceId? = null,
    val recordedAt: Instant? = null,

    val state: EntryState = EntryState.PENDING,
    val confirmedByMemberId: MemberId? = null,
    val confirmedAt: Instant? = null,
    val confirmSource: ConfirmSource? = null,

    /**
     * Who rejected this, if anyone. A rejection is the other answer to the same
     * question a confirmation answers, so it obeys the same rule: the member who
     * recorded an entry may not be the one who throws it out.
     */
    val rejectedByMemberId: MemberId? = null,
    val rejectedAt: Instant? = null,
    val rejectionReason: String? = null,

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
        require(type != EntryType.TRANSFER || counterAccountId != null) {
            "a TRANSFER must name the account the money came from"
        }
        require(type == EntryType.TRANSFER || counterAccountId == null) {
            "only a TRANSFER may carry counterAccountId"
        }
    }
}
