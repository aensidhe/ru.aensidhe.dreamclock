package ru.aensidhe.dreamclock.core.photos

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class AssetOrientationTest {
    @Test
    fun `wider than tall is landscape`() {
        assertEquals(Orientation.LANDSCAPE, AssetOrientation.of(4000, 3000, null))
    }

    @Test
    fun `taller than wide is portrait`() {
        assertEquals(Orientation.PORTRAIT, AssetOrientation.of(3000, 4000, null))
    }

    @Test
    fun `exif orientation 6 swaps dimensions to portrait`() {
        // Stored 4000x3000 (landscape) but tag 6 rotates 90 degrees -> displayed portrait.
        assertEquals(Orientation.PORTRAIT, AssetOrientation.of(4000, 3000, 6))
    }

    @Test
    fun `exif orientation 8 swaps dimensions to portrait`() {
        assertEquals(Orientation.PORTRAIT, AssetOrientation.of(4000, 3000, 8))
    }

    @Test
    fun `exif orientation 3 does not swap`() {
        assertEquals(Orientation.LANDSCAPE, AssetOrientation.of(4000, 3000, 3))
    }

    @Test
    fun `square is landscape`() {
        assertEquals(Orientation.LANDSCAPE, AssetOrientation.of(2000, 2000, null))
    }

    @Test
    fun `zero dimensions default to landscape`() {
        assertEquals(Orientation.LANDSCAPE, AssetOrientation.of(0, 0, null))
    }
}
