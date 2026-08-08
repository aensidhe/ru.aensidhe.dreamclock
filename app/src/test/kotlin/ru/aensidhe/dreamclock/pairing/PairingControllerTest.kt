package ru.aensidhe.dreamclock.pairing

import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import ru.aensidhe.dreamclock.core.pairing.PairingCrypto
import ru.aensidhe.dreamclock.immich.ImmichClient
import ru.aensidhe.dreamclock.immich.ImmichCredentials

class PairingControllerTest {
    private val key = ByteArray(32) { it.toByte() }
    private val iv = ByteArray(12) { (it + 1).toByte() }

    private fun envelope(payloadJson: String): String {
        val ct = PairingCrypto.encrypt(key, iv, payloadJson.toByteArray())
        val enc = Base64.getUrlEncoder().withoutPadding()
        return """{"iv":"${enc.encodeToString(iv)}","ciphertext":"${enc.encodeToString(ct)}"}"""
    }

    @Test
    fun key_mode_validates_then_saves() =
        runBlocking {
            val server = MockWebServer()
            server.enqueue(MockResponse().setBody("""{"assets":{"total":3,"count":1,"items":[]}}"""))
            var saved: ImmichCredentials? = null
            val host = server.url("/").toString()
            val controller =
                PairingController(
                    key = key,
                    apiFactory = { ImmichClient.api(it) },
                    zone = ZoneId.of("UTC"),
                    save = { saved = it },
                )

            val outcome =
                controller.receive(
                    envelope("""{"mode":"key","host":"$host","apiKey":"secret-key"}"""),
                    today = LocalDate.of(2026, 8, 8),
                    daysEitherSide = 3,
                )

            assertEquals(PairingOutcome.Saved, outcome)
            assertEquals("secret-key", saved?.apiKey)
            server.shutdown()
        }

    @Test
    fun login_mode_mints_then_saves() =
        runBlocking {
            val server = MockWebServer()
            try {
                server.enqueue(MockResponse().setBody("""{"accessToken":"tok"}"""))
                server.enqueue(MockResponse().setBody("""{"secret":"minted"}"""))
                var saved: ImmichCredentials? = null
                val host = server.url("/").toString()
                val controller =
                    PairingController(key, { ImmichClient.api(it) }, ZoneId.of("UTC")) { saved = it }

                val outcome =
                    controller.receive(
                        envelope("""{"mode":"login","host":"$host","email":"a@b.c","password":"pw"}"""),
                        today = LocalDate.of(2026, 8, 8),
                        daysEitherSide = 3,
                    )

                assertEquals(PairingOutcome.Saved, outcome)
                assertEquals("minted", saved?.apiKey)
                assertEquals("/api/auth/login", server.takeRequest().path)
                assertEquals("/api/api-keys", server.takeRequest().path)
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun a_tampered_envelope_fails_without_saving() =
        runBlocking {
            var saved: ImmichCredentials? = null
            val controller =
                PairingController(key, { ImmichClient.api(it) }, ZoneId.of("UTC")) { saved = it }
            val good = envelope("""{"mode":"key","host":"http://x","apiKey":"k"}""")
            val tampered = good.dropLast(3) + "AAA\""

            val outcome =
                controller.receive(tampered, LocalDate.of(2026, 8, 8), 3)

            assertTrue(outcome is PairingOutcome.Failed)
            assertEquals(null, saved)
        }

    @Test
    fun a_corrupted_ciphertext_byte_fails_without_saving() =
        runBlocking {
            var saved: ImmichCredentials? = null
            val controller =
                PairingController(key, { ImmichClient.api(it) }, ZoneId.of("UTC")) { saved = it }
            val valid = envelope("""{"mode":"key","host":"http://x","apiKey":"k"}""")
            val env = PairingCodec.parseEnvelope(valid)
            val ct = PairingCodec.decodeBase64Url(env.ciphertext)
            ct[ct.lastIndex] = (ct.last() + 1).toByte()
            val enc = Base64.getUrlEncoder().withoutPadding()
            val tampered = """{"iv":"${env.iv}","ciphertext":"${enc.encodeToString(ct)}"}"""

            val outcome = controller.receive(tampered, LocalDate.of(2026, 8, 8), 3)

            assertTrue(outcome is PairingOutcome.Failed)
            assertEquals(null, saved)
        }
}
