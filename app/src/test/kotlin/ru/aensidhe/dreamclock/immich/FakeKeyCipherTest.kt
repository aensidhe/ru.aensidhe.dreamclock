package ru.aensidhe.dreamclock.immich

import kotlin.test.Test
import kotlin.test.assertEquals

class FakeKeyCipher : KeyCipher {
    override fun encrypt(plaintext: String): ByteArray = plaintext.toByteArray(Charsets.UTF_8)

    override fun decrypt(blob: ByteArray): String = String(blob, Charsets.UTF_8)
}

class FakeKeyCipherTest {
    @Test
    fun roundTrips() {
        val cipher = FakeKeyCipher()
        assertEquals("api-key-123", cipher.decrypt(cipher.encrypt("api-key-123")))
    }
}
