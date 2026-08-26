package online.vyybandasky.plus365.core.governance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import online.vyybandasky.plus365.core.domain.Entry
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.domain.EntryType

private const val A = "bonnie"
private const val B = "brian"
private const val C = "kangiri"

private fun pendingEntry(recordedBy: String = A) = Entry(
    id = "e1",
    seq = 1,
    type = EntryType.CONTRIBUTION,
    amountCents = 100_000,
    memberId = recordedBy,
    recordedByMemberId = recordedBy,
    state = EntryState.PENDING,
)

class GovernanceTest {

    private val dev = ActorConfig.dev(owner = A, everyone = setOf(A, B, C))

    @Test
    fun the_recorder_cannot_confirm_their_own_entry() {
        val decision = checkConfirm(pendingEntry(recordedBy = A), A, dev)
        val refused = assertIs<Decision.Refused>(decision)
        assertIs<Refusal.SelfConfirmation>(refused.refusal)
    }

    @Test
    fun anyone_else_can() {
        assertIs<Decision.Allowed<Unit>>(checkConfirm(pendingEntry(recordedBy = A), B, dev))
        assertIs<Decision.Allowed<Unit>>(checkConfirm(pendingEntry(recordedBy = A), C, dev))
    }

    @Test
    fun dev_mode_does_not_switch_the_rule_off_it_only_widens_who_may_act() {
        // Bonnie may act as anyone here — and still cannot confirm his own entry.
        val ownEntry = pendingEntry(recordedBy = A)
        assertIs<Decision.Refused>(checkConfirm(ownEntry, A, dev))
        // But he may confirm one Brian recorded, standing in as himself.
        assertIs<Decision.Allowed<Unit>>(checkConfirm(pendingEntry(recordedBy = B), A, dev))
    }

    @Test
    fun production_confines_a_device_to_one_identity() {
        val prod = ActorConfig.production(A)
        assertTrue(prod.mayAct(A))
        assertFalse(prod.mayAct(B))
        val refused = assertIs<Decision.Refused>(checkConfirm(pendingEntry(recordedBy = B), C, prod))
        assertIs<Refusal.NotAuthorised>(refused.refusal)
    }

    @Test
    fun a_production_config_cannot_be_built_with_extra_identities() {
        assertFailsWith<IllegalArgumentException> {
            ActorConfig(Mode.PRODUCTION, deviceOwner = A, mayActAs = setOf(A, B))
        }
    }

    @Test
    fun a_config_must_at_least_let_the_owner_act_as_themselves() {
        assertFailsWith<IllegalArgumentException> {
            ActorConfig(Mode.DEV, deviceOwner = A, mayActAs = setOf(B, C))
        }
    }

    @Test
    fun only_a_pending_entry_can_be_confirmed() {
        val already = pendingEntry(recordedBy = A).copy(state = EntryState.CONFIRMED)
        val refused = assertIs<Decision.Refused>(checkConfirm(already, B, dev))
        assertIs<Refusal.NotPending>(refused.refusal)
    }

    @Test
    fun eligible_confirmers_excludes_the_recorder_and_nobody_else() {
        val eligible = eligibleConfirmers(pendingEntry(recordedBy = B), listOf(A, B, C), dev)
        assertEquals(listOf(A, C), eligible)
    }

    @Test
    fun eligible_confirmers_is_empty_on_a_single_member_production_device() {
        // A member alone on a production device can record but never self-confirm,
        // so nothing they record can be cleared without a second person.
        val prod = ActorConfig.production(A)
        assertEquals(emptyList(), eligibleConfirmers(pendingEntry(recordedBy = A), listOf(A, B, C), prod))
    }

    @Test
    fun the_rule_holds_for_every_ordered_pair_of_members() {
        val all = listOf(A, B, C)
        for (recorder in all) {
            for (confirmer in all) {
                val allowed = checkConfirm(pendingEntry(recordedBy = recorder), confirmer, dev)
                assertEquals(
                    recorder != confirmer,
                    allowed is Decision.Allowed,
                    "recorder=$recorder confirmer=$confirmer",
                )
            }
        }
    }
}
