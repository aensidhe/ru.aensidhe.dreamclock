package ru.aensidhe.dreamclock.core.pairing

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object PairingCrypto {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    fun encrypt(
        key: ByteArray,
        iv: ByteArray,
        plaintext: ByteArray,
    ): ByteArray = run(Cipher.ENCRYPT_MODE, key, iv, plaintext)

    fun decrypt(
        key: ByteArray,
        iv: ByteArray,
        ciphertextAndTag: ByteArray,
    ): ByteArray = run(Cipher.DECRYPT_MODE, key, iv, ciphertextAndTag)

    private fun run(
        mode: Int,
        key: ByteArray,
        iv: ByteArray,
        input: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(input)
    }
}
