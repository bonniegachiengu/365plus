package online.vyybandasky.plus365.core.money

/**
 * Integer minor units in, human string out.
 *
 * Shared for the same reason the fold is: if the phone and the laptop rendered
 * the same balance differently, three people would be looking at "different"
 * numbers that are actually the same number, which is worse than being wrong
 * in one place. No Double or Float goes anywhere near an amount (§1.3).
 */
fun formatKes(cents: Long): String {
    val negative = cents < 0L
    // Careful with Long.MIN_VALUE: negating it overflows back to itself.
    val abs = if (negative) {
        if (cents == Long.MIN_VALUE) Long.MAX_VALUE else -cents
    } else {
        cents
    }
    val whole = abs / 100
    val part = abs % 100
    val grouped = whole.toString().reversed().chunked(3).joinToString(",").reversed()
    val sign = if (negative) "-" else ""
    return "KSh $sign$grouped.${part.toString().padStart(2, '0')}"
}
