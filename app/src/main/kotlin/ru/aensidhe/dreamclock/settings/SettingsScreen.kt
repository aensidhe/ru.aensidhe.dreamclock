package ru.aensidhe.dreamclock.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import com.google.protobuf.ByteString
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.aensidhe.dreamclock.R
import ru.aensidhe.dreamclock.core.photos.SimilarTimeWindows
import ru.aensidhe.dreamclock.immich.ImmichClient
import ru.aensidhe.dreamclock.immich.ImmichHealth
import ru.aensidhe.dreamclock.immich.KeyCipher
import ru.aensidhe.dreamclock.immich.KeystoreCipher
import ru.aensidhe.dreamclock.immich.PhotoHistory
import ru.aensidhe.dreamclock.immich.PhotoHistoryStore
import ru.aensidhe.dreamclock.immich.ProbeResult

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: SettingsRepository,
    scope: CoroutineScope,
    onTest: () -> Unit,
    onSetScreensaver: () -> Unit,
    historyStore: PhotoHistoryStore,
    cipher: KeyCipher = KeystoreCipher(),
) {
    val settings by
        repository.settings.collectAsStateWithLifecycle(
            initialValue = SettingsSerializer.defaultValue,
        )
    val firstRow = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstRow.requestFocus() }

    val baseContext = LocalContext.current
    val localizedContext = remember(settings.language) { baseContext.localizedFor(settings.language) }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
    ) {
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

                    ToggleRow(
                        null,
                        stringResource(R.string.settings_advanced_debugging),
                        settings.advancedDebugging,
                    ) { on ->
                        scope.launch { repository.update { it.toBuilder().setAdvancedDebugging(on).build() } }
                    }

                    ImmichSection(settings, cipher, repository, scope, historyStore)

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

private const val KEY_PLACEHOLDER = "••••••"

private class ImmichStepper(
    val labelRes: Int,
    val value: Int,
    val min: Int,
    val max: Int,
    val setter: (Settings.Builder, Int) -> Settings.Builder,
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ImmichSection(
    settings: Settings,
    cipher: KeyCipher,
    repository: SettingsRepository,
    scope: CoroutineScope,
    historyStore: PhotoHistoryStore,
) {
    SectionHeader(stringResource(R.string.settings_immich_section))
    ToggleRow(null, stringResource(R.string.settings_immich_enable), settings.photosEnabled) { on ->
        scope.launch { repository.update { it.toBuilder().setPhotosEnabled(on).build() } }
    }
    if (!settings.photosEnabled) return

    TextFieldRow(
        label = stringResource(R.string.settings_immich_host),
        value = settings.immichHost,
        isSecret = false,
    ) { text ->
        scope.launch { repository.update { it.toBuilder().setImmichHost(text.trim()).build() } }
    }

    val keyDisplay = if (!settings.immichKeyCiphertext.isEmpty) KEY_PLACEHOLDER else ""
    TextFieldRow(
        label = stringResource(R.string.settings_immich_key),
        value = keyDisplay,
        isSecret = true,
    ) { text ->
        if (text.isBlank() || text == KEY_PLACEHOLDER) return@TextFieldRow
        scope.launch {
            val blob = withContext(Dispatchers.Default) { cipher.encrypt(text) }
            repository.update { it.toBuilder().setImmichKeyCiphertext(ByteString.copyFrom(blob)).build() }
        }
    }
    if (!settings.immichKeyCiphertext.isEmpty) {
        Button(
            onClick = {
                scope.launch { repository.update { it.toBuilder().clearImmichKeyCiphertext().build() } }
            },
        ) { Text(stringResource(R.string.settings_immich_clear_key)) }
    }

    val steppers =
        listOf(
            ImmichStepper(R.string.settings_days_either_side, settings.daysEitherSide, 0, 30) { b, v ->
                b.setDaysEitherSide(v)
            },
            ImmichStepper(R.string.settings_max_empty_years_back, settings.maxEmptyYearsBack, 1, 50) { b, v ->
                b.setMaxEmptyYearsBack(v)
            },
            ImmichStepper(R.string.settings_photo_interval, settings.photoIntervalSeconds, 3, 60) { b, v ->
                b.setPhotoIntervalSeconds(v)
            },
            ImmichStepper(R.string.settings_shown_every_xth_minute, settings.shownEveryXthMinute, 1, 60) { b, v ->
                b.setShownEveryXthMinute(v)
            },
            ImmichStepper(R.string.settings_analog_slide_seconds, settings.analogSlideSeconds, 3, 60) { b, v ->
                b.setAnalogSlideSeconds(v)
            },
        )
    steppers.forEach { spec ->
        StepperRow(
            label = stringResource(spec.labelRes),
            value = spec.value,
            min = spec.min,
            max = spec.max,
            step = 1,
        ) { newValue ->
            scope.launch { repository.update { spec.setter(it.toBuilder(), newValue).build() } }
        }
    }

    ImmichConnectionTest(settings, cipher, historyStore, scope)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ImmichConnectionTest(
    settings: Settings,
    cipher: KeyCipher,
    historyStore: PhotoHistoryStore,
    scope: CoroutineScope,
) {
    var status by remember { mutableStateOf<ProbeResult?>(null) }
    val statusContext = LocalContext.current

    Button(
        onClick = {
            scope.launch {
                status = ProbeResult.Checking
                val result =
                    runCatching {
                        withContext(Dispatchers.Default) {
                            val api = ImmichClient.api(settings.immichHost)
                            val apiKey = cipher.decrypt(settings.immichKeyCiphertext.toByteArray())
                            val window = SimilarTimeWindows.windowFor(LocalDate.now(), settings.daysEitherSide, 0)
                            ImmichHealth.probe(api, apiKey, window, ZoneId.systemDefault())
                        }
                    }.getOrElse {
                        if (it is CancellationException) throw it
                        ProbeResult.Error(ImmichHealth.truncateDetail(it.message ?: it.javaClass.simpleName))
                    }
                if (result is ProbeResult.Reachable) {
                    historyStore.update { PhotoHistory.resetOnHostChange(it, settings.immichHost) }
                }
                status = result
            }
        },
        enabled = settings.immichHost.isNotBlank() && !settings.immichKeyCiphertext.isEmpty,
    ) { Text(stringResource(R.string.settings_immich_test)) }

    status?.let { Text(probeStatusLabel(statusContext, it)) }
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
