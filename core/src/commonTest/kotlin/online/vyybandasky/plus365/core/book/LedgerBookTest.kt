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

private fun freshBook() = LedgerBook(members = DevSeed.MEMBERS)

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
        b = b.confirm("e1", DevSeed.PINAH, CONFIG).value().book

        assertEquals(100_000L, b.state().poolCashCents)
        assertEquals(0L, b.state().pendingPoolCashCents)
        assertEquals(DevSeed.PINAH, b.entry("e1")!!.confirmedByMemberId)
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
        b = b.confirm("e1", DevSeed.PINAH, CONFIG).value().book

        assertIs<Decision.Refused>(b.confirm("e1", DevSeed.BRIAN, CONFIG))
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
                memberId = DevSeed.PINAH, recordedBy = DevSeed.PINAH, config = prod,
            ),
        )
        assertIs<Refusal.NotAuthorised>(refused.refusal)
    }

    @Test
    fun a_loan_produces_a_principal_entry_and_an_interest_entry_both_pending() {
        val recorded = freshBook().disburseLoan(
            loanId = "L-1",
            principalEntryId = "L-1-p",
            interestEntryId = "L-1-i",
            borrower = DevSeed.KANGIRI,
            principalCents = 200_000, // KSh 2,000
            recordedBy = DevSeed.PINAH,
            config = CONFIG,
        ).value()

        assertEquals(2, recorded.entries.size)
        assertEquals(EntryType.LOAN_OUT, recorded.entries[0].type)
        assertEquals(EntryType.INTEREST_ACCRUAL, recorded.entries[1].type)
        assertEquals(14_000L, recorded.entries[1].amountCents, "7% of 2,000 is 140")
        assertTrue(recorded.entries.all { it.state == EntryState.PENDING })
        assertEquals(700, recorded.book.loan("L-1")!!.rateBps)
    }

    @Test
    fun an_unconfirmed_loan_has_not_left_the_pool() {
        val b = freshBook().disburseLoan(
            loanId = "L-1", principalEntryId = "L-1-p", interestEntryId = "L-1-i",
            borrower = DevSeed.KANGIRI, principalCents = 200_000,
            recordedBy = DevSeed.PINAH, config = CONFIG,
        ).value().book

        assertEquals(0L, b.state().poolCashCents)
        assertEquals(0L, b.state().balanceOf(DevSeed.KANGIRI).debtCents)
    }

    @Test
    fun a_confirmed_loan_debits_the_pool_and_the_borrower_owes_principal_plus_interest() {
        var b = freshBook()
        b = b.record(
            id = "c1", type = EntryType.CONTRIBUTION, amountCents = 500_000,
            memberId = DevSeed.BONNIE, recordedBy = DevSeed.BONNIE, config = CONFIG,
        ).value().book
        b = b.confirm("c1", DevSeed.PINAH, CONFIG).value().book

        val loan = b.disburseLoan(
            loanId = "L-1", principalEntryId = "L-1-p", interestEntryId = "L-1-i",
            borrower = DevSeed.KANGIRI, principalCents = 200_000,
            recordedBy = DevSeed.PINAH, config = CONFIG,
        ).value()
        b = loan.book
        for (e in loan.entries) b = b.confirm(e.id, DevSeed.BONNIE, CONFIG).value().book

        // Cash out is the principal only — the interest was never cash the pool held.
        assertEquals(300_000L, b.state().poolCashCents)
        // Debt is signed from the pool's view: negative means the member owes it.
        assertEquals(-214_000L, b.state().balanceOf(DevSeed.KANGIRI).debtCents)
    }

    @Test
    fun a_reversal_is_itself_pending_and_undoes_only_once_confirmed() {
        var b = freshBook()
        b = b.record(
            id = "c1", type = EntryType.CONTRIBUTION, amountCents = 100_000,
            memberId = DevSeed.BONNIE, recordedBy = DevSeed.BONNIE, config = CONFIG,
        ).value().book
        b = b.confirm("c1", DevSeed.PINAH, CONFIG).value().book
        assertEquals(100_000L, b.state().poolCashCents)

        b = b.reverse("rev1", "c1", recordedBy = DevSeed.PINAH, config = CONFIG).value().book
        assertEquals(100_000L, b.state().poolCashCents, "an unconfirmed reversal undoes nothing")

        b = b.confirm("rev1", DevSeed.BRIAN, CONFIG).value().book
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
        b = b.confirm("c1", DevSeed.PINAH, CONFIG).value().book
        b = b.reverse("rev1", "c1", recordedBy = DevSeed.PINAH, config = CONFIG).value().book

        val refused = assertIs<Decision.Refused>(b.confirm("rev1", DevSeed.PINAH, CONFIG))
        assertIs<Refusal.SelfConfirmation>(refused.refusal)
    }
}
