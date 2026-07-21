package ru.aensidhe.dreamclock.dream

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import ru.aensidhe.dreamclock.R
import ru.aensidhe.dreamclock.core.photos.SlidePlanner
import ru.aensidhe.dreamclock.core.schedule.DaySchedule
import ru.aensidhe.dreamclock.core.schedule.Schedule
import ru.aensidhe.dreamclock.core.schedule.StateType
import ru.aensidhe.dreamclock.core.schedule.Window
import ru.aensidhe.dreamclock.immich.AssetLoad
import ru.aensidhe.dreamclock.immich.AssetPool
import ru.aensidhe.dreamclock.immich.CredentialsStore
import ru.aensidhe.dreamclock.immich.ImmichClient
import ru.aensidhe.dreamclock.immich.ImmichCredentials
import ru.aensidhe.dreamclock.immich.ImmichRepository
import ru.aensidhe.dreamclock.immich.NoCredentialsStore
import ru.aensidhe.dreamclock.immich.PhotoFetchConfig
import ru.aensidhe.dreamclock.immich.PhotoHistory
import ru.aensidhe.dreamclock.immich.PhotoHistoryProto
import ru.aensidhe.dreamclock.immich.PhotoHistoryStore
import ru.aensidhe.dreamclock.immich.SlideDriver
import ru.aensidhe.dreamclock.immich.SlideResolver
import ru.aensidhe.dreamclock.settings.ColorRenderModeProto
import ru.aensidhe.dreamclock.settings.Language
import ru.aensidhe.dreamclock.settings.Settings
import ru.aensidhe.dreamclock.settings.SettingsRepository
import ru.aensidhe.dreamclock.settings.SettingsSerializer
import ru.aensidhe.dreamclock.settings.clockLocale
import ru.aensidhe.dreamclock.settings.localizedFor
import ru.aensidhe.dreamclock.ui.ClockViewModel
import ru.aensidhe.dreamclock.ui.DreamRoot
import ru.aensidhe.dreamclock.ui.ImmichImageLoader
import ru.aensidhe.dreamclock.ui.SlideDeckModel
import ru.aensidhe.dreamclock.ui.colorrender.ColorRenderMode

/**
 * Baked default schedule until a schedule-editor UI lands. Four intervals span the full day:
 * 00:00–07:00 sleep, 07:00–21:00 play, 21:00–22:00 prepare for bed, 22:00–00:00 sleep.
 */
internal fun defaultSchedule(): Schedule =
    Schedule(
        default =
            DaySchedule(
                listOf(
                    Window(LocalTime.MIDNIGHT, StateType.SLEEP),
                    Window(LocalTime.of(7, 0), StateType.PLAY),
                    Window(LocalTime.of(21, 0), StateType.PREPARE),
                    Window(LocalTime.of(22, 0), StateType.SLEEP),
                ),
            ),
    )

internal fun statusTextFor(
    context: Context,
    state: StateType,
): String =
    when (state) {
        StateType.PLAY -> context.getString(R.string.status_play)
        StateType.PREPARE -> context.getString(R.string.status_prepare)
        StateType.SLEEP -> context.getString(R.string.status_sleep)
    }

/**
 * A status-text lookup that resolves strings in the app's chosen [Language]. The localized
 * context is rebuilt only when the language changes (collectLatest drives this single-threaded).
 */
internal fun Context.localizedStatusText(): (Language, StateType) -> String {
    var cachedLanguage: Language? = null
    var cachedContext: Context = this
    return { language, state ->
        if (language != cachedLanguage) {
            cachedLanguage = language
            cachedContext = localizedFor(language)
        }
        statusTextFor(cachedContext, state)
    }
}

internal fun ColorRenderModeProto.toColorRenderMode(): ColorRenderMode =
    when (this) {
        ColorRenderModeProto.TEXT_TINT -> ColorRenderMode.TEXT_TINT
        ColorRenderModeProto.PANEL_TINT -> ColorRenderMode.PANEL_TINT
        ColorRenderModeProto.FULL_SCRIM -> ColorRenderMode.FULL_SCRIM
        ColorRenderModeProto.ACCENT -> ColorRenderMode.ACCENT
        ColorRenderModeProto.UNRECOGNIZED -> ColorRenderMode.TEXT_TINT
    }

@Composable
internal fun DreamContent(
    viewModel: ClockViewModel,
    repository: SettingsRepository,
    credentialsStore: CredentialsStore = NoCredentialsStore,
    historyStore: PhotoHistoryStore? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by
        repository.settings.collectAsStateWithLifecycle(
            initialValue = SettingsSerializer.defaultValue,
        )
    val context = LocalContext.current
    val credentials =
        remember(credentialsStore, settings.immichHost, settings.immichKeyCiphertext) {
            credentialsStore.credentials(settings)
        }
    val httpClient = remember { OkHttpClient() }
    val imageLoader =
        remember(credentials) {
            credentials?.let { ImmichImageLoader.create(context, it.host, it.apiKey) }
        }
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalDateTime.now()
            val next = now.toLocalDate().plusDays(1).atStartOfDay()
            delay(Duration.between(now, next).toMillis().coerceAtLeast(1_000L))
            today = LocalDate.now()
        }
    }
    val deck by
        produceState<SlideDeckModel?>(
            initialValue = null,
            credentials,
            settings.immichHost,
            settings.immichKeyCiphertext,
            settings.photosEnabled,
            settings.daysEitherSide,
            settings.maxEmptyYearsBack,
            settings.language,
            today,
        ) {
            value = buildSlideDeck(credentials, settings, httpClient, today, historyStore)
        }
    DreamRoot(
        state = uiState,
        showAnalog = settings.showAnalogSlide,
        mode = settings.colorRenderMode.toColorRenderMode(),
        deck = deck,
        imageLoader = imageLoader,
        everyXthMinute = settings.shownEveryXthMinute,
        photoSeconds = settings.photoIntervalSeconds,
        analogSeconds = settings.analogSlideSeconds,
    )
}

private suspend fun buildSlideDeck(
    credentials: ImmichCredentials?,
    settings: Settings,
    httpClient: OkHttpClient,
    today: LocalDate,
    historyStore: PhotoHistoryStore?,
): SlideDeckModel? {
    if (credentials == null || !settings.photosEnabled) return null
    val repository =
        ImmichRepository(
            apiFactory = { host -> ImmichClient.api(host, httpClient) },
            today = { today },
            zone = ZoneId.systemDefault(),
        )
    val history = historyStore?.current() ?: PhotoHistoryProto.getDefaultInstance()
    val cachedOldestYear = PhotoHistory.oldestYear(history, credentials.host, today.year)
    val config = PhotoFetchConfig(settings.daysEitherSide, settings.maxEmptyYearsBack, cachedOldestYear)
    val load =
        runCatching { repository.loadAssets(credentials, config) }
            .getOrElse { if (it is CancellationException) throw it else AssetLoad(emptyList(), null) }
    if (load.assets.isEmpty()) return null
    val observed = load.oldestPopulatedYear
    if (observed != null) {
        historyStore?.update { PhotoHistory.withObservedOldestYear(it, credentials.host, observed) }
    }
    val locale = clockLocale(settings.language, Locale.getDefault())
    val pool = AssetPool(load.assets, Random(System.nanoTime()))
    val planner = SlidePlanner()
    val driver =
        SlideDriver(
            assets = pool.endlessSequence().iterator(),
            planner = planner,
            zone = ZoneId.systemDefault(),
        )
    val resolver = SlideResolver(credentials.host, load.assets.associate { it.id to it.caption }, locale)
    return SlideDeckModel(driver, resolver)
}
