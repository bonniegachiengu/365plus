package online.vyybandasky.plus365.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import online.vyybandasky.plus365.core.book.confirm
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.governance.Decision
import online.vyybandasky.plus365.core.governance.Refusal
import online.vyybandasky.plus365.core.money.formatKes

class DevSeedTest {

    private val book = DevSeed.book()
    private val state = book.state()

    @Test
    fun the_seed_builds_at_all() {
        // DevSeed goes through record() and confirm(), so if two-person control
        // were broken this would throw rather than silently seed something wrong.
        assertEquals(4, book.members.size)
        assertEquals(2, book.loans.size)
        assertTrue(book.entries.isNotEmpty())
    }

    @Test
    fun every_confirmed_entry_was_confirmed_by_someone_other_than_its_recorder() {
        val confirmed = book.entries.filter { it.state == EntryState.CONFIRMED }
        assertTrue(confirmed.isNotEmpty())
        for (e in confirmed) {
            assertNotNull(e.confirmedByMemberId, "${e.id} is confirmed by nobody")
            assertTrue(
                e.recordedByMemberId != e.confirmedByMemberId,
                "${e.id} was confirmed by its own recorder",
            )
        }
    }

    @Test
    fun the_pool_holds_what_the_confirmed_entries_add_up_to() {
        // 3,000 + 2,000 + 1,500 + 1,000 in, 2,000 and 1,300 lent out, 500 repaid.
        assertEquals(470_000L, state.poolCashCents)
        assertEquals("KSh 4,700.00", formatKes(state.poolCashCents))
    }

    @Test
    fun the_pending_entry_is_held_apart_from_the_pool() {
        assertEquals(1, book.pending().size)
        assertEquals(50_000L, state.pendingPoolCashCents)
        assertEquals(470_000L, state.poolCashCents, "pending money is not in the pool")
    }

    @Test
    fun stakes_are_what_each_member_put_in() {
        assertEquals(300_000L, state.balanceOf(DevSeed.BONNIE).stakeCents)
        assertEquals(200_000L, state.balanceOf(DevSeed.PINAH).stakeCents)
        assertEquals(150_000L, state.balanceOf(DevSeed.BRIAN).stakeCents)
        assertEquals(100_000L, state.balanceOf(DevSeed.KANGIRI).stakeCents)
    }

    @Test
    fun borrowers_owe_principal_plus_seven_percent_less_what_they_have_repaid() {
        // Kang'iri: 2,000 + 140 interest - 500 repaid = 1,640 owed.
        assertEquals(-164_000L, state.balanceOf(DevSeed.KANGIRI).debtCents)
        // Brian: 1,300 + 91 interest = 1,391 owed.
        assertEquals(-139_100L, state.balanceOf(DevSeed.BRIAN).debtCents)
    }

    @Test
    fun the_loans_carry_the_house_rate_and_the_interest_it_implies() {
        assertEquals(700, book.loan("L-001")!!.rateBps)
        assertEquals(700, book.loan("L-002")!!.rateBps)
        assertEquals(14_000L, state.loans["L-001"]!!.interestAccruedCents)
        assertEquals(9_100L, state.loans["L-002"]!!.interestAccruedCents)
    }

    @Test
    fun a_repayment_has_come_off_the_right_loan() {
        assertEquals(150_000L, state.loans["L-001"]!!.principalOutstandingCents)
        assertEquals(130_000L, state.loans["L-002"]!!.principalOutstandingCents)
    }

    @Test
    fun the_waiting_entry_still_cannot_be_confirmed_by_the_member_who_recorded_it() {
        val waiting = book.pending().single()
        val refused = assertIs<Decision.Refused>(
            book.confirm(waiting.id, waiting.recordedByMemberId!!, DevSeed.DEV_CONFIG),
        )
        assertIs<Refusal.SelfConfirmation>(refused.refusal)
    }

    @Test
    fun no_member_record_carries_a_phone_number() {
        // Numbers are deployment config. Nothing in this app contacts anyone, and
        // no number is compiled into it.
        assertTrue(book.members.all { it.phoneE164.isEmpty() })
    }
}
