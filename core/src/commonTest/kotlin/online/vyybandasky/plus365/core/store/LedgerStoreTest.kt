package online.vyybandasky.plus365.core.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.book.confirm
import online.vyybandasky.plus365.core.book.record
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.book.Recorded
import online.vyybandasky.plus365.core.governance.Decision

private fun Decision<Recorded>.book(): LedgerBook =
    assertIs<Decision.Allowed<Recorded>>(this).value.book

class LedgerStoreTest {

    private val seeded = DevSeed.book()

    @Test
    fun a_book_survives_a_round_trip_unchanged() {
        val back = decodeBook(encodeBook(seeded)).getOrThrow()
        assertEquals(seeded.entries, back.entries)
        assertEquals(seeded.loans, back.loans)
        assertEquals(seeded.members, back.members)
        assertEquals(seeded.accounts, back.accounts)
        assertEquals(seeded.nextSeq, back.nextSeq)
    }

    @Test
    fun the_pockets_themselves_survive_not_just_the_ids_pointing_at_them() {
        // Entries keep their pocketId either way, so the fold still splits the
        // money correctly — which is exactly why losing the definitions stayed
        // invisible until a screen had nothing left to label the split with.
        val back = decodeBook(encodeBook(seeded)).getOrThrow()
        assertEquals(seeded.pockets, back.pockets)
        assertTrue(back.pockets.isNotEmpty())
        assertEquals(seeded.state().perPocket, back.state().perPocket)
    }

    @Test
    fun every_named_thing_the_book_holds_survives_a_round_trip() {
        val back = decodeBook(encodeBook(seeded)).getOrThrow()
        assertEquals(seeded.members, back.members)
        assertEquals(seeded.accounts, back.accounts)
        assertEquals(seeded.pockets, back.pockets)
        assertEquals(seeded.loans, back.loans)
    }

    @Test
    fun the_balances_come_back_identical_because_they_are_refolded_not_stored() {
        val back = decodeBook(encodeBook(seeded)).getOrThrow()
        assertEquals(seeded.state().poolCashCents, back.state().poolCashCents)
        assertEquals(seeded.state().cashAtHandCents, back.state().cashAtHandCents)
        assertEquals(seeded.state().totalOutstandingCents, back.state().totalOutstandingCents)
        assertEquals(seeded.state().perAccount, back.state().perAccount)
        assertEquals(seeded.state().loans, back.state().loans)
    }

    @Test
    fun no_derived_total_is_written_to_the_file() {
        // If a total were stored it could drift from the log. Check for stored
        // FIELDS rather than bare words: a pasted M-Pesa message legitimately
        // contains "balance" in its own text, and that is evidence, not a total.
        val text = encodeBook(seeded)
        for (field in listOf("poolCash", "cashAtHand", "totalOutstanding", "stake", "debt")) {
            assertTrue(
                !text.contains("\"$field"),
                "the file should hold no derived total, found a \"$field\" field",
            )
        }
    }

    @Test
    fun a_pasted_message_is_stored_but_its_phone_number_is_not() {
        val text = encodeBook(seeded)
        assertTrue(text.contains("QGH7X2K9LM"), "the shared code is the proof and must persist")
        assertTrue(!text.contains("0700000000"), "a phone number reached the stored file")
        assertTrue(!text.contains("0700000001"), "a phone number reached the stored file")
    }

    @Test
    fun confirmations_survive_so_the_rule_is_not_relitigated_on_load() {
        val back = decodeBook(encodeBook(seeded)).getOrThrow()
        val confirmed = back.entries.filter { it.state == EntryState.CONFIRMED }
        assertTrue(confirmed.isNotEmpty())
        for (e in confirmed) {
            assertTrue(e.confirmedByMemberId != null)
            assertTrue(e.recordedByMemberId != e.confirmedByMemberId)
        }
    }

    @Test
    fun a_pending_entry_is_still_pending_after_a_reload() {
        val back = decodeBook(encodeBook(seeded)).getOrThrow()
        assertEquals(seeded.pending().size, back.pending().size)
        assertEquals(0L, back.state().poolCashCents - seeded.state().poolCashCents)
    }

    @Test
    fun a_reloaded_book_can_still_be_recorded_and_confirmed_against() {
        var back = decodeBook(encodeBook(seeded)).getOrThrow()
        val before = back.state().poolCashCents

        back = back.record(
            id = "after-reload",
            type = EntryType.CONTRIBUTION,
            amountCents = 100_000,
            memberId = DevSeed.BONNIE,
            recordedBy = DevSeed.BONNIE,
            config = DevSeed.DEV_CONFIG,
        ).book()

        // seq continues from the stored counter rather than restarting at 1
        assertEquals(seeded.nextSeq, back.entry("after-reload")!!.seq)

        back = back.confirm("after-reload", DevSeed.BRIAN, DevSeed.DEV_CONFIG).book()

        assertEquals(before + 100_000, back.state().poolCashCents)
    }

    @Test
    fun an_empty_store_reports_empty_rather_than_pretending() {
        val f = assertIs<LoadException>(decodeBook(null).exceptionOrNull())
        assertIs<LoadFailure.Empty>(f.failure)
        assertIs<LoadFailure.Empty>(
            assertIs<LoadException>(decodeBook("   ").exceptionOrNull()).failure,
        )
    }

    @Test
    fun a_truncated_file_fails_readably_instead_of_crashing() {
        val half = encodeBook(seeded).take(120)
        val f = assertIs<LoadException>(decodeBook(half).exceptionOrNull())
        assertIs<LoadFailure.Unreadable>(f.failure)
    }

    @Test
    fun a_file_from_another_version_is_refused_by_name() {
        val text = encodeBook(seeded).replaceFirst("\"version\": 1", "\"version\": 99")
        val f = assertIs<LoadException>(decodeBook(text).exceptionOrNull())
        assertEquals(99, assertIs<LoadFailure.WrongVersion>(f.failure).found)
    }

    @Test
    fun loadOr_falls_back_when_there_is_nothing_readable() {
        val store = InMemoryStore()
        assertEquals(0, store.loadOr { LedgerBook() }.entries.size)

        store.save(seeded)
        assertEquals(seeded.entries.size, store.loadOr { LedgerBook() }.entries.size)

        store.clear()
        assertEquals(0, store.loadOr { LedgerBook() }.entries.size)
    }

    @Test
    fun opening_an_empty_store_writes_the_baseline_rather_than_only_showing_it() {
        val store = InMemoryStore()
        assertTrue(store.read() == null)

        val opened = store.openOrSeed { seeded }

        assertEquals(seeded.entries.size, opened.entries.size)
        assertTrue(store.read() != null, "the baseline must be on disk, not just on screen")
        // A second open reads the file back rather than re-seeding.
        assertEquals(opened.entries.size, store.openOrSeed { LedgerBook() }.entries.size)
    }

    @Test
    fun opening_never_overwrites_a_book_that_read_back_fine() {
        val store = InMemoryStore()
        store.save(seeded)
        val opened = store.openOrSeed { LedgerBook() }
        assertEquals(seeded.entries.size, opened.entries.size, "the stored book wins over the seed")
    }

    @Test
    fun saving_twice_leaves_one_book_not_two() {
        val store = InMemoryStore()
        store.save(seeded)
        store.save(seeded)
        assertEquals(seeded.entries.size, store.loadOr { LedgerBook() }.entries.size)
    }
}
