package ru.aensidhe.dreamclock.settings

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
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
        val dreamSettings = Intent(SCREENSAVER_SETTINGS_ACTION)
        val daydreamActivity =
            Intent(Intent.ACTION_MAIN).setClassName(TV_SETTINGS_PACKAGE, DAYDREAM_ACTIVITY)
        val intent =
            when {
                intentAvailable(dreamSettings) -> dreamSettings
                intentAvailable(daydreamActivity) -> daydreamActivity
                else -> null
            }
        if (intent != null) {
            startActivity(intent)
        } else {
            showAdbInstructions()
        }
    }

    private fun intentAvailable(intent: Intent): Boolean = packageManager.queryIntentActivities(intent, 0).isNotEmpty()

    private fun showAdbInstructions() {
        val localized = localizedFor(currentLanguage)
        AlertDialog
            .Builder(this)
            .setTitle(localized.getString(R.string.screensaver_manual_title))
            .setMessage(localized.getString(R.string.screensaver_manual_message, ADB_SCREENSAVER_COMMAND))
            .setPositiveButton(localized.getString(R.string.action_close), null)
            .show()
    }
}
