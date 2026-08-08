package ru.aensidhe.dreamclock.core.pairing

import javax.crypto.AEADBadTagException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun hex(s: String): ByteArray =
    s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

class PairingCryptoTest {
    // NIST AES-256-GCM, 96-bit IV. key/iv/plaintext/aad -> ciphertext||tag.
    private val key = hex("feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308")
    private val iv = hex("cafebabefacedbaddecaf888")
    private val plaintext =
        hex(
            "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a72" +
                "1c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b39",
        )
    private val expected =
        hex(
            "522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa" +
                "8cb08e48590dbb3da7b08b1056828838c5f61e6393ba7a0abcc9f662" +
                "eb9f796c8d356fc31a8433884b696f4f",
        )

    @Test
    fun encrypt_matches_the_nist_vector() {
        assertEquals(expected.hex(), PairingCrypto.encrypt(key, iv, plaintext).hex())
    }

    @Test
    fun decrypt_round_trips() {
        assertEquals(plaintext.hex(), PairingCrypto.decrypt(key, iv, expected).hex())
    }

    @Test
    fun decrypt_rejects_a_tampered_tag() {
        val tampered = expected.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }
        assertFailsWith<AEADBadTagException> { PairingCrypto.decrypt(key, iv, tampered) }
    }

    @Test
    fun a_fresh_random_iv_still_round_trips() {
        val freshIv = ByteArray(12) { it.toByte() }
        val ct = PairingCrypto.encrypt(key, freshIv, "hello".toByteArray())
        assertTrue(ct.size > 5)
        assertEquals("hello", String(PairingCrypto.decrypt(key, freshIv, ct)))
    }
}
