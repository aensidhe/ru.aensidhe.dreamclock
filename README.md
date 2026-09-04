# Reverie

> The user-facing name is a localized app label: Reverie in English, Грёзы in
> Russian.

An Android TV screensaver (`DreamService`) for the living-room big screen. It
shows an always-visible, kid-friendly clock over a rotating deck of slides:
family photos anchored around the current date, and tomorrow's agenda.

Target devices: NVIDIA Shield and Xiaomi TV Stick, at 1080p or higher.

## Features

Built feature by feature, in priority order:

1. Kid-friendly dual clock (built) — the time in the locale's convention
   (24-hour in Russia) plus a spoken, colloquial form kids can read aloud
   (RU: `без четверти десять`; EN: `quarter to ten`). Colors follow the time of
   day across three states — play, prepare for sleep, sleep — to give
   pre-literate children a glanceable cue. Behind the overlay an analog-clock
   slide renders a full 1–12 face with minute ticks; its second hand carries the
   current state color.
2. Immich photos (built) — slides drawn from an [Immich](https://immich.app)
   server, showing photos within ±N days of today across every past year, with
   local-network pairing to bring an Immich key over from a phone. Video playback
   is deferred.
3. People names (built) — photo slides captioned with the names of the people
   Immich recognised in them, joined the way you would say them aloud.
4. Tick colours (built) — the minute ticks show the current hour: each tick
   takes the colour of the state the schedule assigns to that minute, in sync
   with the status text, and the last four ticks carry a growing share of the
   coming hour's colour before the ring turns over on the hour.
5. Schedule editor (planned) — a D-pad UI over the existing schedule model
   (time windows, day-of-week, date overrides).
6. Agenda (planned) — tomorrow's agenda rendered as a slide every N photos.

A companion settings screen, navigable by TV remote, configures language and
display options and hands off to the system screensaver picker.

## Status

Feature 1 is built and running on-device: the clock, the analog face, the
D-pad settings screen, an adaptive launcher icon, and a TV banner. Feature 2's
Immich photo deck, settings, and local-network pairing are built and validated
on-device; video playback is deferred. Feature 3's people captions are built
and validated on-device. Feature 4's schedule-coloured tick ring is built,
pending on-device validation. Features 5–6 are planned. See the design specs
under
[`docs/superpowers/specs/`](docs/superpowers/specs/).

## Tech stack

- Kotlin + Jetpack Compose, hosted in a `DreamService`
- Two modules: a pure-Kotlin `:core` (schedule + colloquial time) and an
  Android `:app`
- Jetpack DataStore (Proto) for configuration
- Companion `SettingsActivity` (D-pad TV UI) for configuration

## License

[MIT](LICENSE) © 2026 Anatoly Popov
