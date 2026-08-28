package online.vyybandasky.plus365.core.book

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.governance.Decision
import online.vyybandasky.plus365.core.governance.Refusal
import online.vyybandasky.plus365.core.sms.Assurance
import online.vyybandasky.plus365.core.sms.ParseOutcome
import online.vyybandasky.plus365.core.sms.SmsEvidence
import online.vyybandasky.plus365.core.sms.parseSms

private val CONFIG = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)

private const val SENT =
    "SK34H7T8QW Confirmed. Ksh2,000.00 sent to BRIAN OMONDI 0712345678 on 26/8/26 " +
        "at 10:15 AM. New M-PESA balance is Ksh3,000.00."

private const val RECEIVED =
    "SK34H7T8QW Confirmed. You have received Ksh2,000.00 from BONNIE GACHIENGU " +
        "0798765432 on 26/8/26 at 10:15 AM. New M-PESA balance is Ksh5,000.00."

private fun ev(text: String, who: String): SmsEvidence =
    assertIs<ParseOutcome.Parsed>(parseSms(text, who)).evidence

private fun freshBook() = LedgerBook(members = DevSeed.MEMBERS, accounts = DevSeed.ACCOUNTS)

private fun <T> Decision<T>.value(): T = assertIs<Decision.Allowed<T>>(this).value

/**
 * The paste-and-match control, end to end through the book's two doors.
 */
class EvidenceTest {

    private fun recorded(): LedgerBook = freshBook().record(
        id = "e1",
        type = EntryType.CONTRIBUTION,
        amountCents = 200_000,
        memberId = DevSeed.BONNIE,
        recordedBy = DevSeed.BONNIE,
        config = CONFIG,
        evidence = ev(SENT, DevSeed.BONNIE),
    ).value().book

    @Test
    fun a_matching_pair_confirms_the_entry_and_marks_it_code_matched() {
        var b = recorded()
        assertEquals(0L, b.state().poolCashCents, "still pending")

        b = b.confirm("e1", DevSeed.BRIAN, CONFIG, evidence = ev(RECEIVED, DevSeed.BRIAN)).value().book

        assertEquals(EntryState.CONFIRMED, b.entry("e1")!!.state)
        assertEquals(Assurance.CODE_MATCHED, b.entry("e1")!!.assurance)
        assertEquals(200_000L, b.state().poolCashCents)
    }

    @Test
    fun both_messages_are_kept_on_the_entry() {
        var b = recorded()
        b = b.confirm("e1", DevSeed.BRIAN, CONFIG, evidence = ev(RECEIVED, DevSeed.BRIAN)).value().book

        val e = b.entry("e1")!!
        assertEquals("SK34H7T8QW", e.recordedEvidence!!.reference)
        assertEquals("SK34H7T8QW", e.confirmedEvidence!!.reference)
        assertEquals(DevSeed.BONNIE, e.recordedEvidence!!.pastedBy)
        assertEquals(DevSeed.BRIAN, e.confirmedEvidence!!.pastedBy)
    }

    @Test
    fun an_entry_recorded_with_a_message_cannot_be_waved_through_without_one() {
        // The whole point of the refinement: a second member is no longer enough
        // on its own. They must have their own half of the transaction.
        val b = recorded()
        val refused = assertIs<Decision.Refused>(b.confirm("e1", DevSeed.BRIAN, CONFIG))
        assertIs<Refusal.BadEvidence>(refused.refusal)
        assertEquals(EntryState.PENDING, b.entry("e1")!!.state)
        assertEquals(0L, b.state().poolCashCents)
    }

    @Test
    fun a_different_code_is_refused_and_says_which_codes_did_not_line_up() {
        val b = recorded()
        val other = ev(RECEIVED.replace("SK34H7T8QW", "ZZ99Z9Z9Z9"), DevSeed.BRIAN)

        val refused = assertIs<Decision.Refused>(b.confirm("e1", DevSeed.BRIAN, CONFIG, evidence = other))
        val mismatch = assertIs<Refusal.EvidenceMismatch>(refused.refusal)
        assertTrue(mismatch.message.contains("SK34H7T8QW"))
        assertTrue(mismatch.message.contains("ZZ99Z9Z9Z9"))
        assertEquals(0L, b.state().poolCashCents, "a refused confirm moves nothing")
    }

    @Test
    fun a_different_amount_is_refused_even_when_the_code_matches() {
        val b = recorded()
        val other = ev(RECEIVED.replace("Ksh2,000.00", "Ksh2,500.00"), DevSeed.BRIAN)
        val refused = assertIs<Decision.Refused>(b.confirm("e1", DevSeed.BRIAN, CONFIG, evidence = other))
        assertIs<Refusal.EvidenceMismatch>(refused.refusal)
    }

    @Test
    fun forwarding_the_recorders_message_to_the_confirmer_does_not_work() {
        val b = recorded()
        val forwarded = ev(SENT, DevSeed.BRIAN) // Brian pastes Bonnie's text
        val refused = assertIs<Decision.Refused>(b.confirm("e1", DevSeed.BRIAN, CONFIG, evidence = forwarded))
        val mismatch = assertIs<Refusal.EvidenceMismatch>(refused.refusal)
        assertTrue(mismatch.message.contains("side"), mismatch.message)
    }

    @Test
    fun the_confirmer_must_have_pasted_their_own_message() {
        val b = recorded()
        // Evidence attributed to Bonnie, offered while confirming as Brian.
        val notTheirs = ev(RECEIVED, DevSeed.BONNIE)
        val refused = assertIs<Decision.Refused>(b.confirm("e1", DevSeed.BRIAN, CONFIG, evidence = notTheirs))
        assertIs<Refusal.BadEvidence>(refused.refusal)
    }

    @Test
    fun the_recorder_still_cannot_confirm_even_holding_both_messages() {
        // Dev mode lets Bonnie act as anyone, so this is the case that matters:
        // evidence does not replace the two-person rule, it stacks on top of it.
        val b = recorded()
        val refused = assertIs<Decision.Refused>(
            b.confirm("e1", DevSeed.BONNIE, CONFIG, evidence = ev(RECEIVED, DevSeed.BONNIE)),
        )
        assertIs<Refusal.SelfConfirmation>(refused.refusal)
    }

    @Test
    fun one_code_cannot_back_two_entries() {
        val b = recorded()
        val refused = assertIs<Decision.Refused>(
            b.record(
                id = "e2",
                type = EntryType.CONTRIBUTION,
                amountCents = 200_000,
                memberId = DevSeed.BONNIE,
                recordedBy = DevSeed.BONNIE,
                config = CONFIG,
                evidence = ev(SENT, DevSeed.BONNIE),
            ),
        )
        assertIs<Refusal.BadEvidence>(refused.refusal)
        assertTrue(refused.refusal.message.contains("SK34H7T8QW"))
    }

    @Test
    fun the_recorder_must_paste_their_own_message_too() {
        val refused = assertIs<Decision.Refused>(
            freshBook().record(
                id = "e1",
                type = EntryType.CONTRIBUTION,
                amountCents = 200_000,
                memberId = DevSeed.BONNIE,
                recordedBy = DevSeed.BONNIE,
                config = CONFIG,
                evidence = ev(SENT, DevSeed.BRIAN),
            ),
        )
        assertIs<Refusal.BadEvidence>(refused.refusal)
    }

    // ── the fallback, so the app is never blocked ────────────────────────────

    @Test
    fun an_entry_with_no_message_falls_back_to_a_plain_second_member() {
        var b = freshBook().record(
            id = "cash1",
            type = EntryType.CONTRIBUTION,
            amountCents = 50_000,
            memberId = DevSeed.KANGIRI,
            recordedBy = DevSeed.KANGIRI,
            config = CONFIG,
        ).value().book

        b = b.confirm("cash1", DevSeed.BRIAN, CONFIG).value().book

        assertEquals(EntryState.CONFIRMED, b.entry("cash1")!!.state)
        assertEquals(Assurance.ATTESTED, b.entry("cash1")!!.assurance)
        assertNull(b.entry("cash1")!!.recordedEvidence)
        assertEquals(50_000L, b.state().poolCashCents)
    }

    @Test
    fun the_fallback_is_still_two_people() {
        val b = freshBook().record(
            id = "cash1",
            type = EntryType.CONTRIBUTION,
            amountCents = 50_000,
            memberId = DevSeed.KANGIRI,
            recordedBy = DevSeed.KANGIRI,
            config = CONFIG,
        ).value().book

        val refused = assertIs<Decision.Refused>(b.confirm("cash1", DevSeed.KANGIRI, CONFIG))
        assertIs<Refusal.SelfConfirmation>(refused.refusal)
    }

    @Test
    fun the_two_assurance_levels_are_distinguishable_on_the_entry() {
        var b = recorded()
        b = b.confirm("e1", DevSeed.BRIAN, CONFIG, evidence = ev(RECEIVED, DevSeed.BRIAN)).value().book
        b = b.record(
            id = "cash1", type = EntryType.CONTRIBUTION, amountCents = 50_000,
            memberId = DevSeed.KANGIRI, recordedBy = DevSeed.KANGIRI, config = CONFIG,
        ).value().book
        b = b.confirm("cash1", DevSeed.BRIAN, CONFIG).value().book

        assertEquals(Assurance.CODE_MATCHED, b.entry("e1")!!.assurance)
        assertEquals(Assurance.ATTESTED, b.entry("cash1")!!.assurance)
    }

    // ── a loan: the money leg carries the message, the rest do not ───────────

    @Test
    fun a_loan_is_backed_by_one_message_for_the_principal_that_actually_moved() {
        var b = freshBook().disburseLoan(
            loanId = "L-9",
            borrower = DevSeed.KANGIRI,
            principalCents = 200_000,
            recordedBy = DevSeed.BONNIE,
            config = CONFIG,
            mpesaChargeCents = 3_300,
            evidence = ev(SENT, DevSeed.BONNIE),
        ).value().book

        assertEquals("SK34H7T8QW", b.entry("L-9-principal")!!.recordedEvidence!!.reference)
        assertNull(b.entry("L-9-interest")!!.recordedEvidence, "interest is owed, not transferred")
        assertNull(b.entry("L-9-mpesacharge")!!.recordedEvidence)

        b = b.confirmGroup("L-9", DevSeed.BRIAN, CONFIG, evidence = ev(RECEIVED, DevSeed.BRIAN)).value().book

        assertTrue(b.pending().isEmpty(), "one decision cleared all three legs")
        assertEquals(Assurance.CODE_MATCHED, b.entry("L-9-principal")!!.assurance)
        assertEquals(Assurance.ATTESTED, b.entry("L-9-interest")!!.assurance)
    }

    @Test
    fun a_loan_whose_money_leg_does_not_match_clears_nothing_at_all() {
        val b = freshBook().disburseLoan(
            loanId = "L-9",
            borrower = DevSeed.KANGIRI,
            principalCents = 200_000,
            recordedBy = DevSeed.BONNIE,
            config = CONFIG,
            evidence = ev(SENT, DevSeed.BONNIE),
        ).value().book

        val wrong = ev(RECEIVED.replace("SK34H7T8QW", "ZZ99Z9Z9Z9"), DevSeed.BRIAN)
        assertIs<Decision.Refused>(b.confirmGroup("L-9", DevSeed.BRIAN, CONFIG, evidence = wrong))
        assertEquals(2, b.pending().size, "nothing was cleared")
    }
}
