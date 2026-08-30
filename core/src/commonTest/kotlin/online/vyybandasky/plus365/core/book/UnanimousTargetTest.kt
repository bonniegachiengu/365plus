package online.vyybandasky.plus365.core.book

import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.domain.TargetChangeState
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.governance.Decision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * A target moves only when everybody agrees.
 *
 * Bonnie's ruling. The tests that matter here are the negative ones: that two
 * of three is not enough, and that one refusal ends it. Unanimity is defined by
 * what it refuses, so a suite that only proves the happy path would pass just
 * as well against a majority rule.
 */
class UnanimousTargetTest {

    private val config = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)

    private fun <T> Decision<T>.value(): T = (this as Decision.Allowed).value

    private fun book() = LedgerBook(
        members = DevSeed.MEMBERS,
        accounts = DevSeed.ACCOUNTS,
        pockets = DevSeed.POCKETS,
    )

    private fun targetOf(b: LedgerBook, id: String) = b.member(id)!!.contributionTargetCents

    private fun proposed() = book().proposeTargetChange(
        id = "t1",
        memberId = DevSeed.BRIAN,
        newTargetCents = 900_000L,
        by = DevSeed.BONNIE,
        config = config,
    ).value()

    // ── the rule ────────────────────────────────────────────────────────────

    @Test
    fun proposing_alone_does_not_move_the_target() {
        val b = proposed()
        assertEquals(TargetChangeState.PROPOSED, b.targetChange("t1")!!.state)
        assertEquals(DevSeed.TARGET, targetOf(b, DevSeed.BRIAN))
    }

    @Test
    fun two_of_the_three_founders_is_still_not_enough() {
        // This is the assertion that separates unanimous from majority. Two out
        // of three is a majority, and it must change nothing.
        val b = proposed().approveTargetChange("t1", DevSeed.BRIAN, config).value()
        assertEquals(setOf(DevSeed.BONNIE, DevSeed.BRIAN), b.targetChange("t1")!!.approvals)
        assertEquals(TargetChangeState.PROPOSED, b.targetChange("t1")!!.state)
        assertEquals(DevSeed.TARGET, targetOf(b, DevSeed.BRIAN))
        assertNotEquals(900_000L, targetOf(b, DevSeed.BRIAN))
    }

    @Test
    fun the_last_yes_is_what_moves_it() {
        var b = proposed().approveTargetChange("t1", DevSeed.BRIAN, config).value()
        b = b.approveTargetChange("t1", DevSeed.KANGIRI, config).value()
        assertEquals(TargetChangeState.AGREED, b.targetChange("t1")!!.state)
        assertEquals(900_000L, targetOf(b, DevSeed.BRIAN))
    }

    @Test
    fun one_refusal_ends_it() {
        var b = proposed().approveTargetChange("t1", DevSeed.BRIAN, config).value()
        b = b.rejectTargetChange("t1", DevSeed.KANGIRI, "Not this month.", config).value()
        assertEquals(TargetChangeState.REJECTED, b.targetChange("t1")!!.state)
        assertEquals(DevSeed.TARGET, targetOf(b, DevSeed.BRIAN))
        assertEquals("Not this month.", b.targetChange("t1")!!.rejectionReason)
    }

    @Test
    fun the_member_whose_target_it_is_can_refuse_it_themselves() {
        // The point of unanimity rather than a majority: nobody has a target
        // changed for them by other people agreeing among themselves.
        val b = proposed()
            .rejectTargetChange("t1", DevSeed.BRIAN, "I did not agree to that.", config).value()
        assertEquals(TargetChangeState.REJECTED, b.targetChange("t1")!!.state)
        assertEquals(DevSeed.TARGET, targetOf(b, DevSeed.BRIAN))
    }

    @Test
    fun a_settled_proposal_cannot_be_reopened_by_approving_it_again() {
        var b = proposed().approveTargetChange("t1", DevSeed.BRIAN, config).value()
        b = b.rejectTargetChange("t1", DevSeed.KANGIRI, "No.", config).value()
        assertTrue(b.approveTargetChange("t1", DevSeed.KANGIRI, config) is Decision.Refused)
        assertEquals(DevSeed.TARGET, targetOf(b, DevSeed.BRIAN))
    }

    // ── who counts ──────────────────────────────────────────────────────────

    @Test
    fun a_keshflo_borrower_has_no_vote() {
        val b = proposed()
        assertTrue(b.approveTargetChange("t1", DevSeed.WANJIKU, config) is Decision.Refused)
        // And their absence does not hold the change up.
        var c = b.approveTargetChange("t1", DevSeed.BRIAN, config).value()
        c = c.approveTargetChange("t1", DevSeed.KANGIRI, config).value()
        assertEquals(TargetChangeState.AGREED, c.targetChange("t1")!!.state)
    }

    @Test
    fun a_keshflo_borrower_cannot_propose_one_either() {
        val r = book().proposeTargetChange(
            "t2", DevSeed.BRIAN, 900_000L, DevSeed.WANJIKU, config,
        )
        assertTrue(r is Decision.Refused)
    }

    @Test
    fun a_keshflo_borrower_has_no_target_to_change() {
        val r = book().proposeTargetChange(
            "t3", DevSeed.WANJIKU, 900_000L, DevSeed.BONNIE, config,
        )
        assertTrue(r is Decision.Refused)
    }

    // ── the awkward cases ───────────────────────────────────────────────────

    @Test
    fun nobody_gets_to_agree_twice() {
        // A count of approvals would let one enthusiastic founder carry a vote.
        val b = proposed()
        assertTrue(b.approveTargetChange("t1", DevSeed.BONNIE, config) is Decision.Refused)
    }

    @Test
    fun only_one_change_can_be_waiting_on_a_member_at_a_time() {
        val b = proposed()
        val r = b.proposeTargetChange("t9", DevSeed.BRIAN, 700_000L, DevSeed.KANGIRI, config)
        assertTrue(r is Decision.Refused)
    }

    @Test
    fun but_a_different_member_can_have_one_at_the_same_time() {
        val b = proposed()
        val r = b.proposeTargetChange("t9", DevSeed.KANGIRI, 700_000L, DevSeed.BONNIE, config)
        assertTrue(r is Decision.Allowed)
    }

    @Test
    fun a_refusal_has_to_say_why() {
        val b = proposed()
        assertTrue(b.rejectTargetChange("t1", DevSeed.BRIAN, "   ", config) is Decision.Refused)
    }

    @Test
    fun only_the_proposer_can_withdraw_it() {
        val b = proposed()
        assertTrue(b.withdrawTargetChange("t1", DevSeed.BRIAN, config) is Decision.Refused)
        val w = b.withdrawTargetChange("t1", DevSeed.BONNIE, config).value()
        assertEquals(TargetChangeState.WITHDRAWN, w.targetChange("t1")!!.state)
        assertEquals(DevSeed.TARGET, targetOf(w, DevSeed.BRIAN))
    }

    @Test
    fun proposing_the_target_it_already_has_is_refused() {
        val r = book().proposeTargetChange(
            "t4", DevSeed.BRIAN, DevSeed.TARGET, DevSeed.BONNIE, config,
        )
        assertTrue(r is Decision.Refused)
    }

    @Test
    fun a_negative_target_is_refused() {
        val r = book().proposeTargetChange("t5", DevSeed.BRIAN, -1L, DevSeed.BONNIE, config)
        assertTrue(r is Decision.Refused)
    }

    /**
     * Somebody joining mid-vote has to be asked.
     *
     * The alternative — settling against the electorate as it stood when the
     * proposal was made — would let a promise binding four people be agreed by
     * three, which is the exact thing unanimity is for.
     */
    @Test
    fun a_founder_who_joins_mid_vote_still_has_to_agree() {
        var b = proposed().approveTargetChange("t1", DevSeed.BRIAN, config).value()
        val newcomer = DevSeed.MEMBERS.first { it.id == DevSeed.BONNIE }
            .copy(id = "dee", displayName = "Dee")
        b = b.copy(members = b.members + newcomer)
        // A member who exists in the book but not in the actor config cannot act
        // at all — that gate fires before this one, and it is a separate rule.
        val wider = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE + "dee")
        b = b.approveTargetChange("t1", DevSeed.KANGIRI, wider).value()
        assertEquals(TargetChangeState.PROPOSED, b.targetChange("t1")!!.state)
        assertEquals(DevSeed.TARGET, targetOf(b, DevSeed.BRIAN))

        b = b.approveTargetChange("t1", "dee", wider).value()
        assertEquals(TargetChangeState.AGREED, b.targetChange("t1")!!.state)
        assertEquals(900_000L, targetOf(b, DevSeed.BRIAN))
    }

    /** Nothing about a target touches money. */
    @Test
    fun agreeing_a_target_moves_no_money() {
        var b = proposed().approveTargetChange("t1", DevSeed.BRIAN, config).value()
        val before = b.state().cashAtHandCents
        b = b.approveTargetChange("t1", DevSeed.KANGIRI, config).value()
        assertEquals(TargetChangeState.AGREED, b.targetChange("t1")!!.state)
        assertEquals(before, b.state().cashAtHandCents)
        assertEquals(0, b.entries.size)
        assertTrue(b.state().balances)
    }
}
