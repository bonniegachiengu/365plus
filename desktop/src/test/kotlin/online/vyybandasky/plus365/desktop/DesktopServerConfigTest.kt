package online.vyybandasky.plus365.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopServerConfigTest {

    @Test
    fun `pairing code is required`() {
        assertNull(
            DesktopServerConfig.fromValues(
                serverUrl = "http://127.0.0.1:8443",
                pairingCode = null,
            ),
        )
        assertNull(
            DesktopServerConfig.fromValues(
                serverUrl = "http://127.0.0.1:8443",
                pairingCode = "   ",
            ),
        )
    }

    @Test
    fun `server url defaults when omitted`() {
        assertEquals(
            DesktopServerConfig(
                serverUrl = DEFAULT_SERVER_URL,
                pairingCode = "ABC123",
            ),
            DesktopServerConfig.fromValues(
                serverUrl = null,
                pairingCode = " ABC123 ",
            ),
        )
    }

    @Test
    fun `configured values are trimmed and preserved`() {
        assertEquals(
            DesktopServerConfig(
                serverUrl = "http://192.168.1.20:8443",
                pairingCode = "ABC123",
            ),
            DesktopServerConfig.fromValues(
                serverUrl = " http://192.168.1.20:8443 ",
                pairingCode = " ABC123 ",
            ),
        )
    }
}
