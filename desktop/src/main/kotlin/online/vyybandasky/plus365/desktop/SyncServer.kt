package online.vyybandasky.plus365.desktop

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.book.MergeReport
import online.vyybandasky.plus365.core.book.mergeFrom
import online.vyybandasky.plus365.core.store.decodeBook
import online.vyybandasky.plus365.core.store.encodeBook
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.random.Random

/**
 * The laptop's side of sync.
 *
 * A star, not a mesh: every phone talks to this and to nothing else, and this
 * holds the one authoritative ledger. For three founders on one WiFi that is
 * both simpler and more correct than peer-to-peer — there is exactly one book
 * to be right about, and exactly one place where merging happens.
 *
 * Everything here is a thin shell over [mergeFrom]. The transport decides
 * nothing; if it did, there would be two answers to what the ledger says.
 */
class SyncHub(
    /** The laptop's current book. */
    val read: () -> LedgerBook,
    /** Replace it. Returns false if it could not be written to disk. */
    val write: (LedgerBook) -> Boolean,
)

/**
 * The shared secret a phone must present.
 *
 * This listens on the LAN, which means it listens to everyone on the WiFi —
 * the flat, the café, whoever guessed the password. The group's money should
 * not be readable by anybody who can reach the port, and "it is a home network"
 * is a description of today rather than a security decision.
 *
 * Short and typeable on a phone, because a code nobody can type is a code that
 * gets replaced by turning the check off. It is a shared secret between three
 * people who see each other, not a password against a determined attacker.
 */
object PairingCode {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** Reads the code, making one the first time. Kept beside the ledger. */
    fun loadOrCreate(dir: File): String {
        val f = File(dir, "pairing-code.txt")
        val existing = runCatching { f.readText().trim() }.getOrNull()
        if (!existing.isNullOrBlank()) return existing
        val made = (1..6).map { ALPHABET[Random.nextInt(ALPHABET.length)] }.joinToString("")
        runCatching {
            dir.mkdirs()
            f.writeText(made)
        }
        return made
    }
}

/**
 * The address a phone should be pointed at.
 *
 * Picks a real IPv4 address on a live interface, skipping loopback and the
 * virtual adapters Docker and WSL leave behind — those are routable from this
 * machine and from nowhere else, and handing somebody a phone-unreachable
 * address to type in is a slow way to waste an evening.
 */
fun lanAddress(): String? = runCatching {
    // `isVirtual` is no help: it means "subinterface", and Hyper-V's adapters
    // are not that. On this laptop the virtual ones present 172.23.96.1 and
    // 172.25.32.1, both perfectly site-local and both reachable from precisely
    // one machine — so a naive pick hands somebody an address that will never
    // answer, and they spend the evening blaming the WiFi.
    val skip = listOf("vethernet", "virtual", "vmware", "hyper-v", "wsl", "loopback", "bluetooth", "docker")
    val candidates = NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .filterNot { ni ->
            val n = (ni.displayName + " " + ni.name).lowercase()
            skip.any { it in n }
        }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .filter { !it.isLoopbackAddress && it.isSiteLocalAddress }
        .mapNotNull { it.hostAddress }

    // Home WiFi first. 192.168/16 is what a domestic router hands out, and
    // preferring it is the difference between the address on the screen being
    // the one the phones can reach and merely being a valid one.
    candidates.firstOrNull { it.startsWith("192.168.") }
        ?: candidates.firstOrNull { it.startsWith("10.") }
        ?: candidates.firstOrNull()
}.getOrNull()

/**
 * What the laptop tells a phone after a merge.
 *
 * The merged ledger and a plain account of what the merge did. The phone
 * replaces its book with `ledger` wholesale — there is nothing for it to
 * decide, which is the point of the topology.
 */
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

/**
 * The sync routes.
 *
 * `/health` stays open — it carries no ledger data and is how a build gets
 * identified from outside. Everything that touches the book asks for the code.
 */
fun Routing.syncRoutes(hub: SyncHub, code: String) {
    /** True if the caller presented the pairing code. */
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
        call.respondText(encodeBook(hub.read()), ContentType.Application.Json)
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
        // One at a time. Two phones pushing at once would otherwise both merge
        // against the same starting book and the second would write over the
        // first — the classic lost update, with somebody's contribution as the
        // thing lost.
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
        call.respondText(syncResponse(merged, report), ContentType.Application.Json)
    }
}
