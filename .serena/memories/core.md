# Core Project Overview

**Project**: Reverie (ru.aensidhe.dreamclock) — Android TV screensaver with dual clock, photo slide deck, and agenda.

**Repository**: `/mnt/second/sources/github.com/aensidhe/ru.aensidhe.dreamclock`

**Target devices**: NVIDIA Shield, Xiaomi TV Stick (≥1080p)

**Build order (feature priorities)**:
1. Kid-friendly dual clock (locale time + colloquial spoken form + 3 color states)
2. Immich photos (±N days around today)
3. Agenda slide every N photos

**Architecture**:
- `TvDreamService` — screensaver entry point with `ComposeView`
- `SlideDeck` — slide rotation (clock/photos/agenda)
- `ClockOverlay` — always-on overlay: digital + colloquial + status text
- `ScheduleEngine` — pure Kotlin, heavily unit-tested
- `ColloquialTimeFormatter` — per-locale (Ru/En), pure Kotlin
- `SettingsActivity` + `SettingsRepository` (Proto DataStore)

See `mem:tech_stack`, `mem:conventions`, `mem:task_completion`.
