package online.vyybandasky.plus365.core.presentation

import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.book.confirm
import online.vyybandasky.plus365.core.book.record
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.governance.Decision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A repayment is not a contribution.
 *
 * Brian's point, and the one thing in this model that would be easy to get
 * quietly wrong: both messages bring money in, so both raise cash at hand, and
 * a model that stopped there would look right on the total while being wrong
 * about every member.
 *
 * They differ in what else they touch. A contribution raises what a member has
 * put in — their stake, the figure their target is measured against. A
 * repayment touches only what they owe. Somebody who borrows 10,000 and pays it
 * all back has contributed nothing, and if repaying quietly counted as
 * contributing, the fastest way to hit a savings target would be to borrow from
 * the pool and hand the money straight back.
 */
class UpdateKindsTest {

    private val config = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)

    private fun <T> Decision<T>.value(): T = (this as Decision.Allowed).value

    private fun book() = LedgerBook(
        members = DevSeed.MEMBERS,
        accounts = DevSeed.ACCOUNTS,
        pockets = DevSeed.POCKETS,
    )

    /** Record and confirm in one go — money only moves once somebody agrees. */
    private fun LedgerBook.settled(
        id: String,
        type: EntryType,
        amountCents: Long,
        who: String = DevSeed.KANGIRI,
        pocket: String = DevSeed.POOL,
    ): LedgerBook {
        val b = record(
            id = id, type = type, amountCents = amountCents, memberId = who,
            recordedBy = DevSeed.BONNIE, config = config, pocketId = pocket,
            accountId = DevSeed.ACCOUNTS.first().id,
        ).value().book
        return b.confirm(id, DevSeed.BRIAN, config).value().book
    }

    @Test
    fun a_contribution_raises_what_they_have_put_in() {
        val b = book().settled("c1", EntryType.CONTRIBUTION, 500_000)
        val s = b.state()
        assertEquals(500_000L, s.balanceOf(DevSeed.KANGIRI).stakeCents)
        assertEquals(500_000L, s.cashAtHandCents)
    }

    @Test
    fun a_repayment_does_not() {
        // Both raise cash at hand. Only one raises the stake, and that is the
        // whole distinction.
        var b = book().settled("l1", EntryType.LOAN_OUT, 500_000)
        val stakeAfterBorrowing = b.state().balanceOf(DevSeed.KANGIRI).stakeCents
        b = b.settled("r1", EntryType.LOAN_REPAYMENT, 500_000)
        val s = b.state()
        assertEquals(stakeAfterBorrowing, s.balanceOf(DevSeed.KANGIRI).stakeCents)
        assertEquals(0L, s.balanceOf(DevSeed.KANGIRI).stakeCents)
        assertEquals(0L, s.cashAtHandCents)
    }

    @Test
    fun borrowing_and_repaying_in_full_leaves_no_trace_on_the_stake() {
        var b = book().settled("l1", EntryType.LOAN_OUT, 1_000_000)
        b = b.settled("r1", EntryType.LOAN_REPAYMENT, 1_000_000)
        val s = b.state()
        assertEquals(0L, s.balanceOf(DevSeed.KANGIRI).stakeCents)
        assertEquals(0L, s.balanceOf(DevSeed.KANGIRI).debtCents)
        assertTrue(s.balances)
    }

    @Test
    fun a_lend_out_lowers_the_cash_and_a_repayment_brings_it_back() {
        var b = book().settled("c1", EntryType.CONTRIBUTION, 1_000_000)
        b = b.settled("l1", EntryType.LOAN_OUT, 400_000)
        assertEquals(600_000L, b.state().cashAtHandCents)
        b = b.settled("r1", EntryType.LOAN_REPAYMENT, 400_000)
        assertEquals(1_000_000L, b.state().cashAtHandCents)
    }

    // ── the receipt that follows every update ───────────────────────────────

    @Test
    fun every_update_reports_both_accounts_and_their_total() {
        var b = book().settled("c1", EntryType.CONTRIBUTION, 800_000, pocket = DevSeed.POOL)
        b = b.settled("k1", EntryType.CONTRIBUTION, 200_000, pocket = DevSeed.KESHFLO)
        val r = b.receipt()
        assertEquals(800_000L, r.foundersCents)
        assertEquals(200_000L, r.keshfloCents)
        assertEquals(1_000_000L, r.cashAtHandCents)
        assertEquals(r.foundersCents + r.keshfloCents, r.cashAtHandCents)
        assertTrue(r.addsUp)
    }

    @Test
    fun the_total_is_the_two_added_together_after_a_repayment_too() {
        var b = book().settled("c1", EntryType.CONTRIBUTION, 1_000_000, pocket = DevSeed.POOL)
        b = b.settled("l1", EntryType.LOAN_OUT, 300_000, pocket = DevSeed.POOL)
        b = b.settled("r1", EntryType.LOAN_REPAYMENT, 100_000, pocket = DevSeed.POOL)
        val r = b.receipt()
        assertEquals(800_000L, r.foundersCents)
        assertEquals(0L, r.keshfloCents)
        assertEquals(800_000L, r.cashAtHandCents)
        assertTrue(r.addsUp)
    }

    /**
     * The receipt says so when the two named accounts stop being the whole story.
     *
     * "Cash at hand is the sum of both" is true because the group has exactly
     * two pockets, not because anything enforces it. A third one holding money
     * would quietly break the sum, and a receipt that printed all three figures
     * anyway would be teaching people to trust arithmetic that no longer works.
     */
    @Test
    fun and_says_so_when_money_is_somewhere_else() {
        val b = book().settled("c1", EntryType.CONTRIBUTION, 500_000, pocket = "somewhere-else")
        val r = b.receipt()
        assertEquals(0L, r.foundersCents)
        assertEquals(0L, r.keshfloCents)
        assertEquals(500_000L, r.cashAtHandCents)
        assertEquals(500_000L, r.elsewhereCents)
        assertTrue(!r.addsUp)
    }

    /** Nothing unconfirmed reaches the receipt. */
    @Test
    fun a_recorded_but_unconfirmed_update_moves_nothing() {
        val b = book().record(
            id = "c1", type = EntryType.CONTRIBUTION, amountCents = 500_000,
            memberId = DevSeed.KANGIRI, recordedBy = DevSeed.BONNIE, config = config,
            pocketId = DevSeed.POOL,
        ).value().book
        val r = b.receipt()
        assertEquals(0L, r.cashAtHandCents)
        assertEquals(0L, r.foundersCents)
    }
}
