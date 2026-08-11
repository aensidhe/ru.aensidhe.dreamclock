package ru.aensidhe.dreamclock.settings

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class CycleRowTest {
    @Test
    fun `steps forward within bounds`() {
        assertEquals(1, cycledIndex(0, 3, 1))
        assertEquals(2, cycledIndex(1, 3, 1))
    }

    @Test
    fun `wraps forward past the end`() {
        assertEquals(0, cycledIndex(2, 3, 1))
    }

    @Test
    fun `wraps backward past the start`() {
        assertEquals(2, cycledIndex(0, 3, -1))
        assertEquals(0, cycledIndex(1, 3, -1))
    }

    @Test
    fun `single option always resolves to itself`() {
        assertEquals(0, cycledIndex(0, 1, 1))
        assertEquals(0, cycledIndex(0, 1, -1))
    }

    @Test
    fun `empty option list is guarded`() {
        assertEquals(0, cycledIndex(0, 0, 1))
    }
}
