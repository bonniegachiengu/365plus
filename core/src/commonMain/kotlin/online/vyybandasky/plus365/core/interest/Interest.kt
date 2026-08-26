package online.vyybandasky.plus365.core.interest

/**
 * Loan interest: 7% flat on principal, charged once at disbursement.
 *
 * Flat, not annualised and not a monthly accrual — one charge, at the moment the
 * money goes out. That matches how the pool has always been run.
 *
 * Keep this concept away from the money-market return on the pool's own float.
 * Both are "interest" in English and they are not the same number: this one is
 * charged to a borrower, the other is earned by the pool.
 */

/** The house rate for NEW loans, in basis points. */
const val HOUSE_RATE_BPS: Int = 700

/** Interest rounds to the nearest whole shilling, halves up. */
const val CENTS_PER_SHILLING: Long = 100

/**
 * Interest on [principalCents] at [rateBps], rounded to the nearest whole
 * shilling.
 *
 * Rounding to the shilling rather than the cent is deliberate: the pool's books
 * have always been kept in whole shillings, and a ledger that quietly carried
 * half-shillings would disagree with the person keeping them by a few cents per
 * loan — which is exactly the drift this app exists to prevent.
 *
 * Halves go up, so 122.50 becomes 123.
 */
fun interestCents(principalCents: Long, rateBps: Int): Long {
    require(principalCents >= 0) { "principal is a magnitude; got $principalCents" }
    require(rateBps >= 0) { "rate is a magnitude; got $rateBps" }

    // principalCents * rateBps is scaled by 10_000 (basis points). Dividing by
    // 10_000 gives cents; dividing by 10_000 * 100 gives shillings. Do it in one
    // step so the intermediate cents value never rounds twice.
    val scaled = principalCents * rateBps.toLong()
    val perShilling = 10_000L * CENTS_PER_SHILLING
    val shillings = (scaled + perShilling / 2) / perShilling
    return shillings * CENTS_PER_SHILLING
}

/** Interest on a new loan at the house rate. */
fun houseInterestCents(principalCents: Long): Long =
    interestCents(principalCents, HOUSE_RATE_BPS)

/**
 * The rate a loan was actually written at, in basis points, derived from its
 * stored principal and interest.
 *
 * For display only. A loan's interest is stored as an amount, never recomputed
 * from a rate — older loans were written at other rates and must keep them.
 */
fun actualRateBps(principalCents: Long, interestCents: Long): Int {
    if (principalCents <= 0L) return 0
    return ((interestCents * 10_000L + principalCents / 2) / principalCents).toInt()
}
