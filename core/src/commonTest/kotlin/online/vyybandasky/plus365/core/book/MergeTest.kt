package online.vyybandasky.plus365.core.book

import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.domain.Pocket
import online.vyybandasky.plus365.core.domain.TargetChangeState
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.governance.Decision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Three devices, one ledger.
 *
 * The laptop holds the authoritative book; phones push what they have and take
 * back what the laptop makes of it. Everything that could go wrong with that is
 * a question about one function, [mergeFrom], so this is where it gets asked.
 *
 * The tests worth reading are the ones about disagreement. Merging two books
 * that agree is arithmetic. Merging two that do not is a decision about somebody
 * else's money, and the rule is that the merge does not make it.
 */
class MergeTest {

    private val config = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)

    private fun <T> Decision<T>.value(): T = (this as Decision.Allowed).value

    private fun book() = LedgerBook(
        members = DevSeed.MEMBERS,
        accounts = DevSeed.ACCOUNTS,
        pockets = DevSeed.POCKETS,
    )

    private fun LedgerBook.recorded(id: String, amount: Long, by: String = DevSeed.BONNIE) =
        record(
            id = id, type = EntryType.CONTRIBUTION, amountCents = amount,
            memberId = DevSeed.KANGIRI, recordedBy = by, config = config,
            accountId = DevSeed.ACCOUNTS.first().id, pocketId = DevSeed.POOL,
        ).value().book

    // ── the ordinary case ───────────────────────────────────────────────────

    @Test
    fun an_entry_the_laptop_has_never_seen_is_taken() {
        val laptop = book()
        val phone = book().recorded("p1", 100_000)
        val (merged, report) = laptop.mergeFrom(phone)
        assertEquals(listOf("p1"), report.added)
        assertEquals(1, merged.entries.size)
    }

    @Test
    fun syncing_the_same_thing_twice_changes_nothing_the_second_time() {
        // Ids are generated where the entry is recorded, so a repeated push is
        // the same entry arriving again, not a second one.
        val phone = book().recorded("p1", 100_000)
        val (once, _) = book().mergeFrom(phone)
        val (twice, report) = once.mergeFrom(phone)
        assertEquals(once.entries.size, twice.entries.size)
        assertTrue(!report.changedAnything)
    }

    @Test
    fun two_phones_recording_different_things_both_land() {
        val a = book().recorded("a1", 100_000)
        val b = book().recorded("b1", 250_000)
        var laptop = book()
        laptop = laptop.mergeFrom(a).first
        laptop = laptop.mergeFrom(b).first
        assertEquals(setOf("a1", "b1"), laptop.entries.map { it.id }.toSet())
    }

    // ── cross-device confirmation, which is the point of the whole thing ────

    @Test
    fun a_confirmation_made_on_another_phone_reaches_the_laptop() {
        val laptop = book().recorded("p1", 100_000, by = DevSeed.BONNIE)
        // Brian's phone pulled it, confirmed it, and pushes back.
        val brian = laptop.confirm("p1", DevSeed.BRIAN, config).value().book
        val (merged, report) = laptop.mergeFrom(brian)
        assertEquals(listOf("p1"), report.advanced)
        assertEquals(EntryState.CONFIRMED, merged.entry("p1")!!.state)
        assertEquals(DevSeed.BRIAN, merged.entry("p1")!!.confirmedByMemberId)
    }

    @Test
    fun and_the_money_only_moves_once_it_has() {
        val laptop = book().recorded("p1", 100_000)
        assertEquals(0L, laptop.state().cashAtHandCents)
        val brian = laptop.confirm("p1", DevSeed.BRIAN, config).value().book
        val (merged, _) = laptop.mergeFrom(brian)
        assertEquals(100_000L, merged.state().cashAtHandCents)
        assertTrue(merged.state().balances)
    }

    @Test
    fun a_pending_copy_arriving_late_cannot_un_confirm_anything() {
        // The phone that recorded it still holds its own PENDING version and
        // pushes again after somebody else confirmed. Nothing may go backwards.
        val stale = book().recorded("p1", 100_000)
        val laptop = stale.confirm("p1", DevSeed.BRIAN, config).value().book
        val (merged, report) = laptop.mergeFrom(stale)
        assertEquals(EntryState.CONFIRMED, merged.entry("p1")!!.state)
        assertTrue(report.advanced.isEmpty())
        assertEquals(100_000L, merged.state().cashAtHandCents)
    }

    // ── disagreement is reported, never resolved ────────────────────────────

    @Test
    fun the_same_entry_settled_two_different_ways_is_a_conflict() {
        val base = book().recorded("p1", 100_000)
        val confirmed = base.confirm("p1", DevSeed.BRIAN, config).value().book
        val rejected = base.reject("p1", DevSeed.KANGIRI, config, "Never happened.").value().book
        val (merged, report) = confirmed.mergeFrom(rejected)
        assertEquals(listOf("p1"), report.conflicted)
        // The laptop's version stands. Choosing by clock would be choosing who
        // was right by whose phone was faster.
        assertEquals(EntryState.CONFIRMED, merged.entry("p1")!!.state)
    }

    // ── definitions ─────────────────────────────────────────────────────────

    @Test
    fun a_place_a_phone_knows_about_is_taken() {
        val phone = book().copy(pockets = DevSeed.POCKETS + Pocket("school", "School fees", ""))
        val (merged, report) = book().mergeFrom(phone)
        assertTrue(merged.pockets.any { it.id == "school" })
        assertTrue(report.definitionsAdded.any { it.contains("school") })
    }

    @Test
    fun but_a_phone_cannot_rename_one_out_from_under_the_laptop() {
        val phone = book().copy(
            pockets = listOf(Pocket(DevSeed.POOL, "Whatever Brian calls it", "")),
        )
        val (merged, _) = book().mergeFrom(phone)
        assertEquals("Founder's A/C", merged.pockets.single { it.id == DevSeed.POOL }.label)
    }

    // ── unanimity across devices ────────────────────────────────────────────

    @Test
    fun approvals_held_on_different_phones_are_pooled() {
        val base = book().proposeTargetChange(
            "tc1", DevSeed.KANGIRI, 900_000, DevSeed.BONNIE, config,
        ).value()
        // Brian agreed on his phone. Kang'iri agreed on his. Neither device has
        // both, and neither is wrong.
        val brian = base.approveTargetChange("tc1", DevSeed.BRIAN, config).value()
        val kangiri = base.approveTargetChange("tc1", DevSeed.KANGIRI, config).value()

        var laptop = base.mergeFrom(brian).first
        assertEquals(TargetChangeState.PROPOSED, laptop.targetChange("tc1")!!.state)

        val (after, report) = laptop.mergeFrom(kangiri)
        laptop = after
        assertEquals(TargetChangeState.AGREED, laptop.targetChange("tc1")!!.state)
        assertEquals(listOf("tc1"), report.settledByPooling)
        assertEquals(900_000L, laptop.member(DevSeed.KANGIRI)!!.contributionTargetCents)
    }

    @Test
    fun a_refusal_arriving_from_a_phone_ends_it() {
        val base = book().proposeTargetChange(
            "tc1", DevSeed.KANGIRI, 900_000, DevSeed.BONNIE, config,
        ).value()
        val refused = base.rejectTargetChange("tc1", DevSeed.KANGIRI, "Too much.", config).value()
        val (merged, _) = base.mergeFrom(refused)
        assertEquals(TargetChangeState.REJECTED, merged.targetChange("tc1")!!.state)
        assertEquals(DevSeed.TARGET, merged.member(DevSeed.KANGIRI)!!.contributionTargetCents)
    }

    // ── convergence, which is the property the whole topology rests on ──────

    @Test
    fun every_device_ends_up_with_the_same_ledger() {
        val phoneA = book().recorded("a1", 100_000)
        val phoneB = book().recorded("b1", 250_000)

        // Both push to the laptop.
        var laptop = book()
        laptop = laptop.mergeFrom(phoneA).first
        laptop = laptop.mergeFrom(phoneB).first

        // The laptop pushes the merged state back; each phone takes it whole.
        val a = laptop
        val b = laptop

        assertEquals(a.entries.map { it.id }.toSet(), b.entries.map { it.id }.toSet())
        assertEquals(setOf("a1", "b1"), a.entries.map { it.id }.toSet())
        assertEquals(a.state().cashAtHandCents, b.state().cashAtHandCents)
        assertEquals(laptop.state().cashAtHandCents, a.state().cashAtHandCents)
    }

    @Test
    fun the_order_the_phones_arrive_in_does_not_matter() {
        val a = book().recorded("a1", 100_000)
        val b = book().recorded("b1", 250_000)
        val ab = book().mergeFrom(a).first.mergeFrom(b).first
        val ba = book().mergeFrom(b).first.mergeFrom(a).first
        assertEquals(ab.entries.map { it.id }.toSet(), ba.entries.map { it.id }.toSet())
        assertEquals(ab.state().cashAtHandCents, ba.state().cashAtHandCents)
    }

    /** The invariant the whole app rests on has to survive a merge. */
    @Test
    fun the_books_still_balance_afterwards() {
        val a = book().recorded("a1", 100_000)
        val confirmedA = a.confirm("a1", DevSeed.BRIAN, config).value().book
        val b = book().recorded("b1", 250_000)
        val confirmedB = b.confirm("b1", DevSeed.KANGIRI, config).value().book
        var laptop = book()
        laptop = laptop.mergeFrom(confirmedA).first
        laptop = laptop.mergeFrom(confirmedB).first
        val s = laptop.state()
        assertTrue(s.balances)
        assertEquals(350_000L, s.cashAtHandCents)
        assertEquals(s.cashAtHandCents, s.perAccount.values.sum())
        assertEquals(s.cashAtHandCents, s.perPocket.values.sum())
    }

    /** A merge is not a place to invent money. */
    @Test
    fun merging_a_book_into_itself_changes_nothing() {
        val b = book().recorded("a1", 100_000)
        val (merged, report) = b.mergeFrom(b)
        assertEquals(b.entries.size, merged.entries.size)
        assertEquals(b.state().cashAtHandCents, merged.state().cashAtHandCents)
        assertTrue(!report.changedAnything)
    }
}
