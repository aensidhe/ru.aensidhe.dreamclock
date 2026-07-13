# TV Feedback Round 2 — Design

On-device feedback for the Reverie screensaver after the round-1 fixes. Five
independent changes: a real analog clock face, repositioned overlay text, a
larger digital time, removal of the settings window title, a working
"Set as screensaver" button, and a documented answer on startup latency.

Approved visual reference (analog face + text layout):
`tmp/clock-face-preview.html` (published as an Artifact).

## Global constraints

Copied from the project stack; every change below inherits these.

- Kotlin 2.4.0 + Jetpack Compose; Gradle 8.14.5, AGP 8.13.2, JDK 21.
- `minSdk` 30, `compileSdk`/`targetSdk` 36. No new dependencies.
- No AppCompat. Use `androidx.tv.material3` for Compose UI and the platform
  `android.app.AlertDialog` where a dialog is needed.
- Do not modify `:core`, `ColorRenderers.kt`, `StateColors.kt`, or the
  color-render wiring. The face is neutral; state color continues to tint
  only the overlay text.
- Any `when` over `Language` / `ColorRenderModeProto` keeps `UNRECOGNIZED`
  grouped with the system-default branch.
- ktlint and detekt must pass; `assemble` and unit `test` stay green.
- Commits: Conventional Commits, `:robot:` after the type.

## 1. Analog clock face

File: `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/AnalogClockSlide.kt`.

Today the slide draws three bare hands on an empty field. Add a static face
behind the hands:

- Face radius `R = min(width, height) / 2 * 0.82`, centered.
- Sixty minute ticks around the rim. The twelve hour ticks (every fifth) are
  longer and thicker (inset `~0.09 * R`, stroke `~0.014 * R`); the remaining
  minute ticks are short and thin (inset `~0.045 * R`, stroke `~0.007 * R`).
  Rounded caps. Tick color muted off-white (`0xFF8794AB`).
- Arabic numerals 1 through 12 on a circle at `0.80 * R`, drawn with the
  native canvas text API (Compose `Canvas` exposes
  `drawContext.canvas.nativeCanvas.drawText` with an `android.graphics.Paint`).
  Numerals off-white (`0xFFEEF2F8`), bold, text size `~0.15 * R`, centered on
  their point (horizontal center + vertical baseline correction).
- Hands unchanged in proportion and color: hour `0.50 * R` white, minute
  `0.72 * R` white, second `0.80 * R` amber (`0xFFFFB300`). A small filled
  center cap (`~0.022 * R`, white) covers the pivot.

The face is neutral in every color-render mode; only the overlay text changes
color with state.

Testing: this is Canvas rendering with no pure-logic branch, and the project
has no screenshot-test harness. Verify visually on-device (numerals upright
and evenly spaced, hour ticks distinguishable, hands centered over the cap).
No unit test is added for this task.

## 2. Overlay text: placement, order, larger digital

File: `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/ClockOverlay.kt`.

- Digital time (top-left) grows from `24.sp` to `32.sp`. It stays
  `TopStart`, bold, white.
- The colloquial/status block moves from `BottomCenter` to `BottomStart` and
  its column aligns `Start` instead of `CenterHorizontally`, clearing the
  hands' sweep (the reason the seconds hand crossed the words).
- Line order inside the block flips: status line first, colloquial reading
  second. Rendered result, e.g. in the sleep state:

  ```
  Пора спать,
  половина десятого
  ```

  When the status string is empty (play state) only the colloquial reading
  shows, with no leading blank line and no comma.

Both lines remain inside the single `mode.RenderOverlay(...)` slot so the
color-render state tints them together, exactly as today.

### Status string copy

Files: `app/src/main/res/values/status_texts.xml`,
`app/src/main/res/values-ru/status_texts.xml`.

The status line now leads the phrase, so it is capitalized and ends with a
comma; `status_play` stays empty.

- ru: `status_prepare` → `Пора готовиться ко сну,`;
  `status_sleep` → `Пора спать,`.
- en: `status_prepare` → `Time to get ready for bed,`;
  `status_sleep` → `Time to sleep,`.

## 3. Remove the settings window title

File: `app/src/main/AndroidManifest.xml`.

The tiny "Reverie" at the top-left of the settings screen is the OS-drawn
window title: `SettingsActivity` declares no theme, so the platform default
renders a title bar showing `android:label`. Give the activity a no-title
platform theme:

```
android:theme="@android:style/Theme.Black.NoTitleBar"
```

This removes the window title (and its background flash) without adding theme
resources. The large localized "Грёзы" headline inside the Compose screen is
the in-app title and stays.

Testing: verify on-device that no "Reverie" strip appears above the content
and the screen still opens on the first toggle row.

## 4. Working "Set as screensaver" button

Files: `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsActivity.kt`,
`app/src/main/AndroidManifest.xml`.

Today `openScreensaverSettings` tries `android.settings.DREAM_SETTINGS`
guarded by `resolveActivity`, and on failure opens generic Settings plus a
toast — which is the "nothing happens" the user saw. Replace it with the
cascade proven by the Immich-Android-TV app, resolved via
`queryIntentActivities` (more reliable than `resolveActivity` under Android 11
package visibility):

1. `Intent("android.settings.DREAM_SETTINGS")` — if it resolves, start it.
2. Otherwise `Intent(Intent.ACTION_MAIN).setClassName(
   "com.android.tv.settings",
   "com.android.tv.settings.device.display.daydream.DaydreamActivity")` — if it
   resolves, start it.
3. Otherwise show a platform `android.app.AlertDialog` explaining that this
   device hides the screensaver settings, with the adb command to select the
   dream directly:

   ```
   adb shell settings put secure screensaver_components \
     ru.aensidhe.dreamclock/.dream.TvDreamService
   ```

   The dialog message text is localized (new string in `values` and
   `values-ru`), resolved through the existing `localizedFor(currentLanguage)`
   context so it follows the in-app language. The dialog has a single dismiss
   button. The adb command itself stays untranslated in a code-style line.

A private `intentAvailable(intent): Boolean` helper wraps
`packageManager.queryIntentActivities(intent, 0).isNotEmpty()`.

Android 11 package visibility requires declaring what we query. Add to the
manifest:

```
<queries>
    <intent>
        <action android:name="android.settings.DREAM_SETTINGS" />
    </intent>
    <package android:name="com.android.tv.settings" />
</queries>
```

The old `SCREENSAVER_SETTINGS_ACTION` constant in `SettingsLabels.kt` and the
`settings_screensaver_fallback` string may be reused or replaced; the new
dialog string supersedes the toast.

Testing: intent resolution depends on the device and cannot be unit-tested
meaningfully here. Verify on the Hartens TV that the button opens a screensaver
picker (step 1 or 2), or shows the adb dialog if neither exists.

## 5. Startup latency — diagnose and document

No committed code change is assumed; this is a measurement task whose result is
recorded in the PR description (and here, on completion).

- Measure debug cold start: `adb shell am start -W -n
  ru.aensidhe.dreamclock/.settings.SettingsActivity`, and note the
  `Displayed` line in logcat.
- Build a minified release variant locally (signed with the debug keystore for
  timing only) and measure the same way.
- Expectation: the debug build is slow because it is `debuggable`,
  non-minified, and runs largely interpreted, plus Compose debug tooling; the
  release build should start far faster. The first Proto DataStore read is a
  minor, already-async cost.

If the release cold start is acceptable, record the numbers and close this as a
debug-build artifact — no code change. Only if release start is still poor do we
profile for a real hotspot; that would be a follow-up, not part of this round.

## Out of scope

Feature 2 (Immich photos), feature 3 (schedule editor), feature 4 (agenda),
and any release signing/publishing setup. Deferred infra items from prior
rounds (manifest `<uses-feature>`, CI artifact upload, action version bumps,
branch protection) are unchanged here.
