package ru.aensidhe.dreamclock.immich

import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import ru.aensidhe.dreamclock.core.photos.Orientation

class AssetMapperTest {
    @Test
    fun `maps a portrait image with full exif`() {
        val asset =
            ImmichAsset(
                id = "a1",
                type = "IMAGE",
                localDateTime = "2026-07-19T14:32:00.000Z",
                exifInfo =
                    ExifInfo(
                        dateTimeOriginal = "2026-07-19T14:32:00.000+02:00",
                        city = "Berlin",
                        country = "Germany",
                        exifImageWidth = 3000,
                        exifImageHeight = 4000,
                        orientation = "1",
                    ),
            )
        val slide = AssetMapper.toSlideAsset(asset)!!
        assertEquals("a1", slide.id)
        assertEquals(Orientation.PORTRAIT, slide.orientation)
        assertEquals(LocalDateTime.of(2026, 7, 19, 14, 32), slide.caption.takenAt)
        assertEquals("Berlin", slide.caption.city)
        assertEquals("Germany", slide.caption.country)
    }

    @Test
    fun `exif orientation tag rotates a landscape image to portrait`() {
        val asset =
            ImmichAsset(
                id = "a2",
                type = "IMAGE",
                exifInfo = ExifInfo(exifImageWidth = 4000, exifImageHeight = 3000, orientation = "6"),
            )
        assertEquals(Orientation.PORTRAIT, AssetMapper.toSlideAsset(asset)!!.orientation)
    }

    @Test
    fun `image without exif is landscape with an empty caption`() {
        val slide = AssetMapper.toSlideAsset(ImmichAsset(id = "a3", type = "IMAGE"))!!
        assertEquals(Orientation.LANDSCAPE, slide.orientation)
        assertNull(slide.caption.takenAt)
        assertNull(slide.caption.city)
    }

    @Test
    fun `video assets are dropped`() {
        assertNull(AssetMapper.toSlideAsset(ImmichAsset(id = "v1", type = "VIDEO")))
    }

    @Test
    fun `blank id is dropped`() {
        assertNull(AssetMapper.toSlideAsset(ImmichAsset(id = "", type = "IMAGE")))
    }
}
