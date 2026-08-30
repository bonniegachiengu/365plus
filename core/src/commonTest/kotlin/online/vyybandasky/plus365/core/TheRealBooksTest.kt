package online.vyybandasky.plus365.core

import online.vyybandasky.plus365.core.domain.MemberKind
import online.vyybandasky.plus365.core.presentation.quoteLoan
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The group's actual books, pinned.
 *
 * Everything here comes from the real ledger the three of them have been
 * keeping since November, not from anything this app invented. Brian asked for
 * the same wording as the old system, and wording is the kind of thing that
 * drifts one careless rename at a time with nothing failing.
 *
 * So the labels and the arithmetic are written down here as assertions. If a
 * future change moves either, this is what says so.
 */
class TheRealBooksTest {

    /**
     * Brian's worked example, exactly as it appears in the books:
     * principal 1,000 + interest 50 + transaction 7 = 1,057.
     *
     * The 50 is the check that matters — it is the 5% founder rate arrived at
     * independently by the group, and it agreeing with ours is the only reason
     * to believe the rate in this app is the rate they actually charge.
     */
    @Test
    fun the_worked_example_from_the_books() {
        val q = quoteLoan(
            principalCents = 100_000L,
            kind = MemberKind.FOUNDER,
            txnCostCents = 700L,
        )
        assertEquals(100_000L, q.principalCents)
        assertEquals(5_000L, q.interestCents)
        assertEquals(700L, q.txnCostCents)
        assertEquals(105_700L, q.principalCents + q.interestCents + q.txnCostCents)
    }

    /** And the total the screen prints is that same sum, not a second one. */
    @Test
    fun the_total_on_the_screen_is_that_sum() {
        val q = quoteLoan(100_000L, MemberKind.FOUNDER, txnCostCents = 700L)
        assertEquals("KSh 1,057.00", q.totalRepayable)
    }

    /**
     * Two accounts, and these are their names.
     *
     * "Founder's A/C" and "Keshflo A/C" are what the old system calls them. A
     * new ledger has to open with those words on it; a stored one that predates
     * this carries its own labels forever, which is why renaming exists.
     */
    @Test
    fun the_two_accounts_are_named_the_way_the_group_names_them() {
        assertEquals(2, DevSeed.POCKETS.size)
        assertEquals("Founder's A/C", DevSeed.POCKETS.single { it.id == DevSeed.POOL }.label)
        assertEquals("Keshflo A/C", DevSeed.POCKETS.single { it.id == DevSeed.KESHFLO }.label)
    }

    /** Cash at hand is their sum and nothing else. */
    @Test
    fun there_is_no_third_place_for_money_to_be() {
        assertEquals(setOf(DevSeed.POOL, DevSeed.KESHFLO), DevSeed.POCKETS.map { it.id }.toSet())
    }
}
