package ru.aensidhe.dreamclock.immich

import kotlin.test.Test
import kotlin.test.assertFailsWith

class KeystoreCipherTest {
    @Test
    fun decryptRejectsBlobShorterThanIv() {
        assertFailsWith<IllegalArgumentException> {
            KeystoreCipher().decrypt(byteArrayOf(1, 2, 3))
        }
    }
}
