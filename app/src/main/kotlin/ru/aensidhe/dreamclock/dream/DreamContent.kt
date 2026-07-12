package ru.aensidhe.dreamclock.dream

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalTime
import ru.aensidhe.dreamclock.R
import ru.aensidhe.dreamclock.core.schedule.DaySchedule
import ru.aensidhe.dreamclock.core.schedule.Schedule
import ru.aensidhe.dreamclock.core.schedule.StateType
import ru.aensidhe.dreamclock.core.schedule.Window
import ru.aensidhe.dreamclock.settings.ColorRenderModeProto
import ru.aensidhe.dreamclock.settings.SettingsRepository
import ru.aensidhe.dreamclock.settings.SettingsSerializer
import ru.aensidhe.dreamclock.ui.ClockViewModel
import ru.aensidhe.dreamclock.ui.DreamRoot
import ru.aensidhe.dreamclock.ui.colorrender.ColorRenderMode

/**
 * No schedule-configuration UI exists yet (feature 1 scope only): a single all-day
 * PLAY window is the placeholder default until a schedule editor lands.
 */
internal fun defaultSchedule(): Schedule =
    Schedule(
        default = DaySchedule(listOf(Window(LocalTime.MIDNIGHT, StateType.PLAY))),
    )

internal fun statusTextFor(
    context: Context,
    state: StateType,
): String =
    when (state) {
        StateType.PLAY -> context.getString(R.string.status_play)
        StateType.PREPARE -> context.getString(R.string.status_prepare)
        StateType.SLEEP -> context.getString(R.string.status_sleep)
    }

internal fun ColorRenderModeProto.toColorRenderMode(): ColorRenderMode =
    when (this) {
        ColorRenderModeProto.TEXT_TINT -> ColorRenderMode.TEXT_TINT
        ColorRenderModeProto.PANEL_TINT -> ColorRenderMode.PANEL_TINT
        ColorRenderModeProto.FULL_SCRIM -> ColorRenderMode.FULL_SCRIM
        ColorRenderModeProto.ACCENT -> ColorRenderMode.ACCENT
        ColorRenderModeProto.UNRECOGNIZED -> ColorRenderMode.TEXT_TINT
    }

@Composable
internal fun DreamContent(
    viewModel: ClockViewModel,
    repository: SettingsRepository,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by
        repository.settings.collectAsStateWithLifecycle(
            initialValue = SettingsSerializer.defaultValue,
        )
    DreamRoot(
        state = uiState,
        showAnalog = settings.showAnalogSlide,
        mode = settings.colorRenderMode.toColorRenderMode(),
    )
}
