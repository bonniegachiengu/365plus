package online.vyybandasky.plus365.core.interest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InterestTest {

    @Test
    fun the_house_rate_is_seven_percent() {
        assertEquals(700, HOUSE_RATE_BPS)
    }

    @Test
    fun seven_percent_of_two_thousand_is_one_forty() {
        assertEquals(14_000L, houseInterestCents(200_000L))
    }

    @Test
    fun seven_percent_of_thirteen_hundred_rounds_to_ninety_one() {
        // 1,300 * 7% = 91 exactly.
        assertEquals(9_100L, houseInterestCents(130_000L))
    }

    @Test
    fun a_half_shilling_rounds_up() {
        // 1,750 * 7% = 122.50 -> 123.
        assertEquals(12_300L, houseInterestCents(175_000L))
    }

    @Test
    fun below_a_half_shilling_rounds_down() {
        // 1,000 * 7% = 70.00; 1,003 * 7% = 70.21 -> 70.
        assertEquals(7_000L, houseInterestCents(100_000L))
        assertEquals(7_000L, houseInterestCents(100_300L))
    }

    @Test
    fun interest_is_always_a_whole_number_of_shillings() {
        var principal = 0L
        while (principal <= 1_000_000L) {
            assertEquals(
                0L,
                houseInterestCents(principal) % 100L,
                "interest on $principal was not a whole shilling",
            )
            principal += 137L // a deliberately awkward step
        }
    }

    @Test
    fun zero_principal_earns_nothing() {
        assertEquals(0L, houseInterestCents(0L))
    }

    @Test
    fun a_negative_principal_is_rejected_rather_than_quietly_inverted() {
        assertFailsWith<IllegalArgumentException> { houseInterestCents(-100L) }
    }

    @Test
    fun an_older_rate_still_computes_at_that_rate() {
        // The pool has written loans below the house rate before; the rate is an
        // argument, and the stored amount is what a loan actually keeps.
        assertEquals(10_000L, interestCents(200_000L, 500))
    }

    @Test
    fun the_derived_rate_reads_back_what_a_loan_was_written_at() {
        assertEquals(700, actualRateBps(200_000L, 14_000L))
        assertEquals(500, actualRateBps(200_000L, 10_000L))
        assertEquals(0, actualRateBps(0L, 0L))
    }
}
