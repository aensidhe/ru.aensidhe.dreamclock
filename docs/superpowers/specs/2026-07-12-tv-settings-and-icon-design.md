# Reverie (Грёзы) — TV-Remote Settings GUI + Icon Set — Design

- Date: 2026-07-12
- Status: Approved for implementation
- Branch: feat/kid-clock (folded into the same branch; not yet merged to main)
- Depends on: 2026-07-12-android-tv-ambient-clock-design.md (feature 1)

## Why

The first on-device launch showed the settings screen is a phone-style form:
a light `MaterialTheme` with Material3 `Switch`/`RadioButton` rows in a plain
`Column`, no D-pad focus or focus highlight, raw enum names as option labels,
and no overscan-safe layout. The target has been an Android TV device (NVIDIA
Shield, Xiaomi TV Stick) from the start, driven by a remote. This design
replaces that screen with a D-pad-navigable TV GUI, adds two actions the TV
context needs (test the screensaver, jump to the system screensaver picker),
and adds the app icon set the launcher requires.

## Scope

In scope:

- Replace `SettingsActivity`'s UI with a D-pad-navigable TV screen built on
  Compose-for-TV (`androidx.tv:tv-material`).
- Friendly, localized option labels for Language and Colour style (no raw enum
  names).
- A "Test screensaver" button that previews the real dream in-app.
- A "Set as screensaver" button that opens the system screensaver settings.
- An app icon set: an adaptive launcher icon and a TV banner.

Out of scope (non-goals):

- No phone or tablet layout. This is a TV-only GUI.
- No changes to the pure-logic `:core` module.
- No changes to the dream's rendering (`DreamRoot`, `SlideDeck`, `ClockOverlay`,
  colour renderers). The preview reuses `DreamRoot` unchanged.
- No reconciliation of the clock's three schedule-state colours with the new
  five-stop icon palette. The palette is icon/banner-only.

## Toolkit decision

Use `androidx.tv:tv-material:1.0.1` (Compose-for-TV, tv-material3). It provides
built-in D-pad focus traversal, focus highlight and scale, and TV-shaped
surfaces, and it is Google's recommended path for TV Compose UIs.

Version rationale (guards the pinned stable-Gradle-8 stack): tv-material 1.0.0
and 1.0.1 depend on Compose 1.6.8 (compileSdk-34 era), so they resolve happily
under the pinned composeBom 2025.09.01 without demanding compileSdk 37. Version
1.1.0 depends on Compose 1.10.3, which risks dragging in the compileSdk-37 /
AGP-9 line the project deliberately avoids. Therefore pin exactly 1.0.1.

Verification gate: `assembleDebug` runs `checkDebugAarMetadata`. If tv-material
1.0.1 fails the AAR-metadata compatibility check against compileSdk 36, fall
back to hand-rolled focus on the existing Material3 components (focusable
modifiers, manual focus highlight, `FocusRequester` for initial focus) — the
rest of this design (theme, layout, labels, buttons, icons) is unchanged by the
fallback.

## TV settings screen

Component: `SettingsActivity` stays a `ComponentActivity`; only its `setContent`
body changes.

Theme:

- tv-material3 `MaterialTheme` with a dark, ambient colour scheme. Background is
  a deep near-black indigo; text is a soft near-white; the accent is drawn from
  the icon palette (deep plum with teal). No light/white background.

Layout:

- A single vertical, scrollable column with overscan-safe padding (about 5% of
  each edge, e.g. 48dp horizontal and 27dp vertical at 1080p).
- Initial D-pad focus is requested on the first focusable row via a
  `FocusRequester`, so the remote lands somewhere visible on launch.
- Section headers group related controls.

Controls, in order:

1. Three toggle rows — Spoken time, Show seconds, Analog clock slide — each a
   focusable tv-material3 `ListItem` with a trailing tv `Switch`. Activating the
   row (D-pad centre) flips the toggle.
2. Language — a section header plus one focusable selectable row per option
   (Follow system, Русский, English), with a check indicator on the selected
   one. Selecting a row persists that value.
3. Colour style — a section header plus one focusable selectable row per option
   (the four colour modes), with a check on the selected one and a short
   descriptive subtitle per mode.
4. Two action buttons — "Test screensaver" and "Set as screensaver" — as
   tv-material3 `Button`s at the bottom.

Behaviour carried over unchanged from the current screen:

- The `UNRECOGNIZED` synthetic protobuf-lite enum value is filtered out of both
  selectors.
- Persistence is via `SettingsRepository.update { ... }` in `lifecycleScope`,
  writing to Proto DataStore.

Friendly option labels (new string resources, `values/` + `values-ru/`):

- Language: Follow system / Как в системе; Русский / Русский; English / English.
- Colour style, one label plus one short subtitle each, describing how loudly
  the state colour reads (from the code in `ColorRenderers.kt`):
  - TEXT_TINT — "Coloured text": the clock text itself takes the state colour.
  - PANEL_TINT — "Soft panel": near-white text on a translucent coloured card.
  - FULL_SCRIM — "Full tint": near-white text over a full-screen colour wash.
  - ACCENT — "Bold badge": near-white text on a near-solid colour block.
- Action buttons: "Test screensaver" / "Проверить заставку"; "Set as
  screensaver" / "Выбрать заставку".

The exact wording is finalized in the implementation plan; the raw enum `.name`
labels are removed.

## The two action buttons

Set as screensaver:

- `startActivity(Intent("android.settings.DREAM_SETTINGS"))`, wrapped in
  try/catch. On `ActivityNotFoundException`, fall back to
  `Intent(Settings.ACTION_SETTINGS)` and show a short toast explaining where to
  find the screensaver setting.
- Android TV does not let an app set itself as the active dream
  programmatically; the user selects Reverie in that system screen. This button
  removes the guesswork of finding it.

Test screensaver:

- A new lightweight `DreamPreviewActivity` (a `ComponentActivity`) that hosts the
  same `DreamRoot` composable fullscreen, reading the current settings, so the
  preview is exactly what the screensaver renders.
- It is fullscreen and immersive (hide system bars), and dismisses on BACK or
  D-pad centre.
- It is not exported and has no launcher intent-filter; it is started only from
  `SettingsActivity`.
- `DreamRoot` currently takes its inputs from `TvDreamService`. The preview needs
  the same inputs (settings flow, schedule placeholder, status-text lookup). The
  plan factors the shared wiring so both the service and the preview activity
  build `DreamRoot` the same way, without duplicating logic. No rendering code
  changes.

## Icon set

Palette (five-stop dawn-to-dusk gradient, icon/banner only):

| Stop | Name | Hex |
|---|---|---|
| 1 | Play (teal) | `#1FA9A0` |
| 2 | Soft green | `#9FC97C` |
| 3 | Warm amber | `#F5B342` |
| 4 | Muted rose-brown | `#B07C6E` |
| 5 | Deep plum | `#4B2A4A` |

Concept: a soft analog clock face (cream face, dark hands at a friendly angle)
over the five-stop gradient running teal to plum diagonally.

Adaptive launcher icon:

- `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`, each an
  `<adaptive-icon>` with a vector background (the gradient) and a vector
  foreground (the clock mark). minSdk 30 means every target supports adaptive
  icons, so no legacy PNG density buckets are required.
- Hand-authored vector drawables; no raster tooling needed for the launcher
  icon.

TV banner:

- `android:banner`, a 320x180 (xhdpi) raster, showing the clock mark plus the
  "Reverie" wordmark on the gradient band. Vector drawables cannot render text,
  so the banner is a committed PNG produced by a one-time local script using
  Pillow (installed in the gitignored `tmp/imgvenv`). The script lives in the
  repo (e.g. `tools/`) and is documented, but it is a build-time asset generator,
  not a runtime or Gradle dependency. The wordmark stays "Reverie" (the brand
  mark); the localized app label is handled separately by `@string/app_name`.

Manifest wiring:

- Add `android:icon="@mipmap/ic_launcher"`, `android:roundIcon=
  "@mipmap/ic_launcher_round"`, and `android:banner="@drawable/tv_banner"` to the
  `<application>` element. Keep `android:label="@string/app_name"`.

## Testing

- Pure/unit (fits the existing JUnit5 setup): factor the enum-to-string-res-id
  label mapping for Language and Colour style, and the screensaver-intent
  builder, as small pure functions and unit-test them. These carry the only
  branching logic worth asserting.
- Compose/on-device: pragmatic. No instrumented tests (no device in the build
  environment). D-pad focus behaviour, the in-app preview, the two buttons, the
  launcher icon, and the TV banner are owner on-device verification.

## Risks and mitigations

- tv-material 1.0.1 incompatible with compileSdk 36: caught by
  `checkDebugAarMetadata` during `assembleDebug`; fall back to hand-rolled focus
  (see Toolkit decision). The rest of the design is unaffected.
- `android.settings.DREAM_SETTINGS` missing on a device: handled by the
  try/catch fallback in the Set-as-screensaver button.
- Banner text rendering: solved by generating a committed PNG with Pillow rather
  than attempting text in a vector drawable.

## Definition of done

- Settings screen is fully operable with a D-pad remote: every control is
  reachable and activatable, focus is always visible, and the initial focus is
  set on launch.
- Option labels are friendly and localized (EN + RU); no raw enum names appear.
- "Test screensaver" previews the real dream; "Set as screensaver" opens the
  system screensaver picker (or a graceful fallback).
- The launcher shows the adaptive icon and the TV home row shows the banner.
- `assembleDebug`, unit `test`, `ktlintCheck`, and `detekt` are green.
