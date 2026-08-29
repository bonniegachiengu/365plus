package online.vyybandasky.plus365.core.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.store.InMemoryStore

private val T0 = Instant.parse("2026-08-29T13:00:00Z")

/**
 * The moves the book could always make but no screen offered.
 *
 * A capability with no screen may as well not exist — and the ledger page was
 * promising a correction the app had no way to write.
 */
class MovesTest {

    private fun session() = Session.restored(InMemoryStore(), T0)

    // ── correcting a confirmed entry ─────────────────────────────────────────

    @Test
    fun a_confirmed_entry_offers_a_reversal_and_a_waiting_one_does_not() {
        val s = session()
        val confirmed = s.book.confirmed().first()
        val waiting = s.book.pending().first()

        assertTrue(s.book.entryDetail(confirmed.id, s.config, T0)!!.canReverse)
        assertTrue(!s.book.entryDetail(waiting.id, s.config, T0)!!.canReverse)
    }

    @Test
    fun reversing_undoes_it_only_once_a_second_member_agrees() {
        var s = session()
        val target = s.book.confirmed().first { it.amountCents > 0 }
        val before = s.book.state().poolCashCents

        s = s.actAs(DevSeed.BONNIE).reverse(target.id, T0)
        assertEquals(before, s.book.state().poolCashCents, "a waiting reversal undoes nothing")

        val rev = s.book.pending().last()
        s = s.confirmAct(rev.id, DevSeed.KANGIRI, T0)

        assertEquals(EntryState.CONFIRMED, s.book.entry(rev.id)!!.state)
        // Both the entry and its correction remain in the record.
        assertEquals(EntryState.CONFIRMED, s.book.entry(target.id)!!.state)
        assertNotNull(s.book.entryDetail(target.id, s.config, T0)!!.reversedByEntryId)
    }

    @Test
    fun an_entry_can_only_be_reversed_once() {
        var s = session()
        val target = s.book.confirmed().first()
        s = s.actAs(DevSeed.BONNIE).reverse(target.id, T0)
        val rev = s.book.pending().last()
        s = s.confirmAct(rev.id, DevSeed.KANGIRI, T0)

        assertTrue(!s.book.entryDetail(target.id, s.config, T0)!!.canReverse)
    }

    // ── moving money between accounts ───────────────────────────────────────

    @Test
    fun moving_money_changes_where_it_is_and_not_what_it_is_for() {
        var s = session()
        val pocketsBefore = s.book.state().perPocket
        val cashBefore = s.book.state().cashAtHandCents

        s = s.actAs(DevSeed.BONNIE).moveMoney(DevSeed.ZIIDI, DevSeed.MSHWARI, 100_000, T0)
        val id = s.book.pending().last().id
        s = s.confirmAct(id, DevSeed.BRIAN, T0)

        assertEquals(cashBefore, s.book.state().cashAtHandCents, "a move creates nothing")
        assertEquals(100_000L, s.book.state().accountBalance(DevSeed.MSHWARI))
        assertEquals(pocketsBefore, s.book.state().perPocket, "the earmarking did not budge")
        assertTrue(s.book.state().balances)
    }

    @Test
    fun a_move_carries_the_time_it_was_recorded() {
        var s = session().actAs(DevSeed.BONNIE).moveMoney(DevSeed.ZIIDI, DevSeed.MSHWARI, 1_000, T0)
        assertEquals(T0, s.book.pending().last().recordedAt)
    }

    // ── earmarking ──────────────────────────────────────────────────────────

    @Test
    fun setting_money_aside_moves_no_money_and_still_needs_two_people() {
        var s = session()
        val accountsBefore = s.book.state().perAccount

        s = s.actAs(DevSeed.BONNIE).earmark(DevSeed.POOL, DevSeed.KESHFLO, 50_000, T0)
        val id = s.book.pending().last().id
        assertEquals(accountsBefore, s.book.state().perAccount)

        s = s.confirmAct(id, DevSeed.BRIAN, T0)
        assertEquals(accountsBefore, s.book.state().perAccount, "still not a shilling moved")
        assertEquals(200_000L, s.book.state().pocketBalance(DevSeed.KESHFLO))
        assertTrue(s.book.state().balances)
    }

    // ── interest an account paid ────────────────────────────────────────────

    @Test
    fun recording_interest_lifts_the_pool_once_agreed() {
        var s = session()
        val before = s.book.state().poolCashCents

        s = s.actAs(DevSeed.BRIAN).recordInterest(DevSeed.ZIIDI, 5_000, DevSeed.POOL, T0)
        assertEquals(before, s.book.state().poolCashCents, "not counted until agreed")

        val id = s.book.pending().last().id
        s = s.confirmAct(id, DevSeed.BONNIE, T0)
        assertEquals(before + 5_000L, s.book.state().poolCashCents)
        assertTrue(s.book.state().balances)
    }

    @Test
    fun interest_cannot_be_booked_against_an_account_that_does_not_earn() {
        val s = session().actAs(DevSeed.BRIAN).recordInterest(DevSeed.POCHI, 5_000, at = T0)
        assertIs<Notice.Refused>(s.notice)
    }

    @Test
    fun every_move_is_offered_with_words_a_member_would_use() {
        for (m in PoolMove.entries) {
            assertTrue(m.label.isNotBlank())
            assertTrue(m.blurb.isNotBlank())
            assertTrue(!m.blurb.contains("pocket") || m.blurb.contains("earmarked"))
        }
    }
}
