package online.vyybandasky.plus365.core.presentation

import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.domain.PocketId
import online.vyybandasky.plus365.core.money.formatKes

/**
 * The three figures the group reads after anything happens.
 *
 * Brian's shape, and it is the one the old books already print: the Founders
 * account, the Keshflo account, and the total cash at hand, which is the two
 * added together. Whatever kind of update it was — a contribution, a repayment,
 * a transfer between the pool's own accounts — the same three numbers come back
 * afterwards, so anybody can check the arithmetic without opening anything.
 *
 * Derived, never stored, and never assembled from the message that caused it. A
 * receipt built out of the text describing an update is a receipt that can
 * disagree with the ledger; this one is folded from the ledger itself, so if it
 * is wrong then the ledger is wrong, and that is the thing worth knowing.
 *
 * Named [Receipt] because `Standing` was taken, by the enum describing whether
 * one entry is confirmed. Two meanings of the same word in one package is how
 * somebody ends up reading the wrong one.
 */
data class Receipt(
    val founders: String,
    val keshflo: String,
    val cashAtHand: String,
    val foundersCents: Long,
    val keshfloCents: Long,
    val cashAtHandCents: Long,

    /**
     * Money sitting in neither of the two named accounts.
     *
     * Normally zero, and normally this whole field is ignorable. It exists
     * because "cash at hand is the sum of both" is a claim about the group
     * having exactly two accounts, and that is true today rather than true by
     * construction. If a third pocket ever holds anything, the two figures stop
     * adding up to the third, and a receipt that silently printed all three
     * anyway would be teaching people to trust a sum that no longer holds.
     */
    val elsewhereCents: Long,
) {
    /** Whether the two named accounts still account for every shilling. */
    val addsUp: Boolean get() = elsewhereCents == 0L

    val elsewhere: String get() = formatKes(elsewhereCents)
}

/**
 * The standing, folded from the confirmed ledger.
 *
 * Confirmed only, because that is what the balances are. An entry somebody has
 * recorded but nobody has agreed to has not moved any money, and a receipt that
 * counted it would be reporting one member's claim as the group's position.
 */
fun LedgerBook.receipt(
    foundersPocket: PocketId = "pool",
    keshfloPocket: PocketId = "keshflo",
): Receipt {
    val s = state()
    val founders = s.pocketBalance(foundersPocket)
    val keshflo = s.pocketBalance(keshfloPocket)
    val cash = s.cashAtHandCents
    return Receipt(
        founders = formatKes(founders),
        keshflo = formatKes(keshflo),
        cashAtHand = formatKes(cash),
        foundersCents = founders,
        keshfloCents = keshflo,
        cashAtHandCents = cash,
        elsewhereCents = cash - founders - keshflo,
    )
}
