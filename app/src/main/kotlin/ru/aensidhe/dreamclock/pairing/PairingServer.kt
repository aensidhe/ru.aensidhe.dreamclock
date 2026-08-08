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
import io.ktor.server.request.receiveText
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
            val ok = onEnvelope(call.receiveText())
            call.respondText(
                if (ok) "ok" else "no",
                status = if (ok) HttpStatusCode.OK else HttpStatusCode.BadRequest,
            )
        }
    }
}

private suspend fun ApplicationCall.serveAssetImpl(
    name: String,
    assets: (String) -> ByteArray?,
) {
    val bytes = assets(name)
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
        engine?.stop(0, 0)
        engine = null
    }

    companion object {
        fun contentTypeFor(name: String): String =
            when {
                name.endsWith(".html") -> "text/html; charset=utf-8"
                name.endsWith(".js") -> "text/javascript; charset=utf-8"
                else -> "application/octet-stream"
            }
    }
}
