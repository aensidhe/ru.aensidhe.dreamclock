package ru.aensidhe.dreamclock.dream

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import ru.aensidhe.dreamclock.settings.SettingsRepository
import ru.aensidhe.dreamclock.ui.ClockViewModel

/**
 * Fullscreen in-app preview of the screensaver (the Settings "Test" button). Renders the
 * same [DreamContent] as [TvDreamService] with the current settings, and dismisses on any
 * remote key so it behaves like a real screensaver.
 */
class DreamPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val repository = SettingsRepository.from(this)
        val viewModel =
            ClockViewModel(
                scope = lifecycleScope,
                settingsFlow = repository.settings,
                schedule = defaultSchedule(),
                statusTextFor = localizedStatusText(),
            )
        setContent { DreamContent(viewModel, repository) }
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        finish()
        return true
    }
}
