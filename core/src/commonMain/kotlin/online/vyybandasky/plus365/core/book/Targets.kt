package online.vyybandasky.plus365.core.book

import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.domain.MemberId
import online.vyybandasky.plus365.core.domain.TargetChange
import online.vyybandasky.plus365.core.domain.TargetChangeState
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.governance.Decision
import online.vyybandasky.plus365.core.governance.Refusal
import online.vyybandasky.plus365.core.governance.checkRecord

/**
 * Changing what somebody agreed to save.
 *
 * Everything else in this ledger is settled by two people, because everything
 * else is a question about whether money moved — and money either moved or it
 * did not, so a second pair of eyes is enough to establish it.
 *
 * A contribution target is not that kind of fact. It is a promise the group made
 * to each other, and no two of them can quietly rewrite what a third agreed to.
 * Bonnie's ruling: unanimous, from everybody the promise binds.
 *
 * The practical consequence, which is the point rather than a side effect: any
 * one person can stop it, including the member whose target it is. That is what
 * separates unanimity from a majority, and it is why this could not be built by
 * reusing `confirm`.
 */
fun LedgerBook.proposeTargetChange(
    id: String,
    memberId: MemberId,
    newTargetCents: Long,
    by: MemberId,
    config: ActorConfig,
    at: Instant? = null,
): Decision<LedgerBook> {
    when (val gate = checkRecord(by, config)) {
        is Decision.Refused -> return gate
        is Decision.Allowed -> Unit
    }
    if (by !in targetElectorate()) return Decision.Refused(Refusal.NotAMember(by))

    val subject = member(memberId)
        ?: return Decision.Refused(Refusal.Invalid("There is no member $memberId."))
    if (memberId !in targetElectorate()) {
        return Decision.Refused(
            Refusal.Invalid("${subject.displayName} does not have a contribution target."),
        )
    }
    if (newTargetCents < 0L) {
        return Decision.Refused(Refusal.Invalid("A target cannot be less than nothing."))
    }
    if (newTargetCents == subject.contributionTargetCents) {
        return Decision.Refused(
            Refusal.Invalid("That is already the target for ${subject.displayName}."),
        )
    }
    if (targetChanges.any { it.memberId == memberId && it.state == TargetChangeState.PROPOSED }) {
        // Two live proposals for one person are two answers to one question, and
        // whichever settled second would silently overwrite the first.
        return Decision.Refused(
            Refusal.Invalid("A change is already waiting on the target for ${subject.displayName}."),
        )
    }
    if (targetChanges.any { it.id == id }) {
        return Decision.Refused(Refusal.Invalid("That proposal already exists."))
    }

    val proposal = TargetChange(
        id = id,
        memberId = memberId,
        newTargetCents = newTargetCents,
        previousTargetCents = subject.contributionTargetCents,
        proposedBy = by,
        proposedAt = at,
        approvals = setOf(by),
    )
    // Settled immediately in the degenerate case where the proposer is the whole
    // electorate. Unanimity among one person is that person.
    return Decision.Allowed(copy(targetChanges = targetChanges + proposal).settleIfUnanimous(id))
}

/** Say yes. The change lands the moment the last person does. */
fun LedgerBook.approveTargetChange(
    id: String,
    by: MemberId,
    config: ActorConfig,
): Decision<LedgerBook> {
    when (val gate = checkRecord(by, config)) {
        is Decision.Refused -> return gate
        is Decision.Allowed -> Unit
    }
    val proposal = targetChange(id)
        ?: return Decision.Refused(Refusal.Invalid("There is no proposal $id."))
    if (proposal.state != TargetChangeState.PROPOSED) {
        return Decision.Refused(Refusal.Invalid("That was already settled."))
    }
    if (by !in targetElectorate()) return Decision.Refused(Refusal.NotAMember(by))
    if (by in proposal.approvals) {
        return Decision.Refused(Refusal.Invalid("You have already agreed to this."))
    }

    val updated = targetChanges.map {
        if (it.id == id) it.copy(approvals = it.approvals + by) else it
    }
    return Decision.Allowed(copy(targetChanges = updated).settleIfUnanimous(id))
}

/**
 * Say no, and it is over.
 *
 * A rejection needs no second signature. Under unanimity one refusal is already
 * decisive, so asking somebody to confirm it would be theatre — the answer
 * cannot change. The reason is required for the same purpose it is required on
 * an override: a no with nothing behind it is an unexplained decision about
 * somebody else's plans.
 */
fun LedgerBook.rejectTargetChange(
    id: String,
    by: MemberId,
    reason: String,
    config: ActorConfig,
    at: Instant? = null,
): Decision<LedgerBook> {
    when (val gate = checkRecord(by, config)) {
        is Decision.Refused -> return gate
        is Decision.Allowed -> Unit
    }
    val proposal = targetChange(id)
        ?: return Decision.Refused(Refusal.Invalid("There is no proposal $id."))
    if (proposal.state != TargetChangeState.PROPOSED) {
        return Decision.Refused(Refusal.Invalid("That was already settled."))
    }
    if (by !in targetElectorate()) return Decision.Refused(Refusal.NotAMember(by))
    if (reason.isBlank()) {
        return Decision.Refused(Refusal.Invalid("Say why. A bare no explains nothing."))
    }

    return Decision.Allowed(
        copy(
            targetChanges = targetChanges.map {
                if (it.id == id) {
                    it.copy(
                        state = TargetChangeState.REJECTED,
                        rejectedBy = by,
                        rejectedAt = at,
                        rejectionReason = reason.trim(),
                    )
                } else {
                    it
                }
            },
        ),
    )
}

/** The proposer taking it back. Only the proposer, and only before it settles. */
fun LedgerBook.withdrawTargetChange(
    id: String,
    by: MemberId,
    config: ActorConfig,
): Decision<LedgerBook> {
    when (val gate = checkRecord(by, config)) {
        is Decision.Refused -> return gate
        is Decision.Allowed -> Unit
    }
    val proposal = targetChange(id)
        ?: return Decision.Refused(Refusal.Invalid("There is no proposal $id."))
    if (proposal.state != TargetChangeState.PROPOSED) {
        return Decision.Refused(Refusal.Invalid("That was already settled."))
    }
    if (proposal.proposedBy != by) {
        return Decision.Refused(
            Refusal.Invalid("Only the member who proposed this can take it back."),
        )
    }
    return Decision.Allowed(
        copy(
            targetChanges = targetChanges.map {
                if (it.id == id) it.copy(state = TargetChangeState.WITHDRAWN) else it
            },
        ),
    )
}

/**
 * Apply the change if, and only if, everybody has said yes.
 *
 * `containsAll` against the electorate rather than set equality, for two
 * reasons that pull in opposite directions and are both wanted. An approval
 * left behind by somebody who has since gone inactive cannot block a change
 * forever. And a member who joins *after* a proposal is made still has to
 * agree, because they are in the electorate now and their name is not yet in
 * the set — a promise the group makes together cannot be settled by a group
 * that no longer exists.
 */
private fun LedgerBook.settleIfUnanimous(id: String): LedgerBook {
    val proposal = targetChange(id) ?: return this
    if (proposal.state != TargetChangeState.PROPOSED) return this
    val electorate = targetElectorate()
    if (electorate.isEmpty() || !proposal.approvals.containsAll(electorate)) return this

    return copy(
        members = members.map {
            if (it.id == proposal.memberId) {
                it.copy(contributionTargetCents = proposal.newTargetCents)
            } else {
                it
            }
        },
        targetChanges = targetChanges.map {
            if (it.id == id) it.copy(state = TargetChangeState.AGREED) else it
        },
    )
}
