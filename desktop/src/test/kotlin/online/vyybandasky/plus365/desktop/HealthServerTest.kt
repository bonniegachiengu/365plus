package online.vyybandasky.plus365.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HealthServerTest {

    @Test
    fun health_payload_is_the_shape_the_tunnel_check_expects() {
        assertEquals(
            """{"status":"ok","service":"365plus","version":"0.1.0"}""",
            healthJson("0.1.0"),
        )
    }

    @Test
    fun the_api_listens_on_loopback_only() {
        // Phones reach the master through the tunnel, never by dialling the
        // laptop directly, so binding the LAN would widen the surface for nothing.
        assertEquals("127.0.0.1", DEFAULT_HOST)
        assertEquals(8443, DEFAULT_PORT)
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
