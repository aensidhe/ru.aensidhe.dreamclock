package ru.aensidhe.dreamclock.dream

import android.service.dreams.DreamService
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import ru.aensidhe.dreamclock.settings.SettingsRepository
import ru.aensidhe.dreamclock.ui.ClockViewModel

/**
 * Screensaver entry point. Hosts a [ComposeView] rendering the shared [DreamContent].
 *
 * [DreamService] is not a [LifecycleOwner] / [SavedStateRegistryOwner] / [ViewModelStoreOwner]
 * out of the box, but [ComposeView] requires all three on the view tree. This class supplies
 * minimal implementations and drives them through the dream lifecycle.
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
                statusTextFor = localizedStatusText(),
            )
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@TvDreamService)
            setViewTreeSavedStateRegistryOwner(this@TvDreamService)
            setViewTreeViewModelStoreOwner(this@TvDreamService)
            setContent { DreamContent(viewModel, repository) }
        }
    }
}
