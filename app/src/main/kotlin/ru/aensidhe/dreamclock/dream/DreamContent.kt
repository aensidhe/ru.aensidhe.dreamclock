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
import ru.aensidhe.dreamclock.settings.Language
import ru.aensidhe.dreamclock.settings.SettingsRepository
import ru.aensidhe.dreamclock.settings.SettingsSerializer
import ru.aensidhe.dreamclock.settings.localizedFor
import ru.aensidhe.dreamclock.ui.ClockViewModel
import ru.aensidhe.dreamclock.ui.DreamRoot
import ru.aensidhe.dreamclock.ui.colorrender.ColorRenderMode

/**
 * Baked default schedule until a schedule-editor UI lands: play through the day, prepare for
 * bed at 21:00, sleep from 22:00 until 07:30. The 00:00 window carries the overnight sleep tail.
 */
internal fun defaultSchedule(): Schedule =
    Schedule(
        default =
            DaySchedule(
                listOf(
                    Window(LocalTime.MIDNIGHT, StateType.SLEEP),
                    Window(LocalTime.of(7, 30), StateType.PLAY),
                    Window(LocalTime.of(21, 0), StateType.PREPARE),
                    Window(LocalTime.of(22, 0), StateType.SLEEP),
                ),
            ),
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

/**
 * A status-text lookup that resolves strings in the app's chosen [Language]. The localized
 * context is rebuilt only when the language changes (collectLatest drives this single-threaded).
 */
internal fun Context.localizedStatusText(): (Language, StateType) -> String {
    var cachedLanguage: Language? = null
    var cachedContext: Context = this
    return { language, state ->
        if (language != cachedLanguage) {
            cachedLanguage = language
            cachedContext = localizedFor(language)
        }
        statusTextFor(cachedContext, state)
    }
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
