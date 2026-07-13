package ru.aensidhe.dreamclock.dream

import java.time.LocalDateTime
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import ru.aensidhe.dreamclock.core.schedule.ScheduleEngine
import ru.aensidhe.dreamclock.core.schedule.StateType

class DefaultScheduleTest {
    private fun stateAt(
        hour: Int,
        minute: Int,
    ): StateType =
        ScheduleEngine
            .activeState(
                LocalDateTime.of(2026, 7, 13, hour, minute),
                defaultSchedule(),
            ).state

    @Test
    fun `sleeps in the early morning`() {
        assertEquals(StateType.SLEEP, stateAt(6, 0))
    }

    @Test
    fun `plays during the day`() {
        assertEquals(StateType.PLAY, stateAt(7, 30))
        assertEquals(StateType.PLAY, stateAt(12, 0))
    }

    @Test
    fun `prepares in the late evening`() {
        assertEquals(StateType.PREPARE, stateAt(21, 30))
    }

    @Test
    fun `sleeps at night`() {
        assertEquals(StateType.SLEEP, stateAt(22, 0))
        assertEquals(StateType.SLEEP, stateAt(23, 45))
    }
}
