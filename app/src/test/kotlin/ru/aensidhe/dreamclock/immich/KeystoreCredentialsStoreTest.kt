package ru.aensidhe.dreamclock.immich

import com.google.protobuf.ByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import ru.aensidhe.dreamclock.settings.Settings

class KeystoreCredentialsStoreTest {
    private val cipher = FakeKeyCipher()

    private fun settings(
        host: String,
        key: String?,
    ): Settings {
        val b = Settings.newBuilder().setImmichHost(host)
        if (key != null) b.immichKeyCiphertext = ByteString.copyFrom(cipher.encrypt(key))
        return b.build()
    }

    @Test
    fun nullWhenHostBlank() {
        assertNull(KeystoreCredentialsStore(cipher).credentials(settings("", "k")))
    }

    @Test
    fun nullWhenKeyMissing() {
        assertNull(KeystoreCredentialsStore(cipher).credentials(settings("https://a", null)))
    }

    @Test
    fun decryptsWhenBothPresent() {
        val creds = KeystoreCredentialsStore(cipher).credentials(settings("https://a", "secret"))
        assertEquals(ImmichCredentials("https://a", "secret"), creds)
    }

    @Test
    fun nullWhenDecryptThrows() {
        val throwingCipher =
            object : KeyCipher {
                override fun encrypt(plaintext: String): ByteArray = plaintext.toByteArray()

                override fun decrypt(blob: ByteArray): String = throw java.security.GeneralSecurityException("bad key")
            }
        val settings =
            Settings
                .newBuilder()
                .setImmichHost("https://a")
                .setImmichKeyCiphertext(ByteString.copyFrom(byteArrayOf(1, 2, 3)))
                .build()
        assertNull(KeystoreCredentialsStore(throwingCipher).credentials(settings))
    }
}
