# TV-Remote Settings GUI + Icon Set Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the phone-style settings screen with a D-pad-navigable Android TV GUI, add Test-screensaver and Set-as-screensaver actions, and add an app icon set (adaptive launcher icon + TV banner).

**Architecture:** The settings UI is rebuilt on Compose-for-TV (`androidx.tv:tv-material`) with focusable rows, a dark ambient theme, and friendly localized labels. The dream's Compose wiring is factored into a shared `DreamContent` reused by both the screensaver service and a new in-app preview activity (the Test button). Icons are hand-authored vector drawables plus one generated banner PNG.

**Tech Stack:** Kotlin 2.4.0, Jetpack Compose (compose-bom 2025.09.01 → Compose 1.9.2), `androidx.tv:tv-material:1.0.1`, Proto DataStore, AGP 8.13.2 / Gradle 8.14.5 / JDK 21, JUnit5 (mannodermaus on Android), detekt 1.23.8, ktlint-gradle 14.2.0.

## Global Constraints

- Stay on the pinned stable-Gradle-8 stack: compileSdk/targetSdk 36, minSdk 30, AGP 8.13.2. Nothing may require compileSdk 37 or AGP 9.x.
- Use `androidx.tv:tv-material` version exactly `1.0.1` (verified compatible; 1.1.0 pulls Compose 1.10.3 / compileSdk 37 and is forbidden).
- No phone/tablet layout. TV-only, remote-driven. Every control must be D-pad reachable and activatable, with visible focus.
- No raw enum names in the UI. Language and Colour-style options use friendly, localized labels (EN + RU).
- The five-stop icon palette is icon/banner-only: teal `#1FA9A0`, soft green `#9FC97C`, warm amber `#F5B342`, rose-brown `#B07C6E`, deep plum `#4B2A4A`. Do not change the clock's three schedule-state colours in `StateColors.kt`.
- Do not touch the pure-logic `:core` module or the dream rendering (`DreamRoot`, `SlideDeck`, `ClockOverlay`, colour renderers).
- Shell rule (from CLAUDE.md): prefer dedicated file/Serena tools over shell one-liners; no inline Bash relying on escaping, expansion, heredocs, or here-strings. A genuinely required script is written to a file under `tmp/` (gitignored) and run from there. The banner generator is a committed `tools/` script, not an inline command.
- Commits: Conventional Commits with a `:robot:` marker after the type (e.g. `feat: :robot: …`). No `Co-Authored-By` trailer.
- Markdown in any docs: no bold/italic for inline emphasis; structural markup only.
- Kotlin unit tests use JUnit5 (`org.junit.jupiter.api.Test`) with `kotlin.test.assertEquals`, run via `:app:testDebugUnitTest`.

## File Structure

New:
- `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsLabels.kt` — pure enum→string-resource label mappings + the screensaver settings action constant.
- `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsScreen.kt` — the tv-material3 settings screen composable and its row helpers.
- `app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamContent.kt` — shared dream wiring (schedule default, status-text lookup, proto→render-mode mapping, the `DreamContent` composable).
- `app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamPreviewActivity.kt` — fullscreen in-app preview of the dream (Test button target).
- `app/src/test/kotlin/ru/aensidhe/dreamclock/settings/SettingsLabelsTest.kt`
- `app/src/test/kotlin/ru/aensidhe/dreamclock/dream/DreamContentTest.kt`
- `app/src/main/res/drawable/ic_launcher_background.xml`, `app/src/main/res/drawable/ic_launcher_foreground.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- `app/src/main/res/drawable-xhdpi/tv_banner.png` (generated)
- `tools/generate_banner.py` — Pillow banner generator (build-time asset tool).

Modified:
- `gradle/libs.versions.toml` — add tv-material version + library alias.
- `app/build.gradle.kts` — add the tv-material dependency.
- `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsActivity.kt` — host `SettingsScreen`, wire the two buttons.
- `app/src/main/kotlin/ru/aensidhe/dreamclock/dream/TvDreamService.kt` — use shared `DreamContent`; drop its private duplicates.
- `app/src/main/res/values/strings_settings.xml` + `app/src/main/res/values-ru/strings_settings.xml` — friendly labels, descriptions, button and state strings, fallback toast.
- `app/src/main/AndroidManifest.xml` — register the preview activity; add `android:icon`, `android:roundIcon`, `android:banner`.

---

### Task 1: Friendly localized labels + pure mapping

**Files:**
- Modify: `app/src/main/res/values/strings_settings.xml`
- Modify: `app/src/main/res/values-ru/strings_settings.xml`
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsLabels.kt`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/settings/SettingsLabelsTest.kt`

**Interfaces:**
- Consumes: `Language` and `ColorRenderModeProto` (generated protobuf-lite enums in package `ru.aensidhe.dreamclock.settings`); `ru.aensidhe.dreamclock.R`.
- Produces:
  - `@StringRes fun languageLabel(language: Language): Int`
  - `@StringRes fun colorModeLabel(mode: ColorRenderModeProto): Int`
  - `@StringRes fun colorModeDescription(mode: ColorRenderModeProto): Int`
  - `const val SCREENSAVER_SETTINGS_ACTION: String = "android.settings.DREAM_SETTINGS"`

- [ ] **Step 1: Add English strings**

Replace `app/src/main/res/values/strings_settings.xml` with:

```xml
<resources>
    <string name="settings_colloquial">Spoken time</string>
    <string name="settings_seconds">Show seconds</string>
    <string name="settings_analog">Analog clock slide</string>
    <string name="settings_language">Language</string>
    <string name="settings_render_mode">Colour style</string>

    <string name="lang_follow_system">Follow system</string>
    <string name="lang_ru">Русский</string>
    <string name="lang_en">English</string>

    <string name="render_text_tint_label">Coloured text</string>
    <string name="render_text_tint_desc">The clock text takes the state colour</string>
    <string name="render_panel_tint_label">Soft panel</string>
    <string name="render_panel_tint_desc">Light text on a translucent coloured card</string>
    <string name="render_full_scrim_label">Full tint</string>
    <string name="render_full_scrim_desc">Light text over a full-screen colour wash</string>
    <string name="render_accent_label">Bold badge</string>
    <string name="render_accent_desc">Light text on a near-solid colour block</string>

    <string name="state_on">On</string>
    <string name="state_off">Off</string>

    <string name="action_test">Test screensaver</string>
    <string name="action_set_screensaver">Set as screensaver</string>
    <string name="settings_screensaver_fallback">Open Settings › Device Preferences › Screen saver to choose Reverie</string>
</resources>
```

- [ ] **Step 2: Add Russian strings**

Replace `app/src/main/res/values-ru/strings_settings.xml` with:

```xml
<resources>
    <string name="settings_colloquial">Время прописью</string>
    <string name="settings_seconds">Показывать секунды</string>
    <string name="settings_analog">Слайд с часами</string>
    <string name="settings_language">Язык</string>
    <string name="settings_render_mode">Стиль цвета</string>

    <string name="lang_follow_system">Как в системе</string>
    <string name="lang_ru">Русский</string>
    <string name="lang_en">English</string>

    <string name="render_text_tint_label">Цветной текст</string>
    <string name="render_text_tint_desc">Текст часов окрашивается в цвет состояния</string>
    <string name="render_panel_tint_label">Мягкая панель</string>
    <string name="render_panel_tint_desc">Светлый текст на полупрозрачной цветной карточке</string>
    <string name="render_full_scrim_label">Полная заливка</string>
    <string name="render_full_scrim_desc">Светлый текст поверх цветной заливки экрана</string>
    <string name="render_accent_label">Яркий значок</string>
    <string name="render_accent_desc">Светлый текст на почти сплошном цветном блоке</string>

    <string name="state_on">Вкл</string>
    <string name="state_off">Выкл</string>

    <string name="action_test">Проверить заставку</string>
    <string name="action_set_screensaver">Выбрать заставку</string>
    <string name="settings_screensaver_fallback">Откройте Настройки › Настройки устройства › Заставка и выберите Грёзы</string>
</resources>
```

- [ ] **Step 3: Write the failing test**

Create `app/src/test/kotlin/ru/aensidhe/dreamclock/settings/SettingsLabelsTest.kt`:

```kotlin
package ru.aensidhe.dreamclock.settings

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import ru.aensidhe.dreamclock.R

class SettingsLabelsTest {
    @Test
    fun `language labels map to the right resources`() {
        assertEquals(R.string.lang_follow_system, languageLabel(Language.FOLLOW_SYSTEM))
        assertEquals(R.string.lang_ru, languageLabel(Language.RU))
        assertEquals(R.string.lang_en, languageLabel(Language.EN))
    }

    @Test
    fun `colour mode labels map to the right resources`() {
        assertEquals(R.string.render_text_tint_label, colorModeLabel(ColorRenderModeProto.TEXT_TINT))
        assertEquals(R.string.render_panel_tint_label, colorModeLabel(ColorRenderModeProto.PANEL_TINT))
        assertEquals(R.string.render_full_scrim_label, colorModeLabel(ColorRenderModeProto.FULL_SCRIM))
        assertEquals(R.string.render_accent_label, colorModeLabel(ColorRenderModeProto.ACCENT))
    }

    @Test
    fun `colour mode descriptions map to the right resources`() {
        assertEquals(R.string.render_text_tint_desc, colorModeDescription(ColorRenderModeProto.TEXT_TINT))
        assertEquals(R.string.render_panel_tint_desc, colorModeDescription(ColorRenderModeProto.PANEL_TINT))
        assertEquals(R.string.render_full_scrim_desc, colorModeDescription(ColorRenderModeProto.FULL_SCRIM))
        assertEquals(R.string.render_accent_desc, colorModeDescription(ColorRenderModeProto.ACCENT))
    }

    @Test
    fun `screensaver settings action is the platform constant`() {
        assertEquals("android.settings.DREAM_SETTINGS", SCREENSAVER_SETTINGS_ACTION)
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.aensidhe.dreamclock.settings.SettingsLabelsTest"`
Expected: FAIL — compilation error, unresolved reference `languageLabel` / `colorModeLabel` / `colorModeDescription` / `SCREENSAVER_SETTINGS_ACTION`.

- [ ] **Step 5: Implement the mapping**

Create `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsLabels.kt`:

```kotlin
package ru.aensidhe.dreamclock.settings

import androidx.annotation.StringRes
import ru.aensidhe.dreamclock.R

const val SCREENSAVER_SETTINGS_ACTION: String = "android.settings.DREAM_SETTINGS"

@StringRes
fun languageLabel(language: Language): Int =
    when (language) {
        Language.FOLLOW_SYSTEM, Language.UNRECOGNIZED -> R.string.lang_follow_system
        Language.RU -> R.string.lang_ru
        Language.EN -> R.string.lang_en
    }

@StringRes
fun colorModeLabel(mode: ColorRenderModeProto): Int =
    when (mode) {
        ColorRenderModeProto.TEXT_TINT, ColorRenderModeProto.UNRECOGNIZED -> R.string.render_text_tint_label
        ColorRenderModeProto.PANEL_TINT -> R.string.render_panel_tint_label
        ColorRenderModeProto.FULL_SCRIM -> R.string.render_full_scrim_label
        ColorRenderModeProto.ACCENT -> R.string.render_accent_label
    }

@StringRes
fun colorModeDescription(mode: ColorRenderModeProto): Int =
    when (mode) {
        ColorRenderModeProto.TEXT_TINT, ColorRenderModeProto.UNRECOGNIZED -> R.string.render_text_tint_desc
        ColorRenderModeProto.PANEL_TINT -> R.string.render_panel_tint_desc
        ColorRenderModeProto.FULL_SCRIM -> R.string.render_full_scrim_desc
        ColorRenderModeProto.ACCENT -> R.string.render_accent_desc
    }
```

Note: `UNRECOGNIZED` is grouped with the default branch only to satisfy exhaustive `when`; the selectors filter it out so it never renders.

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.aensidhe.dreamclock.settings.SettingsLabelsTest"`
Expected: PASS (4 tests).

- [ ] **Step 7: Lint**

Run: `./gradlew ktlintCheck detekt`
Expected: BUILD SUCCESSFUL. If ktlint reports formatting, run `./gradlew ktlintFormat` and re-check.

- [ ] **Step 8: Commit**

```
git add app/src/main/res/values/strings_settings.xml app/src/main/res/values-ru/strings_settings.xml app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsLabels.kt app/src/test/kotlin/ru/aensidhe/dreamclock/settings/SettingsLabelsTest.kt
git commit -m "feat: :robot: friendly localized settings labels + pure mapping"
```

---

### Task 2: Shared dream content wiring

**Files:**
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamContent.kt`
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/dream/TvDreamService.kt` (full replace)
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/dream/DreamContentTest.kt`

**Interfaces:**
- Consumes: `ClockViewModel(scope, settingsFlow, schedule, statusTextFor, nowProvider)`; `DreamRoot(state, showAnalog, mode)`; `SettingsRepository.from(context)`, `.settings`; `SettingsSerializer.defaultValue`; `Schedule`, `DaySchedule`, `Window`, `StateType` from `ru.aensidhe.dreamclock.core.schedule`; `ColorRenderMode`; `ColorRenderModeProto`.
- Produces (package `ru.aensidhe.dreamclock.dream`):
  - `internal fun defaultSchedule(): Schedule`
  - `internal fun statusTextFor(context: android.content.Context, state: StateType): String`
  - `internal fun ColorRenderModeProto.toColorRenderMode(): ColorRenderMode`
  - `@Composable internal fun DreamContent(viewModel: ClockViewModel, repository: SettingsRepository)`

- [ ] **Step 1: Create the shared wiring file**

Create `app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamContent.kt`:

```kotlin
package ru.aensidhe.dreamclock.dream

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalTime
import ru.aensidhe.dreamclock.R
import ru.aensidhe.dreamclock.core.schedule.DaySchedule
import ru.aensidhe.dreamclock.core.schedule.Schedule
import ru.aensidhe.dreamclock.core.schedule.StateType
import ru.aensidhe.dreamclock.core.schedule.Window
import ru.aensidhe.dreamclock.settings.ColorRenderModeProto
import ru.aensidhe.dreamclock.settings.SettingsRepository
import ru.aensidhe.dreamclock.settings.SettingsSerializer
import ru.aensidhe.dreamclock.ui.ClockViewModel
import ru.aensidhe.dreamclock.ui.DreamRoot
import ru.aensidhe.dreamclock.ui.colorrender.ColorRenderMode

/**
 * No schedule-configuration UI exists yet (feature 1 scope only): a single all-day
 * PLAY window is the placeholder default until a schedule editor lands.
 */
internal fun defaultSchedule(): Schedule =
    Schedule(default = DaySchedule(listOf(Window(LocalTime.MIDNIGHT, StateType.PLAY))))

internal fun statusTextFor(
    context: Context,
    state: StateType,
): String =
    when (state) {
        StateType.PLAY -> context.getString(R.string.status_play)
        StateType.PREPARE -> context.getString(R.string.status_prepare)
        StateType.SLEEP -> context.getString(R.string.status_sleep)
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
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by
        repository.settings.collectAsStateWithLifecycle(
            initialValue = SettingsSerializer.defaultValue,
        )
    DreamRoot(
        state = uiState,
        showAnalog = settings.showAnalogSlide,
        mode = settings.colorRenderMode.toColorRenderMode(),
    )
}
```

- [ ] **Step 2: Replace TvDreamService to use the shared wiring**

Replace the entire contents of `app/src/main/kotlin/ru/aensidhe/dreamclock/dream/TvDreamService.kt` with:

```kotlin
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
                statusTextFor = { statusTextFor(this, it) },
            )
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@TvDreamService)
            setViewTreeSavedStateRegistryOwner(this@TvDreamService)
            setViewTreeViewModelStoreOwner(this@TvDreamService)
            setContent { DreamContent(viewModel, repository) }
        }
    }
}
```

- [ ] **Step 3: Write the mapping test**

Create `app/src/test/kotlin/ru/aensidhe/dreamclock/dream/DreamContentTest.kt`:

```kotlin
package ru.aensidhe.dreamclock.dream

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import ru.aensidhe.dreamclock.settings.ColorRenderModeProto
import ru.aensidhe.dreamclock.ui.colorrender.ColorRenderMode

class DreamContentTest {
    @Test
    fun `proto colour modes map to render modes`() {
        assertEquals(ColorRenderMode.TEXT_TINT, ColorRenderModeProto.TEXT_TINT.toColorRenderMode())
        assertEquals(ColorRenderMode.PANEL_TINT, ColorRenderModeProto.PANEL_TINT.toColorRenderMode())
        assertEquals(ColorRenderMode.FULL_SCRIM, ColorRenderModeProto.FULL_SCRIM.toColorRenderMode())
        assertEquals(ColorRenderMode.ACCENT, ColorRenderModeProto.ACCENT.toColorRenderMode())
    }

    @Test
    fun `unrecognized colour mode falls back to text tint`() {
        assertEquals(ColorRenderMode.TEXT_TINT, ColorRenderModeProto.UNRECOGNIZED.toColorRenderMode())
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.aensidhe.dreamclock.dream.DreamContentTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Verify the whole module still builds and all tests pass**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; the existing `ClockFormattingTest` and `SettingsRepositoryTest` and the new tests all pass.

- [ ] **Step 6: Lint**

Run: `./gradlew ktlintCheck detekt`
Expected: BUILD SUCCESSFUL (run `./gradlew ktlintFormat` first if formatting is reported).

- [ ] **Step 7: Commit**

```
git add app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamContent.kt app/src/main/kotlin/ru/aensidhe/dreamclock/dream/TvDreamService.kt app/src/test/kotlin/ru/aensidhe/dreamclock/dream/DreamContentTest.kt
git commit -m "refactor: :robot: extract shared DreamContent wiring from dream service"
```

---

### Task 3: In-app dream preview activity

**Files:**
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamPreviewActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `DreamContent`, `defaultSchedule`, `statusTextFor` (Task 2); `ClockViewModel`; `SettingsRepository.from`.
- Produces: `DreamPreviewActivity` — started via `Intent(context, DreamPreviewActivity::class.java)`; dismisses on any key.

- [ ] **Step 1: Create the preview activity**

Create `app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamPreviewActivity.kt`:

```kotlin
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
                statusTextFor = { statusTextFor(this, it) },
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
```

- [ ] **Step 2: Register the activity in the manifest**

In `app/src/main/AndroidManifest.xml`, add this `<activity>` inside `<application>` (after the existing `SettingsActivity` block):

```xml
        <activity
            android:name=".dream.DreamPreviewActivity"
            android:exported="false"
            android:theme="@android:style/Theme.Black.NoTitleBar.Fullscreen" />
```

- [ ] **Step 3: Build to verify it compiles and the manifest merges**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Lint**

Run: `./gradlew ktlintCheck detekt`
Expected: BUILD SUCCESSFUL (run `./gradlew ktlintFormat` first if needed).

- [ ] **Step 5: Commit**

```
git add app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamPreviewActivity.kt app/src/main/AndroidManifest.xml
git commit -m "feat: :robot: in-app fullscreen dream preview activity"
```

---

### Task 4: TV settings screen + button wiring

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsScreen.kt`
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsActivity.kt` (full replace)

**Interfaces:**
- Consumes: `languageLabel`, `colorModeLabel`, `colorModeDescription`, `SCREENSAVER_SETTINGS_ACTION` (Task 1); `SettingsRepository.settings` / `.update`; `SettingsSerializer.defaultValue`; `Language`, `ColorRenderModeProto`; `DreamPreviewActivity` (Task 3); `androidx.tv.material3` components.
- Produces: `@Composable fun SettingsScreen(repository, scope, onTest, onSetScreensaver)`.

Note on tv-material3: components used here (`Surface`, `ListItem`, `Button`, `Text`, `MaterialTheme`, `darkColorScheme`) are annotated `@ExperimentalTvMaterial3Api` in 1.0.1, hence the `@OptIn`. If a symbol name differs from this plan, the design intent is fixed: focusable `ListItem` rows and tv `Button`s, a dark tv `MaterialTheme`, initial focus on the first row. Resolve against the actual tv-material3 API and keep that intent; do not fall back to phone Material3 components (they lack D-pad focus).

- [ ] **Step 1: Add the tv-material version catalog entry**

In `gradle/libs.versions.toml`, under `[versions]` add:

```toml
tvMaterial = "1.0.1"
```

Under `[libraries]` add:

```toml
androidx-tv-material = { module = "androidx.tv:tv-material", version.ref = "tvMaterial" }
```

- [ ] **Step 2: Add the dependency to the app module**

In `app/build.gradle.kts`, in the `dependencies { }` block, add after the `implementation(libs.compose.material3)` line:

```kotlin
    implementation(libs.androidx.tv.material)
```

- [ ] **Step 3: Verify resolution against the pinned stack**

Run: `./gradlew :app:checkDebugAarMetadata`
Expected: BUILD SUCCESSFUL (tv-material 1.0.1 does not require compileSdk 37). If this fails with an AAR-metadata / compileSdk error, stop and escalate — do not bump compileSdk.

- [ ] **Step 4: Create the settings screen**

Create `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsScreen.kt`:

```kotlin
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

                SectionHeader(stringResource(R.string.settings_language))
                Language.values().filter { it != Language.UNRECOGNIZED }.forEach { option ->
                    SelectableRow(
                        label = stringResource(languageLabel(option)),
                        description = null,
                        selected = option == settings.language,
                    ) { scope.launch { repository.update { it.toBuilder().setLanguage(option).build() } } }
                }

                SectionHeader(stringResource(R.string.settings_render_mode))
                ColorRenderModeProto.values().filter { it != ColorRenderModeProto.UNRECOGNIZED }.forEach { option ->
                    SelectableRow(
                        label = stringResource(colorModeLabel(option)),
                        description = stringResource(colorModeDescription(option)),
                        selected = option == settings.colorRenderMode,
                    ) { scope.launch { repository.update { it.toBuilder().setColorRenderMode(option).build() } } }
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
```

- [ ] **Step 5: Rewrite SettingsActivity to host the screen and wire the buttons**

Replace the entire contents of `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsActivity.kt` with:

```kotlin
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
```

- [ ] **Step 6: Build and lint**

Run: `./gradlew :app:assembleDebug ktlintCheck detekt`
Expected: BUILD SUCCESSFUL. If ktlint reports formatting, run `./gradlew ktlintFormat` and re-check. If detekt flags a long method on `SettingsScreen`, that is acceptable under the existing `LongMethod` threshold (80); if exceeded, extract the Language and Colour-style blocks into private `@Composable` helpers rather than suppressing.

- [ ] **Step 7: Run all unit tests (guard against regressions)**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (all prior tests still green; this task adds no unit tests — it is UI verified on-device by the owner).

- [ ] **Step 8: Commit**

```
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsScreen.kt app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsActivity.kt
git commit -m "feat: :robot: D-pad TV settings screen on tv-material with test/set buttons"
```

---

### Task 5: App icon set (adaptive launcher + TV banner)

**Files:**
- Create: `app/src/main/res/drawable/ic_launcher_background.xml`
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Create: `tools/generate_banner.py`
- Create (generated): `app/src/main/res/drawable-xhdpi/tv_banner.png`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:** none (resources + manifest attributes only).

- [ ] **Step 1: Adaptive icon background (five-stop gradient)**

Create `app/src/main/res/drawable/ic_launcher_background.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0"
                android:startY="0"
                android:endX="108"
                android:endY="108">
                <item android:offset="0.0" android:color="#1FA9A0" />
                <item android:offset="0.25" android:color="#9FC97C" />
                <item android:offset="0.5" android:color="#F5B342" />
                <item android:offset="0.75" android:color="#B07C6E" />
                <item android:offset="1.0" android:color="#4B2A4A" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
```

- [ ] **Step 2: Adaptive icon foreground (clock face)**

Create `app/src/main/res/drawable/ic_launcher_foreground.xml`. The clock sits inside the central 66% safe zone (viewport 108, centre 54,54, face radius 26):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Face -->
    <path
        android:pathData="M28,54 a26,26 0 1,0 52,0 a26,26 0 1,0 -52,0 z"
        android:fillColor="#FFF6E9" />
    <!-- Rim -->
    <path
        android:pathData="M28,54 a26,26 0 1,0 52,0 a26,26 0 1,0 -52,0 z"
        android:strokeColor="#2A2340"
        android:strokeWidth="2.5"
        android:fillColor="#00000000" />
    <!-- 12/3/6/9 tick marks -->
    <path android:pathData="M54,32 L54,37" android:strokeColor="#2A2340" android:strokeWidth="2.5" android:strokeLineCap="round" />
    <path android:pathData="M76,54 L71,54" android:strokeColor="#2A2340" android:strokeWidth="2.5" android:strokeLineCap="round" />
    <path android:pathData="M54,76 L54,71" android:strokeColor="#2A2340" android:strokeWidth="2.5" android:strokeLineCap="round" />
    <path android:pathData="M32,54 L37,54" android:strokeColor="#2A2340" android:strokeWidth="2.5" android:strokeLineCap="round" />
    <!-- Minute hand (to 12) -->
    <path android:pathData="M54,54 L54,38" android:strokeColor="#2A2340" android:strokeWidth="3" android:strokeLineCap="round" />
    <!-- Hour hand (to ~2 o'clock) -->
    <path android:pathData="M54,54 L66,48" android:strokeColor="#2A2340" android:strokeWidth="4" android:strokeLineCap="round" />
    <!-- Centre pin -->
    <path
        android:pathData="M51,54 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0 z"
        android:fillColor="#2A2340" />
</vector>
```

- [ ] **Step 3: Adaptive icon manifests**

Create `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

Create `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` with identical content (same two lines). minSdk is 30, so the `anydpi-v26` adaptive icon always resolves; no PNG density buckets are needed.

- [ ] **Step 4: Write the banner generator script**

Create `tools/generate_banner.py`:

```python
#!/usr/bin/env python3
"""Generate the Android TV banner (320x180 xhdpi) for Reverie.

Draws the five-stop dawn-to-dusk gradient, a small clock mark, and the
"Reverie" wordmark. Run with the repo-local Pillow venv:

    tmp/imgvenv/bin/python tools/generate_banner.py

Output: app/src/main/res/drawable-xhdpi/tv_banner.png (committed asset).
"""
from __future__ import annotations

import os

from PIL import Image, ImageDraw, ImageFont

W, H = 320, 180
STOPS = [
    (0.00, (0x1F, 0xA9, 0xA0)),
    (0.25, (0x9F, 0xC9, 0x7C)),
    (0.50, (0xF5, 0xB3, 0x42)),
    (0.75, (0xB0, 0x7C, 0x6E)),
    (1.00, (0x4B, 0x2A, 0x4A)),
]
CREAM = (0xFF, 0xF6, 0xE9)
INK = (0x2A, 0x23, 0x40)
OUT = "app/src/main/res/drawable-xhdpi/tv_banner.png"
FONT_CANDIDATES = [
    "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf",
]


def lerp(a: int, b: int, t: float) -> int:
    return round(a + (b - a) * t)


def color_at(t: float) -> tuple[int, int, int]:
    for i in range(len(STOPS) - 1):
        o0, c0 = STOPS[i]
        o1, c1 = STOPS[i + 1]
        if o0 <= t <= o1:
            k = 0.0 if o1 == o0 else (t - o0) / (o1 - o0)
            return tuple(lerp(c0[j], c1[j], k) for j in range(3))
    return STOPS[-1][1]


def load_font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def main() -> None:
    img = Image.new("RGB", (W, H))
    px = img.load()
    for x in range(W):
        r, g, b = color_at(x / (W - 1))
        for y in range(H):
            px[x, y] = (r, g, b)

    draw = ImageDraw.Draw(img)
    # Clock mark on the left.
    cx, cy, rad = 78, H // 2, 52
    draw.ellipse([cx - rad, cy - rad, cx + rad, cy + rad], fill=CREAM, outline=INK, width=4)
    draw.line([cx, cy, cx, cy - 34], fill=INK, width=5)      # minute hand -> 12
    draw.line([cx, cy, cx + 26, cy - 13], fill=INK, width=6)  # hour hand -> ~2
    draw.ellipse([cx - 5, cy - 5, cx + 5, cy + 5], fill=INK)

    # Wordmark on the right.
    font = load_font(46)
    draw.text((150, cy), "Reverie", font=font, fill=CREAM, anchor="lm")

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 5: Generate the banner PNG**

Run: `tmp/imgvenv/bin/python tools/generate_banner.py`
Expected: prints `wrote app/src/main/res/drawable-xhdpi/tv_banner.png` and the file exists (320x180 PNG). Verify: `file app/src/main/res/drawable-xhdpi/tv_banner.png`.

- [ ] **Step 6: Wire icon and banner into the manifest**

In `app/src/main/AndroidManifest.xml`, on the `<application>` element, add these three attributes (keep the existing `android:label`, `android:allowBackup`, `android:supportsRtl`):

```
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
android:banner="@drawable/tv_banner"
```

- [ ] **Step 7: Build to verify resources compile**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (aapt processes the adaptive icon, the vectors, and the banner). Then `./gradlew detekt` (no Kotlin changed, but keep the gate green).

- [ ] **Step 8: Commit**

```
git add app/src/main/res/drawable/ic_launcher_background.xml app/src/main/res/drawable/ic_launcher_foreground.xml app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml app/src/main/res/drawable-xhdpi/tv_banner.png tools/generate_banner.py app/src/main/AndroidManifest.xml
git commit -m "feat: :robot: adaptive launcher icon and TV banner"
```

---

## Verification (whole feature)

- [ ] `./gradlew :app:assembleDebug :app:testDebugUnitTest ktlintCheck detekt` — all green.
- [ ] Owner on-device (no device in the build environment): settings screen is fully D-pad operable with visible focus and an initial focus; option labels are friendly and localized; "Test screensaver" shows the real dream and any key exits; "Set as screensaver" opens the system screensaver picker (or the graceful fallback); the launcher shows the adaptive icon and the TV home row shows the banner.
