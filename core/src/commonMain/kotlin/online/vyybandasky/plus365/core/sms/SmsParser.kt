package online.vyybandasky.plus365.core.sms

import kotlinx.serialization.Serializable
import online.vyybandasky.plus365.core.domain.MemberId

/**
 * Reading a transaction SMS.
 *
 * 365+ has its own parser. It depends on nothing outside this module and knows
 * nothing about any other project.
 *
 * Three rules shape everything here:
 *
 *  * **A one-time code is never stored.** The OTP filter runs before anything
 *    else and before any part of the message is kept, so a message that looks
 *    like a secret is refused with nothing retained. Getting this wrong once —
 *    writing somebody's banking OTP into a ledger file that syncs between three
 *    phones — would be worse than every bug this app is meant to prevent.
 *  * **Phone numbers are redacted from what is stored.** The reference code is
 *    the proof, not the number. The app has no reason to accumulate the members'
 *    or their counterparties' numbers, and every reason not to.
 *  * **Parsing never throws.** A member pastes whatever their phone gave them.
 *    An unreadable message is an outcome to explain, not a crash.
 */

enum class SmsProvider { MPESA, KCB, UNKNOWN }

/** Which way the money moved, from the point of view of whoever got this SMS. */
enum class SmsDirection { SENT, RECEIVED }

/**
 * One member's own message about one transaction.
 *
 * The [reference] is the point of the whole exercise: M-Pesa and KCB print the
 * same code on both parties' messages, so two people holding messages with the
 * same code are holding evidence of one real transaction.
 */
@Serializable
data class SmsEvidence(
    /** The pasted message, with phone numbers masked. Never an OTP. */
    val raw: String,
    val provider: SmsProvider,
    /** The shared transaction code. Upper-cased and trimmed. */
    val reference: String,
    val amountCents: Long,
    val direction: SmsDirection,
    /** The other party's name as printed. Null when the format did not give one. */
    val counterparty: String? = null,
    /** The date/time exactly as printed. Not parsed — formats vary and core has no clock. */
    val occurredAtText: String? = null,
    /** Who pasted this. Half of what makes a pair a pair. */
    val pastedBy: MemberId,
)

/** Why a paste could not be used. */
enum class RejectReason {
    /** Looks like a one-time code or a secret. Nothing was stored. */
    LOOKS_LIKE_OTP,
    NO_REFERENCE,
    NO_AMOUNT,
    UNRECOGNISED,
    EMPTY,
    TOO_LONG,
}

fun RejectReason.message(): String = when (this) {
    RejectReason.LOOKS_LIKE_OTP ->
        "That looks like a one-time code. Never paste those here — nothing was saved."
    RejectReason.NO_REFERENCE ->
        "No transaction code found. Paste the whole message, including the code at the start."
    RejectReason.NO_AMOUNT -> "No amount found in that message."
    RejectReason.UNRECOGNISED -> "That does not look like an M-Pesa or KCB transaction message."
    RejectReason.EMPTY -> "Nothing was pasted."
    RejectReason.TOO_LONG -> "That is longer than a transaction message. Paste just the one message."
}

sealed interface ParseOutcome {
    data class Parsed(val evidence: SmsEvidence) : ParseOutcome
    data class Rejected(val reason: RejectReason) : ParseOutcome
}

/** A transaction SMS is a couple of hundred characters. Anything past this is not one. */
private const val MAX_SMS_LENGTH = 1_000

/**
 * Words that mean "this is a secret, not a receipt".
 *
 * Deliberately broad. A false positive costs a member one confused moment; a
 * false negative writes their banking credential into a file.
 */
private val OTP_MARKERS = listOf(
    "one-time", "one time", "onetime", "otp", "do not share", "don't share",
    "verification code", "verify code", "security code", "confirmation code",
    "your code is", "authentication", "2fa", "passcode", "activation code",
    "never share", "sharing this code",
)

/**
 * M-Pesa prints a ten-character code; KCB uses shorter, varied references.
 *
 * Case-insensitive, because a member may retype rather than paste. The candidate
 * must also carry a letter (see [hasLetter]): a Kenyan mobile number is ten
 * digits, so a digits-only pattern reads a phone number as a transaction code,
 * and then two members who had both texted the same person would appear to hold
 * matching evidence for a transfer that never happened.
 */
private val MPESA_REF = Regex("""\b([A-Za-z0-9]{10})\b""")
private val KCB_REF = Regex("""(?:ref|reference|txn|transaction)[\s:.#]*([A-Z0-9]{4,20})\b""", RegexOption.IGNORE_CASE)

private val AMOUNT = Regex("""(?:ksh|kes)\s*\.?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)

/** Kenyan mobile numbers in the shapes that appear in these messages. */
private val PHONE = Regex("""(?:\+?254|0)7\d{8}|\b\d{9,12}\b""")

private val SENT_MARKERS = listOf("sent to", "paid to", "you have sent", "debited", "withdrawn", "buy goods")
private val RECEIVED_MARKERS = listOf("you have received", "received from", "credited", "deposited")

private val COUNTERPARTY_TO = Regex("""(?:sent to|paid to)\s+([A-Za-z][A-Za-z .'\-]{1,40}?)(?=\s+(?:\+?254|0)7|\s+on\b|\s+for\b|[.,])""", RegexOption.IGNORE_CASE)
private val COUNTERPARTY_FROM = Regex("""(?:received)\s+(?:ksh|kes)?\s*[\d,.]*\s*from\s+([A-Za-z][A-Za-z .'\-]{1,40}?)(?=\s+(?:\+?254|0)7|\s+on\b|[.,])""", RegexOption.IGNORE_CASE)

private val WHEN = Regex("""on\s+(\d{1,2}[/-]\d{1,2}[/-]\d{2,4}(?:\s+at\s+\d{1,2}:\d{2}\s*(?:[AaPp][Mm])?)?)""")

/**
 * Read a pasted message.
 *
 * [pastedBy] is recorded on the evidence because a pair is only a pair when two
 * different people produced it.
 */
fun parseSms(text: String, pastedBy: MemberId): ParseOutcome {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return ParseOutcome.Rejected(RejectReason.EMPTY)
    if (trimmed.length > MAX_SMS_LENGTH) return ParseOutcome.Rejected(RejectReason.TOO_LONG)

    // First, always. Nothing below this line runs on a suspected secret.
    val lower = trimmed.lowercase()
    if (OTP_MARKERS.any { it in lower }) {
        return ParseOutcome.Rejected(RejectReason.LOOKS_LIKE_OTP)
    }

    val provider = detectProvider(lower)

    val amountCents = AMOUNT.find(trimmed)?.groupValues?.get(1)?.let(::parseAmountToCents)
        ?: return ParseOutcome.Rejected(RejectReason.NO_AMOUNT)

    val reference = findReference(trimmed, provider)
        ?: return ParseOutcome.Rejected(RejectReason.NO_REFERENCE)

    val direction = detectDirection(lower)
        ?: return ParseOutcome.Rejected(RejectReason.UNRECOGNISED)

    val counterparty = (COUNTERPARTY_TO.find(trimmed) ?: COUNTERPARTY_FROM.find(trimmed))
        ?.groupValues?.get(1)?.trim()?.trimEnd('.', ',')?.takeIf { it.isNotBlank() }

    return ParseOutcome.Parsed(
        SmsEvidence(
            raw = redactNumbers(trimmed),
            provider = provider,
            reference = reference,
            amountCents = amountCents,
            direction = direction,
            counterparty = counterparty,
            occurredAtText = WHEN.find(trimmed)?.groupValues?.get(1),
            pastedBy = pastedBy,
        ),
    )
}

private fun detectProvider(lower: String): SmsProvider = when {
    "m-pesa" in lower || "mpesa" in lower -> SmsProvider.MPESA
    "kcb" in lower -> SmsProvider.KCB
    else -> SmsProvider.UNKNOWN
}

private fun detectDirection(lower: String): SmsDirection? = when {
    RECEIVED_MARKERS.any { it in lower } -> SmsDirection.RECEIVED
    SENT_MARKERS.any { it in lower } -> SmsDirection.SENT
    else -> null
}

/**
 * M-Pesa's code is a bare ten-character token, usually first. KCB labels its
 * reference, so look for the label before falling back to the M-Pesa shape.
 */
private fun findReference(text: String, provider: SmsProvider): String? {
    if (provider == SmsProvider.KCB) {
        KCB_REF.find(text)?.groupValues?.get(1)?.takeIf(::hasLetter)?.let { return it.uppercase() }
    }
    MPESA_REF.findAll(text).map { it.groupValues[1] }.firstOrNull(::hasLetter)
        ?.let { return it.uppercase() }
    KCB_REF.find(text)?.groupValues?.get(1)?.takeIf(::hasLetter)?.let { return it.uppercase() }
    return null
}

/**
 * A transaction code always carries at least one letter. A phone number never
 * does, which is precisely why this check is the one keeping them apart.
 */
private fun hasLetter(candidate: String): Boolean = candidate.any { it.isLetter() }

/** "2,000.00" -> 200000. Integer only; no float goes near an amount. */
internal fun parseAmountToCents(raw: String): Long? {
    val cleaned = raw.replace(",", "").trim()
    val dot = cleaned.indexOf('.')
    return if (dot < 0) {
        cleaned.toLongOrNull()?.times(100)
    } else {
        val whole = cleaned.substring(0, dot).toLongOrNull() ?: return null
        val fraction = cleaned.substring(dot + 1).padEnd(2, '0').take(2).toLongOrNull() ?: return null
        whole * 100 + fraction
    }
}

/**
 * Mask anything number-shaped before the message is stored.
 *
 * The reference code is what proves the transaction; the phone number proves
 * nothing and is the one thing in the message worth not keeping. The ledger file
 * syncs between three phones, so whatever goes in it travels.
 */
internal fun redactNumbers(text: String): String =
    PHONE.replace(text) { m ->
        // Keep the shape so the message still reads naturally, lose the number.
        "*".repeat(m.value.length.coerceAtMost(12))
    }
