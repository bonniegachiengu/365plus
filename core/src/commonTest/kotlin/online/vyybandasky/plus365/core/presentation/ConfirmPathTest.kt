package online.vyybandasky.plus365.core.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.store.InMemoryStore

private val T0 = Instant.parse("2026-08-29T10:00:00Z")

private fun sent(code: String, sh: Int) =
    "$code Confirmed. Ksh$sh.00 sent to 365 POOL 0700000000 on 29/8/26 at 10:00 AM."

private fun received(code: String, sh: Int) =
    "$code Confirmed. You have received Ksh$sh.00 from A MEMBER 0700000001 on 29/8/26."

/**
 * What a shell must do to clear an entry, depending on how it was recorded.
 *
 * A screen that offers a bare Confirm button on an entry recorded with a
 * message is offering something that cannot work.
 */
class ConfirmPathTest {

    private fun session() = Session.restored(InMemoryStore(), T0)

    @Test
    fun an_entry_recorded_with_a_message_cannot_be_cleared_by_a_bare_confirm() {
        var s = session().actAs(DevSeed.BRIAN)
            .contribute(DevSeed.KANGIRI, 50_000, T0, sent("BARE111AAA", 500))
        val id = s.book.pending().last().id

        // No paste. This is what a screen without a paste field would send.
        s = s.confirmAct(id, DevSeed.BONNIE, T0)

        assertIs<Notice.Refused>(s.notice)
        assertEquals(EntryState.PENDING, s.book.entry(id)!!.state)
    }

    @Test
    fun a_shell_can_ask_the_book_which_entries_need_a_message() {
        var s = session().actAs(DevSeed.BRIAN)
            .contribute(DevSeed.KANGIRI, 50_000, T0, sent("NEED111AAA", 500))
        val withEvidence = s.book.pending().last().id
        val withoutEvidence = s.book.pending().first { it.id != withEvidence }.id

        assertTrue(s.actNeedsEvidence(withEvidence), "the screen must show a paste field")
        assertTrue(!s.actNeedsEvidence(withoutEvidence), "and must not for a cash entry")
        assertEquals("NEED111AAA", s.actReference(withEvidence))
    }

    @Test
    fun the_matching_message_clears_it() {
        var s = session().actAs(DevSeed.BRIAN)
            .contribute(DevSeed.KANGIRI, 50_000, T0, sent("GOOD111AAA", 500))
        val id = s.book.pending().last().id
        s = s.confirmAct(id, DevSeed.BONNIE, T0, received("GOOD111AAA", 500))
        assertEquals(EntryState.CONFIRMED, s.book.entry(id)!!.state)
    }
}
