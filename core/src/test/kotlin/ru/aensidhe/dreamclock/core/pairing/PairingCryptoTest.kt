package ru.aensidhe.dreamclock.core.pairing

import javax.crypto.AEADBadTagException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun hex(s: String): ByteArray = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

class PairingCryptoTest {
    // GCM spec Test Case 14 (AES-256-GCM, 96-bit IV, no AAD), verified against OpenSSL.
    // Any correct AES-GCM — JDK here, @noble/ciphers on the phone — reproduces it.
    private val key = hex("0000000000000000000000000000000000000000000000000000000000000000")
    private val iv = hex("000000000000000000000000")
    private val plaintext = hex("00000000000000000000000000000000")
    private val expected = hex("cea7403d4d606b6e074ec5d3baf39d18d0d1c8a799996bf0265b98b5d48ab919")

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
