package online.vyybandasky.plus365.core.book

import online.vyybandasky.plus365.core.domain.Entry
import online.vyybandasky.plus365.core.domain.EntryId
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.domain.TargetChange
import online.vyybandasky.plus365.core.domain.TargetChangeState

/**
 * What a merge did, in the terms somebody would want to check it in.
 *
 * Returned rather than logged. A sync that quietly resolved a disagreement is a
 * sync nobody can audit, and disagreement about money is the one thing here that
 * must never be settled invisibly.
 */
data class MergeReport(
    /** Entries this book had never seen. */
    val added: List<EntryId> = emptyList(),

    /** Entries that arrived carrying a confirmation this book did not have. */
    val advanced: List<EntryId> = emptyList(),

    /**
     * Entries settled one way here and another way there.
     *
     * Kept, never resolved. This book's version stands — on the laptop that
     * means the authoritative ledger wins — and the ids come back so a person
     * can look at both and decide. Picking a winner by timestamp would be
     * choosing which member was right by which clock was faster.
     */
    val conflicted: List<EntryId> = emptyList(),

    /** Target proposals whose approvals were pooled. */
    val approvalsPooled: List<String> = emptyList(),

    /** Proposals that reached unanimity only because of that pooling. */
    val settledByPooling: List<String> = emptyList(),

    /** Places and people the incoming book knew about and this one did not. */
    val definitionsAdded: List<String> = emptyList(),
) {
    val changedAnything: Boolean
        get() = added.isNotEmpty() || advanced.isNotEmpty() ||
            approvalsPooled.isNotEmpty() || definitionsAdded.isNotEmpty()
}

/**
 * How settled an entry is. Higher means further along.
 *
 * PENDING is the only state a merge will overwrite. Everything above it is a
 * decision somebody made about somebody else's money, and a decision that can be
 * replaced by syncing is not a decision.
 */
private fun EntryState.settledness(): Int = when (this) {
    EntryState.DRAFT -> 0
    EntryState.PENDING -> 1
    EntryState.CONFIRMED, EntryState.DISPUTED, EntryState.NEEDS_OVERRIDE -> 2
    EntryState.VOID -> 2
}

/**
 * Fold another book into this one.
 *
 * The whole of sync, and deliberately the only place that decides anything. The
 * laptop calls it on what a phone sent; nothing else merges.
 *
 * Entries are matched by id, which works because ids are generated on the device
 * that records the entry and never reused — so the same entry seen twice is the
 * same entry, and two entries are only the same if they really are.
 *
 * The rules, in the order they matter:
 *
 *  * An entry this book has never seen is taken.
 *  * An entry this book has as PENDING, arriving settled, is taken. That is
 *    cross-device confirmation: Brian confirms on his phone what Bonnie
 *    recorded on hers, and the laptop learns it here.
 *  * An entry settled differently on both sides is **not** resolved. This
 *    book's version stands and the id is reported. Two people disagreeing about
 *    money is exactly the thing the app refuses to settle on their behalf.
 *  * Definitions — members, accounts, pockets — are added if missing and never
 *    overwritten. The laptop is the authority on wording; a phone can introduce
 *    a place, not rename one out from under everybody.
 *  * Approvals on a target change are **pooled**, because unanimity is a set
 *    union by nature. Two phones each holding a different half of the yeses is
 *    not a conflict, it is the answer arriving in two pieces — and if pooling
 *    completes the set, the change lands here and now.
 */
fun LedgerBook.mergeFrom(incoming: LedgerBook): Pair<LedgerBook, MergeReport> {
    val mine = entries.associateBy { it.id }

    val added = mutableListOf<EntryId>()
    val advanced = mutableListOf<EntryId>()
    val conflicted = mutableListOf<EntryId>()
    val defsAdded = mutableListOf<String>()

    val mergedEntries = LinkedHashMap<EntryId, Entry>(mine.size + incoming.entries.size)
    mergedEntries.putAll(mine)

    for (theirs in incoming.entries) {
        val ours = mine[theirs.id]
        if (ours == null) {
            mergedEntries[theirs.id] = theirs
            added += theirs.id
            continue
        }
        if (ours == theirs) continue

        val ourRank = ours.state.settledness()
        val theirRank = theirs.state.settledness()
        when {
            theirRank > ourRank -> {
                mergedEntries[theirs.id] = theirs
                advanced += theirs.id
            }
            theirRank < ourRank -> Unit // ours is further along; keep it.
            ours.state != theirs.state -> conflicted += theirs.id
            // Same state, different detail — a confirmation recorded twice with
            // different evidence, say. Ours stands and it is worth a look.
            else -> conflicted += theirs.id
        }
    }

    // ── definitions: add what is missing, overwrite nothing ──────────────────
    val myMembers = members.map { it.id }.toSet()
    val newMembers = incoming.members.filter { it.id !in myMembers }
    val myAccounts = accounts.map { it.id }.toSet()
    val newAccounts = incoming.accounts.filter { it.id !in myAccounts }
    val myPockets = pockets.map { it.id }.toSet()
    val newPockets = incoming.pockets.filter { it.id !in myPockets }
    val myLoans = loans.map { it.id }.toSet()
    val newLoans = incoming.loans.filter { it.id !in myLoans }
    newMembers.forEach { defsAdded += "member ${it.id}" }
    newAccounts.forEach { defsAdded += "account ${it.id}" }
    newPockets.forEach { defsAdded += "pocket ${it.id}" }
    newLoans.forEach { defsAdded += "loan ${it.id}" }

    // ── target changes: pool the approvals ───────────────────────────────────
    val pooled = mutableListOf<String>()
    val myChanges = targetChanges.associateBy { it.id }
    val mergedChanges = LinkedHashMap<String, TargetChange>(myChanges.size)
    mergedChanges.putAll(myChanges)
    for (theirs in incoming.targetChanges) {
        val ours = mergedChanges[theirs.id]
        if (ours == null) {
            mergedChanges[theirs.id] = theirs
            pooled += theirs.id
            continue
        }
        if (ours.state != TargetChangeState.PROPOSED) continue
        if (theirs.state != TargetChangeState.PROPOSED) {
            // Somebody answered decisively over there. A refusal ends it, and an
            // agreement is only reachable through the same union we are doing.
            mergedChanges[theirs.id] = theirs
            pooled += theirs.id
            continue
        }
        val union = ours.approvals + theirs.approvals
        if (union != ours.approvals) {
            mergedChanges[theirs.id] = ours.copy(approvals = union)
            pooled += theirs.id
        }
    }

    var merged = copy(
        members = members + newMembers,
        accounts = accounts + newAccounts,
        pockets = pockets + newPockets,
        loans = loans + newLoans,
        entries = mergedEntries.values.sortedBy { it.seq ?: Long.MAX_VALUE },
        targetChanges = mergedChanges.values.toList(),
        nextSeq = maxOf(nextSeq, incoming.nextSeq),
    )

    // Pooling can complete a set of yeses that neither device had on its own.
    val settled = mutableListOf<String>()
    for (id in mergedChanges.keys) {
        val before = merged.targetChange(id)
        if (before?.state != TargetChangeState.PROPOSED) continue
        val after = merged.settleIfUnanimousInternal(id)
        if (after.targetChange(id)?.state == TargetChangeState.AGREED) {
            settled += id
            merged = after
        }
    }

    return merged to MergeReport(
        added = added,
        advanced = advanced,
        conflicted = conflicted,
        approvalsPooled = pooled,
        settledByPooling = settled,
        definitionsAdded = defsAdded,
    )
}
