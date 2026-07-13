package ru.aensidhe.dreamclock.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import ru.aensidhe.dreamclock.R
import ru.aensidhe.dreamclock.dream.DreamPreviewActivity

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = SettingsRepository.from(this)
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
            Toast.makeText(this, R.string.settings_screensaver_fallback, Toast.LENGTH_LONG).show()
        }
    }
}
