package ru.aensidhe.dreamclock.dream

import android.service.dreams.DreamService
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
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
 * Screensaver entry point. Hosts a [ComposeView] rendering [DreamRoot], driven by
 * [ClockViewModel] (ticking clock state) and [SettingsRepository] (user preferences).
 *
 * [DreamService] is not a [LifecycleOwner] / [SavedStateRegistryOwner] / [ViewModelStoreOwner]
 * out of the box, but [ComposeView] requires all three to be present on the view tree. This
 * class supplies minimal implementations and drives them through the dream lifecycle.
 */
class TvDreamService :
    DreamService(),
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        isInteractive = false
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        setContentView(buildComposeView())
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        super.onDetachedFromWindow()
    }

    private fun buildComposeView(): View {
        val repository = SettingsRepository.from(this)
        val viewModel =
            ClockViewModel(
                scope = lifecycleScope,
                settingsFlow = repository.settings,
                schedule = defaultSchedule(),
                statusTextFor = ::statusTextFor,
            )
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@TvDreamService)
            setViewTreeSavedStateRegistryOwner(this@TvDreamService)
            setViewTreeViewModelStoreOwner(this@TvDreamService)
            setContent {
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
        }
    }

    private fun statusTextFor(state: StateType): String =
        when (state) {
            StateType.PLAY -> getString(R.string.status_play)
            StateType.PREPARE -> getString(R.string.status_prepare)
            StateType.SLEEP -> getString(R.string.status_sleep)
        }

    /**
     * No schedule-configuration UI exists yet (feature 1 scope only): a single all-day
     * PLAY window is the placeholder default until a schedule editor lands.
     */
    private fun defaultSchedule(): Schedule {
        val allDayPlay = Window(LocalTime.MIDNIGHT, StateType.PLAY)
        return Schedule(default = DaySchedule(listOf(allDayPlay)))
    }
}

private fun ColorRenderModeProto.toColorRenderMode(): ColorRenderMode =
    when (this) {
        ColorRenderModeProto.TEXT_TINT -> ColorRenderMode.TEXT_TINT
        ColorRenderModeProto.PANEL_TINT -> ColorRenderMode.PANEL_TINT
        ColorRenderModeProto.FULL_SCRIM -> ColorRenderMode.FULL_SCRIM
        ColorRenderModeProto.ACCENT -> ColorRenderMode.ACCENT
        ColorRenderModeProto.UNRECOGNIZED -> ColorRenderMode.TEXT_TINT
    }
