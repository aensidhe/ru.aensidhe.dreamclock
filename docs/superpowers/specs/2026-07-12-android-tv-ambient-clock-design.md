# Reverie (Грёзы) — Android TV Screensaver — Design

- Date: 2026-07-12
- Status: Approved for feature 1 implementation
- Name (localized label): Reverie (EN) / Грёзы (RU)
- Package (`applicationId`): `ru.aensidhe.dreamclock`

## Overview

An Android TV screensaver (`DreamService`) for the living-room screen. It shows
an always-visible, kid-friendly clock overlaid on a rotating deck of slides.
Photos anchored around the current date and a "tomorrow" agenda are later slide
sources.

Target devices: NVIDIA Shield and Xiaomi TV Stick, at 1080p or higher.

### Goals (priority order)

1. Kid-friendly dual clock — build first. Fully designed here.
2. Immich photos — recorded as scope; full design deferred to its own brainstorm.
3. Agenda slide — recorded as scope; source undecided; design deferred.

Everything below designs feature 1 in full and records 2 and 3 as future scope.

## Architecture

```
┌─────────────────────────────────────────────┐
│  DreamService (the screensaver)               │
│  hosts a ComposeView                          │
│                                               │
│   ┌───────────────────────────────────────┐   │  ← background layer
│   │  Slide deck (rotates)                  │   │
│   │   • Analog-clock slide                 │   │
│   │   • Photo slides (feature 2, later)    │   │
│   │   • Agenda slide every N (feature 3)   │   │
│   └───────────────────────────────────────┘   │
│                                               │
│   ┌───────────────────────────────────────┐   │  ← overlay layer
│   │  Clock overlay (always on top)         │   │
│   │   digital + colloquial + status text   │   │
│   └───────────────────────────────────────┘   │
└─────────────────────────────────────────────┘

Companion SettingsActivity (normal launchable Activity)
Config persisted via Jetpack DataStore (Proto)
State driven by ViewModel / Kotlin Flows (clock ticks, active state)
```

### Units (each one job, well-bounded interface)

| Unit                      | Responsibility                                                   | Android deps |
| ------------------------- | ---------------------------------------------------------------- | ------------ |
| `TvDreamService`          | Screensaver entry point; hosts Compose; lifecycle                | Yes          |
| `SlideDeck`               | Rotation logic; which slide is current                           | UI only      |
| `ClockOverlay`            | Renders digital + colloquial + status; applies color render mode | UI only      |
| `ScheduleEngine`          | `(now, config) → active state + status text`                     | No           |
| `ColloquialTimeFormatter` | Per-locale spoken time (`Ru`, `En`)                              | No           |
| `SettingsRepository`      | Read/write config (Proto DataStore)                              | Yes          |
| `SettingsActivity`        | Configuration UI                                                 | Yes          |

The pure-Kotlin units (`ScheduleEngine`, `ColloquialTimeFormatter`) hold most of
the real logic and carry the bulk of test coverage.

### Platform

- Kotlin + Jetpack Compose, latest stable tooling. Kotlin over Java.
- `minSdk` 30 — both target devices (Shield and Xiaomi TV Stick) run Android 11.
- `compileSdk` / `targetSdk` 36 — the latest API that Android Gradle Plugin 8.13
  supports.
- Android Gradle Plugin 8.13.2; Gradle 8.14.5 (wrapper-pinned); Kotlin 2.4.0;
  JDK 21. Static analysis via detekt 1.23.8 and ktlint-gradle 14.2.0. The stack
  stays on the Gradle 8 line so every tool is a stable release: API 37 would
  force AGP 9 and Gradle 9, where detekt has no stable build yet (only a 2.0
  alpha), and API 36 costs nothing here — `minSdk` is 30 and the app is not on
  Play.
- TV screensaver registered via manifest `<meta-data>` (Leanback/DreamService).

## Feature 1 — The clock

### Overlay contents (always on top of the current slide)

1. Digital time — the locale's convention (24-hour in RU, e.g. `21:05:00`).
   Seconds shown by default; a "full time" toggle can drop seconds. Ticks every
   second.
2. Colloquial time (toggle, default on) — kid-readable spoken form. See rules
   below. Recomputed every minute.
3. Status text — from the active state's localized template, or a custom override
   string.

### Three states

Fixed times now; the data model supports more (see Schedule).

| State     | Meaning                     | Default color |
| --------- | --------------------------- | ------------- |
| `PLAY`    | Play is fine                | Soft green    |
| `PREPARE` | Time to get ready for sleep | Warm amber    |
| `SLEEP`   | Time to sleep               | Deep plum     |

Colors are per-state configurable. See Color palette and Color render modes.

### Colloquial time rules

A dedicated `ColloquialTimeFormatter` per locale, no Android deps, exhaustively
unit-tested. No part-of-day suffix (утра/вечера / "in the evening") — the color
state and the 24-hour digital line already convey the time of day.

Russian — pivot at 30 minutes; reference the coming hour:
- Exact hour: `<hour> часов` — e.g. 21:00 → `девять часов`.
- 1–14 and 16–29 min: `<minutes, agreeing> минут <coming-hour ordinal, genitive>`
  — e.g. 21:05 → `пять минут десятого`; 21:23 → `двадцать три минуты десятого`.
- 15 min: `четверть <coming-hour ordinal, genitive>` — 21:15 → `четверть десятого`.
- 30 min: `половина <coming-hour ordinal, genitive>` — 21:30 → `половина десятого`.
- 45 min: `без четверти <next-hour cardinal>` — 21:45 → `без четверти десять`.
- 31–44 and 46–59 min: `без <60−min, genitive> <next-hour cardinal>` — 21:55 →
  `без пяти десять`; 21:35 → `без двадцати пяти десять`.
- Feminine numerals for the minute count (1 → одна, 2 → две) and
  ordinal-genitive of the coming hour (первого…двенадцатого). The 12-hour number
  is used (21 → десятого / десять). Minute-noun agreement per the declension
  rules below.
- Noon/midnight kept plain: `двенадцать часов` (полдень/полночь may come later).

English — pivot at 30 minutes; `past`/`to`, with `quarter`/`half` words:
- 21:00 → `nine o'clock`
- 21:05 → `five past nine`; 21:15 → `quarter past nine`; 21:23 → `twenty-three past nine`
- 21:30 → `half past nine`
- 21:45 → `quarter to ten`; 21:55 → `five to ten`
- 12:00 → `twelve o'clock`
- Uses the 12-hour number. No part-of-day suffix.

### Minute-noun declension

Russian минута has three forms, chosen by the minute value m:

- одна минута (one): `m % 10 == 1 and m % 100 != 11` — 1, 21, 31, …
- две минуты (few): `m % 10 in {2,3,4} and m % 100 not in {12,13,14}` — 2–4,
  22–24, …
- минут (many): everything else, including the teens 11–14 and
  `m % 10 in {0,5,6,7,8,9}` — 5–20, 25–29, …

Numerals are feminine because минута is feminine (одна/две, not один/два). This
agreement applies only to the "X минут <ordinal>" forms (minutes 1–14 and
16–29). 15 (четверть), 30 (половина), and the "без …" forms carry no минут noun,
so no agreement is needed there.

English minute has two forms, used only when the noun is actually spoken (the
default past/to phrasing omits it): minute for `m == 1`, minutes otherwise.

### Analog-clock slide

A full-screen analog clock face, part of the slide deck. Toggle on/off (default
on). Also the placeholder content shown before the photo gallery exists. The
digital overlay still sits on top of it.

### Color palette

A five-color ramp is defined; three states are active now, no ramp (the two extra
colors stay defined but unused for now):

```
Play (teal)  →  Soft green  →  Warm amber  →  Muted rose-brown  →  Deep plum
```

Rationale: cool-and-bright → warm-and-dim follows circadian wind-down; the warm,
dim end (amber → plum) lowers blue light in the evening. Default active mapping:
`PLAY → soft green`, `PREPARE → warm amber`, `SLEEP → deep plum`. `teal` and
`muted rose-brown` remain in the palette for later.

### Color render modes

How the state color is applied is a configurable strategy (`ColorRenderMode`),
selectable in settings, so we can build several, compare on real devices, and
likely keep more than one. Implemented as swappable renderers behind one
interface; nothing else in the app depends on which is active. Each mode owns its
own text legibility (adds a readability backing if needed).

| Mode         | Digits/text | Where the state color lives                                |
| ------------ | ----------- | ---------------------------------------------------------- |
| `TEXT_TINT`  | colored     | digits & status text take the state color                  |
| `PANEL_TINT` | near-white  | translucent tinted panel behind the text block             |
| `FULL_SCRIM` | near-white  | gentle full-screen color wash (doubles as evening dimming) |
| `ACCENT`     | near-white  | white text + a thin colored accent/glow, transparent panel |

The set is extensible; more modes may be added after on-device comparison.

## Configuration & schedule data model

Persisted in Proto DataStore (typed; suits a nested schedule).

### Schedule resolution — most specific wins

```
date override (holidays)  →  day-of-week schedule  →  default schedule
```

Only the default schedule is populated now (fixed times, same every day). The
day-of-week and date-override layers exist in the model so they can be filled
later as pure data, no rework.

### A day schedule is ordered state windows

```
DaySchedule = [ Window(startTime, StateType, textOverride?), … ]   // sorted by start
StateType   = PLAY | PREPARE | SLEEP     // enum, extensible
```

Example default: `00:00 SLEEP`, `07:00 PLAY`, `20:00 PREPARE`, `21:00 SLEEP`.
`ScheduleEngine` selects the window whose `startTime ≤ now`.

### Per-state config

- Color (default per the mapping above)
- Status text: localized template, or a custom override string

### Global settings surface

- Language: follow system / force RU / force EN
- Colloquial on/off; full-time (seconds) on/off; analog-slide on/off
- `ColorRenderMode` selection
- Photo window ±N days (feature 2; default e.g. 20)
- Agenda-every-N-photos (feature 3)
- Immich connection details (feature 2; URL, API key) — later

## Feature 2 — Immich photos (recorded, deferred)

Slide source pulling photos within ±N days of today across all past years from an
Immich server. Needs: connection config (URL, API key), a date-window query,
image loading/caching, slide transitions. Full design in its own brainstorm.

## Feature 3 — Agenda (recorded, deferred)

"Tomorrow's agenda" rendered as a slide every N photos (configurable). Source
undecided (local setup unsettled). Design deferred until the source is chosen.

## Testing

- `ScheduleEngine` — exhaustive unit tests over state boundaries and schedule
  resolution (override → day-of-week → default).
- `ColloquialTimeFormatter` (RU/EN) — case-table unit tests covering every rule
  and edge case (agreement, genitives, quarter/half, noon/midnight, past/to).
- `SettingsRepository` — DataStore read/write round-trips.
- UI (Compose) — light instrumentation; the DreamService shell and slide deck
  verified manually on Shield/Xiaomi (DreamService is awkward to fully automate).

## Open decisions

- Color render modes — the winning default (and which modes to keep) decided
  after side-by-side testing on real devices.

## Out of scope (for now)

- Color ramp beyond three states.
- Part-of-day suffix in colloquial time.
- Full designs for features 2 and 3.
