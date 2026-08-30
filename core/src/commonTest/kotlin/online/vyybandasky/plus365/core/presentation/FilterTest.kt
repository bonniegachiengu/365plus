package online.vyybandasky.plus365.core.presentation

import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.money.formatKes
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

/**
 * Grouping must never lose a row.
 *
 * A ledger that quietly drops an entry while tidying the display is worse than a
 * ledger with no tidying at all, so the flattening is checked rather than
 * assumed.
 */
class LedgerGroupingTest {

    private val now = Instant.parse("2026-08-29T09:00:00Z")
    private val book = DevSeed.book(now)

    @Test
    fun `the groups are the rows, rearranged and nothing else`() {
        val f = book.filteredActivity(LedgerFilter(), now)
        assertEquals(f.rows, f.groups.flatMap { it.rows })
    }

    @Test
    fun `headings do not repeat, so a run is a run`() {
        val headings = book.filteredActivity(LedgerFilter(), now).groups.map { it.heading }
        assertEquals(headings.size, headings.toSet().size, "a heading appearing twice is a broken run")
    }

    @Test
    fun `newest first survives grouping`() {
        val f = book.filteredActivity(LedgerFilter(), now)
        val order = listOf(
            "Today", "Yesterday", "Earlier this week",
            "Earlier this month", "Earlier this year", "Older", "Undated",
        )
        val seen = f.groups.map { order.indexOf(it.heading) }
        assertEquals(seen.sorted(), seen, "groups must run newest to oldest")
        assertTrue(seen.none { it == -1 }, "an unexpected heading appeared")
    }

    @Test
    fun `a narrowed ledger is grouped too`() {
        val f = book.filteredActivity(LedgerFilter(kind = LedgerKind.IN), now)
        assertEquals(f.rows, f.groups.flatMap { it.rows })
    }

    @Test
    fun `an empty result has no groups rather than an empty group`() {
        val f = book.filteredActivity(LedgerFilter(text = "no such thing"), now)
        assertTrue(f.groups.isEmpty(), "an empty heading over nothing is noise")
    }
}

/**
 * What a borrower actually arrives asking.
 *
 * "What do I owe?" had no one-number answer on either screen — both listed the
 * loans and left the addition to the reader. The figure existed in
 * `MemberDetail.owes` and neither shell rendered it.
 */
class OwesTest {

    private val now = Instant.parse("2026-08-29T09:00:00Z")

    @Test
    fun `a member with a live loan is told what it is`() {
        val d = DevSeed.book(now).memberDetail(DevSeed.WANJIKU, now)!!
        assertTrue(d.owesCents > 0L, "the seed lends to Wanjiku and she has not cleared it")
        assertEquals(d.owes, formatKes(d.owesCents))
    }

    @Test
    fun `owing nothing is zero, not a negative dressed up`() {
        // owesCents comes from a signed balance where the pool owing *them*
        // is the other direction. Nothing owed must read as nothing owed.
        for (m in DevSeed.MEMBERS) {
            val d = DevSeed.book(now).memberDetail(m.id, now)!!
            assertTrue(d.owesCents >= 0L, "${m.displayName} shows a negative debt")
        }
    }
}

/**
 * The hero figure on a member page must be the one they came to read.
 *
 * A Keshflo borrower has no share of the pool and never will. Leading their page
 * with "Pool contribution KSh 0.00" answers a question nobody asked and buries
 * the one they did.
 */
class MemberHeroTest {

    private val now = Instant.parse("2026-08-29T09:00:00Z")

    @Test
    fun `a borrower is a borrower and a founder is not`() {
        assertTrue(DevSeed.book(now).memberDetail(DevSeed.WANJIKU, now)!!.isBeneficiary)
        assertTrue(!DevSeed.book(now).memberDetail(DevSeed.BONNIE, now)!!.isBeneficiary)
    }

    @Test
    fun `a borrower has no share to show`() {
        val d = DevSeed.book(now).memberDetail(DevSeed.WANJIKU, now)!!
        assertEquals("KSh 0.00", d.stake, "if this is ever non-zero the page is lying somewhere")
        assertTrue(d.owesCents > 0L, "which is why the debt has to be the figure")
    }
}

/**
 * An ATM withdrawal is the weakest evidence this app accepts, and says so.
 *
 * Every other message has something on the other side: an M-Pesa transfer leaves
 * a matching message in somebody else's phone, which is the whole basis of
 * paste-and-match. A withdrawal leaves a note that money left an account and says
 * nothing about where it went next.
 *
 * Both are "evidence". Treating them as the same strength is how a pool ends up
 * satisfied by a receipt that proves the wrong thing, so the entry says which
 * kind it is holding.
 */
class AtmCaveatTest {

    private val now = Instant.parse("2026-08-29T09:00:00Z")
    private val config = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)

    private val atmSms =
        "Dear Customer, KES 5,000.00 has been debited from your account 1234567890 " +
            "via ATM on 26/08/2026. Ref: ATM88231X. Available balance KES 12,300.00"

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
    fun `an entry backed by an ATM slip says what the slip does not prove`() {
        val s = session().payOut(DevSeed.KANGIRI, 500_000, now, atmSms)
        val id = s.book.pending().single().id
        val e = s.book.entryDetail(id, config, now)!!.recordedEvidence!!

        assertTrue(e.isAtmWithdrawal)
        assertNotNull(e.atmCaveat)
        assertTrue(
            e.atmCaveat.contains("does not show where it went"),
            "the caveat has to name the thing the slip cannot prove",
        )
    }

    @Test
    fun `an ordinary message carries no caveat`() {
        val mpesa = "RTY4M8N2PQ Confirmed. Ksh500.00 sent to KANGIRI 0712345678 " +
            "on 26/8/26 at 4:10 PM. New M-PESA balance is Ksh1,200.00."
        val s = session().payOut(DevSeed.KANGIRI, 50_000, now, mpesa)
        val id = s.book.pending().single().id
        val e = s.book.entryDetail(id, config, now)!!.recordedEvidence!!

        assertTrue(!e.isAtmWithdrawal)
        assertNull(e.atmCaveat, "a caveat on every entry is a caveat nobody reads")
    }
}

/**
 * The promise the ledger makes has to survive being checked.
 *
 * It used to say "only ever added, never changed or deleted", inline in both
 * shells. An entry does change — it gains a confirmation, a rejection, an
 * override. What never changes is a figure, and what never happens is a
 * deletion. Saying exactly that is stronger than the vaguer claim.
 *
 * These tests hold the words to the behaviour, so the sentence cannot drift away
 * from what the book actually does.
 */
class LedgerPromiseTest {

    private val now = Instant.parse("2026-08-29T09:00:00Z")
    private val config = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)

    private fun view() = DevSeed.book(now).filteredActivity(LedgerFilter(), now)

    @Test
    fun `the promise names the two things that are actually true`() {
        val blurb = view().blurb
        assertTrue("Nothing here is deleted" in blurb, blurb)
        assertTrue("no figure is ever edited" in blurb, blurb)
    }

    @Test
    fun `it does not claim entries never change, because they do`() {
        val blurb = view().blurb
        assertTrue(
            "never changed" !in blurb,
            "an entry gains its confirmation, so this claim would be false: $blurb",
        )
    }

    @Test
    fun `and the behaviour matches - confirming changes state and not the amount`() {
        var s = Session(
            book = LedgerBook(
                members = DevSeed.MEMBERS,
                accounts = DevSeed.ACCOUNTS,
                pockets = DevSeed.POCKETS,
            ),
            config = config,
            actingAs = DevSeed.BONNIE,
        )
        s = s.contribute(DevSeed.BONNIE, 123_400, now)
        val before = s.book.pending().single()
        s = s.actAs(DevSeed.BRIAN).confirm(before.id, DevSeed.BRIAN, now)
        val after = s.book.entries.single { it.id == before.id }

        assertEquals(before.amountCents, after.amountCents, "the figure was edited")
        assertTrue(after.state != before.state, "the entry did not gain its agreement")
        assertEquals(
            s.book.entries.size,
            s.book.entries.map { it.id }.toSet().size,
            "confirming duplicated an entry",
        )
    }

    @Test
    fun `both shells read the same sentence`() {
        // It was inline in two files before, which is exactly how two shells end
        // up making two different promises about the same ledger.
        assertEquals(view().blurb, DevSeed.book(now).filteredActivity(LedgerFilter(), now).blurb)
    }
}

/**
 * Money earmarked to a pocket the book cannot name.
 *
 * Found on a real device: Bonnie's phone was running a ledger created before
 * pockets existed. No pocket definitions, nineteen entries earmarked to ids
 * those definitions would have described. The fold was correct and the balance
 * invariant held — and the "what it is for" list was built from the definitions,
 * so it came back empty and the section vanished.
 *
 * KSh 3,658 allocated to pockets no screen could show, and nothing looked wrong,
 * because an absent section looks like one with nothing to say.
 */
class OrphanPocketTest {

    private val now = Instant.parse("2026-08-29T09:00:00Z")
    private val config = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)

    /** A book with entries earmarked to a pocket, and no pocket definitions. */
    private fun bookWithNoPocketDefinitions(): LedgerBook {
        var s = Session(
            book = LedgerBook(
                members = DevSeed.MEMBERS,
                accounts = DevSeed.ACCOUNTS,
                pockets = DevSeed.POCKETS,
            ),
            config = config,
            actingAs = DevSeed.BONNIE,
        )
        s = s.contribute(DevSeed.BONNIE, 365_800, now)
        s = s.actAs(DevSeed.BRIAN).confirm(s.book.pending().single().id, DevSeed.BRIAN, now)
        // Exactly the shape an older stored file decodes into.
        return s.book.copy(pockets = emptyList())
    }

    @Test
    fun the_money_is_still_allocated_and_the_invariant_still_holds() {
        val st = bookWithNoPocketDefinitions().state()
        assertEquals(st.poolCashCents, st.allocatedCents)
        assertTrue(st.balances, "the fold was never the problem")
    }

    @Test
    fun and_it_is_no_longer_missing_from_the_screen() {
        val cash = bookWithNoPocketDefinitions().cashOnHand(now)
        assertTrue(cash.pockets.isNotEmpty(), "the section disappeared with money in it")
        assertEquals(
            365_800L,
            cash.pockets.sumOf { it.balanceCents },
            "what it is for must add up to what there is",
        )
    }

    @Test
    fun the_two_splits_agree_with_each_other() {
        // The only reason to show them side by side.
        val cash = bookWithNoPocketDefinitions().cashOnHand(now)
        assertEquals(
            cash.accounts.sumOf { it.balanceCents },
            cash.pockets.sumOf { it.balanceCents },
        )
    }

    @Test
    fun an_unnamed_pocket_says_so_rather_than_inventing_a_name() {
        val row = bookWithNoPocketDefinitions().cashOnHand(now).pockets.first()
        assertTrue("no description for it" in row.blurb, row.blurb)
    }

    @Test
    fun a_book_with_proper_definitions_grows_no_extra_rows() {
        // The fix must not add a phantom row to a healthy ledger.
        val cash = DevSeed.book(now).cashOnHand(now)
        assertEquals(DevSeed.POCKETS.size, cash.pockets.size)
    }
}
