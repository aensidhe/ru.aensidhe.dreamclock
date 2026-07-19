package ru.aensidhe.dreamclock.core.photos

import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import ru.aensidhe.dreamclock.core.time.ClockLocale

class PhotoCaptionTest {
    private val takenAt = LocalDateTime.of(2026, 7, 19, 14, 32)

    @Test
    fun `english full caption`() {
        val c = PhotoCaption.format(CaptionSource(takenAt, "Berlin", "Germany"), ClockLocale.EN)!!
        assertEquals("19 July 2026 | 14:32", c.dateTime)
        assertEquals("Berlin, Germany", c.location)
    }

    @Test
    fun `russian full caption`() {
        val c = PhotoCaption.format(CaptionSource(takenAt, "Берлин", "Германия"), ClockLocale.RU)!!
        assertEquals("19 июля 2026 | 14:32", c.dateTime)
        assertEquals("Берлин, Германия", c.location)
    }

    @Test
    fun `city only`() {
        val c = PhotoCaption.format(CaptionSource(takenAt, "Berlin", null), ClockLocale.EN)!!
        assertEquals("Berlin", c.location)
    }

    @Test
    fun `country only`() {
        val c = PhotoCaption.format(CaptionSource(takenAt, "  ", "Germany"), ClockLocale.EN)!!
        assertEquals("Germany", c.location)
    }

    @Test
    fun `no location`() {
        val c = PhotoCaption.format(CaptionSource(takenAt, null, null), ClockLocale.EN)!!
        assertEquals("19 July 2026 | 14:32", c.dateTime)
        assertNull(c.location)
    }

    @Test
    fun `no date`() {
        val c = PhotoCaption.format(CaptionSource(null, "Berlin", "Germany"), ClockLocale.EN)!!
        assertNull(c.dateTime)
        assertEquals("Berlin, Germany", c.location)
    }

    @Test
    fun `nothing available yields null`() {
        assertNull(PhotoCaption.format(CaptionSource(null, " ", null), ClockLocale.EN))
    }
}
