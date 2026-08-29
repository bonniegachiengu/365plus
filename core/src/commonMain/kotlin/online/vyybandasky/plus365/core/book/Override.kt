package online.vyybandasky.plus365.core.book

import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.domain.Entry
import online.vyybandasky.plus365.core.domain.EntryId
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.domain.MemberId
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.governance.Conflict
import online.vyybandasky.plus365.core.governance.ConflictKind
import online.vyybandasky.plus365.core.governance.Decision
import online.vyybandasky.plus365.core.governance.OverrideDecision
import online.vyybandasky.plus365.core.governance.OverrideRecord
import online.vyybandasky.plus365.core.governance.Refusal
import online.vyybandasky.plus365.core.governance.asNeedingOverride
import online.vyybandasky.plus365.core.governance.checkOverride
import online.vyybandasky.plus365.core.governance.checkRecord
import online.vyybandasky.plus365.core.governance.withOverride
import online.vyybandasky.plus365.core.sms.Assurance
import online.vyybandasky.plus365.core.domain.ConfirmSource
import online.vyybandasky.plus365.core.sms.redactContactNumbers

/**
 * The third member's door.
 *
 * A fallout — two messages that did not match, a member who disagrees, a figure
 * that needs correcting — moves the entry to [EntryState.NEEDS_OVERRIDE], where
 * neither of the two involved can touch it. The remaining member settles it, and
 * the settling is written into the log.
 */

/** Send an entry to the third member. Anyone involved may do this; nobody else needs to. */
fun LedgerBook.escalate(
    entryId: EntryId,
    raisedBy: MemberId,
    kind: ConflictKind,
    config: ActorConfig,
    reasons: List<String> = emptyList(),
    note: String? = null,
    at: Instant? = null,
): Decision<Recorded> {
    val target = entry(entryId)
        ?: return Decision.Refused(Refusal.UnknownEntry(entryId))
    when (val gate = checkRecord(raisedBy, config)) {
        is Decision.Refused -> return gate
        is Decision.Allowed -> Unit
    }
    if (!isFounder(raisedBy)) {
        return Decision.Refused(Refusal.NotAMember(raisedBy))
    }
    if (target.state != EntryState.PENDING) {
        return Decision.Refused(
            Refusal.Invalid("Only an entry still waiting can be sent to the third member."),
        )
    }
    // Escalating your own entry is fine — noticing your own mistake is not a
    // conflict of interest. It only bars you from being the one who settles it.
    val marked = target.asNeedingOverride(
        Conflict(kind = kind, raisedBy = raisedBy, at = at, reasons = reasons, note = note),
    )
    return Decision.Allowed(
        Recorded(copy(entries = entries.map { if (it.id == entryId) marked else it }), listOf(marked)),
    )
}

/**
 * Settle a fallout as the third member.
 *
 * [reason] is required. An override without a stated reason is an unexplained
 * decision about somebody else's money.
 */
fun LedgerBook.override(
    entryId: EntryId,
    overrider: MemberId,
    decision: OverrideDecision,
    reason: String,
    config: ActorConfig,
    at: Instant? = null,
    correctedAmountCents: Long? = null,
    replacementEntryId: EntryId? = null,
): Decision<Recorded> {
    val target = entry(entryId)
        ?: return Decision.Refused(Refusal.UnknownEntry(entryId))
    if (member(overrider) == null) {
        return Decision.Refused(Refusal.UnknownMember(overrider))
    }
    // Settling a disagreement about the members' money is the strongest thing
    // anyone does here. Someone the pool lends to has no stake to back it.
    if (!isFounder(overrider)) {
        return Decision.Refused(Refusal.NotAMember(overrider))
    }
    if (reason.isBlank()) {
        return Decision.Refused(
            Refusal.Invalid("Say why. An override with no reason is not a decision, it is a shrug."),
        )
    }
    when (val gate = checkOverride(target, overrider, config)) {
        is Decision.Refused -> return gate
        is Decision.Allowed -> Unit
    }

    return when (decision) {
        OverrideDecision.CONFIRMED -> {
            val record = OverrideRecord(overrider, decision, reason, at)
            val settled = target
                .withOverride(record, EntryState.CONFIRMED)
                .copy(
                    confirmedByMemberId = overrider,
                    confirmedAt = at,
                    confirmSource = ConfirmSource.OVERRIDE,
                    // Never CODE_MATCHED: the codes are exactly what failed.
                    assurance = Assurance.OVERRIDDEN,
                )
            Decision.Allowed(Recorded(replacing(entryId, settled), listOf(settled)))
        }

        OverrideDecision.REJECTED -> {
            val record = OverrideRecord(overrider, decision, reason, at)
            val settled = target
                .withOverride(record, EntryState.DISPUTED)
                .copy(rejectedByMemberId = overrider, rejectedAt = at, rejectionReason = reason)
            Decision.Allowed(Recorded(replacing(entryId, settled), listOf(settled)))
        }

        OverrideDecision.CORRECTED -> {
            val amount = correctedAmountCents
                ?: return Decision.Refused(Refusal.Invalid("A correction needs the corrected amount."))
            if (amount <= 0L) {
                return Decision.Refused(Refusal.Invalid("Amount must be more than zero."))
            }
            val newId = replacementEntryId
                ?: return Decision.Refused(Refusal.Invalid("A correction needs an id for the replacement."))
            if (entry(newId) != null) {
                return Decision.Refused(Refusal.Invalid("Replacement id $newId is already used."))
            }

            // Append rather than edit. The wrong figure and the right one both
            // stay in the log, joined, so the correction is legible as a
            // correction instead of looking like the truth all along.
            val record = OverrideRecord(
                by = overrider,
                decision = decision,
                reason = redactContactNumbers(reason),
                at = at,
                correctedAmountCents = amount,
                replacedByEntryId = newId,
            )
            val closed = target
                .withOverride(record, EntryState.DISPUTED)
                .copy(rejectedByMemberId = overrider, rejectedAt = at, rejectionReason = reason)

            val replacement = Entry(
                id = newId,
                seq = nextSeq,
                type = target.type,
                amountCents = amount,
                memberId = target.memberId,
                loanId = target.loanId,
                accountId = target.accountId,
                counterAccountId = target.counterAccountId,
                groupId = target.groupId,
                recordedByMemberId = target.recordedByMemberId,
                recordedAt = target.recordedAt,
                state = EntryState.CONFIRMED,
                confirmedByMemberId = overrider,
                confirmedAt = at,
                confirmSource = ConfirmSource.OVERRIDE,
                note = "Corrected by ${displayName(overrider)}: $reason",
                recordedEvidence = target.recordedEvidence,
                assurance = Assurance.OVERRIDDEN,
                correctsEntryId = entryId,
            )

            Decision.Allowed(
                Recorded(
                    replacing(entryId, closed).copy(
                        entries = replacing(entryId, closed).entries + replacement,
                        nextSeq = nextSeq + 1,
                    ),
                    listOf(closed, replacement),
                ),
            )
        }
    }
}

private fun LedgerBook.replacing(id: EntryId, updated: Entry): LedgerBook =
    copy(entries = entries.map { if (it.id == id) updated else it })

/** Everything sitting with the third member, oldest first. */
fun LedgerBook.needingOverride(): List<Entry> =
    entries.filter { it.state == EntryState.NEEDS_OVERRIDE }
        .sortedBy { it.seq ?: Long.MAX_VALUE }


/**
 * Confirm if the messages agree; hand it to the third member if they do not.
 *
 * One call because that is what actually happens: a member taps confirm, and
 * either it settles or it becomes somebody else's problem. Making the screen
 * orchestrate two calls would let a failed match end as a refusal that nobody
 * ever routed anywhere, which is the state this whole feature exists to remove.
 *
 * The returned entry's state says which way it went.
 */
fun LedgerBook.confirmOrEscalate(
    entryId: EntryId,
    confirmedBy: MemberId,
    config: ActorConfig,
    at: Instant? = null,
    evidence: online.vyybandasky.plus365.core.sms.SmsEvidence? = null,
): Decision<Recorded> {
    return when (val attempt = confirm(entryId, confirmedBy, config, at = at, evidence = evidence)) {
        is Decision.Allowed -> attempt
        is Decision.Refused -> when (val why = attempt.refusal) {
            is Refusal.EvidenceMismatch -> escalate(
                entryId = entryId,
                raisedBy = confirmedBy,
                kind = ConflictKind.EVIDENCE_MISMATCH,
                config = config,
                reasons = why.reasons,
                at = at,
            ).let { escalation ->
                when (escalation) {
                    is Decision.Refused -> escalation
                    is Decision.Allowed -> {
                        // Keep the message that failed, so the third member can
                        // read both halves rather than a description of them.
                        val marked = escalation.value.entry.let { e ->
                            e.copy(conflict = e.conflict?.copy(attemptedEvidence = evidence))
                        }
                        Decision.Allowed(
                            Recorded(
                                escalation.value.book.copy(
                                    entries = escalation.value.book.entries.map {
                                        if (it.id == entryId) marked else it
                                    },
                                ),
                                listOf(marked),
                            ),
                        )
                    }
                }
            }
            // Everything else is a plain refusal: self-confirmation, a message
            // that is not yours, an entry that is not pending. None of those are
            // disagreements, so none of them belong with the third member.
            else -> attempt
        }
    }
}


/**
 * Confirm a whole act if the messages agree; hand every leg to the third member
 * if they do not.
 *
 * A loan is three entries but one decision. Escalating only the leg that carried
 * the message would leave the other two waiting on a confirmation that can never
 * come — the interest and the cost have no message of their own — so the act
 * moves as a unit or not at all.
 */
fun LedgerBook.confirmGroupOrEscalate(
    groupId: String,
    confirmedBy: MemberId,
    config: ActorConfig,
    at: Instant? = null,
    evidence: online.vyybandasky.plus365.core.sms.SmsEvidence? = null,
): Decision<Recorded> {
    return when (val attempt = confirmGroup(groupId, confirmedBy, config, at = at, evidence = evidence)) {
        is Decision.Allowed -> attempt
        is Decision.Refused -> when (val why = attempt.refusal) {
            is Refusal.EvidenceMismatch -> {
                var book = this
                val moved = mutableListOf<Entry>()
                for (leg in group(groupId).filter { it.state == EntryState.PENDING }) {
                    when (
                        val step = book.escalate(
                            entryId = leg.id,
                            raisedBy = confirmedBy,
                            kind = ConflictKind.EVIDENCE_MISMATCH,
                            config = config,
                            reasons = why.reasons,
                            at = at,
                        )
                    ) {
                        is Decision.Refused -> return step
                        is Decision.Allowed -> {
                            // Keep the offered message on the leg that had one to
                            // compare against, so the third member sees both halves.
                            val marked = step.value.entry.let { e ->
                                if (e.recordedEvidence != null) {
                                    e.copy(conflict = e.conflict?.copy(attemptedEvidence = evidence))
                                } else {
                                    e
                                }
                            }
                            book = step.value.book.copy(
                                entries = step.value.book.entries.map {
                                    if (it.id == marked.id) marked else it
                                },
                            )
                            moved += marked
                        }
                    }
                }
                Decision.Allowed(Recorded(book, moved))
            }
            else -> attempt
        }
    }
}

/**
 * Settle every leg of an act in one decision.
 *
 * Correcting a multi-leg act is deliberately not offered. A loan's interest and
 * transaction cost follow from its principal, so quietly recomputing them from a
 * corrected figure would put numbers on the books that nobody agreed to. Reject
 * it and have it recorded again, which is slower and honest.
 */
fun LedgerBook.overrideGroup(
    groupId: String,
    overrider: MemberId,
    decision: OverrideDecision,
    reason: String,
    config: ActorConfig,
    at: Instant? = null,
): Decision<Recorded> {
    val legs = group(groupId).filter { it.state == EntryState.NEEDS_OVERRIDE }
    if (legs.isEmpty()) {
        return Decision.Refused(Refusal.Invalid("Nothing in $groupId is waiting to be settled."))
    }
    if (decision == OverrideDecision.CORRECTED && legs.size > 1) {
        return Decision.Refused(
            Refusal.Invalid(
                "This is a loan, and its interest and cost follow from the amount. " +
                    "Throw it out and record it again with the right figure.",
            ),
        )
    }

    var book = this
    val settled = mutableListOf<Entry>()
    for (leg in legs) {
        when (val step = book.override(leg.id, overrider, decision, reason, config, at)) {
            is Decision.Refused -> return step
            is Decision.Allowed -> {
                book = step.value.book
                settled += step.value.entries
            }
        }
    }
    return Decision.Allowed(Recorded(book, settled))
}

/** Acts waiting on the third member, grouped the way they will be decided. */
fun LedgerBook.overrideActs(): List<List<Entry>> {
    val stuck = needingOverride()
    val standalone = stuck.filter { it.groupId == null }.map { listOf(it) }
    val grouped = stuck.filter { it.groupId != null }
        .groupBy { it.groupId!! }
        .map { (_, es) -> es.sortedBy { it.seq ?: Long.MAX_VALUE } }
    return (standalone + grouped).sortedBy { it.first().seq ?: Long.MAX_VALUE }
}
