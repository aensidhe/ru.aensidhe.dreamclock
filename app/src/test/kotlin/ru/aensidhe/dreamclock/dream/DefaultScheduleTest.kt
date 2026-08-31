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
    fun `sleeps from midnight until 06-00`() {
        assertEquals(StateType.SLEEP, stateAt(0, 0))
        assertEquals(StateType.SLEEP, stateAt(5, 59))
    }

    @Test
    fun `plays from 06-00 until 20-00`() {
        assertEquals(StateType.PLAY, stateAt(6, 0))
        assertEquals(StateType.PLAY, stateAt(19, 59))
    }

    @Test
    fun `prepares from 20-00 until 21-00`() {
        assertEquals(StateType.PREPARE, stateAt(20, 0))
        assertEquals(StateType.PREPARE, stateAt(20, 59))
    }

    @Test
    fun `sleeps from 21-00 until midnight`() {
        assertEquals(StateType.SLEEP, stateAt(21, 0))
        assertEquals(StateType.SLEEP, stateAt(23, 45))
    }
}
