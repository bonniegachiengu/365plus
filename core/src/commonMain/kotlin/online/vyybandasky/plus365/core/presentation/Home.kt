package online.vyybandasky.plus365.core.presentation

import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.domain.Entry
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.domain.MemberId
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.governance.eligibleConfirmers
import online.vyybandasky.plus365.core.interest.HOUSE_RATE_BPS
import online.vyybandasky.plus365.core.interest.interestCents
import online.vyybandasky.plus365.core.money.formatKes

/**
 * The home screen and its flows, in the words a member reads.
 *
 * Every sentence on screen is built here, in shared code, so the phone and the
 * laptop cannot describe the same entry differently. Names and shillings only —
 * no entry ids, no type names, no "outstanding principal".
 */

/** Whether money is settled or still waiting on a second person. */
enum class Standing { CONFIRMED, PENDING, REJECTED }

// ── the hero ─────────────────────────────────────────────────────────────────

data class CashOnHand(
    val total: String,
    val memberCountLine: String,
    val lastUpdated: String,
    val pendingLine: String?,
    val accounts: List<AccountRow>,
)

fun LedgerBook.cashOnHand(now: Instant? = null): CashOnHand {
    val s = state()
    val pendingCount = pendingActs().size
    return CashOnHand(
        total = formatKes(s.cashAtHandCents),
        memberCountLine = "across ${members.size} members",
        lastUpdated = lastActivityAt()?.let { "updated ${relativeTime(it, now)}" } ?: "no activity yet",
        pendingLine = when (pendingCount) {
            0 -> null
            1 -> "1 entry waiting to be confirmed"
            else -> "$pendingCount entries waiting to be confirmed"
        },
        accounts = summaryView().accounts,
    )
}

/** The newest thing that happened, by the clock rather than by sequence. */
fun LedgerBook.lastActivityAt(): Instant? =
    entries.mapNotNull { it.confirmedAt ?: it.recordedAt }.maxOrNull()

// ── the actions ──────────────────────────────────────────────────────────────

/** The four things a member opens the app to do. */
enum class PoolAction(val label: String, val blurb: String) {
    CONTRIBUTE("Contribute", "Add money to the pool"),
    LEND("Lend", "Lend pool money to a member"),
    BORROW("Borrow", "Borrow from the pool"),
    REPAY("Repay", "Pay back a loan"),
}

// ── needs confirming ─────────────────────────────────────────────────────────

/**
 * One decision waiting on a second person.
 *
 * A loan is three entries but one act, so this is the unit the confirm screen
 * asks about — ask once, not three times.
 */
data class PendingAct(
    /** The group id, or the lone entry's id. What [Session] acts on. */
    val actId: String,
    val isGroup: Boolean,
    /** "Brian recorded: lend KSh 2,000 to Kang'iri" */
    val sentence: String,
    val amount: String,
    /** "They repay KSh 2,173 in total" — only where it adds something. */
    val detail: String?,
    val recordedBy: String,
    val recordedById: MemberId,
    val whenRecorded: String,
    /** Members who may clear it. Never contains [recordedById]. */
    val eligibleConfirmers: List<MemberRef>,
    val entryCount: Int,
)

fun LedgerBook.pendingActs(config: ActorConfig, now: Instant? = null): List<PendingAct> =
    pendingActs().map { act ->
        val lead = act.firstOrNull { it.type != EntryType.INTEREST_ACCRUAL && it.type != EntryType.TXN_COST }
            ?: act.first()
        val recorder = lead.recordedByMemberId ?: "?"
        PendingAct(
            actId = lead.groupId ?: lead.id,
            isGroup = lead.groupId != null,
            sentence = "${displayName(recorder)} recorded: ${actPhrase(lead)}",
            amount = formatKes(lead.amountCents),
            detail = actDetail(act),
            recordedBy = displayName(recorder),
            recordedById = recorder,
            whenRecorded = lead.recordedAt?.let { relativeTime(it, now) } ?: "just now",
            eligibleConfirmers = eligibleConfirmers(lead, memberIds(), config)
                .map { MemberRef(it, displayName(it)) },
            entryCount = act.size,
        )
    }

/** The act in plain words, without the amount — the caller shows that big. */
private fun LedgerBook.actPhrase(lead: Entry): String {
    val who = displayName(lead.memberId)
    return when (lead.type) {
        EntryType.CONTRIBUTION -> "$who adds to the pool"
        EntryType.PAYOUT -> "pay $who out of the pool"
        EntryType.LOAN_OUT -> "lend to $who"
        EntryType.LOAN_REPAYMENT -> "$who pays back a loan"
        EntryType.MEMBER_LOAN_IN -> "$who lends to the pool"
        EntryType.POOL_REPAY_MEMBER -> "pay $who back"
        EntryType.TRANSFER -> "move money between pool accounts"
        EntryType.INTEREST_ACCRUAL -> "interest on $who's loan"
        EntryType.TXN_COST -> "M-Pesa cost on $who's loan"
        EntryType.REVERSAL -> "cancel an earlier entry"
    }
}

/** The second line, where there is genuinely more to say. */
private fun actDetail(act: List<Entry>): String? {
    if (act.size <= 1) return null
    val principal = act.firstOrNull { it.type == EntryType.LOAN_OUT }?.amountCents ?: return null
    val interest = act.filter { it.type == EntryType.INTEREST_ACCRUAL }.sumOf { it.amountCents }
    val cost = act.filter { it.type == EntryType.TXN_COST }.sumOf { it.amountCents }
    return "They repay ${formatKes(principal + interest + cost)} in total"
}

// ── members ──────────────────────────────────────────────────────────────────

data class MemberCard(
    val id: MemberId,
    val name: String,
    val initial: String,
    val stake: String,
    /** "owes KSh 1,673" / "clear" / "the pool owes them KSh 400" */
    val standingLine: String,
    val owesCents: Long,
    val inDebt: Boolean,
)

fun LedgerBook.memberCards(): List<MemberCard> {
    val s = state()
    return members.map { m ->
        val b = s.balanceOf(m.id)
        MemberCard(
            id = m.id,
            name = m.displayName,
            initial = m.displayName.take(1).uppercase(),
            stake = formatKes(b.stakeCents),
            standingLine = when {
                b.debtCents < 0L -> "owes ${formatKes(-b.debtCents)}"
                b.debtCents > 0L -> "the pool owes them ${formatKes(b.debtCents)}"
                else -> "clear"
            },
            owesCents = b.debtCents,
            inDebt = b.debtCents < 0L,
        )
    }
}

// ── activity ─────────────────────────────────────────────────────────────────

data class ActivityRow(
    val entryId: String,
    val sentence: String,
    val amount: String,
    val standing: Standing,
    val footnote: String,
)

/**
 * The append-only record, newest first. The trust surface: who recorded it, who
 * confirmed it, when. Nothing here is editable, only added to.
 */
fun LedgerBook.activity(now: Instant? = null, limit: Int? = null): List<ActivityRow> {
    val rows = entries
        .sortedByDescending { it.seq ?: Long.MIN_VALUE }
        .filter { it.type != EntryType.INTEREST_ACCRUAL && it.type != EntryType.TXN_COST }
        .map { e ->
            val recorder = displayName(e.recordedByMemberId ?: "?")
            ActivityRow(
                entryId = e.id,
                sentence = actPhrase(e).replaceFirstChar { it.uppercase() },
                amount = formatKes(e.amountCents),
                standing = when (e.state) {
                    EntryState.CONFIRMED -> Standing.CONFIRMED
                    EntryState.DISPUTED -> Standing.REJECTED
                    else -> Standing.PENDING
                },
                footnote = when (e.state) {
                    EntryState.CONFIRMED ->
                        "$recorder recorded · ${displayName(e.confirmedByMemberId ?: "?")} confirmed" +
                            (e.confirmedAt?.let { " · ${relativeTime(it, now)}" } ?: "")
                    EntryState.DISPUTED ->
                        "$recorder recorded · ${displayName(e.rejectedByMemberId ?: "?")} rejected"
                    else -> "$recorder recorded · waiting for someone else"
                },
            )
        }
    return if (limit == null) rows else rows.take(limit)
}

// ── member detail ────────────────────────────────────────────────────────────

data class MemberDetail(
    val name: String,
    val stake: String,
    val standingLine: String,
    val loans: List<LoanRow>,
    val activity: List<ActivityRow>,
)

fun LedgerBook.memberDetail(memberId: MemberId, now: Instant? = null): MemberDetail {
    val card = memberCards().first { it.id == memberId }
    return MemberDetail(
        name = card.name,
        stake = card.stake,
        standingLine = card.standingLine,
        loans = loanRows().filter { row ->
            loans.firstOrNull { it.id == row.loanId }?.counterpartyMemberId == memberId
        },
        activity = activity(now).filter { row ->
            entries.firstOrNull { it.id == row.entryId }?.memberId == memberId
        },
    )
}

// ── the lend / borrow quote ──────────────────────────────────────────────────

/**
 * What a loan will cost, worked out before anyone commits to it.
 *
 * The review step shows this in full. Nobody should agree to a loan without
 * seeing the number they will actually repay.
 */
data class LoanQuote(
    val principal: String,
    val interest: String,
    val txnCost: String,
    val totalRepayable: String,
    val rateLabel: String,
    val principalCents: Long,
    val interestCents: Long,
    val txnCostCents: Long,
) {
    val totalRepayableCents: Long get() = principalCents + interestCents + txnCostCents
}

fun quoteLoan(principalCents: Long, txnCostCents: Long = 0L, rateBps: Int = HOUSE_RATE_BPS): LoanQuote {
    val interest = interestCents(principalCents, rateBps)
    return LoanQuote(
        principal = formatKes(principalCents),
        interest = formatKes(interest),
        txnCost = formatKes(txnCostCents),
        totalRepayable = formatKes(principalCents + interest + txnCostCents),
        rateLabel = "${rateBps / 100.0}% one-off charge",
        principalCents = principalCents,
        interestCents = interest,
        txnCostCents = txnCostCents,
    )
}

/** A loan a member could pay against, for the Repay flow. */
data class RepayableLoan(
    val loanId: String,
    val borrower: String,
    val borrowerId: MemberId,
    val remaining: String,
    val remainingCents: Long,
)

fun LedgerBook.repayableLoans(): List<RepayableLoan> {
    val s = state()
    return loans.mapNotNull { loan ->
        val position = s.loans[loan.id] ?: return@mapNotNull null
        if (position.settled) return@mapNotNull null
        RepayableLoan(
            loanId = loan.id,
            borrower = displayName(loan.counterpartyMemberId),
            borrowerId = loan.counterpartyMemberId,
            remaining = formatKes(position.outstandingCents),
            remainingCents = position.outstandingCents,
        )
    }
}

// ── time, in words ───────────────────────────────────────────────────────────

/**
 * "just now", "5 min ago", "3 h ago", "2 days ago".
 *
 * Deliberately coarse. Nobody needs the second a contribution was typed, and a
 * precise timestamp on a money screen reads as clutter rather than rigour.
 */
fun relativeTime(then: Instant, now: Instant?): String {
    if (now == null) return "recently"
    val seconds = (now - then).inWholeSeconds
    return when {
        seconds < 0L -> "just now"
        seconds < 60L -> "just now"
        seconds < 3_600L -> "${seconds / 60} min ago"
        seconds < 86_400L -> "${seconds / 3_600} h ago"
        seconds < 172_800L -> "yesterday"
        else -> "${seconds / 86_400} days ago"
    }
}
