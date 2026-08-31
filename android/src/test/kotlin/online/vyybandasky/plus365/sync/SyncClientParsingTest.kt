package online.vyybandasky.plus365.sync

import online.vyybandasky.plus365.core.store.decodeBook
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The phone reading what the laptop said.
 *
 * Both functions under test pick fields out of JSON with string arithmetic
 * rather than a parser, which is defensible only because the same codebase
 * writes the response — and is exactly the sort of thing that works on the
 * example somebody had in mind and fails on the real one.
 *
 * So the fixture is a real one: `sync-response.json` is a response the laptop
 * actually produced on 31 Aug 2026, captured off the wire during the first
 * cross-device confirmation, not a shape written by hand to match the code.
 */
class SyncClientParsingTest {

    private fun realResponse(): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("sync-response.json")) {
            "the captured sync response is missing"
        }.bufferedReader().readText()

    @Test
    fun the_ledger_comes_out_of_a_real_response_and_decodes() {
        val json = assertNotNull(realResponse().ledgerPart(), "no ledger found in the response")
        val book = decodeBook(json).getOrNull()
        assertNotNull(book, "the extracted ledger did not decode")
        // The entry that was pushed from one device and confirmed on another.
        assertTrue(book.entries.any { it.id == "PHONE-A-1" })
    }

    /**
     * The figures survive the trip.
     *
     * A parser that returns a decodable but truncated ledger is the dangerous
     * failure — it would give the phone a smaller, entirely valid-looking book.
     * So this asserts the money, not just that something parsed.
     */
    @Test
    fun and_the_balances_are_the_ones_the_laptop_had() {
        val book = decodeBook(realResponse().ledgerPart()!!).getOrNull()!!
        val s = book.state()
        assertEquals(443_500L, s.cashAtHandCents)
        assertTrue(s.balances)
        assertEquals(s.cashAtHandCents, s.perAccount.values.sum())
        assertEquals(s.cashAtHandCents, s.perPocket.values.sum())
    }

    @Test
    fun the_summary_counts_what_actually_happened() {
        // This response advanced exactly one entry and added none.
        assertEquals("1 confirmation shared.", realResponse().summarise())
    }

    @Test
    fun an_empty_report_says_so_rather_than_saying_nothing() {
        val quiet = """{"ok":true,"report":{"added":[],"advanced":[],"conflicted":[],""" +
            """"approvalsPooled":[],"settledByPooling":[],"definitionsAdded":[]},"ledger":{}}"""
        assertEquals("Already up to date.", quiet.summarise())
    }

    @Test
    fun several_of_each_are_counted_and_named() {
        val busy = """{"ok":true,"report":{"added":["a","b"],"advanced":["c"],""" +
            """"conflicted":["d","e"],"approvalsPooled":[],"settledByPooling":[],""" +
            """"definitionsAdded":[]},"ledger":{}}"""
        assertEquals(
            "2 entries sent up, 1 confirmation shared, " +
                "2 entries disagree — the laptop's versions stand.",
            busy.summarise(),
        )
    }

    @Test
    fun rubbish_comes_back_as_nothing_rather_than_half_a_ledger() {
        assertEquals(null, "not json at all".ledgerPart())
        assertEquals(null, """{"ok":true,"report":{}}""".ledgerPart())
    }
}
