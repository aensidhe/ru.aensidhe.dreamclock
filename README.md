# TV Ambient Clock

> Working title — the final name is not decided yet.

An **Android TV screensaver** (`DreamService`) for the living-room big screen. It
shows an always-visible, kid-friendly clock over a rotating deck of slides:
family photos anchored around the current date, and tomorrow's agenda.

Target devices: **NVIDIA Shield** and **Xiaomi TV Stick**, at 1080p or higher.

## Features

Built feature by feature, in priority order:

1. **Kid-friendly dual clock** *(first)* — the time in the locale's convention
   (24-hour in Russia) plus a spoken, colloquial form kids can read aloud
   (RU: `без четверти десять`; EN: `quarter to ten`). Colors follow the time of
   day across three states — **play**, **prepare for sleep**, **sleep** — to give
   pre-literate children a glanceable cue. An analog-clock slide is included and
   also serves as the placeholder before the photo gallery exists.
2. **Immich photos** *(planned)* — slides drawn from an
   [Immich](https://immich.app) server, showing photos within ±N days of today
   across every past year.
3. **Agenda** *(planned)* — tomorrow's agenda rendered as a slide every N photos.

## Status

In design. See the design spec under
[`docs/superpowers/specs/`](docs/superpowers/specs/). No application code has
been scaffolded yet.

## Tech stack

- Kotlin + Jetpack Compose (latest stable), hosted in a `DreamService`
- Jetpack DataStore (Proto) for configuration
- Companion `SettingsActivity` for configuration UI

## License

[MIT](LICENSE) © 2026 Anatoly Popov
