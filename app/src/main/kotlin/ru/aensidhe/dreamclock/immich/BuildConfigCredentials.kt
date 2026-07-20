package ru.aensidhe.dreamclock.immich

import ru.aensidhe.dreamclock.BuildConfig

object BuildConfigCredentials {
    fun store(): CredentialsStore {
        val host = BuildConfig.IMMICH_HOST
        val key = BuildConfig.IMMICH_KEY
        return if (host.isNotBlank() && key.isNotBlank()) {
            StaticCredentialsStore(ImmichCredentials(host, key))
        } else {
            NoCredentialsStore
        }
    }
}
