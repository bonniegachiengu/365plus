package online.vyybandasky.plus365.core.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.store.InMemoryStore

private val T0 = Instant.parse("2026-08-26T04:00:00Z")

class HomeTest {

    private val session = Session.restored(InMemoryStore(), T0)
    private val book = session.book

    // ── the hero ─────────────────────────────────────────────────────────────

    @Test
    fun the_hero_says_when_the_book_was_last_touched() {
        // A pool full of money and "no activity yet" underneath it reads as a
        // bug, because it is one — the seed must carry real times.
        val cash = book.cashOnHand(T0)
        assertFalse(cash.lastUpdated.contains("no activity"), cash.lastUpdated)
        assertTrue(cash.lastUpdated.startsWith("updated "), cash.lastUpdated)
    }

    @Test
    fun every_seeded_entry_knows_when_it_happened() {
        assertTrue(
            book.entries.all { it.recordedAt != null },
            "an entry from nowhere makes the screen lie about time",
        )
        assertTrue(book.entries.mapNotNull { it.recordedAt }.all { it <= T0 })
    }

    @Test
    fun the_hero_shows_the_roll_up_and_says_how_many_members() {
        val cash = book.cashOnHand(T0)
        assertEquals("KSh 4,644.00", cash.total)
        assertEquals("across 3 members", cash.memberCountLine)
    }

    @Test
    fun the_hero_counts_waiting_entries_as_acts_not_as_rows() {
        // The seed leaves one entry waiting. A loan would be three entries but
        // still one thing to decide, so the count must be of decisions.
        assertEquals("1 entry waiting to be confirmed", book.cashOnHand(T0).pendingLine)

        val withLoan = session.actAs(DevSeed.BRIAN).lend(DevSeed.KANGIRI, 200_000, at = T0).book
        assertEquals(3, withLoan.pending().size, "principal and interest, plus the seeded entry")
        assertEquals("2 entries waiting to be confirmed", withLoan.cashOnHand(T0).pendingLine)
    }

    @Test
    fun the_hero_says_nothing_about_pending_when_there_is_nothing_pending() {
        val waiting = book.pending().single()
        val cleared = session.confirmAct(waiting.id, DevSeed.BONNIE, T0).book
        assertNull(cleared.cashOnHand(T0).pendingLine)
    }

    // ── plain language ───────────────────────────────────────────────────────

    @Test
    fun a_waiting_act_reads_as_a_sentence_about_people_and_shillings() {
        val act = book.pendingActs(session.config, T0).single()
        assertEquals("Brian recorded: Kang'iri adds to the pool", act.sentence)
        assertEquals("KSh 500.00", act.amount)
    }

    @Test
    fun no_screen_text_leaks_an_id_or_a_type_name() {
        val loaded = session.actAs(DevSeed.BRIAN).lend(DevSeed.KANGIRI, 200_000, at = T0)
        val text = buildList {
            loaded.book.pendingActs(loaded.config, T0).forEach {
                add(it.sentence); add(it.amount); add(it.detail ?: ""); add(it.whenRecorded)
            }
            loaded.book.activity(T0).forEach { add(it.sentence); add(it.footnote) }
            loaded.book.memberCards().forEach { add(it.name); add(it.standingLine); add(it.stake) }
        }.joinToString(" ")

        for (leak in listOf("LOAN_OUT", "INTEREST_ACCRUAL", "TXN_COST", "CONTRIBUTION", "L-001", "evt-", "memberId")) {
            assertFalse(text.contains(leak), "screen text leaked \"$leak\"")
        }
    }

    @Test
    fun a_loan_asks_once_and_shows_what_will_actually_be_repaid() {
        // The Lend flow does not collect an M-Pesa cost, so a loan recorded from
        // the app is principal plus interest — two entries, still one decision.
        val s = session.actAs(DevSeed.BRIAN).lend(DevSeed.KANGIRI, 200_000, at = T0)
        val loanAct = s.book.pendingActs(s.config, T0).first { it.isGroup }

        assertEquals(2, loanAct.entryCount, "principal and interest")
        assertEquals("Brian recorded: lend to Kang'iri", loanAct.sentence)
        assertEquals("They repay KSh 2,140.00 in total", loanAct.detail)
    }

    @Test
    fun a_loan_carrying_an_mpesa_cost_is_three_legs_and_still_one_decision() {
        val s = session.actAs(DevSeed.BRIAN).lend(DevSeed.KANGIRI, 200_000, 3_300, at = T0)
        val loanAct = s.book.pendingActs(s.config, T0).first { it.isGroup }

        assertEquals(3, loanAct.entryCount, "principal, interest and cost")
        assertEquals("They repay KSh 2,173.00 in total", loanAct.detail)
        assertEquals(1, s.book.pendingActs(s.config, T0).count { it.isGroup })
    }

    @Test
    fun the_recorder_is_never_offered_as_a_confirmer_on_any_waiting_act() {
        val s = session.actAs(DevSeed.BRIAN).lend(DevSeed.KANGIRI, 200_000, at = T0)
        val acts = s.book.pendingActs(s.config, T0)
        assertTrue(acts.isNotEmpty())
        for (act in acts) {
            assertFalse(
                act.eligibleConfirmers.any { it.id == act.recordedById },
                "${act.sentence} offered its own recorder",
            )
            assertEquals(2, act.eligibleConfirmers.size)
        }
    }

    // ── members and activity ─────────────────────────────────────────────────

    @Test
    fun a_member_in_debt_reads_as_owing_shillings() {
        val kangiri = book.memberCards().first { it.id == DevSeed.KANGIRI }
        assertEquals("owes KSh 1,673.00", kangiri.standingLine)
        assertTrue(kangiri.inDebt)

        val bonnie = book.memberCards().first { it.id == DevSeed.BONNIE }
        assertEquals("clear", bonnie.standingLine)
        assertFalse(bonnie.inDebt)
    }

    @Test
    fun activity_shows_who_recorded_and_who_confirmed_each_entry() {
        val row = book.activity(T0).first { it.standing == Standing.CONFIRMED }
        assertTrue(row.footnote.contains("recorded"))
        assertTrue(row.footnote.contains("confirmed"))
    }

    @Test
    fun an_entry_stuck_in_a_dispute_does_not_look_like_one_merely_waiting() {
        // Money queued behind a second pair of eyes and money stuck in a
        // disagreement are different situations, and the list must say so.
        val stuck = book.activity(T0).first { it.entryId == "x1" }
        assertEquals(Standing.NEEDS_SETTLING, stuck.standing)
        assertTrue(stuck.footnote.contains("disagreed"), stuck.footnote)
        assertTrue(stuck.footnote.contains("third member"), stuck.footnote)

        val queued = book.activity(T0).first { it.entryId == "p1" }
        assertEquals(Standing.PENDING, queued.standing)
    }

    @Test
    fun the_ledger_shows_every_leg_while_the_home_summary_hides_the_noise() {
        val summary = book.activity(T0)
        val everything = book.activity(T0, everything = true)
        assertTrue(
            everything.size > summary.size,
            "the ledger must not quietly drop a loan's interest and cost",
        )
        assertTrue(everything.any { it.sentence.startsWith("Interest") })
        assertTrue(summary.none { it.sentence.startsWith("Interest") })
    }

    @Test
    fun a_waiting_entry_looks_different_from_a_settled_one() {
        val standings = book.activity(T0).map { it.standing }.toSet()
        assertTrue(Standing.PENDING in standings)
        assertTrue(Standing.CONFIRMED in standings)
    }

    @Test
    fun activity_hides_the_interest_and_cost_legs_that_would_only_be_noise() {
        val s = session.actAs(DevSeed.BRIAN).lend(DevSeed.KANGIRI, 200_000, at = T0)
        val sentences = s.book.activity(T0).map { it.sentence }
        assertFalse(sentences.any { it.startsWith("Interest") || it.startsWith("M-Pesa") })
    }

    // ── the quote shown before anyone commits ────────────────────────────────

    @Test
    fun the_loan_quote_spells_out_the_total_repayable() {
        val q = quoteLoan(200_000)
        assertEquals("KSh 2,000.00", q.principal)
        assertEquals("KSh 140.00", q.interest)
        assertEquals("KSh 2,140.00", q.totalRepayable)
        assertEquals("7.0% one-off charge", q.rateLabel)
    }

    // ── reject ───────────────────────────────────────────────────────────────

    @Test
    fun rejecting_leaves_the_entry_in_the_log_but_out_of_the_balances() {
        val waiting = book.pending().single()
        val before = book.state().poolCashCents

        val s = session.rejectAct(waiting.id, DevSeed.BONNIE, "not agreed", T0)

        assertEquals(before, s.book.state().poolCashCents, "a rejected entry never counted")
        assertEquals(EntryState.DISPUTED, s.book.entry(waiting.id)!!.state)
        assertEquals(DevSeed.BONNIE, s.book.entry(waiting.id)!!.rejectedByMemberId)
        assertEquals(book.entries.size, s.book.entries.size, "nothing was deleted")
        assertTrue(s.book.pending().isEmpty(), "it is no longer waiting")
    }

    @Test
    fun the_recorder_cannot_reject_their_own_entry_either() {
        val waiting = book.pending().single()
        val s = session.rejectAct(waiting.id, waiting.recordedByMemberId!!, at = T0)

        assertIs<Notice.Refused>(s.notice)
        assertEquals(EntryState.PENDING, s.book.entry(waiting.id)!!.state)
    }

    @Test
    fun rejecting_a_loan_throws_out_all_three_legs_together() {
        val s = session.actAs(DevSeed.BRIAN).lend(DevSeed.KANGIRI, 200_000, 3_300, at = T0)
        val loanAct = s.book.pendingActs(s.config, T0).first { it.isGroup }
        val debtBefore = s.book.state().balanceOf(DevSeed.KANGIRI).debtCents

        val after = s.rejectAct(loanAct.actId, DevSeed.BONNIE, at = T0)

        assertEquals(3, after.book.rejected().size, "all three legs went together")
        assertEquals(
            debtBefore,
            after.book.state().balanceOf(DevSeed.KANGIRI).debtCents,
            "a rejected loan never touched the debt, before or after",
        )
    }

    // ── time ─────────────────────────────────────────────────────────────────

    @Test
    fun time_reads_in_words_not_in_timestamps() {
        assertEquals("just now", relativeTime(T0, T0))
        assertEquals("5 min ago", relativeTime(T0, T0.plus(kotlin.time.Duration.parse("5m"))))
        assertEquals("3 h ago", relativeTime(T0, T0.plus(kotlin.time.Duration.parse("3h"))))
        assertEquals("2 days ago", relativeTime(T0, T0.plus(kotlin.time.Duration.parse("48h"))))
    }

    @Test
    fun a_recorded_entry_carries_the_time_it_was_recorded() {
        val s = session.actAs(DevSeed.BRIAN).contribute(DevSeed.KANGIRI, 100_000, at = T0)
        assertEquals(T0, s.book.pending().last().recordedAt)
        assertEquals("just now", s.book.pendingActs(s.config, T0).last().whenRecorded)
    }
}
