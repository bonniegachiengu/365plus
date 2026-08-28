package online.vyybandasky.plus365.core.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.book.needingOverride
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.governance.OverrideDecision
import online.vyybandasky.plus365.core.sms.Assurance
import online.vyybandasky.plus365.core.store.InMemoryStore

private val T0 = Instant.parse("2026-08-26T09:00:00Z")

/** Two halves of one transfer, parameterised so each flow gets its own code. */
private fun sent(code: String, shillings: Int) =
    "$code Confirmed. Ksh$shillings.00 sent to 365 POOL 0700000000 on 26/8/26 at 09:00 AM."

private fun received(code: String, shillings: Int) =
    "$code Confirmed. You have received Ksh$shillings.00 from A MEMBER 0700000001 on 26/8/26."

/**
 * Each action flow, all the way through: recorded, waiting, confirmed — and the
 * fallout path where the two involved cannot agree.
 *
 * These drive [Session] exactly as the screens do, so a flow that works here is
 * a flow that works in the app.
 */
class FlowsTest {

    private fun fresh() = Session(
        book = LedgerBook(members = DevSeed.MEMBERS, accounts = DevSeed.ACCOUNTS),
        config = DevSeed.DEV_CONFIG,
        actingAs = DevSeed.BONNIE,
    )

    /** Put money in the pool so lending has something to lend. */
    private fun funded(): Session {
        var s = fresh().actAs(DevSeed.BONNIE)
            .contribute(DevSeed.BONNIE, 1_000_000, T0, sent("FUND111AAA", 10_000))
        val id = s.book.pending().last().id
        s = s.confirmAct(id, DevSeed.BRIAN, T0, received("FUND111AAA", 10_000))
        return s
    }

    // ── contribute ───────────────────────────────────────────────────────────

    @Test
    fun contribute_goes_recorded_then_waiting_then_confirmed() {
        var s = fresh().actAs(DevSeed.KANGIRI)
            .contribute(DevSeed.KANGIRI, 50_000, T0, sent("CONT111AAA", 500))

        val id = s.book.pending().single().id
        assertEquals(EntryState.PENDING, s.book.entry(id)!!.state)
        assertEquals(0L, s.book.state().poolCashCents, "waiting money is not money")

        s = s.confirmAct(id, DevSeed.BRIAN, T0, received("CONT111AAA", 500))

        assertEquals(EntryState.CONFIRMED, s.book.entry(id)!!.state)
        assertEquals(Assurance.CODE_MATCHED, s.book.entry(id)!!.assurance)
        assertEquals(50_000L, s.book.state().poolCashCents)
        assertEquals(50_000L, s.book.state().balanceOf(DevSeed.KANGIRI).stakeCents)
    }

    // ── lend ─────────────────────────────────────────────────────────────────

    @Test
    fun lend_charges_the_founder_rate_and_confirms_as_one_act() {
        var s = funded().actAs(DevSeed.BRIAN)
            .lend(DevSeed.KANGIRI, 200_000, at = T0, smsText = sent("LEND111AAA", 2_000))

        val act = s.book.pendingActs(s.config, T0).single { it.isGroup }
        assertEquals(2, act.entryCount, "principal and interest")
        assertEquals("They repay KSh 2,100.00 in total", act.detail)

        s = s.confirmAct(act.actId, DevSeed.BONNIE, T0, received("LEND111AAA", 2_000))

        assertTrue(s.book.pending().isEmpty(), "one decision cleared the whole loan")
        assertEquals(-210_000L, s.book.state().balanceOf(DevSeed.KANGIRI).debtCents)
        assertEquals(210_000L, s.book.state().totalOutstandingCents)
        // Cash leaves for the principal only — the interest was never held.
        assertEquals(1_000_000L - 200_000L, s.book.state().poolCashCents)
    }

    // ── borrow ───────────────────────────────────────────────────────────────

    @Test
    fun borrow_is_the_same_event_seen_from_the_borrowers_side() {
        var s = funded().actAs(DevSeed.KANGIRI)
            .borrow(DevSeed.KANGIRI, 100_000, at = T0, smsText = sent("BORR111AAA", 1_000))

        val act = s.book.pendingActs(s.config, T0).single { it.isGroup }
        s = s.confirmAct(act.actId, DevSeed.BRIAN, T0, received("BORR111AAA", 1_000))

        // 1,000 + 50 interest at the founder rate.
        assertEquals(-105_000L, s.book.state().balanceOf(DevSeed.KANGIRI).debtCents)
    }

    // ── repay ────────────────────────────────────────────────────────────────

    @Test
    fun repay_comes_off_the_loan_it_names_and_shows_what_is_left() {
        var s = funded().actAs(DevSeed.BRIAN)
            .lend(DevSeed.KANGIRI, 200_000, at = T0, smsText = sent("LEND222AAA", 2_000))
        val loan = s.book.pendingActs(s.config, T0).single { it.isGroup }
        s = s.confirmAct(loan.actId, DevSeed.BONNIE, T0, received("LEND222AAA", 2_000))

        val repayable = s.book.repayableLoans().single()
        assertEquals("KSh 2,100.00", repayable.remaining)

        s = s.actAs(DevSeed.KANGIRI)
            .repay(repayable.loanId, DevSeed.KANGIRI, 50_000, T0, sent("REPY111AAA", 500))
        val id = s.book.pending().single().id
        s = s.confirmAct(id, DevSeed.BRIAN, T0, received("REPY111AAA", 500))

        assertEquals("KSh 1,600.00", s.book.repayableLoans().single().remaining)
        assertEquals(-160_000L, s.book.state().balanceOf(DevSeed.KANGIRI).debtCents)
    }

    @Test
    fun a_fully_repaid_loan_drops_off_the_repay_list() {
        var s = funded().actAs(DevSeed.BRIAN)
            .lend(DevSeed.KANGIRI, 100_000, at = T0, smsText = sent("LEND333AAA", 1_000))
        val loan = s.book.pendingActs(s.config, T0).single { it.isGroup }
        s = s.confirmAct(loan.actId, DevSeed.BONNIE, T0, received("LEND333AAA", 1_000))

        val id0 = s.book.repayableLoans().single().loanId
        s = s.actAs(DevSeed.KANGIRI).repay(id0, DevSeed.KANGIRI, 105_000, T0, sent("REPY222AAA", 1_050))
        val rid = s.book.pending().single().id
        s = s.confirmAct(rid, DevSeed.BRIAN, T0, received("REPY222AAA", 1_050))

        assertTrue(s.book.repayableLoans().isEmpty(), "nothing left owing on it")
        assertEquals(0L, s.book.state().totalOutstandingCents)
    }

    // ── the fallback: no message at all ──────────────────────────────────────

    @Test
    fun a_cash_contribution_with_no_message_still_completes_at_lower_assurance() {
        var s = fresh().actAs(DevSeed.KANGIRI).contribute(DevSeed.KANGIRI, 30_000, T0)
        val id = s.book.pending().single().id
        s = s.confirmAct(id, DevSeed.BRIAN, T0)

        assertEquals(EntryState.CONFIRMED, s.book.entry(id)!!.state)
        assertEquals(Assurance.ATTESTED, s.book.entry(id)!!.assurance)
        assertEquals(30_000L, s.book.state().poolCashCents)
    }

    // ── the fallout path, per flow ───────────────────────────────────────────

    @Test
    fun a_contribution_whose_messages_clash_reaches_the_third_member_and_is_settled() {
        var s = fresh().actAs(DevSeed.KANGIRI)
            .contribute(DevSeed.KANGIRI, 50_000, T0, sent("CLSH111AAA", 500))
        val id = s.book.pending().single().id

        s = s.confirmAct(id, DevSeed.BONNIE, T0, received("CLSH222BBB", 500))
        assertIs<Notice.Refused>(s.notice)
        assertEquals(EntryState.NEEDS_OVERRIDE, s.book.entry(id)!!.state)
        assertEquals(0L, s.book.state().poolCashCents)

        // Only Brian is left; the book refuses the other two.
        val task = s.book.overrideTasks(s.config, T0).single()
        assertEquals(listOf("Brian"), task.settledBy.map { it.name })

        s = s.overrideAct(id, DevSeed.BRIAN, OverrideDecision.CONFIRMED, "I saw it land", T0)
        assertEquals(EntryState.CONFIRMED, s.book.entry(id)!!.state)
        assertEquals(Assurance.OVERRIDDEN, s.book.entry(id)!!.assurance)
        assertEquals(50_000L, s.book.state().poolCashCents)
    }

    @Test
    fun a_loan_whose_messages_clash_moves_every_leg_and_settles_as_one() {
        var s = funded().actAs(DevSeed.BRIAN)
            .lend(DevSeed.KANGIRI, 200_000, at = T0, smsText = sent("CLSH333AAA", 2_000))
        val act = s.book.pendingActs(s.config, T0).single { it.isGroup }

        s = s.confirmAct(act.actId, DevSeed.BONNIE, T0, received("CLSH444BBB", 2_000))
        assertEquals(2, s.book.needingOverride().size, "no leg left half-settled")
        assertTrue(s.book.pending().isEmpty())

        val task = s.book.overrideTasks(s.config, T0).single()
        assertEquals(2, task.entryCount)
        assertTrue(!task.canCorrect, "a loan's interest follows its amount")
        assertEquals(listOf("Kang'iri"), task.settledBy.map { it.name })

        s = s.overrideAct(task.entryId, DevSeed.KANGIRI, OverrideDecision.CONFIRMED, "verified", T0)
        assertTrue(s.book.needingOverride().isEmpty())
        assertEquals(-210_000L, s.book.state().balanceOf(DevSeed.KANGIRI).debtCents)
    }

    @Test
    fun a_member_can_send_something_they_disagree_with_to_the_third_member() {
        var s = fresh().actAs(DevSeed.KANGIRI).contribute(DevSeed.KANGIRI, 50_000, T0)
        val id = s.book.pending().single().id

        s = s.escalateAct(id, DevSeed.BONNIE, note = "no such handover", at = T0)
        assertEquals(EntryState.NEEDS_OVERRIDE, s.book.entry(id)!!.state)

        s = s.overrideAct(id, DevSeed.BRIAN, OverrideDecision.REJECTED, "agreed, never happened", T0)
        assertEquals(EntryState.DISPUTED, s.book.entry(id)!!.state)
        assertEquals(0L, s.book.state().poolCashCents)
    }

    // ── the whole thing, persisted ───────────────────────────────────────────

    @Test
    fun a_full_lifecycle_survives_being_closed_and_reopened() {
        val store = InMemoryStore()
        var s = Session.restored(store, T0)
        val before = s.book.state().poolCashCents

        s = s.actAs(DevSeed.KANGIRI).contribute(DevSeed.KANGIRI, 50_000, T0, sent("PERS111AAA", 500))
        val id = s.book.pending().last().id
        s = s.confirmAct(id, DevSeed.BRIAN, T0, received("PERS111AAA", 500))
        store.write(online.vyybandasky.plus365.core.store.encodeBook(s.book))

        val reopened = Session.restored(store, T0)
        assertEquals(before + 50_000L, reopened.book.state().poolCashCents)
        assertEquals(Assurance.CODE_MATCHED, reopened.book.entry(id)!!.assurance)
        assertEquals("PERS111AAA", reopened.book.entry(id)!!.confirmedEvidence!!.reference)
    }

    // ── the ledger as a surface ──────────────────────────────────────────────

    @Test
    fun every_entry_ever_touched_stays_visible_in_the_ledger() {
        var s = fresh().actAs(DevSeed.KANGIRI).contribute(DevSeed.KANGIRI, 50_000, T0)
        val kept = s.book.pending().single().id
        s = s.confirmAct(kept, DevSeed.BRIAN, T0)

        s = s.actAs(DevSeed.KANGIRI).contribute(DevSeed.KANGIRI, 10_000, T0)
        val binned = s.book.pending().single().id
        s = s.escalateAct(binned, DevSeed.BONNIE, at = T0)
        s = s.overrideAct(binned, DevSeed.BRIAN, OverrideDecision.REJECTED, "not real", T0)

        val rows = s.book.activity(T0)
        assertEquals(2, rows.size, "a rejected entry is still in the record")
        assertTrue(rows.any { it.standing == Standing.CONFIRMED })
        assertTrue(rows.any { it.standing == Standing.REJECTED })
    }
}
