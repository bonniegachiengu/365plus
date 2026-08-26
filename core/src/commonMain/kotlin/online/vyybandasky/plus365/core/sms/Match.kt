package online.vyybandasky.plus365.core.sms

import kotlinx.serialization.Serializable
import online.vyybandasky.plus365.core.money.formatKes

/**
 * Matching two members' messages about the same transaction.
 *
 * The point: M-Pesa and KCB print the same reference code on both parties'
 * messages. Two different people each holding a message with that code, for that
 * amount, seen from opposite sides, is evidence that one real transaction
 * happened. Neither of them can produce that alone.
 *
 * What this buys is not tidiness. It means an entry on this ledger is backed by
 * something outside the app, and that no member — including whoever wrote the
 * app — can invent one.
 */

/** How well an entry is backed. Shown wherever the entry is shown. */
@Serializable
enum class Assurance {
    /** Two members' messages, same code. The strong case. */
    CODE_MATCHED,

    /**
     * A second member vouched for it without a matching message — a cash
     * handover, or a transaction where only one side gets an SMS. Still
     * two-person, but a person's word rather than the network's receipt.
     */
    ATTESTED,
}

fun Assurance.label(): String = when (this) {
    Assurance.CODE_MATCHED -> "Codes matched"
    Assurance.ATTESTED -> "Confirmed by hand"
}

fun Assurance.blurb(): String = when (this) {
    Assurance.CODE_MATCHED ->
        "Both members' messages carry the same transaction code."
    Assurance.ATTESTED ->
        "No matching message — a second member vouched for this. Lower assurance."
}

/** What failed when two messages did not line up. */
enum class MismatchReason {
    REFERENCE,
    AMOUNT,
    SAME_SIDE,
    SAME_PASTER,
}

fun MismatchReason.explain(a: SmsEvidence, b: SmsEvidence): String = when (this) {
    MismatchReason.REFERENCE ->
        "Different transaction codes: ${a.reference} and ${b.reference}. These are two different transactions."
    MismatchReason.AMOUNT ->
        "Different amounts: ${formatKes(a.amountCents)} and ${formatKes(b.amountCents)}."
    MismatchReason.SAME_SIDE ->
        "Both messages are the ${a.direction.word()} side. One of you should have the other half."
    MismatchReason.SAME_PASTER ->
        "Both messages were pasted by the same person. The whole point is that they come from two."
}

private fun SmsDirection.word(): String = when (this) {
    SmsDirection.SENT -> "paid"
    SmsDirection.RECEIVED -> "received"
}

sealed interface MatchResult {
    /** Same code, same amount, two people, opposite sides. */
    data object Matched : MatchResult

    data class Mismatch(val reasons: List<MismatchReason>) : MatchResult
}

/**
 * Do these two messages describe one transaction, seen from both ends?
 *
 * Every check is required. In particular the two must disagree about direction:
 * if one member forwards their message to the other and both paste the same
 * text, both sides read the same way and this refuses it — which is exactly the
 * shortcut the control exists to close.
 */
fun matchEvidence(recorded: SmsEvidence, confirming: SmsEvidence): MatchResult {
    val reasons = buildList {
        if (!recorded.reference.equals(confirming.reference, ignoreCase = true)) {
            add(MismatchReason.REFERENCE)
        }
        if (recorded.amountCents != confirming.amountCents) {
            add(MismatchReason.AMOUNT)
        }
        if (recorded.direction == confirming.direction) {
            add(MismatchReason.SAME_SIDE)
        }
        if (recorded.pastedBy == confirming.pastedBy) {
            add(MismatchReason.SAME_PASTER)
        }
    }
    return if (reasons.isEmpty()) MatchResult.Matched else MatchResult.Mismatch(reasons)
}

/** A short line for the screen once a pair has matched. */
fun matchedSummary(a: SmsEvidence): String =
    "Code ${a.reference} · ${formatKes(a.amountCents)}"
