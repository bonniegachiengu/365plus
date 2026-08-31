package online.vyybandasky.plus365.core

import online.vyybandasky.plus365.core.domain.AccountKind
import online.vyybandasky.plus365.core.sms.ParseOutcome
import online.vyybandasky.plus365.core.sms.SmsProvider
import online.vyybandasky.plus365.core.sms.parseSms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The accounts the group actually has, pinned.
 *
 * Confirmed by Bonnie, 30 Aug 2026: M-Pesa/Pochi, the Ziidi investment account
 * behind the Founder's A/C, and the Etica money market fund behind the Keshflo
 * A/C. No M-Shwari — they do not have one and are not opening one.
 */
class TheRealAccountsTest {

    @Test
    fun a_new_ledger_opens_with_the_three_they_have() {
        assertEquals(
            listOf("M-Pesa Pochi", "Ziidi", "Etica MMF"),
            DevSeed.ACCOUNTS.map { it.label },
        )
    }

    @Test
    fun and_no_m_shwari_anywhere_in_it() {
        assertTrue(DevSeed.ACCOUNTS.none { it.kind == AccountKind.MSHWARI })
    }

    /**
     * The constant survives even though the account does not.
     *
     * Ledgers written before this decision name M-Shwari. Deleting the enum
     * value would not tidy those up, it would make them unreadable — so the
     * test that matters is that it still exists while never being offered.
     */
    @Test
    fun m_shwari_is_still_a_kind_that_can_be_read_back() {
        assertEquals("MSHWARI", AccountKind.MSHWARI.name)
    }

    @Test
    fun but_nobody_is_ever_offered_it() {
        assertFalse(AccountKind.MSHWARI in AccountKind.offerable)
        assertTrue(AccountKind.ETICA in AccountKind.offerable)
        assertTrue(AccountKind.ZIIDI in AccountKind.offerable)
        assertTrue(AccountKind.MPESA in AccountKind.offerable)
    }

    /** Both funds grow on their own; the wallet does not. */
    @Test
    fun which_of_them_earn() {
        val earning = DevSeed.ACCOUNTS.filter { it.earnsInterest }.map { it.label }
        assertEquals(listOf("Ziidi", "Etica MMF"), earning)
    }

    /**
     * An Etica message is recognised and refused, not guessed at.
     *
     * The failure this prevents is specific: without Etica in the provider list
     * its message reaches the generic path and gets read with M-Pesa's rules,
     * which would hand back something shaped exactly like evidence.
     */
    @Test
    fun an_etica_message_is_recognised_and_not_read() {
        val outcome = parseSms(
            "ETICA: Your Etica Money Market Fund balance is Ksh 12,345.67 as at 30/08/2026.",
            DevSeed.BONNIE,
        )
        assertIs<ParseOutcome.Unmapped>(outcome)
        assertEquals(SmsProvider.ETICA, outcome.provider)
    }

    /** And M-Shwari stays refused for the same reason, permanently. */
    @Test
    fun an_m_shwari_message_is_still_refused() {
        val outcome = parseSms(
            "M-Shwari: You have deposited Ksh1,000.00 to M-Shwari account. Balance Ksh8,000.00.",
            DevSeed.BONNIE,
        )
        assertIs<ParseOutcome.Unmapped>(outcome)
        assertEquals(SmsProvider.MSHWARI, outcome.provider)
    }

    /** The member-facing names stay the pockets', not the vehicles'. */
    @Test
    fun the_labels_people_read_are_still_the_groups_own_words() {
        assertEquals("Founder's A/C", DevSeed.POCKETS.single { it.id == DevSeed.POOL }.label)
        assertEquals("Keshflo A/C", DevSeed.POCKETS.single { it.id == DevSeed.KESHFLO }.label)
        // The vehicle is noted, not promoted.
        assertTrue(DevSeed.POCKETS.single { it.id == DevSeed.POOL }.blurb.contains("Ziidi"))
        assertTrue(DevSeed.POCKETS.single { it.id == DevSeed.KESHFLO }.blurb.contains("Etica"))
    }
}

/**
 * The build says one thing about itself, everywhere.
 *
 * `INSTALLER_VERSION` was a hand-kept constant with a comment asking somebody to
 * keep it in step with [BuildInfo.NAME]. It read 1.41.0 while the app shipped
 * 1.50.0 — nine releases of drift in the one value whose entire job is to say
 * which build this is. It is derived now, and this is what stops it being typed
 * again.
 */
class BuildStampTest {

    @Test
    fun the_installer_version_follows_the_name() {
        assertEquals(
            "1." + BuildInfo.NAME.substringBefore('-').substringAfter('.'),
            BuildInfo.INSTALLER_VERSION,
        )
    }

    @Test
    fun and_is_a_shape_windows_will_accept() {
        val parts = BuildInfo.INSTALLER_VERSION.split(".")
        assertEquals(3, parts.size)
        assertTrue(parts.all { it.toIntOrNull() != null })
        // MSI wants a major of at least one.
        assertTrue(parts[0].toInt() >= 1)
    }

    @Test
    fun the_header_names_the_build_and_the_commit() {
        val label = BuildInfo.label()
        assertTrue(label.contains(BuildInfo.NAME), "the header must name the build")
        assertTrue(label.contains(BuildInfo.COMMIT), "the header must name the commit")
    }

    /**
     * The commit is real, not a placeholder.
     *
     * `nogit` is what the generator writes when it cannot ask git. A build that
     * shipped saying that would look stamped and answer nothing.
     */
    @Test
    fun the_commit_came_from_git() {
        assertTrue(BuildInfo.COMMIT.isNotBlank())
        assertTrue(BuildInfo.COMMIT != "nogit", "the build stamp never reached git")
        assertTrue(BuildInfo.COMMIT.substringBefore('-').length in 7..12)
    }
}
