# CLAUDE.md

Project memory for Claude Code. Keep it concise and high-signal.

## What this is

An Android TV screensaver (`DreamService`) — a kid-friendly ambient clock
over a rotating slide deck (photos, agenda). Target devices: NVIDIA Shield and
Xiaomi TV Stick, ≥1080p.

Package (`applicationId`): `ru.aensidhe.dreamclock`. The user-facing app name is
Reverie (Russian: Грёзы), shown as a localized app label.

## Build order (do not skip ahead)

Documented goals, built one at a time. Build feature 1 first; features 2 and 3
are recorded scope, not yet designed in full.

1. Kid-friendly dual clock (locale time + colloquial spoken form + 3 color states)
2. Immich photos (±N days around today, across all past years)
3. Agenda slide every N photos

## Architecture

- `TvDreamService` — screensaver entry point; hosts a `ComposeView`.
- `SlideDeck` — slide rotation (analog clock / photos / agenda).
- `ClockOverlay` — always-on overlay: digital + colloquial + status text.
- `ScheduleEngine` — pure Kotlin, no Android deps: `(now, config) → active
  state + status text`. Heavily unit-tested.
- `ColloquialTimeFormatter` — pure Kotlin, per-locale (`Ru`, `En`). Heavily
  unit-tested with case tables.
- `SettingsActivity` + `SettingsRepository` (Proto DataStore).

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
- CI (GitHub Actions), on push and pull_request: `assemble`, unit `test`,
  `ktlintCheck`, `detekt`, with Gradle caching. Added once the Gradle project
  exists.
- Testing: TDD for the pure-logic units (`ScheduleEngine`,
  `ColloquialTimeFormatter`) with case tables; pragmatic tests elsewhere. May
  tighten to full TDD later.
- Tooling: Gradle Kotlin DSL + version catalog (`libs.versions.toml`); ktlint
  (format) + detekt (static analysis); JUnit + kotlin.test.

## Conventions

- Kotlin over Java. Newest stable tooling.
- Scratch/throwaway files go in repo-local `tmp/` (gitignored), never `/tmp`.
- Design specs live in `docs/superpowers/specs/`.
- Markdown: no bold/italic for inline emphasis in prose (READMEs, specs, docs).
  Convey emphasis with sentence structure; reserve markup for headings, lists,
  and code spans.

## Current status

In design. Repo scaffolded with meta files only; no app code yet. See the
latest spec in `docs/superpowers/specs/`.
