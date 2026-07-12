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
    fun `colour mode labels map to the right resources`() {
        assertEquals(R.string.render_text_tint_label, colorModeLabel(ColorRenderModeProto.TEXT_TINT))
        assertEquals(R.string.render_panel_tint_label, colorModeLabel(ColorRenderModeProto.PANEL_TINT))
        assertEquals(R.string.render_full_scrim_label, colorModeLabel(ColorRenderModeProto.FULL_SCRIM))
        assertEquals(R.string.render_accent_label, colorModeLabel(ColorRenderModeProto.ACCENT))
    }

    @Test
    fun `colour mode descriptions map to the right resources`() {
        assertEquals(R.string.render_text_tint_desc, colorModeDescription(ColorRenderModeProto.TEXT_TINT))
        assertEquals(R.string.render_panel_tint_desc, colorModeDescription(ColorRenderModeProto.PANEL_TINT))
        assertEquals(R.string.render_full_scrim_desc, colorModeDescription(ColorRenderModeProto.FULL_SCRIM))
        assertEquals(R.string.render_accent_desc, colorModeDescription(ColorRenderModeProto.ACCENT))
    }

    @Test
    fun `screensaver settings action is the platform constant`() {
        assertEquals("android.settings.DREAM_SETTINGS", SCREENSAVER_SETTINGS_ACTION)
    }
}
