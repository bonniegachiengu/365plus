package online.vyybandasky.plus365.core.interest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import online.vyybandasky.plus365.core.domain.MemberKind

class InterestTest {

    // ── the two tiers ────────────────────────────────────────────────────────

    @Test
    fun founders_borrow_at_five_percent_and_keshflo_lends_out_at_ten() {
        assertEquals(500, FOUNDER_RATE_BPS)
        assertEquals(1_000, BENEFICIARY_RATE_BPS)
        assertEquals(500, rateFor(MemberKind.FOUNDER))
        assertEquals(1_000, rateFor(MemberKind.KESHFLO_BENEFICIARY))
    }

    @Test
    fun the_same_principal_costs_twice_as_much_to_an_outsider() {
        // A member borrowing their own pool, against Keshflo lending it outward.
        assertEquals(10_000L, interestFor(200_000L, MemberKind.FOUNDER))
        assertEquals(20_000L, interestFor(200_000L, MemberKind.KESHFLO_BENEFICIARY))
    }

    @Test
    fun five_percent_of_two_thousand_is_one_hundred() {
        assertEquals(10_000L, interestFor(200_000L, MemberKind.FOUNDER))
    }

    @Test
    fun ten_percent_of_one_thousand_is_one_hundred() {
        assertEquals(10_000L, interestFor(100_000L, MemberKind.KESHFLO_BENEFICIARY))
    }

    // ── rounding, unchanged ──────────────────────────────────────────────────

    @Test
    fun a_half_shilling_rounds_up() {
        // 5% of 2,050 is 102.50 -> 103.
        assertEquals(10_300L, interestFor(205_000L, MemberKind.FOUNDER))
    }

    @Test
    fun below_a_half_shilling_rounds_down() {
        // 5% of 2,004 is 100.20 -> 100.
        assertEquals(10_000L, interestFor(200_400L, MemberKind.FOUNDER))
    }

    @Test
    fun interest_is_always_a_whole_number_of_shillings_at_either_rate() {
        var principal = 0L
        while (principal <= 1_000_000L) {
            for (kind in MemberKind.entries) {
                assertEquals(
                    0L,
                    interestFor(principal, kind) % 100L,
                    "interest on $principal for $kind was not a whole shilling",
                )
            }
            principal += 137L
        }
    }

    @Test
    fun zero_principal_earns_nothing_at_either_rate() {
        assertEquals(0L, interestFor(0L, MemberKind.FOUNDER))
        assertEquals(0L, interestFor(0L, MemberKind.KESHFLO_BENEFICIARY))
    }

    @Test
    fun a_negative_principal_is_rejected_rather_than_quietly_inverted() {
        assertFailsWith<IllegalArgumentException> { interestFor(-100L, MemberKind.FOUNDER) }
    }

    @Test
    fun an_older_rate_still_computes_at_that_rate() {
        // A loan keeps the rate it was written at; changing the tiers must never
        // reach backwards into one already on the books.
        assertEquals(14_000L, interestCents(200_000L, 700))
    }

    @Test
    fun the_derived_rate_reads_back_what_a_loan_was_written_at() {
        assertEquals(500, actualRateBps(200_000L, 10_000L))
        assertEquals(1_000, actualRateBps(200_000L, 20_000L))
        assertEquals(700, actualRateBps(200_000L, 14_000L))
        assertEquals(0, actualRateBps(0L, 0L))
    }
}
