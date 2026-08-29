package online.vyybandasky.plus365.core.ledger

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.book.confirm
import online.vyybandasky.plus365.core.book.record
import online.vyybandasky.plus365.core.book.transfer
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.governance.Decision
import online.vyybandasky.plus365.core.presentation.overdrawReport

private val CONFIG = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)
private val T0 = Instant.parse("2026-08-29T09:00:00Z")

private fun book() = LedgerBook(
    members = DevSeed.MEMBERS,
    accounts = DevSeed.ACCOUNTS,
    pockets = DevSeed.POCKETS,
)

private fun <T> Decision<T>.value(): T = assertIs<Decision.Allowed<T>>(this).value

/**
 * Overdrawing an account is flagged, never refused.
 *
 * The entry stands, the postings still balance, and the slip is written down
 * with enough to trace it.
 */
class OverdrawTest {

    /** 1,000 in Pochi, then 1,500 paid out of it. */
    private fun overdrawn(): LedgerBook {
        var b = book().record(
            id = "in1", type = EntryType.CONTRIBUTION, amountCents = 100_000,
            memberId = DevSeed.BONNIE, recordedBy = DevSeed.BONNIE, config = CONFIG,
            accountId = DevSeed.POCHI, pocketId = DevSeed.POOL, at = T0,
        ).value().book
        b = b.confirm("in1", DevSeed.BRIAN, CONFIG, at = T0).value().book

        b = b.record(
            id = "out1", type = EntryType.PAYOUT, amountCents = 150_000,
            memberId = DevSeed.KANGIRI, recordedBy = DevSeed.BRIAN, config = CONFIG,
            accountId = DevSeed.POCHI, pocketId = DevSeed.POOL, at = T0,
        ).value().book
        return b.confirm("out1", DevSeed.BONNIE, CONFIG, at = T0).value().book
    }

    // ── recorded, not refused ────────────────────────────────────────────────

    @Test
    fun an_entry_that_overdraws_an_account_is_still_recorded() {
        val b = overdrawn()
        assertEquals(EntryState.CONFIRMED, b.entry("out1")!!.state)
        assertEquals(-50_000L, b.state().accountBalance(DevSeed.POCHI))
    }

    @Test
    fun the_books_still_balance_across_an_overdraw() {
        // The flag is observational. It must not change a single figure.
        val s = overdrawn().state()
        assertEquals(-50_000L, s.poolCashCents)
        assertEquals(s.poolCashCents, s.cashAtHandCents, "where the money is")
        assertEquals(s.poolCashCents, s.allocatedCents, "what it is for")
        assertTrue(s.balances)
    }

    // ── flagged, with enough to trace it ────────────────────────────────────

    @Test
    fun the_slip_is_flagged_with_everything_needed_to_find_the_cause() {
        val f = overdrawn().state().overdrawFlags.single()

        assertEquals(DevSeed.POCHI, f.accountId, "which account")
        assertEquals("out1", f.entryId, "which entry took it under")
        assertEquals(EntryType.PAYOUT, f.type)
        assertEquals(150_000L, f.amountCents, "what that entry moved")
        assertEquals(-50_000L, f.balanceAfterCents, "how far under")
        assertEquals(50_000L, f.shortfallCents)
        assertEquals(DevSeed.KANGIRI, f.memberId, "who was on the other side")
        assertEquals(DevSeed.BRIAN, f.recordedByMemberId, "who wrote it down")
        assertEquals(DevSeed.POOL, f.pocketId, "what it was earmarked for")
        assertEquals(T0, f.at, "when")
    }

    @Test
    fun a_healthy_book_carries_no_flags() {
        var b = book().record(
            id = "in1", type = EntryType.CONTRIBUTION, amountCents = 100_000,
            memberId = DevSeed.BONNIE, recordedBy = DevSeed.BONNIE, config = CONFIG,
            accountId = DevSeed.POCHI, at = T0,
        ).value().book
        b = b.confirm("in1", DevSeed.BRIAN, CONFIG, at = T0).value().book

        assertTrue(b.state().overdrawFlags.isEmpty())
        assertTrue(!b.state().hasOverdrawn)
    }

    @Test
    fun a_transfer_out_of_an_empty_account_is_flagged_too() {
        // A transfer can overdraw the account it came from just as easily as a
        // payment can, and it is the same kind of slip.
        var b = book().transfer(
            "t1", DevSeed.POCHI, DevSeed.ZIIDI, 90_000, DevSeed.BONNIE, CONFIG, at = T0,
        ).value().book
        b = b.confirm("t1", DevSeed.BRIAN, CONFIG, at = T0).value().book

        val f = b.state().overdrawFlags.single()
        assertEquals(DevSeed.POCHI, f.accountId)
        assertEquals(EntryType.TRANSFER, f.type)
        assertEquals(-90_000L, f.balanceAfterCents)
    }

    // ── one slip reads as one slip ──────────────────────────────────────────

    @Test
    fun digging_further_under_is_its_own_slip() {
        var b = overdrawn()
        b = b.record(
            id = "out2", type = EntryType.PAYOUT, amountCents = 20_000,
            memberId = DevSeed.BRIAN, recordedBy = DevSeed.KANGIRI, config = CONFIG,
            accountId = DevSeed.POCHI, at = T0,
        ).value().book
        b = b.confirm("out2", DevSeed.BONNIE, CONFIG, at = T0).value().book

        val flags = b.state().overdrawFlags
        assertEquals(2, flags.size, "two separate occasions of going further under")
        assertEquals(-70_000L, flags.last().balanceAfterCents)
        assertEquals(DevSeed.KANGIRI, flags.last().recordedByMemberId)
    }

    @Test
    fun paying_money_back_in_while_under_is_not_a_slip() {
        // An entry that leaves the account negative but LESS negative is digging
        // it out. Flagging that would bury the real slips in their own aftermath.
        var b = overdrawn()
        b = b.record(
            id = "in2", type = EntryType.CONTRIBUTION, amountCents = 20_000,
            memberId = DevSeed.BONNIE, recordedBy = DevSeed.BONNIE, config = CONFIG,
            accountId = DevSeed.POCHI, at = T0,
        ).value().book
        b = b.confirm("in2", DevSeed.BRIAN, CONFIG, at = T0).value().book

        assertEquals(-30_000L, b.state().accountBalance(DevSeed.POCHI), "still under")
        assertEquals(1, b.state().overdrawFlags.size, "but not a new slip")
    }

    @Test
    fun an_unconfirmed_entry_does_not_raise_a_flag() {
        // Money nobody has agreed to has not overdrawn anything yet.
        val b = book().record(
            id = "out1", type = EntryType.PAYOUT, amountCents = 150_000,
            memberId = DevSeed.KANGIRI, recordedBy = DevSeed.BRIAN, config = CONFIG,
            accountId = DevSeed.POCHI, at = T0,
        ).value().book
        assertTrue(b.state().overdrawFlags.isEmpty())
    }

    // ── the pattern, which is the point ─────────────────────────────────────

    @Test
    fun the_report_says_which_account_and_how_often() {
        val r = overdrawn().overdrawReport(T0)
        assertEquals(1, r.total)
        assertEquals(1, r.accountsAffected)
        assertTrue(r.any)

        val account = r.byAccount.single()
        assertEquals("M-Pesa Pochi", account.account)
        assertEquals(1, account.times)
        assertEquals("KSh 500.00", account.worstShortfall)
        assertTrue(account.currentlyUnder)
    }

    @Test
    fun the_report_counts_both_sides_of_each_slip() {
        // Whose transaction it was and who wrote it down are often different
        // people, and a habit can sit with either.
        val r = overdrawn().overdrawReport(T0)
        val kangiri = r.byMember.single { it.memberId == DevSeed.KANGIRI }
        val brian = r.byMember.single { it.memberId == DevSeed.BRIAN }

        assertEquals(1, kangiri.involvedIn, "the payout was to Kang'iri")
        assertEquals(0, kangiri.recorded)
        assertEquals(1, brian.recorded, "Brian wrote it down")
        assertEquals(0, brian.involvedIn)
    }

    @Test
    fun repeated_slips_on_one_account_read_as_a_pattern_not_as_one_off_noise() {
        var b = overdrawn()
        b = b.record(
            id = "out2", type = EntryType.PAYOUT, amountCents = 20_000,
            memberId = DevSeed.KANGIRI, recordedBy = DevSeed.BRIAN, config = CONFIG,
            accountId = DevSeed.POCHI, at = T0,
        ).value().book
        b = b.confirm("out2", DevSeed.BONNIE, CONFIG, at = T0).value().book

        val r = b.overdrawReport(T0)
        assertEquals(2, r.total)
        assertTrue(r.headline.contains("M-Pesa Pochi"), r.headline)
        assertTrue(r.headline.contains("2 times"), r.headline)
        assertEquals(2, r.byMember.single { it.memberId == DevSeed.BRIAN }.recorded)
    }

    @Test
    fun every_row_names_the_entry_so_it_can_be_opened() {
        val row = overdrawn().overdrawReport(T0).rows.single()
        assertEquals("out1", row.entryId)
        assertTrue(row.headline.contains("M-Pesa Pochi"))
        assertTrue(row.headline.contains("KSh 500.00"))
        assertTrue(row.detail.contains("Kang'iri"))
        assertEquals("Brian", row.recordedBy)
    }

    @Test
    fun a_clean_book_says_so_plainly() {
        val r = book().overdrawReport(T0)
        assertTrue(!r.any)
        assertEquals("No account has gone below zero.", r.headline)
    }

    // ── the seeded book, which is genuinely overdrawn ───────────────────────

    @Test
    fun the_seeded_pochi_overdraw_is_flagged_rather_than_hidden() {
        // The dev seed lends more out of Pochi than remains after parking money
        // in Ziidi. That was showing as a bare negative with no explanation.
        val r = DevSeed.book().overdrawReport(T0)
        assertTrue(r.any, "the seed genuinely overdraws Pochi")
        assertTrue(r.byAccount.any { it.accountId == DevSeed.POCHI })
        assertTrue(DevSeed.book().state().balances, "and the books still balance")
    }
}
