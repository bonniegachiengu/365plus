package online.vyybandasky.plus365.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import online.vyybandasky.plus365.core.BuildInfo
import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.book.MergeReport
import online.vyybandasky.plus365.core.book.mergeFrom
import online.vyybandasky.plus365.core.store.decodeBook
import online.vyybandasky.plus365.core.store.encodeBook

const val DEFAULT_PORT: Int = 8443
const val DEFAULT_HOST: String = "0.0.0.0"

val APP_VERSION: String get() = BuildInfo.NAME

interface ServerLedger {
    fun read(): LedgerBook
    fun write(book: LedgerBook): Boolean
}

class SyncHub(
    private val ledger: ServerLedger,
) {
    fun read(): LedgerBook = ledger.read()
    fun write(book: LedgerBook): Boolean = ledger.write(book)
}

fun healthJson(
    version: String = APP_VERSION,
    commit: String = BuildInfo.COMMIT,
): String =
    """{"status":"ok","service":"365plus","version":"$version","commit":"$commit"}"""

private fun syncResponse(book: LedgerBook, report: MergeReport): String {
    fun ids(xs: List<String>) = xs.joinToString(",", "[", "]") { "\"$it\"" }

    return """{"ok":true,"report":{"added":${ids(report.added)},""" +
        """"advanced":${ids(report.advanced)},""" +
        """"conflicted":${ids(report.conflicted)},""" +
        """"approvalsPooled":${ids(report.approvalsPooled)},""" +
        """"settledByPooling":${ids(report.settledByPooling)},""" +
        """"definitionsAdded":${ids(report.definitionsAdded)}},""" +
        """"ledger":${encodeBook(book)}}"""
}

fun Routing.healthRoutes() {
    get("/health") {
        call.respondText(healthJson(), ContentType.Application.Json)
    }
}

fun Routing.syncRoutes(hub: SyncHub, code: String) {
    suspend fun io.ktor.server.application.ApplicationCall.authorised(): Boolean {
        val given = request.headers["X-Pairing-Code"]
        if (given == code) return true

        respondText(
            """{"ok":false,"error":"pairing code missing or wrong"}""",
            ContentType.Application.Json,
            HttpStatusCode.Unauthorized,
        )
        return false
    }

    get("/ledger") {
        if (!call.authorised()) return@get
        call.respondText(
            encodeBook(hub.read()),
            ContentType.Application.Json,
        )
    }

    post("/sync") {
        if (!call.authorised()) return@post

        val body = call.receiveText()
        val incoming = decodeBook(body).getOrElse {
            call.respondText(
                """{"ok":false,"error":"could not read that ledger"}""",
                ContentType.Application.Json,
                HttpStatusCode.BadRequest,
            )
            return@post
        }

        val (merged, report) = synchronized(hub) {
            val (m, r) = hub.read().mergeFrom(incoming)

            if (r.changedAnything && !hub.write(m)) {
                return@synchronized null to r
            }

            m to r
        }

        if (merged == null) {
            call.respondText(
                """{"ok":false,"error":"the laptop could not save the merged ledger"}""",
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError,
            )
            return@post
        }

        call.respondText(
            syncResponse(merged, report),
            ContentType.Application.Json,
        )
    }
}

fun buildServer(
    port: Int = DEFAULT_PORT,
    host: String = DEFAULT_HOST,
    hub: SyncHub? = null,
    pairingCode: String? = null,
): EmbeddedServer<*, *> =
    embeddedServer(CIO, port = port, host = host) {
        routing {
            healthRoutes()
            if (hub != null && pairingCode != null) {
                syncRoutes(hub, pairingCode)
            }
        }
    }

fun startServer(
    port: Int = DEFAULT_PORT,
    host: String = DEFAULT_HOST,
    hub: SyncHub? = null,
    pairingCode: String? = null,
): EmbeddedServer<*, *> =
    buildServer(port, host, hub, pairingCode).also {
        it.start(wait = false)
    }
