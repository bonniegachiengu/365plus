package online.vyybandasky.plus365.core.book

import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.governance.Decision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A stake belongs to the pool's own members.
 *
 * `record` already refuses a Keshflo beneficiary who tries to *record* — that is
 * the governance gate. It said nothing about a beneficiary being the *subject* of
 * an entry that moves a stake, which is a different question with the same
 * answer: an outside borrower has no share of the pool, so there is nothing to
 * add to and nothing to pay out.
 *
 * The phone hid this by offering only founders in the picker. A rule enforced by
 * which buttons are drawn is not a rule; it is a habit that holds until somebody
 * builds a second screen. This is that second screen's fault line, found before
 * it was built.
 */
class StakeSubjectTest {

    private val config = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)

    private val book = LedgerBook(
        members = DevSeed.MEMBERS,
        accounts = DevSeed.ACCOUNTS,
        pockets = DevSeed.POCKETS,
    )

    @Test
    fun `a beneficiary cannot be given a stake in the pool`() {
        val r = book.record(
            id = "x-1",
            type = EntryType.CONTRIBUTION,
            amountCents = 10_000,
            memberId = DevSeed.WANJIKU,
            recordedBy = DevSeed.BONNIE,
            config = config,
        )
        assertTrue(r is Decision.Refused, "a Keshflo borrower has no share to add to")
    }

    @Test
    fun `a beneficiary cannot be paid out of a stake they never had`() {
        val r = book.record(
            id = "x-2",
            type = EntryType.PAYOUT,
            amountCents = 10_000,
            memberId = DevSeed.WANJIKU,
            recordedBy = DevSeed.BONNIE,
            config = config,
        )
        assertTrue(r is Decision.Refused, "there is no stake to pay out")
    }

    @Test
    fun `a founder can still be paid out`() {
        val r = book.record(
            id = "x-3",
            type = EntryType.PAYOUT,
            amountCents = 10_000,
            memberId = DevSeed.KANGIRI,
            recordedBy = DevSeed.BONNIE,
            config = config,
        )
        assertTrue(r is Decision.Allowed, "paying a member out is an ordinary thing to do")
    }

    @Test
    fun `lending to a beneficiary is still allowed`() {
        // The whole point of a Keshflo borrower. Guarding the stake must not
        // guard the lending.
        val r = book.record(
            id = "x-4",
            type = EntryType.LOAN_REPAYMENT,
            amountCents = 10_000,
            memberId = DevSeed.WANJIKU,
            recordedBy = DevSeed.BONNIE,
            config = config,
        )
        assertTrue(r is Decision.Allowed, "a borrower repaying is not a stake move")
    }

    @Test
    fun `paying out lowers the stake and the pool by the same amount`() {
        val before = book.state()
        val r = book.record(
            id = "x-5",
            type = EntryType.PAYOUT,
            amountCents = 50_000,
            memberId = DevSeed.KANGIRI,
            recordedBy = DevSeed.BONNIE,
            config = config,
        )
        val recorded = (r as Decision.Allowed).value
        val after = recorded.book
            .confirm("x-5", DevSeed.BRIAN, config)
            .let { (it as Decision.Allowed).value.book }
            .state()

        assertEquals(
            before.balanceOf(DevSeed.KANGIRI).stakeCents - 50_000,
            after.balanceOf(DevSeed.KANGIRI).stakeCents,
        )
        assertEquals(before.poolCashCents - 50_000, after.poolCashCents)
    }
}
