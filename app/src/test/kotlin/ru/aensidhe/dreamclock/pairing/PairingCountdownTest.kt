package ru.aensidhe.dreamclock.pairing

import kotlin.test.Test
import kotlin.test.assertEquals

class PairingCountdownTest {
    @Test
    fun formats_minutes_and_zero_padded_seconds() {
        assertEquals("4:32", PairingCountdown.format(272))
        assertEquals("0:05", PairingCountdown.format(5))
        assertEquals("0:00", PairingCountdown.format(0))
    }
}
