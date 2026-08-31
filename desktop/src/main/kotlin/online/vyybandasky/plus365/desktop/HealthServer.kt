package online.vyybandasky.plus365.desktop

import io.ktor.http.ContentType
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import online.vyybandasky.plus365.core.BuildInfo
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

/**
 * What this endpoint says it is.
 *
 * Taken from [BuildInfo] rather than typed here. It was a hand-written "0.1.0"
 * and stayed that way through thirty releases, which is the same failure the
 * build stamp in the window exists to prevent — except worse, because a person
 * looking at the window would have noticed and a phone asking this endpoint what
 * it is talking to would not.
 */
val APP_VERSION: String get() = BuildInfo.NAME

/**
 * The port the brief names (§4). Plain HTTP despite the 8443 convention — TLS is
 * terminated by Cloudflare at the edge, exactly as it is for Myra and LaunchGear,
 * so terminating it again here would buy nothing.
 *
 * On this machine 8443 is unusable: Windows reserves 8435-8534 for Hyper-V and
 * WSL. The app tries a list and reports which one it got (D92), so this is the
 * first preference rather than a promise.
 */
const val DEFAULT_PORT: Int = 8443

/**
 * Every interface, so a phone on the same WiFi can reach it.
 *
 * This was loopback, on the reasoning that phones would arrive through a tunnel
 * rather than dial the laptop directly. The group has no tunnel and three
 * founders in one room, so the LAN is the transport.
 *
 * Listening on the LAN means listening to everyone on the WiFi, which is why
 * every route that touches the ledger asks for a pairing code. Binding wide and
 * checking nothing would put the group's money one guessed port away from
 * anybody sharing the network.
 */
const val DEFAULT_HOST: String = "0.0.0.0"

/**
 * The health payload, built as a pure function so it can be asserted without
 * standing a server up.
 */
fun healthJson(version: String = APP_VERSION): String =
    """{"status":"ok","service":"365plus","version":"$version"}"""

/** Routes live apart from the engine so M2 can mount the sync routes alongside. */
fun Routing.healthRoutes() {
    get("/health") {
        call.respondText(healthJson(), ContentType.Application.Json)
    }
}

fun buildServer(
    port: Int = DEFAULT_PORT,
    host: String = DEFAULT_HOST,
    hub: SyncHub? = null,
    pairingCode: String? = null,
): EmbeddedServer<*, *> = embeddedServer(CIO, port = port, host = host) {
    routing {
        healthRoutes()
        if (hub != null && pairingCode != null) syncRoutes(hub, pairingCode)
    }
}

/** Starts the embedded API. Non-blocking so the Compose window owns the main thread. */
fun startHealthServer(
    port: Int = DEFAULT_PORT,
    host: String = DEFAULT_HOST,
    hub: SyncHub? = null,
    pairingCode: String? = null,
): EmbeddedServer<*, *> =
    buildServer(port, host, hub, pairingCode).also { it.start(wait = false) }
