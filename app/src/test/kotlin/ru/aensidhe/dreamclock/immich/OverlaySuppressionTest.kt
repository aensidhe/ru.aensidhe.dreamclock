package ru.aensidhe.dreamclock.immich

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import ru.aensidhe.dreamclock.core.photos.CaptionLines

class OverlaySuppressionTest {
    private fun photo(caption: CaptionLines?) = RenderPhoto("p", "t", caption)

    @Test
    fun `a paired slide with a captioned left photo suppresses the bottom-left group`() {
        val slide =
            RenderPairedPhoto(
                photo(CaptionLines("19 July 2026 | 14:32", "Berlin, Germany")),
                photo(null),
            )
        assertTrue(OverlaySuppression.suppressBottomLeft(slide))
    }

    @Test
    fun `a paired slide with no left caption does not suppress`() {
        assertFalse(
            OverlaySuppression.suppressBottomLeft(
                RenderPairedPhoto(photo(null), photo(CaptionLines("d", null))),
            ),
        )
    }

    @Test
    fun `a single photo never suppresses`() {
        assertFalse(
            OverlaySuppression.suppressBottomLeft(photo(CaptionLines("d", "l"))),
        )
    }

    @Test
    fun `the clock slide never suppresses`() {
        assertFalse(OverlaySuppression.suppressBottomLeft(RenderClock))
    }
}
