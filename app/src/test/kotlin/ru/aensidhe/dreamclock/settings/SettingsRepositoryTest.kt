package ru.aensidhe.dreamclock.settings

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SettingsRepositoryTest {
    @Test
    fun `defaults then update round-trips`() =
        runTest {
            val repo = SettingsRepository.inMemory()
            assertTrue(repo.settings.first().showColloquial)
            repo.update { it.toBuilder().setShowSeconds(false).build() }
            assertEquals(false, repo.settings.first().showSeconds)
        }

    @Test
    fun `photo settings have sensible defaults`() =
        runTest {
            val s = SettingsRepository.inMemory().settings.first()
            assertEquals(false, s.photosEnabled)
            assertEquals("", s.immichHost)
            assertEquals(3, s.daysEitherSide)
            assertEquals(0, s.maxYearsBack)
            assertEquals(8, s.photoIntervalSeconds)
            assertEquals(5, s.analogEveryNSlides)
            assertEquals(300, s.maxClockGapSeconds)
            assertEquals(10, s.analogSlideSeconds)
        }

    @Test
    fun `photos enabled round-trips`() =
        runTest {
            val repo = SettingsRepository.inMemory()
            repo.update {
                it
                    .toBuilder()
                    .setPhotosEnabled(true)
                    .setImmichHost("https://immich.lan")
                    .build()
            }
            val s = repo.settings.first()
            assertEquals(true, s.photosEnabled)
            assertEquals("https://immich.lan", s.immichHost)
        }
}
