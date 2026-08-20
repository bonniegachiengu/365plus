package online.vyybandasky.plus365.core.money

import kotlin.test.Test
import kotlin.test.assertEquals
import online.vyybandasky.plus365.core.SampleLedger
import online.vyybandasky.plus365.core.ledger.fold

class MoneyTest {

    @Test
    fun formats_from_integer_minor_units() {
        assertEquals("KSh 0.00", formatKes(0))
        assertEquals("KSh 0.05", formatKes(5))
        assertEquals("KSh 1.05", formatKes(105))
        assertEquals("KSh 999.99", formatKes(99_999))
        assertEquals("KSh 5,000.00", formatKes(500_000))
        assertEquals("KSh 1,234,567.89", formatKes(123_456_789))
    }

    @Test
    fun negatives_keep_the_sign_in_front_of_the_amount() {
        assertEquals("KSh -1,200.00", formatKes(-120_000))
        assertEquals("KSh -0.01", formatKes(-1))
    }

    @Test
    fun grouping_lands_on_the_right_boundaries() {
        assertEquals("KSh 100.00", formatKes(10_000))
        assertEquals("KSh 1,000.00", formatKes(100_000))
        assertEquals("KSh 10,000.00", formatKes(1_000_000))
    }

    @Test
    fun the_extreme_negative_does_not_overflow_into_a_positive() {
        // Negating Long.MIN_VALUE wraps back to itself; without the guard this
        // renders a large POSITIVE amount, which is the worst possible bug in a
        // money formatter.
        assertEquals('-', formatKes(Long.MIN_VALUE)[4])
    }
}

class SampleLedgerTest {

    @Test
    fun the_shared_sample_folds_to_the_number_both_shells_display() {
        val state = fold(SampleLedger.ENTRIES)
        assertEquals(SampleLedger.EXPECTED_POOL_CASH_CENTS, state.poolCashCents)
        assertEquals("KSh 6,300.00", formatKes(state.poolCashCents))
    }
}
