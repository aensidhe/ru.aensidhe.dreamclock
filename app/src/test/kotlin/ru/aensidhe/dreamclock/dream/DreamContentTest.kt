package ru.aensidhe.dreamclock.dream

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import ru.aensidhe.dreamclock.settings.ColorRenderModeProto
import ru.aensidhe.dreamclock.ui.colorrender.ColorRenderMode

class DreamContentTest {
    @Test
    fun `proto colour modes map to render modes`() {
        assertEquals(ColorRenderMode.TEXT_TINT, ColorRenderModeProto.TEXT_TINT.toColorRenderMode())
        assertEquals(ColorRenderMode.PANEL_TINT, ColorRenderModeProto.PANEL_TINT.toColorRenderMode())
        assertEquals(ColorRenderMode.FULL_SCRIM, ColorRenderModeProto.FULL_SCRIM.toColorRenderMode())
        assertEquals(ColorRenderMode.ACCENT, ColorRenderModeProto.ACCENT.toColorRenderMode())
    }

    @Test
    fun `unrecognized colour mode falls back to text tint`() {
        assertEquals(ColorRenderMode.TEXT_TINT, ColorRenderModeProto.UNRECOGNIZED.toColorRenderMode())
    }
}
