package ru.aensidhe.dreamclock.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.aensidhe.dreamclock.R

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: SettingsRepository,
    scope: CoroutineScope,
    onTest: () -> Unit,
    onSetScreensaver: () -> Unit,
) {
    val settings by
        repository.settings.collectAsStateWithLifecycle(
            initialValue = SettingsSerializer.defaultValue,
        )
    val firstRow = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstRow.requestFocus() }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 48.dp, vertical = 27.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                )

                ToggleRow(firstRow, stringResource(R.string.settings_colloquial), settings.showColloquial) { on ->
                    scope.launch { repository.update { it.toBuilder().setShowColloquial(on).build() } }
                }
                ToggleRow(null, stringResource(R.string.settings_seconds), settings.showSeconds) { on ->
                    scope.launch { repository.update { it.toBuilder().setShowSeconds(on).build() } }
                }
                ToggleRow(null, stringResource(R.string.settings_analog), settings.showAnalogSlide) { on ->
                    scope.launch { repository.update { it.toBuilder().setShowAnalogSlide(on).build() } }
                }

                LanguageSection(settings.language) { option ->
                    scope.launch { repository.update { it.toBuilder().setLanguage(option).build() } }
                }

                ColorModeSection(settings.colorRenderMode) { option ->
                    scope.launch { repository.update { it.toBuilder().setColorRenderMode(option).build() } }
                }

                Row(
                    Modifier.padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Button(onClick = onTest) { Text(stringResource(R.string.action_test)) }
                    Button(onClick = onSetScreensaver) { Text(stringResource(R.string.action_set_screensaver)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LanguageSection(
    selected: Language,
    onSelect: (Language) -> Unit,
) {
    SectionHeader(stringResource(R.string.settings_language))
    Language.values().filter { it != Language.UNRECOGNIZED }.forEach { option ->
        SelectableRow(
            label = stringResource(languageLabel(option)),
            description = null,
            selected = option == selected,
        ) { onSelect(option) }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ColorModeSection(
    selected: ColorRenderModeProto,
    onSelect: (ColorRenderModeProto) -> Unit,
) {
    SectionHeader(stringResource(R.string.settings_render_mode))
    ColorRenderModeProto.values().filter { it != ColorRenderModeProto.UNRECOGNIZED }.forEach { option ->
        SelectableRow(
            label = stringResource(colorModeLabel(option)),
            description = stringResource(colorModeDescription(option)),
            selected = option == selected,
        ) { onSelect(option) }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ToggleRow(
    focusRequester: FocusRequester?,
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val modifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    ListItem(
        selected = false,
        onClick = { onToggle(!checked) },
        headlineContent = { Text(label) },
        trailingContent = { Text(stringResource(if (checked) R.string.state_on else R.string.state_off)) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SelectableRow(
    label: String,
    description: String?,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    ListItem(
        selected = selected,
        onClick = onSelect,
        headlineContent = { Text(label) },
        supportingContent = description?.let { desc -> { Text(desc) } },
        trailingContent = if (selected) ({ Text("✓") }) else null,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        Modifier.padding(top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleMedium,
    )
}
