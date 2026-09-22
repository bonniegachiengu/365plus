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
        body: String? = null,
    ): Pair<Int, String> {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 2_000
        connection.readTimeout = 2_000

        if (pairingCode != null) {
            connection.setRequestProperty("X-Pairing-Code", pairingCode)
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
