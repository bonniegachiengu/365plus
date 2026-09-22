package online.vyybandasky.plus365.desktop.store

import java.net.ServerSocket
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.store.encodeBook
import online.vyybandasky.plus365.server.ServerRuntime

class HttpLedgerStoreTest {

    private fun freePort(): Int =
        ServerSocket(0).use { it.localPort }

    @Test
    fun read_fetches_the_server_ledger() {
        val dataDir = createTempDirectory("365plus-http-store").toFile()
        val port = freePort()
        val runtime = ServerRuntime(
            dataDir = dataDir,
            port = port,
            host = "127.0.0.1",
        )

        runtime.start()

        try {
            var last: Throwable? = null
            var ledger: String? = null

            repeat(50) {
                try {
                    ledger = HttpLedgerStore(
                        baseUrl = "http://127.0.0.1:$port",
                        pairingCode = runtime.pairingCode,
                    ).read()
                    last = null
                    return@repeat
                } catch (t: Throwable) {
                    last = t
                    Thread.sleep(100)
                }
            }

            if (last != null) {
                throw AssertionError(
                    "desktop client never reached /ledger on port $port",
                    last,
                )
            }

            assertEquals(encodeBook(DevSeed.book()), ledger)
        } finally {
            runtime.stop()
            dataDir.deleteRecursively()
        }
    }

    @Test
    fun read_returns_null_when_the_pairing_code_is_wrong() {
        val dataDir = createTempDirectory("365plus-http-auth").toFile()
        val port = freePort()
        val runtime = ServerRuntime(
            dataDir = dataDir,
            port = port,
            host = "127.0.0.1",
        )

        runtime.start()

        try {
            var last: Throwable? = null
            var ledger: String? = null

            repeat(50) {
                try {
                    ledger = HttpLedgerStore(
                        baseUrl = "http://127.0.0.1:$port",
                        pairingCode = "WRONG",
                    ).read()
                    last = null
                    return@repeat
                } catch (t: Throwable) {
                    last = t
                    Thread.sleep(100)
                }
            }

            if (last != null) {
                throw AssertionError(
                    "desktop client never reached /ledger on port $port",
                    last,
                )
            }

            assertNull(ledger)
        } finally {
            runtime.stop()
            dataDir.deleteRecursively()
        }
    }
}
