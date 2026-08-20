package online.vyybandasky.plus365

import kotlin.test.Test
import kotlin.test.assertEquals
import online.vyybandasky.plus365.core.SampleLedger
import online.vyybandasky.plus365.core.ledger.fold
import online.vyybandasky.plus365.core.money.formatKes

/**
 * A JVM unit test, no device needed. Its job is narrow but real: prove that
 * :core is genuinely on the Android module classpath and produces the same
 * numbers the desktop shows. If the two ever diverged, the app is worthless
 * (365PLUS_BRIEF.md §4).
 */
class LedgerScreenTest {

    @Test
    fun the_phone_folds_the_shared_sample_to_the_same_number_as_the_desktop() {
        val state = fold(SampleLedger.ENTRIES)
        assertEquals(SampleLedger.EXPECTED_POOL_CASH_CENTS, state.poolCashCents)
        assertEquals("KSh 6,300.00", formatKes(state.poolCashCents))
    }

    @Test
    fun member_stakes_come_through_the_shared_fold() {
        val state = fold(SampleLedger.ENTRIES)
        assertEquals(380_000, state.balanceOf(SampleLedger.ALICE).stakeCents)
        assertEquals(250_000, state.balanceOf(SampleLedger.BOB).stakeCents)
    }
}
