package online.vyybandasky.plus365.core.presentation

import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.governance.ActorConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Becoming somebody else, and the line that stops it.
 *
 * A dev build lets one person stand in for all three so both ends of a
 * two-person rule can be exercised on one machine. A live build holds one
 * identity. Neither the switcher nor its absence is cosmetic: a switcher on a
 * production device is impersonation with a nice label on it.
 *
 * Separately — and this is the part worth keeping honest — switching who you are
 * must never let the same human confirm their own entry. Dev mode relaxes *who
 * this device may be*, not *whether a recorder may confirm*.
 */
class ActingAsTest {

    private fun book() = LedgerBook(
        members = DevSeed.MEMBERS,
        accounts = DevSeed.ACCOUNTS,
        pockets = DevSeed.POCKETS,
    )

    private val dev = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)
    private val live = ActorConfig.production(DevSeed.BONNIE)

    @Test
    fun `a dev build offers the switch`() {
        val p = book().profile(DevSeed.BONNIE, dev)
        assertTrue(p.canSwitch, "one person has to be able to test both ends")
        assertTrue(p.canActAs.size > 1)
    }

    @Test
    fun `a live build does not`() {
        val p = book().profile(DevSeed.BONNIE, live)
        assertTrue(!p.canSwitch, "a switcher here would be impersonation with a label on it")
        assertEquals(1, p.canActAs.size)
    }

    @Test
    fun `switching changes who you are`() {
        val s = Session(book = book(), config = dev, actingAs = DevSeed.BONNIE)
        val after = s.actAs(DevSeed.KANGIRI)
        assertEquals(DevSeed.KANGIRI, after.actingAs)
        assertNotEquals(s.actingAs, after.actingAs)
    }

    @Test
    fun `a live build refuses to become anybody else`() {
        val s = Session(book = book(), config = live, actingAs = DevSeed.BONNIE)
        val after = s.actAs(DevSeed.KANGIRI)
        assertEquals(DevSeed.BONNIE, after.actingAs, "the device is who it is")
        assertTrue(after.notice is Notice.Refused)
    }

    @Test
    fun `switching does not let the recorder confirm their own entry`() {
        // The whole point. Dev mode relaxes which member this device may *be*;
        // it does not relax the rule that two different people are involved.
        var s = Session(book = book(), config = dev, actingAs = DevSeed.BONNIE)
        s = s.contribute(DevSeed.BONNIE, 10_000)
        val id = s.book.pending().last().id

        val self = s.confirm(id, DevSeed.BONNIE)
        assertTrue(self.notice is Notice.Refused, "you cannot vouch for your own entry")

        val other = s.actAs(DevSeed.BRIAN).confirm(id, DevSeed.BRIAN)
        assertTrue(other.notice is Notice.Info, "somebody else can")
    }

    @Test
    fun `the storage sentence names the machine it is on`() {
        assertTrue(book().profile(DevSeed.BONNIE, dev, Shell.PHONE).storageLine.contains("phone"))
        assertTrue(book().profile(DevSeed.BONNIE, dev, Shell.DESKTOP).storageLine.contains("laptop"))
    }
}
