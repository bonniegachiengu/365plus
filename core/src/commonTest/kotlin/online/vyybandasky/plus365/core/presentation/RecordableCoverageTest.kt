package online.vyybandasky.plus365.core.presentation

import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.governance.ActorConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every type the book says a person may record has a way in.
 *
 * `RECORDABLE_TYPES` listed five types and the shells offered two of them. The
 * list was right and the app was wrong, and nothing anywhere noticed, because a
 * declaration nobody checks is a comment with a type signature.
 *
 * This is the check. Add a recordable type without an action and this fails, so
 * the gap that produced payouts-you-cannot-record cannot quietly reopen.
 */
class RecordableCoverageTest {

    private val config = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)

    private fun session() = Session(
        book = LedgerBook(
            members = DevSeed.MEMBERS,
            accounts = DevSeed.ACCOUNTS,
            pockets = DevSeed.POCKETS,
        ),
        config = config,
        actingAs = DevSeed.BONNIE,
    )

    @Test
    fun `every recordable type is reachable from some action`() {
        val covered = PoolAction.entries.mapNotNull { it.entryType }.toSet()
        val missing = RECORDABLE_TYPES.filterNot { it in covered }
        assertTrue(
            missing.isEmpty(),
            "no way to record: ${missing.joinToString()} — add a PoolAction or drop the type",
        )
    }

    @Test
    fun `lending is the only action without a single type`() {
        val typeless = PoolAction.entries.filter { it.entryType == null }
        assertEquals(listOf(PoolAction.LEND, PoolAction.BORROW), typeless)
    }

    @Test
    fun `paying out lowers the pool and needs a second member`() {
        val s = session()
        val before = s.book.state().poolCashCents
        val after = s.payOut(DevSeed.KANGIRI, 50_000)

        assertEquals(
            before,
            after.book.state().poolCashCents,
            "recording it moves nothing until somebody else agrees",
        )
        val cleared = after.actAs(DevSeed.BRIAN).let { b ->
            b.confirm(b.book.pending().last().id, DevSeed.BRIAN)
        }
        assertEquals(before - 50_000, cleared.book.state().poolCashCents)
    }

    @Test
    fun `a member lending in raises the pool without raising their share`() {
        val s = session()
        val stakeBefore = s.book.state().balanceOf(DevSeed.KANGIRI).stakeCents
        val cashBefore = s.book.state().poolCashCents

        val after = s.memberLendsIn(DevSeed.KANGIRI, 30_000).let { r ->
            r.actAs(DevSeed.BRIAN).let { b -> b.confirm(b.book.pending().last().id, DevSeed.BRIAN) }
        }

        assertEquals(cashBefore + 30_000, after.book.state().poolCashCents, "the cash is real")
        assertEquals(
            stakeBefore,
            after.book.state().balanceOf(DevSeed.KANGIRI).stakeCents,
            "lending to the pool is not the same as owning more of it",
        )
    }

    @Test
    fun `paying a member back settles what the pool owes them`() {
        val s = session()
        val lent = s.memberLendsIn(DevSeed.KANGIRI, 30_000).let { r ->
            r.actAs(DevSeed.BRIAN).let { b -> b.confirm(b.book.pending().last().id, DevSeed.BRIAN) }
        }
        val owed = lent.book.state().balanceOf(DevSeed.KANGIRI).debtCents

        val settled = lent.actAs(DevSeed.BONNIE).repayMember(DevSeed.KANGIRI, 30_000).let { r ->
            r.actAs(DevSeed.BRIAN).let { b -> b.confirm(b.book.pending().last().id, DevSeed.BRIAN) }
        }

        assertEquals(
            owed - 30_000,
            settled.book.state().balanceOf(DevSeed.KANGIRI).debtCents,
            "what the pool owes comes back down",
        )
    }

    @Test
    fun `a Keshflo borrower cannot be paid out`() {
        val s = session().payOut(DevSeed.WANJIKU, 10_000)
        assertTrue(s.notice is Notice.Refused, "there is no share to pay out")
    }
}

/**
 * A payout bigger than the share it comes from.
 *
 * Not refused — the pool may well decide to pay somebody more than they put in,
 * and a ledger that refuses to record what happened is a ledger people stop
 * using. But a bare negative figure under "their share after this" reads as a
 * bug, and a member who thinks the app is broken checks nothing.
 */
class PayoutOverdrawTest {

    private val config = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)

    private fun funded(): Session {
        var s = Session(
            book = LedgerBook(
                members = DevSeed.MEMBERS,
                accounts = DevSeed.ACCOUNTS,
                pockets = DevSeed.POCKETS,
            ),
            config = config,
            actingAs = DevSeed.BONNIE,
        )
        s = s.contribute(DevSeed.KANGIRI, 100_000)
        val id = s.book.pending().last().id
        return s.actAs(DevSeed.BRIAN).confirm(id, DevSeed.BRIAN)
    }

    @Test
    fun `a payout within the share says nothing`() {
        assertNull(funded().book.payoutOverdrawLine(DevSeed.KANGIRI, 50_000))
    }

    @Test
    fun `a payout of exactly the share says nothing`() {
        assertNull(funded().book.payoutOverdrawLine(DevSeed.KANGIRI, 100_000))
    }

    @Test
    fun `a payout past the share names the excess and the person`() {
        val line = funded().book.payoutOverdrawLine(DevSeed.KANGIRI, 150_000)!!
        assertTrue("KSh 500.00" in line, "the excess is not named: $line")
        assertTrue("Kang'iri" in line, "the person is not named: $line")
        assertTrue("recorded, not blocked" in line, "it must not read as a refusal: $line")
    }

    @Test
    fun `it warns but does not refuse`() {
        val s = funded().actAs(DevSeed.BONNIE).payOut(DevSeed.KANGIRI, 150_000)
        assertTrue(s.notice !is Notice.Refused, "flag, do not block")
        assertEquals(1, s.book.pending().size)
    }
}
