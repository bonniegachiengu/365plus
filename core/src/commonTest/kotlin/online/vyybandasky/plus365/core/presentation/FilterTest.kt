package online.vyybandasky.plus365.core.presentation

import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.domain.EntryType
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A narrowed ledger must never read as the whole one.
 *
 * That is the property worth defending here. Filtering itself is arithmetic; the
 * danger is a person looking at four rows, believing that is the record, and
 * concluding their money has gone missing. Every narrowed view says how much it
 * is hiding, and the empty case says it in words rather than showing nothing.
 */
class FilterTest {

    private val now = Instant.parse("2026-08-29T09:00:00Z")
    private val book = DevSeed.book(now)

    @Test
    fun `no filter shows the whole record and says nothing about narrowing`() {
        val f = book.filteredActivity(LedgerFilter(), now)
        assertEquals(book.activity(now, everything = true).size, f.rows.size)
        assertNull(f.narrowedLine, "nothing is hidden, so there is nothing to warn about")
        assertNull(f.shownTotal)
        assertNull(f.emptyLine)
    }

    @Test
    fun `a narrowed view always says how much it is hiding`() {
        val f = book.filteredActivity(LedgerFilter(kind = LedgerKind.OUT), now)
        assertNotNull(f.narrowedLine, "a filtered ledger that looks whole is a lie")
        assertTrue(f.narrowedLine.contains("of ${f.totalCount}"))
        assertTrue(f.rows.size < f.totalCount, "the seed has money going both ways")
    }

    @Test
    fun `money in and money out do not overlap and together cover the movements`() {
        val all = book.filteredActivity(LedgerFilter(), now).rows.map { it.entryId }.toSet()
        val ins = book.filteredActivity(LedgerFilter(kind = LedgerKind.IN), now)
            .rows.map { it.entryId }.toSet()
        val outs = book.filteredActivity(LedgerFilter(kind = LedgerKind.OUT), now)
            .rows.map { it.entryId }.toSet()
        val internal = book.filteredActivity(LedgerFilter(kind = LedgerKind.INTERNAL), now)
            .rows.map { it.entryId }.toSet()

        assertTrue((ins intersect outs).isEmpty(), "one entry cannot be both directions")
        assertTrue((ins intersect internal).isEmpty())
        assertTrue((outs intersect internal).isEmpty())

        // Every kind is accounted for. A type nobody classified would silently
        // vanish from all three filters at once, which is the failure that would
        // be hardest to notice by looking.
        val uncovered = all - ins - outs - internal
        assertTrue(
            uncovered.isEmpty(),
            "these entries fall through every filter: $uncovered",
        )
    }

    @Test
    fun `every entry type is classified`() {
        // The set-based check above only covers types the seed happens to use.
        // This one covers the enum.
        val unclassified = EntryType.entries.filter { t ->
            t != EntryType.REVERSAL &&
                !LedgerKind.IN.matches(t) &&
                !LedgerKind.OUT.matches(t) &&
                !LedgerKind.INTERNAL.matches(t)
        }
        assertTrue(unclassified.isEmpty(), "unclassified: $unclassified")
    }

    @Test
    fun `filtering by member shows only their lines`() {
        val f = book.filteredActivity(LedgerFilter(memberId = DevSeed.KANGIRI), now)
        assertTrue(f.rows.isNotEmpty())
        for (row in f.rows) {
            val e = book.entries.first { it.id == row.entryId }
            assertEquals(DevSeed.KANGIRI, e.memberId)
        }
        assertTrue(f.narrowedLine!!.contains(book.displayName(DevSeed.KANGIRI)))
    }

    @Test
    fun `searching finds a transaction code`() {
        val code = book.activity(now, everything = true)
            .firstNotNullOf { it.reference }
        val f = book.filteredActivity(LedgerFilter(text = code), now)
        assertTrue(f.rows.isNotEmpty(), "a pasted code is the thing people search for")
        assertTrue(f.rows.all { it.reference == code })
    }

    @Test
    fun `searching is case-insensitive`() {
        val code = book.activity(now, everything = true).firstNotNullOf { it.reference }
        assertEquals(
            book.filteredActivity(LedgerFilter(text = code), now).rows.size,
            book.filteredActivity(LedgerFilter(text = code.lowercase()), now).rows.size,
        )
    }

    @Test
    fun `a filter that matches nothing says so, and says what is still there`() {
        val f = book.filteredActivity(LedgerFilter(text = "no such thing"), now)
        assertTrue(f.rows.isEmpty())
        assertNotNull(f.emptyLine)
        assertTrue(
            f.emptyLine.contains("${f.totalCount}"),
            "an empty screen must not imply an empty ledger",
        )
    }

    @Test
    fun `an empty book says the ledger is new, not that the search failed`() {
        val empty = online.vyybandasky.plus365.core.book.LedgerBook(
            members = DevSeed.MEMBERS,
            accounts = DevSeed.ACCOUNTS,
            pockets = DevSeed.POCKETS,
        )
        val f = empty.filteredActivity(LedgerFilter(), now)
        assertNotNull(f.emptyLine)
        assertTrue(f.emptyLine.contains("first entry"))
    }

    @Test
    fun `filters combine`() {
        val f = book.filteredActivity(
            LedgerFilter(kind = LedgerKind.IN, memberId = DevSeed.KANGIRI),
            now,
        )
        for (row in f.rows) {
            val e = book.entries.first { it.id == row.entryId }
            assertEquals(DevSeed.KANGIRI, e.memberId)
            assertTrue(LedgerKind.IN.matches(e.type))
        }
        assertTrue(f.narrowedLine!!.contains("money in"))
    }
}
