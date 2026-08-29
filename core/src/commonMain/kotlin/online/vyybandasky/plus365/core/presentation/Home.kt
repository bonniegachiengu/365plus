package online.vyybandasky.plus365.core.presentation

import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.domain.Entry
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.domain.MemberId
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.governance.eligibleConfirmers
import online.vyybandasky.plus365.core.domain.MemberKind
import online.vyybandasky.plus365.core.interest.rateFor
import online.vyybandasky.plus365.core.interest.interestCents
import online.vyybandasky.plus365.core.money.formatKes
import online.vyybandasky.plus365.core.sms.Assurance

/**
 * The home screen and its flows, in the words a member reads.
 *
 * Every sentence on screen is built here, in shared code, so the phone and the
 * laptop cannot describe the same entry differently. Names and shillings only —
 * no entry ids, no type names, no "outstanding principal".
 */

/**
 * Where an entry stands.
 *
 * [NEEDS_SETTLING] is deliberately its own state rather than a flavour of
 * [PENDING]. Money merely queued behind a second pair of eyes and money stuck in
 * a disagreement are not the same situation, and a member glancing at a list
 * should not have to read the small print to tell them apart.
 */
enum class Standing { CONFIRMED, PENDING, NEEDS_SETTLING, REJECTED }

// ── the hero ─────────────────────────────────────────────────────────────────

data class CashOnHand(
    val total: String,
    val memberCountLine: String,
    val lastUpdated: String,
    val pendingLine: String?,
    val accounts: List<AccountRow>,
    val pockets: List<PocketRow>,
)

fun LedgerBook.cashOnHand(now: Instant? = null): CashOnHand {
    val s = state()
    val pendingCount = pendingActs().size
    return CashOnHand(
        total = formatKes(s.cashAtHandCents),
        memberCountLine = "across ${founders().size} members",
        lastUpdated = lastActivityAt()?.let { "updated ${relativeTime(it, now)}" } ?: "no activity yet",
        pendingLine = when (pendingCount) {
            0 -> null
            1 -> "1 entry waiting to be confirmed"
            else -> "$pendingCount entries waiting to be confirmed"
        },
        accounts = summaryView().accounts,
        pockets = summaryView().pockets,
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

/**
 * The rarer moves: housekeeping rather than lending.
 *
 * Kept off the four primary actions because they are not what anyone opens the
 * app to do — but the book could already do all three, and a capability with no
 * screen may as well not exist.
 */
enum class PoolMove(val label: String, val blurb: String) {
    /** Between the pool's own accounts. Cash-at-hand cannot change. */
    MOVE("Move money", "Between the pool's own accounts"),

    /** Between pockets. No money moves at all. */
    EARMARK("Set aside", "Change what money is earmarked for"),

    /** A savings account paying out. */
    INTEREST("Interest earned", "Record what Ziidi or M-Shwari paid"),
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
    /** The code the confirmer must match, if the recorder supplied one. */
    val reference: String? = null,
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
            reference = act.firstNotNullOfOrNull { it.recordedEvidence?.reference },
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
        EntryType.ACCOUNT_INTEREST -> "interest earned on the pool's savings"
        EntryType.POCKET_TRANSFER -> "change what money is set aside for"
        EntryType.INTEREST_ACCRUAL -> "interest on $who's loan"
        EntryType.TXN_COST -> "transaction cost on $who's loan"
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
    /** Their pool contribution. */
    val stake: String,
    /** "pending loan amount KSh 1,633" / "no pending loan" / "the pool owes them ..." */
    val standingLine: String,
    val owesCents: Long,
    val inDebt: Boolean,
    /** Someone Keshflo lends to, not a member of the pool. */
    val isBeneficiary: Boolean = false,
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
                b.debtCents < 0L -> "pending loan amount ${formatKes(-b.debtCents)}"
                b.debtCents > 0L -> "the pool owes them ${formatKes(b.debtCents)}"
                else -> "no pending loan"
            },
            owesCents = b.debtCents,
            inDebt = b.debtCents < 0L,
            isBeneficiary = m.isBeneficiary,
        )
    }
}

/** The three. The Members list on the home screen shows these. */
fun LedgerBook.founderCards(): List<MemberCard> = memberCards().filter { !it.isBeneficiary }

/** People Keshflo has lent to. Shown apart — they are borrowers, not members. */
fun LedgerBook.beneficiaryCards(): List<MemberCard> = memberCards().filter { it.isBeneficiary }

// ── activity ─────────────────────────────────────────────────────────────────

data class ActivityRow(
    val entryId: String,
    val sentence: String,
    val amount: String,
    val standing: Standing,
    val footnote: String,
    /** Null until confirmed. Shown so the weaker kind never passes for the stronger. */
    val assurance: Assurance? = null,
    /** The shared transaction code, where there is one. */
    val reference: String? = null,
)

/**
 * The append-only record, newest first. The trust surface: who recorded it, who
 * confirmed it, when. Nothing here is editable, only added to.
 *
 * [everything] decides whether a loan's interest and transaction-cost legs show.
 * The home screen hides them, because four lines for one loan reads as noise on
 * a summary. The ledger shows them, because a screen that claims to be the whole
 * record and quietly drops two entries per loan is not the whole record.
 */
fun LedgerBook.activity(
    now: Instant? = null,
    limit: Int? = null,
    everything: Boolean = false,
): List<ActivityRow> {
    val rows = entries
        .sortedByDescending { it.seq ?: Long.MIN_VALUE }
        .filter {
            everything ||
                (it.type != EntryType.INTEREST_ACCRUAL && it.type != EntryType.TXN_COST)
        }
        .map { e ->
            val recorder = displayName(e.recordedByMemberId ?: "?")
            ActivityRow(
                entryId = e.id,
                sentence = actPhrase(e).replaceFirstChar { it.uppercase() },
                amount = formatKes(e.amountCents),
                standing = when (e.state) {
                    EntryState.CONFIRMED -> Standing.CONFIRMED
                    EntryState.DISPUTED -> Standing.REJECTED
                    EntryState.NEEDS_OVERRIDE -> Standing.NEEDS_SETTLING
                    else -> Standing.PENDING
                },
                footnote = when (e.state) {
                    EntryState.CONFIRMED ->
                        "$recorder recorded · ${displayName(e.confirmedByMemberId ?: "?")} confirmed" +
                            (e.confirmedAt?.let { " · ${relativeTime(it, now)}" } ?: "")
                    EntryState.DISPUTED ->
                        "$recorder recorded · ${displayName(e.rejectedByMemberId ?: "?")} rejected"
                    EntryState.NEEDS_OVERRIDE ->
                        "$recorder recorded · ${displayName(e.conflict?.raisedBy ?: "?")} disapproved " +
                            "· with the third member"
                    else -> "$recorder recorded · waiting for someone else"
                },
                assurance = e.assurance,
                reference = e.recordedEvidence?.reference,
            )
        }
    return if (limit == null) rows else rows.take(limit)
}

// ── member detail ────────────────────────────────────────────────────────────

data class MemberDetail(
    val name: String,
    val initial: String,
    val stake: String,
    val standingLine: String,
    val inDebt: Boolean,
    /** Their pending loan amount. */
    val owes: String,
    val contributionCount: Int,
    val activeLoanCount: Int,
    val loans: List<LoanRow>,
    val activity: List<ActivityRow>,
)

fun LedgerBook.memberDetail(memberId: MemberId, now: Instant? = null): MemberDetail {
    val card = memberCards().first { it.id == memberId }
    val theirLoans = loanRows().filter { row ->
        loans.firstOrNull { it.id == row.loanId }?.counterpartyMemberId == memberId
    }
    return MemberDetail(
        name = card.name,
        initial = card.initial,
        stake = card.stake,
        standingLine = card.standingLine,
        inDebt = card.inDebt,
        owes = formatKes(if (card.owesCents < 0) -card.owesCents else 0L),
        contributionCount = entries.count {
            it.memberId == memberId &&
                it.type == EntryType.CONTRIBUTION &&
                it.state == EntryState.CONFIRMED
        },
        activeLoanCount = theirLoans.count { !it.settled },
        loans = theirLoans,
        activity = activity(now, everything = true).filter { row ->
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
    /** "Founder rate" or "Keshflo rate", so nobody has to work out which applied. */
    val tierLabel: String,
    val principalCents: Long,
    val interestCents: Long,
    val txnCostCents: Long,
) {
    val totalRepayableCents: Long get() = principalCents + interestCents + txnCostCents
}

/**
 * What a loan will cost, at the rate this borrower attracts.
 *
 * The tier is shown, not just the number. A member seeing "10%" without being
 * told why would reasonably think it a mistake.
 */
fun quoteLoan(
    principalCents: Long,
    kind: MemberKind = MemberKind.FOUNDER,
    txnCostCents: Long = 0L,
    rateBps: Int? = null,
): LoanQuote {
    val rate = rateBps ?: rateFor(kind)
    val interest = interestCents(principalCents, rate)
    return LoanQuote(
        principal = formatKes(principalCents),
        interest = formatKes(interest),
        txnCost = formatKes(txnCostCents),
        totalRepayable = formatKes(principalCents + interest + txnCostCents),
        rateLabel = "${rate / 100.0}% one-off interest",
        tierLabel = when (kind) {
            MemberKind.FOUNDER -> "Founder rate"
            MemberKind.KESHFLO_BENEFICIARY -> "Keshflo rate"
        },
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
    /** The pending loan amount. */
    val remaining: String,
    val remainingCents: Long,
    val borrowerIsBeneficiary: Boolean = false,
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
            borrowerIsBeneficiary = member(loan.counterpartyMemberId)?.isBeneficiary == true,
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
