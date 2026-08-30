package online.vyybandasky.plus365.core.presentation

import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.ledger.LedgerState
import online.vyybandasky.plus365.core.domain.Entry
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.domain.MemberId
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.governance.eligibleConfirmers
import online.vyybandasky.plus365.core.interest.actualRateBps
import online.vyybandasky.plus365.core.money.formatKes

/**
 * Everything the screens display, computed once in shared code.
 *
 * The two shells render these rows and nothing else — no arithmetic, no
 * formatting, no rule-checking happens in the UI layer. That is the same reason
 * the fold is shared: if the phone and the laptop each worked out what to show,
 * they would eventually disagree, and a ledger that disagrees with itself is
 * worthless.
 */

data class SummaryView(
    /** The roll-up: every account added together. */
    val cashAtHand: String,
    val pendingCash: String,
    /** Every pending loan amount, added up. */
    val totalOutstanding: String,
    val pendingCount: Int,
    /** Where the money is. */
    val accounts: List<AccountRow>,
    /** What it is earmarked for. Sums to the same total. */
    val pockets: List<PocketRow>,
)

/** One earmark: what a slice of the total is set aside for. */
data class PocketRow(
    val id: String,
    val label: String,
    val blurb: String,
    val balance: String,
    val balanceCents: Long,
)

data class AccountRow(
    val id: String,
    val label: String,
    val balance: String,
    val balanceCents: Long,
    /** Ziidi and Etica grow on their own; a wallet does not. */
    val earns: Boolean = false,
)

/** A loan in the parts the pool keeps it in, plus the roll-ups. */
data class LoanRow(
    val loanId: String,
    val borrower: String,
    val principal: String,
    val interest: String,
    /** Both fees together. */
    val txnCost: String,
    val mpesaCharge: String,
    val bankCharge: String,
    val hasBankCharge: Boolean,
    val repaid: String,
    val totalDue: String,
    /** The pending loan amount. */
    val outstanding: String,
    val rateLabel: String,
    val borrowerIsBeneficiary: Boolean,
    val settled: Boolean,
)

data class MemberRef(val id: MemberId, val name: String)

/** A human label for an entry type. */
fun EntryType.label(): String = when (this) {
    EntryType.CONTRIBUTION -> "Contribution"
    EntryType.PAYOUT -> "Payout"
    EntryType.LOAN_OUT -> "Loan out"
    EntryType.LOAN_REPAYMENT -> "Repayment"
    EntryType.MEMBER_LOAN_IN -> "Member loan in"
    EntryType.POOL_REPAY_MEMBER -> "Pool repays member"
    EntryType.INTEREST_ACCRUAL -> "Interest"
    EntryType.TXN_COST -> "Transaction cost"
    EntryType.TRANSFER -> "Transfer"
    EntryType.ACCOUNT_INTEREST -> "Interest earned"
    EntryType.POCKET_TRANSFER -> "Re-earmarked"
    EntryType.REVERSAL -> "Reversal"
}

private fun LedgerBook.describe(e: Entry): String =
    "${e.type.label()} — ${displayName(e.memberId)}"

fun LedgerBook.summaryView(): SummaryView {
    val s = state()
    return SummaryView(
        cashAtHand = formatKes(s.cashAtHandCents),
        pendingCash = formatKes(s.pendingPoolCashCents),
        totalOutstanding = formatKes(s.totalOutstandingCents),
        pendingCount = pending().size,
        accounts = accountRows(s),
        pockets = pocketRows(s),
    )
}

/**
 * Every pocket holding money, including ones the book has no definition for.
 *
 * Found on Bonnie's phone, running a ledger created before pockets existed. It
 * had **no pocket definitions at all** and nineteen entries earmarked to ids
 * those definitions would have described. The fold was fine — the money was
 * allocated and the balance invariant held — but this list was built from the
 * *definitions*, so it came back empty and the whole "what it is for" section
 * silently disappeared.
 *
 * KSh 3,658 was allocated to pockets no screen could show. Nothing looked wrong,
 * because an absent section looks like a section that has nothing to say.
 *
 * A display that quietly omits money is worse than one that shows a name it does
 * not recognise, so an id with a balance and no definition gets a row of its own
 * and says plainly what it is. The two splits go back to agreeing, which is the
 * only reason to show them side by side.
 */
/**
 * Every account holding money, including ones the book has no definition for.
 *
 * The same hole as [pocketRows], found by going looking for it after the pocket
 * one was fixed rather than by waiting for a second phone to show it. This side
 * is the worse of the two: "where it is" is the card somebody checks against
 * what their own bank app says, and an account silently missing from it means
 * cash on hand no longer equals the rows underneath it. The figure that is
 * wrong is the one nobody would think to doubt.
 *
 * An id with a balance and no definition gets a row named from the id. It is
 * not marked as earning, because whether it earns is exactly the kind of thing
 * the missing definition would have said, and guessing at it would be inventing
 * a fact about somebody's money.
 */
private fun LedgerBook.accountRows(s: LedgerState): List<AccountRow> {
    val defined = accounts.map { a ->
        AccountRow(
            id = a.id,
            label = a.label,
            balance = formatKes(s.accountBalance(a.id)),
            balanceCents = s.accountBalance(a.id),
            earns = a.earnsInterest,
        )
    }
    val known = accounts.map { it.id }.toSet()
    val orphans = s.perAccount
        .filterKeys { it !in known }
        .filterValues { it != 0L }
        .map { (id, cents) ->
            AccountRow(
                id = id,
                label = id.replaceFirstChar { it.uppercase() },
                balance = formatKes(cents),
                balanceCents = cents,
                earns = false,
            )
        }
        .sortedBy { it.id }
    return defined + orphans
}

private fun LedgerBook.pocketRows(s: LedgerState): List<PocketRow> {
    val defined = pockets.map { p ->
        PocketRow(
            id = p.id,
            label = p.label,
            blurb = p.blurb,
            balance = formatKes(s.pocketBalance(p.id)),
            balanceCents = s.pocketBalance(p.id),
        )
    }
    val known = pockets.map { it.id }.toSet()
    val orphans = s.perPocket
        .filterKeys { it !in known }
        .filterValues { it != 0L }
        .map { (id, cents) ->
            PocketRow(
                id = id,
                label = id.replaceFirstChar { it.uppercase() },
                blurb = "This ledger has no description for it. The money is here; " +
                    "the name is not. Rename it on the Places screen.",
                balance = formatKes(cents),
                balanceCents = cents,
            )
        }
        .sortedBy { it.id }
    return defined + orphans
}

fun LedgerBook.loanRows(): List<LoanRow> {
    val s = state()
    return loans.map { loan ->
        val o = s.loans[loan.id]
        val interest = o?.interestAccruedCents ?: 0L
        LoanRow(
            loanId = loan.id,
            borrower = displayName(loan.counterpartyMemberId),
            principal = formatKes(o?.principalCents ?: 0L),
            interest = formatKes(interest),
            txnCost = formatKes(o?.txnCostCents ?: 0L),
            mpesaCharge = formatKes(o?.mpesaChargeCents ?: 0L),
            bankCharge = formatKes(o?.bankChargeCents ?: 0L),
            hasBankCharge = (o?.bankChargeCents ?: 0L) > 0L,
            repaid = formatKes(o?.repaidCents ?: 0L),
            totalDue = formatKes(o?.totalDueCents ?: 0L),
            outstanding = formatKes(o?.outstandingCents ?: 0L),
            rateLabel = "${actualRateBps(loan.principalCents, interest) / 100.0}% flat",
            borrowerIsBeneficiary = member(loan.counterpartyMemberId)?.isBeneficiary == true,
            settled = o?.settled ?: false,
        )
    }
}

/** The types a person can record from the shell. Reversal has its own path. */
val RECORDABLE_TYPES: List<EntryType> = listOf(
    EntryType.CONTRIBUTION,
    EntryType.PAYOUT,
    EntryType.LOAN_REPAYMENT,
    EntryType.MEMBER_LOAN_IN,
    EntryType.POOL_REPAY_MEMBER,
)
