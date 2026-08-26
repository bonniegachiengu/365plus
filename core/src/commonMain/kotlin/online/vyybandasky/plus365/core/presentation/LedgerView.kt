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
    val poolCash: String,
    val pendingCash: String,
    val totalOwed: String,
    val pendingCount: Int,
)

data class MemberRow(
    val id: MemberId,
    val name: String,
    val stake: String,
    /** Human-readable, already resolved from the pool's signed view. */
    val owes: String,
    val owesCents: Long,
)

data class LoanRow(
    val loanId: String,
    val borrower: String,
    val principalOutstanding: String,
    val interest: String,
    val rateLabel: String,
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
    EntryType.REVERSAL -> "Reversal"
}

private fun LedgerBook.describe(e: Entry): String =
    "${e.type.label()} — ${displayName(e.memberId)}"

fun LedgerBook.summaryView(): SummaryView {
    val s = state()
    val owed = s.perMember.values.sumOf { if (it.debtCents < 0) -it.debtCents else 0L }
    return SummaryView(
        poolCash = formatKes(s.poolCashCents),
        pendingCash = formatKes(s.pendingPoolCashCents),
        totalOwed = formatKes(owed),
        pendingCount = pending().size,
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
        val principal = o?.principalOutstandingCents ?: 0L
        val interest = o?.interestAccruedCents ?: 0L
        LoanRow(
            loanId = loan.id,
            borrower = displayName(loan.counterpartyMemberId),
            principalOutstanding = formatKes(principal),
            interest = formatKes(interest),
            rateLabel = "${actualRateBps(loan.principalCents, interest) / 100.0}% flat",
            settled = principal <= 0L,
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
