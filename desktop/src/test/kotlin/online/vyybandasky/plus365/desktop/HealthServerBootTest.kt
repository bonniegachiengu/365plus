package online.vyybandasky.plus365.desktop

import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Stands the embedded server up for real and talks to it over a socket.
 *
 * The unit tests around [healthJson] only prove the string is right. This proves
 * the thing the brief's M0 actually asks for: the desktop app boots an HTTP
 * endpoint a phone could reach.
 */
class HealthServerBootTest {

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private fun get(url: String): Pair<Int, String> {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 2_000
        connection.readTimeout = 2_000
        return try {
            val code = connection.responseCode
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            code to body
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun the_embedded_api_serves_health_over_a_real_socket() {
        // Not 8443: a developer running the desktop app while tests run would
        // otherwise collide with it and fail for the wrong reason.
        val port = freePort()
        val server = startHealthServer(port = port)
        try {
            var last: Throwable? = null
            repeat(50) {
                try {
                    val (code, body) = get("http://127.0.0.1:$port/health")
                    assertEquals(200, code)
                    assertEquals(healthJson(), body)
                    return
                } catch (t: Throwable) {
                    last = t
                    Thread.sleep(100)
                }
            }
            fail("server never answered on port $port: ${last?.message}")
        } finally {
            server.stop(gracePeriodMillis = 100, timeoutMillis = 1_000)
        }
    }
}
