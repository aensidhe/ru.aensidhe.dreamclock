package ru.aensidhe.dreamclock.core.time

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class RuColloquialFormatterTest {
    private fun ru(
        h: Int,
        m: Int,
    ) = RuColloquialFormatter.format(h, m)

    @Test
    fun `exact hours`() {
        assertEquals("девять часов", ru(21, 0))
        assertEquals("час", ru(13, 0))
        assertEquals("два часа", ru(14, 0))
        assertEquals("двенадцать часов", ru(12, 0))
        assertEquals("двенадцать часов", ru(0, 0))
    }

    @Test
    fun `minutes into the coming hour`() {
        assertEquals("одна минута десятого", ru(21, 1))
        assertEquals("пять минут десятого", ru(21, 5))
        assertEquals("двадцать три минуты десятого", ru(21, 23))
        assertEquals("двадцать одна минута десятого", ru(21, 21))
    }

    @Test
    fun `quarter and half`() {
        assertEquals("четверть десятого", ru(21, 15))
        assertEquals("половина десятого", ru(21, 30))
    }

    @Test
    fun `to the next hour`() {
        assertEquals("без четверти десять", ru(21, 45))
        assertEquals("без пяти десять", ru(21, 55))
        assertEquals("без двадцати пяти десять", ru(21, 35))
        assertEquals("без одной десять", ru(21, 59))
    }

    @Test
    fun `wrap around noon and midnight`() {
        assertEquals("без четверти час", ru(12, 45))
        assertEquals("пять минут первого", ru(0, 5))
        assertEquals("без пяти двенадцать", ru(23, 55))
    }
}
