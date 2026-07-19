# Immich Photos — Core Logic Implementation Plan (Plan 1 of 5)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the pure, Android-free logic that drives the Immich photo slideshow — date-window math, year-walk stopping, orientation, captions, slide planning with portrait pairing, and the clock-gap guarantee — all in the `:core` module, fully unit-tested.

**Architecture:** Everything here is deterministic Kotlin with no Android, network, or GPU dependencies, living in a new `ru.aensidhe.dreamclock.core.photos` package. It mirrors the existing `:core` style (stateless `object`s for engines, small data classes, `java.time`). Later plans (the Immich client, the Compose slideshow, credentials/pairing, video/audio, settings) consume these types; nothing here consumes them yet, so this plan produces a self-contained, tested library.

**Tech Stack:** Kotlin 2.4.0 (JVM), `java.time`, kotlin.test + JUnit 5 (JUnit Platform).

## Global Constraints

- Module `:core` has no Android dependencies — pure Kotlin/JVM only.
- Package root for all new code: `ru.aensidhe.dreamclock.core.photos`.
- JDK toolchain 21 (`jvmToolchain(21)` already set in `core/build.gradle.kts`).
- Tests use `kotlin.test` assertions with `org.junit.jupiter.api.Test`; the module runs on the JUnit Platform.
- ktlint and detekt must pass: 4-space indentation, trailing commas on multi-line argument/parameter lists (matching existing files), no wildcard imports.
- Commit convention: Conventional Commits with a `:robot:` marker after the type (e.g. `feat: :robot: …`). No `Co-Authored-By` trailer.
- Per-task test command: `./gradlew :core:test`. Pre-commit gate: `./gradlew :core:ktlintFormat :core:ktlintCheck :core:detekt :core:test`.

---

### Task 1: Orientation classifier

**Files:**
- Create: `core/src/main/kotlin/ru/aensidhe/dreamclock/core/photos/Orientation.kt`
- Test: `core/src/test/kotlin/ru/aensidhe/dreamclock/core/photos/AssetOrientationTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `enum class Orientation { PORTRAIT, LANDSCAPE }`
  - `object AssetOrientation { fun of(width: Int, height: Int, exifOrientation: Int?): Orientation }`
  - Rule: EXIF orientation tags 5–8 mean the stored image is rotated 90°, so width/height are swapped for display. After accounting for that, `PORTRAIT` when effective height > effective width; otherwise `LANDSCAPE`. Squares, zero, and negative dimensions classify as `LANDSCAPE` (shown alone, full width).

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.core.photos

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class AssetOrientationTest {
    @Test
    fun `wider than tall is landscape`() {
        assertEquals(Orientation.LANDSCAPE, AssetOrientation.of(4000, 3000, null))
    }

    @Test
    fun `taller than wide is portrait`() {
        assertEquals(Orientation.PORTRAIT, AssetOrientation.of(3000, 4000, null))
    }

    @Test
    fun `exif orientation 6 swaps dimensions to portrait`() {
        // Stored 4000x3000 (landscape) but tag 6 rotates 90 degrees -> displayed portrait.
        assertEquals(Orientation.PORTRAIT, AssetOrientation.of(4000, 3000, 6))
    }

    @Test
    fun `exif orientation 8 swaps dimensions to portrait`() {
        assertEquals(Orientation.PORTRAIT, AssetOrientation.of(4000, 3000, 8))
    }

    @Test
    fun `exif orientation 3 does not swap`() {
        assertEquals(Orientation.LANDSCAPE, AssetOrientation.of(4000, 3000, 3))
    }

    @Test
    fun `square is landscape`() {
        assertEquals(Orientation.LANDSCAPE, AssetOrientation.of(2000, 2000, null))
    }

    @Test
    fun `zero dimensions default to landscape`() {
        assertEquals(Orientation.LANDSCAPE, AssetOrientation.of(0, 0, null))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests 'ru.aensidhe.dreamclock.core.photos.AssetOrientationTest'`
Expected: FAIL — compilation error, `Orientation` / `AssetOrientation` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package ru.aensidhe.dreamclock.core.photos

enum class Orientation { PORTRAIT, LANDSCAPE }

object AssetOrientation {
    private val ROTATING_TAGS = setOf(5, 6, 7, 8)

    fun of(
        width: Int,
        height: Int,
        exifOrientation: Int?,
    ): Orientation {
        if (width <= 0 || height <= 0) return Orientation.LANDSCAPE
        val rotated = exifOrientation in ROTATING_TAGS
        val effectiveWidth = if (rotated) height else width
        val effectiveHeight = if (rotated) width else height
        return if (effectiveHeight > effectiveWidth) Orientation.PORTRAIT else Orientation.LANDSCAPE
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests 'ru.aensidhe.dreamclock.core.photos.AssetOrientationTest'`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
./gradlew :core:ktlintFormat
git add core/src/main/kotlin/ru/aensidhe/dreamclock/core/photos/Orientation.kt core/src/test/kotlin/ru/aensidhe/dreamclock/core/photos/AssetOrientationTest.kt
git commit -m "feat: :robot: add EXIF-aware orientation classifier for photos"
```

---

### Task 2: Similar-time date windows

**Files:**
- Create: `core/src/main/kotlin/ru/aensidhe/dreamclock/core/photos/SimilarTimeWindows.kt`
- Test: `core/src/test/kotlin/ru/aensidhe/dreamclock/core/photos/SimilarTimeWindowsTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `data class DateWindow(val fromInclusive: LocalDate, val toInclusive: LocalDate)`
  - `object SimilarTimeWindows { fun windowFor(today: LocalDate, daysEitherSide: Int, yearsBack: Int): DateWindow }`
  - Semantics: center = `today.minusYears(yearsBack)`; `from = center.minusDays(daysEitherSide)`; `to = center.plusDays(daysEitherSide)`. The window is a date range, inclusive on both ends; converting to Immich's offset-date-time bounds is a later (`:app`) concern.

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.core.photos

import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class SimilarTimeWindowsTest {
    @Test
    fun `current year window is centered on today`() {
        val w = SimilarTimeWindows.windowFor(LocalDate.of(2026, 7, 20), daysEitherSide = 3, yearsBack = 0)
        assertEquals(LocalDate.of(2026, 7, 17), w.fromInclusive)
        assertEquals(LocalDate.of(2026, 7, 23), w.toInclusive)
    }

    @Test
    fun `years back shifts the whole window`() {
        val w = SimilarTimeWindows.windowFor(LocalDate.of(2026, 7, 20), daysEitherSide = 3, yearsBack = 5)
        assertEquals(LocalDate.of(2021, 7, 17), w.fromInclusive)
        assertEquals(LocalDate.of(2021, 7, 23), w.toInclusive)
    }

    @Test
    fun `window crossing month boundary`() {
        val w = SimilarTimeWindows.windowFor(LocalDate.of(2026, 1, 1), daysEitherSide = 2, yearsBack = 0)
        assertEquals(LocalDate.of(2025, 12, 30), w.fromInclusive)
        assertEquals(LocalDate.of(2026, 1, 3), w.toInclusive)
    }

    @Test
    fun `leap day today clamps to feb 28 in a non-leap year`() {
        val w = SimilarTimeWindows.windowFor(LocalDate.of(2024, 2, 29), daysEitherSide = 1, yearsBack = 1)
        // 2023 has no Feb 29; java.time clamps minusYears to Feb 28.
        assertEquals(LocalDate.of(2023, 2, 27), w.fromInclusive)
        assertEquals(LocalDate.of(2023, 3, 1), w.toInclusive)
    }

    @Test
    fun `zero days either side yields a single day`() {
        val w = SimilarTimeWindows.windowFor(LocalDate.of(2026, 7, 20), daysEitherSide = 0, yearsBack = 0)
        assertEquals(LocalDate.of(2026, 7, 20), w.fromInclusive)
        assertEquals(LocalDate.of(2026, 7, 20), w.toInclusive)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests 'ru.aensidhe.dreamclock.core.photos.SimilarTimeWindowsTest'`
Expected: FAIL — `DateWindow` / `SimilarTimeWindows` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package ru.aensidhe.dreamclock.core.photos

import java.time.LocalDate

data class DateWindow(
    val fromInclusive: LocalDate,
    val toInclusive: LocalDate,
)

object SimilarTimeWindows {
    fun windowFor(
        today: LocalDate,
        daysEitherSide: Int,
        yearsBack: Int,
    ): DateWindow {
        val center = today.minusYears(yearsBack.toLong())
        return DateWindow(
            fromInclusive = center.minusDays(daysEitherSide.toLong()),
            toInclusive = center.plusDays(daysEitherSide.toLong()),
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests 'ru.aensidhe.dreamclock.core.photos.SimilarTimeWindowsTest'`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
./gradlew :core:ktlintFormat
git add core/src/main/kotlin/ru/aensidhe/dreamclock/core/photos/SimilarTimeWindows.kt core/src/test/kotlin/ru/aensidhe/dreamclock/core/photos/SimilarTimeWindowsTest.kt
git commit -m "feat: :robot: add similar-time date window computation"
```

---

### Task 3: Year-walk stop policy

**Files:**
- Create: `core/src/main/kotlin/ru/aensidhe/dreamclock/core/photos/YearWalk.kt`
- Test: `core/src/test/kotlin/ru/aensidhe/dreamclock/core/photos/YearWalkTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `object YearWalk { const val MAX_EMPTY_STREAK = 20; fun shouldQueryNextYear(yearsQueried: Int, consecutiveEmptyYears: Int, maxYearsCap: Int): Boolean }`
  - Semantics: the repository queries year offsets 0, 1, 2, … and calls this before each next query. `maxYearsCap` of `0` means unlimited. Return `false` (stop) when a positive cap is reached (`yearsQueried >= maxYearsCap`) or when `consecutiveEmptyYears >= MAX_EMPTY_STREAK`; otherwise `true`.

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.core.photos

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class YearWalkTest {
    @Test
    fun `continues while under cap and streak`() {
        assertTrue(YearWalk.shouldQueryNextYear(yearsQueried = 3, consecutiveEmptyYears = 2, maxYearsCap = 0))
    }

    @Test
    fun `stops at positive cap`() {
        assertFalse(YearWalk.shouldQueryNextYear(yearsQueried = 10, consecutiveEmptyYears = 0, maxYearsCap = 10))
    }

    @Test
    fun `continues just under positive cap`() {
        assertTrue(YearWalk.shouldQueryNextYear(yearsQueried = 9, consecutiveEmptyYears = 0, maxYearsCap = 10))
    }

    @Test
    fun `cap of zero means unlimited`() {
        assertTrue(YearWalk.shouldQueryNextYear(yearsQueried = 100, consecutiveEmptyYears = 5, maxYearsCap = 0))
    }

    @Test
    fun `stops after twenty consecutive empty years`() {
        assertFalse(YearWalk.shouldQueryNextYear(yearsQueried = 40, consecutiveEmptyYears = 20, maxYearsCap = 0))
    }

    @Test
    fun `continues at nineteen empty years`() {
        assertTrue(YearWalk.shouldQueryNextYear(yearsQueried = 40, consecutiveEmptyYears = 19, maxYearsCap = 0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests 'ru.aensidhe.dreamclock.core.photos.YearWalkTest'`
Expected: FAIL — `YearWalk` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package ru.aensidhe.dreamclock.core.photos

object YearWalk {
    const val MAX_EMPTY_STREAK = 20

    fun shouldQueryNextYear(
        yearsQueried: Int,
        consecutiveEmptyYears: Int,
        maxYearsCap: Int,
    ): Boolean {
        if (maxYearsCap > 0 && yearsQueried >= maxYearsCap) return false
        if (consecutiveEmptyYears >= MAX_EMPTY_STREAK) return false
        return true
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests 'ru.aensidhe.dreamclock.core.photos.YearWalkTest'`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
./gradlew :core:ktlintFormat
git add core/src/main/kotlin/ru/aensidhe/dreamclock/core/photos/YearWalk.kt core/src/test/kotlin/ru/aensidhe/dreamclock/core/photos/YearWalkTest.kt
git commit -m "feat: :robot: add year-walk stop policy for photo fetch"
```

---

### Task 4: Photo caption formatting

**Files:**
- Create: `core/src/main/kotlin/ru/aensidhe/dreamclock/core/photos/PhotoCaption.kt`
- Test: `core/src/test/kotlin/ru/aensidhe/dreamclock/core/photos/PhotoCaptionTest.kt`

**Interfaces:**
- Consumes: `ru.aensidhe.dreamclock.core.time.ClockLocale` (existing enum `{ RU, EN }`).
- Produces:
  - `data class CaptionSource(val takenAt: LocalDateTime?, val city: String?, val country: String?)`
  - `data class CaptionLines(val dateTime: String?, val location: String?)`
  - `object PhotoCaption { fun format(source: CaptionSource, locale: ClockLocale): CaptionLines? }`
  - Semantics: `dateTime` is `"<d MMMM yyyy> | <HH:mm>"` in the locale (null when `takenAt` is null). `location` joins non-blank city and country with `", "` (null when both blank). Returns null only when both lines would be null.

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.core.photos

import java.time.LocalDateTime
import ru.aensidhe.dreamclock.core.time.ClockLocale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class PhotoCaptionTest {
    private val takenAt = LocalDateTime.of(2026, 7, 19, 14, 32)

    @Test
    fun `english full caption`() {
        val c = PhotoCaption.format(CaptionSource(takenAt, "Berlin", "Germany"), ClockLocale.EN)!!
        assertEquals("19 July 2026 | 14:32", c.dateTime)
        assertEquals("Berlin, Germany", c.location)
    }

    @Test
    fun `russian full caption`() {
        val c = PhotoCaption.format(CaptionSource(takenAt, "Берлин", "Германия"), ClockLocale.RU)!!
        assertEquals("19 июля 2026 | 14:32", c.dateTime)
        assertEquals("Берлин, Германия", c.location)
    }

    @Test
    fun `city only`() {
        val c = PhotoCaption.format(CaptionSource(takenAt, "Berlin", null), ClockLocale.EN)!!
        assertEquals("Berlin", c.location)
    }

    @Test
    fun `country only`() {
        val c = PhotoCaption.format(CaptionSource(takenAt, "  ", "Germany"), ClockLocale.EN)!!
        assertEquals("Germany", c.location)
    }

    @Test
    fun `no location`() {
        val c = PhotoCaption.format(CaptionSource(takenAt, null, null), ClockLocale.EN)!!
        assertEquals("19 July 2026 | 14:32", c.dateTime)
        assertNull(c.location)
    }

    @Test
    fun `no date`() {
        val c = PhotoCaption.format(CaptionSource(null, "Berlin", "Germany"), ClockLocale.EN)!!
        assertNull(c.dateTime)
        assertEquals("Berlin, Germany", c.location)
    }

    @Test
    fun `nothing available yields null`() {
        assertNull(PhotoCaption.format(CaptionSource(null, " ", null), ClockLocale.EN))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests 'ru.aensidhe.dreamclock.core.photos.PhotoCaptionTest'`
Expected: FAIL — `PhotoCaption` / `CaptionSource` / `CaptionLines` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package ru.aensidhe.dreamclock.core.photos

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import ru.aensidhe.dreamclock.core.time.ClockLocale

data class CaptionSource(
    val takenAt: LocalDateTime?,
    val city: String?,
    val country: String?,
)

data class CaptionLines(
    val dateTime: String?,
    val location: String?,
)

object PhotoCaption {
    private fun jvmLocale(locale: ClockLocale): Locale =
        when (locale) {
            ClockLocale.RU -> Locale.forLanguageTag("ru")
            ClockLocale.EN -> Locale.ENGLISH
        }

    fun format(
        source: CaptionSource,
        locale: ClockLocale,
    ): CaptionLines? {
        val jvm = jvmLocale(locale)
        val dateTime =
            source.takenAt?.let {
                val date = it.format(DateTimeFormatter.ofPattern("d MMMM yyyy", jvm))
                val time = it.format(DateTimeFormatter.ofPattern("HH:mm", jvm))
                "$date | $time"
            }
        val location =
            listOfNotNull(source.city, source.country)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .takeIf { it.isNotEmpty() }
                ?.joinToString(", ")
        return if (dateTime == null && location == null) null else CaptionLines(dateTime, location)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests 'ru.aensidhe.dreamclock.core.photos.PhotoCaptionTest'`
Expected: PASS (7 tests). If the Russian month string differs on the JDK in use, adjust the expected string to the JDK's output (the format call is correct; only the expected literal may need to match the platform CLDR data).

- [ ] **Step 5: Commit**

```bash
./gradlew :core:ktlintFormat
git add core/src/main/kotlin/ru/aensidhe/dreamclock/core/photos/PhotoCaption.kt core/src/test/kotlin/ru/aensidhe/dreamclock/core/photos/PhotoCaptionTest.kt
git commit -m "feat: :robot: add localized photo caption formatting"
```

---

### Task 5: Slide planner (portrait pairing + clock cadence)

**Files:**
- Create: `core/src/main/kotlin/ru/aensidhe/dreamclock/core/photos/SlidePlanner.kt`
- Test: `core/src/test/kotlin/ru/aensidhe/dreamclock/core/photos/SlidePlannerTest.kt`

**Interfaces:**
- Consumes: `Orientation` (Task 1).
- Produces:
  - `enum class SlideMediaKind { PHOTO, VIDEO }`
  - `data class PlannerAsset(val id: String, val kind: SlideMediaKind, val orientation: Orientation)`
  - `sealed interface PlannedSlide` with:
    - `data class SinglePhotoSlide(val asset: PlannerAsset) : PlannedSlide`
    - `data class PairedPhotoSlide(val left: PlannerAsset, val right: PlannerAsset) : PlannedSlide`
    - `data class VideoSlide(val asset: PlannerAsset) : PlannedSlide`
    - `data object ClockSlide : PlannedSlide`
  - `class SlidePlanner(private val analogCadence: Int) { fun offer(asset: PlannerAsset): List<PlannedSlide> }`
  - Semantics: a stateful streaming transducer. It holds at most one pending portrait photo. On a landscape photo it emits `SinglePhotoSlide`; on a video it emits `VideoSlide`; on a portrait photo it pairs with a pending portrait (`PairedPhotoSlide`) or becomes the new pending (emitting nothing). Every content slide (single, paired, or video) increments a counter; when the counter reaches `analogCadence`, a `ClockSlide` is appended and the counter resets. The pending portrait persists across calls indefinitely (this is what gives carry-over across reshuffles in the consumer). `analogCadence` must be ≥ 1.

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.core.photos

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class SlidePlannerTest {
    private fun photo(id: String, o: Orientation) = PlannerAsset(id, SlideMediaKind.PHOTO, o)

    private fun video(id: String) = PlannerAsset(id, SlideMediaKind.VIDEO, Orientation.LANDSCAPE)

    @Test
    fun `two consecutive portraits pair`() {
        val planner = SlidePlanner(analogCadence = 100)
        assertEquals(emptyList(), planner.offer(photo("a", Orientation.PORTRAIT)))
        assertEquals(
            listOf(PairedPhotoSlide(photo("a", Orientation.PORTRAIT), photo("b", Orientation.PORTRAIT))),
            planner.offer(photo("b", Orientation.PORTRAIT)),
        )
    }

    @Test
    fun `landscape between portraits is emitted alone and portraits still pair`() {
        val planner = SlidePlanner(analogCadence = 100)
        assertEquals(emptyList(), planner.offer(photo("p1", Orientation.PORTRAIT)))
        assertEquals(
            listOf(SinglePhotoSlide(photo("l", Orientation.LANDSCAPE))),
            planner.offer(photo("l", Orientation.LANDSCAPE)),
        )
        assertEquals(
            listOf(PairedPhotoSlide(photo("p1", Orientation.PORTRAIT), photo("p2", Orientation.PORTRAIT))),
            planner.offer(photo("p2", Orientation.PORTRAIT)),
        )
    }

    @Test
    fun `video is emitted alone and does not consume a pending portrait`() {
        val planner = SlidePlanner(analogCadence = 100)
        assertEquals(emptyList(), planner.offer(photo("p1", Orientation.PORTRAIT)))
        assertEquals(listOf(VideoSlide(video("v"))), planner.offer(video("v")))
        assertEquals(
            listOf(PairedPhotoSlide(photo("p1", Orientation.PORTRAIT), photo("p2", Orientation.PORTRAIT))),
            planner.offer(photo("p2", Orientation.PORTRAIT)),
        )
    }

    @Test
    fun `clock slide injected every cadence content slides`() {
        val planner = SlidePlanner(analogCadence = 2)
        assertEquals(listOf(SinglePhotoSlide(photo("l1", Orientation.LANDSCAPE))), planner.offer(photo("l1", Orientation.LANDSCAPE)))
        assertEquals(
            listOf(SinglePhotoSlide(photo("l2", Orientation.LANDSCAPE)), ClockSlide),
            planner.offer(photo("l2", Orientation.LANDSCAPE)),
        )
        assertEquals(listOf(SinglePhotoSlide(photo("l3", Orientation.LANDSCAPE))), planner.offer(photo("l3", Orientation.LANDSCAPE)))
        assertEquals(
            listOf(SinglePhotoSlide(photo("l4", Orientation.LANDSCAPE)), ClockSlide),
            planner.offer(photo("l4", Orientation.LANDSCAPE)),
        )
    }

    @Test
    fun `a paired slide counts as one content slide toward cadence`() {
        val planner = SlidePlanner(analogCadence = 1)
        assertEquals(emptyList(), planner.offer(photo("p1", Orientation.PORTRAIT)))
        assertEquals(
            listOf(
                PairedPhotoSlide(photo("p1", Orientation.PORTRAIT), photo("p2", Orientation.PORTRAIT)),
                ClockSlide,
            ),
            planner.offer(photo("p2", Orientation.PORTRAIT)),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests 'ru.aensidhe.dreamclock.core.photos.SlidePlannerTest'`
Expected: FAIL — planner types unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package ru.aensidhe.dreamclock.core.photos

enum class SlideMediaKind { PHOTO, VIDEO }

data class PlannerAsset(
    val id: String,
    val kind: SlideMediaKind,
    val orientation: Orientation,
)

sealed interface PlannedSlide

data class SinglePhotoSlide(val asset: PlannerAsset) : PlannedSlide

data class PairedPhotoSlide(
    val left: PlannerAsset,
    val right: PlannerAsset,
) : PlannedSlide

data class VideoSlide(val asset: PlannerAsset) : PlannedSlide

data object ClockSlide : PlannedSlide

class SlidePlanner(private val analogCadence: Int) {
    init {
        require(analogCadence >= 1) { "analogCadence must be >= 1" }
    }

    private var pendingPortrait: PlannerAsset? = null
    private var contentSinceClock = 0

    fun offer(asset: PlannerAsset): List<PlannedSlide> {
        val out = mutableListOf<PlannedSlide>()
        val isPortraitPhoto = asset.kind == SlideMediaKind.PHOTO && asset.orientation == Orientation.PORTRAIT
        if (isPortraitPhoto) {
            val held = pendingPortrait
            if (held == null) {
                pendingPortrait = asset
            } else {
                pendingPortrait = null
                emitContent(PairedPhotoSlide(held, asset), out)
            }
        } else if (asset.kind == SlideMediaKind.VIDEO) {
            emitContent(VideoSlide(asset), out)
        } else {
            emitContent(SinglePhotoSlide(asset), out)
        }
        return out
    }

    private fun emitContent(
        slide: PlannedSlide,
        out: MutableList<PlannedSlide>,
    ) {
        out.add(slide)
        contentSinceClock++
        if (contentSinceClock >= analogCadence) {
            out.add(ClockSlide)
            contentSinceClock = 0
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests 'ru.aensidhe.dreamclock.core.photos.SlidePlannerTest'`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
./gradlew :core:ktlintFormat
git add core/src/main/kotlin/ru/aensidhe/dreamclock/core/photos/SlidePlanner.kt core/src/test/kotlin/ru/aensidhe/dreamclock/core/photos/SlidePlannerTest.kt
git commit -m "feat: :robot: add slide planner with portrait pairing and clock cadence"
```

---

### Task 6: Clock-gap guarantee policy

**Files:**
- Create: `core/src/main/kotlin/ru/aensidhe/dreamclock/core/photos/ClockGapPolicy.kt`
- Test: `core/src/test/kotlin/ru/aensidhe/dreamclock/core/photos/ClockGapPolicyTest.kt`

**Interfaces:**
- Consumes: nothing (`java.time.Instant`, `java.time.Duration`).
- Produces:
  - `object ClockGapPolicy { fun shouldForceClock(lastClockAt: Instant, now: Instant, maxGap: Duration): Boolean }`
  - Semantics: returns true when `now - lastClockAt >= maxGap`. The `:app` runtime calls this at each slide boundary (never mid-video) to decide whether to insert an analog-clock slide ahead of the next planned content slide.

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.core.photos

import java.time.Duration
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ClockGapPolicyTest {
    private val base = Instant.parse("2026-07-20T10:00:00Z")
    private val fiveMinutes = Duration.ofMinutes(5)

    @Test
    fun `forces clock when gap exceeded`() {
        assertTrue(ClockGapPolicy.shouldForceClock(base, base.plus(Duration.ofMinutes(6)), fiveMinutes))
    }

    @Test
    fun `forces clock exactly at the gap`() {
        assertTrue(ClockGapPolicy.shouldForceClock(base, base.plus(fiveMinutes), fiveMinutes))
    }

    @Test
    fun `does not force clock under the gap`() {
        assertFalse(ClockGapPolicy.shouldForceClock(base, base.plus(Duration.ofMinutes(4)), fiveMinutes))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests 'ru.aensidhe.dreamclock.core.photos.ClockGapPolicyTest'`
Expected: FAIL — `ClockGapPolicy` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package ru.aensidhe.dreamclock.core.photos

import java.time.Duration
import java.time.Instant

object ClockGapPolicy {
    fun shouldForceClock(
        lastClockAt: Instant,
        now: Instant,
        maxGap: Duration,
    ): Boolean = Duration.between(lastClockAt, now) >= maxGap
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests 'ru.aensidhe.dreamclock.core.photos.ClockGapPolicyTest'`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
./gradlew :core:ktlintFormat
git add core/src/main/kotlin/ru/aensidhe/dreamclock/core/photos/ClockGapPolicy.kt core/src/test/kotlin/ru/aensidhe/dreamclock/core/photos/ClockGapPolicyTest.kt
git commit -m "feat: :robot: add clock-gap guarantee policy"
```

---

### Task 7: Full-module gate

**Files:** none (verification only).

- [ ] **Step 1: Run the full core gate**

Run: `./gradlew :core:ktlintFormat :core:ktlintCheck :core:detekt :core:test`
Expected: BUILD SUCCESSFUL; all `photos` tests pass alongside the existing `schedule` and `time` tests.

- [ ] **Step 2: Commit any formatting deltas (only if the working tree is dirty)**

```bash
git add -A core/
git commit -m "style: :robot: apply ktlint formatting to core photos package"
```

---

## Subsequent plans (roadmap, to be written as their own documents)

This plan is phase 1 of 5. Each later phase becomes its own plan document and produces working, testable software building on this core:

2. Immich client, repository, and basic photo slideshow (`:app`): Retrofit/OkHttp client and kotlinx.serialization models, per-year fetch with full pagination, pool/shuffle, Coil `preview`+`thumbnail` photo slides with captions, `SlideDeck` rendering the planned sequence with the clock cadence and gap guarantee, Coil disk caching, and the clock fallback. Tests use OkHttp `MockWebServer`. Adds the photo-related proto/settings fields it needs.
3. Credentials and pairing (`:app`): manual host/key entry, Keystore-backed key storage, the Ktor local pairing server, `PairingCrypto` (AES-GCM), `QrCode` (ZXing), the served phone page (WebCrypto, two modes), the email/password key-mint path, and the settings status line.
4. Video and audio (`:app`): Media3 `VideoSlide`, full-clip playback, and the schedule-aware three-way audio mode.
5. Settings surface polish (`:app`): remaining proto fields, `SettingsScreen` Immich section, steppers, and Ru/En labels.
