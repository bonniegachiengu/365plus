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

/**
 * Who sent the message.
 *
 * BANK covers a plain bank account — ATM withdrawals, card purchases, direct
 * debits and credits. It is here ahead of the account existing, because an ATM
 * cash-out is exactly the kind of movement that would otherwise leave the books
 * with an unexplained gap: money genuinely left, and the only record of it is
 * the text the bank sent.
 */
enum class SmsProvider {
    MPESA,
    KCB,
    BANK,

    /**
     * Safaricom's money-market fund. It does send confirmations — but their
     * exact wording is not known here yet, so nothing reads them. See
     * [ParseOutcome.Unmapped].
     */
    ZIIDI,

    /** Safaricom's savings product. Unmapped for the same reason. */
    MSHWARI,

    UNKNOWN,
}

fun SmsProvider.label(): String = when (this) {
    SmsProvider.MPESA -> "M-Pesa"
    SmsProvider.KCB -> "KCB"
    SmsProvider.BANK -> "your bank"
    SmsProvider.ZIIDI -> "Ziidi"
    SmsProvider.MSHWARI -> "M-Shwari"
    SmsProvider.UNKNOWN -> "an unrecognised sender"
}

/**
 * Providers we can recognise but cannot yet read.
 *
 * Kept as a list rather than guessed at. The last time a format was guessed —
 * KCB — the guess went in untested against a real message and had to be flagged
 * as unverified in the docs. Recognising a message and admitting it cannot be
 * read is worth more than parsing it wrongly and calling the result evidence.
 */
private val UNMAPPED_PROVIDERS = setOf(SmsProvider.ZIIDI, SmsProvider.MSHWARI)

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

    /**
     * We know who sent this and we cannot read it yet.
     *
     * Distinct from [Rejected] on purpose: rejected means the message is no good,
     * unmapped means *we* are not good enough yet. The member did nothing wrong,
     * the text is worth keeping, and it must never be counted as proof of
     * anything until the format is known.
     *
     * When a real Ziidi message arrives, that is the one place this changes:
     * teach [parseSms] the shape and this outcome stops being returned for it.
     */
    data class Unmapped(
        val provider: SmsProvider,
        /** Redacted the same way real evidence is: no numbers survive. */
        val raw: String,
    ) : ParseOutcome

    data class Rejected(val reason: RejectReason) : ParseOutcome
}

/** What to tell a member when their message is recognised but unreadable. */
fun ParseOutcome.Unmapped.message(): String =
    "This looks like a ${provider.label()} message. 365+ cannot read those yet — " +
        "the exact wording is still being confirmed. Record it by hand for now; " +
        "the message is kept so it can be matched later."

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

private val AMOUNT = Regex("""(?:ksh|kes|kshs)\s*\.?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)

/** Some banks put the currency after the figure: "2,000.00 KES has been ...". */
private val AMOUNT_TRAILING = Regex("""([\d,]+\.\d{2})\s*(?:ksh|kes|kshs)\b""", RegexOption.IGNORE_CASE)

/**
 * Numbers worth masking: mobile numbers, and bank account or card numbers.
 *
 * A bank message carries an account number the way an M-Pesa one carries a phone
 * number, and neither belongs in a file that syncs between three phones. The
 * reference code is the proof; the account number proves nothing.
 */
private val PHONE = Regex("""(?:\+?254|0)7\d{8}|\b\d{6,16}\b|\b(?:x|\*){2,}\d{2,6}\b""")

/**
 * A phone number or an account number, and nothing else.
 *
 * The narrow twin of [redactNumbers], for text a person typed rather than a
 * message a bank sent.
 *
 * The difference matters. An override reason is somebody explaining a decision
 * about money, and the explanation is frequently *"this should have been
 * 150000"* — running that through the message redactor removes the one figure
 * the sentence exists to record. So this takes Kenyan mobile numbers and runs of
 * ten or more digits, and leaves shorter runs alone on the grounds that they are
 * almost always amounts.
 *
 * That is a deliberate trade. A ten-digit amount would be redacted; a ten-digit
 * amount is KSh 10,000,000 and this pool does not have one.
 */
fun redactContactNumbers(text: String): String =
    CONTACT_NUMBER.replace(text) { m -> "*".repeat(m.value.length.coerceAtMost(12)) }

private val CONTACT_NUMBER = Regex("""(?:\+?254|0)7\d{8}|\b\d{10,16}\b""")

private val SENT_MARKERS = listOf(
    "sent to", "paid to", "you have sent", "debited", "withdrawn", "withdrawal",
    "buy goods", "atm", "cash withdrawal", "purchase at", "has been debited",
)
private val RECEIVED_MARKERS = listOf(
    "you have received", "received from", "credited", "deposited",
    "has been credited", "deposit of",
)

/**
 * Words that mean this came from a bank rather than a wallet.
 *
 * Deliberately generous — the account is not open yet, so this cannot be tuned
 * against a real message. Whatever it misses falls through to UNKNOWN, which
 * still parses if a code and an amount are there. Guessing the provider wrong
 * costs a label; refusing to read the message would cost the entry.
 */
private val BANK_MARKERS = listOf(
    "atm", "account ending", "a/c ", "acct", "account number", "available balance",
    "avail bal", "card ending", "your account", "bank", "current account",
    "savings account", "cash withdrawal",
)

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

    // Recognised, not readable. Bail out before pretending to understand it —
    // a half-read money message is worse than an admittedly unread one.
    if (provider in UNMAPPED_PROVIDERS) {
        return ParseOutcome.Unmapped(provider, redactNumbers(trimmed))
    }

    val amountCents = (
        AMOUNT.find(trimmed)?.groupValues?.get(1)
            ?: AMOUNT_TRAILING.find(trimmed)?.groupValues?.get(1)
        )?.let(::parseAmountToCents)
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
    // The unmapped ones first. A Ziidi message moves money through M-Pesa and
    // says so, so checking M-Pesa first would read it as an M-Pesa message and
    // pull out fields that mean something else.
    "ziidi" in lower -> SmsProvider.ZIIDI
    "m-shwari" in lower || "mshwari" in lower -> SmsProvider.MSHWARI
    // KCB next: a KCB M-Pesa message mentions both, and the bank is the one
    // that actually holds the money and prints the reference.
    "kcb" in lower -> SmsProvider.KCB
    "m-pesa" in lower || "mpesa" in lower -> SmsProvider.MPESA
    BANK_MARKERS.any { it in lower } -> SmsProvider.BANK
    else -> SmsProvider.UNKNOWN
}

/** Whether this message describes cash coming out of a machine. */
fun SmsEvidence.isAtmWithdrawal(): Boolean =
    provider != SmsProvider.MPESA &&
        direction == SmsDirection.SENT &&
        raw.contains("atm", ignoreCase = true)

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
    if (provider == SmsProvider.KCB || provider == SmsProvider.BANK) {
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
/**
 * Strip anything number-shaped from a pasted message.
 *
 * Aggressive on purpose — it takes any run of six or more digits — because
 * everything a message needs to prove has already been pulled into structured
 * fields by the time this runs. The reference, the amount and the direction are
 * kept; the raw text is only there to be read back by a person, and it can
 * afford to lose every number in it.
 *
 * Do not use this on text a person typed. See [redactContactNumbers].
 */
internal fun redactNumbers(text: String): String =
    PHONE.replace(text) { m ->
        // Keep the shape so the message still reads naturally, lose the number.
        "*".repeat(m.value.length.coerceAtMost(12))
    }
