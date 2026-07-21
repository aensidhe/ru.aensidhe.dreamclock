# Immich Settings, Credentials, and Cadence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Immich photos configurable and secure on the device — a D-pad
settings surface, a Keystore-encrypted API key, a live connectivity probe, a
predictable wall-clock clock cadence, per-host history caching, and
date-rollover refetch.

**Architecture:** Pure logic lands in `:core` (a `PredictableClock` cadence
scheduler and a cache-aware `YearWalk`), tested with case tables like
`ScheduleEngine`. The `:app` module gains a Keystore cipher, a settings-driven
`CredentialsStore`, a `PhotoHistoryStore` (Proto DataStore), a health probe, and
new settings composables. The deck-driving chain (`SlidePlanner`, `SlideDriver`,
`SlideDeckModel`, `SlideDeck`) is refactored from count-based to time-driven, and
`DreamContent` is rewired across three integration tasks. The proto reuses its
freed field numbers directly.

**Tech Stack:** Kotlin 2.4.0, Jetpack Compose (TV Material3), Proto DataStore,
AndroidKeyStore (AES-GCM), Retrofit + kotlinx.serialization, JUnit + kotlin.test.

## Global Constraints

- `:core` has no Android dependencies; `PredictableClock` and `YearWalk` use only
  `java.time` and Kotlin stdlib.
- The API key never appears in plaintext in the proto or in logs. Only its
  AES-GCM ciphertext (`immich_key_ciphertext`) is persisted.
- The Keystore AES key is not bound to user authentication — the screensaver must
  decrypt unattended.
- Credentials come only from the settings UI. `BuildConfigCredentials`, the
  gradle `IMMICH_HOST` / `IMMICH_KEY` fields, and the `local.properties` bootstrap
  are deleted by the end of the plan.
- The proto reuses freed field numbers in place: field 9 → `max_empty_years_back`
  (`int32`), field 11 → `shown_every_xth_minute` (`int32`), field 12 →
  `immich_key_ciphertext` (`bytes`). No `reserved`, no appended numbers.
  Field 12 also changes wire type at the same number (`int32 max_clock_gap_seconds`
  → `bytes immich_key_ciphertext`). This is safe only because the app is unreleased
  (`versionCode` 1, no persisted store carries the old varint value) and
  protobuf-lite drops a known field whose wire type mismatches to the unknown-field
  set rather than throwing. Once the app ships, reusing a live field number with a
  new wire type would corrupt existing stores and must not be done.
- Stepper bounds/defaults: `days_either_side` 0–30 (15), `max_empty_years_back`
  1–50 (20), `photo_interval_seconds` 3–60 (30), `shown_every_xth_minute` 1–60
  (5), `analog_slide_seconds` 3–60 (30).
- Clock schedule: marks at wall-clock minutes that are multiples of
  `shown_every_xth_minute`, counted from the top of each hour, reset at each hour.
  At a slide boundary at time `f`, let `T` be the smallest mark `>= f`; if
  `T - f < 60s` show the clock over `[f, T + analog_slide_seconds)`, else play the
  next asset for `photo_interval_seconds`.
- Year walk: a year at or above the cached oldest year (or the current year when
  no cache) is always queried and does not count toward stopping; a year below it
  is counted, and `max_empty_years_back` consecutive empty years stops the walk; a
  populated year resets the streak and becomes the new cached oldest. The cache
  resets on host change, detected at a successful connectivity test; a key-only
  change does not reset.
- Every new user-facing string has Ru and En resources.
- ktlint line length ≤ 120. Commits use Conventional Commits with a `:robot:`
  emoji after the type; no Co-Authored-By trailer.
- CI gate: `./gradlew ktlintCheck detekt :core:test :app:testDebugUnitTest assemble`.

## File Structure

Created:

- `core/.../core/photos/PredictableClock.kt` — pure cadence scheduler.
- `core/.../core/photos/PredictableClockTest.kt` — case tables.
- `core/.../core/photos/YearWalkTest.kt` — extend for the cache-aware rework
  (may already exist; add cases).
- `app/.../immich/KeyCipher.kt` — cipher interface + `KeystoreCipher` + errors.
- `app/.../immich/CredentialsStores.kt` (or edits in `CredentialsStore.kt`) —
  `KeystoreCredentialsStore`.
- `app/.../immich/PhotoHistory.kt` — pure oldest-year resolution + host reset.
- `app/src/main/proto/photo_history.proto` — history message.
- `app/.../immich/PhotoHistorySerializer.kt` + `PhotoHistoryStore.kt` — DataStore.
- `app/.../immich/ImmichHealth.kt` — `ProbeResult` + `probe()` + label mapping.
- `app/.../settings/SettingsRows.kt` — `StepperRow`, `TextFieldRow` composables.
- Test files under `app/src/test/.../immich` and `.../settings`.

Modified:

- `app/src/main/proto/settings.proto`, `SettingsSerializer.kt`.
- `core/.../core/photos/SlidePlanner.kt`, `YearWalk.kt`; delete `ClockGapPolicy.kt`.
- `app/.../immich/SlideDriver.kt`, `SlideTiming.kt` (delete), `ImmichRepository.kt`,
  `ImmichCredentials.kt`, `CredentialsStore.kt`; delete `BuildConfigCredentials.kt`.
- `app/.../ui/SlideDeckModel.kt`, `SlideDeck.kt`, `DreamRoot.kt`.
- `app/.../dream/DreamContent.kt`, `TvDreamService.kt`, `DreamPreviewActivity.kt`.
- `app/.../settings/SettingsScreen.kt`, `SettingsLabels.kt`, `strings.xml` (en +
  ru), `SettingsRepository.kt` (only if a shared DataStore helper is extracted).
- `app/build.gradle.kts` (remove immich BuildConfig fields).
- `docs/superpowers/plans/2026-07-20-3-immich-photo-rendering.md` (trim Task 12).

---

## Task 1: PredictableClock cadence scheduler (`:core`, pure)

**Files:**
- Create: `core/src/main/kotlin/ru/aensidhe/dreamclock/core/photos/PredictableClock.kt`
- Test: `core/src/test/kotlin/ru/aensidhe/dreamclock/core/photos/PredictableClockTest.kt`

**Interfaces:**
- Produces: `PredictableClock.nextMark(from: LocalDateTime, everyXthMinute: Int): LocalDateTime`,
  `clockDue(now: LocalDateTime, everyXthMinute: Int): Boolean`,
  `clockDuration(now: LocalDateTime, everyXthMinute: Int, analogSeconds: Int): Duration`.

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.core.photos

import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PredictableClockTest {
    private fun t(h: Int, m: Int, s: Int = 0) = LocalDateTime.of(2026, 7, 20, h, m, s)

    @Test
    fun nextMarkExactlyOnMarkReturnsSame() {
        assertEquals(t(12, 5), PredictableClock.nextMark(t(12, 5), 5))
    }

    @Test
    fun nextMarkMidIntervalRoundsUp() {
        assertEquals(t(12, 5), PredictableClock.nextMark(t(12, 4, 30), 5))
        assertEquals(t(12, 10), PredictableClock.nextMark(t(12, 6), 5))
    }

    @Test
    fun nextMarkPastMarkSecondsRoundsToNext() {
        assertEquals(t(12, 10), PredictableClock.nextMark(t(12, 5, 1), 5))
    }

    @Test
    fun nextMarkWrapsToTopOfNextHour() {
        assertEquals(t(13, 0), PredictableClock.nextMark(t(12, 57), 5))
        assertEquals(t(13, 0), PredictableClock.nextMark(t(12, 30), 60))
    }

    @Test
    fun nextMarkHandlesNonDivisorX() {
        assertEquals(t(12, 56), PredictableClock.nextMark(t(12, 56), 7))
        assertEquals(t(13, 0), PredictableClock.nextMark(t(12, 57), 7))
    }

    @Test
    fun clockDueWithinLeadWindow() {
        assertTrue(PredictableClock.clockDue(t(12, 4, 30), 5))
        assertTrue(PredictableClock.clockDue(t(12, 5), 5))
        assertFalse(PredictableClock.clockDue(t(12, 3), 5))
    }

    @Test
    fun clockDurationCoversMarkPlusAnalog() {
        assertEquals(Duration.ofSeconds(60), PredictableClock.clockDuration(t(12, 4, 30), 5, 30))
        assertEquals(Duration.ofSeconds(30), PredictableClock.clockDuration(t(12, 5), 5, 30))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "ru.aensidhe.dreamclock.core.photos.PredictableClockTest"`
Expected: FAIL — `PredictableClock` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package ru.aensidhe.dreamclock.core.photos

import java.time.Duration
import java.time.LocalDateTime

object PredictableClock {
    private val CLOCK_LEAD: Duration = Duration.ofSeconds(60)

    fun nextMark(
        from: LocalDateTime,
        everyXthMinute: Int,
    ): LocalDateTime {
        val x = everyXthMinute.coerceIn(1, 60)
        val onMinute = from.withSecond(0).withNano(0)
        val exactlyOnMark = from == onMinute && from.minute % x == 0
        if (exactlyOnMark) return from
        val nextMultiple = (from.minute / x + 1) * x
        return if (nextMultiple >= 60) {
            from.plusHours(1).withMinute(0).withSecond(0).withNano(0)
        } else {
            from.withMinute(nextMultiple).withSecond(0).withNano(0)
        }
    }

    fun clockDue(
        now: LocalDateTime,
        everyXthMinute: Int,
    ): Boolean = Duration.between(now, nextMark(now, everyXthMinute)) < CLOCK_LEAD

    fun clockDuration(
        now: LocalDateTime,
        everyXthMinute: Int,
        analogSeconds: Int,
    ): Duration =
        Duration.between(now, nextMark(now, everyXthMinute)).plusSeconds(analogSeconds.toLong())
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "ru.aensidhe.dreamclock.core.photos.PredictableClockTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/ru/aensidhe/dreamclock/core/photos/PredictableClock.kt core/src/test/kotlin/ru/aensidhe/dreamclock/core/photos/PredictableClockTest.kt
git commit -m "feat: :robot: add predictable wall-clock cadence scheduler"
```

---

## Task 2: Time-driven cadence integration

Replaces the count-based cadence with `PredictableClock`. Touches the proto
(field 11 rename, field 12 dropped), the deck-driving chain, and `DreamContent`.

**Files:**
- Modify: `app/src/main/proto/settings.proto`
- Modify: `app/.../settings/SettingsSerializer.kt`
- Modify: `core/.../core/photos/SlidePlanner.kt`
- Delete: `core/.../core/photos/ClockGapPolicy.kt` (+ its test if present)
- Modify: `app/.../immich/SlideDriver.kt`
- Delete: `app/.../immich/SlideTiming.kt`
- Modify: `app/.../ui/SlideDeckModel.kt`, `SlideDeck.kt`, `DreamRoot.kt`
- Modify: `app/.../dream/DreamContent.kt`

**Interfaces:**
- Consumes: `PredictableClock` (Task 1).
- Produces: `TimedSlide(slide: PlannedSlide, duration: Duration)` and
  `TimedRender(slide: RenderSlide, duration: Duration)` in the `immich` package;
  `SlideDriver.next(now: Instant, everyXthMinute: Int, photoSeconds: Int, analogSeconds: Int): TimedSlide`;
  `SlideDeckModel.next(now, everyXthMinute, photoSeconds, analogSeconds): TimedRender`.

- [ ] **Step 1: Rename the proto cadence fields**

In `app/src/main/proto/settings.proto`, change field 11 and remove field 12:

```proto
  int32 photo_interval_seconds = 10;
  int32 shown_every_xth_minute = 11;
  int32 analog_slide_seconds = 13;
```

Delete the old `analog_every_n_slides = 11;` and `max_clock_gap_seconds = 12;`
lines. Field 12 is intentionally left unused here; Task 7 reuses it for
`immich_key_ciphertext`.

- [ ] **Step 2: Update serializer defaults**

In `SettingsSerializer.kt`, replace the cadence setters:

```kotlin
            .setPhotoIntervalSeconds(30)
            .setShownEveryXthMinute(5)
            .setAnalogSlideSeconds(30)
```

Remove `.setAnalogEveryNSlides(...)` and `.setMaxClockGapSeconds(...)`. Also set
`.setDaysEitherSide(15)`. Leave `photosEnabled`, `immichHost`, `maxYearsBack`
(renamed in Task 5) as they are for now.

- [ ] **Step 3: Simplify `SlidePlanner` to content-only**

Replace the `SlidePlanner` class body so it no longer injects clocks:

```kotlin
class SlidePlanner {
    private var pendingPortrait: PlannerAsset? = null

    fun offer(asset: PlannerAsset): List<PlannedSlide> {
        val isPortraitPhoto = asset.kind == SlideMediaKind.PHOTO && asset.orientation == Orientation.PORTRAIT
        if (isPortraitPhoto) {
            val held = pendingPortrait
            return if (held == null) {
                pendingPortrait = asset
                emptyList()
            } else {
                pendingPortrait = null
                listOf(PairedPhotoSlide(held, asset))
            }
        }
        return if (asset.kind == SlideMediaKind.VIDEO) listOf(VideoSlide(asset)) else listOf(SinglePhotoSlide(asset))
    }
}
```

Remove `analogCadence`, `contentSinceClock`, `emitContent`, and the `ClockSlide`
emission. `ClockSlide` and `PlannedSlide` stay defined where they are. If a
`SlidePlannerTest` asserts clock injection, update it to expect content-only
output.

- [ ] **Step 4: Delete `ClockGapPolicy`**

Remove `core/.../core/photos/ClockGapPolicy.kt` and any `ClockGapPolicyTest`.

- [ ] **Step 5: Rewrite `SlideDriver` to use the scheduler**

```kotlin
package ru.aensidhe.dreamclock.immich

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import ru.aensidhe.dreamclock.core.photos.ClockSlide
import ru.aensidhe.dreamclock.core.photos.PlannedSlide
import ru.aensidhe.dreamclock.core.photos.PredictableClock
import ru.aensidhe.dreamclock.core.photos.SlidePlanner

data class TimedSlide(
    val slide: PlannedSlide,
    val duration: Duration,
)

class SlideDriver(
    private val assets: Iterator<SlideAsset>,
    private val planner: SlidePlanner,
    private val zone: ZoneId,
) {
    private val buffer = ArrayDeque<PlannedSlide>()

    fun next(
        now: Instant,
        everyXthMinute: Int,
        photoSeconds: Int,
        analogSeconds: Int,
    ): TimedSlide {
        val local = LocalDateTime.ofInstant(now, zone)
        if (PredictableClock.clockDue(local, everyXthMinute)) {
            return TimedSlide(ClockSlide, PredictableClock.clockDuration(local, everyXthMinute, analogSeconds))
        }
        while (buffer.isEmpty()) {
            buffer.addAll(planner.offer(assets.next().toPlannerAsset()))
        }
        return TimedSlide(buffer.removeFirst(), Duration.ofSeconds(photoSeconds.toLong()))
    }
}
```

- [ ] **Step 6: Delete `SlideTiming` and update `SlideDeckModel`**

Remove `app/.../immich/SlideTiming.kt`. Rewrite `SlideDeckModel`:

```kotlin
class SlideDeckModel(
    private val driver: SlideDriver,
    private val resolver: SlideResolver,
) {
    fun next(
        now: Instant,
        everyXthMinute: Int,
        photoSeconds: Int,
        analogSeconds: Int,
    ): TimedRender {
        val timed = driver.next(now, everyXthMinute, photoSeconds, analogSeconds)
        return TimedRender(resolver.resolve(timed.slide), timed.duration)
    }

    fun preload(slide: RenderSlide, imageLoader: ImageLoader, context: PlatformContext) {
        urlsOf(slide).forEach { imageLoader.enqueue(ImageRequest.Builder(context).data(it).build()) }
    }

    private fun urlsOf(slide: RenderSlide): List<String> =
        when (slide) {
            is RenderPhoto -> listOf(slide.previewUrl, slide.placeholderUrl)
            is RenderPairedPhoto -> urlsOf(slide.left) + urlsOf(slide.right)
            RenderClock -> emptyList()
        }
}

data class TimedRender(
    val slide: RenderSlide,
    val duration: Duration,
)
```

Add `import java.time.Duration` and keep the existing Coil imports. Remove the
`SlideTiming` import.

- [ ] **Step 7: Rewrite the `SlideDeck` loop to consume durations**

In `SlideDeck.kt`, add an `everyXthMinute: Int` parameter and replace the
`LaunchedEffect(deck)` body so each slide's boundary is computed deterministically
and the clock's variable duration is honored:

```kotlin
        val latestEveryX by rememberUpdatedState(everyXthMinute)
        val latestPhotoSeconds by rememberUpdatedState(photoSeconds)
        val latestAnalogSeconds by rememberUpdatedState(analogSeconds)
        var current by remember(deck) { mutableStateOf<RenderSlide?>(null) }
        LaunchedEffect(deck) {
            var shownAt = Instant.now()
            var shown = deck.next(shownAt, latestEveryX, latestPhotoSeconds, latestAnalogSeconds)
            current = shown.slide
            while (true) {
                onSuppressBottomLeft(OverlaySuppression.suppressBottomLeft(shown.slide))
                val boundary = shownAt.plus(shown.duration)
                val upcoming = deck.next(boundary, latestEveryX, latestPhotoSeconds, latestAnalogSeconds)
                deck.preload(upcoming.slide, imageLoader, context)
                delay(shown.duration.toMillis())
                current = upcoming.slide
                shown = upcoming
                shownAt = boundary
            }
        }
```

Remove the `SlideTiming` import. Keep the `Crossfade` block unchanged.

- [ ] **Step 8: Thread `everyXthMinute` through `DreamRoot`**

In `DreamRoot.kt`, add `everyXthMinute: Int` to the signature and pass it to
`SlideDeck(... everyXthMinute = everyXthMinute ...)`.

- [ ] **Step 9: Update `DreamContent` cadence wiring**

In `DreamContent.kt`:
- In the `produceState` key list, remove `settings.analogEveryNSlides` and
  `settings.maxClockGapSeconds`. Keep `settings.maxYearsBack` (renamed in Task 5).
- In `buildSlideDeck`, remove the `SlidePlanner(analogCadence)` argument and the
  `SlideDriver(maxGap = ..., lastClockAt = ...)` arguments; construct:

```kotlin
    val planner = SlidePlanner()
    val driver =
        SlideDriver(
            assets = pool.endlessSequence().iterator(),
            planner = planner,
            zone = ZoneId.systemDefault(),
        )
```

- Pass `everyXthMinute = settings.shownEveryXthMinute` to `DreamRoot`.

Remove now-unused imports (`java.time.Duration`, `java.time.Instant` if unused).

- [ ] **Step 10: Run the gate**

Run: `./gradlew ktlintCheck detekt :core:test :app:testDebugUnitTest assemble`
Expected: BUILD SUCCESSFUL. Fix ktlint/detekt findings (lines ≤ 120).

- [ ] **Step 11: Commit**

```bash
git add app/src/main/proto/settings.proto app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsSerializer.kt core/src/main/kotlin/ru/aensidhe/dreamclock/core/photos/SlidePlanner.kt app/src/main/kotlin/ru/aensidhe/dreamclock/immich/SlideDriver.kt app/src/main/kotlin/ru/aensidhe/dreamclock/ui/SlideDeckModel.kt app/src/main/kotlin/ru/aensidhe/dreamclock/ui/SlideDeck.kt app/src/main/kotlin/ru/aensidhe/dreamclock/ui/DreamRoot.kt app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamContent.kt
git rm core/src/main/kotlin/ru/aensidhe/dreamclock/core/photos/ClockGapPolicy.kt app/src/main/kotlin/ru/aensidhe/dreamclock/immich/SlideTiming.kt
git commit -m "feat: :robot: drive the clock on a predictable wall-clock schedule"
```

---

## Task 3: Cache-aware YearWalk (`:core`, pure)

**Files:**
- Modify: `core/.../core/photos/YearWalk.kt`
- Test: `core/.../core/photos/YearWalkTest.kt`

**Interfaces:**
- Produces: `YearWalk.shouldQueryOlderYear(candidateYear: Int, cachedOldestYear: Int, consecutiveEmptyBelowOldest: Int, maxEmptyYearsBack: Int): Boolean`
  and `YearWalk.countsTowardEmptyStreak(candidateYear: Int, cachedOldestYear: Int): Boolean`.

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.core.photos

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YearWalkTest {
    @Test
    fun yearsAtOrAboveOldestNeverCountEmpties() {
        assertFalse(YearWalk.countsTowardEmptyStreak(candidateYear = 2010, cachedOldestYear = 2001))
        assertFalse(YearWalk.countsTowardEmptyStreak(candidateYear = 2001, cachedOldestYear = 2001))
        assertTrue(YearWalk.countsTowardEmptyStreak(candidateYear = 2000, cachedOldestYear = 2001))
    }

    @Test
    fun alwaysWalkWhileAtOrAboveOldest() {
        assertTrue(
            YearWalk.shouldQueryOlderYear(
                candidateYear = 2005,
                cachedOldestYear = 2001,
                consecutiveEmptyBelowOldest = 0,
                maxEmptyYearsBack = 10,
            ),
        )
    }

    @Test
    fun stopBelowOldestAfterThresholdEmpties() {
        assertTrue(
            YearWalk.shouldQueryOlderYear(2000, 2001, consecutiveEmptyBelowOldest = 9, maxEmptyYearsBack = 10),
        )
        assertFalse(
            YearWalk.shouldQueryOlderYear(1991, 2001, consecutiveEmptyBelowOldest = 10, maxEmptyYearsBack = 10),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "ru.aensidhe.dreamclock.core.photos.YearWalkTest"`
Expected: FAIL — new methods unresolved.

- [ ] **Step 3: Rewrite `YearWalk`**

```kotlin
package ru.aensidhe.dreamclock.core.photos

object YearWalk {
    fun countsTowardEmptyStreak(
        candidateYear: Int,
        cachedOldestYear: Int,
    ): Boolean = candidateYear < cachedOldestYear

    fun shouldQueryOlderYear(
        candidateYear: Int,
        cachedOldestYear: Int,
        consecutiveEmptyBelowOldest: Int,
        maxEmptyYearsBack: Int,
    ): Boolean {
        if (candidateYear >= cachedOldestYear) return true
        return consecutiveEmptyBelowOldest < maxEmptyYearsBack
    }
}
```

Remove the old `MAX_EMPTY_STREAK` constant and `shouldQueryNextYear`; its only
caller (`ImmichRepository`) is updated in Task 5.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "ru.aensidhe.dreamclock.core.photos.YearWalkTest"`
Expected: PASS. (`:app` will not compile until Task 5 updates the caller — that
is expected; run only the `:core` test here.)

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/ru/aensidhe/dreamclock/core/photos/YearWalk.kt core/src/test/kotlin/ru/aensidhe/dreamclock/core/photos/YearWalkTest.kt
git commit -m "feat: :robot: make the year walk cache-aware with an empty-year threshold"
```

Note: `:app` compilation is red between Tasks 3 and 5 by design. Task 5 restores
green. Do not run `assemble` until Task 5.

---

## Task 4: PhotoHistory store and pure resolution (`:app`)

**Files:**
- Create: `app/src/main/proto/photo_history.proto`
- Create: `app/.../immich/PhotoHistory.kt` (pure helpers)
- Create: `app/.../immich/PhotoHistorySerializer.kt`, `PhotoHistoryStore.kt`
- Test: `app/src/test/.../immich/PhotoHistoryTest.kt`

**Interfaces:**
- Produces: pure object `PhotoHistory` with
  `oldestYear(history: PhotoHistoryProto, host: String, currentYear: Int): Int`,
  `withObservedOldestYear(history, host, year: Int): PhotoHistoryProto`,
  `resetOnHostChange(history, testedHost: String): PhotoHistoryProto`.
  `PhotoHistoryStore` (Android) wrapping a Proto DataStore.

- [ ] **Step 1: Define the history proto**

`app/src/main/proto/photo_history.proto`:

```proto
syntax = "proto3";
option java_package = "ru.aensidhe.dreamclock.immich";
option java_multiple_files = true;

message PhotoHistoryProto {
  map<string, int32> oldest_year_by_host = 1;
  string last_tested_host = 2;
}
```

- [ ] **Step 2: Write the failing test for the pure helpers**

```kotlin
package ru.aensidhe.dreamclock.immich

import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoHistoryTest {
    @Test
    fun oldestYearFallsBackToCurrentWhenNoEntry() {
        val h = PhotoHistoryProto.getDefaultInstance()
        assertEquals(2026, PhotoHistory.oldestYear(h, "https://a", 2026))
    }

    @Test
    fun oldestYearUsesCachedEntry() {
        val h = PhotoHistory.withObservedOldestYear(PhotoHistoryProto.getDefaultInstance(), "https://a", 2001)
        assertEquals(2001, PhotoHistory.oldestYear(h, "https://a", 2026))
    }

    @Test
    fun withObservedOldestYearKeepsTheEarliest() {
        var h = PhotoHistory.withObservedOldestYear(PhotoHistoryProto.getDefaultInstance(), "https://a", 2001)
        h = PhotoHistory.withObservedOldestYear(h, "https://a", 2010)
        assertEquals(2001, PhotoHistory.oldestYear(h, "https://a", 2026))
    }

    @Test
    fun resetOnHostChangeClearsChangedHostButKeepsKeyOnlyChange() {
        var h = PhotoHistory.withObservedOldestYear(PhotoHistoryProto.getDefaultInstance(), "https://a", 2001)
        h = PhotoHistory.resetOnHostChange(h, "https://a")
        assertEquals(2001, PhotoHistory.oldestYear(h, "https://a", 2026))
        h = PhotoHistory.resetOnHostChange(h, "https://b")
        assertEquals(2026, PhotoHistory.oldestYear(h, "https://b", 2026))
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.aensidhe.dreamclock.immich.PhotoHistoryTest"`
Expected: FAIL — `PhotoHistory` / `PhotoHistoryProto` unresolved. (This also
triggers proto generation for the new message.)

- [ ] **Step 4: Implement the pure helpers**

```kotlin
package ru.aensidhe.dreamclock.immich

object PhotoHistory {
    fun oldestYear(
        history: PhotoHistoryProto,
        host: String,
        currentYear: Int,
    ): Int = history.oldestYearByHostMap[host] ?: currentYear

    fun withObservedOldestYear(
        history: PhotoHistoryProto,
        host: String,
        year: Int,
    ): PhotoHistoryProto {
        val existing = history.oldestYearByHostMap[host]
        val next = if (existing == null) year else minOf(existing, year)
        return history.toBuilder().putOldestYearByHost(host, next).build()
    }

    fun resetOnHostChange(
        history: PhotoHistoryProto,
        testedHost: String,
    ): PhotoHistoryProto {
        if (history.lastTestedHost == testedHost) return history
        return history.toBuilder()
            .removeOldestYearByHost(testedHost)
            .setLastTestedHost(testedHost)
            .build()
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.aensidhe.dreamclock.immich.PhotoHistoryTest"`
Expected: PASS.

- [ ] **Step 6: Add the DataStore serializer and store (no unit test — Android glue)**

`PhotoHistorySerializer.kt`:

```kotlin
package ru.aensidhe.dreamclock.immich

import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream

object PhotoHistorySerializer : Serializer<PhotoHistoryProto> {
    override val defaultValue: PhotoHistoryProto = PhotoHistoryProto.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): PhotoHistoryProto = PhotoHistoryProto.parseFrom(input)

    override suspend fun writeTo(t: PhotoHistoryProto, output: OutputStream) = t.writeTo(output)
}
```

`PhotoHistoryStore.kt`:

```kotlin
package ru.aensidhe.dreamclock.immich

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.first

private val Context.photoHistoryStore: DataStore<PhotoHistoryProto> by dataStore(
    fileName = "photo_history.pb",
    serializer = PhotoHistorySerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { PhotoHistoryProto.getDefaultInstance() },
)

class PhotoHistoryStore internal constructor(
    private val store: DataStore<PhotoHistoryProto>,
) {
    suspend fun current(): PhotoHistoryProto = store.data.first()

    suspend fun update(transform: (PhotoHistoryProto) -> PhotoHistoryProto) {
        store.updateData(transform)
    }

    companion object {
        fun from(context: Context): PhotoHistoryStore = PhotoHistoryStore(context.photoHistoryStore)
    }
}
```

- [ ] **Step 7: Run the gate**

Run: `./gradlew ktlintCheck detekt :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (`:app` still red overall from Task 3 until Task 5 —
run only these; do not `assemble` yet).

If the module cannot compile because of the Task 3 caller gap, proceed to Task 5
before the full gate; the `PhotoHistoryTest` result from Step 5 is the gate for
this task.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/proto/photo_history.proto app/src/main/kotlin/ru/aensidhe/dreamclock/immich/PhotoHistory.kt app/src/main/kotlin/ru/aensidhe/dreamclock/immich/PhotoHistorySerializer.kt app/src/main/kotlin/ru/aensidhe/dreamclock/immich/PhotoHistoryStore.kt app/src/test/kotlin/ru/aensidhe/dreamclock/immich/PhotoHistoryTest.kt
git commit -m "feat: :robot: add per-host oldest-date history store"
```

---

## Task 5: Year-walk fetch integration + date rollover

Renames proto field 9, rewires the repository walk to the cached oldest year, and
adds the date-rollover refetch. Restores `:app` to green.

**Files:**
- Modify: `app/src/main/proto/settings.proto`, `SettingsSerializer.kt`
- Modify: `app/.../immich/ImmichCredentials.kt` (`PhotoFetchConfig`)
- Modify: `app/.../immich/ImmichRepository.kt`
- Modify: `app/.../dream/DreamContent.kt`
- Test: `app/src/test/.../immich/ImmichRepositoryTest.kt` (extend if present)

**Interfaces:**
- Consumes: `YearWalk` (Task 3), `PhotoHistory`/`PhotoHistoryStore` (Task 4).
- Produces: `PhotoFetchConfig(daysEitherSide, maxEmptyYearsBack, cachedOldestYear, pageSize = 100)`;
  `ImmichRepository.loadAssets(credentials, config): AssetLoad` where
  `AssetLoad(assets: List<SlideAsset>, oldestPopulatedYear: Int?)`.

- [ ] **Step 1: Rename proto field 9**

In `settings.proto`, change field 9:

```proto
  int32 max_empty_years_back = 9;
```

Remove `int32 max_years_back = 9;`.

- [ ] **Step 2: Set the default**

In `SettingsSerializer.kt` replace `.setMaxYearsBack(0)` with
`.setMaxEmptyYearsBack(20)`.

- [ ] **Step 3: Update `PhotoFetchConfig`**

In `ImmichCredentials.kt`:

```kotlin
data class PhotoFetchConfig(
    val daysEitherSide: Int,
    val maxEmptyYearsBack: Int,
    val cachedOldestYear: Int,
    val pageSize: Int = 100,
)
```

- [ ] **Step 4: Rewrite the walk in `ImmichRepository`**

Change `loadAssets` to walk by absolute year, apply the cache boundary, and
report the earliest date observed:

```kotlin
data class AssetLoad(
    val assets: List<SlideAsset>,
    val oldestPopulatedYear: Int?,
)

class ImmichRepository(
    private val apiFactory: ImmichApiFactory,
    private val today: () -> LocalDate,
    private val zone: ZoneId,
) {
    suspend fun loadAssets(
        credentials: ImmichCredentials,
        config: PhotoFetchConfig,
    ): AssetLoad {
        val api = apiFactory.create(credentials.host)
        val currentYear = today().year
        val all = mutableListOf<SlideAsset>()
        var oldestPopulatedYear: Int? = null
        var emptyBelowOldest = 0
        var candidateYear = currentYear
        while (YearWalk.shouldQueryOlderYear(candidateYear, config.cachedOldestYear, emptyBelowOldest, config.maxEmptyYearsBack)) {
            val yearOffset = currentYear - candidateYear
            val year = fetchYear(api, credentials.apiKey, yearOffset, config)
            all += year
            if (year.isNotEmpty()) oldestPopulatedYear = candidateYear
            if (YearWalk.countsTowardEmptyStreak(candidateYear, config.cachedOldestYear)) {
                emptyBelowOldest = if (year.isEmpty()) emptyBelowOldest + 1 else 0
            }
            candidateYear -= 1
        }
        return AssetLoad(all, oldestPopulatedYear)
    }
    // ... fetchYear unchanged except it takes yearOffset as before ...
}
```

Because the walk descends from `currentYear`, the last non-empty year assigned to
`oldestPopulatedYear` is the smallest one — exactly the new oldest. `fetchYear`
keeps using `SimilarTimeWindows.windowFor(today(), config.daysEitherSide, yearOffset)`
and `config.pageSize`. No `SlideAsset` or `AssetMapper` change is needed — the walk
records the oldest populated year directly, without reading per-asset dates.

- [ ] **Step 5: Extend the repository test**

Add cases to `ImmichRepositoryTest` (or create it) using a fake `ImmichApiFactory`:
walk stops after `maxEmptyYearsBack` empty years below the cached oldest; empty
years at or above the cached oldest do not stop it; `oldestPopulatedYear` reports
the smallest year that returned photos. Run:

`./gradlew :app:testDebugUnitTest --tests "ru.aensidhe.dreamclock.immich.ImmichRepositoryTest"`
Expected: PASS.

- [ ] **Step 6: Rewire `DreamContent` fetch + date rollover**

In `DreamContent`:
- Add a date ticker and key `produceState` on it:

```kotlin
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalDateTime.now()
            val next = now.toLocalDate().plusDays(1).atStartOfDay()
            delay(Duration.between(now, next).toMillis().coerceAtLeast(1_000L))
            today = LocalDate.now()
        }
    }
```

- Replace `settings.maxYearsBack` in the `produceState` keys with
  `settings.maxEmptyYearsBack`, and add `today`.
- Pass a `PhotoHistoryStore` into `DreamContent` (constructed by the entry points,
  defaulting to `null` for previews), read its `current()` inside `buildSlideDeck`
  to compute `cachedOldestYear = PhotoHistory.oldestYear(history, credentials.host, today.year)`,
  build `PhotoFetchConfig(settings.daysEitherSide, settings.maxEmptyYearsBack, cachedOldestYear)`,
  and after a successful load call
  `historyStore.update { PhotoHistory.withObservedOldestYear(it, credentials.host, load.oldestPopulatedYear) }`
  when `oldestPopulatedYear != null`.
- `buildSlideDeck` now consumes `AssetLoad`: `val load = repository.loadAssets(...)`,
  use `load.assets` for the pool/resolver, `PhotoFallback.shouldShowPhotos(..., assetCount = load.assets.size)`.
- Use `today` in `ImmichRepository(today = { today }, ...)` so the window centers
  on the ticked date.

Add imports: `java.time.LocalDateTime`, `kotlinx.coroutines.delay`,
`androidx.compose.runtime.*` (mutableStateOf/LaunchedEffect/setValue/getValue).

- [ ] **Step 7: Run the full gate**

Run: `./gradlew ktlintCheck detekt :core:test :app:testDebugUnitTest assemble`
Expected: BUILD SUCCESSFUL. `:app` is green again.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/proto/settings.proto app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsSerializer.kt app/src/main/kotlin/ru/aensidhe/dreamclock/immich/ImmichCredentials.kt app/src/main/kotlin/ru/aensidhe/dreamclock/immich/ImmichRepository.kt app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamContent.kt app/src/test/kotlin/ru/aensidhe/dreamclock/immich/ImmichRepositoryTest.kt
git commit -m "feat: :robot: walk years from the cached oldest and refetch on date rollover"
```

---

## Task 6: Keystore cipher (`:app`)

**Files:**
- Create: `app/.../immich/KeyCipher.kt`
- Test: `app/src/test/.../immich/FakeKeyCipherTest.kt` (verifies a fake round-trips;
  the real Keystore is on-device only)

**Interfaces:**
- Produces: `interface KeyCipher { fun encrypt(plaintext: String): ByteArray; fun decrypt(blob: ByteArray): String }`,
  `class KeystoreCipher : KeyCipher`.

- [ ] **Step 1: Write the failing test with a fake**

```kotlin
package ru.aensidhe.dreamclock.immich

import kotlin.test.Test
import kotlin.test.assertEquals

class FakeKeyCipher : KeyCipher {
    override fun encrypt(plaintext: String): ByteArray = plaintext.toByteArray(Charsets.UTF_8)
    override fun decrypt(blob: ByteArray): String = String(blob, Charsets.UTF_8)
}

class FakeKeyCipherTest {
    @Test
    fun roundTrips() {
        val cipher = FakeKeyCipher()
        assertEquals("api-key-123", cipher.decrypt(cipher.encrypt("api-key-123")))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.aensidhe.dreamclock.immich.FakeKeyCipherTest"`
Expected: FAIL — `KeyCipher` unresolved.

- [ ] **Step 3: Implement `KeyCipher` and `KeystoreCipher`**

```kotlin
package ru.aensidhe.dreamclock.immich

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface KeyCipher {
    fun encrypt(plaintext: String): ByteArray

    fun decrypt(blob: ByteArray): String
}

class KeystoreCipher(
    private val alias: String = "immich_api_key",
) : KeyCipher {
    override fun encrypt(plaintext: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return iv + ciphertext
    }

    override fun decrypt(blob: ByteArray): String {
        val iv = blob.copyOfRange(0, IV_BYTES)
        val ciphertext = blob.copyOfRange(IV_BYTES, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_BITS = 256
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.aensidhe.dreamclock.immich.FakeKeyCipherTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/immich/KeyCipher.kt app/src/test/kotlin/ru/aensidhe/dreamclock/immich/FakeKeyCipherTest.kt
git commit -m "feat: :robot: add Keystore-backed AES-GCM cipher for the API key"
```

---

## Task 7: Settings-driven credentials + remove BuildConfig

Adds `immich_key_ciphertext` at field 12, changes the credential seam, wires the
entry points, and deletes the bootstrap.

**Files:**
- Modify: `app/src/main/proto/settings.proto`
- Modify: `app/.../immich/CredentialsStore.kt`
- Delete: `app/.../immich/BuildConfigCredentials.kt`
- Modify: `app/.../dream/DreamContent.kt`, `TvDreamService.kt`, `DreamPreviewActivity.kt`
- Modify: `app/build.gradle.kts`
- Test: `app/src/test/.../immich/KeystoreCredentialsStoreTest.kt`

**Interfaces:**
- Consumes: `KeyCipher` (Task 6).
- Produces: `interface CredentialsStore { fun credentials(settings: Settings): ImmichCredentials? }`,
  `KeystoreCredentialsStore(cipher: KeyCipher)`.

- [ ] **Step 1: Add the ciphertext field to the proto**

In `settings.proto`:

```proto
  bytes immich_key_ciphertext = 12;
```

- [ ] **Step 2: Write the failing test with the fake cipher**

```kotlin
package ru.aensidhe.dreamclock.immich

import com.google.protobuf.ByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import ru.aensidhe.dreamclock.settings.Settings

class KeystoreCredentialsStoreTest {
    private val cipher = FakeKeyCipher()

    private fun settings(host: String, key: String?): Settings {
        val b = Settings.newBuilder().setImmichHost(host)
        if (key != null) b.immichKeyCiphertext = ByteString.copyFrom(cipher.encrypt(key))
        return b.build()
    }

    @Test
    fun nullWhenHostBlank() {
        assertNull(KeystoreCredentialsStore(cipher).credentials(settings("", "k")))
    }

    @Test
    fun nullWhenKeyMissing() {
        assertNull(KeystoreCredentialsStore(cipher).credentials(settings("https://a", null)))
    }

    @Test
    fun decryptsWhenBothPresent() {
        val creds = KeystoreCredentialsStore(cipher).credentials(settings("https://a", "secret"))
        assertEquals(ImmichCredentials("https://a", "secret"), creds)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.aensidhe.dreamclock.immich.KeystoreCredentialsStoreTest"`
Expected: FAIL — `KeystoreCredentialsStore` / new interface unresolved.

- [ ] **Step 4: Change the seam**

Rewrite `CredentialsStore.kt`:

```kotlin
package ru.aensidhe.dreamclock.immich

import ru.aensidhe.dreamclock.settings.Settings

interface CredentialsStore {
    fun credentials(settings: Settings): ImmichCredentials?
}

object NoCredentialsStore : CredentialsStore {
    override fun credentials(settings: Settings): ImmichCredentials? = null
}

class KeystoreCredentialsStore(
    private val cipher: KeyCipher,
) : CredentialsStore {
    override fun credentials(settings: Settings): ImmichCredentials? {
        if (settings.immichHost.isBlank() || settings.immichKeyCiphertext.isEmpty) return null
        val key = cipher.decrypt(settings.immichKeyCiphertext.toByteArray())
        return ImmichCredentials(settings.immichHost, key)
    }
}
```

Remove `StaticCredentialsStore` if only used by `BuildConfigCredentials`;
otherwise update it to the new interface. Delete `BuildConfigCredentials.kt`.

- [ ] **Step 5: Rewire `DreamContent`**

Replace the credentials line and its `produceState` key:

```kotlin
    val credentials =
        remember(credentialsStore, settings.immichHost, settings.immichKeyCiphertext) {
            credentialsStore.credentials(settings)
        }
```

Add `settings.immichHost` and `settings.immichKeyCiphertext` to the
`produceState` keys (they already drive `credentials`, but keeping them keeps the
deck rebuild explicit). Remove the old `credentialsStore.current()` usage.

- [ ] **Step 6: Wire the entry points**

In `TvDreamService.kt` and `DreamPreviewActivity.kt`, replace
`BuildConfigCredentials.store()` with `KeystoreCredentialsStore(KeystoreCipher())`
and pass a `PhotoHistoryStore.from(this)` into `DreamContent` (per Task 5's
signature). Remove the `BuildConfigCredentials` import.

- [ ] **Step 7: Remove the gradle bootstrap**

In `app/build.gradle.kts`, delete the `immichProps` block and both
`buildConfigField("String", "IMMICH_HOST"/"IMMICH_KEY", ...)` lines. Leave
`buildConfig = true` (still used for the generated `BuildConfig`). No
`local.properties` immich entries remain in use.

- [ ] **Step 8: Run the gate**

Run: `./gradlew ktlintCheck detekt :core:test :app:testDebugUnitTest assemble`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/proto/settings.proto app/src/main/kotlin/ru/aensidhe/dreamclock/immich/CredentialsStore.kt app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamContent.kt app/src/main/kotlin/ru/aensidhe/dreamclock/dream/TvDreamService.kt app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamPreviewActivity.kt app/build.gradle.kts app/src/test/kotlin/ru/aensidhe/dreamclock/immich/KeystoreCredentialsStoreTest.kt
git rm app/src/main/kotlin/ru/aensidhe/dreamclock/immich/BuildConfigCredentials.kt
git commit -m "feat: :robot: source credentials from settings via a Keystore-backed store"
```

---

## Task 8: Health probe (`:app`, pure mapping)

**Files:**
- Create: `app/.../immich/ImmichHealth.kt`
- Test: `app/src/test/.../immich/ImmichHealthTest.kt`

**Interfaces:**
- Produces: `sealed interface ProbeResult { Checking; data class Reachable(total: Int?); Unauthorized; Unreachable; data class Error(detail: String) }`,
  `ImmichHealth.probe(api, apiKey, window, zone): ProbeResult`,
  `ImmichHealth.truncateDetail(raw: String): String`.

- [ ] **Step 1: Write the failing test for the pure parts**

```kotlin
package ru.aensidhe.dreamclock.immich

import kotlin.test.Test
import kotlin.test.assertEquals

class ImmichHealthTest {
    @Test
    fun detailTruncatesToHundred() {
        val raw = "e".repeat(250)
        assertEquals(100, ImmichHealth.truncateDetail(raw).length)
    }

    @Test
    fun detailKeepsShortMessages() {
        assertEquals("boom", ImmichHealth.truncateDetail("boom"))
    }

    @Test
    fun statusForUnauthorizedFromHttp401() {
        assertEquals(ProbeResult.Unauthorized, ImmichHealth.classify(status = 401, body = "nope"))
    }

    @Test
    fun statusForServerErrorCarriesDetail() {
        val result = ImmichHealth.classify(status = 500, body = "internal boom")
        assertEquals(ProbeResult.Error("internal boom"), result)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.aensidhe.dreamclock.immich.ImmichHealthTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `ImmichHealth`**

```kotlin
package ru.aensidhe.dreamclock.immich

import java.io.IOException
import java.time.ZoneId
import ru.aensidhe.dreamclock.core.photos.DateWindow
import retrofit2.HttpException

sealed interface ProbeResult {
    data object Checking : ProbeResult

    data class Reachable(val total: Int?) : ProbeResult

    data object Unauthorized : ProbeResult

    data object Unreachable : ProbeResult

    data class Error(val detail: String) : ProbeResult
}

object ImmichHealth {
    private const val MAX_DETAIL = 100

    fun truncateDetail(raw: String): String = raw.trim().take(MAX_DETAIL)

    fun classify(
        status: Int,
        body: String,
    ): ProbeResult =
        when {
            status == 401 || status == 403 -> ProbeResult.Unauthorized
            status in 200..299 -> ProbeResult.Reachable(null)
            else -> ProbeResult.Error(truncateDetail(body))
        }

    suspend fun probe(
        api: ImmichApi,
        apiKey: String,
        window: DateWindow,
        zone: ZoneId,
    ): ProbeResult {
        val bounds = ImmichSearchBoundsFactory.forWindow(window, zone)
        return try {
            val response =
                api.searchMetadata(
                    apiKey = apiKey,
                    request =
                        SearchMetadataRequest(
                            takenAfter = bounds.takenAfter,
                            takenBefore = bounds.takenBefore,
                            page = 1,
                            size = 1,
                        ),
                )
            ProbeResult.Reachable(response.assets.total)
        } catch (e: HttpException) {
            classify(e.code(), e.response()?.errorBody()?.string().orEmpty())
        } catch (e: IOException) {
            ProbeResult.Unreachable
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.aensidhe.dreamclock.immich.ImmichHealthTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/immich/ImmichHealth.kt app/src/test/kotlin/ru/aensidhe/dreamclock/immich/ImmichHealthTest.kt
git commit -m "feat: :robot: add Immich connectivity probe with surfaced server errors"
```

---

## Task 9: Settings row composables + strings

**Files:**
- Create: `app/.../settings/SettingsRows.kt`
- Modify: `app/.../settings/SettingsLabels.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-ru/strings.xml`
- Test: `app/src/test/.../settings/StepperClampTest.kt`

**Interfaces:**
- Produces: `clampStepper(value: Int, min: Int, max: Int): Int`;
  `@Composable StepperRow(label, value, min, max, step, onChange)`;
  `@Composable TextFieldRow(label, value, isSecret, onCommit)`.

- [ ] **Step 1: Write the failing clamp test**

```kotlin
package ru.aensidhe.dreamclock.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class StepperClampTest {
    @Test
    fun clampsWithinBounds() {
        assertEquals(0, clampStepper(-3, 0, 30))
        assertEquals(30, clampStepper(45, 0, 30))
        assertEquals(15, clampStepper(15, 0, 30))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.aensidhe.dreamclock.settings.StepperClampTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `clampStepper` and the composables**

In `SettingsRows.kt` add the pure function and the two composables. `clampStepper`:

```kotlin
fun clampStepper(value: Int, min: Int, max: Int): Int = value.coerceIn(min, max)
```

`StepperRow` renders the label, the current value, and two TV `Button`s that call
`onChange(clampStepper(value - step, min, max))` and
`onChange(clampStepper(value + step, min, max))`. `TextFieldRow` renders a
focusable `OutlinedTextField` (TV Material3) using `PasswordVisualTransformation`
when `isSecret`, committing `onCommit(text)` on `KeyboardActions.onDone` and on
focus loss. Follow the existing `ListItem`/`FocusRequester` patterns in
`SettingsScreen.kt`. Keep every line ≤ 120 chars.

- [ ] **Step 4: Add string resources**

In `values/strings.xml` add (English), and Russian equivalents in
`values-ru/strings.xml`:

```xml
<string name="settings_immich_section">Immich photos</string>
<string name="settings_immich_enable">Show photos</string>
<string name="settings_immich_host">Server address</string>
<string name="settings_immich_key">API key</string>
<string name="settings_immich_test">Test connection</string>
<string name="settings_days_either_side">Days around today</string>
<string name="settings_max_empty_years_back">Empty years to explore</string>
<string name="settings_photo_interval">Seconds per photo</string>
<string name="settings_shown_every_xth_minute">Clock every N minutes</string>
<string name="settings_analog_slide_seconds">Clock seconds</string>
<string name="probe_checking">Checking…</string>
<string name="probe_connected">Connected</string>
<string name="probe_connected_count">Connected — %1$d photos for today</string>
<string name="probe_unauthorized">Authorization failed</string>
<string name="probe_unreachable">Host unreachable</string>
<string name="probe_error">Error: %1$s</string>
```

Add `probeStatusLabel(context, result): String` to `SettingsLabels.kt` mapping
`ProbeResult` to these strings (using the count overload when `Reachable.total`
is non-null).

- [ ] **Step 5: Run the gate**

Run: `./gradlew ktlintCheck detekt :app:testDebugUnitTest assemble`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsRows.kt app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsLabels.kt app/src/main/res/values/strings.xml app/src/main/res/values-ru/strings.xml app/src/test/kotlin/ru/aensidhe/dreamclock/settings/StepperClampTest.kt
git commit -m "feat: :robot: add stepper and text-field settings rows with Ru/En strings"
```

---

## Task 10: Immich settings section wiring

**Files:**
- Modify: `app/.../settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: `StepperRow`, `TextFieldRow` (Task 9); `KeystoreCipher` (Task 6);
  settings fields from Tasks 2/5/7.

- [ ] **Step 1: Add the section to `SettingsScreen`**

After `ColorModeSection`, add an Immich section: a `SectionHeader`
(`settings_immich_section`), a `ToggleRow` bound to `settings.photosEnabled`, and
when enabled reveal:
- `TextFieldRow` for the host, committing
  `repository.update { it.toBuilder().setImmichHost(text.trim()).build() }`.
- `TextFieldRow(isSecret = true)` for the key, committing the ciphertext:
  `val blob = cipher.encrypt(text); repository.update { it.toBuilder().setImmichKeyCiphertext(ByteString.copyFrom(blob)).build() }`.
  Show dots as the current value when a ciphertext is stored; never display the
  decrypted key.
- Five `StepperRow`s bound to `daysEitherSide` (0–30), `maxEmptyYearsBack` (1–50),
  `photoIntervalSeconds` (3–60), `shownEveryXthMinute` (1–60),
  `analogSlideSeconds` (3–60), each committing via `repository.update`.

Pass a `KeyCipher` (default `KeystoreCipher()`) into `SettingsScreen`, and thread
it from `SettingsActivity`. The Test button and status line are wired in Task 11 —
add a placeholder `Button` that is filled in there, or defer the button to Task
11 entirely.

- [ ] **Step 2: Run the gate**

Run: `./gradlew ktlintCheck detekt :app:testDebugUnitTest assemble`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsScreen.kt app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsActivity.kt
git commit -m "feat: :robot: add the Immich settings section with host, key, and steppers"
```

---

## Task 11: Test button, status line, and host-change reset

**Files:**
- Modify: `app/.../settings/SettingsScreen.kt`, `SettingsActivity.kt`

**Interfaces:**
- Consumes: `ImmichHealth` (Task 8), `PhotoHistoryStore`/`PhotoHistory` (Task 4),
  `ImmichClient`/`ImmichApi`.

- [ ] **Step 1: Wire the Test button and status state**

In the Immich section, hold `var status by remember { mutableStateOf<ProbeResult?>(null) }`.
The Test button launches in `scope`:

```kotlin
status = ProbeResult.Checking
val api = ImmichClient.api(settings.immichHost, OkHttpClient())
val window = SimilarTimeWindows.windowFor(LocalDate.now(), settings.daysEitherSide, 0)
val result = ImmichHealth.probe(api, cipher.decrypt(settings.immichKeyCiphertext.toByteArray()), window, ZoneId.systemDefault())
if (result is ProbeResult.Reachable) {
    historyStore.update { PhotoHistory.resetOnHostChange(it, settings.immichHost) }
}
status = result
```

Render the status line with `probeStatusLabel(localizedContext, status)` when
non-null. Guard the Test button so it is disabled when host or ciphertext is
blank.

- [ ] **Step 2: Thread the history store**

Pass `PhotoHistoryStore.from(context)` from `SettingsActivity` into
`SettingsScreen` alongside the cipher.

- [ ] **Step 3: Run the gate**

Run: `./gradlew ktlintCheck detekt :app:testDebugUnitTest assemble`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsScreen.kt app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsActivity.kt
git commit -m "feat: :robot: add the connectivity test, status line, and host-change reset"
```

---

## Task 12: Trim Plan 3, final gate, on-device validation

**Files:**
- Modify: `docs/superpowers/plans/2026-07-20-3-immich-photo-rendering.md`

- [ ] **Step 1: Trim Plan 3's Task 12**

Edit Plan 3's `## Task 12` down to only its CI build gate (its Step 1 and Step 5).
Remove the on-device photo-validation steps (its Steps 2–4) — they live below.
Commit:

```bash
git add docs/superpowers/plans/2026-07-20-3-immich-photo-rendering.md
git commit -m "docs: :robot: move Immich on-device validation from plan 3 to plan 4"
```

- [ ] **Step 2: Run the full CI gate**

Run: `./gradlew ktlintCheck detekt :core:test :app:testDebugUnitTest assemble`
Expected: BUILD SUCCESSFUL. Commit any lint-only fixes with
`style: :robot: satisfy linters for the settings and cadence work`.

- [ ] **Step 3: Work through the on-device validation checklist**

Install on the target device and verify. This list is copy-pasteable:

```
- [ ] Open Settings; the Immich section appears with an enable toggle off by default.
- [ ] Enter the server address; enter the API key (shows as dots, never plaintext).
- [ ] Press Test connection: "Checking…" then "Connected — N photos for today".
- [ ] Enter a wrong key and Test: "Authorization failed".
- [ ] Enter an unreachable host and Test: "Host unreachable".
- [ ] Force a server error (e.g. a bad path) and Test: "Error: <server text>" (~100 chars, not hidden).
- [ ] Restart the app; the key persists (still connects) — it was stored encrypted, not in plaintext.
- [ ] Enable photos; open the dream. Single landscape photos fill the screen.
- [ ] Two portraits pair side by side, each with its own bottom-right caption.
- [ ] On a paired slide whose left photo has a caption, the bottom-left status/colloquial group hides while the top-left digital time stays.
- [ ] The clock appears on the predictable schedule (every shown_every_xth_minute, counted from the top of the hour).
- [ ] When an asset ends within a minute before a mark, the clock comes up early and stays through the mark plus analog_slide_seconds.
- [ ] Slides crossfade; the next image is already loaded when it appears.
- [ ] A photo with no EXIF shows no caption; captions omit missing parts.
- [ ] Set max_empty_years_back and confirm older years are reached; the oldest date is remembered per host.
- [ ] Change the host and re-run a successful Test: the remembered oldest date resets for the new host; changing only the key does not reset.
- [ ] Cross local midnight (or set the clock forward): the deck refetches and re-centers on the new day.
- [ ] Disable photos: the feature-1 clock returns exactly as before, no crash.
- [ ] git status --porcelain shows no secret staged; local.properties carries no immich.* entries.
```

- [ ] **Step 4: Finish the branch**

Once the checklist passes, use superpowers:finishing-a-development-branch to
rebase, push, open the PR, and (after CI is green) fast-forward merge to `main`.

## Self-Review

Spec coverage: proto reuse of fields 9/11/12 (Tasks 2, 5, 7); Keystore cipher
(Task 6) and settings-driven `CredentialsStore` (Task 7); `BuildConfigCredentials`
removal (Task 7); settings section with host/key/steppers (Tasks 9, 10) and Ru/En
(Task 9); predictable cadence with the early-clock overrun (Tasks 1, 2); per-host
oldest-date cache and cache-aware walk (Tasks 3, 4, 5); host-change reset at a
successful test (Tasks 4, 11); probe with surfaced ~100-char server error (Task 8,
11); date-rollover refetch (Task 5); on-device checklist moved from Plan 3 (Task
12).

Type consistency: `PredictableClock` signatures match between Tasks 1 and 2;
`TimedSlide`/`TimedRender(slide, duration)` match across the driver, deck model,
and `SlideDeck` loop; `PhotoFetchConfig(daysEitherSide, maxEmptyYearsBack,
cachedOldestYear, pageSize)` and `AssetLoad(assets, oldestPopulatedYear)` match
between Tasks 5's repository and `DreamContent`; `PhotoHistory.oldestYear` /
`withObservedOldestYear` are year-based across Tasks 4 and 5; `KeyCipher.encrypt/decrypt`
match between Tasks 6, 7, 10, 11; `CredentialsStore.credentials(settings)` matches
Task 7 and its call sites; `ProbeResult` and `PhotoHistory` helper signatures
match between their defining tasks and their consumers.
