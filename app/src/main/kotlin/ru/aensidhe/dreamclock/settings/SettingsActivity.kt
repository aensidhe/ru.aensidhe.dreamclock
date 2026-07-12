package ru.aensidhe.dreamclock.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.aensidhe.dreamclock.R

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = SettingsRepository.from(this)
        setContent {
            MaterialTheme {
                val settings by repo.settings.collectAsStateWithLifecycle(
                    initialValue = SettingsSerializer.defaultValue,
                )
                Column(Modifier.padding(32.dp)) {
                    ToggleRow(getString(R.string.settings_colloquial), settings.showColloquial) { on ->
                        lifecycleScope.launch { repo.update { it.toBuilder().setShowColloquial(on).build() } }
                    }
                    ToggleRow(getString(R.string.settings_seconds), settings.showSeconds) { on ->
                        lifecycleScope.launch { repo.update { it.toBuilder().setShowSeconds(on).build() } }
                    }
                    ToggleRow(getString(R.string.settings_analog), settings.showAnalogSlide) { on ->
                        lifecycleScope.launch { repo.update { it.toBuilder().setShowAnalogSlide(on).build() } }
                    }
                    LanguageSelector(settings.language) { language ->
                        lifecycleScope.launch { repo.update { it.toBuilder().setLanguage(language).build() } }
                    }
                    RenderModeSelector(settings.colorRenderMode) { mode ->
                        lifecycleScope.launch { repo.update { it.toBuilder().setColorRenderMode(mode).build() } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(label, Modifier.padding(end = 16.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LanguageSelector(
    selected: Language,
    onSelect: (Language) -> Unit,
) {
    Column(Modifier.padding(vertical = 12.dp)) {
        Text(stringResource(R.string.settings_language))
        Language.values().filter { it != Language.UNRECOGNIZED }.forEach { option ->
            OptionRow(option.name, option == selected) { onSelect(option) }
        }
    }
}

@Composable
private fun RenderModeSelector(
    selected: ColorRenderModeProto,
    onSelect: (ColorRenderModeProto) -> Unit,
) {
    Column(Modifier.padding(vertical = 12.dp)) {
        Text(stringResource(R.string.settings_render_mode))
        ColorRenderModeProto.values().filter { it != ColorRenderModeProto.UNRECOGNIZED }.forEach { option ->
            OptionRow(option.name, option == selected) { onSelect(option) }
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, Modifier.padding(start = 8.dp))
    }
}
