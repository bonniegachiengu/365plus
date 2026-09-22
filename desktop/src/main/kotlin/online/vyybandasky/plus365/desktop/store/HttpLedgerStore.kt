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
        val connection =
            URI("${baseUrl.trimEnd('/')}/sync").toURL().openConnection()
                as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 2_000
            connection.readTimeout = 2_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-Pairing-Code", pairingCode)

            connection.outputStream.use { it.write(text.toByteArray()) }

            val code = connection.responseCode
            if (code !in 200..299) {
                val detail =
                    (connection.errorStream ?: connection.inputStream)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()

                throw IllegalStateException(
                    "server sync failed with HTTP $code${if (detail.isBlank()) "" else ": $detail"}",
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    override fun clear() {
        throw UnsupportedOperationException(
            "HttpLedgerStore.clear is not implemented yet",
        )
    }
}
