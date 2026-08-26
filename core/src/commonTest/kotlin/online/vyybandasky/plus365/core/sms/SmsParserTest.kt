package online.vyybandasky.plus365.core.sms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val BONNIE = "bonnie"
private const val BRIAN = "brian"

/** The two halves of one real-shaped M-Pesa transfer. */
private const val SENT_SMS =
    "SK34H7T8QW Confirmed. Ksh2,000.00 sent to BRIAN OMONDI 0712345678 on 26/8/26 " +
        "at 10:15 AM. New M-PESA balance is Ksh3,000.00. Transaction cost, Ksh23.00."

private const val RECEIVED_SMS =
    "SK34H7T8QW Confirmed. You have received Ksh2,000.00 from BONNIE GACHIENGU " +
        "0798765432 on 26/8/26 at 10:15 AM. New M-PESA balance is Ksh5,000.00."

private fun parsed(text: String, who: String): SmsEvidence =
    assertIs<ParseOutcome.Parsed>(parseSms(text, who)).evidence

class SmsParserTest {

    // ── the OTP filter, which runs before anything is kept ───────────────────

    @Test
    fun a_one_time_code_is_refused_and_nothing_is_stored() {
        val otp = "Your one-time password is 483920. Do not share it with anyone."
        val outcome = parseSms(otp, BONNIE)
        assertEquals(RejectReason.LOOKS_LIKE_OTP, assertIs<ParseOutcome.Rejected>(outcome).reason)
    }

    @Test
    fun every_shape_of_secret_message_is_caught() {
        val secrets = listOf(
            "Your OTP is 1234",
            "Verification code: 5566. Never share this code.",
            "Use passcode 9080 to complete your login",
            "Your confirmation code is 4321, do not share",
            "Security code 1111 for your account",
            "Your activation code is 7777",
        )
        for (text in secrets) {
            assertEquals(
                RejectReason.LOOKS_LIKE_OTP,
                assertIs<ParseOutcome.Rejected>(parseSms(text, BONNIE)).reason,
                "not caught: $text",
            )
        }
    }

    @Test
    fun the_otp_filter_runs_before_the_message_is_read_at_all() {
        // A message carrying both a code and a plausible amount must still be
        // refused. Cheaper to reject a real receipt than to store one secret.
        val mixed = "Your one-time code is 1234 for Ksh2,000.00 transfer SK34H7T8QW"
        assertEquals(
            RejectReason.LOOKS_LIKE_OTP,
            assertIs<ParseOutcome.Rejected>(parseSms(mixed, BONNIE)).reason,
        )
    }

    // ── reading a real transaction ───────────────────────────────────────────

    @Test
    fun the_sent_side_reads_out_in_full() {
        val e = parsed(SENT_SMS, BONNIE)
        assertEquals("SK34H7T8QW", e.reference)
        assertEquals(200_000L, e.amountCents)
        assertEquals(SmsDirection.SENT, e.direction)
        assertEquals(SmsProvider.MPESA, e.provider)
        assertEquals("BRIAN OMONDI", e.counterparty)
        assertEquals("26/8/26 at 10:15 AM", e.occurredAtText)
        assertEquals(BONNIE, e.pastedBy)
    }

    @Test
    fun the_received_side_reads_out_in_full() {
        val e = parsed(RECEIVED_SMS, BRIAN)
        assertEquals("SK34H7T8QW", e.reference)
        assertEquals(200_000L, e.amountCents)
        assertEquals(SmsDirection.RECEIVED, e.direction)
        assertEquals("BONNIE GACHIENGU", e.counterparty)
    }

    @Test
    fun a_kcb_message_reads_its_labelled_reference() {
        val kcb = "KCB: KES 1,500.00 has been credited to your account on 26/08/2026. " +
            "Ref: ABC123XY. Available balance KES 9,000.00"
        val e = parsed(kcb, BRIAN)
        assertEquals(SmsProvider.KCB, e.provider)
        assertEquals("ABC123XY", e.reference)
        assertEquals(150_000L, e.amountCents)
        assertEquals(SmsDirection.RECEIVED, e.direction)
    }

    @Test
    fun amounts_come_out_as_integer_cents() {
        assertEquals(200_000L, parseAmountToCents("2,000.00"))
        assertEquals(200_000L, parseAmountToCents("2000"))
        assertEquals(12_250L, parseAmountToCents("122.50"))
        assertEquals(5L, parseAmountToCents("0.05"))
        assertEquals(100L, parseAmountToCents("1.0"))
    }

    // ── what is stored ───────────────────────────────────────────────────────

    @Test
    fun phone_numbers_are_masked_before_the_message_is_stored() {
        val e = parsed(SENT_SMS, BONNIE)
        assertFalse(e.raw.contains("0712345678"), "a number survived into the stored message")
        assertTrue(e.raw.contains("BRIAN OMONDI"), "the name should survive; it is not a secret")
        assertTrue(e.raw.contains("SK34H7T8QW"), "the code is the proof and must survive")
    }

    @Test
    fun the_stored_message_keeps_enough_to_be_read_by_a_person() {
        val e = parsed(RECEIVED_SMS, BRIAN)
        assertTrue(e.raw.contains("received"))
        assertTrue(e.raw.contains("2,000.00"))
    }

    // ── refusals that are not secrets ────────────────────────────────────────

    @Test
    fun a_phone_number_is_never_mistaken_for_a_transaction_code() {
        // Ten digits looks exactly like an M-Pesa code to a naive pattern. If a
        // number could stand in as the shared reference, two members who had both
        // texted the same person would appear to hold matching evidence.
        val noCode = "Confirmed. Ksh2,000.00 sent to BRIAN 0712345678 on 26/8/26."
        assertEquals(
            RejectReason.NO_REFERENCE,
            assertIs<ParseOutcome.Rejected>(parseSms(noCode, BONNIE)).reason,
        )
    }

    @Test
    fun a_lowercase_code_still_reads_as_the_code_and_not_the_number() {
        val e = parsed(SENT_SMS.replace("SK34H7T8QW", "sk34h7t8qw"), BONNIE)
        assertEquals("SK34H7T8QW", e.reference)
    }

    @Test
    fun a_message_with_no_code_is_refused_by_name() {
        val outcome = parseSms("You have received Ksh500.00 from someone", BONNIE)
        assertEquals(RejectReason.NO_REFERENCE, assertIs<ParseOutcome.Rejected>(outcome).reason)
    }

    @Test
    fun a_message_with_no_amount_is_refused_by_name() {
        val outcome = parseSms("SK34H7T8QW Confirmed. Something happened.", BONNIE)
        assertEquals(RejectReason.NO_AMOUNT, assertIs<ParseOutcome.Rejected>(outcome).reason)
    }

    @Test
    fun an_empty_or_enormous_paste_is_refused_rather_than_parsed() {
        assertEquals(RejectReason.EMPTY, assertIs<ParseOutcome.Rejected>(parseSms("   ", BONNIE)).reason)
        assertEquals(
            RejectReason.TOO_LONG,
            assertIs<ParseOutcome.Rejected>(parseSms("x".repeat(2_000), BONNIE)).reason,
        )
    }

    @Test
    fun parsing_never_throws_whatever_is_pasted() {
        val junk = listOf("", "?!?!", "Ksh", "1234567890", "\n\n\n", "SK34H7T8QW", "ksh 5 sent to")
        for (text in junk) {
            // The assertion is that this line returns at all.
            parseSms(text, BONNIE)
        }
    }

    @Test
    fun every_refusal_can_be_explained_to_a_member() {
        for (reason in RejectReason.entries) {
            assertTrue(reason.message().isNotBlank())
        }
    }
}

class MatchTest {

    private val sent = parsed(SENT_SMS, BONNIE)
    private val received = parsed(RECEIVED_SMS, BRIAN)

    @Test
    fun two_sides_of_one_transaction_match() {
        assertEquals(MatchResult.Matched, matchEvidence(sent, received))
    }

    @Test
    fun a_different_code_is_a_different_transaction() {
        val other = parsed(RECEIVED_SMS.replace("SK34H7T8QW", "ZZ99Z9Z9Z9"), BRIAN)
        val m = assertIs<MatchResult.Mismatch>(matchEvidence(sent, other))
        assertTrue(MismatchReason.REFERENCE in m.reasons)
        assertTrue(m.reasons.first().explain(sent, other).contains("SK34H7T8QW"))
    }

    @Test
    fun a_different_amount_is_refused_even_with_the_same_code() {
        val other = parsed(RECEIVED_SMS.replace("Ksh2,000.00", "Ksh2,500.00"), BRIAN)
        val m = assertIs<MatchResult.Mismatch>(matchEvidence(sent, other))
        assertTrue(MismatchReason.AMOUNT in m.reasons)
    }

    @Test
    fun forwarding_your_own_message_to_the_other_member_does_not_work() {
        // The shortcut the whole control exists to close: if one person's message
        // is pasted at both ends, both read the same side.
        val forwarded = parsed(SENT_SMS, BRIAN)
        val m = assertIs<MatchResult.Mismatch>(matchEvidence(sent, forwarded))
        assertTrue(MismatchReason.SAME_SIDE in m.reasons)
    }

    @Test
    fun one_person_pasting_both_halves_is_refused() {
        val bothMine = parsed(RECEIVED_SMS, BONNIE)
        val m = assertIs<MatchResult.Mismatch>(matchEvidence(sent, bothMine))
        assertTrue(MismatchReason.SAME_PASTER in m.reasons)
    }

    @Test
    fun a_mismatch_names_every_thing_that_failed_not_just_the_first() {
        val wrong = parsed(
            SENT_SMS.replace("SK34H7T8QW", "ZZ99Z9Z9Z9").replace("Ksh2,000.00", "Ksh9,000.00"),
            BONNIE,
        )
        val m = assertIs<MatchResult.Mismatch>(matchEvidence(sent, wrong))
        assertTrue(MismatchReason.REFERENCE in m.reasons)
        assertTrue(MismatchReason.AMOUNT in m.reasons)
        assertTrue(MismatchReason.SAME_SIDE in m.reasons)
        assertTrue(MismatchReason.SAME_PASTER in m.reasons)
    }

    @Test
    fun the_code_comparison_ignores_case() {
        val lower = parsed(RECEIVED_SMS.replace("SK34H7T8QW", "sk34h7t8qw"), BRIAN)
        // The parser upper-cases, so this is really asserting normalisation.
        assertEquals("SK34H7T8QW", lower.reference)
        assertEquals(MatchResult.Matched, matchEvidence(sent, lower))
    }

    @Test
    fun every_mismatch_reason_can_be_explained_to_a_member() {
        for (reason in MismatchReason.entries) {
            assertTrue(reason.explain(sent, received).isNotBlank())
        }
    }

    @Test
    fun both_assurance_levels_read_plainly_and_differ() {
        assertTrue(Assurance.CODE_MATCHED.label().isNotBlank())
        assertTrue(Assurance.ATTESTED.blurb().contains("Lower assurance"))
        assertTrue(Assurance.CODE_MATCHED.blurb() != Assurance.ATTESTED.blurb())
    }

    @Test
    fun a_matched_pair_summarises_to_the_code_and_the_amount() {
        assertEquals("Code SK34H7T8QW · KSh 2,000.00", matchedSummary(sent))
    }

    @Test
    fun neither_stored_message_carries_a_phone_number() {
        assertNull(Regex("""\b07\d{8}\b""").find(sent.raw))
        assertNull(Regex("""\b07\d{8}\b""").find(received.raw))
    }
}
