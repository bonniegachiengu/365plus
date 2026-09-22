package online.vyybandasky.plus365.desktop.store

import java.net.HttpURLConnection
import java.net.URI
import online.vyybandasky.plus365.core.store.LedgerStore

class HttpLedgerStore(
    private val baseUrl: String,
    private val pairingCode: String,
) : LedgerStore {

    override fun read(): String? =
        runCatching {
            val connection =
                URI("${baseUrl.trimEnd('/')}/ledger").toURL().openConnection()
                    as HttpURLConnection

            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 2_000
                connection.readTimeout = 2_000
                connection.setRequestProperty("X-Pairing-Code", pairingCode)

                if (connection.responseCode !in 200..299) {
                    null
                } else {
                    connection.inputStream
                        .bufferedReader()
                        .use { it.readText() }
                }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()

    override fun write(text: String) {
        throw UnsupportedOperationException(
            "HttpLedgerStore.write is not implemented yet; use /sync for writes",
        )
    }

    override fun clear() {
        throw UnsupportedOperationException(
            "HttpLedgerStore.clear is not implemented yet",
        )
    }
}
