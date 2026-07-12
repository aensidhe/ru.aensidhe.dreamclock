package ru.aensidhe.dreamclock.core.schedule

import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class ScheduleTest {
    @Test
    fun `day schedule sorts windows`() {
        val day =
            DaySchedule(
                listOf(
                    Window(LocalTime.of(20, 0), StateType.PREPARE),
                    Window(LocalTime.MIDNIGHT, StateType.SLEEP),
                    Window(LocalTime.of(7, 0), StateType.PLAY),
                ),
            )
        assertEquals(
            listOf(LocalTime.MIDNIGHT, LocalTime.of(7, 0), LocalTime.of(20, 0)),
            day.windows.map { it.start },
        )
    }

    @Test
    fun `day schedule requires midnight window`() {
        assertFailsWith<IllegalArgumentException> {
            DaySchedule(listOf(Window(LocalTime.of(7, 0), StateType.PLAY)))
        }
    }
}
