# CLAUDE.md

Project memory for Claude Code. Keep it concise and high-signal.

## What this is

An Android TV screensaver (`DreamService`) — a kid-friendly ambient clock
over a rotating slide deck (photos, agenda). Target devices: NVIDIA Shield and
Xiaomi TV Stick, ≥1080p.

Package (`applicationId`): `ru.aensidhe.dreamclock`. The user-facing app name is
Reverie (Russian: Грёзы), shown as a localized app label.

## Features

What the app does today. The numbering is historical; specs refer to "feature N".

1. Kid-friendly dual clock — locale time + colloquial spoken form + 3 color
   states, with an analog face and a state-tinted second hand
2. Immich photos — ±N days around today, across all past years, with QR /
   local-network pairing to carry an Immich key across from a phone
3. People names on photo slides — a caption line above date and location
   naming the people Immich recognised, joined as a natural list per locale,
   with a settings toggle
4. Numeral colours — each numeral takes the colour of the state at the first
   minute of its hour in the current half-day; play hours keep the neutral
   white, so only prepare and sleep hours stand out

Planned work and the backlog are tracked exclusively in GitHub issues (features
5–7 are #23, #24, #25), never in this file. README's Features section describes
what the app does today; update it when a feature ships.

## Architecture

Two Gradle modules: a pure-Kotlin `:core` (no Android deps) holds the logic;
the Android `:app` holds the screensaver, UI, and settings.

- `TvDreamService` — screensaver entry point; hosts a `ComposeView`.
- `DreamPreviewActivity` — in-app fullscreen preview of the dream.
- `DreamContent` / `DreamRoot` — shared Compose wiring for both.
- `SlideDeck` — slide rotation (analog clock / photos / agenda).
- `AnalogClockSlide` — the analog face (1–12 numerals, minute ticks, hands).
- `ClockOverlay` — always-on overlay: digital + colloquial + status text.
- `stateColor` — maps the active state to its colour; it tints the overlay
  status/colloquial text, the second hand, and the prepare/sleep numerals
  (play numerals stay neutral white).
- `ScheduleEngine` (`:core`) — `(now, config) → active state + status text`.
  Heavily unit-tested.
- `ColloquialTimeFormatter` (`:core`) — per-locale (`Ru`, `En`). Heavily
  unit-tested with case tables.
- `SettingsActivity` + `SettingsScreen` (D-pad TV UI) + `SettingsRepository`
  (Proto DataStore); `Localization` drives in-app language.

The pure-logic units (`ScheduleEngine`, `ColloquialTimeFormatter`) hold most of
the real complexity and get the most test coverage.

## Tech stack

- Kotlin 2.4.0 + Jetpack Compose
- Jetpack DataStore (Proto) for config
- Gradle 8.14.5 (wrapper-pinned), AGP 8.13.2, JDK 21
- `minSdk` 30 (Shield + Xiaomi Stick run Android 11); `compileSdk`/`targetSdk` 36
- Static analysis: ktlint-gradle 14.2.0, detekt 1.23.8
- Stays on the Gradle 8 line so every tool is a stable release; API 37 / AGP 9
  would force Gradle 9, where detekt has no stable build yet

## Development workflow

- Commits: Conventional Commits (feat/fix/docs/chore/test/ci/refactor/build),
  optional scope, imperative summary. No Co-Authored-By trailer; mark
  robot-authored commits with a `:robot:` emoji after the type
  (e.g. `feat: :robot: …`).
- History is linear. Work on short-lived feature branches. Integrate by:
  `git rebase main` on the branch, push and open a PR so CI runs, then once
  green `git switch main && git merge --ff-only <branch> && git push`. No
  squashing, no merge commits. (Interactive history curation is done by hand;
  this environment cannot run `git rebase -i`.)
- `main` is protected by a ruleset: linear history and signed commits.
  Commits are signed locally, so integrate by the local fast-forward push
  above — GitHub's server-side rebase/squash merge re-creates commits it
  cannot sign and is rejected.
- Canonical gate: `./gradlew verify` (root task) runs ktlint, detekt, all unit
  tests, and assemble across both modules. Use it everywhere — locally, in task
  gates, and in CI — instead of spelling out the task list.
- CI (GitHub Actions, workflow `CI`, job `build`), on push and pull_request:
  `./gradlew verify`, with Gradle caching.
- Testing: TDD for the pure-logic units (`ScheduleEngine`,
  `ColloquialTimeFormatter`) with case tables; pragmatic tests elsewhere. May
  tighten to full TDD later.
- Tooling: Gradle Kotlin DSL + version catalog (`libs.versions.toml`); ktlint
  (format) + detekt (static analysis); JUnit + kotlin.test.
- The TV is never connected to the workstation: no adb, no `installDebug`, no
  logcat. On-device testing is manual — `./gradlew :app:assembleDebug`, carry
  `app/build/outputs/apk/debug/app-debug.apk` over with LocalSend, install by
  hand. Anything the device must report back has to be visible on screen and
  copyable to the clipboard, because the clipboard is the only channel out.
- Immich source is cloned at `~/sources/github.com/immich-app/immich`
  (`main`, possibly stale; all release tags present). For API questions —
  which permission an endpoint needs, what a response contains — read the
  source at the tag matching the family server (`v2.7.5` as of 2026-08) via a
  detached worktree, e.g. `git worktree add --detach ../immich-v2.7.5 v2.7.5`,
  rather than guessing. Permissions live in the `@Authenticated({ permission })`
  decorator on each controller method and as `x-immich-permission` per
  operation in `open-api/immich-openapi-specs.json`.

## Conventions

- Kotlin over Java. Newest stable tooling.
- Code navigation: Serena's symbolic tools are the default for symbol-shaped
  questions (definitions, references, file structure, symbol edits). Use
  grep/rg only for non-symbolic sweeps (string literals, resource IDs, config
  keys).
- Shell usage: prefer dedicated file tools (read/edit/write, Serena) over shell
  one-liners. Do not run inline Bash that leans on escaping, quoting gymnastics,
  variable expansion, heredocs, or multi-line here-strings — these prompt for
  manual approval and are error-prone. When a script is genuinely unavoidable,
  write it to a file under `tmp/` (gitignored) and run that file.
- Scratch/throwaway files go in repo-local `tmp/` (gitignored), never `/tmp`.
- Design specs live in `docs/superpowers/specs/`.
- Markdown: no bold/italic for inline emphasis in prose (READMEs, specs, docs).
  Convey emphasis with sentence structure; reserve markup for headings, lists,
  and code spans.

## Current status

Features 1–4 are built and merged. The first-time-key-entry
focus risk in Immich setup is accepted — fix it only if it recurs. The backlog
and planned features live in GitHub issues. See the latest specs in
`docs/superpowers/specs/`.
