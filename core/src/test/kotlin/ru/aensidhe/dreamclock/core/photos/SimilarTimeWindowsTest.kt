package ru.aensidhe.dreamclock.core.photos

import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class SimilarTimeWindowsTest {
    @Test
    fun `current year window is centered on today`() {
        val w = SimilarTimeWindows.windowFor(LocalDate.of(2026, 7, 20), daysEitherSide = 3, yearsBack = 0)
        assertEquals(LocalDate.of(2026, 7, 17), w.fromInclusive)
        assertEquals(LocalDate.of(2026, 7, 23), w.toInclusive)
    }

    @Test
    fun `years back shifts the whole window`() {
        val w = SimilarTimeWindows.windowFor(LocalDate.of(2026, 7, 20), daysEitherSide = 3, yearsBack = 5)
        assertEquals(LocalDate.of(2021, 7, 17), w.fromInclusive)
        assertEquals(LocalDate.of(2021, 7, 23), w.toInclusive)
    }

    @Test
    fun `window crossing month boundary`() {
        val w = SimilarTimeWindows.windowFor(LocalDate.of(2026, 1, 1), daysEitherSide = 2, yearsBack = 0)
        assertEquals(LocalDate.of(2025, 12, 30), w.fromInclusive)
        assertEquals(LocalDate.of(2026, 1, 3), w.toInclusive)
    }

    @Test
    fun `leap day today clamps to feb 28 in a non-leap year`() {
        val w = SimilarTimeWindows.windowFor(LocalDate.of(2024, 2, 29), daysEitherSide = 1, yearsBack = 1)
        // 2023 has no Feb 29; java.time clamps minusYears to Feb 28.
        assertEquals(LocalDate.of(2023, 2, 27), w.fromInclusive)
        assertEquals(LocalDate.of(2023, 3, 1), w.toInclusive)
    }

    @Test
    fun `zero days either side yields a single day`() {
        val w = SimilarTimeWindows.windowFor(LocalDate.of(2026, 7, 20), daysEitherSide = 0, yearsBack = 0)
        assertEquals(LocalDate.of(2026, 7, 20), w.fromInclusive)
        assertEquals(LocalDate.of(2026, 7, 20), w.toInclusive)
    }
}
