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
     * Safaricom's money-market fund.
     *
     * Two shapes are known and read: *invested* and *withdrawn*, from real
     * messages Brian supplied. Anything else Ziidi sends still comes back as
     * [ParseOutcome.Unmapped] — two verified shapes is what there is, and the
     * third one will be guessed at by nobody.
     */
    ZIIDI,

    /**
     * Safaricom's savings product. Unmapped, and staying that way.
     *
     * The group has no M-Shwari account and is not opening one. Recognising the
     * message and refusing to read it is the finished answer here, not a
     * placeholder for a parser somebody still owes.
     */
    MSHWARI,

    /**
     * The Etica money market fund, where the Keshflo A/C sits.
     *
     * Recognised so that a pasted Etica message is not mistaken for M-Pesa and
     * read with the wrong rules. Nobody has shown this code a real one, so
     * nothing is claimed about its shape.
     */
    ETICA,

    UNKNOWN,
}

fun SmsProvider.label(): String = when (this) {
    SmsProvider.MPESA -> "M-Pesa"
    SmsProvider.KCB -> "KCB"
    SmsProvider.BANK -> "your bank"
    SmsProvider.ZIIDI -> "Ziidi"
    SmsProvider.MSHWARI -> "M-Shwari"
    SmsProvider.ETICA -> "Etica"
    SmsProvider.UNKNOWN -> "an unrecognised sender"
}

/**
 * Providers we can recognise but cannot yet read.
 *
 * Kept as a list rather than guessed at. The last time a format was guessed —
 * KCB — the guess went in untested against a real message and had to be flagged
 * as unverified in the docs. Recognising a message and admitting it cannot be
 * read is worth more than parsing it wrongly and calling the result evidence.
 *
 * M-Shwari is here permanently rather than pending. The group has no M-Shwari
 * account and is not opening one (Bonnie, 30 Aug 2026), so no parser will ever
 * be written for it — and this list is how that is expressed. Taking it out
 * would not close the item; it would drop M-Shwari messages into the generic
 * M-Pesa path, where the general rules would read an unverified format and hand
 * back something that looks like evidence. "No parser" and "parse it with
 * somebody else's rules" are opposite things.
 *
 * Etica joins it for the ordinary reason: the Keshflo A/C sits there, it sends
 * messages, and nobody has shown one to this code yet.
 */
private val UNMAPPED_PROVIDERS = setOf(SmsProvider.MSHWARI, SmsProvider.ETICA)

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
    /**
     * The account balance the message reports afterwards, where it gives one.
     *
     * Ziidi prints it; M-Pesa prints one too but this parser has never taken it.
     * Kept because a savings account's own statement of where it landed is worth
     * more than an inference — it is how a member checks the ledger against the
     * account without opening the app twice.
     *
     * It is **not** evidence of anything by itself and no balance in this app is
     * ever taken from a message: cash-at-hand is the fold, always. This is a
     * figure to show a person, not a figure to compute with.
     */
    val balanceAfterCents: Long? = null,
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
    RejectReason.UNRECOGNISED ->
        "That does not look like a transaction message from M-Pesa, a bank, or Ziidi."
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
     * Ziidi is how this is meant to go. It sat here for weeks; two real messages
     * arrived; those two shapes are read now and every other Ziidi sentence
     * still lands here. Knowing two sentences a provider sends is not knowing
     * the provider, and the outcome does not become dishonest because some of
     * the provider is understood.
     */
    data class Unmapped(
        val provider: SmsProvider,
        /** Redacted the same way real evidence is: no numbers survive. */
        val raw: String,
    ) : ParseOutcome

    data class Rejected(val reason: RejectReason) : ParseOutcome
}

/**
 * What to tell a member when their message is recognised but unreadable.
 *
 * Two different situations, and telling a member the wrong one costs trust for
 * no reason:
 *
 *  * **A provider nothing is known about.** M-Shwari, Etica. "Cannot read those" is
 *    exactly right.
 *  * **A provider partly known.** Ziidi: its invest and withdraw messages read
 *    perfectly, and this is some other sentence it sends. Telling somebody 365+
 *    cannot read Ziidi messages, when the two they actually paste work, would be
 *    false and would stop them pasting the ones that do.
 */
fun ParseOutcome.Unmapped.message(): String = if (provider.isPartlyKnown()) {
    "This looks like a ${provider.label()} message, but not one of the kinds 365+ " +
        "knows. Its money-in and money-out messages are read; this is some other " +
        "notice. Record it by hand for now — the message is kept."
} else {
    "This looks like a ${provider.label()} message. 365+ cannot read those yet — " +
        "the exact wording is still being confirmed. Record it by hand for now; " +
        "the message is kept so it can be matched later."
}

/**
 * Whether some of this provider's messages are understood.
 *
 * Ziidi is the only one so far: two verified shapes out of however many it
 * sends. The distinction exists because "we cannot read this provider" and "we
 * cannot read this sentence" are different sentences to be told about your own
 * money.
 */
private fun SmsProvider.isPartlyKnown(): Boolean = this == SmsProvider.ZIIDI

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
/**
 * The two Ziidi shapes, from real messages.
 *
 *   You have successfully withdrawn Ksh. 1,000.00 of transaction code
 *   UH21I1HQI9. Your ZIIDI balance is Ksh. 17,707.74.
 *
 *   You have successfully invested Ksh. 11,000.00 of transaction code
 *   UHL1I3NX68. Your ZIIDI balance is Ksh. 11,001.07.
 *
 * Every field is anchored on the words around it rather than on position. The
 * message carries **two** amounts — the transaction and the resulting balance —
 * and a rule like "the first one" would read the balance as the amount the day
 * Ziidi reorders the sentence. Taking the verb and the amount from one match
 * makes it impossible for them to come from different halves of the message.
 */
private val ZIIDI_MOVE = Regex(
    """successfully\s+(withdrawn|invested)\s+(?:ksh|kes|kshs)\s*\.?\s*([\d,]+(?:\.\d{1,2})?)""",
    RegexOption.IGNORE_CASE,
)

private val ZIIDI_REF = Regex(
    """transaction\s+code\s+([A-Za-z0-9]{6,20})\b""",
    RegexOption.IGNORE_CASE,
)

private val ZIIDI_BALANCE = Regex(
    """ziidi\s+balance\s+is\s+(?:ksh|kes|kshs)\s*\.?\s*([\d,]+(?:\.\d{1,2})?)""",
    RegexOption.IGNORE_CASE,
)

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

    // Ziidi has its own two shapes and does not go through the generic path.
    // Its message carries two amounts and labels neither the way M-Pesa does, so
    // the general rules would read the wrong one.
    if (provider == SmsProvider.ZIIDI) {
        return parseZiidi(trimmed, pastedBy)
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

/**
 * Read a Ziidi confirmation, or admit it is a shape nobody has seen.
 *
 * ## Which way is "sent"
 *
 * [SmsDirection] is the direction *this message's account* moved, and this
 * message is about the Ziidi account:
 *
 *  * **invested** — money arrived in Ziidi. `RECEIVED`.
 *  * **withdrawn** — money left Ziidi. `SENT`.
 *
 * ## What it is evidence *of*
 *
 * Not income and not spending. Ziidi is one of the pool's own accounts, so
 * investing and withdrawing are **transfers between accounts the pool already
 * owns** — cash-at-hand cannot change, and treating an "invested" message as
 * money arriving would inflate the pool by the amount it just moved.
 *
 * The message reports only the Ziidi side. The matching M-Pesa leg arrives as
 * its own message, and the two together are the pair.
 *
 * ## Anything else
 *
 * Returned [ParseOutcome.Unmapped], exactly as before. Two shapes were verified
 * against real messages; a third would be a guess, and the last guessed format
 * had to be marked unverified in the docs afterwards.
 */
private fun parseZiidi(trimmed: String, pastedBy: MemberId): ParseOutcome {
    val move = ZIIDI_MOVE.find(trimmed)
        ?: return ParseOutcome.Unmapped(SmsProvider.ZIIDI, redactNumbers(trimmed))

    val verb = move.groupValues[1].lowercase()
    val amountCents = parseAmountToCents(move.groupValues[2])
        ?: return ParseOutcome.Rejected(RejectReason.NO_AMOUNT)

    val reference = ZIIDI_REF.find(trimmed)?.groupValues?.get(1)?.takeIf(::hasLetter)?.uppercase()
        ?: return ParseOutcome.Rejected(RejectReason.NO_REFERENCE)

    val direction = when (verb) {
        "invested" -> SmsDirection.RECEIVED
        "withdrawn" -> SmsDirection.SENT
        // Unreachable while the regex only offers those two, and a cheap way to
        // fail loudly rather than silently if a third verb is ever added above
        // without deciding which way it points.
        else -> return ParseOutcome.Unmapped(SmsProvider.ZIIDI, redactNumbers(trimmed))
    }

    return ParseOutcome.Parsed(
        SmsEvidence(
            raw = redactNumbers(trimmed),
            provider = SmsProvider.ZIIDI,
            reference = reference,
            amountCents = amountCents,
            direction = direction,
            counterparty = null,
            occurredAtText = WHEN.find(trimmed)?.groupValues?.get(1),
            balanceAfterCents = ZIIDI_BALANCE.find(trimmed)
                ?.groupValues?.get(1)
                ?.let(::parseAmountToCents),
            pastedBy = pastedBy,
        ),
    )
}

private fun detectProvider(lower: String): SmsProvider = when {
    // The fund providers first. A Ziidi message moves money through M-Pesa and
    // says so, so checking M-Pesa first would read it as an M-Pesa message and
    // pull out fields that mean something else — its reference is labelled
    // differently and it carries a second amount that is not the transaction.
    "ziidi" in lower -> SmsProvider.ZIIDI
    "m-shwari" in lower || "mshwari" in lower -> SmsProvider.MSHWARI
    "etica" in lower -> SmsProvider.ETICA
    // KCB next: a KCB M-Pesa message mentions both, and the bank is the one
    // that actually holds the money and prints the reference.
    "kcb" in lower -> SmsProvider.KCB
    "m-pesa" in lower || "mpesa" in lower -> SmsProvider.MPESA
    BANK_MARKERS.any { it in lower } -> SmsProvider.BANK
    else -> SmsProvider.UNKNOWN
}

/**
 * Whether this message is the only one anybody will ever have for it.
 *
 * Paste-and-match rests on an assumption that is true of an M-Pesa transfer and
 * false of several other real movements: that *two* people each receive their
 * own message carrying the same code. When that holds, requiring the second
 * message is what makes two-person control structural rather than procedural.
 *
 * It does not hold here:
 *
 *  * **Ziidi.** Both verified shapes move money between accounts the same person
 *    owns. There is no counterparty to receive anything, and the matching M-Pesa
 *    leg is a different transaction with a different code — so no second message
 *    with this code exists anywhere in the world.
 *  * **An ATM withdrawal.** Cash out of a machine has nobody on the other side.
 *
 * A rule that demands a message which cannot exist does not add safety. It makes
 * the entry unconfirmable, and the way round it is to record the movement with
 * no message at all — losing the proof *and* still ending up with a hand
 * confirmation. Strictly worse than admitting what this is.
 *
 * So these confirm by hand, and land as [Assurance.ATTESTED], which has said
 * "only one side gets an SMS" in its own documentation since the day it was
 * written.
 */
fun SmsEvidence.isOneSided(): Boolean =
    provider == SmsProvider.ZIIDI || isAtmWithdrawal()

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
