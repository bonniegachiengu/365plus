package online.vyybandasky.plus365.core.book

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.domain.ConfirmSource
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.governance.Decision
import online.vyybandasky.plus365.core.governance.Refusal

private val CONFIG = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)

private fun freshBook(withAccounts: Boolean = false) = LedgerBook(
    members = DevSeed.MEMBERS,
    accounts = if (withAccounts) DevSeed.ACCOUNTS else emptyList(),
)

private fun <T> Decision<T>.value(): T = assertIs<Decision.Allowed<T>>(this).value

class LedgerBookTest {

    @Test
    fun a_recorded_entry_lands_pending_and_moves_nothing() {
        val b = freshBook().record(
            id = "e1",
            type = EntryType.CONTRIBUTION,
            amountCents = 100_000,
            memberId = DevSeed.BONNIE,
            recordedBy = DevSeed.BONNIE,
            config = CONFIG,
        ).value().book

        assertEquals(EntryState.PENDING, b.entry("e1")!!.state)
        assertEquals(0L, b.state().poolCashCents, "pending money is not money")
        assertEquals(100_000L, b.state().pendingPoolCashCents)
    }

    @Test
    fun confirming_by_someone_else_moves_it_onto_the_balances() {
        var b = freshBook().record(
            id = "e1",
            type = EntryType.CONTRIBUTION,
            amountCents = 100_000,
            memberId = DevSeed.BONNIE,
            recordedBy = DevSeed.BONNIE,
            config = CONFIG,
        ).value().book
        b = b.confirm("e1", DevSeed.BRIAN, CONFIG).value().book

        assertEquals(100_000L, b.state().poolCashCents)
        assertEquals(0L, b.state().pendingPoolCashCents)
        assertEquals(DevSeed.BRIAN, b.entry("e1")!!.confirmedByMemberId)
        assertEquals(ConfirmSource.HUMAN, b.entry("e1")!!.confirmSource)
    }

    @Test
    fun the_recorder_confirming_their_own_entry_is_refused_and_changes_nothing() {
        val b = freshBook().record(
            id = "e1",
            type = EntryType.CONTRIBUTION,
            amountCents = 100_000,
            memberId = DevSeed.BONNIE,
            recordedBy = DevSeed.BONNIE,
            config = CONFIG,
        ).value().book

        val refused = assertIs<Decision.Refused>(b.confirm("e1", DevSeed.BONNIE, CONFIG))
        assertIs<Refusal.SelfConfirmation>(refused.refusal)
        assertEquals(EntryState.PENDING, b.entry("e1")!!.state)
        assertEquals(0L, b.state().poolCashCents)
    }

    @Test
    fun confirming_twice_is_refused_rather_than_double_counted() {
        var b = freshBook().record(
            id = "e1",
            type = EntryType.CONTRIBUTION,
            amountCents = 100_000,
            memberId = DevSeed.BONNIE,
            recordedBy = DevSeed.BONNIE,
            config = CONFIG,
        ).value().book
        b = b.confirm("e1", DevSeed.BRIAN, CONFIG).value().book

        assertIs<Decision.Refused>(b.confirm("e1", DevSeed.KANGIRI, CONFIG))
        assertEquals(100_000L, b.state().poolCashCents)
    }

    @Test
    fun recording_the_same_id_twice_is_the_same_fact_not_a_second_one() {
        var b = freshBook().record(
            id = "e1", type = EntryType.CONTRIBUTION, amountCents = 100_000,
            memberId = DevSeed.BONNIE, recordedBy = DevSeed.BONNIE, config = CONFIG,
        ).value().book
        b = b.record(
            id = "e1", type = EntryType.CONTRIBUTION, amountCents = 100_000,
            memberId = DevSeed.BONNIE, recordedBy = DevSeed.BONNIE, config = CONFIG,
        ).value().book

        assertEquals(1, b.entries.size)
    }

    @Test
    fun a_zero_or_negative_amount_is_refused() {
        val refused = assertIs<Decision.Refused>(
            freshBook().record(
                id = "e1", type = EntryType.CONTRIBUTION, amountCents = 0,
                memberId = DevSeed.BONNIE, recordedBy = DevSeed.BONNIE, config = CONFIG,
            ),
        )
        assertIs<Refusal.Invalid>(refused.refusal)
    }

    @Test
    fun an_unknown_member_is_refused() {
        val refused = assertIs<Decision.Refused>(
            freshBook().record(
                id = "e1", type = EntryType.CONTRIBUTION, amountCents = 100,
                memberId = "nobody", recordedBy = DevSeed.BONNIE, config = CONFIG,
            ),
        )
        assertIs<Refusal.UnknownMember>(refused.refusal)
    }

    @Test
    fun a_production_device_cannot_record_as_another_member() {
        val prod = ActorConfig.production(DevSeed.BONNIE)
        val refused = assertIs<Decision.Refused>(
            freshBook().record(
                id = "e1", type = EntryType.CONTRIBUTION, amountCents = 100,
                memberId = DevSeed.BRIAN, recordedBy = DevSeed.BRIAN, config = prod,
            ),
        )
        assertIs<Refusal.NotAuthorised>(refused.refusal)
    }

    @Test
    fun a_loan_produces_principal_interest_and_cost_all_pending_in_one_group() {
        val recorded = freshBook().disburseLoan(
            loanId = "L-1",
            borrower = DevSeed.KANGIRI,
            principalCents = 200_000, // KSh 2,000
            recordedBy = DevSeed.BRIAN,
            config = CONFIG,
            txnCostCents = 3_300, // KSh 33 M-Pesa cost
        ).value()

        assertEquals(3, recorded.entries.size, "principal, interest, transaction cost")
        assertEquals(EntryType.LOAN_OUT, recorded.entries[0].type)
        assertEquals(200_000L, recorded.entries[0].amountCents)
        assertEquals(EntryType.INTEREST_ACCRUAL, recorded.entries[1].type)
        assertEquals(14_000L, recorded.entries[1].amountCents, "7% of 2,000 is 140")
        assertEquals(EntryType.TXN_COST, recorded.entries[2].type)
        assertEquals(3_300L, recorded.entries[2].amountCents)

        assertTrue(recorded.entries.all { it.state == EntryState.PENDING })
        assertTrue(recorded.entries.all { it.groupId == "L-1" }, "one act, one group")
        assertEquals(700, recorded.book.loan("L-1")!!.rateBps)
        assertEquals(3_300L, recorded.book.loan("L-1")!!.txnCostCents)
    }

    @Test
    fun one_confirmation_clears_all_three_legs_of_a_loan() {
        var b = freshBook(withAccounts = true)
        b = b.record(
            id = "c1", type = EntryType.CONTRIBUTION, amountCents = 500_000,
            memberId = DevSeed.BONNIE, recordedBy = DevSeed.BONNIE, config = CONFIG,
        ).value().book
        b = b.confirm("c1", DevSeed.BRIAN, CONFIG).value().book
        b = b.disburseLoan(
            loanId = "L-1", borrower = DevSeed.KANGIRI, principalCents = 200_000,
            recordedBy = DevSeed.BRIAN, config = CONFIG, txnCostCents = 3_300,
        ).value().book

        val cleared = b.confirmGroup("L-1", DevSeed.BONNIE, CONFIG).value()
        assertEquals(3, cleared.entries.size)
        assertTrue(cleared.book.pending().isEmpty())
        // Each leg still records its own confirmation, by the same person.
        assertTrue(cleared.entries.all { it.confirmedByMemberId == DevSeed.BONNIE })
    }

    @Test
    fun the_recorder_cannot_clear_a_whole_loan_group_either() {
        var b = freshBook()
        b = b.disburseLoan(
            loanId = "L-1", borrower = DevSeed.KANGIRI, principalCents = 200_000,
            recordedBy = DevSeed.BRIAN, config = CONFIG, txnCostCents = 3_300,
        ).value().book

        val refused = assertIs<Decision.Refused>(b.confirmGroup("L-1", DevSeed.BRIAN, CONFIG))
        assertIs<Refusal.SelfConfirmation>(refused.refusal)
        assertEquals(3, b.pending().size, "nothing was cleared")
    }

    @Test
    fun an_unconfirmed_loan_has_not_left_the_pool() {
        val b = freshBook().disburseLoan(
            loanId = "L-1", borrower = DevSeed.KANGIRI, principalCents = 200_000,
            recordedBy = DevSeed.BRIAN, config = CONFIG, txnCostCents = 3_300,
        ).value().book

        assertEquals(0L, b.state().poolCashCents)
        assertEquals(0L, b.state().balanceOf(DevSeed.KANGIRI).debtCents)
    }

    @Test
    fun a_confirmed_loan_debits_principal_and_cost_but_never_the_interest() {
        var b = freshBook()
        b = b.record(
            id = "c1", type = EntryType.CONTRIBUTION, amountCents = 500_000,
            memberId = DevSeed.BONNIE, recordedBy = DevSeed.BONNIE, config = CONFIG,
        ).value().book
        b = b.confirm("c1", DevSeed.BRIAN, CONFIG).value().book

        val loan = b.disburseLoan(
            loanId = "L-1", borrower = DevSeed.KANGIRI, principalCents = 200_000,
            recordedBy = DevSeed.BRIAN, config = CONFIG, txnCostCents = 3_300,
        ).value()
        b = loan.book
        for (e in loan.entries) b = b.confirm(e.id, DevSeed.BONNIE, CONFIG).value().book

        // Cash leaves for the principal and the M-Pesa cost. NOT for the
        // interest — that is owed by the borrower, never money the pool held.
        assertEquals(500_000L - 200_000L - 3_300L, b.state().poolCashCents)
        // Debt is signed from the pool's view: negative means the member owes it.
        // All three components are owed: 2,000 + 140 + 33.
        assertEquals(-217_300L, b.state().balanceOf(DevSeed.KANGIRI).debtCents)

        val position = b.state().loans.getValue("L-1")
        assertEquals(200_000L, position.principalCents)
        assertEquals(14_000L, position.interestAccruedCents)
        assertEquals(3_300L, position.txnCostCents)
        assertEquals(217_300L, position.totalDueCents)
        assertEquals(217_300L, position.outstandingCents)
        assertEquals(217_300L, b.state().totalOutstandingCents)
    }

    @Test
    fun cash_at_hand_is_the_sum_of_the_pockets_and_a_transfer_never_changes_it() {
        var b = freshBook(withAccounts = true)
        b = b.record(
            id = "c1", type = EntryType.CONTRIBUTION, amountCents = 500_000,
            memberId = DevSeed.BONNIE, recordedBy = DevSeed.BONNIE, config = CONFIG,
            accountId = DevSeed.SAVINGS,
        ).value().book
        b = b.confirm("c1", DevSeed.BRIAN, CONFIG).value().book
        assertEquals(500_000L, b.state().cashAtHandCents)

        b = b.transfer("t1", DevSeed.SAVINGS, DevSeed.FLOAT, 200_000, DevSeed.BONNIE, CONFIG)
            .value().book
        b = b.confirm("t1", DevSeed.BRIAN, CONFIG).value().book

        assertEquals(300_000L, b.state().accountBalance(DevSeed.SAVINGS))
        assertEquals(200_000L, b.state().accountBalance(DevSeed.FLOAT))
        assertEquals(500_000L, b.state().cashAtHandCents, "a transfer moves, never creates")
        assertEquals(
            b.state().poolCashCents,
            b.state().cashAtHandCents,
            "the roll-up must agree with the total",
        )
    }

    @Test
    fun a_transfer_needs_two_different_accounts() {
        val b = freshBook(withAccounts = true)
        val refused = assertIs<Decision.Refused>(
            b.transfer("t1", DevSeed.SAVINGS, DevSeed.SAVINGS, 100, DevSeed.BONNIE, CONFIG),
        )
        assertIs<Refusal.Invalid>(refused.refusal)
    }

    @Test
    fun a_reversal_is_itself_pending_and_undoes_only_once_confirmed() {
        var b = freshBook()
        b = b.record(
            id = "c1", type = EntryType.CONTRIBUTION, amountCents = 100_000,
            memberId = DevSeed.BONNIE, recordedBy = DevSeed.BONNIE, config = CONFIG,
        ).value().book
        b = b.confirm("c1", DevSeed.BRIAN, CONFIG).value().book
        assertEquals(100_000L, b.state().poolCashCents)

        b = b.reverse("rev1", "c1", recordedBy = DevSeed.BRIAN, config = CONFIG).value().book
        assertEquals(100_000L, b.state().poolCashCents, "an unconfirmed reversal undoes nothing")

        b = b.confirm("rev1", DevSeed.KANGIRI, CONFIG).value().book
        assertEquals(0L, b.state().poolCashCents)
        assertEquals(2, b.entries.size, "the mistake and the correction both remain")
        assertEquals(EntryState.CONFIRMED, b.entry("c1")!!.state, "nothing was deleted")
    }

    @Test
    fun a_reversal_cannot_be_confirmed_by_whoever_recorded_it() {
        var b = freshBook()
        b = b.record(
            id = "c1", type = EntryType.CONTRIBUTION, amountCents = 100_000,
            memberId = DevSeed.BONNIE, recordedBy = DevSeed.BONNIE, config = CONFIG,
        ).value().book
        b = b.confirm("c1", DevSeed.BRIAN, CONFIG).value().book
        b = b.reverse("rev1", "c1", recordedBy = DevSeed.BRIAN, config = CONFIG).value().book

        val refused = assertIs<Decision.Refused>(b.confirm("rev1", DevSeed.BRIAN, CONFIG))
        assertIs<Refusal.SelfConfirmation>(refused.refusal)
    }
}
