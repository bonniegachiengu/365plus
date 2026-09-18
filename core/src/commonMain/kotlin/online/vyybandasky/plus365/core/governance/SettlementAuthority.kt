package online.vyybandasky.plus365.core.governance

import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.domain.MemberId

/**
 * Explicit authority to settle financial entries.
 *
 * Settlement authority is separate from device identity, administrative
 * authority, confirmation authority, and general Founder status.
 *
 * The mapping is intentionally explicit: being involved in a transaction or
 * holding a general role does not by itself create settlement authority.
 */
data class SettlementAuthority(
    val authorizedByEntryType: Map<EntryType, Set<MemberId>>,
) {
    fun maySettle(entryType: EntryType, actor: MemberId): Boolean =
        actor in authorizedByEntryType[entryType].orEmpty()

    companion object {
        fun none(): SettlementAuthority =
            SettlementAuthority(emptyMap())

        fun of(
            vararg authorizations: Pair<EntryType, Set<MemberId>>,
        ): SettlementAuthority =
            SettlementAuthority(authorizations.toMap())
    }
}
