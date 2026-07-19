package ru.aensidhe.dreamclock.immich

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class PhotoFallbackTest {
    @Test
    fun `shows photos when enabled with credentials and assets`() {
        assertTrue(PhotoFallback.shouldShowPhotos(enabled = true, hasCredentials = true, assetCount = 5))
    }

    @Test
    fun `hides photos when disabled`() {
        assertFalse(PhotoFallback.shouldShowPhotos(enabled = false, hasCredentials = true, assetCount = 5))
    }

    @Test
    fun `hides photos without credentials`() {
        assertFalse(PhotoFallback.shouldShowPhotos(enabled = true, hasCredentials = false, assetCount = 5))
    }

    @Test
    fun `hides photos when no assets are available`() {
        assertFalse(PhotoFallback.shouldShowPhotos(enabled = true, hasCredentials = true, assetCount = 0))
    }
}
