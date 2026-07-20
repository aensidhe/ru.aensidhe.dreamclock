package ru.aensidhe.dreamclock.immich

import ru.aensidhe.dreamclock.settings.Settings

interface CredentialsStore {
    fun credentials(settings: Settings): ImmichCredentials?
}

object NoCredentialsStore : CredentialsStore {
    override fun credentials(settings: Settings): ImmichCredentials? = null
}

class KeystoreCredentialsStore(
    private val cipher: KeyCipher,
) : CredentialsStore {
    override fun credentials(settings: Settings): ImmichCredentials? {
        if (settings.immichHost.isBlank() || settings.immichKeyCiphertext.isEmpty) return null
        val key = cipher.decrypt(settings.immichKeyCiphertext.toByteArray())
        return ImmichCredentials(settings.immichHost, key)
    }
}
