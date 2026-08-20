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


    @Test
    fun version_is_reported() {
        assertTrue(APP_VERSION.isNotBlank())
    }
}
