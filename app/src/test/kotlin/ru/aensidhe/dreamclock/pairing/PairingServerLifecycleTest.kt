package ru.aensidhe.dreamclock.pairing

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Exercises the real [PairingServer.start]/[PairingServer.stop] lifecycle against an
 * actual bound socket on 127.0.0.1, unlike [PairingServerRoutesTest], which drives the
 * routing in-process via Ktor's `testApplication` and never binds a port.
 */
class PairingServerLifecycleTest {
    @Test
    fun start_binds_a_real_socket_serves_requests_and_stop_tears_it_down() {
        val server =
            PairingServer(
                address = "127.0.0.1",
                assets = { name -> if (name == "index.html") "<html>OK</html>".toByteArray() else null },
                onEnvelope = { true },
            )

        val port = server.start()
        try {
            assertTrue(port in MIN_PORT..MAX_PORT, "expected an ephemeral port, got $port")

            val (getStatus, getBody) = awaitReady(port)
            assertEquals(HttpURLConnection.HTTP_OK, getStatus)
            assertTrue(getBody.contains("OK"), "expected body to contain OK, was: $getBody")

            val postStatus = postPair(port, "{}")
            assertEquals(HttpURLConnection.HTTP_OK, postStatus)

            assertFailsWith<IllegalStateException> { server.start() }
        } finally {
            server.stop()
        }
    }

    /**
     * `embeddedServer(CIO).start(wait = false)` binds asynchronously, so the very first
     * request after [PairingServer.start] can race the listener coming up. Retries a
     * bounded number of times with a short backoff instead of a fixed sleep.
     */
    private fun awaitReady(port: Int): Pair<Int, String> {
        val deadline = System.nanoTime() + READY_TIMEOUT_MS * NANOS_PER_MILLI
        var lastError: IOException? = null
        while (System.nanoTime() < deadline) {
            try {
                return getRoot(port)
            } catch (error: IOException) {
                lastError = error
                Thread.sleep(RETRY_BACKOFF_MS)
            }
        }
        throw AssertionError("PairingServer on port $port never became ready", lastError)
    }

    private fun getRoot(port: Int): Pair<Int, String> {
        val connection = URI("http://127.0.0.1:$port/").toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = CONNECT_TIMEOUT_MS
        return try {
            val status = connection.responseCode
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            status to body
        } finally {
            connection.disconnect()
        }
    }

    private fun postPair(
        port: Int,
        body: String,
    ): Int {
        val connection = URI("http://127.0.0.1:$port/pair").toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = CONNECT_TIMEOUT_MS
        connection.requestMethod = "POST"
        connection.doOutput = true
        return try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val MIN_PORT = 1
        private const val MAX_PORT = 65535
        private const val READY_TIMEOUT_MS = 2000L
        private const val RETRY_BACKOFF_MS = 50L
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val CONNECT_TIMEOUT_MS = 500
    }
}
