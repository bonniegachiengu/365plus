package online.vyybandasky.plus365.core.book

import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.governance.Decision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * `state()` folds the whole log. One render of the home screen asks for it
 * repeatedly — cash on hand, the member cards, the activity list, the overdraw
 * report and the rest each want balances — and Compose re-runs that on every
 * recomposition.
 *
 * Today the seed has a dozen entries and it does not matter. Brian's real history
 * is years of them, and the same screen would fold it several times a frame.
 *
 * A `LedgerBook` is immutable, so the fold has exactly one answer for the life of
 * the object. This measures that it is computed once and reused, rather than
 * asserting it in a comment.
 */
class FoldCostTest {

    private val config = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)

    /**
     * Recorded *and confirmed*. The first version of this fixture only recorded,
     * and every balance stayed at zero because a pending entry moves nothing —
     * which made two of these tests pass for the wrong reason and fail for the
     * right one.
     */
    private fun bigBook(entries: Int): LedgerBook {
        var b = LedgerBook(
            members = DevSeed.MEMBERS,
            accounts = DevSeed.ACCOUNTS,
            pockets = DevSeed.POCKETS,
        )
        repeat(entries) { i ->
            val recorded = b.record(
                id = "e$i",
                type = EntryType.CONTRIBUTION,
                amountCents = 1_000,
                memberId = DevSeed.BONNIE,
                recordedBy = DevSeed.BONNIE,
                config = config,
            )
            b = (recorded as Decision.Allowed).value.book
            b = (b.confirm("e$i", DevSeed.BRIAN, config) as Decision.Allowed).value.book
        }
        return b
    }

    @Test
    fun the_fold_is_computed_once_per_book() {
        val b = bigBook(50)
        assertSame(b.state(), b.state(), "the same book folded twice returns two objects")
    }

    @Test
    fun a_changed_book_folds_again() {
        // The memo must belong to the book, not outlive it. A stale balance is
        // worse than a slow one.
        val b = bigBook(5)
        val before = b.state().poolCashCents
        val recorded = (b.record(
            id = "extra",
            type = EntryType.CONTRIBUTION,
            amountCents = 7_700,
            memberId = DevSeed.BONNIE,
            recordedBy = DevSeed.BONNIE,
            config = config,
        ) as Decision.Allowed).value.book
        assertEquals(
            before,
            recorded.state().poolCashCents,
            "a pending entry moved money before anybody agreed to it",
        )
        val after = (recorded.confirm("extra", DevSeed.BRIAN, config) as Decision.Allowed).value.book
        assertTrue(after.state().poolCashCents != before, "a confirmed entry did not change the fold")
    }

    @Test
    fun a_copy_folds_its_own_entries() {
        // `copy` is how every mutation in this codebase produces a new book, so
        // a memo that survived a copy would hand back the old balances.
        val b = bigBook(5)
        val fewer = b.copy(entries = b.entries.take(2))
        assertTrue(
            fewer.state().poolCashCents < b.state().poolCashCents,
            "a copy with fewer entries reported the original's balances",
        )
    }

    @Test
    fun repeated_reads_agree_with_a_fresh_fold() {
        val b = bigBook(30)
        val fresh = online.vyybandasky.plus365.core.ledger.fold(b.entries, b.loans)
        assertEquals(fresh.poolCashCents, b.state().poolCashCents)
        assertEquals(fresh.cashAtHandCents, b.state().cashAtHandCents)
        assertEquals(fresh.allocatedCents, b.state().allocatedCents)
        assertTrue(b.state().balances)
    }
}
