package online.vyybandasky.plus365.desktop.store

import java.net.HttpURLConnection
import java.net.URI
import online.vyybandasky.plus365.core.store.LedgerStore

class HttpLedgerStore(
    private val baseUrl: String,
    private val pairingCode: String,
) : LedgerStore {

    private var sessionToken: String? = null

    private fun connection(path: String): HttpURLConnection =
        URI("${baseUrl.trimEnd('/')}$path").toURL().openConnection() as HttpURLConnection

    fun bootstrapSession(): Boolean =
        runCatching {
            val connection = connection("/session")

            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 2_000
                connection.readTimeout = 2_000
                connection.doOutput = true
                connection.setRequestProperty("X-Pairing-Code", pairingCode)
                connection.outputStream.use { }

                if (connection.responseCode !in 200..299) {
                    false
                } else {
                    val body = connection.inputStream
                        .bufferedReader()
                        .use { it.readText() }

                    val token = Regex("""\"session\"\s*:\s*\"([^\"]+)\"""")
                        .find(body)
                        ?.groupValues
                        ?.getOrNull(1)

                    if (token.isNullOrBlank()) {
                        false
                    } else {
                        sessionToken = token
                        true
                    }
                }
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)

    private fun ensureSession() {
        if (sessionToken == null) bootstrapSession()
    }

    private fun authorise(connection: HttpURLConnection) {
        sessionToken?.let {
            connection.setRequestProperty("X-Session-Token", it)
        } ?: connection.setRequestProperty("X-Pairing-Code", pairingCode)
    }

    override fun read(): String? =
        runCatching {
            ensureSession()
            val connection = connection("/ledger")

            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 2_000
                connection.readTimeout = 2_000
                authorise(connection)

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
        ensureSession()
        val connection = connection("/sync")

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 2_000
            connection.readTimeout = 2_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            authorise(connection)

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
