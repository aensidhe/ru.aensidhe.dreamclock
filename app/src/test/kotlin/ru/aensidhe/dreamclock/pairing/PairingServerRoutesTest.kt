package ru.aensidhe.dreamclock.pairing

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

private val KNOWN_ASSETS =
    mapOf(
        "index.html" to "INDEX_MARKER".toByteArray(),
        "noble-ciphers.js" to "NOBLE_MARKER".toByteArray(),
    )

private fun fakeAssets(name: String): ByteArray? = KNOWN_ASSETS[name]

class PairingServerRoutesTest {
    @Test
    fun root_serves_index_html() =
        testApplication {
            application { pairingRoutes(::fakeAssets) { true } }

            val response = client.get("/")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("INDEX_MARKER", String(response.bodyAsBytes()))
        }

    @Test
    fun named_asset_is_served() =
        testApplication {
            application { pairingRoutes(::fakeAssets) { true } }

            val response = client.get("/noble-ciphers.js")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("NOBLE_MARKER", String(response.bodyAsBytes()))
        }

    @Test
    fun unknown_asset_is_not_found() =
        testApplication {
            application { pairingRoutes(::fakeAssets) { true } }

            val response = client.get("/does-not-exist.bin")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun pair_forwards_body_and_responds_ok_when_envelope_accepted() =
        testApplication {
            var received: String? = null
            application {
                pairingRoutes(::fakeAssets) { body ->
                    received = body
                    true
                }
            }

            val response =
                client.post("/pair") {
                    setBody("""{"iv":"a","ciphertext":"b"}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"iv":"a","ciphertext":"b"}""", received)
        }

    @Test
    fun pair_responds_bad_request_when_envelope_rejected() =
        testApplication {
            var received: String? = null
            application {
                pairingRoutes(::fakeAssets) { body ->
                    received = body
                    false
                }
            }

            val response =
                client.post("/pair") {
                    setBody("not a real envelope")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("not a real envelope", received)
            assertEquals("no", response.bodyAsText())
        }
}
