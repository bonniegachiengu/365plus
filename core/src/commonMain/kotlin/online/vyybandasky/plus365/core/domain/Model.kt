package online.vyybandasky.plus365.core.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import online.vyybandasky.plus365.core.governance.Conflict
import online.vyybandasky.plus365.core.governance.OverrideRecord
import online.vyybandasky.plus365.core.sms.Assurance
import online.vyybandasky.plus365.core.sms.SmsEvidence

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
     * What it cost to move the money — the M-Pesa fee, or the bank's. Kept as its
     * own component rather than folded into principal: the pool has always
     * tracked it separately, and burying it would put our running total a few
     * shillings off theirs with no way to tell which was right.
     *
     * Which kind of charge it was lives in [Entry.chargeKind]. One entry type
     * rather than two, because the effect on the books is identical and the
     * effect table is deliberately written exactly once.
     */
    TXN_COST,

    /** Cash moved between the pool's own accounts. Cash-at-hand is unchanged. */
    TRANSFER,

    /** Cancels a prior entry by applying the exact inverse of its effect. */
    REVERSAL,
}

/**
 * DRAFT -> (sync) -> PENDING -> CONFIRMED | DISPUTED, or sideways into
 * NEEDS_OVERRIDE when the usual two cannot settle it and the third member has
 * to. VOID is reached from DISPUTED via a reversal.
 */
@Serializable
enum class EntryState { DRAFT, PENDING, CONFIRMED, DISPUTED, NEEDS_OVERRIDE, VOID }

@Serializable
/**
 * What stood in for a confirmation.
 *
 * OVERRIDE is its own source rather than a flavour of HUMAN: an entry settled by
 * the third member is a fact about a disagreement, and flattening that into
 * "a human confirmed it" would lose the only part worth knowing.
 */
enum class ConfirmSource { HUMAN, MPESA, SYSTEM, OVERRIDE }

@Serializable
enum class LoanDirection {
    /** The pool lent to a member: the member owes the pool. */
    POOL_TO_MEMBER,

    /** A member lent to the pool: the pool owes the member. */
    MEMBER_TO_POOL,
}

@Serializable
enum class LoanState { ACTIVE, SETTLED, WRITTEN_OFF }

/** Whose fee this was. Set on [EntryType.TXN_COST]. */
@Serializable
enum class ChargeKind { MPESA, BANK }

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

/**
 * Who a party to the ledger is.
 *
 * This is not a label. A founder owns a share of the pool and governs it —
 * records, confirms, settles. A Keshflo beneficiary is someone the pool lends
 * to and nothing else: they hold no contribution, they cannot confirm anybody's
 * entry, and they can never settle a disagreement. Letting an outside borrower
 * near the two-person control would hand a say in the members' money to someone
 * with no stake in it.
 */
@Serializable
enum class MemberKind {
    /** One of the three. Contributes, borrows, and governs. */
    FOUNDER,

    /** Someone Keshflo lends to. Borrows only. */
    KESHFLO_BENEFICIARY,
}

@Serializable
data class Member(
    val id: MemberId,
    val displayName: String,
    val phoneE164: String,
    val kind: MemberKind = MemberKind.FOUNDER,
    val active: Boolean = true,
    val joinedAt: Instant? = null,
) {
    val isFounder: Boolean get() = kind == MemberKind.FOUNDER
    val isBeneficiary: Boolean get() = kind == MemberKind.KESHFLO_BENEFICIARY
}

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
    /** The M-Pesa fee for disbursing this loan. Tracked apart from principal. */
    val mpesaChargeCents: Long = 0L,
    /** The bank's fee, once there is a bank account. Tracked apart again. */
    val bankChargeCents: Long = 0L,
    /**
     * The rate this loan was actually written at. Founders borrow at one rate
     * and Keshflo beneficiaries at another, and a loan keeps whichever applied
     * when it was made — never a rate looked up later.
     */
    val rateBps: Int = 0,
    /** What the borrower was when this was written. Kept for the same reason. */
    val borrowerKind: MemberKind = MemberKind.FOUNDER,
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

    /**
     * The recorder's own transaction message, if they had one.
     *
     * Kept on the entry rather than alongside it because the message *is* the
     * evidence for this entry — separating them would let one survive the other.
     */
    val recordedEvidence: SmsEvidence? = null,

    /** The confirmer's own message. Its code must match [recordedEvidence]. */
    val confirmedEvidence: SmsEvidence? = null,

    /**
     * Why the usual two could not settle this, if they could not. Kept whole,
     * including the message that failed to match, because the third member
     * cannot arbitrate on a summary.
     */
    val conflict: Conflict? = null,

    /**
     * Every override ever applied, oldest first. Append-only like everything
     * else: a settled disagreement leaves its history behind, not a tidy result.
     */
    val overrides: List<OverrideRecord> = emptyList(),

    /** Set on a replacement entry: the entry it was written to correct. */
    val correctsEntryId: EntryId? = null,

    /**
     * Which fee this was, on a TXN_COST entry. M-Pesa today; the bank once the
     * account is open. Split so the two can be told apart in the books rather
     * than added into one number nobody can take back apart.
     */
    val chargeKind: ChargeKind? = null,

    /**
     * How well backed this entry is. [Assurance.CODE_MATCHED] means two members'
     * messages carried the same transaction code; [Assurance.ATTESTED] means a
     * second member vouched for it without one.
     */
    val assurance: Assurance? = null,

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
