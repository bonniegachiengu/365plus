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
 */
const val DEFAULT_PORT: Int = 8443

/**
 * Bound to loopback on purpose. Phones reach this through the tunnel, never by
 * dialling the laptop directly, so there is no reason to listen on the LAN.
 */
const val DEFAULT_HOST: String = "127.0.0.1"

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
): EmbeddedServer<*, *> = embeddedServer(CIO, port = port, host = host) {
    routing {
        healthRoutes()
    }
}

/** Starts the embedded API. Non-blocking so the Compose window owns the main thread. */
fun startHealthServer(
    port: Int = DEFAULT_PORT,
    host: String = DEFAULT_HOST,
): EmbeddedServer<*, *> = buildServer(port, host).also { it.start(wait = false) }
