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
    fun money_formats_from_integer_minor_units() {
        assertEquals("KSh 0.00", formatKes(0))
        assertEquals("KSh 1.05", formatKes(105))
        assertEquals("KSh 5,000.00", formatKes(500_000))
        assertEquals("KSh 1,234,567.89", formatKes(123_456_789))
    }

    @Test
    fun negative_money_keeps_its_sign_in_front_of_the_currency_amount() {
        assertEquals("KSh -1,200.00", formatKes(-120_000))
    }

    @Test
    fun version_is_reported() {
        assertTrue(APP_VERSION.isNotBlank())
    }
}
