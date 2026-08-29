package online.vyybandasky.plus365.core.sms

import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.presentation.entryDetail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ziidi, read from real messages at last.
 *
 * These two came from Brian's phone and are the only Ziidi shapes anybody here
 * has actually seen. Everything in this file is checked against them literally,
 * character for character, because the whole reason Ziidi sat unparsed for so
 * long is that the previous format guessed at — KCB — went in untested and had
 * to be marked unverified afterwards.
 *
 * The transaction codes are real. They are transaction references, not secrets:
 * they identify a movement that already happened and grant nothing. They are
 * kept verbatim because a fixture that has been edited "to be safe" is a fixture
 * that stops testing the thing it was made from.
 */
class ZiidiParserTest {

    private val withdraw =
        "You have successfully withdrawn Ksh. 1,000.00 of transaction code UH21I1HQI9. " +
            "Your ZIIDI balance is Ksh. 17,707.74."

    private val invest =
        "You have successfully invested Ksh. 11,000.00 of transaction code UHL1I3NX68. " +
            "Your ZIIDI balance is Ksh. 11,001.07."

    private fun parsed(text: String) =
        assertIs<ParseOutcome.Parsed>(parseSms(text, DevSeed.BONNIE)).evidence

    // ── the two shapes, field by field ──────────────────────────────────────

    @Test
    fun a_withdrawal_is_read_in_full() {
        val e = parsed(withdraw)
        assertEquals(SmsProvider.ZIIDI, e.provider)
        assertEquals("UH21I1HQI9", e.reference)
        assertEquals(100_000L, e.amountCents, "Ksh 1,000.00")
        assertEquals(SmsDirection.SENT, e.direction, "withdrawing takes money out of Ziidi")
        assertEquals(1_770_774L, e.balanceAfterCents, "Ksh 17,707.74")
    }

    @Test
    fun an_investment_is_read_in_full() {
        val e = parsed(invest)
        assertEquals(SmsProvider.ZIIDI, e.provider)
        assertEquals("UHL1I3NX68", e.reference)
        assertEquals(1_100_000L, e.amountCents, "Ksh 11,000.00")
        assertEquals(SmsDirection.RECEIVED, e.direction, "investing puts money into Ziidi")
        assertEquals(1_100_107L, e.balanceAfterCents, "Ksh 11,001.07")
    }

    // ── the trap this shape sets ────────────────────────────────────────────

    @Test
    fun the_amount_is_the_transaction_and_never_the_balance() {
        // Both messages carry two figures. The investment is the dangerous one:
        // its amount and its balance are within a shilling of each other in the
        // first two digits, so a parser reading the wrong one might look right
        // for a long time.
        assertEquals(1_100_000L, parsed(invest).amountCents)
        assertTrue(
            parsed(invest).amountCents != parsed(invest).balanceAfterCents,
            "the amount and the balance came from the same figure",
        )
        assertEquals(100_000L, parsed(withdraw).amountCents)
        assertEquals(1_770_774L, parsed(withdraw).balanceAfterCents)
    }

    @Test
    fun the_verb_alone_decides_the_direction() {
        // "withdrawn" is in the generic SENT markers and "invested" is in
        // nothing, so before this slice the first message would have been read
        // as an ordinary outgoing payment and the second refused outright.
        assertEquals(SmsDirection.SENT, parsed(withdraw).direction)
        assertEquals(SmsDirection.RECEIVED, parsed(invest).direction)
    }

    // ── the standing rules still hold ───────────────────────────────────────

    @Test
    fun the_transaction_code_survives_redaction() {
        // The code is the entire point of the exercise. A redactor that ate it
        // would leave two members holding messages that cannot be matched.
        assertTrue("UH21I1HQI9" in parsed(withdraw).raw)
        assertTrue("UHL1I3NX68" in parsed(invest).raw)
    }

    @Test
    fun neither_message_trips_the_otp_filter() {
        // "transaction code" sits close to several OTP markers. If it ever
        // matched one, every real Ziidi message would be silently refused.
        for (text in listOf(withdraw, invest)) {
            assertIs<ParseOutcome.Parsed>(parseSms(text, DevSeed.BONNIE))
        }
    }

    @Test
    fun no_counterparty_is_invented() {
        // Ziidi names nobody, because there is nobody: the money moved between
        // two accounts the same person owns.
        assertNull(parsed(withdraw).counterparty)
        assertNull(parsed(invest).counterparty)
    }

    @Test
    fun a_ziidi_message_is_never_read_as_m_pesa() {
        assertEquals(SmsProvider.ZIIDI, parsed(withdraw).provider)
        assertEquals(SmsProvider.ZIIDI, parsed(invest).provider)
    }

    // ── and the shapes nobody has verified ──────────────────────────────────

    @Test
    fun an_unseen_ziidi_shape_is_still_admitted_to_be_unreadable() {
        // Two shapes were verified. A third is a guess, and this app does not
        // guess about money — it says so instead.
        val unseen = "Ziidi: your fund statement for August is ready. Balance Ksh. 11,001.07."
        val outcome = assertIs<ParseOutcome.Unmapped>(parseSms(unseen, DevSeed.BONNIE))
        assertEquals(SmsProvider.ZIIDI, outcome.provider)
    }

    @Test
    fun a_ziidi_move_with_no_code_is_refused_rather_than_half_read() {
        val noCode = "You have successfully invested Ksh. 11,000.00. Your ZIIDI balance is Ksh. 11,001.07."
        assertIs<ParseOutcome.Rejected>(parseSms(noCode, DevSeed.BONNIE))
    }

    // ── what it is evidence of ──────────────────────────────────────────────

    @Test
    fun an_investment_is_not_an_atm_withdrawal() {
        assertTrue(!parsed(invest).isAtmWithdrawal())
        assertTrue(
            !parsed(withdraw).isAtmWithdrawal(),
            "a Ziidi withdrawal is a transfer between the pool's own accounts, " +
                "not cash out of a machine",
        )
    }
}

/**
 * What a member is told when the message is recognised but not one of the
 * shapes that read.
 *
 * "365+ cannot read Ziidi messages" would be false — the two they actually paste
 * work — and a person told that would stop pasting the ones that do.
 */
class UnmappedMessageTest {

    private fun unmapped(text: String) =
        assertIs<ParseOutcome.Unmapped>(parseSms(text, DevSeed.BONNIE))

    @Test
    fun an_unknown_ziidi_notice_says_it_is_this_sentence_not_this_provider() {
        val m = unmapped(
            "Ziidi: your fund statement for August is ready. Balance Ksh. 11,001.07.",
        ).message()
        assertTrue("not one of the kinds" in m, m)
        assertTrue(
            "cannot read those yet" !in m,
            "this would tell a member Ziidi is unsupported when it is not: $m",
        )
    }

    @Test
    fun an_m_shwari_message_still_says_the_provider_is_unknown() {
        val m = unmapped(
            "M-Shwari: You have deposited Ksh1,000.00 to M-Shwari account. Balance Ksh8,000.00.",
        ).message()
        assertTrue("cannot read those yet" in m, m)
    }
}

/**
 * The balance the message reports reaches the screen.
 *
 * Extracted, stored, and shown nowhere would be the same gap this session has
 * closed a dozen times — a capability that may as well not exist.
 */
class ReportedBalanceTest {

    private val now = kotlinx.datetime.Instant.parse("2026-08-29T09:00:00Z")
    private val config = online.vyybandasky.plus365.core.governance.ActorConfig
        .dev(DevSeed.BONNIE, DevSeed.EVERYONE)

    @Test
    fun a_ziidi_entry_shows_what_the_account_then_held() {
        var s = online.vyybandasky.plus365.core.presentation.Session(
            book = online.vyybandasky.plus365.core.book.LedgerBook(
                members = DevSeed.MEMBERS,
                accounts = DevSeed.ACCOUNTS,
                pockets = DevSeed.POCKETS,
            ),
            config = config,
            actingAs = DevSeed.BONNIE,
        )
        s = s.moveMoney(
            DevSeed.POCHI, DevSeed.ZIIDI, 1_100_000, now,
            "You have successfully invested Ksh. 11,000.00 of transaction code UHL1I3NX68. " +
                "Your ZIIDI balance is Ksh. 11,001.07.",
        )
        val id = s.book.pending().single().id
        val detail = s.book.entryDetail(id, config, now)!!
        assertEquals("KSh 11,001.07", detail.recordedEvidence?.balanceAfter)
    }

    @Test
    fun an_m_pesa_entry_shows_no_such_line() {
        // M-Pesa prints a balance too and this parser has never taken it, so
        // there is nothing to show and the row must not appear empty.
        val e = assertIs<ParseOutcome.Parsed>(
            parseSms(
                "RTY4M8N2PQ Confirmed. Ksh500.00 sent to KANGIRI 0712345678 on 26/8/26 " +
                    "at 4:10 PM. New M-PESA balance is Ksh1,200.00.",
                DevSeed.BONNIE,
            ),
        ).evidence
        assertEquals(null, e.balanceAfterCents)
    }
}
