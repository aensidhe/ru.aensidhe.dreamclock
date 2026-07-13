package ru.aensidhe.dreamclock.ui

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.aensidhe.dreamclock.core.schedule.Schedule
import ru.aensidhe.dreamclock.core.schedule.ScheduleEngine
import ru.aensidhe.dreamclock.core.schedule.StateType
import ru.aensidhe.dreamclock.core.time.ClockLocale
import ru.aensidhe.dreamclock.core.time.colloquialFormatter
import ru.aensidhe.dreamclock.settings.Language
import ru.aensidhe.dreamclock.settings.Settings
import ru.aensidhe.dreamclock.settings.effectiveLocale

data class ClockUiState(
    val digital: String,
    val colloquial: String?,
    val statusText: String?,
    val state: StateType,
)

private val withSeconds = DateTimeFormatter.ofPattern("HH:mm:ss")
private val withoutSeconds = DateTimeFormatter.ofPattern("HH:mm")

fun buildClockUiState(
    now: LocalDateTime,
    settings: Settings,
    schedule: Schedule,
    systemLocale: Locale,
    statusTextFor: (Language, StateType) -> String,
): ClockUiState {
    val active = ScheduleEngine.activeState(now, schedule)
    val digital = now.format(if (settings.showSeconds) withSeconds else withoutSeconds)
    val colloquial =
        if (settings.showColloquial) {
            colloquialFormatter(clockLocale(settings.language, systemLocale)).format(now.hour, now.minute)
        } else {
            null
        }
    val status = active.textOverride ?: statusTextFor(settings.language, active.state)
    return ClockUiState(digital, colloquial, status, active.state)
}

private fun clockLocale(
    language: Language,
    systemLocale: Locale,
): ClockLocale = if (effectiveLocale(language, systemLocale).language == "ru") ClockLocale.RU else ClockLocale.EN

/**
 * Ticking clock view model: recomputes [ClockUiState] once per second from the
 * current settings/schedule, exposed as a [StateFlow] for Compose to collect.
 */
class ClockViewModel(
    scope: CoroutineScope,
    private val settingsFlow: Flow<Settings>,
    private val schedule: Schedule,
    private val statusTextFor: (Language, StateType) -> String,
    private val nowProvider: () -> LocalDateTime = LocalDateTime::now,
    private val systemLocale: Locale = Locale.getDefault(),
) {
    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<ClockUiState> = _uiState

    init {
        scope.launch {
            settingsFlow.collectLatest { settings ->
                while (true) {
                    _uiState.value = buildClockUiState(nowProvider(), settings, schedule, systemLocale, statusTextFor)
                    delay(TICK_INTERVAL_MS)
                }
            }
        }
    }

    private fun initialState(): ClockUiState =
        ClockUiState(
            digital = "",
            colloquial = null,
            statusText = null,
            state = StateType.PLAY,
        )

    private companion object {
        const val TICK_INTERVAL_MS = 1000L
    }
}
