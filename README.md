# Reverie

> The user-facing name is a localized app label: Reverie in English, Грёзы in
> Russian.

An Android TV screensaver (`DreamService`) for the living-room big screen. It
shows an always-visible, kid-friendly clock over a rotating deck of slides:
family photos anchored around the current date, and tomorrow's agenda.

Target devices: NVIDIA Shield and Xiaomi TV Stick, at 1080p or higher.

## Features

What the app does today:

1. Kid-friendly dual clock — the time in the locale's convention
   (24-hour in Russia) plus a spoken, colloquial form kids can read aloud
   (RU: `без четверти десять`; EN: `quarter to ten`). Colors follow the time of
   day across three states — play, prepare for sleep, sleep — to give
   pre-literate children a glanceable cue. Behind the overlay an analog-clock
   slide renders a full 1–12 face with minute ticks; its second hand carries the
   current state color.
2. Immich photos — slides drawn from an [Immich](https://immich.app)
   server, showing photos within ±N days of today across every past year, with
   local-network pairing to bring an Immich key over from a phone.
3. People names — photo slides captioned with the names of the people
   Immich recognised in them, joined the way you would say them aloud.
4. Numeral colours — each numeral on the analog face takes the colour
   of the state the schedule assigns to the first minute of that hour in the
   current half-day; play hours keep the neutral white, so only the prepare
   and sleep hours stand out around the dial.

Planned work is tracked in the
[GitHub issues](https://github.com/aensidhe/ru.aensidhe.dreamclock/issues).

A companion settings screen, navigable by TV remote, configures language and
display options and hands off to the system screensaver picker.

## Status

Features 1–4 are built: the clock and analog face, the
Immich photo deck with local-network pairing, people captions, and
schedule-coloured numerals, along with the D-pad settings screen, an adaptive
launcher icon, and a TV banner. See the design specs under
[`docs/superpowers/specs/`](docs/superpowers/specs/).

## Tech stack

- Kotlin + Jetpack Compose, hosted in a `DreamService`
- Two modules: a pure-Kotlin `:core` (schedule + colloquial time) and an
  Android `:app`
- Jetpack DataStore (Proto) for configuration
- Companion `SettingsActivity` (D-pad TV UI) for configuration

## License

[MIT](LICENSE) © 2026 Anatoly Popov
