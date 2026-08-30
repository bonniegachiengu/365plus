package online.vyybandasky.plus365.core.presentation

import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.book.confirm
import online.vyybandasky.plus365.core.book.record
import online.vyybandasky.plus365.core.book.renameAccount
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.governance.Decision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Money at an account the book cannot name.
 *
 * The pocket version of this was found on a phone. This one was found by going
 * looking for it afterwards, because the accounts list was built the same way —
 * from the definitions — and a stored file can hold money at an id it has no
 * definition for.
 *
 * It is the worse of the two. "Where it is" is the card somebody checks against
 * their own bank app, and an account missing from it means cash on hand no
 * longer equals the rows beneath it.
 */
class OrphanAccountTest {

    private val config = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)

    private fun <T> Decision<T>.value(): T = (this as Decision.Allowed).value

    /** A book whose account definitions were lost, as an older file's would be. */
    private fun bookWithOrphanAccount(): LedgerBook {
        var b = LedgerBook(
            members = DevSeed.MEMBERS,
            accounts = DevSeed.ACCOUNTS,
            pockets = DevSeed.POCKETS,
        )
        b = (
            b.record(
                id = "c1", type = EntryType.CONTRIBUTION, amountCents = 250_000,
                memberId = DevSeed.BONNIE, recordedBy = DevSeed.BONNIE, config = config,
                pocketId = DevSeed.POOL, accountId = DevSeed.ACCOUNTS.first().id,
            ) as Decision.Allowed
            ).value.book
        b = (b.confirm("c1", DevSeed.BRIAN, config) as Decision.Allowed).value.book
        return b.copy(accounts = emptyList())
    }

    @Test
    fun the_money_is_still_on_the_screen() {
        val v = bookWithOrphanAccount().summaryView()
        assertEquals(1, v.accounts.size)
        assertEquals(250_000L, v.accounts.single().balanceCents)
    }

    @Test
    fun and_cash_on_hand_equals_the_rows_under_it() {
        val v = bookWithOrphanAccount().summaryView()
        assertEquals(
            bookWithOrphanAccount().state().cashAtHandCents,
            v.accounts.sumOf { it.balanceCents },
        )
    }

    @Test
    fun an_orphan_is_never_claimed_to_earn() {
        // Whether it grows is what the missing definition would have said.
        assertTrue(bookWithOrphanAccount().summaryView().accounts.none { it.earns })
    }

    @Test
    fun a_healthy_book_grows_no_extra_row() {
        var b = LedgerBook(
            members = DevSeed.MEMBERS,
            accounts = DevSeed.ACCOUNTS,
            pockets = DevSeed.POCKETS,
        )
        b = (
            b.record(
                id = "c1", type = EntryType.CONTRIBUTION, amountCents = 250_000,
                memberId = DevSeed.BONNIE, recordedBy = DevSeed.BONNIE, config = config,
                pocketId = DevSeed.POOL, accountId = DevSeed.ACCOUNTS.first().id,
            ) as Decision.Allowed
            ).value.book
        b = (b.confirm("c1", DevSeed.BRIAN, config) as Decision.Allowed).value.book
        assertEquals(DevSeed.ACCOUNTS.size, b.summaryView().accounts.size)
    }

    @Test
    fun an_orphan_account_can_be_given_a_name() {
        val id = DevSeed.ACCOUNTS.first().id
        val b = bookWithOrphanAccount()
            .renameAccount(id, "Co-op current", DevSeed.BONNIE, config).value()
        assertEquals("Co-op current", b.accounts.single { it.id == id }.label)
        assertEquals(250_000L, b.state().accountBalance(id))
        assertTrue(b.state().balances)
    }

    @Test
    fun naming_an_account_that_holds_nothing_is_still_refused() {
        val r = bookWithOrphanAccount()
            .renameAccount("invented", "Something", DevSeed.BONNIE, config)
        assertTrue(r is Decision.Refused)
    }
}
