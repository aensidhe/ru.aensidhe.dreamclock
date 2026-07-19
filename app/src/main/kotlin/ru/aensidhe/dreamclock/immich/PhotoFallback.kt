package ru.aensidhe.dreamclock.immich

object PhotoFallback {
    fun shouldShowPhotos(
        enabled: Boolean,
        hasCredentials: Boolean,
        assetCount: Int,
    ): Boolean = enabled && hasCredentials && assetCount > 0
}
