package online.vyybandasky.plus365.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.store.decodeBook
import online.vyybandasky.plus365.core.store.encodeBook
import java.net.HttpURLConnection
import java.net.URL

/**
 * The phone's side of sync.
 *
 * Deliberately thin. The phone sends what it has and takes back what the laptop
 * makes of it — it does not merge, does not resolve anything, and has no opinion
 * about whose version of an entry is right. That is the whole point of a star:
 * one place decides, and it is not here.
 *
 * `HttpURLConnection` rather than a client library. One POST and one GET does
 * not justify a dependency, and the ledger is small enough that streaming it as
 * a string is the simplest thing that is also correct.
 */
sealed interface SyncOutcome {
    /** The laptop merged and sent back the authoritative book. */
    data class Synced(val book: LedgerBook, val summary: String) : SyncOutcome

    /** Reached the laptop, and it said no. */
    data class Refused(val why: String) : SyncOutcome

    /** Never reached it. */
    data class Unreachable(val why: String) : SyncOutcome
}

class SyncClient(
    private val baseUrl: String,
    private val pairingCode: String,
) {
    /**
     * Push what this phone has, take back the merged ledger.
     *
     * One round trip does both directions. A separate pull would leave a window
     * where the phone had pushed and not yet received, and a phone in that state
     * cannot tell whether it is behind or the laptop is.
     */
    suspend fun sync(book: LedgerBook): SyncOutcome = withContext(Dispatchers.IO) {
        val url = runCatching { URL("${baseUrl.trimEnd('/')}/sync") }.getOrNull()
            ?: return@withContext SyncOutcome.Unreachable("That does not look like an address.")

        val conn = runCatching { url.openConnection() as HttpURLConnection }.getOrElse {
            return@withContext SyncOutcome.Unreachable(it.plainly())
        }
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 8_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-Pairing-Code", pairingCode)
            conn.outputStream.use { it.write(encodeBook(book).toByteArray()) }

            val code = conn.responseCode
            if (code == 401) {
                return@withContext SyncOutcome.Refused(
                    "The laptop did not recognise that code. Check the six letters on its screen.",
                )
            }
            if (code !in 200..299) {
                val err = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull()
                return@withContext SyncOutcome.Refused(
                    "The laptop answered $code." + (err?.let { " $it" } ?: ""),
                )
            }
            val body = conn.inputStream.bufferedReader().readText()
            val ledgerJson = body.ledgerPart()
                ?: return@withContext SyncOutcome.Refused("The laptop's answer made no sense.")
            val merged = decodeBook(ledgerJson).getOrElse {
                return@withContext SyncOutcome.Refused("Could not read the ledger the laptop sent.")
            }
            SyncOutcome.Synced(merged, body.summarise())
        } catch (e: Exception) {
            SyncOutcome.Unreachable(e.plainly())
        } finally {
            runCatching { conn.disconnect() }
        }
    }
}

/**
 * The ledger out of the sync response, without a JSON library.
 *
 * The response is `{"ok":true,"report":{...},"ledger":{...}}` and the ledger is
 * the last field, so everything from `"ledger":` to the final closing brace is
 * it. Crude, and it holds only because this app also writes the response — but
 * it is checked by [decodeBook] immediately afterwards, so a wrong guess here
 * fails loudly rather than producing half a ledger.
 */
internal fun String.ledgerPart(): String? {
    val marker = "\"ledger\":"
    val i = indexOf(marker)
    if (i < 0) return null
    val start = i + marker.length
    val end = lastIndexOf('}')
    if (end <= start) return null
    return substring(start, end).trim()
}

/** A sentence about what the merge did, for the person who pressed the button. */
internal fun String.summarise(): String {
    fun count(field: String): Int {
        val i = indexOf("\"$field\":[")
        if (i < 0) return 0
        val close = indexOf(']', i)
        if (close < 0) return 0
        val inner = substring(i + field.length + 4, close).trim()
        return if (inner.isEmpty()) 0 else inner.count { it == ',' } + 1
    }
    val added = count("added")
    val advanced = count("advanced")
    val conflicted = count("conflicted")
    val parts = buildList {
        if (added > 0) add(if (added == 1) "1 entry sent up" else "$added entries sent up")
        if (advanced > 0) {
            add(if (advanced == 1) "1 confirmation shared" else "$advanced confirmations shared")
        }
        if (conflicted > 0) {
            add(
                if (conflicted == 1) {
                    "1 entry disagrees — the laptop's version stands"
                } else {
                    "$conflicted entries disagree — the laptop's versions stand"
                },
            )
        }
    }
    return if (parts.isEmpty()) "Already up to date." else parts.joinToString(", ") + "."
}

/**
 * The failure, in words somebody standing in a kitchen can act on.
 *
 * A stack trace class name tells a member nothing. What they need is which of
 * the three ordinary things went wrong: wrong address, laptop asleep, wrong
 * WiFi.
 */
private fun Throwable.plainly(): String = when (this) {
    is java.net.SocketTimeoutException ->
        "The laptop did not answer. Is 365+ open on it, and is this phone on the same WiFi?"
    is java.net.ConnectException ->
        "Nothing is listening there. Check the address, and that 365+ is open on the laptop."
    is java.net.UnknownHostException -> "That address does not exist on this network."
    is java.net.NoRouteToHostException -> "Cannot reach the laptop. Same WiFi?"
    else -> message ?: this::class.simpleName ?: "Something went wrong."
}
