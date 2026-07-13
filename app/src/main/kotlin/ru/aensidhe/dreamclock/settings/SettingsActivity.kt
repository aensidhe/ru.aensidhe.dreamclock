package ru.aensidhe.dreamclock.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.aensidhe.dreamclock.R
import ru.aensidhe.dreamclock.dream.DreamPreviewActivity

class SettingsActivity : ComponentActivity() {
    private val repository by lazy { SettingsRepository.from(this) }
    private var currentLanguage: Language = Language.FOLLOW_SYSTEM

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repository.settings.collect { currentLanguage = it.language }
        }
        setContent {
            SettingsScreen(
                repository = repository,
                scope = lifecycleScope,
                onTest = { startActivity(Intent(this, DreamPreviewActivity::class.java)) },
                onSetScreensaver = ::openScreensaverSettings,
            )
        }
    }

    private fun openScreensaverSettings() {
        val intent = Intent(SCREENSAVER_SETTINGS_ACTION)
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            startActivity(Intent(Settings.ACTION_SETTINGS))
            val message = localizedFor(currentLanguage).getString(R.string.settings_screensaver_fallback)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }
}
