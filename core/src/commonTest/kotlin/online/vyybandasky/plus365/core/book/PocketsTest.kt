package online.vyybandasky.plus365.core.book

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.governance.Decision
import online.vyybandasky.plus365.core.governance.Refusal
import online.vyybandasky.plus365.core.sms.ParseOutcome
import online.vyybandasky.plus365.core.sms.SmsProvider
import online.vyybandasky.plus365.core.sms.message
import online.vyybandasky.plus365.core.sms.parseSms

private val CONFIG = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)

private fun book() = LedgerBook(
    members = DevSeed.MEMBERS,
    accounts = DevSeed.ACCOUNTS,
    pockets = DevSeed.POCKETS,
)

private fun <T> Decision<T>.value(): T = assertIs<Decision.Allowed<T>>(this).value

/**
 * Accounts answer *where*. Pockets answer *what for*. Neither is the other.
 */
class PocketsTest {

    private fun funded(): LedgerBook {
        var b = book().record(
            id = "c1", type = EntryType.CONTRIBUTION, amountCents = 1_000_000,
            memberId = DevSeed.BONNIE, recordedBy = DevSeed.BONNIE, config = CONFIG,
            accountId = DevSeed.POCHI, pocketId = DevSeed.POOL,
        ).value().book
        b = b.confirm("c1", DevSeed.BRIAN, CONFIG).value().book
        return b
    }

    // ── the invariant ────────────────────────────────────────────────────────

    @Test
    fun the_three_views_of_the_pool_always_agree() {
        val s = funded().state()
        assertEquals(1_000_000L, s.poolCashCents)
        assertEquals(1_000_000L, s.cashAtHandCents, "where the money is")
        assertEquals(1_000_000L, s.allocatedCents, "what it is for")
        assertTrue(s.balances)
    }

    // ── moving between accounts changes where, not what for ─────────────────

    @Test
    fun moving_money_to_ziidi_leaves_the_earmarking_untouched() {
        var b = funded()
        b = b.transfer("t1", DevSeed.POCHI, DevSeed.ZIIDI, 400_000, DevSeed.BONNIE, CONFIG)
            .value().book
        b = b.confirm("t1", DevSeed.BRIAN, CONFIG).value().book

        val s = b.state()
        assertEquals(600_000L, s.accountBalance(DevSeed.POCHI))
        assertEquals(400_000L, s.accountBalance(DevSeed.ZIIDI))
        // The pocket split did not budge: the money is somewhere else, but it is
        // for the same thing.
        assertEquals(1_000_000L, s.pocketBalance(DevSeed.POOL))
        assertEquals(0L, s.pocketBalance(DevSeed.KESHFLO))
        assertTrue(s.balances)
    }

    // ── re-earmarking changes what for, not where ───────────────────────────

    @Test
    fun setting_money_aside_for_keshflo_moves_no_money_at_all() {
        var b = funded()
        val accountsBefore = b.state().perAccount

        b = b.reallocate("k1", DevSeed.POOL, DevSeed.KESHFLO, 250_000, DevSeed.BONNIE, CONFIG)
            .value().book
        b = b.confirm("k1", DevSeed.BRIAN, CONFIG).value().book

        val s = b.state()
        assertEquals(750_000L, s.pocketBalance(DevSeed.POOL))
        assertEquals(250_000L, s.pocketBalance(DevSeed.KESHFLO))
        assertEquals(accountsBefore, s.perAccount, "not a shilling moved anywhere")
        assertEquals(1_000_000L, s.cashAtHandCents)
        assertTrue(s.balances)
    }

    @Test
    fun re_earmarking_still_needs_a_second_member() {
        // Deciding a slice of the members' savings is now the Keshflo fund is a
        // decision about the members' money, whatever account it sits in.
        val b = funded().reallocate(
            "k1", DevSeed.POOL, DevSeed.KESHFLO, 250_000, DevSeed.BONNIE, CONFIG,
        ).value().book

        assertEquals(0L, b.state().pocketBalance(DevSeed.KESHFLO), "not yet agreed")
        assertIs<Refusal.SelfConfirmation>(
            assertIs<Decision.Refused>(b.confirm("k1", DevSeed.BONNIE, CONFIG)).refusal,
        )
    }

    @Test
    fun re_earmarking_needs_two_different_pockets() {
        val refused = assertIs<Decision.Refused>(
            funded().reallocate("k1", DevSeed.POOL, DevSeed.POOL, 100L, DevSeed.BONNIE, CONFIG),
        )
        assertIs<Refusal.Invalid>(refused.refusal)
    }

    // ── interest an account paid ────────────────────────────────────────────

    @Test
    fun ziidi_paying_out_lifts_the_pool_without_lifting_anyones_contribution() {
        var b = funded()
        val stakeBefore = b.state().balanceOf(DevSeed.BRIAN).stakeCents

        b = b.recordAccountInterest(
            "i1", DevSeed.ZIIDI, 4_200, DevSeed.BRIAN, CONFIG, pocketId = DevSeed.POOL,
        ).value().book
        b = b.confirm("i1", DevSeed.BONNIE, CONFIG).value().book

        val s = b.state()
        assertEquals(1_004_200L, s.poolCashCents)
        assertEquals(4_200L, s.accountBalance(DevSeed.ZIIDI))
        assertEquals(
            stakeBefore,
            s.balanceOf(DevSeed.BRIAN).stakeCents,
            "nobody contributed it, so nobody's contribution rises",
        )
        assertTrue(s.balances)
    }

    @Test
    fun an_account_that_does_not_earn_cannot_pay_interest() {
        // The wallet does not grow on its own. Recording interest against it
        // would be inventing money.
        val refused = assertIs<Decision.Refused>(
            funded().recordAccountInterest("i1", DevSeed.POCHI, 4_200, DevSeed.BRIAN, CONFIG),
        )
        assertIs<Refusal.Invalid>(refused.refusal)
    }

    @Test
    fun account_interest_waits_for_a_second_member_like_everything_else() {
        val b = funded().recordAccountInterest(
            "i1", DevSeed.ZIIDI, 4_200, DevSeed.BRIAN, CONFIG,
        ).value().book
        assertEquals(1_000_000L, b.state().poolCashCents, "not counted until agreed")
    }

    @Test
    fun a_beneficiary_can_neither_re_earmark_nor_book_interest() {
        val wide = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE + DevSeed.WANJIKU)
        val b = funded()
        assertIs<Refusal.NotAMember>(
            assertIs<Decision.Refused>(
                b.reallocate("k1", DevSeed.POOL, DevSeed.KESHFLO, 100L, DevSeed.WANJIKU, wide),
            ).refusal,
        )
        assertIs<Refusal.NotAMember>(
            assertIs<Decision.Refused>(
                b.recordAccountInterest("i1", DevSeed.ZIIDI, 100L, DevSeed.WANJIKU, wide),
            ).refusal,
        )
    }

    // ── which accounts are which ────────────────────────────────────────────

    @Test
    fun only_the_savings_products_earn() {
        assertEquals(
            setOf(DevSeed.ZIIDI, DevSeed.MSHWARI),
            book().earningAccounts().map { it.id }.toSet(),
        )
    }

    @Test
    fun ziidi_is_zero_rated_and_the_wallet_is_not() {
        assertTrue(book().account(DevSeed.ZIIDI)!!.zeroRated)
        assertTrue(!book().account(DevSeed.POCHI)!!.zeroRated)
    }
}

/**
 * Ziidi and M-Shwari: recognised, deliberately not read.
 *
 * There is no real Ziidi message to work from yet. The last format that was
 * guessed at went in untested and had to be flagged as unverified; this one is
 * left honestly unmapped until a redacted sample arrives.
 */
class UnmappedProviderTest {

    private val ziidi =
        "Ziidi: Your investment of KES 4,000.00 was successful. Fund balance KES 24,042.00."
    private val mshwari =
        "M-Shwari: You have deposited Ksh1,000.00 to M-Shwari account. Balance Ksh8,000.00."

    @Test
    fun a_ziidi_message_is_recognised_but_not_parsed() {
        val outcome = assertIs<ParseOutcome.Unmapped>(parseSms(ziidi, DevSeed.BONNIE))
        assertEquals(SmsProvider.ZIIDI, outcome.provider)
    }

    @Test
    fun an_mshwari_message_is_recognised_but_not_parsed() {
        val outcome = assertIs<ParseOutcome.Unmapped>(parseSms(mshwari, DevSeed.BONNIE))
        assertEquals(SmsProvider.MSHWARI, outcome.provider)
    }

    @Test
    fun no_fields_are_invented_from_an_unmapped_message() {
        // The point of the outcome. A half-read money message is worse than an
        // admittedly unread one, so nothing is offered that could be mistaken
        // for a reference or an amount.
        val outcome = assertIs<ParseOutcome.Unmapped>(parseSms(ziidi, DevSeed.BONNIE))
        assertTrue(outcome !is ParseOutcome.Parsed)
    }

    @Test
    fun the_text_is_kept_but_redacted() {
        val withNumber =
            "Ziidi: Your investment of KES 4,000.00 from 0712345678 was successful."
        val outcome = assertIs<ParseOutcome.Unmapped>(parseSms(withNumber, DevSeed.BONNIE))
        assertTrue(!outcome.raw.contains("0712345678"), "a number reached the store")
        assertTrue(outcome.raw.contains("Ziidi"))
    }

    @Test
    fun a_ziidi_message_is_not_mistaken_for_an_mpesa_one() {
        // Ziidi moves money through M-Pesa and says so. Checking M-Pesa first
        // would read it as an M-Pesa message and pull out fields meaning
        // something else entirely.
        val viaMpesa =
            "Ziidi: KES 4,000.00 moved from your M-PESA to your Ziidi fund. Balance KES 24,042.00."
        val outcome = assertIs<ParseOutcome.Unmapped>(parseSms(viaMpesa, DevSeed.BONNIE))
        assertEquals(SmsProvider.ZIIDI, outcome.provider)
    }

    @Test
    fun an_unmapped_message_still_says_something_useful_to_the_member() {
        val outcome = assertIs<ParseOutcome.Unmapped>(parseSms(ziidi, DevSeed.BONNIE))
        val text = outcome.message()
        assertTrue(text.contains("Ziidi"))
        assertTrue(text.contains("cannot read"))
        assertTrue(text.contains("by hand"), "it must tell them what to do instead")
    }

    @Test
    fun a_ziidi_one_time_code_is_still_refused_before_anything_else() {
        // The OTP filter runs first, ahead of provider detection. A secret must
        // never survive being recognised as a known sender.
        val otp = "Ziidi: your one-time password is 448120. Do not share it."
        assertIs<ParseOutcome.Rejected>(parseSms(otp, DevSeed.BONNIE))
    }
}
