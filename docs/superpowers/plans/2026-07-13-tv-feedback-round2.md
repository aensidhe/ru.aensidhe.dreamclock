# TV Feedback Round 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply the round-2 on-device feedback: a real analog clock face, repositioned overlay text with a larger digital time, no settings window title, a working "Set as screensaver" button, and a documented answer on startup latency.

**Architecture:** Five independent changes across the `:app` module only — a Compose `Canvas` face, a Compose overlay layout tweak, string-resource edits, an `AndroidManifest.xml` theme/queries edit, and an `Activity` intent-cascade. None touch `:core` or the color-render engine. These are UI/resource/manifest changes with no pure-logic branch, so they are verified by build + ktlint + detekt (and the unchanged unit suite staying green), plus on-device visual acceptance — not by new unit tests. This matches the project rule: TDD for the pure-logic units, pragmatic tests elsewhere.

**Tech Stack:** Kotlin 2.4.0 + Jetpack Compose, `androidx.tv:tv-material`, Proto DataStore, Gradle 8.14.5 / AGP 8.13.2 / JDK 21.

## Global Constraints

- Kotlin 2.4.0 + Jetpack Compose; Gradle 8.14.5, AGP 8.13.2, JDK 21.
- `minSdk` 30, `compileSdk`/`targetSdk` 36. No new dependencies.
- No AppCompat. Use `androidx.tv.material3` for Compose UI and the platform `android.app.AlertDialog` where a dialog is needed.
- Do not modify `:core`, `ColorRenderers.kt`, `StateColors.kt`, or the color-render wiring. The face is neutral; state color continues to tint only the overlay text.
- Any `when` over `Language` / `ColorRenderModeProto` keeps `UNRECOGNIZED` grouped with the system-default branch.
- ktlint and detekt must pass; `assemble` and unit `test` stay green.
- Commits: Conventional Commits, `:robot:` after the type.

**Standard verification gate** (used by the code tasks below; run from repo root):

```bash
./gradlew ktlintFormat && ./gradlew ktlintCheck detekt test :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. `ktlintFormat` first auto-fixes formatting; the second command is the gate.

---

### Task 1: Overlay text — placement, order, larger digital, status copy

**Files:**
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/ClockOverlay.kt`
- Modify: `app/src/main/res/values/status_texts.xml`
- Modify: `app/src/main/res/values-ru/status_texts.xml`

**Interfaces:**
- Consumes: `ClockUiState(digital: String, colloquial: String?, statusText: String?, state: StateType)` from `ClockViewModel.kt` (unchanged) and `ColorRenderMode.RenderOverlay(state, content)` from `ColorRenderers.kt` (unchanged). Note: for the play state `statusText` is the empty string `""`, not null, because `status_play` is an empty resource.
- Produces: nothing new; same `ClockOverlay(ui, mode)` signature.

- [ ] **Step 1: Move the block to bottom-left, flip line order, enlarge digital**

Replace the body of `ClockOverlay` in `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/ClockOverlay.kt` (the current function spans lines 18-40) with:

```kotlin
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
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        Box(Modifier.align(Alignment.BottomStart)) {
            mode.RenderOverlay(ui.state) { textColor ->
                Column(horizontalAlignment = Alignment.Start) {
                    ui.statusText?.takeIf { it.isNotBlank() }?.let { Text(it, color = textColor, fontSize = 24.sp) }
                    ui.colloquial?.let { Text(it, color = textColor, fontSize = 24.sp) }
                }
            }
        }
    }
}
```

Three changes versus the original: digital `fontSize` `24.sp` → `32.sp`; the block aligns `BottomStart` instead of `BottomCenter` and the column aligns `Start` instead of `CenterHorizontally`; the status line renders first (guarded by `isNotBlank()` so the empty play state shows only the colloquial line), the colloquial reading second. No import changes are needed — `Alignment`, `Column`, `Text`, `sp` are already imported.

- [ ] **Step 2: Capitalize the status strings and add the trailing comma (English)**

Replace `app/src/main/res/values/status_texts.xml` with:

```xml
<resources>
    <string name="status_play"></string>
    <string name="status_prepare">Time to get ready for bed,</string>
    <string name="status_sleep">Time to sleep,</string>
</resources>
```

- [ ] **Step 3: Capitalize the status strings and add the trailing comma (Russian)**

Replace `app/src/main/res/values-ru/status_texts.xml` with:

```xml
<resources>
    <string name="status_play"></string>
    <string name="status_prepare">Пора готовиться ко сну,</string>
    <string name="status_sleep">Пора спать,</string>
</resources>
```

- [ ] **Step 4: Run the verification gate**

Run: `./gradlew ktlintFormat && ./gradlew ktlintCheck detekt test :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/ui/ClockOverlay.kt \
        app/src/main/res/values/status_texts.xml \
        app/src/main/res/values-ru/status_texts.xml
git commit -m "feat: :robot: overlay text bottom-left, status above colloquial, 32sp digital"
```

**On-device acceptance (user):** in the screensaver, the digital time is visibly larger; the status line sits above the colloquial reading in the bottom-left corner (e.g. `Пора спать,` / `половина десятого`); no hand crosses the text; in the daytime play state only the colloquial line shows, with no blank line above it.

---

### Task 2: Analog clock face — numerals and minute ticks

**Files:**
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/AnalogClockSlide.kt`

**Interfaces:**
- Consumes: `now: LocalDateTime` (unchanged parameter).
- Produces: nothing new; same `AnalogClockSlide(now)` signature.

- [ ] **Step 1: Replace the file with the full face implementation**

Replace the entire contents of `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/AnalogClockSlide.kt` with:

```kotlin
package ru.aensidhe.dreamclock.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import java.time.LocalDateTime
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val tickColor = Color(0xFF8794AB)
private val handColor = Color(0xFFF2F5FB)
private val secondColor = Color(0xFFFFB300)
private val numeralColor = Color(0xFFEEF2F8)

@Composable
fun AnalogClockSlide(now: LocalDateTime) {
    Canvas(Modifier.fillMaxSize()) {
        val radius = min(size.width, size.height) / 2f * 0.82f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawTicks(center, radius)
        drawNumerals(center, radius)

        fun hand(
            fraction: Float,
            length: Float,
            color: Color,
            width: Float,
        ) {
            val angle = (fraction * 2f * Math.PI - Math.PI / 2f).toFloat()
            drawLine(
                color = color,
                start = center,
                end = Offset(center.x + cos(angle) * radius * length, center.y + sin(angle) * radius * length),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }
        hand((now.hour % 12 + now.minute / 60f) / 12f, 0.50f, handColor, max(6f, radius * 0.026f))
        hand(now.minute / 60f, 0.72f, handColor, max(4f, radius * 0.018f))
        hand(now.second / 60f, 0.80f, secondColor, max(2f, radius * 0.009f))

        drawCircle(handColor, radius = max(4f, radius * 0.022f), center = center)
    }
}

private fun DrawScope.drawTicks(
    center: Offset,
    radius: Float,
) {
    for (i in 0 until 60) {
        val angle = (i / 60f * 2f * Math.PI - Math.PI / 2f).toFloat()
        val hour = i % 5 == 0
        val inner = radius - if (hour) radius * 0.09f else radius * 0.045f
        drawLine(
            color = tickColor,
            start = Offset(center.x + cos(angle) * inner, center.y + sin(angle) * inner),
            end = Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius),
            strokeWidth = if (hour) max(3f, radius * 0.014f) else max(1.5f, radius * 0.007f),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawNumerals(
    center: Offset,
    radius: Float,
) {
    val paint =
        Paint().apply {
            isAntiAlias = true
            color = numeralColor.toArgb()
            textAlign = Paint.Align.CENTER
            textSize = radius * 0.15f
            typeface = Typeface.DEFAULT_BOLD
        }
    val numeralRadius = radius * 0.80f
    val baseline = -(paint.descent() + paint.ascent()) / 2f
    for (n in 1..12) {
        val angle = (n / 12f * 2f * Math.PI - Math.PI / 2f).toFloat()
        val x = center.x + cos(angle) * numeralRadius
        val y = center.y + sin(angle) * numeralRadius + baseline
        drawContext.canvas.nativeCanvas.drawText(n.toString(), x, y, paint)
    }
}
```

Notes: numerals are drawn through the native canvas because Compose's `DrawScope` has no text primitive; `baseline` re-centers the glyph vertically. The hand fractions and the amber second hand match the original. The face is neutral in every color-render mode. This drawing file already relied on literal factors (`0.8f`, `12f`) that pass detekt; if a new `MagicNumber` finding appears on the added literals, extract that one value to a named `private val` rather than suppressing the rule.

- [ ] **Step 2: Run the verification gate**

Run: `./gradlew ktlintFormat && ./gradlew ktlintCheck detekt test :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/ui/AnalogClockSlide.kt
git commit -m "feat: :robot: analog clock face with 1-12 numerals and minute ticks"
```

**On-device acceptance (user):** the analog slide shows an upright 1-12 dial with a tick for every minute (the twelve hour ticks longer/thicker), hands centered over a small cap, seconds amber, nothing crossing the corner text.

---

### Task 3: Remove the settings window title

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:** none.

- [ ] **Step 1: Give `SettingsActivity` a no-title platform theme**

In `app/src/main/AndroidManifest.xml`, the `SettingsActivity` element currently reads:

```xml
        <activity
            android:name=".settings.SettingsActivity"
            android:exported="true"
            android:label="@string/app_name">
```

Add the theme attribute so it becomes:

```xml
        <activity
            android:name=".settings.SettingsActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@android:style/Theme.Black.NoTitleBar">
```

This removes the OS-drawn "Reverie" window title (and its background flash). The large localized "Грёзы" headline inside the Compose screen is unaffected.

- [ ] **Step 2: Run the verification gate**

Run: `./gradlew ktlintFormat && ./gradlew ktlintCheck detekt test :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "fix: :robot: drop settings window title via no-title theme"
```

**On-device acceptance (user):** no small "Reverie" strip above the settings content; the screen still opens focused on the first toggle row, and the "Грёзы" headline remains.

---

### Task 4: Working "Set as screensaver" button

**Files:**
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsActivity.kt`
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsLabels.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings_settings.xml`
- Modify: `app/src/main/res/values-ru/strings_settings.xml`

**Interfaces:**
- Consumes: `Context.localizedFor(language: Language): Context` from `Localization.kt` (returns a localized `Context`; `getString(resId, vararg)` supports the `%1$s` format arg). `currentLanguage: Language` field already tracked in `SettingsActivity`.
- Produces: constants `TV_SETTINGS_PACKAGE`, `DAYDREAM_ACTIVITY`, `ADB_SCREENSAVER_COMMAND` in `SettingsLabels.kt`; string resources `screensaver_manual_title`, `screensaver_manual_message`, `action_close`.

- [ ] **Step 1: Add the cascade constants**

In `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsLabels.kt`, immediately after the existing line 6 (`const val SCREENSAVER_SETTINGS_ACTION ...`), add:

```kotlin
const val TV_SETTINGS_PACKAGE: String = "com.android.tv.settings"
const val DAYDREAM_ACTIVITY: String =
    "com.android.tv.settings.device.display.daydream.DaydreamActivity"
const val ADB_SCREENSAVER_COMMAND: String =
    "adb shell settings put secure screensaver_components ru.aensidhe.dreamclock/.dream.TvDreamService"
```

- [ ] **Step 2: Replace the intent handling in `SettingsActivity`**

Replace the entire contents of `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsActivity.kt` with:

```kotlin
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

    private fun intentAvailable(intent: Intent): Boolean =
        packageManager.queryIntentActivities(intent, 0).isNotEmpty()

    private fun showAdbInstructions() {
        val localized = localizedFor(currentLanguage)
        AlertDialog.Builder(this)
            .setTitle(localized.getString(R.string.screensaver_manual_title))
            .setMessage(localized.getString(R.string.screensaver_manual_message, ADB_SCREENSAVER_COMMAND))
            .setPositiveButton(localized.getString(R.string.action_close), null)
            .show()
    }
}
```

Versus the original this drops the `android.provider.Settings` and `android.widget.Toast` imports (now unused), adds `android.app.AlertDialog`, and replaces `openScreensaverSettings` with the three-step cascade plus the `intentAvailable` and `showAdbInstructions` helpers.

- [ ] **Step 3: Declare package visibility for the queried intents**

In `app/src/main/AndroidManifest.xml`, add a `<queries>` block as a direct child of `<manifest>`, immediately before the `<application>` element:

```xml
    <queries>
        <intent>
            <action android:name="android.settings.DREAM_SETTINGS" />
        </intent>
        <package android:name="com.android.tv.settings" />
    </queries>
```

Under `targetSdk` 36, package visibility hides other apps' components unless declared; without this, `queryIntentActivities` returns empty even when the settings activity exists.

- [ ] **Step 4: Add the dialog strings and drop the obsolete fallback (English)**

In `app/src/main/res/values/strings_settings.xml`, remove the `settings_screensaver_fallback` line and add three strings before the closing `</resources>`:

```xml
    <string name="screensaver_manual_title">Screensaver settings unavailable</string>
    <string name="screensaver_manual_message">This TV hides the screensaver picker. Connect adb and run:\n\n%1$s</string>
    <string name="action_close">Close</string>
```

- [ ] **Step 5: Add the dialog strings and drop the obsolete fallback (Russian)**

In `app/src/main/res/values-ru/strings_settings.xml`, remove the `settings_screensaver_fallback` line and add:

```xml
    <string name="screensaver_manual_title">Настройки заставки недоступны</string>
    <string name="screensaver_manual_message">Этот телевизор скрывает выбор заставки. Подключите adb и выполните:\n\n%1$s</string>
    <string name="action_close">Закрыть</string>
```

- [ ] **Step 6: Run the verification gate**

Run: `./gradlew ktlintFormat && ./gradlew ktlintCheck detekt test :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. (If the build flags `settings_screensaver_fallback` as still referenced, confirm no code path uses it — the old toast was the only consumer and it was removed in Step 2.)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsActivity.kt \
        app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsLabels.kt \
        app/src/main/AndroidManifest.xml \
        app/src/main/res/values/strings_settings.xml \
        app/src/main/res/values-ru/strings_settings.xml
git commit -m "fix: :robot: cascade Set-as-screensaver to daydream activity with adb fallback"
```

**On-device acceptance (user):** on the Hartens TV, the "Set as screensaver" button opens a screensaver picker (step 1 or 2). If neither exists, a localized dialog appears showing the adb command, dismissable with Close.

---

### Task 5: Startup latency — diagnose and document (manual, requires the device)

This task needs `adb` against the TV, so it is run by the user, not a subagent. Its deliverable is the measured numbers and conclusion recorded in the spec. No app-code change is expected.

**Files:**
- Modify: `docs/superpowers/specs/2026-07-13-tv-feedback-round2-design.md` (fill in the item-5 findings)

- [ ] **Step 1: Measure debug cold start**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop ru.aensidhe.dreamclock
adb shell am start -W -n ru.aensidhe.dreamclock/.settings.SettingsActivity
```

Record the `TotalTime` value (ms) from the `am start -W` output. Repeat 3 times after a force-stop and take the median.

- [ ] **Step 2: Build a minified release APK signed with the debug key (local, uncommitted)**

The release build type currently has no signing config and `isMinifyEnabled = false`, so it is neither installable nor representative. Make a temporary local edit to `app/build.gradle.kts` — do not commit it — changing the `release` block to:

```kotlin
    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }
```

Then:

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am force-stop ru.aensidhe.dreamclock
adb shell am start -W -n ru.aensidhe.dreamclock/.settings.SettingsActivity
```

Record the median `TotalTime` the same way.

- [ ] **Step 3: Revert the build file edit**

```bash
git checkout app/build.gradle.kts
```

Confirm `git status` shows `app/build.gradle.kts` clean.

- [ ] **Step 4: Record the conclusion in the spec**

Edit the "5. Startup latency" section of `docs/superpowers/specs/2026-07-13-tv-feedback-round2-design.md` to state the two median `TotalTime` values and the conclusion: if the release build starts acceptably, close this as a debug-build artifact (no code change); only if release start is still poor is a follow-up profiling task warranted.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/specs/2026-07-13-tv-feedback-round2-design.md
git commit -m "docs: :robot: record startup-latency measurements (debug vs release)"
```

---

## Notes for the executor

- Tasks 1-4 are independent (different files, no shared new interfaces except Task 4's own constants/strings) and may be implemented and reviewed in any order; the numbering is just a convenient sequence.
- Task 5 is manual and device-bound; a subagent should hand it to the user rather than attempt it.
- After Tasks 1-4 land, the branch integration follows the standing project flow: rebase `feat/kid-clock` on `main`, push to update the PR so CI runs, then `git switch main && git merge --ff-only feat/kid-clock && git push` once green — and only after the user's on-device sign-off.
