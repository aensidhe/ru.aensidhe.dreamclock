# Immich Settings, Credentials, and Cadence — Design

This is Plan 4 of the Immich photos feature. It turns the photo pipeline built
in Plans 1–3 into something a user can configure and secure on the device, and
it reworks the clock cadence to a predictable wall-clock schedule. After this
plan, photos are enterable, storable, and testable end to end on-device — the
milestone that unblocks on-device photo validation.

## Goal

Give the screensaver a D-pad settings surface for Immich (enable, host, key,
numeric config), store the API key encrypted via the Android Keystore, verify
credentials with a live status line, replace the count-based clock cadence with
a predictable wall-clock schedule, and refetch when the local date rolls over.

## Scope

In scope:

- Proto schema changes for the new/renamed settings and the encrypted key blob.
- A Keystore-backed cipher and a settings-driven `CredentialsStore`.
- Removal of the `BuildConfigCredentials` / `local.properties` bootstrap.
- A new Immich section in the settings screen with text fields and steppers.
- An on-demand health probe feeding a localized status line.
- A pure predictable-cadence scheduler in `:core`, replacing `ClockGapPolicy`.
- A per-host oldest-date cache guiding the backward year walk.
- Date-rollover refetch in the dream.
- Ru/En localization for every new label and status string.

Not in scope (deferred):

- QR / local-network pairing and email-password key minting (Plan 5).
- Video and audio (Plan 6). The cadence rule for assets that straddle a clock
  mark is defined only for photos here; long videos are revisited in Plan 6.

## Proto schema changes (`app/src/main/proto/settings.proto`)

The last build was never deployed, so no install carries persisted settings and
the schema can be broken freely. The three freed field numbers are reused
directly — no `reserved` statements, no new field numbers appended:

- Field 9: `max_years_back` → `max_empty_years_back` (`int32`), renamed with the
  new meaning.
- Field 11: `analog_every_n_slides` → `shown_every_xth_minute` (`int32`).
- Field 12: `max_clock_gap_seconds` → `immich_key_ciphertext` (`bytes`).

Fields 1–8, 10, and 13 are unchanged. `immich_host` (field 7) stays plaintext —
a host URL is not a secret. The API key never appears in the proto in plaintext;
only its ciphertext does.

`SettingsSerializer.defaultValue` updates: `days_either_side = 15`,
`photo_interval_seconds = 30`, `analog_slide_seconds = 30`,
`shown_every_xth_minute = 5`, `max_empty_years_back = 20`. `photos_enabled`
stays `false` and `immich_host` stays empty, so a fresh install still shows the
feature-1 clock with no photos.

## Credential storage: `KeystoreCipher` (`:app`)

A new `KeystoreCipher` wraps an AES-256-GCM key held in `AndroidKeyStore` under
a fixed alias, created lazily on first use:

- `encrypt(plaintext: String): ByteArray` — returns the GCM IV followed by the
  ciphertext, so a single blob round-trips without a separately stored IV.
- `decrypt(blob: ByteArray): String` — splits the IV, decrypts, returns UTF-8.

The key is not bound to user authentication: the screensaver must decrypt
unattended while the device is idle. The Keystore still keeps the raw key
non-exportable and hardware-backed where available, so the encrypted proto blob
is not usable off-device.

The encrypted key blob lives in the Settings proto (`immich_key_ciphertext`).
The DataStore file is app-private, and the Keystore layer adds
defense-in-depth against backup extraction or offline inspection. If the
implementation's security review finds proto storage inadequate, the blob moves
to a separate app-private file; the cipher and store interfaces do not change.

## Credential seam: `CredentialsStore`

The interface changes from a zero-arg snapshot to a settings-driven mapping so
the dream rebuilds when the stored host or key changes:

```
interface CredentialsStore {
    fun credentials(settings: Settings): ImmichCredentials?
}
```

- `NoCredentialsStore` returns `null` (kept as the default for previews, tests,
  and the no-credentials path).
- `KeystoreCredentialsStore(cipher: KeystoreCipher)` returns
  `ImmichCredentials(host, key)` when `immich_host` is non-blank and
  `immich_key_ciphertext` is non-empty (decrypting the key), else `null`.

`TvDreamService` and `DreamPreviewActivity` construct a `KeystoreCredentialsStore`
in place of `BuildConfigCredentials.store()`. `DreamContent` already collects
`settings`; it calls `credentialsStore.credentials(settings)` keyed on
`immich_host` + `immich_key_ciphertext`, so editing either in the UI rebuilds
the deck.

`BuildConfigCredentials.kt`, the gradle `IMMICH_HOST` / `IMMICH_KEY`
`BuildConfig` fields, and the `local.properties` bootstrap are deleted — the UI
is the only credential source.

## Settings UI (`SettingsScreen`)

A new section renders after the existing rows:

- `SectionHeader` "Immich photos".
- `ToggleRow` — Enable photos (`photos_enabled`).
- When enabled, reveal:
  - `TextFieldRow` — Host (`immich_host`), URI keyboard, commits on
    focus-leave / IME-done.
  - `TextFieldRow` — API key, visually masked as dots. On commit the plaintext
    is encrypted through `KeystoreCipher` and written to
    `immich_key_ciphertext`; plaintext lives only transiently in field state and
    is never written to the proto or logged.
  - A Test connection `Button` and a status-line `Text`.
  - `StepperRow` for each numeric setting.

New composables:

- `TextFieldRow(label, value, isSecret, onCommit)` — D-pad-focusable TV text
  entry using the platform soft keyboard.
- `StepperRow(label, value, range, step, onChange)` — decrement / increment with
  D-pad left/right, clamped to the range.

Stepper bounds and defaults:

| Setting | Range | Step | Default |
| --- | --- | --- | --- |
| `days_either_side` | 0–30 | 1 | 15 |
| `max_empty_years_back` | 1–50 | 1 | 20 |
| `photo_interval_seconds` | 3–60 | 1 | 30 |
| `shown_every_xth_minute` | 1–60 | 1 | 5 |
| `analog_slide_seconds` | 3–60 | 1 | 30 |

Clamping is a pure function so it can be unit-tested off-device.

## Health probe

A suspend `probe(credentials): ProbeResult` (on `ImmichRepository`, or a small
`ImmichHealth` helper) issues one authenticated `searchMetadata` for today's
±`days_either_side` window with `size = 1`. This exercises exactly the
permission and endpoint the deck needs and returns a count. `ProbeResult` is a
sealed type mapping to a localized status line:

- `Checking` → "Checking…"
- `Reachable(count)` → "Connected — N photos for today" when the search response
  reports a total, otherwise "Connected"
- `Unauthorized` → "Authorization failed"
- `Unreachable` → "Host unreachable"
- `Error(detail)` → "Error: <detail>"

The count is best-effort: it uses the total the search response exposes for the
window; when the response carries no usable total, the success state is a bare
"Connected". Reachability and authorization are the load-bearing signals.

The server error is not suppressed. On an unexpected response — a non-2xx status
that is not a plain 401, or an unparseable body — the probe captures the
response body (or, for a thrown non-network error, the exception message), trims
it to about 100 characters, and surfaces it in the status line, so the actual
cause is visible on the device rather than hidden behind a generic message.

The probe runs in the screen's coroutine scope from the Test button, and once
automatically when the screen opens if credentials are already stored. It never
fires while typing. The result → label mapping is a pure function and is
unit-tested.

## Predictable clock cadence (`:core`)

The count-based cadence (`ClockGapPolicy.shouldForceClock` plus the
`analog_every_n_slides` count) is replaced by a wall-clock schedule. Clock marks
occur at wall-clock minutes that are multiples of `shown_every_xth_minute`,
counted from the top of each hour and reset at each hour boundary. For X = 5 the
marks are :00, :05, :10, … The mark set restarts at :00, so an X that does not
divide 60 simply yields a shorter final interval before the next hour.

A new pure scheduler decides, at each slide boundary, whether the next slide is
the clock or an asset. At a boundary at time `f` (deck start, or when the
previous slide ends):

- Let `T` be the smallest mark `>= f`.
- If `T - f < 60s`: show the clock now, over the window `[f, T + analog_slide_seconds)`.
  An early finish lengthens the clock; it always stays `analog_slide_seconds`
  past the mark.
- Otherwise: play the next asset for `photo_interval_seconds` (photos).

Because an asset only starts when the next mark is at least 60s away and a photo
runs at most 60s, photos never straddle a mark. Long videos (Plan 6) can, since
decisions are made only at boundaries; that case is deferred.

The clock slide is the analog face when `show_analog_slide` is on, otherwise the
existing null-deck presentation (black with the always-on digital overlay). The
scheduler consumes the current instant plus the zone; it holds no elapsed-gap
state, so it is deterministic and case-table testable. `ClockGapPolicy` and the
`max_clock_gap_seconds`-driven branch of `SlideDriver` are removed; `SlideDriver`
is reworked to consult the scheduler.

## Year walk and per-host history cache

The backward year walk gains a per-host cache and drops the old hard year cap.

`PhotoHistoryStore` (`:app`), a dedicated DataStore, keeps
`map<string host, int32 oldestYear>` — the oldest year that returned photos for
each host — plus the host of the most recent successful connectivity test. The
walk decides termination by year, so the year is all the cache needs to store;
the oldest year is read directly from the walk loop, without reading per-asset
dates. `ImmichRepository.loadAssets` reads the cached oldest year for the current
host to bound the walk and writes back the oldest year that returned photos after
each successful load.

The cache resets on host change, detected at the connectivity test: when a test
succeeds against a host that differs from the last successfully tested host, the
store clears that host's cached oldest date (the next walk for it rediscovers
from the current year) and records the new host as the last tested one. Changing
only the key, with the host unchanged, does not reset. The reset fires only on a
successful test, never on a failed one or on mere field edits.

`YearWalk` is reworked. Its current hardcoded `MAX_EMPTY_STREAK = 20` and
`maxYearsCap` (with the "0 = all" rule) are removed. The walk queries years from
the current year downward, and termination now depends on the cache:

- A year at or above the cached oldest year (or the current year when there is
  no cache yet) is always queried; an empty one does not count toward stopping,
  because known-populated history lies below it.
- A year below the cached oldest is queried and counted. `max_empty_years_back`
  consecutive empty years stops the walk. A populated year resets the streak and
  becomes the new cached oldest.

After the walk, the cache updates to the oldest year that returned photos. This
lets newly-added older photos push the boundary further back over successive
runs, and it prevents a run of empty recent years near today from ending the
walk prematurely — a latent bug in the hardcoded version.

Worked example: cached oldest 2001, `max_empty_years_back = 10`, nothing below
2001 → query 2026…2001 unconditionally, then 2000…1991 empty → stop at 1991. If
1995 held photos, the streak resets there, the cache becomes 1995, and the walk
continues to about 1985.

`PhotoFetchConfig.maxYearsBack` is renamed to `maxEmptyYearsBack`, and
`loadAssets` additionally takes the cached oldest year (and reports the oldest
populated year) so `DreamContent` can persist the cache update.

## Date-rollover refetch

`DreamContent` derives today's `LocalDate` from the same clock source it already
ticks on and keys the deck builder on it alongside credentials and the relevant
settings. At local midnight the date changes, the deck rebuilds, and the
±`days_either_side` window re-centers on the new day. No `WorkManager`: the
screensaver only runs while displayed.

## Localization

Every new label and status string gets Ru and En resources, following the
existing `SettingsLabels` / `stringResource` pattern and the app's in-app
language override. New strings: the section header, enable/host/key labels, the
Test button, the five stepper labels, and the five probe status states.

## Testing

Pure units get case-table coverage in `:core`, matching `ScheduleEngine`:

- Cadence scheduler: early finish inside the 60s window, boundary landing
  exactly on a mark, a boundary far from any mark, the hour rollover, and X
  values that do and do not divide 60.
- `YearWalk` with the cache boundary: empty years above vs. below the cached
  oldest, streak reset on a populated year, and the `max_empty_years_back`
  threshold.

`:app` JVM tests use a fake cipher (the real `AndroidKeyStore` cannot run on the
JVM):

- `KeystoreCredentialsStore.credentials(settings)` — blank host or empty blob →
  null; both present → decrypted credentials.
- Stepper clamping.
- `ProbeResult` → status-label mapping, including error-detail truncation to
  about 100 characters.
- History-cache resolution of the walk boundary, and the host-change reset:
  a successful test against a changed host clears that host's oldest date; a
  key-only change keeps it.

The `AndroidKeyStore` encrypt/decrypt round-trip is validated on-device, not on
the JVM.

## On-device validation checklist (moved from Plan 3)

Plan 3's Task 12 on-device photo validation moves here as the plan's final task,
authored as copy-pasteable `- [ ]` items so it can be worked through directly on
the device. Plan 3's Task 12 is trimmed to keep only its CI build gate (Plan 3
is already merge-ready). The checklist covers: entering host and key in the new
UI, pressing Test and reading each status state, enabling photos, single and
paired photo rendering with bottom-right captions, caption-vs-clock suppression,
the clock appearing on the predictable schedule, the early-clock overrun near a
mark, crossfade and preload, missing-EXIF captions, date-rollover refetch, and a
final check that no secret is staged and the CI gate is green.

## Cleanup

- Delete `BuildConfigCredentials.kt`.
- Remove the `IMMICH_HOST` / `IMMICH_KEY` `BuildConfig` fields and their
  `local.properties` reads from the gradle build.
- Remove `ClockGapPolicy` and the `max_clock_gap_seconds` cadence branch.
