package online.vyybandasky.plus365.core.presentation

import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.BuildInfo
import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.book.needingOverride
import online.vyybandasky.plus365.core.book.overrideActs
import online.vyybandasky.plus365.core.domain.EntryId
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.domain.MemberId
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.governance.ConflictKind
import online.vyybandasky.plus365.core.governance.eligibleOverriders
import online.vyybandasky.plus365.core.governance.label
import online.vyybandasky.plus365.core.money.formatKes
import online.vyybandasky.plus365.core.sms.Assurance
import online.vyybandasky.plus365.core.sms.SmsEvidence
import online.vyybandasky.plus365.core.sms.blurb
import online.vyybandasky.plus365.core.sms.label

/**
 * The screens you reach by tapping something.
 *
 * An entry, a member, or yourself. Each is built here so both shells show the
 * same words, and so the ledger's own claim — that nothing is hidden — is
 * something a member can actually check by tapping.
 */

// ── one entry, in full ───────────────────────────────────────────────────────

data class EvidenceView(
    val whose: String,
    val reference: String,
    val amount: String,
    val direction: String,
    val counterparty: String?,
    val occurredAt: String?,
    val raw: String,
)

data class OverrideView(
    val by: String,
    val decision: String,
    val reason: String,
    val whenIt: String,
    val correctedAmount: String?,
)

data class ConflictView(
    val kind: String,
    val raisedBy: String,
    val whenIt: String,
    val reasons: List<String>,
    val note: String?,
    /** Exactly one member with three; the screen names them. */
    val settledBy: List<MemberRef>,
)

/**
 * Everything known about one entry.
 *
 * Deliberately exhaustive. This is the screen that backs the claim on the ledger
 * page — that nothing is hidden and nothing is editable — so anything the app
 * holds about an entry belongs here where a member can read it.
 */
data class EntryDetail(
    val entryId: EntryId,
    val headline: String,
    val amount: String,
    val standing: Standing,
    val standingLabel: String,
    val typeLabel: String,
    val member: String,
    val account: String?,
    val recordedBy: String,
    val recordedWhen: String,
    val confirmedBy: String?,
    val confirmedWhen: String?,
    val rejectedBy: String?,
    val rejectionReason: String?,
    val assurance: Assurance?,
    val assuranceLabel: String?,
    val assuranceBlurb: String?,
    val recordedEvidence: EvidenceView?,
    val confirmedEvidence: EvidenceView?,
    val conflict: ConflictView?,
    val overrides: List<OverrideView>,
    val correctsEntryId: EntryId?,
    val correctedByEntryId: EntryId?,
    val note: String?,
    /**
     * Whether this entry can still be corrected by appending its reverse.
     *
     * Only a confirmed entry: a waiting one is rejected instead, and a rejected
     * one never counted, so there is nothing to undo.
     */
    val canReverse: Boolean,
    /** Set once a reversal has been written against it. */
    val reversedByEntryId: EntryId?,
)

private fun LedgerBook.evidenceView(e: SmsEvidence): EvidenceView = EvidenceView(
    whose = displayName(e.pastedBy),
    reference = e.reference,
    amount = formatKes(e.amountCents),
    direction = if (e.direction.name == "SENT") "paid out" else "received",
    counterparty = e.counterparty,
    occurredAt = e.occurredAtText,
    raw = e.raw,
)

fun LedgerBook.entryDetail(
    entryId: EntryId,
    config: ActorConfig,
    now: Instant? = null,
): EntryDetail? {
    val e = entry(entryId) ?: return null
    val standing = when (e.state) {
        EntryState.CONFIRMED -> Standing.CONFIRMED
        EntryState.DISPUTED -> Standing.REJECTED
        EntryState.NEEDS_OVERRIDE -> Standing.NEEDS_SETTLING
        else -> Standing.PENDING
    }
    return EntryDetail(
        entryId = e.id,
        headline = "${e.type.label()} — ${displayName(e.memberId)}",
        amount = formatKes(e.amountCents),
        standing = standing,
        standingLabel = when (e.state) {
            EntryState.CONFIRMED -> "Confirmed"
            EntryState.DISPUTED -> "Rejected"
            EntryState.NEEDS_OVERRIDE -> "Needs the third member"
            else -> "Waiting to be confirmed"
        },
        typeLabel = e.type.label(),
        member = displayName(e.memberId),
        account = e.accountId?.let { accountLabel(it) },
        recordedBy = displayName(e.recordedByMemberId ?: "?"),
        recordedWhen = e.recordedAt?.let { relativeTime(it, now) } ?: "unknown",
        confirmedBy = e.confirmedByMemberId?.let { displayName(it) },
        confirmedWhen = e.confirmedAt?.let { relativeTime(it, now) },
        rejectedBy = e.rejectedByMemberId?.let { displayName(it) },
        rejectionReason = e.rejectionReason,
        assurance = e.assurance,
        assuranceLabel = e.assurance?.label(),
        assuranceBlurb = e.assurance?.blurb(),
        recordedEvidence = e.recordedEvidence?.let { evidenceView(it) },
        confirmedEvidence = e.confirmedEvidence?.let { evidenceView(it) },
        conflict = e.conflict?.let { c ->
            ConflictView(
                kind = c.kind.label(),
                raisedBy = displayName(c.raisedBy),
                whenIt = c.at?.let { relativeTime(it, now) } ?: "unknown",
                reasons = c.reasons,
                note = c.note,
                settledBy = eligibleOverriders(e, memberIds(), config)
                    .map { MemberRef(it, displayName(it)) },
            )
        },
        overrides = e.overrides.map { o ->
            OverrideView(
                by = displayName(o.by),
                decision = o.decision.label(),
                reason = o.reason,
                whenIt = o.at?.let { relativeTime(it, now) } ?: "unknown",
                correctedAmount = o.correctedAmountCents?.let { formatKes(it) },
            )
        },
        correctsEntryId = e.correctsEntryId,
        correctedByEntryId = entries.firstOrNull { it.correctsEntryId == e.id }?.id,
        note = e.note,
        canReverse = e.state == EntryState.CONFIRMED &&
            entries.none { it.reversesEntryId == e.id },
        reversedByEntryId = entries.firstOrNull { it.reversesEntryId == e.id }?.id,
    )
}

// ── what the third member sees ───────────────────────────────────────────────

data class OverrideTask(
    val entryId: EntryId,
    val sentence: String,
    val amount: String,
    val amountCents: Long,
    val kind: String,
    val raisedBy: String,
    val recordedBy: String,
    val whenIt: String,
    val reasons: List<String>,
    val note: String?,
    val recordedEvidence: EvidenceView?,
    val attemptedEvidence: EvidenceView?,
    /** Exactly one member with three. Empty means nobody on this device may act. */
    val settledBy: List<MemberRef>,
    /** How many entries this one decision settles. A loan is three. */
    val entryCount: Int = 1,
    /** A correction needs a single figure, so a multi-leg act cannot offer one. */
    val canCorrect: Boolean = true,
)

fun LedgerBook.overrideTasks(config: ActorConfig, now: Instant? = null): List<OverrideTask> =
    overrideActs().map { act ->
        // The leg that carried the money is the one worth naming; the interest
        // and cost legs follow it and have no message of their own.
        val e = act.firstOrNull { it.recordedEvidence != null } ?: act.first()
        val c = e.conflict
        OverrideTask(
            // A grouped act is settled by its group id, exactly as it is confirmed.
            entryId = e.groupId ?: e.id,
            sentence = "${displayName(e.recordedByMemberId ?: "?")} recorded: " +
                "${e.type.label().lowercase()} — ${displayName(e.memberId)}",
            amount = formatKes(e.amountCents),
            amountCents = e.amountCents,
            kind = c?.kind?.label() ?: "Needs settling",
            raisedBy = displayName(c?.raisedBy ?: "?"),
            recordedBy = displayName(e.recordedByMemberId ?: "?"),
            whenIt = c?.at?.let { relativeTime(it, now) } ?: "recently",
            reasons = c?.reasons.orEmpty(),
            note = c?.note,
            recordedEvidence = e.recordedEvidence?.let { evidenceView(it) },
            attemptedEvidence = c?.attemptedEvidence?.let { evidenceView(it) },
            settledBy = eligibleOverriders(e, memberIds(), config)
                .map { MemberRef(it, displayName(it)) },
            entryCount = act.size,
            canCorrect = act.size == 1,
        )
    }

/** Whether the third member has anything waiting. Drives the home-screen card. */
fun LedgerBook.overrideCount(): Int = overrideActs().size

// ── you ──────────────────────────────────────────────────────────────────────

data class ProfileView(
    val name: String,
    val initial: String,
    val memberId: MemberId,
    val roleLine: String,
    val stake: String,
    val standingLine: String,
    val modeLine: String,
    val canActAs: List<MemberRef>,
    val recordedCount: Int,
    val confirmedCount: Int,
    val overrodeCount: Int,
    val storageLine: String,
    /** Which build this is. The only reliable way to know what is running. */
    val buildLine: String,
    /**
     * Whether this device may become somebody else.
     *
     * True only in a dev build. A production device holds one identity, and a
     * switcher on it would be the impersonation the whole design refuses.
     */
    val canSwitch: Boolean,
)

/**
 * Which app is asking.
 *
 * Core owns every sentence the user reads, including the one about where the
 * ledger is kept — and that sentence differs between a phone and a laptop. The
 * shell says which it is; core still says the words.
 */
enum class Shell { PHONE, DESKTOP }

fun LedgerBook.profile(
    actingAs: MemberId,
    config: ActorConfig,
    shell: Shell = Shell.PHONE,
): ProfileView {
    val card = memberCards().first { it.id == actingAs }
    return ProfileView(
        name = card.name,
        initial = card.initial,
        memberId = actingAs,
        roleLine = "Member of a ${founders().size}-person pool",
        stake = card.stake,
        standingLine = card.standingLine,
        modeLine = when (config.mode.name) {
            "DEV" ->
                "Dev build. This device can act as any member so one person can " +
                    "test both ends — the rule that they must be different people " +
                    "is still enforced."
            else -> "Live build. This device acts only as you."
        },
        canActAs = config.mayActAs.sorted().map { MemberRef(it, displayName(it)) },
        recordedCount = entries.count { it.recordedByMemberId == actingAs },
        confirmedCount = entries.count { it.confirmedByMemberId == actingAs },
        overrodeCount = entries.count { e -> e.overrides.any { it.by == actingAs } },
        storageLine = when (shell) {
            Shell.PHONE -> "Kept on this phone only. Nothing is sent anywhere, and no " +
                "phone number is stored."
            Shell.DESKTOP -> "Kept on this laptop, in the app's own folder. Nothing is " +
                "sent anywhere, and no phone number is stored."
        },
        buildLine = BuildInfo.label(),
        canSwitch = config.mayActAs.size > 1,
    )
}
