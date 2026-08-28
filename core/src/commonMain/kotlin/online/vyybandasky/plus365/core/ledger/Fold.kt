package online.vyybandasky.plus365.core.ledger

import online.vyybandasky.plus365.core.domain.AccountId
import online.vyybandasky.plus365.core.domain.ChargeKind
import online.vyybandasky.plus365.core.domain.Accounts
import online.vyybandasky.plus365.core.domain.Entry
import online.vyybandasky.plus365.core.domain.EntryId
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.domain.Loan
import online.vyybandasky.plus365.core.domain.LoanDirection
import online.vyybandasky.plus365.core.domain.LoanId
import online.vyybandasky.plus365.core.domain.MemberId
import online.vyybandasky.plus365.core.domain.PocketId
import online.vyybandasky.plus365.core.domain.Pockets

/** The effect of one entry on the three quantities the ledger tracks. */
data class Effect(
    val stakeCents: Long = 0L,
    val debtCents: Long = 0L,
    val poolCashCents: Long = 0L,
) {
    operator fun plus(other: Effect) = Effect(
        stakeCents + other.stakeCents,
        debtCents + other.debtCents,
        poolCashCents + other.poolCashCents,
    )

    fun negated() = Effect(-stakeCents, -debtCents, -poolCashCents)
}

/** How deep a reversal-of-a-reversal chain may go before we call it a cycle. */
private const val MAX_REVERSAL_DEPTH = 32

/**
 * The §2a table, encoded exactly once.
 *
 * REVERSAL is absent on purpose: its effect is the inverse of whatever it points
 * at, which only [fold] can resolve.
 */
fun effectOf(
    type: EntryType,
    amountCents: Long,
    loanDirection: LoanDirection? = null,
): Effect {
    val amt = amountCents
    return when (type) {
        EntryType.CONTRIBUTION -> Effect(stakeCents = amt, poolCashCents = amt)
        EntryType.PAYOUT -> Effect(stakeCents = -amt, poolCashCents = -amt)
        EntryType.LOAN_OUT -> Effect(debtCents = -amt, poolCashCents = -amt)
        EntryType.LOAN_REPAYMENT -> Effect(debtCents = amt, poolCashCents = amt)
        EntryType.MEMBER_LOAN_IN -> Effect(debtCents = amt, poolCashCents = amt)
        EntryType.POOL_REPAY_MEMBER -> Effect(debtCents = -amt, poolCashCents = -amt)

        // Interest changes what is owed and never moves cash. Which way it moves
        // depends on who lent to whom, so it needs the loan.
        EntryType.INTEREST_ACCRUAL -> when (loanDirection) {
            LoanDirection.POOL_TO_MEMBER -> Effect(debtCents = -amt)
            LoanDirection.MEMBER_TO_POOL -> Effect(debtCents = amt)
            null -> throw IllegalArgumentException(
                "INTEREST_ACCRUAL needs its loan to know the direction; pass loans to fold()"
            )
        }

        // The pool pays the transfer fee out of pocket and the counterparty owes
        // it back, so unlike interest this one DOES move cash. Direction follows
        // the loan for the same reason interest does.
        EntryType.TXN_COST -> when (loanDirection) {
            LoanDirection.POOL_TO_MEMBER ->
                Effect(debtCents = -amt, poolCashCents = -amt)
            LoanDirection.MEMBER_TO_POOL ->
                Effect(debtCents = amt, poolCashCents = -amt)
            null -> throw IllegalArgumentException(
                "TXN_COST needs its loan to know the direction; pass loans to fold()"
            )
        }

        // Between the pool's own accounts. Cash-at-hand cannot change, so the
        // net effect is zero — the movement is in the per-account split alone.
        EntryType.TRANSFER -> Effect()

        /**
         * A fund paying out. Real cash arriving that nobody contributed, so it
         * lifts the pool without lifting anyone's pool contribution.
         */
        EntryType.ACCOUNT_INTEREST -> Effect(poolCashCents = amt)

        // Re-earmarking. Neither cash nor any account moves; only the pocket
        // split does, and that is applied outside the effect table.
        EntryType.POCKET_TRANSFER -> Effect()

        EntryType.REVERSAL -> throw IllegalArgumentException(
            "a REVERSAL effect is the inverse of its target and is resolved by fold()"
        )
    }
}

/**
 * Replays the log into balances. Pure — no I/O, no clock.
 *
 * Only [EntryState.CONFIRMED] entries move the real balances. PENDING is reported
 * separately so an unconfirmed claim is never mistaken for money. DISPUTED and
 * VOID are ignored entirely.
 *
 * Ordering is by seq; entries without one (not yet accepted by the master) sort
 * last, by id, so the result is deterministic for any input order.
 *
 * @param loans needed only to resolve INTEREST_ACCRUAL direction. The brief
 *   documents this as fold(entries), and that still works — loans is a default.
 * @param includeDrafts folds this device's own unsynced DRAFT entries in
 *   optimistically (§2c). Never do this for another device's drafts.
 */
fun fold(
    entries: List<Entry>,
    loans: List<Loan> = emptyList(),
    includeDrafts: Boolean = false,
): LedgerState {
    val byId: Map<EntryId, Entry> = entries.associateBy { it.id }
    val loansById: Map<LoanId, Loan> = loans.associateBy { it.id }

    // seq first (nulls last), then id — a total order regardless of input order.
    val ordered = entries.sortedWith(
        compareBy<Entry> { it.seq ?: Long.MAX_VALUE }.thenBy { it.id }
    )

    val confirmedMembers = mutableMapOf<MemberId, MemberBalance>()
    val pendingMembers = mutableMapOf<MemberId, MemberBalance>()
    val confirmedAccounts = mutableMapOf<AccountId, Long>()
    val confirmedPockets = mutableMapOf<PocketId, Long>()
    var confirmedCash = 0L
    var pendingCash = 0L
    var draftCount = 0

    val loanTallies = mutableMapOf<LoanId, MutableLoanTally>()

    for (entry in ordered) {
        val counts = when (entry.state) {
            EntryState.CONFIRMED -> true
            EntryState.DRAFT -> includeDrafts
            else -> false
        }
        // An entry waiting on the third member is money nobody has agreed to, so
        // it sits in the pending bucket rather than counting or vanishing.
        val isPending = entry.state == EntryState.PENDING ||
            entry.state == EntryState.NEEDS_OVERRIDE
        if (!counts && !isPending) continue

        val effect = resolveEffect(entry, byId, loansById, depth = 0)

        if (counts) {
            if (entry.state == EntryState.DRAFT) draftCount++
            confirmedMembers.accumulate(entry.memberId, effect)
            confirmedCash += effect.poolCashCents
            routeToAccounts(entry, byId, effect, confirmedAccounts)
            routeToPockets(entry, byId, effect, confirmedPockets)
            tallyLoan(entry, byId, loansById, loanTallies)
        } else {
            pendingMembers.accumulate(entry.memberId, effect)
            pendingCash += effect.poolCashCents
        }
    }

    return LedgerState(
        perMember = confirmedMembers.toMap(),
        poolCashCents = confirmedCash,
        perAccount = confirmedAccounts.filterValues { it != 0L }.toMap(),
        perPocket = confirmedPockets.filterValues { it != 0L }.toMap(),
        loans = loanTallies.mapValues { (_, tally) -> tally.toOutstanding() },
        pendingPerMember = pendingMembers.toMap(),
        pendingPoolCashCents = pendingCash,
        includedDraftCount = draftCount,
    )
}

private fun resolveEffect(
    entry: Entry,
    byId: Map<EntryId, Entry>,
    loansById: Map<LoanId, Loan>,
    depth: Int,
): Effect {
    if (entry.type != EntryType.REVERSAL) {
        return effectOf(entry.type, entry.amountCents, entry.loanDirection(loansById))
    }
    require(depth < MAX_REVERSAL_DEPTH) {
        "reversal chain deeper than " + MAX_REVERSAL_DEPTH + " at " + entry.id + "; likely a cycle"
    }
    val targetId = entry.reversesEntryId
        ?: throw IllegalArgumentException("REVERSAL " + entry.id + " has no target")
    val target = byId[targetId]
        ?: throw IllegalArgumentException(
            "REVERSAL " + entry.id + " points at " + targetId + ", which is not in the log"
        )
    return resolveEffect(target, byId, loansById, depth + 1).negated()
}

private fun Entry.loanDirection(loansById: Map<LoanId, Loan>): LoanDirection? =
    loanId?.let { loansById[it]?.direction }

/**
 * Put an entry's cash movement into the right pocket.
 *
 * Every entry that moves cash moves it through exactly one account, except a
 * TRANSFER, which moves it between two and nets to nothing. Cash-at-hand is the
 * sum over this map, so it agrees with [LedgerState.poolCashCents] by
 * construction rather than by anyone remembering to keep them in step.
 */
private fun routeToAccounts(
    entry: Entry,
    byId: Map<EntryId, Entry>,
    effect: Effect,
    into: MutableMap<AccountId, Long>,
) {
    // A reversal moves the inverse through whatever accounts its target used.
    val subject: Entry
    val sign: Long
    if (entry.type == EntryType.REVERSAL) {
        subject = byId[entry.reversesEntryId] ?: return
        sign = -1L
    } else {
        subject = entry
        sign = 1L
    }

    if (subject.type == EntryType.TRANSFER) {
        val from = subject.counterAccountId ?: Accounts.UNASSIGNED
        val to = subject.accountId ?: Accounts.UNASSIGNED
        val amt = subject.amountCents * sign
        into[from] = (into[from] ?: 0L) - amt
        into[to] = (into[to] ?: 0L) + amt
        return
    }

    if (effect.poolCashCents == 0L) return
    val account = subject.accountId ?: Accounts.UNASSIGNED
    into[account] = (into[account] ?: 0L) + effect.poolCashCents
}

/**
 * Put an entry's cash into the right pocket — the *what for*, not the *where*.
 *
 * The same movement is routed twice, once by account and once by pocket, from
 * the same effect. That is what keeps the two views summing to the same number:
 * they are not computed from each other, they are computed from the same source,
 * so neither can quietly drift.
 */
private fun routeToPockets(
    entry: Entry,
    byId: Map<EntryId, Entry>,
    effect: Effect,
    into: MutableMap<PocketId, Long>,
) {
    val subject: Entry
    val sign: Long
    if (entry.type == EntryType.REVERSAL) {
        subject = byId[entry.reversesEntryId] ?: return
        sign = -1L
    } else {
        subject = entry
        sign = 1L
    }

    if (subject.type == EntryType.POCKET_TRANSFER) {
        val from = subject.counterPocketId ?: Pockets.UNALLOCATED
        val to = subject.pocketId ?: Pockets.UNALLOCATED
        val amt = subject.amountCents * sign
        into[from] = (into[from] ?: 0L) - amt
        into[to] = (into[to] ?: 0L) + amt
        return
    }

    if (effect.poolCashCents == 0L) return
    val pocket = subject.pocketId ?: Pockets.UNALLOCATED
    into[pocket] = (into[pocket] ?: 0L) + effect.poolCashCents
}

private fun MutableMap<MemberId, MemberBalance>.accumulate(memberId: MemberId, effect: Effect) {
    val current = this[memberId] ?: MemberBalance()
    this[memberId] = MemberBalance(
        stakeCents = current.stakeCents + effect.stakeCents,
        debtCents = current.debtCents + effect.debtCents,
    )
}

/**
 * A loan's four components, kept apart.
 *
 * Principal, interest and transaction cost are gross — what was ever charged —
 * and [repaid] is what has come back. Both the old principal-only view and the
 * full outstanding derive from these same four numbers, so the two views cannot
 * drift from each other.
 */
private class MutableLoanTally(val loanId: LoanId, val direction: LoanDirection) {
    var principal = 0L
    var interest = 0L
    var mpesaCharge = 0L
    var bankCharge = 0L
    var repaid = 0L

    fun toOutstanding() = LoanOutstanding(
        loanId = loanId,
        direction = direction,
        principalCents = principal,
        interestAccruedCents = interest,
        mpesaChargeCents = mpesaCharge,
        bankChargeCents = bankCharge,
        repaidCents = repaid,
    )
}

/**
 * Loan-level running totals: principal, interest, transaction cost and what has
 * been repaid, each tallied separately.
 *
 * How a repayment is *allocated* across those three — interest first, or
 * principal first — is a later question. Until it is answered, a repayment
 * reduces the loan as a whole and the components stay gross.
 */
private fun tallyLoan(
    entry: Entry,
    byId: Map<EntryId, Entry>,
    loansById: Map<LoanId, Loan>,
    into: MutableMap<LoanId, MutableLoanTally>,
) {
    // A reversal is tallied as the inverse of its target.
    val subject: Entry
    val sign: Long
    if (entry.type == EntryType.REVERSAL) {
        subject = byId[entry.reversesEntryId] ?: return
        sign = -1L
    } else {
        subject = entry
        sign = 1L
    }

    val loanId = subject.loanId ?: return
    val loan = loansById[loanId] ?: return
    val tally = into.getOrPut(loanId) { MutableLoanTally(loanId, loan.direction) }
    val amt = subject.amountCents * sign

    when (subject.type) {
        EntryType.LOAN_OUT, EntryType.MEMBER_LOAN_IN -> tally.principal += amt
        EntryType.LOAN_REPAYMENT, EntryType.POOL_REPAY_MEMBER -> tally.repaid += amt
        EntryType.INTEREST_ACCRUAL -> tally.interest += amt
        EntryType.TXN_COST ->
            if (subject.chargeKind == ChargeKind.BANK) {
                tally.bankCharge += amt
            } else {
                tally.mpesaCharge += amt
            }
        else -> Unit
    }
}
