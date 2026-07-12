package ru.aensidhe.dreamclock.core.time

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class HourMathTest {
    @Test
    fun `hour12 maps 24h to 12h`() {
        assertEquals(12, hour12(0))
        assertEquals(9, hour12(21))
        assertEquals(1, hour12(13))
        assertEquals(12, hour12(12))
    }

    @Test
    fun `comingHour12 is the next 12h hour`() {
        assertEquals(10, comingHour12(21))
        assertEquals(12, comingHour12(23))
        assertEquals(1, comingHour12(12))
        assertEquals(1, comingHour12(0))
    }
}
