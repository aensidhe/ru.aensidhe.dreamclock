package ru.aensidhe.dreamclock.core.time

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class EnColloquialFormatterTest {
    private fun en(
        h: Int,
        m: Int,
    ) = EnColloquialFormatter.format(h, m)

    @Test
    fun `exact and simple`() {
        assertEquals("nine o'clock", en(21, 0))
        assertEquals("twelve o'clock", en(0, 0))
        assertEquals("five past nine", en(21, 5))
        assertEquals("twenty-three past nine", en(21, 23))
    }

    @Test
    fun `quarter half to`() {
        assertEquals("quarter past nine", en(21, 15))
        assertEquals("half past nine", en(21, 30))
        assertEquals("quarter to ten", en(21, 45))
        assertEquals("five to ten", en(21, 55))
        assertEquals("twenty-five to ten", en(21, 35))
    }

    @Test
    fun `wrap around noon`() {
        assertEquals("quarter to one", en(12, 45))
    }
}
