package online.vyybandasky.plus365.core.presentation

import online.vyybandasky.plus365.core.book.LedgerBook
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
    val accounts: List<AccountRow>,
)

data class AccountRow(
    val id: String,
    val label: String,
    val balance: String,
    val balanceCents: Long,
)

data class MemberRow(
    val id: MemberId,
    val name: String,
    val stake: String,
    /** Human-readable, already resolved from the pool's signed view. */
    val owes: String,
    val owesCents: Long,
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

data class PendingRow(
    val entryId: String,
    val what: String,
    val amount: String,
    val recordedBy: String,
    val recordedById: MemberId,
    /** Who this device may tap to confirm — never includes the recorder. */
    val eligibleConfirmers: List<MemberRef>,
)

data class HistoryRow(
    val entryId: String,
    val what: String,
    val amount: String,
    val recordedBy: String,
    val confirmedBy: String,
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
        accounts = accounts.map { a ->
            AccountRow(
                id = a.id,
                label = a.label,
                balance = formatKes(s.accountBalance(a.id)),
                balanceCents = s.accountBalance(a.id),
            )
        },
    )
}

fun LedgerBook.memberRows(): List<MemberRow> {
    val s = state()
    return members.map { m ->
        val b = s.balanceOf(m.id)
        MemberRow(
            id = m.id,
            name = m.displayName,
            stake = formatKes(b.stakeCents),
            owes = when {
                b.debtCents < 0L -> "owes ${formatKes(-b.debtCents)}"
                b.debtCents > 0L -> "is owed ${formatKes(b.debtCents)}"
                else -> "clear"
            },
            owesCents = b.debtCents,
        )
    }
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

fun LedgerBook.pendingRows(config: ActorConfig): List<PendingRow> =
    pending().map { e ->
        PendingRow(
            entryId = e.id,
            what = describe(e),
            amount = formatKes(e.amountCents),
            recordedBy = displayName(e.recordedByMemberId ?: "?"),
            recordedById = e.recordedByMemberId ?: "?",
            eligibleConfirmers = eligibleConfirmers(e, memberIds(), config)
                .map { MemberRef(it, displayName(it)) },
        )
    }

fun LedgerBook.historyRows(): List<HistoryRow> =
    confirmed().map { e ->
        HistoryRow(
            entryId = e.id,
            what = describe(e),
            amount = formatKes(e.amountCents),
            recordedBy = displayName(e.recordedByMemberId ?: "?"),
            confirmedBy = displayName(e.confirmedByMemberId ?: "?"),
        )
    }

/** The types a person can record from the shell. Reversal has its own path. */
val RECORDABLE_TYPES: List<EntryType> = listOf(
    EntryType.CONTRIBUTION,
    EntryType.PAYOUT,
    EntryType.LOAN_REPAYMENT,
    EntryType.MEMBER_LOAN_IN,
    EntryType.POOL_REPAY_MEMBER,
)
