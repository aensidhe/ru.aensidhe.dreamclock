package ru.aensidhe.dreamclock.pairing

import android.content.Context
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.net.ServerSocket

object PairingAssets {
    fun read(
        context: Context,
        name: String,
    ): ByteArray? = runCatching { context.assets.open("pairing/$name").use { it.readBytes() } }.getOrNull()
}

internal fun Application.pairingRoutes(
    assets: (String) -> ByteArray?,
    onEnvelope: suspend (String) -> Boolean,
) {
    routing {
        get("/") { serveAsset("index.html", assets) }
        get("/{name}") { serveAsset(call.parameters["name"].orEmpty(), assets) }
        post("/pair") {
            val length = call.request.contentLength()
            if (length == null || length > PairingServer.MAX_PAIR_BODY_BYTES) {
                call.respond(HttpStatusCode.PayloadTooLarge)
                return@post
            }
            val ok = onEnvelope(call.receiveText())
            call.respondText(
                if (ok) "ok" else "no",
                status = if (ok) HttpStatusCode.OK else HttpStatusCode.BadRequest,
            )
        }
    }
}

/**
 * True for anything that could escape the flat `assets/pairing/` directory: path
 * separators or a `..` segment. This is an unauthenticated LAN endpoint, so the
 * `{name}` path segment is treated as untrusted input.
 */
private fun isUnsafeAssetName(name: String): Boolean = name.contains('/') || name.contains('\\') || name.contains("..")

private suspend fun ApplicationCall.serveAssetImpl(
    name: String,
    assets: (String) -> ByteArray?,
) {
    val bytes = if (isUnsafeAssetName(name)) null else assets(name)
    if (bytes == null) {
        respondText("not found", status = HttpStatusCode.NotFound)
    } else {
        respondBytes(bytes, ContentType.parse(PairingServer.contentTypeFor(name)))
    }
}

private suspend fun RoutingContext.serveAsset(
    name: String,
    assets: (String) -> ByteArray?,
) = call.serveAssetImpl(name, assets)

class PairingServer(
    private val address: String,
    private val assets: (String) -> ByteArray?,
    private val onEnvelope: suspend (String) -> Boolean,
) {
    private var engine: EmbeddedServer<*, *>? = null

    fun start(): Int {
        check(engine == null) { "PairingServer already started" }
        // Ktor 3.2.0's CIO engine does not reliably surface the OS-assigned port when
        // bound with port = 0, so an ephemeral port is chosen up front and used both to
        // start the engine and as the returned value.
        val port = ServerSocket(0).use { it.localPort }
        val server =
            embeddedServer(CIO, host = address, port = port) {
                pairingRoutes(assets, onEnvelope)
            }
        server.start(wait = false)
        engine = server
        return port
    }

    fun stop() {
        // Stop immediately: no grace period for in-flight requests to finish, no extra
        // wait beyond that before the engine is torn down.
        engine?.stop(STOP_GRACE_MS, STOP_TIMEOUT_MS)
        engine = null
    }

    companion object {
        private const val STOP_GRACE_MS = 0L
        private const val STOP_TIMEOUT_MS = 0L

        // The real pairing envelope is a few hundred bytes; this is a generous cap that
        // still rejects an unauthenticated LAN caller trying to stream an unbounded body.
        internal const val MAX_PAIR_BODY_BYTES = 64 * 1024L

        fun contentTypeFor(name: String): String =
            when {
                name.endsWith(".html") -> "text/html; charset=utf-8"
                name.endsWith(".js") -> "text/javascript; charset=utf-8"
                else -> "application/octet-stream"
            }
    }
}
