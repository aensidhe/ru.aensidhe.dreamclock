# CLAUDE.md

Project memory for Claude Code. Keep it concise and high-signal.

## What this is

An **Android TV screensaver** (`DreamService`) — a kid-friendly ambient clock
over a rotating slide deck (photos, agenda). Target devices: NVIDIA Shield and
Xiaomi TV Stick, ≥1080p.

Package (`applicationId`): `ru.aensidhe.dreamclock`. The user-facing app name is
still a working title ("TV Ambient Clock"). The repo directory is still named
`ru.aensidhe.tv.calendar` — harmless; the `applicationId` is what matters.

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
- `ScheduleEngine` — **pure Kotlin, no Android deps**: `(now, config) → active
  state + status text`. Heavily unit-tested.
- `ColloquialTimeFormatter` — **pure Kotlin**, per-locale (`Ru`, `En`). Heavily
  unit-tested with case tables.
- `SettingsActivity` + `SettingsRepository` (Proto DataStore).

The pure-logic units (`ScheduleEngine`, `ColloquialTimeFormatter`) hold most of
the real complexity and get the most test coverage.

## Tech stack

- Kotlin + Jetpack Compose (latest stable)
- Jetpack DataStore (Proto) for config
- minSdk covers Shield + Xiaomi Stick (confirm against real device OS versions)

## Conventions

- Kotlin over Java. Newest stable tooling.
- Scratch/throwaway files go in repo-local `tmp/` (gitignored), never `/tmp`.
- Design specs live in `docs/superpowers/specs/`.

## Current status

In design. Repo scaffolded with meta files only; no app code yet. See the
latest spec in `docs/superpowers/specs/`.
