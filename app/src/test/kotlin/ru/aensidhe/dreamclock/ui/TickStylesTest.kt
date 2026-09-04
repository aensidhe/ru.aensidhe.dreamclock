package ru.aensidhe.dreamclock.ui

import androidx.compose.ui.graphics.Color
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import ru.aensidhe.dreamclock.core.schedule.DaySchedule
import ru.aensidhe.dreamclock.core.schedule.Schedule
import ru.aensidhe.dreamclock.core.schedule.StateType
import ru.aensidhe.dreamclock.core.schedule.Window

class TickStylesTest {
    private val play = Color(0xFF7CB342)
    private val prepare = Color(0xFFFFB300)
    private val sleep = Color(0xFF5E35B1)

    private val schedule =
        Schedule(
            DaySchedule(
                listOf(
                    Window(LocalTime.MIDNIGHT, StateType.SLEEP),
                    Window(LocalTime.of(6, 0), StateType.PLAY),
                    Window(LocalTime.of(20, 0), StateType.PREPARE),
                    Window(LocalTime.of(21, 0), StateType.SLEEP),
                ),
            ),
        )

    private fun at(
        h: Int,
        mi: Int,
    ) = LocalDateTime.of(2026, 9, 5, h, mi)

    @Test
    fun `returns 60 entries`() {
        assertEquals(60, tickStyles(at(10, 0), schedule).size)
    }

    @Test
    fun `ticks 0-55 are solid in the current hour's colour`() {
        val styles = tickStyles(at(19, 10), schedule)
        for (i in 0..55) {
            assertEquals(play, styles[i].base)
            assertEquals(0f, styles[i].incomingFraction)
        }
    }

    @Test
    fun `tail ticks carry the next hour's colour with growing fractions`() {
        val styles = tickStyles(at(19, 10), schedule)
        val fractions = listOf(0.2f, 0.4f, 0.6f, 0.8f)
        for (i in 56..59) {
            assertEquals(play, styles[i].base)
            assertEquals(prepare, styles[i].incoming)
            assertEquals(fractions[i - 56], styles[i].incomingFraction)
        }
    }

    @Test
    fun `same-state hours make the tail invisible`() {
        val styles = tickStyles(at(10, 0), schedule)
        for (i in 56..59) {
            assertEquals(styles[i].base, styles[i].incoming)
        }
    }

    @Test
    fun `mid-hour edge splits the base colours at its minute`() {
        val day =
            DaySchedule(
                listOf(
                    Window(LocalTime.MIDNIGHT, StateType.SLEEP),
                    Window(LocalTime.of(20, 0), StateType.PREPARE),
                    Window(LocalTime.of(20, 30), StateType.SLEEP),
                ),
            )
        val styles = tickStyles(at(20, 45), Schedule(day))
        assertTrue((0..29).all { styles[it].base == prepare })
        assertTrue((30..55).all { styles[it].base == sleep })
    }
}
