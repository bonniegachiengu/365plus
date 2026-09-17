package online.vyybandasky.plus365.core.governance

import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.domain.MemberId

/**
 * Administrative authority is deliberately separate from MemberKind
 * and from financial governance authority.
 *
 * Being an admin does not make somebody a founder and does not grant
 * confirmation, rejection or settlement authority.
 */
data class AdminAuthority(
    val administratorIds: Set<MemberId>,
) {
    fun mayAdminister(actor: MemberId): Boolean =
        actor in administratorIds

    companion object {
        fun none(): AdminAuthority =
            AdminAuthority(emptySet())

        fun of(vararg administrators: MemberId): AdminAuthority =
            AdminAuthority(administrators.toSet())
    }
}

/**
 * Refusals specific to administrative lifecycle operations.
 */
sealed interface AdminRefusal {
    val message: String

    data class NotAuthorised(
        val actor: MemberId,
    ) : AdminRefusal {
        override val message =
            "This actor does not have administrative lifecycle authority."
    }

    data class UnknownMember(
        val memberId: MemberId,
    ) : AdminRefusal {
        override val message =
            "No member $memberId exists in the book."
    }

    data class AlreadyActive(
        val memberId: MemberId,
    ) : AdminRefusal {
        override val message =
            "Member $memberId is already active."
    }

    data class AlreadyInactive(
        val memberId: MemberId,
    ) : AdminRefusal {
        override val message =
            "Member $memberId is already inactive."
    }
}

/**
 * Result of an administrative lifecycle operation.
 */
sealed interface AdminDecision<out T> {
    data class Allowed<T>(
        val value: T,
    ) : AdminDecision<T>

    data class Refused(
        val refusal: AdminRefusal,
    ) : AdminDecision<Nothing>
}

/**
 * Activate an existing member identity.
 *
 * This changes exactly one field:
 *
 *     Member.active
 *
 * Identity, role, contribution target, and all financial history remain intact.
 */
fun LedgerBook.activateMember(
    memberId: MemberId,
    administeredBy: MemberId,
    authority: AdminAuthority,
): AdminDecision<LedgerBook> {
    if (!authority.mayAdminister(administeredBy)) {
        return AdminDecision.Refused(
            AdminRefusal.NotAuthorised(administeredBy),
        )
    }

    val existing = member(memberId)
        ?: return AdminDecision.Refused(
            AdminRefusal.UnknownMember(memberId),
        )

    if (existing.active) {
        return AdminDecision.Refused(
            AdminRefusal.AlreadyActive(memberId),
        )
    }

    return AdminDecision.Allowed(
        copy(
            members = members.map { member ->
                if (member.id == memberId) {
                    member.copy(active = true)
                } else {
                    member
                }
            },
        ),
    )
}

/**
 * Deactivate an existing member identity.
 *
 * Deactivation is administrative state only. It does not delete the identity,
 * touch ledger entries, change stake, alter loans, or rewrite governance.
 */
fun LedgerBook.deactivateMember(
    memberId: MemberId,
    administeredBy: MemberId,
    authority: AdminAuthority,
): AdminDecision<LedgerBook> {
    if (!authority.mayAdminister(administeredBy)) {
        return AdminDecision.Refused(
            AdminRefusal.NotAuthorised(administeredBy),
        )
    }

    val existing = member(memberId)
        ?: return AdminDecision.Refused(
            AdminRefusal.UnknownMember(memberId),
        )

    if (!existing.active) {
        return AdminDecision.Refused(
            AdminRefusal.AlreadyInactive(memberId),
        )
    }

    return AdminDecision.Allowed(
        copy(
            members = members.map { member ->
                if (member.id == memberId) {
                    member.copy(active = false)
                } else {
                    member
                }
            },
        ),
    )
}
