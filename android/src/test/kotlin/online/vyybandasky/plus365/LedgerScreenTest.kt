package online.vyybandasky.plus365

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.presentation.Notice
import online.vyybandasky.plus365.core.presentation.Session
import online.vyybandasky.plus365.core.presentation.memberRows
import online.vyybandasky.plus365.core.presentation.pendingRows
import online.vyybandasky.plus365.core.presentation.summaryView

/**
 * The phone shell, exercised through exactly the calls the screens make.
 *
 * These are unit tests over the session, not Compose tests — the UI holds no
 * logic of its own, so driving the session is driving the screen.
 */
class LedgerScreenTest {

    @Test
    fun the_shell_opens_on_the_seeded_pool() {
        val s = Session.dev()
        val summary = s.book.summaryView()
        assertEquals("KSh 4,644.00", summary.cashAtHand)
        assertEquals("KSh 3,087.00", summary.totalOutstanding)
        assertEquals(2, summary.accounts.size, "savings and float")
        assertEquals(4, s.book.memberRows().size)
        assertEquals(DevSeed.BONNIE, s.actingAs)
    }

    @Test
    fun the_confirm_screen_never_offers_the_recorder_as_a_confirmer() {
        val s = Session.dev()
        val rows = s.book.pendingRows(s.config)
        assertTrue(rows.isNotEmpty())
        for (row in rows) {
            assertFalse(
                row.eligibleConfirmers.any { it.id == row.recordedById },
                "${row.entryId} offered its own recorder",
            )
        }
    }

    @Test
    fun recording_then_confirming_as_someone_else_moves_the_pool() {
        var s = Session.dev()
        val before = s.book.state().poolCashCents

        s = s.actAs(DevSeed.PINAH).record(EntryType.CONTRIBUTION, DevSeed.PINAH, 100_000)
        assertEquals(before, s.book.state().poolCashCents, "still pending")

        // The seed already leaves one entry pending, so take the newest — the one
        // this test just recorded — rather than the first Pinah happens to own.
        val waiting = s.book.pending().last()
        assertEquals(100_000L, waiting.amountCents)
        s = s.confirm(waiting.id, DevSeed.BONNIE)
        assertEquals(before + 100_000, s.book.state().poolCashCents)
    }

    @Test
    fun the_shell_refuses_a_self_confirm_and_says_why() {
        var s = Session.dev()
        s = s.actAs(DevSeed.PINAH).record(EntryType.CONTRIBUTION, DevSeed.PINAH, 100_000)
        val waiting = s.book.pending().last()

        val before = s.book.state().poolCashCents
        s = s.confirm(waiting.id, DevSeed.PINAH)

        assertTrue(s.notice is Notice.Refused)
        assertEquals(before, s.book.state().poolCashCents, "a refused confirm moves nothing")
    }

    @Test
    fun a_loan_recorded_from_the_shell_charges_seven_percent_and_keeps_the_cost_apart() {
        var s = Session.dev()
        s = s.actAs(DevSeed.PINAH).lend(DevSeed.BRIAN, 200_000, txnCostCents = 3_300)

        val interest = s.book.pending().first { it.type == EntryType.INTEREST_ACCRUAL }
        assertEquals(14_000L, interest.amountCents, "7% of 2,000 is 140")
        val cost = s.book.pending().first { it.type == EntryType.TXN_COST }
        assertEquals(3_300L, cost.amountCents, "the M-Pesa cost is its own entry")
    }

    @Test
    fun one_tap_clears_a_whole_loan_but_still_not_for_its_recorder() {
        var s = Session.dev()
        s = s.actAs(DevSeed.PINAH).lend(DevSeed.BRIAN, 200_000, txnCostCents = 3_300)
        val loanId = s.book.loans.last().id

        // Pinah recorded it, so Pinah cannot clear it.
        val refused = s.confirmGroup(loanId, DevSeed.PINAH)
        assertTrue(refused.notice is Notice.Refused)
        assertEquals(3, refused.book.group(loanId).size)

        // Bonnie can, and all three legs go at once.
        s = s.confirmGroup(loanId, DevSeed.BONNIE)
        assertTrue(s.notice is Notice.Info)
        assertTrue(s.book.group(loanId).none { it.state.name == "PENDING" })
    }

    @Test
    fun cash_at_hand_never_changes_across_a_transfer() {
        var s = Session.dev()
        val before = s.book.state().cashAtHandCents
        s = s.actAs(DevSeed.PINAH).transfer(DevSeed.SAVINGS, DevSeed.FLOAT, 100_000)
        val moved = s.book.pending().last()
        s = s.confirm(moved.id, DevSeed.BONNIE)

        assertEquals(before, s.book.state().cashAtHandCents, "a transfer moves, never creates")
    }

    @Test
    fun switching_who_the_device_acts_as_is_allowed_in_dev() {
        val s = Session.dev().actAs(DevSeed.KANGIRI)
        assertEquals(DevSeed.KANGIRI, s.actingAs)
        assertTrue(s.notice is Notice.Info)
    }
}
