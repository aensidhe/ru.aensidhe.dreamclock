package ru.aensidhe.dreamclock.immich

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import ru.aensidhe.dreamclock.core.photos.DateWindow

class ImmichSearchBoundsTest {
    @Test
    fun `formats inclusive day bounds with a trailing offset`() {
        val window = DateWindow(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 23))
        val bounds = ImmichSearchBoundsFactory.forWindow(window, ZoneOffset.ofHours(2))
        assertEquals("2026-07-17T00:00:00+02:00", bounds.takenAfter)
        assertEquals("2026-07-23T23:59:59+02:00", bounds.takenBefore)
    }

    @Test
    fun `utc window carries a Z offset`() {
        val window = DateWindow(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1))
        val bounds = ImmichSearchBoundsFactory.forWindow(window, ZoneOffset.UTC)
        assertEquals("2026-01-01T00:00:00Z", bounds.takenAfter)
        assertEquals("2026-01-01T23:59:59Z", bounds.takenBefore)
    }
}
