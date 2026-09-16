package online.vyybandasky.plus365.core.governance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.domain.MemberKind

private val ADMIN = AdminAuthority.of(DevSeed.BONNIE)
private val NO_ADMIN = AdminAuthority.none()

private fun freshBook(): LedgerBook =
    LedgerBook(
        members = DevSeed.MEMBERS,
    )

class AdminLifecycleTest {

    @Test
    fun authorised_admin_can_deactivate_existing_member() {
        val before = freshBook()
        val memberBefore = before.member(DevSeed.BRIAN)!!

        val result =
            before.deactivateMember(
                memberId = DevSeed.BRIAN,
                administeredBy = DevSeed.BONNIE,
                authority = ADMIN,
            )

        val after = assertIs<AdminDecision.Allowed<LedgerBook>>(result).value
        val memberAfter = after.member(DevSeed.BRIAN)!!

        assertEquals(false, memberAfter.active)
        assertEquals(memberBefore.id, memberAfter.id)
        assertEquals(memberBefore.displayName, memberAfter.displayName)
        assertEquals(memberBefore.phoneE164, memberAfter.phoneE164)
        assertEquals(memberBefore.kind, memberAfter.kind)
        assertEquals(
            memberBefore.contributionTargetCents,
            memberAfter.contributionTargetCents,
        )
    }

    @Test
    fun authorised_admin_can_reactivate_existing_member() {
        val before =
            freshBook()
                .deactivateMember(
                    DevSeed.BRIAN,
                    DevSeed.BONNIE,
                    ADMIN,
                )
                .let {
                    assertIs<AdminDecision.Allowed<LedgerBook>>(it).value
                }

        val result =
            before.activateMember(
                memberId = DevSeed.BRIAN,
                administeredBy = DevSeed.BONNIE,
                authority = ADMIN,
            )

        val after = assertIs<AdminDecision.Allowed<LedgerBook>>(result).value

        assertEquals(true, after.member(DevSeed.BRIAN)!!.active)
    }

    @Test
    fun non_admin_cannot_change_member_lifecycle() {
        val before = freshBook()

        val result =
            before.deactivateMember(
                memberId = DevSeed.BRIAN,
                administeredBy = DevSeed.BRIAN,
                authority = NO_ADMIN,
            )

        val refused = assertIs<AdminDecision.Refused>(result)

        assertIs<AdminRefusal.NotAuthorised>(refused.refusal)
        assertEquals(true, before.member(DevSeed.BRIAN)!!.active)
    }

    @Test
    fun unknown_member_is_refused() {
        val result =
            freshBook().deactivateMember(
                memberId = "does-not-exist",
                administeredBy = DevSeed.BONNIE,
                authority = ADMIN,
            )

        val refused = assertIs<AdminDecision.Refused>(result)

        assertIs<AdminRefusal.UnknownMember>(refused.refusal)
    }

    @Test
    fun admin_status_does_not_change_member_kind() {
        val before = freshBook()

        val beforeKind = before.member(DevSeed.BRIAN)!!.kind

        val after =
            assertIs<AdminDecision.Allowed<LedgerBook>>(
                before.deactivateMember(
                    DevSeed.BRIAN,
                    DevSeed.BONNIE,
                    ADMIN,
                ),
            ).value

        assertEquals(
            beforeKind,
            after.member(DevSeed.BRIAN)!!.kind,
        )
    }

    @Test
    fun lifecycle_change_does_not_change_financial_history() {
        val before = freshBook()

        val after =
            assertIs<AdminDecision.Allowed<LedgerBook>>(
                before.deactivateMember(
                    DevSeed.BRIAN,
                    DevSeed.BONNIE,
                    ADMIN,
                ),
            ).value

        assertEquals(before.entries, after.entries)
        assertEquals(before.loans, after.loans)
        assertEquals(before.accounts, after.accounts)
        assertEquals(before.pockets, after.pockets)
        assertEquals(before.targetChanges, after.targetChanges)
        assertEquals(before.nextSeq, after.nextSeq)
    }

    @Test
    fun activation_changes_only_active_for_the_target_member() {
        val before =
            freshBook()
                .deactivateMember(
                    DevSeed.BRIAN,
                    DevSeed.BONNIE,
                    ADMIN,
                )
                .let {
                    assertIs<AdminDecision.Allowed<LedgerBook>>(it).value
                }

        val after =
            assertIs<AdminDecision.Allowed<LedgerBook>>(
                before.activateMember(
                    DevSeed.BRIAN,
                    DevSeed.BONNIE,
                    ADMIN,
                ),
            ).value

        for (beforeMember in before.members) {
            val afterMember = after.member(beforeMember.id)!!

            if (beforeMember.id == DevSeed.BRIAN) {
                assertNotEquals(beforeMember.active, afterMember.active)
                assertEquals(true, afterMember.active)
            } else {
                assertEquals(beforeMember, afterMember)
            }
        }
    }

    @Test
    fun cannot_deactivate_an_already_inactive_member() {
        val before =
            freshBook()
                .deactivateMember(
                    DevSeed.BRIAN,
                    DevSeed.BONNIE,
                    ADMIN,
                )
                .let {
                    assertIs<AdminDecision.Allowed<LedgerBook>>(it).value
                }

        val result =
            before.deactivateMember(
                DevSeed.BRIAN,
                DevSeed.BONNIE,
                ADMIN,
            )

        assertIs<AdminRefusal.AlreadyInactive>(
            assertIs<AdminDecision.Refused>(result).refusal,
        )
    }

    @Test
    fun cannot_activate_an_already_active_member() {
        val result =
            freshBook().activateMember(
                DevSeed.BRIAN,
                DevSeed.BONNIE,
                ADMIN,
            )

        assertIs<AdminRefusal.AlreadyActive>(
            assertIs<AdminDecision.Refused>(result).refusal,
        )
    }

    @Test
    fun beneficiary_remains_beneficiary_after_lifecycle_change() {
        val beneficiary =
            freshBook().members.first {
                it.kind == MemberKind.KESHFLO_BENEFICIARY
            }

        val after =
            assertIs<AdminDecision.Allowed<LedgerBook>>(
                freshBook().deactivateMember(
                    beneficiary.id,
                    DevSeed.BONNIE,
                    ADMIN,
                ),
            ).value

        assertEquals(
            MemberKind.KESHFLO_BENEFICIARY,
            after.member(beneficiary.id)!!.kind,
        )
    }
}
