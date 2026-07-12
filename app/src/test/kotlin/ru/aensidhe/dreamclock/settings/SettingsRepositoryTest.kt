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
}
