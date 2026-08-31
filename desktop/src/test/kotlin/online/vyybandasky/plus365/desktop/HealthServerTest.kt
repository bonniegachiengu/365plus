package online.vyybandasky.plus365.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HealthServerTest {

    @Test
    fun health_payload_carries_the_version_and_the_commit() {
        // The commit joined it so "am I running the current code?" is answerable
        // with curl, from another machine, without reading a screen.
        assertEquals(
            """{"status":"ok","service":"365plus","version":"0.1.0","commit":"abc1234"}""",
            healthJson("0.1.0", "abc1234"),
        )
    }

    /**
     * This asserted loopback-only until 31 Aug 2026, and it was right to fail
     * when that changed.
     *
     * The old reasoning was that phones would arrive through a tunnel. There is
     * no tunnel and there are three founders in one room, so the LAN is the
     * transport and the bind had to widen. The test is kept rather than deleted
     * because the property it was really protecting still matters — it just is
     * not the bind address any more.
     */
    @Test
    fun the_api_listens_on_the_lan_because_phones_dial_it_directly() {
        assertEquals("0.0.0.0", DEFAULT_HOST)
        assertEquals(8443, DEFAULT_PORT)
    }

    /**
     * What replaced loopback as the thing keeping the ledger private.
     *
     * Binding the LAN means anybody on the WiFi can reach the port. The pairing
     * code is now the only thing between them and the group's money, so the
     * property worth asserting is that it is long enough to not be guessed and
     * drawn from an alphabet without the characters people misread.
     */
    @Test
    fun the_pairing_code_is_the_thing_protecting_it_now() {
        val dir = kotlin.io.path.createTempDirectory("pairing").toFile()
        val code = PairingCode.loadOrCreate(dir)
        assertEquals(6, code.length)
        // No O/0 or I/1: a code read aloud across a room has to survive being
        // heard, and one read off a screen has to survive being typed.
        assertTrue(code.none { it in "O0I1" })
        // Stable. A code that changed on restart would unpair every phone.
        assertEquals(code, PairingCode.loadOrCreate(dir))
    }


    // `version_is_reported` used to live here, asserting APP_VERSION.isNotBlank()
    // on a compile-time constant — a test that could not fail, guarding a value
    // that was wrong for thirty releases. HealthVersionTest below asks the
    // question that matters instead.
}

/**
 * The endpoint must not lie about which build it is.
 *
 * `APP_VERSION` was a hand-typed "0.1.0" and stayed that way through thirty
 * releases. Nobody noticed because nobody reads it — which is precisely the
 * point, since the thing that will read it is a phone deciding whether it can
 * talk to this master.
 */
class HealthVersionTest {

    @Test
    fun the_health_endpoint_reports_the_build_it_is_running() {
        assertEquals(
            online.vyybandasky.plus365.core.BuildInfo.NAME,
            APP_VERSION,
            "the endpoint is reporting a version this build is not",
        )
        assertTrue(
            APP_VERSION in healthJson(),
            "the payload does not contain the version it claims to",
        )
    }

    @Test
    fun it_is_not_the_hand_typed_one_it_used_to_be() {
        assertTrue(APP_VERSION != "0.1.0", "the stale constant is back")
    }
}
