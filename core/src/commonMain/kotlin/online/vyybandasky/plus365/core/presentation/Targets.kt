package online.vyybandasky.plus365.core.presentation

import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.domain.MemberId
import online.vyybandasky.plus365.core.domain.TargetChangeState
import online.vyybandasky.plus365.core.money.formatKes

/**
 * A proposed target change, as a person needs to read it.
 *
 * The two figures side by side rather than one: "8,000 to 9,000" is a decision
 * somebody can make, "9,000" on its own is a number they have to go and check
 * against something else before they can answer.
 */
data class TargetProposalRow(
    val id: String,
    /** Whose target. */
    val memberId: MemberId,
    val memberName: String,
    val from: String,
    val to: String,
    val proposedByName: String,
    /** "Bonnie and Brian have agreed. Waiting on Kang'iri." */
    val standing: String,
    /** Everybody still to answer, by name. Empty once it is settled. */
    val waitingOn: List<String>,
    /** Whether the member reading this is one of them. */
    val yoursToAnswer: Boolean,
    /** Whether the reader proposed it, and so may take it back. */
    val yoursToWithdraw: Boolean,
    val state: TargetChangeState,
    /** Set when somebody refused it. */
    val refusedBy: String? = null,
    val refusedWhy: String? = null,
)

/**
 * Every target proposal still open, plus however many have been settled.
 *
 * Settled ones are included deliberately. A refusal with a reason on it is the
 * most useful thing in this list — it is the record of why somebody's target is
 * still what it was — and a screen that hid it the moment it was decided would
 * throw away the only part worth keeping.
 */
fun LedgerBook.targetProposals(viewer: MemberId): List<TargetProposalRow> {
    val electorate = targetElectorate()
    return targetChanges.map { c ->
        val waiting = electorate.filter { it !in c.approvals }
        val agreed = c.approvals.filter { it in electorate }
        TargetProposalRow(
            id = c.id,
            memberId = c.memberId,
            memberName = displayName(c.memberId),
            from = formatKes(c.previousTargetCents),
            to = formatKes(c.newTargetCents),
            proposedByName = displayName(c.proposedBy),
            standing = standingLine(c.state, agreed.map { displayName(it) }, waiting.map { displayName(it) }),
            waitingOn = waiting.map { displayName(it) },
            yoursToAnswer = c.state == TargetChangeState.PROPOSED && viewer in waiting,
            yoursToWithdraw = c.state == TargetChangeState.PROPOSED && c.proposedBy == viewer,
            state = c.state,
            refusedBy = c.rejectedBy?.let { displayName(it) },
            refusedWhy = c.rejectionReason,
        )
    }.sortedBy { if (it.state == TargetChangeState.PROPOSED) 0 else 1 }
}

/**
 * What is holding this up, in words.
 *
 * Naming who is still to answer rather than printing "2 of 3". A count tells
 * somebody the vote is short; a name tells them who to talk to, and this is a
 * group of three people who see each other.
 */
private fun standingLine(
    state: TargetChangeState,
    agreed: List<String>,
    waiting: List<String>,
): String = when (state) {
    TargetChangeState.AGREED -> "Everybody agreed. This is the target now."
    TargetChangeState.REJECTED -> "Refused, so the target has not moved."
    TargetChangeState.WITHDRAWN -> "Taken back before it was settled."
    TargetChangeState.PROPOSED -> {
        val yes = when (agreed.size) {
            0 -> "Nobody has agreed yet"
            1 -> "${agreed[0]} has agreed"
            else -> agreed.dropLast(1).joinToString(", ") + " and " + agreed.last() + " have agreed"
        }
        val no = when (waiting.size) {
            0 -> ""
            1 -> " Waiting on ${waiting[0]}."
            else -> " Waiting on " + waiting.dropLast(1).joinToString(", ") +
                " and " + waiting.last() + "."
        }
        "$yes.$no Every founder has to agree before a target moves."
    }
}
