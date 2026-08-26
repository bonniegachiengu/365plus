package online.vyybandasky.plus365.core.governance

import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.domain.ConfirmSource
import online.vyybandasky.plus365.core.domain.Entry
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.domain.MemberId
import online.vyybandasky.plus365.core.sms.Assurance
import online.vyybandasky.plus365.core.sms.SmsEvidence

/**
 * Two-person control: whoever records a transaction may not confirm it.
 *
 * This is the app's governance spine, not a guideline. Every path that moves an
 * entry to [EntryState.CONFIRMED] goes through [checkConfirm], and there is no
 * second path.
 *
 * The rule is enforced identically in every mode. What dev mode changes is not
 * the rule but *who may fill the roles*: on Bonnie's own device, while testing
 * alone, he is allowed to act as any member, so he can record as one and confirm
 * as another. The separation is in the code; the casting is in the config.
 */

/** Which build this is. Affects who may act, never whether the rule applies. */
enum class Mode { PRODUCTION, DEV }

/**
 * Which member identities the human holding this device may act as.
 *
 * In [Mode.PRODUCTION] that is exactly one — themselves. In [Mode.DEV] it may be
 * several, which is what lets one person exercise both ends of the control
 * without the control being switched off.
 */
data class ActorConfig(
    val mode: Mode,
    val deviceOwner: MemberId,
    val mayActAs: Set<MemberId>,
) {
    init {
        require(deviceOwner in mayActAs) {
            "the device owner must at least be able to act as themselves"
        }
        require(mode == Mode.DEV || mayActAs == setOf(deviceOwner)) {
            "only DEV may act as anyone other than the device owner; got $mayActAs"
        }
    }

    fun mayAct(as_: MemberId): Boolean = as_ in mayActAs

    companion object {
        /** The real thing: one device, one member, no impersonation. */
        fun production(owner: MemberId) =
            ActorConfig(Mode.PRODUCTION, owner, setOf(owner))

        /** Bonnie testing alone: he may stand in for the others locally. */
        fun dev(owner: MemberId, everyone: Set<MemberId>) =
            ActorConfig(Mode.DEV, owner, everyone + owner)
    }
}

/** Why an action was refused. Exhaustive so the UI can say something specific. */
sealed interface Refusal {
    val message: String

    /** The recorder tried to confirm their own entry. The whole point. */
    data class SelfConfirmation(val memberId: MemberId) : Refusal {
        override val message = "An entry cannot be confirmed by the member who recorded it."
    }

    data class NotAuthorised(val actingAs: MemberId) : Refusal {
        override val message = "This device may not act as $actingAs."
    }

    data class UnknownEntry(val entryId: String) : Refusal {
        override val message = "No entry $entryId in the book."
    }

    data class UnknownMember(val memberId: MemberId) : Refusal {
        override val message = "No member $memberId in the book."
    }

    data class NotPending(val state: EntryState) : Refusal {
        override val message = "Only a PENDING entry can be confirmed; this one is $state."
    }

    data class Invalid(override val message: String) : Refusal

    /**
     * Two messages that do not describe one transaction. Carries what did not
     * line up, so the screen can say exactly which part failed rather than a
     * bare "rejected".
     */
    data class EvidenceMismatch(
        val reasons: List<String>,
    ) : Refusal {
        override val message: String =
            "These two messages are not the same transaction. " + reasons.joinToString(" ")
    }

    /** A pasted message that could not be read, or must not be stored. */
    data class BadEvidence(override val message: String) : Refusal
}

/** The result of a governed action. */
sealed interface Decision<out T> {
    data class Allowed<T>(val value: T) : Decision<T>
    data class Refused(val refusal: Refusal) : Decision<Nothing>
}

/**
 * May [confirmer] confirm [entry]?
 *
 * The single gate. Note the check is against [Entry.recordedByMemberId] — the
 * member who recorded it — and not against the device, because in dev one device
 * legitimately carries several member identities.
 */
fun checkConfirm(
    entry: Entry,
    confirmer: MemberId,
    config: ActorConfig,
): Decision<Unit> {
    if (!config.mayAct(confirmer)) {
        return Decision.Refused(Refusal.NotAuthorised(confirmer))
    }
    if (entry.state != EntryState.PENDING) {
        return Decision.Refused(Refusal.NotPending(entry.state))
    }
    if (entry.recordedByMemberId == confirmer) {
        return Decision.Refused(Refusal.SelfConfirmation(confirmer))
    }
    return Decision.Allowed(Unit)
}

/**
 * May [rejecter] throw [entry] out?
 *
 * Exactly the same gate as confirming. Rejecting is a decision about someone
 * else's entry too, and letting the recorder quietly bin their own would be the
 * same hole from the other side.
 */
fun checkReject(
    entry: Entry,
    rejecter: MemberId,
    config: ActorConfig,
): Decision<Unit> = checkConfirm(entry, rejecter, config)

/** May [recorder] record at all from this device? */
fun checkRecord(recorder: MemberId, config: ActorConfig): Decision<Unit> =
    if (config.mayAct(recorder)) {
        Decision.Allowed(Unit)
    } else {
        Decision.Refused(Refusal.NotAuthorised(recorder))
    }

/**
 * Who, of [members], could confirm [entry] from this device right now.
 *
 * The UI uses this to offer a confirmer rather than letting someone tap and then
 * be told no — the rule shapes the interface instead of merely policing it.
 */
fun eligibleConfirmers(
    entry: Entry,
    members: List<MemberId>,
    config: ActorConfig,
): List<MemberId> = members.filter { candidate ->
    checkConfirm(entry, candidate, config) is Decision.Allowed
}

/**
 * The rejected copy of [entry].
 *
 * Rejection moves it to [EntryState.DISPUTED], which the fold ignores entirely —
 * so a rejected entry never touched a balance and never will, but it stays in
 * the log. Nothing is deleted here either.
 */
internal fun Entry.asRejectedBy(
    rejecter: MemberId,
    at: Instant?,
    reason: String?,
): Entry = copy(
    state = EntryState.DISPUTED,
    rejectedByMemberId = rejecter,
    rejectedAt = at,
    rejectionReason = reason,
)

/**
 * The confirmed copy of [entry].
 *
 * Private to the book: the only way to reach it is through a passed [Decision]
 * that was already allowed, so a caller cannot confirm without checking.
 */
internal fun Entry.asConfirmedBy(
    confirmer: MemberId,
    source: ConfirmSource,
    at: Instant? = null,
    evidence: SmsEvidence? = null,
    assurance: Assurance = Assurance.ATTESTED,
): Entry = copy(
    state = EntryState.CONFIRMED,
    confirmedByMemberId = confirmer,
    confirmedAt = at,
    confirmSource = source,
    confirmedEvidence = evidence,
    assurance = assurance,
)
