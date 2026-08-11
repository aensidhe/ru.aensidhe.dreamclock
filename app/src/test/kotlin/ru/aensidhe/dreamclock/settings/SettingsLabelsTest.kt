package ru.aensidhe.dreamclock.settings

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import ru.aensidhe.dreamclock.R

class SettingsLabelsTest {
    @Test
    fun `language labels map to the right resources`() {
        assertEquals(R.string.lang_follow_system, languageLabel(Language.FOLLOW_SYSTEM))
        assertEquals(R.string.lang_ru, languageLabel(Language.RU))
        assertEquals(R.string.lang_en, languageLabel(Language.EN))
    }

    @Test
    fun `screensaver settings action is the platform constant`() {
        assertEquals("android.settings.DREAM_SETTINGS", SCREENSAVER_SETTINGS_ACTION)
    }
}
