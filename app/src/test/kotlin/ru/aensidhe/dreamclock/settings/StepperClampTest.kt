package ru.aensidhe.dreamclock.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class StepperClampTest {
    @Test
    fun clampsWithinBounds() {
        assertEquals(0, clampStepper(-3, 0, 30))
        assertEquals(30, clampStepper(45, 0, 30))
        assertEquals(15, clampStepper(15, 0, 30))
    }
}
