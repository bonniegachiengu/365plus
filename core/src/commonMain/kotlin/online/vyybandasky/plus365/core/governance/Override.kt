package online.vyybandasky.plus365.core.governance

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import online.vyybandasky.plus365.core.domain.Entry
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.domain.MemberId
import online.vyybandasky.plus365.core.sms.SmsEvidence

/**
 * When two members cannot settle an entry, the third one does.
 *
 * The two people involved in a transaction are exactly the two people with a
 * reason to want it settled their way, so neither of them gets to break the tie.
 * The overrider must be neither the recorder nor whoever tried to confirm it —
 * with three members that leaves exactly one person, and it is never a choice.
 *
 * Two-of-three arbitration, in other words: the involved pair cannot force a
 * disputed entry through, and the uninvolved member cannot be excluded from
 * settling it.
 *
 * Every override is written into the log with who, why and when. An override is
 * the most consequential thing anyone can do on this ledger — it settles a
 * disagreement about money — so it is the last thing that should be quiet.
 */

/** Why an entry stopped being settleable by the usual two. */
@Serializable
enum class ConflictKind {
    /** Two messages that did not describe one transaction. */
    EVIDENCE_MISMATCH,

    /** A member looked at it and disagreed with the entry itself. */
    DISPUTED,

    /** Raised for a correction rather than a disagreement. */
    CORRECTION_ASKED,
}

fun ConflictKind.label(): String = when (this) {
    ConflictKind.EVIDENCE_MISMATCH -> "Messages did not match"
    ConflictKind.DISPUTED -> "Disputed"
    ConflictKind.CORRECTION_ASKED -> "Correction asked for"
}

/**
 * The disagreement, kept whole.
 *
 * Including the message that failed to match. The third member cannot arbitrate
 * on a summary — they need to see what each side actually held.
 */
@Serializable
data class Conflict(
    val kind: ConflictKind,
    /** Who hit the wall. Barred from overriding, along with the recorder. */
    val raisedBy: MemberId,
    val at: Instant? = null,
    /** What did not line up, in the words the screen showed. */
    val reasons: List<String> = emptyList(),
    /** The message the confirmer offered, kept so the third member can read it. */
    val attemptedEvidence: SmsEvidence? = null,
    val note: String? = null,
)

/** What the third member decided. */
@Serializable
enum class OverrideDecision { CONFIRMED, REJECTED, CORRECTED }

fun OverrideDecision.label(): String = when (this) {
    OverrideDecision.CONFIRMED -> "Overridden to confirmed"
    OverrideDecision.REJECTED -> "Overridden to rejected"
    OverrideDecision.CORRECTED -> "Corrected"
}

/**
 * One override, as written into the log.
 *
 * A reason is not optional. An override without a stated reason is exactly the
 * kind of unexplained money decision this whole app exists to make impossible.
 */
@Serializable
data class OverrideRecord(
    val by: MemberId,
    val decision: OverrideDecision,
    val reason: String,
    val at: Instant? = null,
    /** Set on a correction: the amount the replacement entry was written for. */
    val correctedAmountCents: Long? = null,
    /** Set on a correction: the id of the entry that replaced this one. */
    val replacedByEntryId: String? = null,
)

/**
 * May [overrider] settle [entry]?
 *
 * The two bars are the point. Everything else here is ordinary validation.
 */
fun checkOverride(
    entry: Entry,
    overrider: MemberId,
    config: ActorConfig,
): Decision<Unit> {
    if (!config.mayAct(overrider)) {
        return Decision.Refused(Refusal.NotAuthorised(overrider))
    }
    if (entry.state != EntryState.NEEDS_OVERRIDE) {
        return Decision.Refused(
            Refusal.Invalid("Only an entry waiting on a third member can be overridden."),
        )
    }
    if (entry.recordedByMemberId == overrider) {
        return Decision.Refused(Refusal.InvolvedParty(overrider, "recorded it"))
    }
    val raisedBy = entry.conflict?.raisedBy
    if (raisedBy == overrider) {
        return Decision.Refused(Refusal.InvolvedParty(overrider, "raised the conflict"))
    }
    return Decision.Allowed(Unit)
}

/**
 * Who, of [members], may settle this. With three members this is exactly one.
 *
 * The screen uses it to route the conflict rather than to police a tap: the
 * person who can act should be told, and the two who cannot should not be shown
 * a button that will refuse them.
 */
fun eligibleOverriders(
    entry: Entry,
    members: List<MemberId>,
    config: ActorConfig,
): List<MemberId> = members.filter { checkOverride(entry, it, config) is Decision.Allowed }

/** The entry, marked as needing a third pair of eyes. */
internal fun Entry.asNeedingOverride(conflict: Conflict): Entry = copy(
    state = EntryState.NEEDS_OVERRIDE,
    conflict = conflict,
)

/** The entry, with one more override written onto its history. */
internal fun Entry.withOverride(record: OverrideRecord, newState: EntryState): Entry = copy(
    state = newState,
    overrides = overrides + record,
)
