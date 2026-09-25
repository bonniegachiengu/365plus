package online.vyybandasky.plus365.server

import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.store.encodeBook

class ServerSyncTest {

    private fun freePort(): Int =
        ServerSocket(0).use { it.localPort }

    private fun request(
        method: String,
        url: String,
        pairingCode: String? = null,
        sessionToken: String? = null,
        body: String? = null,
    ): Pair<Int, String> {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 2_000
        connection.readTimeout = 2_000

        if (pairingCode != null) {
            connection.setRequestProperty("X-Pairing-Code", pairingCode)
        }
        if (sessionToken != null) {
            connection.setRequestProperty("X-Session-Token", sessionToken)
        }

        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray()) }
        }

        return try {
            val code = connection.responseCode
            val stream =
                if (code >= 400) connection.errorStream else connection.inputStream
            val response = stream?.bufferedReader()?.use { it.readText() } ?: ""
            code to response
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun sync_round_trip_reaches_persistent_server_ledger() {
        val dataDir = createTempDirectory("365plus-server-sync").toFile()
        val port = freePort()
        val runtime = ServerRuntime(
            dataDir = dataDir,
            port = port,
            host = "127.0.0.1",
        )

        runtime.start()

        try {
            var last: Throwable? = null

            repeat(50) {
                try {
                    val (code, body) = request(
                        method = "GET",
                        url = "http://127.0.0.1:$port/ledger",
                        pairingCode = runtime.pairingCode,
                    )
                    assertEquals(200, code)
                    assertTrue(body.startsWith("{"))
                    last = null
                    return@repeat
                } catch (t: Throwable) {
                    last = t
                    Thread.sleep(100)
                }
            }

            if (last != null) {
                throw AssertionError(
                    "server never exposed /ledger on port $port",
                    last,
                )
            }

            val initialBook = DevSeed.book()
            val (syncCode, syncBody) = request(
                method = "POST",
                url = "http://127.0.0.1:$port/sync",
                pairingCode = runtime.pairingCode,
                body = encodeBook(initialBook),
            )

            assertEquals(200, syncCode)
            assertTrue(syncBody.contains("\"ok\":true"))
            assertTrue(syncBody.contains("\"ledger\":"))

            val (ledgerCode, ledgerBody) = request(
                method = "GET",
                url = "http://127.0.0.1:$port/ledger",
                pairingCode = runtime.pairingCode,
            )

            assertEquals(200, ledgerCode)
            assertEquals(encodeBook(initialBook), ledgerBody)
        } finally {
            runtime.stop()
        }

        assertTrue(
            runtime.ledgerFile().exists(),
            "server did not persist ledger.json",
        )
    }

    @Test
    fun session_bootstrap_issues_a_device_token_with_the_pairing_code() {
        val dataDir = createTempDirectory("365plus-server-session").toFile()
        val runtime = ServerRuntime(
            dataDir = dataDir,
            port = freePort(),
            host = "127.0.0.1",
        )

        runtime.start()

        try {
            var last: Throwable? = null
            var responseCode = 0
            var responseBody = ""

            repeat(50) {
                try {
                    val response = request(
                        method = "POST",
                        url = "http://127.0.0.1:${runtime.port}/session",
                        pairingCode = runtime.pairingCode,
                    )
                    responseCode = response.first
                    responseBody = response.second
                    last = null
                    return@repeat
                } catch (t: Throwable) {
                    last = t
                    Thread.sleep(100)
                }
            }

            if (last != null) {
                throw AssertionError(
                    "server never answered /session",
                    last,
                )
            }

            assertEquals(200, responseCode)
            assertTrue(responseBody.startsWith("""{"ok":true,"session":""""))
            assertTrue(responseBody.endsWith(""""}"""))

            val token = responseBody
                .removePrefix("""{"ok":true,"session":"""")
                .removeSuffix(""""}""")

            assertEquals(43, token.length)
            assertTrue(token.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        } finally {
            runtime.stop()
        }
    }

    @Test
    fun session_bootstrap_requires_the_pairing_code() {
        val dataDir = createTempDirectory("365plus-server-session-auth").toFile()
        val runtime = ServerRuntime(
            dataDir = dataDir,
            port = freePort(),
            host = "127.0.0.1",
        )

        runtime.start()

        try {
            var last: Throwable? = null

            repeat(50) {
                try {
                    val (code, body) = request(
                        method = "POST",
                        url = "http://127.0.0.1:${runtime.port}/session",
                        pairingCode = "WRONG1",
                    )
                    assertEquals(401, code)
                    assertTrue(body.contains("pairing code"))
                    last = null
                    return@repeat
                } catch (t: Throwable) {
                    last = t
                    Thread.sleep(100)
                }
            }

            if (last != null) {
                throw AssertionError(
                    "server never answered unauthorised /session",
                    last,
                )
            }
        } finally {
            runtime.stop()
        }
    }
    @Test
    fun session_token_authorises_ledger_without_the_pairing_code() {
        val dataDir = createTempDirectory("365plus-server-session-sync").toFile()
        val runtime = ServerRuntime(
            dataDir = dataDir,
            port = freePort(),
            host = "127.0.0.1",
        )

        runtime.start()

        try {
            val (sessionCode, sessionBody) = request(
                method = "POST",
                url = "http://127.0.0.1:${runtime.port}/session",
                pairingCode = runtime.pairingCode,
            )

            assertEquals(200, sessionCode)

            val token = sessionBody
                .removePrefix("""{"ok":true,"session":"""")
                .removeSuffix(""""}""")

            assertEquals(43, token.length)

            val (ledgerCode, ledgerBody) = request(
                method = "GET",
                url = "http://127.0.0.1:${runtime.port}/ledger",
                sessionToken = token,
            )

            assertEquals(200, ledgerCode)
            assertTrue(ledgerBody.startsWith("{"))
        } finally {
            runtime.stop()
        }
    }


    @Test
    fun sync_requires_the_pairing_code() {
        val dataDir = createTempDirectory("365plus-server-auth").toFile()
        val runtime = ServerRuntime(
            dataDir = dataDir,
            port = freePort(),
            host = "127.0.0.1",
        )

        runtime.start()

        try {
            var last: Throwable? = null

            repeat(50) {
                try {
                    val (code, body) = request(
                        method = "GET",
                        url = "http://127.0.0.1:${runtime.port}/ledger",
                    )
                    assertEquals(401, code)
                    assertTrue(body.contains("pairing code"))
                    last = null
                    return@repeat
                } catch (t: Throwable) {
                    last = t
                    Thread.sleep(100)
                }
            }

            if (last != null) {
                throw AssertionError(
                    "server never answered unauthorised /ledger",
                    last,
                )
            }
        } finally {
            runtime.stop()
        }
    }
}
