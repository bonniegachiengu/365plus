package online.vyybandasky.plus365.core.book

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.domain.MemberKind
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.governance.Decision
import online.vyybandasky.plus365.core.governance.OverrideDecision
import online.vyybandasky.plus365.core.governance.Refusal
import online.vyybandasky.plus365.core.governance.eligibleConfirmers
import online.vyybandasky.plus365.core.interest.BENEFICIARY_RATE_BPS
import online.vyybandasky.plus365.core.interest.FOUNDER_RATE_BPS
import online.vyybandasky.plus365.core.sms.ParseOutcome
import online.vyybandasky.plus365.core.sms.SmsProvider
import online.vyybandasky.plus365.core.sms.isAtmWithdrawal
import online.vyybandasky.plus365.core.sms.parseSms

private val CONFIG = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)

private fun book() = LedgerBook(members = DevSeed.MEMBERS, accounts = DevSeed.ACCOUNTS)

private fun <T> Decision<T>.value(): T = assertIs<Decision.Allowed<T>>(this).value

/**
 * Keshflo lends outward. The people it lends to borrow and nothing else.
 */
class KeshfloTest {

    // ── the two tiers, applied by who is borrowing ───────────────────────────

    @Test
    fun a_founder_borrows_at_five_and_an_outsider_at_ten() {
        var b = book()
        b = b.disburseLoan(
            loanId = "L-f", borrower = DevSeed.KANGIRI, principalCents = 200_000,
            recordedBy = DevSeed.BRIAN, config = CONFIG,
        ).value().book
        b = b.disburseLoan(
            loanId = "L-k", borrower = DevSeed.WANJIKU, principalCents = 200_000,
            recordedBy = DevSeed.BRIAN, config = CONFIG,
        ).value().book

        assertEquals(FOUNDER_RATE_BPS, b.loan("L-f")!!.rateBps)
        assertEquals(BENEFICIARY_RATE_BPS, b.loan("L-k")!!.rateBps)
        assertEquals(10_000L, b.entry("L-f-interest")!!.amountCents, "5% of 2,000")
        assertEquals(20_000L, b.entry("L-k-interest")!!.amountCents, "10% of 2,000")
    }

    @Test
    fun the_rate_follows_the_borrower_not_whoever_recorded_it() {
        // Brian is a founder and records both. The rate must come from who is
        // taking the money, or Keshflo lending would quietly get the cheap rate.
        val b = book().disburseLoan(
            loanId = "L-k", borrower = DevSeed.WANJIKU, principalCents = 100_000,
            recordedBy = DevSeed.BRIAN, config = CONFIG,
        ).value().book
        assertEquals(BENEFICIARY_RATE_BPS, b.loan("L-k")!!.rateBps)
        assertEquals(MemberKind.KESHFLO_BENEFICIARY, b.loan("L-k")!!.borrowerKind)
    }

    @Test
    fun a_loan_keeps_the_rate_it_was_written_at() {
        val b = book().disburseLoan(
            loanId = "L-old", borrower = DevSeed.KANGIRI, principalCents = 200_000,
            recordedBy = DevSeed.BRIAN, config = CONFIG, rateBps = 700,
        ).value().book
        assertEquals(700, b.loan("L-old")!!.rateBps, "an older rate is not re-looked-up")
        assertEquals(14_000L, b.entry("L-old-interest")!!.amountCents)
    }

    // ── a beneficiary governs nothing ────────────────────────────────────────

    @Test
    fun a_beneficiary_cannot_record_an_entry() {
        val refused = assertIs<Decision.Refused>(
            book().record(
                id = "e1", type = EntryType.CONTRIBUTION, amountCents = 10_000,
                memberId = DevSeed.WANJIKU, recordedBy = DevSeed.WANJIKU,
                config = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE + DevSeed.WANJIKU),
            ),
        )
        assertIs<Refusal.NotAMember>(refused.refusal)
    }

    @Test
    fun a_beneficiary_cannot_confirm_anybody_elses_entry() {
        // The one that matters. Confirming is a say in the members' money, and
        // an outside borrower has no stake to back one.
        val wide = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE + DevSeed.WANJIKU)
        val b = book().record(
            id = "e1", type = EntryType.CONTRIBUTION, amountCents = 10_000,
            memberId = DevSeed.BONNIE, recordedBy = DevSeed.BONNIE, config = wide,
        ).value().book

        val refused = assertIs<Decision.Refused>(b.confirm("e1", DevSeed.WANJIKU, wide))
        assertIs<Refusal.NotAMember>(refused.refusal)
        assertEquals(0L, b.state().poolCashCents)
    }

    @Test
    fun a_beneficiary_cannot_reject_or_settle_either() {
        val wide = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE + DevSeed.WANJIKU)
        var b = book().record(
            id = "e1", type = EntryType.CONTRIBUTION, amountCents = 10_000,
            memberId = DevSeed.BONNIE, recordedBy = DevSeed.BONNIE, config = wide,
        ).value().book
        assertIs<Decision.Refused>(b.reject("e1", DevSeed.WANJIKU, wide))

        b = b.escalate(
            "e1", DevSeed.BRIAN,
            online.vyybandasky.plus365.core.governance.ConflictKind.DISPUTED, wide,
        ).value().book
        assertIs<Decision.Refused>(
            b.override("e1", DevSeed.WANJIKU, OverrideDecision.CONFIRMED, "let me", wide),
        )
    }

    @Test
    fun a_beneficiary_is_never_offered_as_a_confirmer() {
        val b = book().record(
            id = "e1", type = EntryType.CONTRIBUTION, amountCents = 10_000,
            memberId = DevSeed.BONNIE, recordedBy = DevSeed.BONNIE, config = CONFIG,
        ).value().book
        val eligible = eligibleConfirmers(b.entry("e1")!!, b.founderIds(), CONFIG)
        assertTrue(DevSeed.WANJIKU !in eligible)
        assertEquals(2, eligible.size)
    }

    @Test
    fun a_dev_device_cannot_stand_in_for_someone_the_pool_lends_to() {
        assertTrue(DevSeed.WANJIKU !in DevSeed.DEV_CONFIG.mayActAs)
    }

    // ── the transaction cost, split ──────────────────────────────────────────

    @Test
    fun a_loan_can_carry_an_mpesa_charge_and_a_bank_charge_separately() {
        val r = book().disburseLoan(
            loanId = "L-1", borrower = DevSeed.KANGIRI, principalCents = 200_000,
            recordedBy = DevSeed.BRIAN, config = CONFIG,
            mpesaChargeCents = 3_300, bankChargeCents = 5_000,
        ).value()

        assertEquals(4, r.entries.size, "principal, interest, and each fee")
        assertEquals(3_300L, r.book.entry("L-1-mpesacharge")!!.amountCents)
        assertEquals(5_000L, r.book.entry("L-1-bankcharge")!!.amountCents)
        assertEquals(3_300L, r.book.loan("L-1")!!.mpesaChargeCents)
        assertEquals(5_000L, r.book.loan("L-1")!!.bankChargeCents)
    }

    @Test
    fun the_two_fees_are_tallied_apart_but_add_up_to_the_transaction_cost() {
        var b = book().disburseLoan(
            loanId = "L-1", borrower = DevSeed.KANGIRI, principalCents = 200_000,
            recordedBy = DevSeed.BRIAN, config = CONFIG,
            mpesaChargeCents = 3_300, bankChargeCents = 5_000,
        ).value().book
        b = b.confirmGroup("L-1", DevSeed.BONNIE, CONFIG).value().book

        val position = b.state().loans.getValue("L-1")
        assertEquals(3_300L, position.mpesaChargeCents)
        assertEquals(5_000L, position.bankChargeCents)
        assertEquals(8_300L, position.txnCostCents, "both fees together")
        assertEquals(200_000L + 10_000L + 8_300L, position.totalDueCents)
    }
}

/** Bank and ATM messages, read the same way as M-Pesa ones. */
class BankSmsTest {

    private fun ev(text: String) =
        assertIs<ParseOutcome.Parsed>(parseSms(text, DevSeed.BONNIE)).evidence

    @Test
    fun an_atm_withdrawal_reads_as_money_leaving() {
        val sms = "Dear Customer, KES 5,000.00 has been debited from your account " +
            "1234567890 via ATM on 26/08/2026. Ref: ATM88231X. Available balance KES 12,300.00"
        val e = ev(sms)
        assertEquals(SmsProvider.BANK, e.provider)
        assertEquals("ATM88231X", e.reference)
        assertEquals(500_000L, e.amountCents)
        assertEquals(online.vyybandasky.plus365.core.sms.SmsDirection.SENT, e.direction)
        assertTrue(e.isAtmWithdrawal())
    }

    @Test
    fun a_bank_credit_reads_as_money_arriving() {
        val sms = "KES 2,500.00 has been credited to your account 9876543210 on " +
            "26/08/2026. Ref: TRF44120Z. Available balance KES 14,800.00"
        val e = ev(sms)
        assertEquals("TRF44120Z", e.reference)
        assertEquals(250_000L, e.amountCents)
        assertEquals(online.vyybandasky.plus365.core.sms.SmsDirection.RECEIVED, e.direction)
        assertTrue(!e.isAtmWithdrawal())
    }

    @Test
    fun a_bank_account_number_is_masked_before_the_message_is_stored() {
        // A bank message carries an account number the way an M-Pesa one carries
        // a phone number, and the ledger file travels between three phones.
        val sms = "Dear Customer, KES 5,000.00 has been debited from your account " +
            "1234567890 via ATM on 26/08/2026. Ref: ATM88231X. Available balance KES 12,300.00"
        val e = ev(sms)
        assertTrue(!e.raw.contains("1234567890"), "an account number reached the store")
        assertTrue(e.raw.contains("ATM88231X"), "the reference is the proof and must stay")
    }

    @Test
    fun a_bank_one_time_code_is_refused_like_any_other() {
        val sms = "Your bank one-time password is 774120. Do not share it with anyone."
        assertIs<ParseOutcome.Rejected>(parseSms(sms, DevSeed.BONNIE))
    }

    @Test
    fun a_bank_message_and_an_mpesa_message_can_still_be_matched_by_their_code() {
        // The whole point of the shared reference survives the provider changing.
        val bank = ev(
            "KES 2,000.00 has been debited from your account 1234567890. Ref: SHARED9911.",
        )
        val wallet = assertIs<ParseOutcome.Parsed>(
            parseSms(
                "SHARED9911 Confirmed. You have received Ksh2,000.00 from BONNIE 0700000001 on 26/8/26.",
                DevSeed.BRIAN,
            ),
        ).evidence
        assertEquals(
            online.vyybandasky.plus365.core.sms.MatchResult.Matched,
            online.vyybandasky.plus365.core.sms.matchEvidence(bank, wallet),
        )
    }
}
