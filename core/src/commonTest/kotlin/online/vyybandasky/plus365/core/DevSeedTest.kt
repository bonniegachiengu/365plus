package online.vyybandasky.plus365.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import online.vyybandasky.plus365.core.book.confirm
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.governance.Decision
import online.vyybandasky.plus365.core.governance.eligibleConfirmers
import online.vyybandasky.plus365.core.governance.Refusal
import online.vyybandasky.plus365.core.money.formatKes

class DevSeedTest {

    private val book = DevSeed.book()
    private val state = book.state()

    @Test
    fun the_seed_builds_at_all() {
        // DevSeed goes through record() and confirm(), so if two-person control
        // were broken this would throw rather than silently seed something wrong.
        assertEquals(3, book.members.size, "Bonnie, Brian, Kang'iri — Brian keeps the book")
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
        // In:  3,000 + 2,000 + 1,500 + 1,000            = 7,500
        // Out: (2,000 + 33) + (1,300 + 23)              = 3,356   principal + cost
        // In:  500 repaid                               =   500
        //                                          cash = 4,644
        // The two interest charges never touch cash — they are owed, not held.
        assertEquals(464_400L, state.poolCashCents)
        assertEquals("KSh 4,644.00", formatKes(state.poolCashCents))
    }

    @Test
    fun cash_at_hand_is_the_roll_up_over_the_pockets() {
        assertEquals(350_000L, state.accountBalance(DevSeed.SAVINGS))
        assertEquals(114_400L, state.accountBalance(DevSeed.FLOAT))
        assertEquals(464_400L, state.cashAtHandCents)
        assertEquals(
            state.poolCashCents,
            state.cashAtHandCents,
            "the roll-up and the total are the same money",
        )
    }

    @Test
    fun the_running_outstanding_is_every_loan_added_up() {
        // Kang'iri 2,173 due less 500 repaid = 1,673; Brian 1,414 due.
        assertEquals(308_700L, state.totalOutstandingCents)
        assertEquals("KSh 3,087.00", formatKes(state.totalOutstandingCents))
    }

    @Test
    fun a_loan_keeps_its_three_components_apart() {
        val k = state.loans.getValue("L-001")
        assertEquals(200_000L, k.principalCents)
        assertEquals(14_000L, k.interestAccruedCents, "7% of 2,000")
        assertEquals(3_300L, k.txnCostCents, "M-Pesa cost, never folded into principal")
        assertEquals(50_000L, k.repaidCents)
        assertEquals(217_300L, k.totalDueCents)
        assertEquals(167_300L, k.outstandingCents)

        val b = state.loans.getValue("L-002")
        assertEquals(9_100L, b.interestAccruedCents, "7% of 1,300 is 91")
        assertEquals(2_300L, b.txnCostCents)
        assertEquals(141_400L, b.totalDueCents)
    }

    @Test
    fun the_pending_entry_is_held_apart_from_the_pool() {
        assertEquals(1, book.pending().size)
        assertEquals(50_000L, state.pendingPoolCashCents)
        assertEquals(464_400L, state.poolCashCents, "pending money is not in the pool")
    }

    @Test
    fun stakes_are_what_each_member_put_in() {
        assertEquals(300_000L, state.balanceOf(DevSeed.BONNIE).stakeCents)
        // Brian contributed twice: 2,000 then 1,500.
        assertEquals(350_000L, state.balanceOf(DevSeed.BRIAN).stakeCents)
        assertEquals(100_000L, state.balanceOf(DevSeed.KANGIRI).stakeCents)
    }

    @Test
    fun borrowers_owe_principal_plus_seven_percent_less_what_they_have_repaid() {
        // Kang'iri: 2,000 + 140 interest + 33 cost - 500 repaid = 1,673 owed.
        assertEquals(-167_300L, state.balanceOf(DevSeed.KANGIRI).debtCents)
        // Brian: 1,300 + 91 interest + 23 cost = 1,414 owed.
        assertEquals(-141_400L, state.balanceOf(DevSeed.BRIAN).debtCents)
    }

    @Test
    fun the_loans_carry_the_house_rate_and_the_interest_it_implies() {
        assertEquals(700, book.loan("L-001")!!.rateBps)
        assertEquals(700, book.loan("L-002")!!.rateBps)
        assertEquals(14_000L, state.loans["L-001"]!!.interestAccruedCents)
        assertEquals(9_100L, state.loans["L-002"]!!.interestAccruedCents)
        assertEquals(2, book.accounts.size)
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
    fun there_are_exactly_three_members_and_brian_keeps_the_book() {
        // Brian owns the accounting, keeps the ledger and holds the account —
        // one person, not two. An earlier model split him into "Pinah" and
        // "Brian"; this pins the correction so it cannot drift back.
        assertEquals(
            listOf("bonnie", "brian", "kangiri"),
            book.members.map { it.id }.sorted(),
        )
        assertTrue(
            book.members.none { it.displayName.equals("pinah", ignoreCase = true) },
            "Pinah and Brian are the same person",
        )
    }

    @Test
    fun three_members_leaves_two_people_able_to_clear_any_entry() {
        // Why three is the floor: with two, whoever records is the only other
        // person, and nothing could ever be confirmed.
        for (e in book.entries) {
            val eligible = eligibleConfirmers(
                e.copy(state = EntryState.PENDING),
                book.memberIds(),
                DevSeed.DEV_CONFIG,
            )
            assertEquals(2, eligible.size, "${e.id} should have two possible confirmers")
            assertTrue(e.recordedByMemberId !in eligible)
        }
    }

    @Test
    fun no_member_record_carries_a_phone_number() {
        // Numbers are deployment config. Nothing in this app contacts anyone, and
        // no number is compiled into it.
        assertTrue(book.members.all { it.phoneE164.isEmpty() })
    }
}
