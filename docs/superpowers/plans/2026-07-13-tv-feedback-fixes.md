# TV On-Device Feedback Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Address the first on-device feedback round: make the whole app follow the in-app Language selector, move the clock readouts to their intended positions and size, and replace the placeholder schedule with a real day/night schedule.

**Architecture:** Localization is delivered app-wide via a reactive locale-overridden `Context` derived from the `Language` value already stored in Proto DataStore (the single source of truth) — not AppCompat per-app locales, which do not localize a `DreamService` on Android 11 (the target hardware). The settings screen wraps its Compose tree in a localized `LocalContext`; the dream resolves its status text through a localized `Context` keyed on the current `Language`. The overlay layout and the baked schedule are plain data/UI changes.

**Tech Stack:** Kotlin 2.4.0, Jetpack Compose (compose-bom 2025.09.01 → Compose 1.9.2), androidx.tv:tv-material 1.0.1, Proto DataStore, JUnit5 (mannodermaus) + kotlin.test.

## Global Constraints

- Pinned stable Gradle-8 stack: compileSdk/targetSdk 36, minSdk 30, AGP 8.13.2, Gradle 8.14.5, JDK 21, detekt 1.23.8. Nothing may require compileSdk 37 / AGP 9.
- No new third-party dependencies. No AppCompat. Localization uses `Context.createConfigurationContext` only.
- `Language` is a protobuf-lite enum with a synthetic `UNRECOGNIZED` value; every `when` over it must handle `UNRECOGNIZED` (group it with `FOLLOW_SYSTEM` → no locale override).
- Do not touch the `:core` module, the colour renderers (`ColorRenderers.kt`), `StateColors.kt`, or the render-mode wiring. The render modes are correct; do not add a render-visibility change.
- All three clock readouts (digital, spoken, status) render at 24sp for now.
- Commits: Conventional Commits with a `:robot:` marker after the type; no Co-Authored-By trailer.
- Gate for every task: `./gradlew :app:testDebugUnitTest ktlintCheck detekt` green (add `:app:assembleDebug` where noted).

---

### Task 1: App language drives the settings screen and toast

**Files:**
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/Localization.kt`
- Create: `app/src/test/kotlin/ru/aensidhe/dreamclock/settings/LocalizationTest.kt`
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsScreen.kt`
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsActivity.kt`

**Interfaces:**
- Produces: `fun languageLocale(language: Language): Locale?` and `fun Context.localizedFor(language: Language): Context` (both in package `ru.aensidhe.dreamclock.settings`). Later tasks (dream status text) consume both.

- [ ] **Step 1: Write the failing test**

`app/src/test/kotlin/ru/aensidhe/dreamclock/settings/LocalizationTest.kt`:

```kotlin
package ru.aensidhe.dreamclock.settings

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class LocalizationTest {
    @Test
    fun `russian maps to ru locale`() {
        assertEquals("ru", languageLocale(Language.RU)?.language)
    }

    @Test
    fun `english maps to en locale`() {
        assertEquals("en", languageLocale(Language.EN)?.language)
    }

    @Test
    fun `follow system has no override`() {
        assertNull(languageLocale(Language.FOLLOW_SYSTEM))
    }

    @Test
    fun `unrecognized has no override`() {
        assertNull(languageLocale(Language.UNRECOGNIZED))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*LocalizationTest'`
Expected: FAIL — `languageLocale` unresolved.

- [ ] **Step 3: Create the localization helper**

`app/src/main/kotlin/ru/aensidhe/dreamclock/settings/Localization.kt`:

```kotlin
package ru.aensidhe.dreamclock.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** The locale to force for a given app [Language], or null to follow the system locale. */
fun languageLocale(language: Language): Locale? =
    when (language) {
        Language.RU -> Locale.forLanguageTag("ru")
        Language.EN -> Locale.ENGLISH
        Language.FOLLOW_SYSTEM, Language.UNRECOGNIZED -> null
    }

/** A copy of this context whose resources resolve in the app's chosen [language]. */
fun Context.localizedFor(language: Language): Context {
    val locale = languageLocale(language) ?: return this
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    return createConfigurationContext(config)
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*LocalizationTest'`
Expected: PASS (4 tests).

- [ ] **Step 5: Wrap the settings screen in the localized context**

In `SettingsScreen.kt`, add imports:

```kotlin
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
```

Then, inside `SettingsScreen`, after the `LaunchedEffect(Unit) { firstRow.requestFocus() }` line and before the `MaterialTheme(...)` call, derive the localized context and wrap the existing `MaterialTheme { Surface { ... } }` block in a `CompositionLocalProvider`. The body of `MaterialTheme` is unchanged; only its wrapping changes:

```kotlin
    val baseContext = LocalContext.current
    val localizedContext = remember(settings.language) { baseContext.localizedFor(settings.language) }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(Modifier.fillMaxSize()) {
                // ... existing Column body unchanged ...
            }
        }
    }
```

- [ ] **Step 6: Localize the screensaver-fallback toast**

In `SettingsActivity.kt`, track the current language and use it for the toast. Add import `ru.aensidhe.dreamclock.settings.Language` is not needed (same package). Add:

```kotlin
import kotlinx.coroutines.launch
```

Change the class so `repository` and the latest language are available to `openScreensaverSettings`:

```kotlin
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
```

- [ ] **Step 7: Run the gate**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug ktlintCheck detekt`
Expected: all green (`assembleDebug` confirms the Compose wrapping and imports compile; no compileSdk-37 metadata error).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/settings/Localization.kt \
        app/src/test/kotlin/ru/aensidhe/dreamclock/settings/LocalizationTest.kt \
        app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsScreen.kt \
        app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsActivity.kt
git commit -m "feat: :robot: settings screen and toast follow in-app language"
```

---

### Task 2: App language drives the dream status text

**Files:**
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/ClockViewModel.kt`
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamContent.kt`
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/dream/TvDreamService.kt`
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamPreviewActivity.kt`
- Modify: `app/src/test/kotlin/ru/aensidhe/dreamclock/ui/ClockFormattingTest.kt`

**Interfaces:**
- Consumes: `Context.localizedFor(Language)` and `languageLocale` from Task 1.
- Produces: `buildClockUiState` and `ClockViewModel` now take `statusTextFor: (Language, StateType) -> String`. `internal fun Context.localizedStatusText(): (Language, StateType) -> String` in the `dream` package.

- [ ] **Step 1: Update the failing test to the new callback shape**

In `ClockFormattingTest.kt`, both `buildClockUiState(...) { "спать" }` trailing lambdas take two args now. Change each `{ "спать" }` to `{ _, _ -> "спать" }`.

- [ ] **Step 2: Run the test to verify it fails to compile**

Run: `./gradlew :app:testDebugUnitTest --tests '*ClockFormattingTest'`
Expected: FAIL — argument type mismatch (`buildClockUiState` still takes `(StateType) -> String`).

- [ ] **Step 3: Thread `Language` through `buildClockUiState` and `ClockViewModel`**

In `ClockViewModel.kt`, change the `statusTextFor` parameter type in both `buildClockUiState` and the `ClockViewModel` constructor from `(StateType) -> String` to `(Language, StateType) -> String`, and change the call site inside `buildClockUiState`:

```kotlin
    val status = active.textOverride ?: statusTextFor(settings.language, active.state)
```

(`Language` is already imported in this file.)

- [ ] **Step 4: Add the localized status-text provider**

In `DreamContent.kt`, add imports:

```kotlin
import ru.aensidhe.dreamclock.settings.Language
import ru.aensidhe.dreamclock.settings.localizedFor
```

Keep the existing `statusTextFor(context, state)` helper. Add a provider that memoizes the localized context per language so it is not rebuilt every tick:

```kotlin
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
```

- [ ] **Step 5: Use the provider in both dream hosts**

In `TvDreamService.kt`, change the `ClockViewModel` construction argument:

```kotlin
                statusTextFor = localizedStatusText(),
```

In `DreamPreviewActivity.kt`, change the same argument:

```kotlin
                statusTextFor = localizedStatusText(),
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*ClockFormattingTest'`
Expected: PASS (2 tests).

- [ ] **Step 7: Run the gate**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug ktlintCheck detekt`
Expected: all green.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/ui/ClockViewModel.kt \
        app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamContent.kt \
        app/src/main/kotlin/ru/aensidhe/dreamclock/dream/TvDreamService.kt \
        app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamPreviewActivity.kt \
        app/src/test/kotlin/ru/aensidhe/dreamclock/ui/ClockFormattingTest.kt
git commit -m "feat: :robot: dream status text follows in-app language"
```

---

### Task 3: Bake the real default schedule

**Files:**
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamContent.kt`
- Create: `app/src/test/kotlin/ru/aensidhe/dreamclock/dream/DefaultScheduleTest.kt`

**Interfaces:**
- Consumes: `defaultSchedule()` (existing, `dream` package), `ScheduleEngine.activeState`, `StateType`.

Schedule to encode (a `DaySchedule` must have a 00:00 window; the engine picks the last window whose start ≤ time):
- 00:00 → Sleep (overnight tail of the 22:00–07:30 sleep window)
- 07:30 → Play
- 21:00 → Prepare
- 22:00 → Sleep

- [ ] **Step 1: Write the failing test**

`app/src/test/kotlin/ru/aensidhe/dreamclock/dream/DefaultScheduleTest.kt`:

```kotlin
package ru.aensidhe.dreamclock.dream

import java.time.LocalDateTime
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import ru.aensidhe.dreamclock.core.schedule.ScheduleEngine
import ru.aensidhe.dreamclock.core.schedule.StateType

class DefaultScheduleTest {
    private fun stateAt(
        hour: Int,
        minute: Int,
    ): StateType =
        ScheduleEngine.activeState(
            LocalDateTime.of(2026, 7, 13, hour, minute),
            defaultSchedule(),
        ).state

    @Test
    fun `sleeps in the early morning`() {
        assertEquals(StateType.SLEEP, stateAt(6, 0))
    }

    @Test
    fun `plays during the day`() {
        assertEquals(StateType.PLAY, stateAt(7, 30))
        assertEquals(StateType.PLAY, stateAt(12, 0))
    }

    @Test
    fun `prepares in the late evening`() {
        assertEquals(StateType.PREPARE, stateAt(21, 30))
    }

    @Test
    fun `sleeps at night`() {
        assertEquals(StateType.SLEEP, stateAt(22, 0))
        assertEquals(StateType.SLEEP, stateAt(23, 45))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*DefaultScheduleTest'`
Expected: FAIL — the all-day PLAY placeholder returns PLAY at 06:00.

- [ ] **Step 3: Replace `defaultSchedule()`**

In `DreamContent.kt`, replace the `defaultSchedule()` function (and its KDoc) with:

```kotlin
/**
 * Baked default schedule until a schedule-editor UI lands: play through the day, prepare for
 * bed at 21:00, sleep from 22:00 until 07:30. The 00:00 window carries the overnight sleep tail.
 */
internal fun defaultSchedule(): Schedule =
    Schedule(
        default =
            DaySchedule(
                listOf(
                    Window(LocalTime.MIDNIGHT, StateType.SLEEP),
                    Window(LocalTime.of(7, 30), StateType.PLAY),
                    Window(LocalTime.of(21, 0), StateType.PREPARE),
                    Window(LocalTime.of(22, 0), StateType.SLEEP),
                ),
            ),
    )
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*DefaultScheduleTest'`
Expected: PASS (4 tests).

- [ ] **Step 5: Run the gate**

Run: `./gradlew :app:testDebugUnitTest ktlintCheck detekt`
Expected: all green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamContent.kt \
        app/src/test/kotlin/ru/aensidhe/dreamclock/dream/DefaultScheduleTest.kt
git commit -m "feat: :robot: bake day/night default schedule (prepare 21:00, sleep 22:00-07:30)"
```

---

### Task 4: Reposition and resize the clock readouts

**Files:**
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/ClockOverlay.kt`

**Interfaces:**
- Consumes: `ClockUiState`, `ColorRenderMode.RenderOverlay` (unchanged).

Layout: digital time top-left at 24sp bold (always white); spoken time and status stacked bottom-centre at 24sp, still wrapped in `mode.RenderOverlay` so the colour-mode treatment is preserved. Screen padding stays 48dp.

- [ ] **Step 1: Replace `ClockOverlay`**

Replace the whole file body with:

```kotlin
package ru.aensidhe.dreamclock.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.aensidhe.dreamclock.ui.colorrender.ColorRenderMode
import ru.aensidhe.dreamclock.ui.colorrender.RenderOverlay

@Composable
fun ClockOverlay(
    ui: ClockUiState,
    mode: ColorRenderMode,
) {
    Box(Modifier.fillMaxSize().padding(48.dp)) {
        Text(
            ui.digital,
            Modifier.align(Alignment.TopStart),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Box(Modifier.align(Alignment.BottomCenter)) {
            mode.RenderOverlay(ui.state) { textColor ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ui.colloquial?.let { Text(it, color = textColor, fontSize = 24.sp) }
                    ui.statusText?.let { Text(it, color = textColor, fontSize = 24.sp) }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Run the gate**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug ktlintCheck detekt`
Expected: all green.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/ui/ClockOverlay.kt
git commit -m "feat: :robot: digital time top-left, spoken time bottom-centre, both 24sp"
```

---

### Task 5: One language-resolution rule shared by spoken time and resources (FOLLOW_SYSTEM merge blocker)

**Files:**
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/Localization.kt`
- Modify: `app/src/test/kotlin/ru/aensidhe/dreamclock/settings/LocalizationTest.kt`
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/ClockViewModel.kt`
- Modify: `app/src/test/kotlin/ru/aensidhe/dreamclock/ui/ClockFormattingTest.kt`

**Problem:** Under `FOLLOW_SYSTEM`, `ClockViewModel.clockLocale()` pins the colloquial spoken time to Russian (everything-but-EN → RU), while `Localization.languageLocale()` returns null (follow the real system locale → default English resources). On an English-locale TV left on `FOLLOW_SYSTEM` the spoken time renders Russian while the status text/labels render English — mixed languages.

**Fix:** Reverie supports Russian and English only. Introduce one resolver, `effectiveLocale(language, systemLocale)`, that maps RU→ru, EN→en, and FOLLOW_SYSTEM/UNRECOGNIZED→ru when the system locale is Russian else en. Both the resource localization and the colloquial formatter consume it, reading the system locale from `Locale.getDefault()` so they always agree.

**Interfaces:**
- Replaces `languageLocale(Language): Locale?` with `effectiveLocale(language: Language, systemLocale: Locale): Locale` (non-null; always ru or en).
- `Context.localizedFor(Language)` keeps its signature; internally uses `effectiveLocale(language, Locale.getDefault())`.
- `buildClockUiState` and `ClockViewModel` gain a `systemLocale: Locale` (ClockViewModel defaults it to `Locale.getDefault()`).

- [ ] **Step 1: Rewrite the localization unit test**

Replace the whole contents of `LocalizationTest.kt` with:

```kotlin
package ru.aensidhe.dreamclock.settings

import java.util.Locale
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class LocalizationTest {
    private val russian = Locale.forLanguageTag("ru")

    @Test
    fun `explicit russian resolves to ru`() {
        assertEquals("ru", effectiveLocale(Language.RU, Locale.ENGLISH).language)
    }

    @Test
    fun `explicit english resolves to en`() {
        assertEquals("en", effectiveLocale(Language.EN, russian).language)
    }

    @Test
    fun `follow system on a russian device resolves to ru`() {
        assertEquals("ru", effectiveLocale(Language.FOLLOW_SYSTEM, russian).language)
    }

    @Test
    fun `follow system on an english device resolves to en`() {
        assertEquals("en", effectiveLocale(Language.FOLLOW_SYSTEM, Locale.ENGLISH).language)
    }

    @Test
    fun `follow system on any other device falls back to en`() {
        assertEquals("en", effectiveLocale(Language.FOLLOW_SYSTEM, Locale.FRENCH).language)
    }

    @Test
    fun `unrecognized on a russian device resolves to ru`() {
        assertEquals("ru", effectiveLocale(Language.UNRECOGNIZED, russian).language)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*LocalizationTest'`
Expected: FAIL — `effectiveLocale` unresolved.

- [ ] **Step 3: Replace the resolver in `Localization.kt`**

Replace the whole file with:

```kotlin
package ru.aensidhe.dreamclock.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

private val RUSSIAN: Locale = Locale.forLanguageTag("ru")

/**
 * Reverie ships Russian and English only. FOLLOW_SYSTEM (and the synthetic protobuf UNRECOGNIZED)
 * resolve to Russian when the system locale is Russian, otherwise English — so the spoken time,
 * status text and settings labels always agree on one language.
 */
fun effectiveLocale(
    language: Language,
    systemLocale: Locale,
): Locale =
    when (language) {
        Language.RU -> RUSSIAN
        Language.EN -> Locale.ENGLISH
        Language.FOLLOW_SYSTEM, Language.UNRECOGNIZED ->
            if (systemLocale.language == RUSSIAN.language) RUSSIAN else Locale.ENGLISH
    }

/** A copy of this context whose resources resolve in the app's effective language. */
fun Context.localizedFor(language: Language): Context {
    val config = Configuration(resources.configuration)
    config.setLocale(effectiveLocale(language, Locale.getDefault()))
    return createConfigurationContext(config)
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*LocalizationTest'`
Expected: PASS (6 tests).

- [ ] **Step 5: Update the clock-formatting test for the new signature and add FOLLOW_SYSTEM coverage**

In `ClockFormattingTest.kt`: add `import java.util.Locale` and `import kotlin.test.assertNotEquals`. Add a fourth positional argument `Locale.ENGLISH` to the two existing `buildClockUiState(...)` calls (immediately after `schedule`, before the trailing lambda). Then add this test method:

```kotlin
    @Test
    fun `follow system picks colloquial language from the system locale`() {
        val settings =
            Settings
                .newBuilder()
                .setLanguage(Language.FOLLOW_SYSTEM)
                .setShowColloquial(true)
                .setShowSeconds(false)
                .build()
        val now = LocalDateTime.of(2026, 7, 13, 21, 45, 5)
        val ru = buildClockUiState(now, settings, schedule, Locale.forLanguageTag("ru")) { _, _ -> "" }.colloquial
        val en = buildClockUiState(now, settings, schedule, Locale.ENGLISH) { _, _ -> "" }.colloquial
        assertEquals("без четверти десять", ru)
        assertNotEquals(ru, en)
    }
```

- [ ] **Step 6: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*ClockFormattingTest'`
Expected: FAIL — `buildClockUiState` still has the 3-arg-plus-lambda signature.

- [ ] **Step 7: Thread `systemLocale` through `ClockViewModel.kt`**

Add imports `import java.util.Locale` and `import ru.aensidhe.dreamclock.settings.effectiveLocale`. Add `systemLocale: Locale` to `buildClockUiState` (after `schedule`, before `statusTextFor`) and use it for the colloquial locale; remove the old `Settings.clockLocale()` extension and add a `clockLocale(language, systemLocale)` helper built on `effectiveLocale`:

```kotlin
fun buildClockUiState(
    now: LocalDateTime,
    settings: Settings,
    schedule: Schedule,
    systemLocale: Locale,
    statusTextFor: (Language, StateType) -> String,
): ClockUiState {
    val active = ScheduleEngine.activeState(now, schedule)
    val digital = now.format(if (settings.showSeconds) withSeconds else withoutSeconds)
    val colloquial =
        if (settings.showColloquial) {
            colloquialFormatter(clockLocale(settings.language, systemLocale)).format(now.hour, now.minute)
        } else {
            null
        }
    val status = active.textOverride ?: statusTextFor(settings.language, active.state)
    return ClockUiState(digital, colloquial, status, active.state)
}

private fun clockLocale(
    language: Language,
    systemLocale: Locale,
): ClockLocale = if (effectiveLocale(language, systemLocale).language == "ru") ClockLocale.RU else ClockLocale.EN
```

In the `ClockViewModel` constructor, add a defaulted parameter `private val systemLocale: Locale = Locale.getDefault(),` (after `nowProvider`), and pass it in the tick call:

```kotlin
                    _uiState.value = buildClockUiState(nowProvider(), settings, schedule, systemLocale, statusTextFor)
```

- [ ] **Step 8: Run the clock-formatting test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*ClockFormattingTest'`
Expected: PASS (3 tests).

- [ ] **Step 9: Run the full gate**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug ktlintCheck detekt`
Expected: all green. (`assembleDebug` confirms `localizedFor`'s new body and the ViewModel wiring compile; the dream hosts construct `ClockViewModel` with named args so the new defaulted parameter needs no change there.)

- [ ] **Step 10: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/settings/Localization.kt \
        app/src/test/kotlin/ru/aensidhe/dreamclock/settings/LocalizationTest.kt \
        app/src/main/kotlin/ru/aensidhe/dreamclock/ui/ClockViewModel.kt \
        app/src/test/kotlin/ru/aensidhe/dreamclock/ui/ClockFormattingTest.kt
git commit -m "fix: :robot: resolve FOLLOW_SYSTEM to one language for spoken time and resources"
```

---

## Owner on-device verification (not a task)

No instrumented tests in this environment. After merge, verify on the TV: switching Language re-renders the settings screen labels and the evening status text; the digital clock sits top-left and the spoken line bottom-centre; the colour modes visibly differ once the state changes to Prepare (amber) at 21:00 and Sleep (purple) at 22:00.
