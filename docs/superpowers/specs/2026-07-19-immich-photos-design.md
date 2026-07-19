# Feature 2 — Immich photos design

Status: approved design, ready for implementation planning.
Date: 2026-07-19.

## Goal

Turn the screensaver into an ambient photo frame built on the family's
self-hosted Immich server: show photos (and videos) taken around today's date
— within a window of plus-or-minus N days — across all past years, the "on this
week over the years" idea. The kid-friendly clock from feature 1 stays present
throughout; photos are additive and the screensaver must keep working when they
are absent.

Reference for inspiration only (not a dependency):
`giejay/Immich-Android-TV`. Its "similar time" feature is the closest analogue
and informed the date-window mechanics; its phone-pairing relay is explicitly
rejected here (see Credentials).

## Scope

In scope:

- Fetching photos and videos from Immich in the plus-or-minus-N-days window
  across past years.
- A continuous, shuffled slideshow with the always-on digital clock overlay and
  a periodic analog-clock slide.
- Portrait pairing, per-photo captions, videos with schedule-aware audio.
- Getting Immich credentials onto the TV without a cloud relay: manual entry and
  a local-network QR pairing flow.
- Settings for all of the above, plus a live Immich status line.
- Caching and a clean fallback to the clock when photos cannot be shown.

Out of scope (later features, unchanged): the schedule editor UI (feature 3) and
the agenda slide (feature 4). Album/person/tag filtering, casting, and search are
not part of this feature.

## Interaction model

- The digital and colloquial clock overlay is always on, drawn on top of every
  slide (photos, videos, and the analog slide).
- The slideshow is a continuous rotation of photo and video slides.
- An analog-clock slide is inserted every C photo-slides (the analog cadence, C
  configurable) and is shown for its own duration M, independent of the
  photo-slide interval M2.
- Photos are additive: if none are available the deck is just the analog clock,
  behaving like feature 1.

## Architecture and module split

Follows the existing convention: pure, deterministic logic in the Kotlin-only
`:core` module (heavily unit-tested, TDD); everything touching Android, the
network, or the GPU in `:app`.

### New in `:core` (pure, unit-tested)

- `SimilarTimeWindows` — given `now`, `daysEitherSide` (N), and a year offset,
  produce the `takenAfter`/`takenBefore` pair for that year's window. Handles
  month/year boundary crossings and leap days.
- Years-back walk policy — a pure function over "did year Y yield any photos"
  that walks years backward from the current year and stops after 20 consecutive
  empty years, honoring an optional maximum-years cap.
- `SlidePlanner` — a streaming state machine that turns an ordered asset stream
  into a slide sequence (see Slide model). Deterministic given its input.
- `PhotoCaption` — format a caption from an asset's EXIF: localized date and
  time plus location, omitting missing parts.
- Orientation helper — classify an asset as portrait or landscape from EXIF
  width/height and orientation.

### New in `:app`

- `ImmichClient` — Retrofit + OkHttp with an `x-api-key` header; endpoints for
  `POST /api/search/metadata`, plus `POST /api/auth/login` and
  `POST /api/api-keys` for the pairing mint path. Models via
  kotlinx.serialization.
- `ImmichRepository` — orchestrates per-year queries using `:core` windows,
  paginates fully, pools and shuffles, exposes the endless asset stream, and owns
  the credential-backed health check. Refreshes on config change and on local
  date rollover.
- `PhotoSlide` / `PairedPhotoSlide` (Compose) — Coil-loaded `preview` image with
  a `thumbnail` placeholder, plus the caption line(s).
- `VideoSlide` (Compose) — Media3 / ExoPlayer on `/video/playback`; full-clip
  playback; audio gated by the schedule state and the audio-mode setting.
- `PairingServer` — an embedded local HTTP server (Ktor CIO) that serves the
  phone page and receives the encrypted credential payload.
- `PairingCrypto` — AES-GCM decrypt of the pairing payload.
- `QrCode` — QR bitmap generation (ZXing core).
- `SlideDeck` changes — render the planned slide sequence with crossfade
  transitions and image preloading.
- Settings changes — proto, `SettingsRepository`, and `SettingsScreen` additions;
  Keystore-backed storage for the API key.

### New dependencies

All mainstream, stable, and compatible with the pinned Gradle 8 line: Retrofit +
OkHttp, kotlinx.serialization, Coil 3 (Compose), Media3 ExoPlayer, Ktor server
CIO, ZXing core, and Jetpack Security / Android Keystore for the API key.

## Credentials and pairing

Two ways in, with no cloud relay, no plaintext credentials on the wire, and no
dependency on Immich's CORS. The TV always talks to Immich with a native HTTP
client, so the browser same-origin policy never applies; a production Immich
sends no CORS headers (verified: `server/src/workers/api.ts` enables CORS only in
development), which rules out any phone-browser-to-Immich variant but does not
affect the TV.

### Manual entry

Fields in `SettingsScreen` for the Immich server URL and API key, typed with the
on-screen keyboard. Always available as the baseline.

### Local-network QR pairing

1. The TV starts `PairingServer` on its LAN address and generates a random
   256-bit AES-GCM key held only in memory.
2. The TV shows a QR encoding `http://<tv-ip>:<port>/#k=<base64url key>`. The key
   rides in the URL fragment, which the phone never transmits; it is read by the
   page's JavaScript.
3. The served page offers two modes:
   - paste an API key (plus host), or
   - enter email and password (plus host), for password-manager users.
4. The page AES-GCM-encrypts the JSON payload with the QR key (WebCrypto) and
   POSTs `{iv, ciphertext}` to the TV. An eavesdropper sees only ciphertext and
   never had the QR, so the plain-HTTP hop is safe.
5. The TV decrypts and talks to Immich directly:
   - API-key mode: validate the key with a cheap authenticated call, then save
     it.
   - Email/password mode: `POST /api/auth/login`, then `POST /api/api-keys` with
     `permissions: [asset.read, asset.view, asset.download]`, and store that
     read-only key. The password is used only for those two calls and is never
     persisted or echoed back.
6. The server stops once a valid credential is saved; the in-memory key is
   dropped.

This gives the reference's phone convenience without its relay: the "server in
the middle" is the TV itself, credentials never leave the LAN, and the minted key
is least-privilege rather than the relay's all-permissions key.

### Secret storage

The API key is stored via Android Keystore-backed encryption, not in the plain
Proto DataStore.

### Required Immich API key permissions

`asset.read`, `asset.view`, `asset.download`. The email/password mint path
additionally needs the logged-in session's ability to create an API key
(`apiKey.create`), which a normal user session has.

## Photo pipeline and fetch

- On dream start, and again whenever the local date rolls over (so "around today"
  tracks the calendar), `ImmichRepository` builds the query set from
  `SimilarTimeWindows`: the current year, year minus one, year minus two, and so
  on, each a `[today − N days, today + N days]` window shifted back by that year
  offset.
- It walks years backward until 20 consecutive empty years, honoring the optional
  maximum-years cap. Each older empty year costs one cheap query, then the walk
  stops.
- Per year: `POST /api/search/metadata` with `takenAfter` / `takenBefore`,
  `withExif = true`, images and videos both included, paginated at 100 per page.
  The repository loops through every page for the year, so a year with 1,000
  matching photos contributes all of them, not a capped 100. Only lightweight
  metadata is loaded up front (id, EXIF, city/country); image and video bytes are
  always lazy-loaded per slide.
- All years are pooled and shuffled once into the endless deck source. When the
  deck exhausts, it reshuffles and continues. Refetch on config change or date
  rollover.
- Each asset is normalized to: id; image or video; orientation (portrait or
  landscape); capture date; city and country.

Immich date format note (from the reference's hard-won experience): v3's
`takenBefore` / `takenAfter` require a trailing offset (`Z` or `±HH:mm`). Format
with the device zone attached (ISO offset date-time), not a zone-less local
date-time, or the server rejects it with a 400.

## Slide model

`SlidePlanner` consumes the shuffled asset stream and emits slides:

- A landscape photo or a video is one slide, shown full width.
- Portraits are paired two-up via a one-slot buffer:
  - on a landscape or video, emit it immediately (it plays while a pending
    portrait waits);
  - on a portrait, if one is already pending, emit the two as a paired slide and
    clear the buffer; otherwise hold this one as pending.
  - The pending portrait is held indefinitely (no cap) and carries across the
    reshuffle boundary, pairing with the first portrait of the next cycle. As a
    result a solo portrait effectively never renders; the only theoretical
    leftover is one portrait pending when the screensaver stops, which no one
    sees.
- An analog-clock slide is injected every C photo-slides (the analog cadence).

Example: for the stream portrait, landscape, portrait, the planner emits the
landscape alone, then the two portraits paired (the first portrait waits through
the landscape).

### Timing and transitions

- Photo or paired slide: M2 seconds.
- Analog-clock slide: M seconds (independent of M2).
- Video slide: until the clip ends, then advance.
- Crossfade between slides; the next image is preloaded via Coil so the swap is
  smooth.

### Image variant

Photos display Immich's `preview` variant
(`GET /api/assets/{id}/thumbnail?size=preview`) with the small `thumbnail` as a
fast progressive placeholder. The `original` is not used — `preview` is sharp on
a 1080p/4K TV and far lighter. Videos play from
`GET /api/assets/{id}/video/playback`.

## Captions and overlay coexistence

- The caption sits at the bottom-right of the photo, two lines, formatted in the
  selected locale:

  ```
  19 July 2026 | 14:32
  Berlin, Germany
  ```

  Line 1 is date and time separated by a pipe; line 2 is the location (city,
  country). Any missing part is dropped; if both lines are empty, no caption is
  drawn.
- On a paired-portrait slide, each half gets its own bottom-right caption under
  its own photo.
- The always-on clock overlay draws the digital time at top-left and the status
  text plus colloquial form in a column at bottom-left. On a single photo the
  caption (bottom-right) and the clock (bottom-left) do not collide.
- On a paired-portrait slide whose left photo has a non-empty caption, the clock
  overlay's bottom-left group (both the status text and the colloquial line) is
  suppressed for that slide, because both would otherwise sit over the left photo
  and clash with its caption. The top-left digital time stays.

## Videos and audio

- Videos are in scope and play their full clip with sound available.
- Audio mode is a three-way setting:
  - respect schedule (default): audio plays in active states and auto-mutes
    during quiet / sleep states from `ScheduleEngine`;
  - force mute: never play audio;
  - force unmute: always play audio regardless of state.
- A video that fails to play is skipped to the next slide.

## Settings surface

Proto additions to `Settings`:

- `photos_enabled` (bool)
- `immich_host` (string)
- `days_either_side` (int, N)
- `max_years_back` (int, 0 = all available years)
- `photo_interval_seconds` (M2)
- `analog_every_n_slides` (C, the analog cadence)
- `analog_slide_seconds` (M)
- `video_audio_mode` (enum: RESPECT_SCHEDULE, FORCE_MUTE, FORCE_UNMUTE)

The API key is not in the proto; it lives in Keystore-backed storage.

`SettingsScreen` gains an Immich section with: a start-QR-pairing action, manual
host and key fields, steppers for the numeric options, the audio-mode switch, and
a live status line. The status line reflects the saved credentials' health, for
example `Connected · <host> · 42 photos today`, or `Not configured`,
`Unreachable`, or `Invalid key`. All labels are localized (Ru / En) via
`SettingsLabels`.

## Caching and error handling

- Coil disk cache for `preview` and `thumbnail` bytes (size-capped) gives
  brief-outage resilience and smooth revisits.
- If a refetch fails but an earlier asset list from today is in hand, keep using
  it.
- When nothing is usable — not configured, unreachable, or the window is
  genuinely empty — fall back to the full-screen analog clock with the digital
  overlay still on, and no error text. The screensaver always works.

## Testing

- `:core` TDD with case tables: `SimilarTimeWindows` (leap-day and month/year
  boundary crossings); the years-back walk/stop policy; `SlidePlanner` (buffered
  pairing including carry-over across reshuffle, clock-every-N interleaving, and
  mixed portrait/landscape/video runs); `PhotoCaption` (every present/absent
  combination of date and location); orientation classification.
- `:app` pragmatic tests: Immich search-request construction; seeded pool and
  shuffle; the pairing encrypt/decrypt round-trip; and the fallback decision
  logic. These run against OkHttp `MockWebServer` with canned JSON fixtures — no
  live Immich server is needed, so CI stays hermetic.
- On-device manual validation for the Compose slides, video playback, and the QR
  pairing flow.

## Open questions and risks

- Login constraints: the email/password mint path assumes a local password
  without TOTP two-factor and not an OAuth-only account; Immich's OAuth is
  authorization-code with redirect and has no device grant, so it is not a
  TV-friendly alternative. Manual API-key entry covers accounts where the mint
  path does not apply.
- Held-portrait latency: with a long run of landscapes a pending portrait can
  wait a while before pairing; accepted by design (unbounded hold).
- Video weight: transcoded playback and full-clip duration can hold the screen
  and delay the next clock slide; acceptable, revisit if it feels long on-device.
