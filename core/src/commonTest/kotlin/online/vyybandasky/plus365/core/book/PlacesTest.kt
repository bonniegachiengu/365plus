package online.vyybandasky.plus365.core.book

import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.domain.AccountKind
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.governance.Decision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Adding a place money can be.
 *
 * The claim being defended is the one that justifies letting a single person do
 * this at all: creating an account moves no money, and the dangerous thing
 * somebody might want it for is stopped where it should be — at the transfer,
 * not at the naming.
 */
class PlacesTest {

    private val config = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)

    private fun book() = LedgerBook(
        members = DevSeed.MEMBERS,
        accounts = DevSeed.ACCOUNTS,
        pockets = DevSeed.POCKETS,
    )

    private fun <T> Decision<T>.value(): T = (this as Decision.Allowed).value

    @Test
    fun `a bank account can finally be added`() {
        val b = book().addAccount("kcb", "KCB", AccountKind.BANK, DevSeed.BONNIE, config).value()
        val added = b.accounts.single { it.id == "kcb" }
        assertEquals("KCB", added.label)
        assertEquals(AccountKind.BANK, added.kind)
        assertTrue(!added.earnsInterest, "a current account is not a savings product")
        assertTrue(added.sendsMessages, "which is what makes the ATM slip parseable")
    }

    @Test
    fun `adding an account moves no money`() {
        val before = book().state()
        val after = book()
            .addAccount("kcb", "KCB", AccountKind.BANK, DevSeed.BONNIE, config).value()
            .state()
        assertEquals(before.poolCashCents, after.poolCashCents)
        assertEquals(0L, after.accountBalance("kcb"), "a new account starts empty")
        assertTrue(after.balances, "the invariant survives a new place to put things")
    }

    @Test
    fun `and the dangerous version is stopped at the transfer, not the naming`() {
        // The reason this needs no second member. Naming an account is free;
        // putting the pool's money into it is an entry like any other.
        val b = book().addAccount("mine", "Somewhere else", AccountKind.CASH, DevSeed.BONNIE, config)
            .value()
        val moved = b.transfer("t-1", DevSeed.POCHI, "mine", 100_000, DevSeed.BONNIE, config)
        val entry = (moved as Decision.Allowed).value.book.entries.last()
        assertEquals(EntryType.TRANSFER, entry.type)
        assertTrue(
            entry.confirmedByMemberId == null,
            "the money has not gone anywhere until somebody else agrees",
        )
    }

    @Test
    fun `a Keshflo borrower cannot add one`() {
        val r = book().addAccount("x", "X", AccountKind.CASH, DevSeed.WANJIKU, config)
        assertTrue(r is Decision.Refused)
    }

    @Test
    fun `two accounts cannot share a name`() {
        val b = book().addAccount("kcb", "KCB", AccountKind.BANK, DevSeed.BONNIE, config).value()
        val again = b.addAccount("kcb2", "kcb", AccountKind.BANK, DevSeed.BONNIE, config)
        assertTrue(
            again is Decision.Refused,
            "two identical labels on a picker is a person choosing at random",
        )
    }

    @Test
    fun `the same id twice is the same account, not an error`() {
        val b = book().addAccount("kcb", "KCB", AccountKind.BANK, DevSeed.BONNIE, config).value()
        val again = b.addAccount("kcb", "KCB", AccountKind.BANK, DevSeed.BONNIE, config).value()
        assertEquals(b.accounts.size, again.accounts.size, "a retried save must not double it")
    }

    @Test
    fun `a nameless account is refused`() {
        assertTrue(
            book().addAccount("x", "   ", AccountKind.CASH, DevSeed.BONNIE, config)
                is Decision.Refused,
        )
    }

    @Test
    fun `a pocket can be added and starts empty`() {
        val b = book().addPocket("school", "School fees", "For the January run", DevSeed.BONNIE, config)
            .value()
        assertEquals("School fees", b.pockets.single { it.id == "school" }.label)
        assertEquals(0L, b.state().pocketBalance("school"))
        assertTrue(b.state().balances, "pockets still sum to cash at hand")
    }

    @Test
    fun `two pockets cannot share a name either`() {
        val b = book().addPocket("a", "School fees", "", DevSeed.BONNIE, config).value()
        assertTrue(b.addPocket("b", "school fees", "", DevSeed.BONNIE, config) is Decision.Refused)
    }

    @Test
    fun `a new account and pocket survive a save`() {
        val b = book()
            .addAccount("kcb", "KCB", AccountKind.BANK, DevSeed.BONNIE, config).value()
            .addPocket("school", "School fees", "For January", DevSeed.BONNIE, config).value()
        val roundTripped = online.vyybandasky.plus365.core.store.decodeBook(
            online.vyybandasky.plus365.core.store.encodeBook(b),
        ).getOrThrow()
        assertTrue(roundTripped.accounts.any { it.id == "kcb" }, "a new account was dropped on save")
        assertTrue(roundTripped.pockets.any { it.id == "school" }, "a new pocket was dropped on save")
    }
}

/**
 * The id a typed name turns into.
 *
 * Ids end up in the stored file and in every entry pointing at the account, so
 * they must be predictable and must never collide.
 */
class SlugTest {

    private val config = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)

    private fun session() = online.vyybandasky.plus365.core.presentation.Session(
        book = LedgerBook(
            members = DevSeed.MEMBERS,
            accounts = DevSeed.ACCOUNTS,
            pockets = DevSeed.POCKETS,
        ),
        config = config,
        actingAs = DevSeed.BONNIE,
    )

    @Test
    fun `a typed name becomes a usable id`() {
        val s = session().addAccount("KCB Current", AccountKind.BANK)
        assertEquals("kcb-current", s.book.accounts.last().id)
        assertEquals("KCB Current", s.book.accounts.last().label)
    }

    @Test
    fun `punctuation and spacing do not leak into the id`() {
        val s = session().addAccount("  Equity (main)!  ", AccountKind.BANK)
        val id = s.book.accounts.last().id
        assertTrue(id.all { it.isLetterOrDigit() || it == '-' }, "id was $id")
        assertTrue(!id.startsWith("-") && !id.endsWith("-"), "id was $id")
    }

    @Test
    fun `a name that would collide gets a distinct id, not a silent merge`() {
        // Two different accounts whose names slug the same must not become one.
        var s = session().addPocket("School fees")
        s = s.addPocket("School Fees!")
        val ids = s.book.pockets.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "two pockets share an id: $ids")
    }
}
