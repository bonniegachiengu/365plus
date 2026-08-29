package online.vyybandasky.plus365.core.presentation

import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.governance.ActorConfig
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A book with the members in it and nothing else.
 *
 * This is not a hypothetical. Brian's real history has not been loaded yet, and
 * the plan has always been to bring the members over first and the entries
 * afterwards — so the very first thing the real ledger will ever be is exactly
 * this: three people, three accounts, no money, no history.
 *
 * Every screen has to survive it. The seed hides the whole question, because the
 * seed always has entries in it, so nothing anybody has looked at so far has ever
 * been the empty case.
 */
class EmptyBookTest {

    private val now = Instant.parse("2026-08-29T09:00:00Z")
    private val config = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)

    private fun empty() = LedgerBook(
        members = DevSeed.MEMBERS,
        accounts = DevSeed.ACCOUNTS,
        pockets = DevSeed.POCKETS,
    )

    @Test
    fun `the home screen renders with nothing in the book`() {
        val b = empty()
        val cash = b.cashOnHand(now)
        assertEquals("KSh 0.00", cash.total)
        assertTrue(cash.accounts.isNotEmpty(), "the accounts exist even at zero")
        assertTrue(cash.pockets.isNotEmpty(), "so do the pockets")
        assertTrue(b.activity(now, limit = 6).isEmpty())
        assertTrue(b.pendingActs(config, now).isEmpty())
        assertTrue(b.overrideTasks(config, now).isEmpty())
        assertTrue(!b.overdrawReport(now).any)
    }

    @Test
    fun `member cards render at zero`() {
        val b = empty()
        assertEquals(DevSeed.MEMBERS.count { it.isFounder }, b.founderCards().size)
        for (c in b.founderCards()) {
            assertEquals("KSh 0.00", c.stake)
            assertTrue(c.standingLine.isNotBlank(), "an empty standing still needs words")
        }
    }

    @Test
    fun `the profile renders at zero`() {
        val p = empty().profile(DevSeed.BONNIE, config)!!
        assertEquals(0, p.recordedCount)
        assertEquals(0, p.confirmedCount)
        assertEquals(0, p.overrodeCount)
        assertTrue(p.buildLine.isNotBlank())
    }

    @Test
    fun `member detail renders at zero`() {
        val d = empty().memberDetail(DevSeed.KANGIRI, now)
        assertNotNull(d, "a member with no history is still a member")
        assertEquals("KSh 0.00", d.stake)
        assertEquals(0, d.contributionCount)
        assertEquals(0, d.activeLoanCount)
        assertTrue(d.activity.isEmpty())
    }

    @Test
    fun `a member the book has never heard of reads as absent, not as a crash`() {
        // A stale link — a screen holding an id the book no longer has. The
        // sibling `entryDetail` had this right; this one used to throw.
        assertNull(empty().memberDetail("nobody", now))
        assertNull(empty().profile("nobody", config))
    }

    @Test
    fun `the ledger renders at zero`() {
        assertTrue(empty().activity(now, everything = true).isEmpty())
    }

    @Test
    fun `nothing is repayable and nothing earns yet`() {
        assertTrue(empty().repayableLoans().isEmpty())
    }

    @Test
    fun `the first entry into an empty book works`() {
        // The moment that matters: the ledger's first-ever line.
        var s = Session(book = empty(), config = config, actingAs = DevSeed.BONNIE)
        s = s.contribute(DevSeed.BONNIE, 100_000, now)
        val id = s.book.pending().single().id
        s = s.actAs(DevSeed.BRIAN).confirm(id, DevSeed.BRIAN, now)

        assertEquals("KSh 1,000.00", s.book.cashOnHand(now).total)
        assertTrue(s.book.state().balances, "the invariant holds from the first entry")
    }
}
