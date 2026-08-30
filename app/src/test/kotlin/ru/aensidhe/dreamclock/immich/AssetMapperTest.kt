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

    private fun person(
        id: String,
        name: String?,
        vararg x: Int,
        hidden: Boolean = false,
    ) = ImmichPerson(id = id, name = name, isHidden = hidden, faces = x.map { ImmichFace(boundingBoxX1 = it) })

    private fun namesOf(vararg people: ImmichPerson): List<String> =
        AssetMapper.toSlideAsset(ImmichAsset(id = "a", type = "IMAGE", people = people.toList()))!!.caption.people

    @Test
    fun `hidden and unnamed people are dropped`() {
        assertEquals(
            listOf("Anna"),
            namesOf(
                person("p1", "Anna", 10),
                person("p2", "", 20),
                person("p3", null, 30),
                person("p4", "   ", 40),
                person("p5", "Ghost", 50, hidden = true),
            ),
        )
    }

    @Test
    fun `names are ordered left to right by the leftmost face`() {
        assertEquals(
            listOf("Left", "Middle", "Right"),
            namesOf(person("p1", "Right", 900), person("p2", "Left", 100, 950), person("p3", "Middle", 500)),
        )
    }

    @Test
    fun `people without coordinates come last in arrival order`() {
        assertEquals(
            listOf("Anna", "NoBoxA", "NoBoxB"),
            namesOf(person("p1", "NoBoxA"), person("p2", "NoBoxB"), person("p3", "Anna", 5)),
        )
    }

    @Test
    fun `names are trimmed and de-duplicated keeping the first`() {
        assertEquals(
            listOf("Anna", "Boris"),
            namesOf(person("p1", " Anna ", 10), person("p2", "Boris", 20), person("p3", "Anna", 30)),
        )
    }

    @Test
    fun `no people yields an empty list`() {
        assertEquals(emptyList<String>(), namesOf())
    }
}
