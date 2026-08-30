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

/**
 * Renaming a place, which is what Brian actually asked for.
 *
 * The note in `Places.kt` used to say renaming could be added when somebody
 * asked. Somebody did: the group's books call the two accounts *Founder's A/C*
 * and *Keshflo A/C*.
 *
 * Changing the seed was not enough, and that is the part worth a test. A ledger
 * stores its own accounts and pockets, so the definitions are frozen into the
 * file the day it is created — the new names reached a fresh book and left the
 * existing one saying "Members' pool".
 */
class RenamePlacesTest {

    private val config = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)

    private fun book() = LedgerBook(
        members = DevSeed.MEMBERS,
        accounts = DevSeed.ACCOUNTS,
        pockets = DevSeed.POCKETS,
    )

    private fun <T> Decision<T>.value(): T = (this as Decision.Allowed).value

    @Test
    fun a_pocket_takes_the_name_the_group_uses() {
        val b = book().renamePocket(DevSeed.POOL, "Founder's A/C", DevSeed.BONNIE, config).value()
        assertEquals("Founder's A/C", b.pockets.single { it.id == DevSeed.POOL }.label)
    }

    @Test
    fun the_id_never_moves_so_nothing_is_orphaned() {
        // Entries point at ids. If a rename touched those, every entry against
        // the old pocket would be pointing at nothing.
        var b = book()
        b = (
            b.record(
                id = "c1", type = EntryType.CONTRIBUTION, amountCents = 100_000,
                memberId = DevSeed.BONNIE, recordedBy = DevSeed.BONNIE, config = config,
                pocketId = DevSeed.POOL,
            ) as Decision.Allowed
            ).value.book
        b = (b.confirm("c1", DevSeed.BRIAN, config) as Decision.Allowed).value.book
        val before = b.state().pocketBalance(DevSeed.POOL)

        b = b.renamePocket(DevSeed.POOL, "Founder's A/C", DevSeed.BONNIE, config).value()

        assertEquals(before, b.state().pocketBalance(DevSeed.POOL), "the money lost its pocket")
        assertTrue(b.state().balances)
        assertEquals(DevSeed.POOL, b.pockets.single { it.label == "Founder's A/C" }.id)
    }

    @Test
    fun renaming_moves_no_money_and_adds_no_entry() {
        var b = book()
        b = (
            b.record(
                id = "c2", type = EntryType.CONTRIBUTION, amountCents = 500_000,
                memberId = DevSeed.BONNIE, recordedBy = DevSeed.BONNIE, config = config,
            ) as Decision.Allowed
            ).value.book
        b = (b.confirm("c2", DevSeed.BRIAN, config) as Decision.Allowed).value.book
        val cash = b.state().poolCashCents
        val entries = b.entries.size

        b = b.renameAccount(DevSeed.POCHI, "Pochi", DevSeed.BONNIE, config).value()

        assertEquals(cash, b.state().poolCashCents)
        assertEquals(entries, b.entries.size, "a rename is not an event in the ledger")
    }

    @Test
    fun two_places_still_cannot_share_a_name() {
        val r = book().renameAccount(DevSeed.POCHI, "Ziidi", DevSeed.BONNIE, config)
        assertTrue(r is Decision.Refused, "two accounts reading Ziidi is a person guessing")
    }

    @Test
    fun renaming_to_the_same_name_is_not_an_error() {
        val b = book().renameAccount(DevSeed.POCHI, "M-Pesa Pochi", DevSeed.BONNIE, config).value()
        assertEquals("M-Pesa Pochi", b.accounts.single { it.id == DevSeed.POCHI }.label)
    }

    @Test
    fun a_nameless_rename_is_refused() {
        assertTrue(
            book().renamePocket(DevSeed.POOL, "   ", DevSeed.BONNIE, config) is Decision.Refused,
        )
    }

    @Test
    fun a_keshflo_borrower_cannot_rename_anything() {
        assertTrue(
            book().renamePocket(DevSeed.POOL, "Mine", DevSeed.WANJIKU, config) is Decision.Refused,
        )
    }

    @Test
    fun a_number_typed_into_a_name_is_still_redacted() {
        val b = book().renameAccount(DevSeed.POCHI, "Pochi 0712345678", DevSeed.BONNIE, config)
            .value()
        val label = b.accounts.single { it.id == DevSeed.POCHI }.label
        assertTrue("0712345678" !in label, label)
        assertTrue("Pochi" in label, label)
    }

    @Test
    fun a_renamed_place_survives_a_save() {
        val b = book().renamePocket(DevSeed.POOL, "Founder's A/C", DevSeed.BONNIE, config).value()
        val back = online.vyybandasky.plus365.core.store.decodeBook(
            online.vyybandasky.plus365.core.store.encodeBook(b),
        ).getOrThrow()
        assertEquals("Founder's A/C", back.pockets.single { it.id == DevSeed.POOL }.label)
    }
}

/**
 * Naming money that arrived with no name.
 *
 * A ledger written before pockets existed has entries earmarked to ids with no
 * definition behind them. The screen that surfaces those tells the member to
 * rename them on the Places screen — and `renamePocket` refused, because there
 * was no pocket to rename.
 *
 * True of the definitions and false of the money, and it made the instruction a
 * dead end. Found by putting the build on a real phone and reading what the app
 * told somebody to do.
 */
class AdoptOrphanPocketTest {

    private val config = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)

    private fun <T> Decision<T>.value(): T = (this as Decision.Allowed).value

    /** Exactly the shape an older stored file decodes into. */
    private fun bookWithOrphanMoney(): LedgerBook {
        var b = LedgerBook(
            members = DevSeed.MEMBERS,
            accounts = DevSeed.ACCOUNTS,
            pockets = DevSeed.POCKETS,
        )
        b = (
            b.record(
                id = "c1", type = EntryType.CONTRIBUTION, amountCents = 365_800,
                memberId = DevSeed.BONNIE, recordedBy = DevSeed.BONNIE, config = config,
                pocketId = DevSeed.POOL,
            ) as Decision.Allowed
            ).value.book
        b = (b.confirm("c1", DevSeed.BRIAN, config) as Decision.Allowed).value.book
        return b.copy(pockets = emptyList())
    }

    @Test
    fun an_id_holding_money_can_be_given_a_name() {
        val b = bookWithOrphanMoney()
            .renamePocket(DevSeed.POOL, "Founder's A/C", DevSeed.BONNIE, config).value()
        assertEquals("Founder's A/C", b.pockets.single { it.id == DevSeed.POOL }.label)
    }

    @Test
    fun and_the_money_is_still_exactly_where_it_was() {
        val before = bookWithOrphanMoney().state().pocketBalance(DevSeed.POOL)
        val after = bookWithOrphanMoney()
            .renamePocket(DevSeed.POOL, "Founder's A/C", DevSeed.BONNIE, config).value()
        assertEquals(before, after.state().pocketBalance(DevSeed.POOL))
        assertTrue(after.state().balances)
    }

    @Test
    fun naming_an_id_that_holds_nothing_is_still_refused() {
        // That is adding a pocket, and there is a button for it.
        val r = bookWithOrphanMoney()
            .renamePocket("invented", "Something", DevSeed.BONNIE, config)
        assertTrue(r is Decision.Refused)
    }

    @Test
    fun adoption_cannot_duplicate_an_existing_name() {
        var b = bookWithOrphanMoney()
        b = b.renamePocket(DevSeed.POOL, "Founder's A/C", DevSeed.BONNIE, config).value()
        // KESHFLO holds nothing here, so this is refused for that reason first —
        // the point is that adoption goes through the same name checks.
        val r = b.renamePocket(DevSeed.KESHFLO, "Founder's A/C", DevSeed.BONNIE, config)
        assertTrue(r is Decision.Refused)
    }

    @Test
    fun a_keshflo_borrower_cannot_adopt_one_either() {
        val r = bookWithOrphanMoney()
            .renamePocket(DevSeed.POOL, "Mine", DevSeed.WANJIKU, config)
        assertTrue(r is Decision.Refused)
    }
}
