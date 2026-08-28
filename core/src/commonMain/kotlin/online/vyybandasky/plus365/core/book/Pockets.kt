package online.vyybandasky.plus365.core.book

import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.domain.AccountId
import online.vyybandasky.plus365.core.domain.Entry
import online.vyybandasky.plus365.core.domain.EntryId
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.domain.MemberId
import online.vyybandasky.plus365.core.domain.Pocket
import online.vyybandasky.plus365.core.domain.PocketId
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.governance.Decision
import online.vyybandasky.plus365.core.governance.Refusal
import online.vyybandasky.plus365.core.governance.checkRecord

/**
 * The two things that only exist because accounts and pockets are separate.
 *
 * Money the pool did not put anywhere — a fund paying out — and money that moved
 * from one earmark to another without going anywhere at all.
 */

/**
 * Record interest a savings account paid the pool.
 *
 * Its own entry type rather than a contribution: nobody's pool contribution
 * rises when Ziidi pays out, and recording it as a contribution would credit a
 * member with money they never put in.
 *
 * It still waits for a second member like everything else. The fund's message is
 * the evidence when the format is known; until then it is confirmed by hand.
 */
fun LedgerBook.recordAccountInterest(
    id: EntryId,
    accountId: AccountId,
    amountCents: Long,
    recordedBy: MemberId,
    config: ActorConfig,
    pocketId: PocketId? = null,
    note: String? = null,
    at: Instant? = null,
): Decision<Recorded> {
    when (val gate = checkRecord(recordedBy, config)) {
        is Decision.Refused -> return gate
        is Decision.Allowed -> Unit
    }
    if (!isFounder(recordedBy)) {
        return Decision.Refused(Refusal.NotAMember(recordedBy))
    }
    val account = account(accountId)
        ?: return Decision.Refused(Refusal.Invalid("Unknown account."))
    if (!account.earnsInterest) {
        return Decision.Refused(
            Refusal.Invalid("${account.label} does not earn interest, so it cannot pay any."),
        )
    }
    if (amountCents <= 0L) {
        return Decision.Refused(Refusal.Invalid("Amount must be more than zero."))
    }
    if (entry(id) != null) {
        return Decision.Allowed(Recorded(this, listOf(entry(id)!!)))
    }

    val appended = Entry(
        id = id,
        seq = nextSeq,
        type = EntryType.ACCOUNT_INTEREST,
        amountCents = amountCents,
        // The pool as a whole earned it, so it is booked against whoever
        // recorded it only for provenance — no stake moves.
        memberId = recordedBy,
        accountId = accountId,
        pocketId = pocketId,
        recordedByMemberId = recordedBy,
        recordedAt = at,
        state = EntryState.PENDING,
        note = note ?: "Interest paid by ${account.label}",
    )
    return Decision.Allowed(
        Recorded(copy(entries = entries + appended, nextSeq = nextSeq + 1), listOf(appended)),
    )
}

/**
 * Change what a sum is earmarked for, without moving it anywhere.
 *
 * Cash-at-hand and every account balance are untouched. Only the pocket split
 * changes — which is the whole reason pockets are not accounts.
 *
 * Still two people: deciding that a slice of the members' savings is now the
 * Keshflo fund is a decision about the members' money, whatever account it sits
 * in.
 */
fun LedgerBook.reallocate(
    id: EntryId,
    fromPocket: PocketId,
    toPocket: PocketId,
    amountCents: Long,
    recordedBy: MemberId,
    config: ActorConfig,
    note: String? = null,
    at: Instant? = null,
): Decision<Recorded> {
    when (val gate = checkRecord(recordedBy, config)) {
        is Decision.Refused -> return gate
        is Decision.Allowed -> Unit
    }
    if (!isFounder(recordedBy)) {
        return Decision.Refused(Refusal.NotAMember(recordedBy))
    }
    if (pocket(fromPocket) == null || pocket(toPocket) == null) {
        return Decision.Refused(Refusal.Invalid("Unknown pocket."))
    }
    if (fromPocket == toPocket) {
        return Decision.Refused(Refusal.Invalid("Re-earmarking needs two different pockets."))
    }
    if (amountCents <= 0L) {
        return Decision.Refused(Refusal.Invalid("Amount must be more than zero."))
    }

    val appended = Entry(
        id = id,
        seq = nextSeq,
        type = EntryType.POCKET_TRANSFER,
        amountCents = amountCents,
        memberId = recordedBy,
        pocketId = toPocket,
        counterPocketId = fromPocket,
        recordedByMemberId = recordedBy,
        recordedAt = at,
        state = EntryState.PENDING,
        note = note,
    )
    return Decision.Allowed(
        Recorded(copy(entries = entries + appended, nextSeq = nextSeq + 1), listOf(appended)),
    )
}

/** The pockets this book knows about. */
fun LedgerBook.pocket(id: PocketId): Pocket? = pockets.firstOrNull { it.id == id }

fun LedgerBook.pocketLabel(id: PocketId): String = pocket(id)?.label ?: id

/** Accounts that grow on their own. Where interest can legitimately arrive. */
fun LedgerBook.earningAccounts() = accounts.filter { it.earnsInterest }
