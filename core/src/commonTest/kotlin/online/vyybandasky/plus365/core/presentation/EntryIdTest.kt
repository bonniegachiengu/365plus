package online.vyybandasky.plus365.core.presentation

import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.store.InMemoryStore
import online.vyybandasky.plus365.core.store.encodeBook
import online.vyybandasky.plus365.core.store.trySave
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Two entries must never share an id, across a restart or otherwise.
 *
 * This one is not a hypothetical worry about tidiness. `record` treats a
 * repeated id as *the same fact* and returns the existing entry as allowed —
 * which is correct for a retried save and catastrophic for a genuinely new
 * entry, because the member is told "Recorded" and nothing was added.
 *
 * The ids come from a counter restored from `nextSeq`, and the argument that it
 * can never go backwards is short enough to be convincing and short enough to be
 * wrong. So it is a test.
 */
class EntryIdTest {

    private val now = Instant.parse("2026-08-29T09:00:00Z")
    private val config = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)

    private fun fresh() = Session(
        book = LedgerBook(
            members = DevSeed.MEMBERS,
            accounts = DevSeed.ACCOUNTS,
            pockets = DevSeed.POCKETS,
        ),
        config = config,
        actingAs = DevSeed.BONNIE,
    )

    private fun ids(book: LedgerBook) = book.entries.map { it.id }

    @Test
    fun `ids are unique after a long run of mixed operations`() {
        var s = fresh()
        repeat(12) { i ->
            s = s.contribute(DevSeed.BONNIE, 1_000L * (i + 1), now)
            s = s.lend(DevSeed.KANGIRI, 5_000L, at = now)
            s = s.moveMoney(DevSeed.POCHI, DevSeed.ZIIDI, 100L, now)
        }
        val all = ids(s.book)
        assertEquals(all.size, all.toSet().size, "duplicate ids: ${all.groupBy { it }.filter { it.value.size > 1 }.keys}")
    }

    @Test
    fun `ids do not collide with what was already stored after a restart`() {
        // The dangerous shape: record, save, reopen, record again. A counter
        // that restarted lower would hand out an id the book already has, and
        // record() would quietly return the old entry as though it were new.
        var s = fresh()
        repeat(5) { s = s.contribute(DevSeed.BONNIE, 1_000, now) }
        val store = InMemoryStore(encodeBook(s.book))
        val before = ids(s.book).toSet()

        var reopened = Session.restored(store, now)
        val countBefore = reopened.book.entries.size
        repeat(5) { reopened = reopened.contribute(DevSeed.KANGIRI, 2_000, now) }

        val after = ids(reopened.book)
        assertEquals(after.size, after.toSet().size, "an id was handed out twice across a restart")
        assertEquals(
            countBefore + 5,
            reopened.book.entries.size,
            "an entry was silently swallowed as a duplicate id",
        )
        assertTrue(before.all { it in after.toSet() }, "a stored entry disappeared")
    }

    @Test
    fun `a loan appending three entries does not desynchronise the counter`() {
        // A loan is three entries and one operation, so seq and the id counter
        // advance at different rates. That is the shape most likely to break it.
        var s = fresh()
        s = s.lend(DevSeed.KANGIRI, 10_000, at = now)
        val store = InMemoryStore(encodeBook(s.book))

        var reopened = Session.restored(store, now)
        reopened = reopened.contribute(DevSeed.BONNIE, 1_000, now)
        val all = ids(reopened.book)
        assertEquals(all.size, all.toSet().size, "the loan's extra legs pushed an id into a collision")
    }

    @Test
    fun `a repeated save does not duplicate anything`() {
        var s = fresh()
        s = s.contribute(DevSeed.BONNIE, 1_000, now)
        val store = InMemoryStore()
        store.trySave(s.book)
        store.trySave(s.book)
        val reopened = Session.restored(store, now)
        assertEquals(s.book.entries.size, reopened.book.entries.size)
    }

    @Test
    fun `pending ids are the ones the confirm screen will use`() {
        var s = fresh()
        s = s.contribute(DevSeed.BONNIE, 1_000, now)
        val pendingIds = s.book.pending().map { it.id }
        assertEquals(pendingIds.size, pendingIds.toSet().size)
        assertTrue(pendingIds.all { it.isNotBlank() })
    }
}
