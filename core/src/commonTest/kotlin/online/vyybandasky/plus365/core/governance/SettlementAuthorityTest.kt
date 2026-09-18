package online.vyybandasky.plus365.core.governance

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import online.vyybandasky.plus365.core.domain.EntryType

class SettlementAuthorityTest {

    @Test
    fun an_explicitly_authorised_actor_may_settle_the_entry_type() {
        val authority = SettlementAuthority.of(
            EntryType.CONTRIBUTION to setOf("bonnie"),
        )

        assertTrue(authority.maySettle(EntryType.CONTRIBUTION, "bonnie"))
    }

    @Test
    fun an_actor_not_explicitly_authorised_cannot_settle_the_entry_type() {
        val authority = SettlementAuthority.of(
            EntryType.CONTRIBUTION to setOf("bonnie"),
        )

        assertFalse(authority.maySettle(EntryType.CONTRIBUTION, "brian"))
    }

    @Test
    fun authority_for_one_entry_type_does_not_grant_authority_for_another() {
        val authority = SettlementAuthority.of(
            EntryType.CONTRIBUTION to setOf("bonnie"),
        )

        assertFalse(authority.maySettle(EntryType.PAYOUT, "bonnie"))
    }

    @Test
    fun no_authority_grants_nobody_settlement_permission() {
        assertFalse(
            SettlementAuthority.none()
                .maySettle(EntryType.CONTRIBUTION, "bonnie"),
        )
    }
}
