package ru.aensidhe.dreamclock.pairing

import kotlin.test.Test
import kotlin.test.assertEquals

class PairingPayloadTest {
    @Test
    fun parses_the_envelope() {
        val env = PairingCodec.parseEnvelope("""{"iv":"AAAA","ciphertext":"BBBB"}""")
        assertEquals("AAAA", env.iv)
        assertEquals("BBBB", env.ciphertext)
    }

    @Test
    fun parses_a_key_mode_payload_ignoring_unknown_fields() {
        val payload =
            PairingCodec.parsePayload(
                """{"mode":"key","host":"http://immich","apiKey":"k","extra":1}""",
            )
        assertEquals(PairingPayload.MODE_KEY, payload.mode)
        assertEquals("http://immich", payload.host)
        assertEquals("k", payload.apiKey)
    }

    @Test
    fun decodes_url_safe_base64_without_padding() {
        // "hello" -> aGVsbG8
        assertEquals("hello", String(PairingCodec.decodeBase64Url("aGVsbG8")))
    }
}
