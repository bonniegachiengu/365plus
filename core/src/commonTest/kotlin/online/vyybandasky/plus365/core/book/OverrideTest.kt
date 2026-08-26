package online.vyybandasky.plus365.core.book

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.domain.ConfirmSource
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.governance.ConflictKind
import online.vyybandasky.plus365.core.governance.Decision
import online.vyybandasky.plus365.core.governance.OverrideDecision
import online.vyybandasky.plus365.core.governance.Refusal
import online.vyybandasky.plus365.core.governance.eligibleOverriders
import online.vyybandasky.plus365.core.sms.Assurance
import online.vyybandasky.plus365.core.sms.ParseOutcome
import online.vyybandasky.plus365.core.sms.SmsEvidence
import online.vyybandasky.plus365.core.sms.parseSms

private val CONFIG = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)
private val T0 = Instant.parse("2026-08-26T08:00:00Z")

private const val SENT =
    "SK34H7T8QW Confirmed. Ksh2,000.00 sent to BRIAN 0712345678 on 26/8/26 at 10:15 AM."
private const val RECEIVED_WRONG =
    "ZZ99Z9Z9ZZ Confirmed. You have received Ksh2,000.00 from BONNIE 0798765432 on 26/8/26."

private fun ev(text: String, who: String): SmsEvidence =
    assertIs<ParseOutcome.Parsed>(parseSms(text, who)).evidence

private fun <T> Decision<T>.value(): T = assertIs<Decision.Allowed<T>>(this).value

/**
 * Two-of-three arbitration: the pair in the argument cannot settle it, and the
 * member outside it cannot be kept out.
 */
class OverrideTest {

    /** Bonnie records with a code; Brian's message does not match. */
    private fun inConflict(): LedgerBook {
        val recorded = LedgerBook(members = DevSeed.MEMBERS, accounts = DevSeed.ACCOUNTS).record(
            id = "e1",
            type = EntryType.CONTRIBUTION,
            amountCents = 200_000,
            memberId = DevSeed.BONNIE,
            recordedBy = DevSeed.BONNIE,
            config = CONFIG,
            evidence = ev(SENT, DevSeed.BONNIE),
            at = T0,
        ).value().book

        return recorded.confirmOrEscalate(
            "e1", DevSeed.BRIAN, CONFIG, at = T0, evidence = ev(RECEIVED_WRONG, DevSeed.BRIAN),
        ).value().book
    }

    // ── the fallout is routed, not dropped ───────────────────────────────────

    @Test
    fun a_failed_match_lands_with_the_third_member_rather_than_vanishing() {
        val b = inConflict()
        val e = b.entry("e1")!!

        assertEquals(EntryState.NEEDS_OVERRIDE, e.state)
        assertEquals(ConflictKind.EVIDENCE_MISMATCH, e.conflict!!.kind)
        assertEquals(DevSeed.BRIAN, e.conflict!!.raisedBy)
        assertTrue(e.conflict!!.reasons.isNotEmpty(), "the third member must see what failed")
        assertEquals(1, b.needingOverride().size)
    }

    @Test
    fun the_message_that_failed_is_kept_so_the_third_member_can_read_it() {
        val e = inConflict().entry("e1")!!
        assertEquals("SK34H7T8QW", e.recordedEvidence!!.reference)
        assertEquals("ZZ99Z9Z9ZZ", e.conflict!!.attemptedEvidence!!.reference)
    }

    @Test
    fun money_in_conflict_is_not_money() {
        val b = inConflict()
        assertEquals(0L, b.state().poolCashCents)
        assertEquals(200_000L, b.state().pendingPoolCashCents, "held apart, not ignored")
    }

    // ── who may settle it ────────────────────────────────────────────────────

    @Test
    fun with_three_members_exactly_one_person_can_settle_it() {
        val b = inConflict()
        val eligible = eligibleOverriders(b.entry("e1")!!, b.memberIds(), CONFIG)
        assertEquals(listOf(DevSeed.KANGIRI), eligible)
    }

    @Test
    fun the_recorder_cannot_settle_their_own_conflict() {
        val b = inConflict()
        val refused = assertIs<Decision.Refused>(
            b.override("e1", DevSeed.BONNIE, OverrideDecision.CONFIRMED, "mine", CONFIG, T0),
        )
        assertIs<Refusal.InvolvedParty>(refused.refusal)
    }

    @Test
    fun the_member_who_raised_it_cannot_settle_it_either() {
        val b = inConflict()
        val refused = assertIs<Decision.Refused>(
            b.override("e1", DevSeed.BRIAN, OverrideDecision.CONFIRMED, "trust me", CONFIG, T0),
        )
        assertIs<Refusal.InvolvedParty>(refused.refusal)
    }

    @Test
    fun neither_involved_party_can_force_it_through_between_them() {
        // The whole point. Two people arguing about money cannot resolve it by
        // one of them insisting, whichever of them insists.
        val b = inConflict()
        for (involved in listOf(DevSeed.BONNIE, DevSeed.BRIAN)) {
            for (decision in OverrideDecision.entries) {
                assertIs<Decision.Refused>(
                    b.override("e1", involved, decision, "because", CONFIG, T0, 100_00, "x"),
                )
            }
        }
        assertEquals(EntryState.NEEDS_OVERRIDE, b.entry("e1")!!.state)
    }

    // ── settling it ──────────────────────────────────────────────────────────

    @Test
    fun the_third_member_can_confirm_it_and_it_is_marked_as_arbitrated() {
        var b = inConflict()
        b = b.override("e1", DevSeed.KANGIRI, OverrideDecision.CONFIRMED, "I saw the transfer", CONFIG, T0)
            .value().book

        val e = b.entry("e1")!!
        assertEquals(EntryState.CONFIRMED, e.state)
        assertEquals(DevSeed.KANGIRI, e.confirmedByMemberId)
        assertEquals(ConfirmSource.OVERRIDE, e.confirmSource)
        assertEquals(
            Assurance.OVERRIDDEN,
            e.assurance,
            "never CODE_MATCHED — the codes are exactly what failed",
        )
        assertEquals(200_000L, b.state().poolCashCents)
    }

    @Test
    fun the_third_member_can_reject_it_and_nothing_moves() {
        var b = inConflict()
        b = b.override("e1", DevSeed.KANGIRI, OverrideDecision.REJECTED, "no such transfer", CONFIG, T0)
            .value().book

        assertEquals(EntryState.DISPUTED, b.entry("e1")!!.state)
        assertEquals(0L, b.state().poolCashCents)
        assertEquals(0L, b.state().pendingPoolCashCents)
    }

    @Test
    fun a_correction_appends_the_right_figure_and_keeps_the_wrong_one() {
        var b = inConflict()
        b = b.override(
            "e1", DevSeed.KANGIRI, OverrideDecision.CORRECTED,
            "it was 1,500 not 2,000", CONFIG, T0,
            correctedAmountCents = 150_000,
            replacementEntryId = "e1-corrected",
        ).value().book

        // The wrong one is closed but still there.
        assertEquals(EntryState.DISPUTED, b.entry("e1")!!.state)
        assertEquals(200_000L, b.entry("e1")!!.amountCents, "the original figure is not edited")

        // The right one is a new entry, joined to it.
        val fixed = b.entry("e1-corrected")!!
        assertEquals(150_000L, fixed.amountCents)
        assertEquals("e1", fixed.correctsEntryId)
        assertEquals(EntryState.CONFIRMED, fixed.state)
        assertEquals(Assurance.OVERRIDDEN, fixed.assurance)

        assertEquals(150_000L, b.state().poolCashCents, "only the corrected figure counts")
        assertEquals(2, b.entries.size)
    }

    // ── the audit trail ──────────────────────────────────────────────────────

    @Test
    fun every_override_is_written_into_the_log_with_who_and_why_and_when() {
        var b = inConflict()
        b = b.override("e1", DevSeed.KANGIRI, OverrideDecision.CONFIRMED, "I saw it land", CONFIG, T0)
            .value().book

        val record = b.entry("e1")!!.overrides.single()
        assertEquals(DevSeed.KANGIRI, record.by)
        assertEquals(OverrideDecision.CONFIRMED, record.decision)
        assertEquals("I saw it land", record.reason)
        assertEquals(T0, record.at)
    }

    @Test
    fun a_correction_records_the_amount_and_the_entry_that_replaced_it() {
        var b = inConflict()
        b = b.override(
            "e1", DevSeed.KANGIRI, OverrideDecision.CORRECTED, "wrong figure", CONFIG, T0,
            correctedAmountCents = 150_000, replacementEntryId = "e1-corrected",
        ).value().book

        val record = b.entry("e1")!!.overrides.single()
        assertEquals(150_000L, record.correctedAmountCents)
        assertEquals("e1-corrected", record.replacedByEntryId)
    }

    @Test
    fun an_override_without_a_reason_is_refused() {
        val b = inConflict()
        val refused = assertIs<Decision.Refused>(
            b.override("e1", DevSeed.KANGIRI, OverrideDecision.CONFIRMED, "   ", CONFIG, T0),
        )
        assertIs<Refusal.Invalid>(refused.refusal)
    }

    @Test
    fun an_entry_nobody_escalated_cannot_be_overridden() {
        val b = LedgerBook(members = DevSeed.MEMBERS).record(
            id = "e9", type = EntryType.CONTRIBUTION, amountCents = 100_00,
            memberId = DevSeed.BONNIE, recordedBy = DevSeed.BONNIE, config = CONFIG,
        ).value().book

        val refused = assertIs<Decision.Refused>(
            b.override("e9", DevSeed.KANGIRI, OverrideDecision.CONFIRMED, "why not", CONFIG, T0),
        )
        assertIs<Refusal.Invalid>(refused.refusal)
    }

    // ── escalating without a mismatch ───────────────────────────────────────

    @Test
    fun a_member_can_send_an_entry_they_simply_disagree_with_to_the_third() {
        var b = LedgerBook(members = DevSeed.MEMBERS).record(
            id = "e9", type = EntryType.CONTRIBUTION, amountCents = 100_00,
            memberId = DevSeed.BONNIE, recordedBy = DevSeed.BONNIE, config = CONFIG,
        ).value().book

        b = b.escalate("e9", DevSeed.BRIAN, ConflictKind.DISPUTED, CONFIG, note = "never happened", at = T0)
            .value().book

        assertEquals(EntryState.NEEDS_OVERRIDE, b.entry("e9")!!.state)
        assertEquals("never happened", b.entry("e9")!!.conflict!!.note)
        assertEquals(listOf(DevSeed.KANGIRI), eligibleOverriders(b.entry("e9")!!, b.memberIds(), CONFIG))
    }

    @Test
    fun noticing_your_own_mistake_is_allowed_but_still_bars_you_from_settling_it() {
        var b = LedgerBook(members = DevSeed.MEMBERS).record(
            id = "e9", type = EntryType.CONTRIBUTION, amountCents = 100_00,
            memberId = DevSeed.BONNIE, recordedBy = DevSeed.BONNIE, config = CONFIG,
        ).value().book

        b = b.escalate("e9", DevSeed.BONNIE, ConflictKind.CORRECTION_ASKED, CONFIG, at = T0).value().book
        assertEquals(EntryState.NEEDS_OVERRIDE, b.entry("e9")!!.state)

        // Recorder and raiser are the same person here, so two of three are out.
        val eligible = eligibleOverriders(b.entry("e9")!!, b.memberIds(), CONFIG)
        assertEquals(setOf(DevSeed.BRIAN, DevSeed.KANGIRI), eligible.toSet())
        assertTrue(DevSeed.BONNIE !in eligible)
    }

    // ── the plain refusals stay plain ───────────────────────────────────────

    @Test
    fun a_self_confirmation_is_still_just_refused_and_never_becomes_a_conflict() {
        // Trying to confirm your own entry is not a disagreement between two
        // people, so it must not land on the third member's desk.
        val b = LedgerBook(members = DevSeed.MEMBERS).record(
            id = "e1", type = EntryType.CONTRIBUTION, amountCents = 200_000,
            memberId = DevSeed.BONNIE, recordedBy = DevSeed.BONNIE, config = CONFIG,
            evidence = ev(SENT, DevSeed.BONNIE),
        ).value().book

        val refused = assertIs<Decision.Refused>(
            b.confirmOrEscalate("e1", DevSeed.BONNIE, CONFIG, evidence = ev(SENT, DevSeed.BONNIE)),
        )
        assertIs<Refusal.SelfConfirmation>(refused.refusal)
        assertEquals(EntryState.PENDING, b.entry("e1")!!.state)
        assertTrue(b.needingOverride().isEmpty())
    }

    @Test
    fun a_loan_whose_messages_clash_also_goes_to_the_third_member() {
        // AUDIT: a loan is three entries but one decision, and its money leg
        // carries the message. A clash there is the same kind of disagreement as
        // on a lone entry, so it must route the same way.
        var b = LedgerBook(members = DevSeed.MEMBERS, accounts = DevSeed.ACCOUNTS)
            .disburseLoan(
                loanId = "L-9",
                borrower = DevSeed.KANGIRI,
                principalCents = 200_000,
                recordedBy = DevSeed.BONNIE,
                config = CONFIG,
                evidence = ev(SENT, DevSeed.BONNIE),
                at = T0,
            ).value().book

        b = b.confirmGroupOrEscalate(
            "L-9", DevSeed.BRIAN, CONFIG, at = T0, evidence = ev(RECEIVED_WRONG, DevSeed.BRIAN),
        ).value().book

        assertEquals(2, b.needingOverride().size, "every leg of the loan goes together")
        assertTrue(b.pending().isEmpty(), "no leg is left half-settled")
        assertEquals(
            listOf(DevSeed.KANGIRI),
            eligibleOverriders(b.entry("L-9-principal")!!, b.memberIds(), CONFIG),
        )
    }

    @Test
    fun settling_a_loan_clears_every_leg_in_one_decision() {
        var b = LedgerBook(members = DevSeed.MEMBERS, accounts = DevSeed.ACCOUNTS)
            .disburseLoan(
                loanId = "L-9", borrower = DevSeed.KANGIRI, principalCents = 200_000,
                recordedBy = DevSeed.BONNIE, config = CONFIG,
                evidence = ev(SENT, DevSeed.BONNIE), at = T0,
            ).value().book
        b = b.confirmGroupOrEscalate(
            "L-9", DevSeed.BRIAN, CONFIG, at = T0, evidence = ev(RECEIVED_WRONG, DevSeed.BRIAN),
        ).value().book

        b = b.overrideGroup(
            "L-9", DevSeed.KANGIRI, OverrideDecision.CONFIRMED, "I saw it land", CONFIG, T0,
        ).value().book

        assertTrue(b.needingOverride().isEmpty())
        assertEquals(EntryState.CONFIRMED, b.entry("L-9-principal")!!.state)
        assertEquals(EntryState.CONFIRMED, b.entry("L-9-interest")!!.state)
        assertEquals(-214_000L, b.state().balanceOf(DevSeed.KANGIRI).debtCents)
    }

    @Test
    fun a_matching_pair_still_confirms_without_ever_involving_a_third_person() {
        val received =
            "SK34H7T8QW Confirmed. You have received Ksh2,000.00 from BONNIE 0798765432 on 26/8/26."
        var b = LedgerBook(members = DevSeed.MEMBERS).record(
            id = "e1", type = EntryType.CONTRIBUTION, amountCents = 200_000,
            memberId = DevSeed.BONNIE, recordedBy = DevSeed.BONNIE, config = CONFIG,
            evidence = ev(SENT, DevSeed.BONNIE),
        ).value().book

        b = b.confirmOrEscalate("e1", DevSeed.BRIAN, CONFIG, evidence = ev(received, DevSeed.BRIAN))
            .value().book

        assertEquals(EntryState.CONFIRMED, b.entry("e1")!!.state)
        assertEquals(Assurance.CODE_MATCHED, b.entry("e1")!!.assurance)
        assertTrue(b.needingOverride().isEmpty())
        assertNotNull(b.entry("e1")!!.confirmedEvidence)
    }
}
