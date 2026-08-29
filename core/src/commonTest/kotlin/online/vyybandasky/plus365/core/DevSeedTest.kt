package online.vyybandasky.plus365.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import online.vyybandasky.plus365.core.book.confirm
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.governance.Decision
import online.vyybandasky.plus365.core.book.needingOverride
import online.vyybandasky.plus365.core.governance.eligibleConfirmers
import online.vyybandasky.plus365.core.governance.eligibleOverriders
import online.vyybandasky.plus365.core.governance.Refusal
import online.vyybandasky.plus365.core.money.formatKes

class DevSeedTest {

    private val book = DevSeed.book()
    private val state = book.state()

    @Test
    fun the_seed_builds_at_all() {
        // DevSeed goes through record() and confirm(), so if two-person control
        // were broken this would throw rather than silently seed something wrong.
        assertEquals(3, book.founders().size, "Bonnie, Brian, Kang'iri")
        assertEquals(1, book.beneficiaries().size, "one Keshflo borrower")
        assertEquals(3, book.loans.size)
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
        // In:  3,000 + 2,000 + 1,500 + 1,000                 = 7,500
        // Out: (2,000+33) + (1,300+23) + (1,000+28)          = 4,384
        // In:  500 repaid, and 42 Ziidi paid out             =   542
        //                                               cash = 3,658
        // Loan interest never touches cash — it is owed, never held. Account
        // interest does: that is real money arriving.
        assertEquals(365_800L, state.poolCashCents)
        assertEquals("KSh 3,658.00", formatKes(state.poolCashCents))
    }

    @Test
    fun cash_at_hand_is_the_roll_up_over_the_pockets() {
        // Most of it parked in Ziidi, which is where the earning happens.
        assertEquals(-38_400L, state.accountBalance(DevSeed.POCHI))
        assertEquals(404_200L, state.accountBalance(DevSeed.ZIIDI))
        assertEquals(365_800L, state.cashAtHandCents)
        assertEquals(
            state.poolCashCents,
            state.cashAtHandCents,
            "the roll-up and the total are the same money",
        )
    }

    @Test
    fun the_running_outstanding_is_every_loan_added_up() {
        // Kang'iri 2,133 due less 500 repaid = 1,633; Brian 1,388; Wanjiku 1,128.
        assertEquals(414_900L, state.totalOutstandingCents)
        assertEquals("KSh 4,149.00", formatKes(state.totalOutstandingCents))
    }

    @Test
    fun a_founder_borrows_at_five_and_keshflo_lends_out_at_ten() {
        assertEquals(500, book.loan("L-001")!!.rateBps, "Kang'iri is a founder")
        assertEquals(500, book.loan("L-002")!!.rateBps, "Brian is a founder")
        assertEquals(1_000, book.loan("L-003")!!.rateBps, "Wanjiku borrows through Keshflo")

        assertEquals(10_000L, state.loans["L-001"]!!.interestAccruedCents, "5% of 2,000")
        assertEquals(6_500L, state.loans["L-002"]!!.interestAccruedCents, "5% of 1,300")
        assertEquals(10_000L, state.loans["L-003"]!!.interestAccruedCents, "10% of 1,000")
    }

    @Test
    fun a_keshflo_beneficiary_governs_nothing() {
        // They borrow and nothing else: no contribution, and no say in anyone's
        // entry. Letting an outside borrower confirm would hand a vote on the
        // members' money to someone with no stake in it.
        assertEquals(0L, state.balanceOf(DevSeed.WANJIKU).stakeCents)
        assertTrue(DevSeed.WANJIKU !in DevSeed.EVERYONE, "a dev device cannot act as them")
        for (e in book.entries) {
            assertTrue(e.recordedByMemberId != DevSeed.WANJIKU)
            assertTrue(e.confirmedByMemberId != DevSeed.WANJIKU)
        }
        for (e in book.entries.filter { it.state == EntryState.PENDING }) {
            assertTrue(
                DevSeed.WANJIKU !in eligibleConfirmers(e, book.memberIds(), DevSeed.DEV_CONFIG),
            )
        }
    }

    @Test
    fun a_loan_keeps_its_components_apart() {
        val k = state.loans.getValue("L-001")
        assertEquals(200_000L, k.principalCents)
        assertEquals(10_000L, k.interestAccruedCents, "5% of 2,000")
        assertEquals(3_300L, k.mpesaChargeCents, "the M-Pesa fee, never folded into principal")
        assertEquals(0L, k.bankChargeCents, "no bank account yet")
        assertEquals(50_000L, k.repaidCents)
        assertEquals(213_300L, k.totalDueCents)
        assertEquals(163_300L, k.outstandingCents)

        val b = state.loans.getValue("L-002")
        assertEquals(6_500L, b.interestAccruedCents, "5% of 1,300 is 65")
        assertEquals(2_300L, b.mpesaChargeCents)
        assertEquals(138_800L, b.totalDueCents)
    }

    @Test
    fun the_pending_entry_is_held_apart_from_the_pool() {
        assertEquals(1, book.pending().size, "one entry still waiting on a second member")
        // 500 waiting plus the 800 in conflict: neither is money yet, and both
        // are held apart rather than quietly ignored.
        assertEquals(130_000L, state.pendingPoolCashCents)
        assertEquals(365_800L, state.poolCashCents, "pending money is not in the pool")
    }

    @Test
    fun the_seed_opens_with_one_fallout_waiting_on_the_uninvolved_member() {
        val stuck = book.needingOverride().single()
        assertEquals(EntryState.NEEDS_OVERRIDE, stuck.state)
        assertEquals(DevSeed.KANGIRI, stuck.recordedByMemberId)
        assertEquals(DevSeed.BONNIE, stuck.conflict!!.raisedBy)
        assertTrue(stuck.conflict!!.reasons.isNotEmpty())

        // Both involved are barred; exactly the third member is left.
        assertEquals(
            listOf(DevSeed.BRIAN),
            eligibleOverriders(stuck, book.memberIds(), DevSeed.DEV_CONFIG),
        )
    }

    @Test
    fun the_fallout_keeps_both_messages_so_the_third_member_can_compare_them() {
        val stuck = book.needingOverride().single()
        assertEquals("RTY4M8N2PQ", stuck.recordedEvidence!!.reference)
        assertEquals("WXZ7K3J5VB", stuck.conflict!!.attemptedEvidence!!.reference)
    }

    @Test
    fun stakes_are_what_each_member_put_in() {
        assertEquals(300_000L, state.balanceOf(DevSeed.BONNIE).stakeCents)
        // Brian contributed twice: 2,000 then 1,500.
        assertEquals(350_000L, state.balanceOf(DevSeed.BRIAN).stakeCents)
        assertEquals(100_000L, state.balanceOf(DevSeed.KANGIRI).stakeCents)
    }

    @Test
    fun a_pending_loan_amount_is_principal_plus_interest_plus_cost_less_repayments() {
        // Kang'iri: 2,000 + 100 interest + 33 cost - 500 repaid = 1,633.
        assertEquals(-163_300L, state.balanceOf(DevSeed.KANGIRI).debtCents)
        // Brian: 1,300 + 65 + 23 = 1,388.
        assertEquals(-138_800L, state.balanceOf(DevSeed.BRIAN).debtCents)
        // Wanjiku, through Keshflo at 10%: 1,000 + 100 + 28 = 1,128.
        assertEquals(-112_800L, state.balanceOf(DevSeed.WANJIKU).debtCents)
    }

    @Test
    fun the_pool_keeps_three_accounts_and_two_pockets() {
        assertEquals(3, book.accounts.size, "Pochi, Ziidi, M-Shwari")
        assertEquals(2, book.pockets.size, "Founder's A/C and Keshflo A/C")
    }

    @Test
    fun where_the_money_is_and_what_it_is_for_agree_with_the_total() {
        // Three routes to the same number, computed separately. If they ever
        // disagree the fold has a hole in it.
        assertTrue(state.balances, "accounts, pockets and the total must agree")
        assertEquals(state.poolCashCents, state.cashAtHandCents)
        assertEquals(state.poolCashCents, state.allocatedCents)
    }

    @Test
    fun the_keshflo_split_sits_over_the_total_without_moving_any_money() {
        assertEquals(150_000L, state.pocketBalance(DevSeed.KESHFLO))
        assertEquals(215_800L, state.pocketBalance(DevSeed.POOL))
        assertEquals(365_800L, state.allocatedCents)
    }

    @Test
    fun ziidi_earns_and_the_wallet_does_not() {
        assertTrue(book.account(DevSeed.ZIIDI)!!.earnsInterest)
        assertTrue(book.account(DevSeed.ZIIDI)!!.zeroRated, "Ziidi is zero-rated")
        assertTrue(!book.account(DevSeed.POCHI)!!.earnsInterest)
        // The interest that arrived lifted the pool without lifting any member's
        // contribution — nobody put it in.
        assertEquals(4_200L, book.entries.first { it.id == "i1" }.amountCents)
        assertEquals(0L, state.balanceOf(DevSeed.BRIAN).stakeCents - 350_000L)
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
            book.founderIds().sorted(),
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
                book.founderIds(),
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
