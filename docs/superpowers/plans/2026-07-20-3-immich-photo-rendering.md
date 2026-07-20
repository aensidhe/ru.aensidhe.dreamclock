# Immich Photo Rendering and Deck Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render the Immich photo stream from the Plan 2 data layer as a live Compose slideshow — Coil-loaded photos and paired portraits with bottom-right captions, a crossfading deck driven by `SlideDriver` with per-slide durations, caption-vs-clock overlay coexistence, and a `CredentialsStore` seam whose no-credentials default keeps the feature-1 clock fallback.

**Architecture:** New pure `:app` render units (`ImmichImageUrls`, a `RenderSlide` model, `SlideResolver`, `SlideTiming`, `OverlaySuppression`) turn each `PlannedSlide` plus its caption into a self-contained render model, unit-tested on the JVM against `MockWebServer`-free plain data. Compose composables (`PhotoSlide`, `PairedPhotoSlide`, the rewritten `SlideDeck`) and a Coil `ImageLoader` render that model on-device only. `DreamContent` builds the deck behind a `CredentialsStore` interface; the production default returns no credentials, so the deck stays null and the clock shows exactly as in feature 1. A gitignored `local.properties` bootstrap supplies real credentials for on-device validation without any secret entering source control.

**Tech Stack:** Kotlin 2.4.0 + Jetpack Compose (BOM 2025.09.01); Coil 3 (`coil-compose` + `coil-network-okhttp`) over the existing OkHttp 4.12; the Plan 1 `:core` slide/caption types; the Plan 2 `:app` Immich data layer (`ImmichRepository`, `SlideDriver`, `AssetPool`, `SlidePlanner`); JUnit 5 + kotlin.test for the pure units.

## Global Constraints

- Kotlin 2.4.0; Compose BOM 2025.09.01; JDK 21; `minSdk` 30, `compileSdk`/`targetSdk` 36.
- Stay on the Gradle 8 line (Gradle 8.14.5, AGP 8.13.2) — every dependency must be a stable release compatible with it. Coil 3 qualifies.
- ktlint 14.2.0 and detekt 1.23.8 both enforce `max_line_length = 120`; keep every line ≤ 120. Detekt config: `config/detekt/detekt.yml`. `FunctionNaming` ignores `@Composable`.
- Images only. This plan renders photos and paired portraits. Videos and audio stay dormant until Plan 6 — the Plan 2 mapper already drops non-`IMAGE` assets, and the resolver maps any stray `VideoSlide` to the clock. Do not add Media3 here.
- Credentials never come from source. The production `CredentialsStore` default returns null (clock fallback). On-device validation reads host and key from a gitignored `local.properties`; the API key is never committed and never logged.
- Commits: Conventional Commits with a `:robot:` emoji after the type (e.g. `feat: :robot: …`). No `Co-Authored-By` trailer. Imperative summary.
- Markdown/docs: no bold or italic for inline emphasis in prose.

---

## File Structure

New files:

- `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/ImmichImageUrls.kt` — build `preview` / `thumbnail` asset URLs from host + id.
- `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/RenderSlide.kt` — the render model (`RenderSlide`, `RenderPhoto`, `RenderPairedPhoto`, `RenderClock`).
- `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/SlideResolver.kt` — `PlannedSlide` + captions → `RenderSlide`.
- `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/SlideTiming.kt` — per-slide display duration.
- `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/OverlaySuppression.kt` — decide whether to hide the overlay's bottom-left group.
- `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/CredentialsStore.kt` — the credential seam (`CredentialsStore`, `NoCredentialsStore`, `StaticCredentialsStore`).
- `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/BuildConfigCredentials.kt` — validation-only bootstrap reading `local.properties`-backed `BuildConfig` fields.
- `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/ImmichImageLoader.kt` — Coil `ImageLoader` with the `x-api-key` interceptor and disk cache.
- `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/PhotoSlide.kt` — `PhotoSlide` / `PairedPhotoSlide` / caption composables.
- `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/SlideDeckModel.kt` — deck holder bundling driver + resolver + timings + preload.
- Tests mirroring the pure units under `app/src/test/kotlin/ru/aensidhe/dreamclock/immich/`.

Modified files:

- `gradle/libs.versions.toml` — Coil 3 version + libraries.
- `app/build.gradle.kts` — Coil dependencies; `buildConfig` feature + `local.properties` fields.
- `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/SlideDeck.kt` — rewrite to render the deck.
- `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/DreamRoot.kt` — thread deck, image loader, and suppression.
- `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/ClockOverlay.kt` — add `suppressBottomLeft`.
- `app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamContent.kt` — build the deck behind `CredentialsStore`.
- `app/src/main/kotlin/ru/aensidhe/dreamclock/dream/TvDreamService.kt` and `DreamPreviewActivity.kt` — pass the store.

## Existing interfaces this plan consumes (verbatim, do not redefine)

From `:core` package `ru.aensidhe.dreamclock.core.photos`:

```kotlin
sealed interface PlannedSlide
data class SinglePhotoSlide(val asset: PlannerAsset) : PlannedSlide
data class PairedPhotoSlide(val left: PlannerAsset, val right: PlannerAsset) : PlannedSlide
data class VideoSlide(val asset: PlannerAsset) : PlannedSlide
data object ClockSlide : PlannedSlide
data class PlannerAsset(val id: String, val kind: SlideMediaKind, val orientation: Orientation)
enum class SlideMediaKind { PHOTO, VIDEO }

data class CaptionSource(val takenAt: LocalDateTime?, val city: String?, val country: String?)
data class CaptionLines(val dateTime: String?, val location: String?)
object PhotoCaption { fun format(source: CaptionSource, locale: ClockLocale): CaptionLines? }
```

Note the name clash: `ru.aensidhe.dreamclock.core.photos.PairedPhotoSlide` (a data model) is distinct from the composable `PairedPhotoSlide` this plan adds in package `ru.aensidhe.dreamclock.ui`. Keep them in their own packages; never import both unqualified into one file.

From `ru.aensidhe.dreamclock.core.time`: `enum class ClockLocale { RU, EN }`.

From `:app` package `ru.aensidhe.dreamclock.immich` (Plan 2):

```kotlin
data class SlideAsset(val id: String, val kind: SlideMediaKind, val orientation: Orientation, val caption: CaptionSource)
data class ImmichCredentials(val host: String, val apiKey: String)
data class PhotoFetchConfig(val daysEitherSide: Int, val maxYearsBack: Int, val pageSize: Int = 100)
fun interface ImmichApiFactory { fun create(host: String): ImmichApi }
object ImmichClient { fun api(host: String, client: OkHttpClient = OkHttpClient()): ImmichApi }
class ImmichRepository(apiFactory: ImmichApiFactory, today: () -> LocalDate, zone: ZoneId) {
    suspend fun loadAssets(credentials: ImmichCredentials, config: PhotoFetchConfig): List<SlideAsset>
}
class AssetPool(assets: List<SlideAsset>, random: Random) { fun endlessSequence(): Sequence<SlideAsset> }
class SlideDriver(assets: Iterator<SlideAsset>, planner: SlidePlanner, maxGap: Duration, lastClockAt: Instant) {
    fun next(now: Instant): PlannedSlide
}
object PhotoFallback { fun shouldShowPhotos(enabled: Boolean, hasCredentials: Boolean, assetCount: Int): Boolean }
```

From `:app` settings: `fun effectiveLocale(language: Language, systemLocale: Locale): Locale`; `SettingsSerializer.defaultValue`; proto getters `photosEnabled`, `daysEitherSide`, `maxYearsBack`, `photoIntervalSeconds`, `analogEveryNSlides`, `maxClockGapSeconds`, `analogSlideSeconds`, `showAnalogSlide`, `colorRenderMode`, `language`.

---

## Task 1: Immich image URLs

**Files:**
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/ImmichImageUrls.kt`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/immich/ImmichImageUrlsTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `object ImmichImageUrls { fun preview(host: String, id: String): String; fun placeholder(host: String, id: String): String }`.

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.immich

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class ImmichImageUrlsTest {
    @Test
    fun `preview points at the sharp thumbnail variant`() {
        assertEquals(
            "https://immich.example/api/assets/abc/thumbnail?size=preview",
            ImmichImageUrls.preview("https://immich.example", "abc"),
        )
    }

    @Test
    fun `placeholder points at the small thumbnail variant`() {
        assertEquals(
            "https://immich.example/api/assets/abc/thumbnail?size=thumbnail",
            ImmichImageUrls.placeholder("https://immich.example", "abc"),
        )
    }

    @Test
    fun `a trailing slash on the host is normalized away`() {
        assertEquals(
            "https://immich.example/api/assets/xyz/thumbnail?size=preview",
            ImmichImageUrls.preview("https://immich.example/", "xyz"),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.ImmichImageUrlsTest'`
Expected: FAIL — unresolved reference `ImmichImageUrls`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package ru.aensidhe.dreamclock.immich

object ImmichImageUrls {
    fun preview(
        host: String,
        id: String,
    ): String = "${asset(host, id)}/thumbnail?size=preview"

    fun placeholder(
        host: String,
        id: String,
    ): String = "${asset(host, id)}/thumbnail?size=thumbnail"

    private fun asset(
        host: String,
        id: String,
    ): String = "${host.trimEnd('/')}/api/assets/$id"
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.ImmichImageUrlsTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/immich/ImmichImageUrls.kt \
        app/src/test/kotlin/ru/aensidhe/dreamclock/immich/ImmichImageUrlsTest.kt
git commit -m "feat: :robot: build immich preview and placeholder image urls"
```

---

## Task 2: Render model and slide resolver

**Files:**
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/RenderSlide.kt`
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/SlideResolver.kt`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/immich/SlideResolverTest.kt`

**Interfaces:**
- Consumes: `ImmichImageUrls` (Task 1); `PhotoCaption`, `CaptionLines`, `CaptionSource`, `PlannedSlide`/`SinglePhotoSlide`/`PairedPhotoSlide`/`VideoSlide`/`ClockSlide`, `PlannerAsset`, `ClockLocale` (existing).
- Produces:
  ```kotlin
  sealed interface RenderSlide
  data class RenderPhoto(val previewUrl: String, val placeholderUrl: String, val caption: CaptionLines?) : RenderSlide
  data class RenderPairedPhoto(val left: RenderPhoto, val right: RenderPhoto) : RenderSlide
  data object RenderClock : RenderSlide
  class SlideResolver(host: String, captions: Map<String, CaptionSource>, locale: ClockLocale) {
      fun resolve(slide: PlannedSlide): RenderSlide
  }
  ```

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.immich

import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import ru.aensidhe.dreamclock.core.photos.CaptionLines
import ru.aensidhe.dreamclock.core.photos.CaptionSource
import ru.aensidhe.dreamclock.core.photos.ClockSlide
import ru.aensidhe.dreamclock.core.photos.Orientation
import ru.aensidhe.dreamclock.core.photos.PairedPhotoSlide
import ru.aensidhe.dreamclock.core.photos.PlannerAsset
import ru.aensidhe.dreamclock.core.photos.SinglePhotoSlide
import ru.aensidhe.dreamclock.core.photos.SlideMediaKind
import ru.aensidhe.dreamclock.core.photos.VideoSlide
import ru.aensidhe.dreamclock.core.time.ClockLocale

class SlideResolverTest {
    private fun photo(id: String) = PlannerAsset(id, SlideMediaKind.PHOTO, Orientation.LANDSCAPE)

    private val captions =
        mapOf(
            "p1" to CaptionSource(LocalDateTime.of(2026, 7, 19, 14, 32), "Berlin", "Germany"),
            "p2" to CaptionSource(null, null, null),
        )
    private val resolver = SlideResolver("https://immich.example", captions, ClockLocale.EN)

    @Test
    fun `single photo carries its urls and formatted caption`() {
        val slide = resolver.resolve(SinglePhotoSlide(photo("p1"))) as RenderPhoto
        assertEquals("https://immich.example/api/assets/p1/thumbnail?size=preview", slide.previewUrl)
        assertEquals("https://immich.example/api/assets/p1/thumbnail?size=thumbnail", slide.placeholderUrl)
        assertEquals(CaptionLines("19 July 2026 | 14:32", "Berlin, Germany"), slide.caption)
    }

    @Test
    fun `a photo with no caption data resolves to a null caption`() {
        val slide = resolver.resolve(SinglePhotoSlide(photo("p2"))) as RenderPhoto
        assertNull(slide.caption)
    }

    @Test
    fun `an unknown id resolves to a null caption`() {
        val slide = resolver.resolve(SinglePhotoSlide(photo("missing"))) as RenderPhoto
        assertNull(slide.caption)
    }

    @Test
    fun `a paired slide resolves each half independently`() {
        val slide = resolver.resolve(PairedPhotoSlide(photo("p1"), photo("p2"))) as RenderPairedPhoto
        assertEquals("Berlin, Germany", slide.left.caption?.location)
        assertNull(slide.right.caption)
    }

    @Test
    fun `clock and video both resolve to the clock render`() {
        assertTrue(resolver.resolve(ClockSlide) is RenderClock)
        assertTrue(resolver.resolve(VideoSlide(photo("v1"))) is RenderClock)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.SlideResolverTest'`
Expected: FAIL — unresolved references `RenderPhoto`, `SlideResolver`.

- [ ] **Step 3: Write minimal implementation**

`RenderSlide.kt`:

```kotlin
package ru.aensidhe.dreamclock.immich

import ru.aensidhe.dreamclock.core.photos.CaptionLines

sealed interface RenderSlide

data class RenderPhoto(
    val previewUrl: String,
    val placeholderUrl: String,
    val caption: CaptionLines?,
) : RenderSlide

data class RenderPairedPhoto(
    val left: RenderPhoto,
    val right: RenderPhoto,
) : RenderSlide

data object RenderClock : RenderSlide
```

`SlideResolver.kt`:

```kotlin
package ru.aensidhe.dreamclock.immich

import ru.aensidhe.dreamclock.core.photos.CaptionSource
import ru.aensidhe.dreamclock.core.photos.ClockSlide
import ru.aensidhe.dreamclock.core.photos.PairedPhotoSlide
import ru.aensidhe.dreamclock.core.photos.PhotoCaption
import ru.aensidhe.dreamclock.core.photos.PlannedSlide
import ru.aensidhe.dreamclock.core.photos.PlannerAsset
import ru.aensidhe.dreamclock.core.photos.SinglePhotoSlide
import ru.aensidhe.dreamclock.core.photos.VideoSlide
import ru.aensidhe.dreamclock.core.time.ClockLocale

class SlideResolver(
    private val host: String,
    private val captions: Map<String, CaptionSource>,
    private val locale: ClockLocale,
) {
    fun resolve(slide: PlannedSlide): RenderSlide =
        when (slide) {
            is SinglePhotoSlide -> photo(slide.asset)
            is PairedPhotoSlide -> RenderPairedPhoto(photo(slide.left), photo(slide.right))
            is VideoSlide -> RenderClock
            ClockSlide -> RenderClock
        }

    private fun photo(asset: PlannerAsset): RenderPhoto =
        RenderPhoto(
            previewUrl = ImmichImageUrls.preview(host, asset.id),
            placeholderUrl = ImmichImageUrls.placeholder(host, asset.id),
            caption = captions[asset.id]?.let { PhotoCaption.format(it, locale) },
        )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.SlideResolverTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/immich/RenderSlide.kt \
        app/src/main/kotlin/ru/aensidhe/dreamclock/immich/SlideResolver.kt \
        app/src/test/kotlin/ru/aensidhe/dreamclock/immich/SlideResolverTest.kt
git commit -m "feat: :robot: resolve planned slides into renderable photo models"
```

---

## Task 3: Per-slide timing

**Files:**
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/SlideTiming.kt`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/immich/SlideTimingTest.kt`

**Interfaces:**
- Consumes: `RenderSlide`/`RenderPhoto`/`RenderPairedPhoto`/`RenderClock` (Task 2).
- Produces: `object SlideTiming { fun durationFor(slide: RenderSlide, photoSeconds: Int, analogSeconds: Int): Duration }`.

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.immich

import java.time.Duration
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import ru.aensidhe.dreamclock.core.photos.CaptionLines

class SlideTimingTest {
    private val photo = RenderPhoto("p", "t", null)

    @Test
    fun `a photo slide shows for the photo interval`() {
        assertEquals(Duration.ofSeconds(8), SlideTiming.durationFor(photo, photoSeconds = 8, analogSeconds = 10))
    }

    @Test
    fun `a paired slide shows for the photo interval`() {
        val paired = RenderPairedPhoto(photo, RenderPhoto("p2", "t2", CaptionLines("d", null)))
        assertEquals(Duration.ofSeconds(8), SlideTiming.durationFor(paired, photoSeconds = 8, analogSeconds = 10))
    }

    @Test
    fun `the clock slide shows for the analog interval`() {
        assertEquals(Duration.ofSeconds(10), SlideTiming.durationFor(RenderClock, photoSeconds = 8, analogSeconds = 10))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.SlideTimingTest'`
Expected: FAIL — unresolved reference `SlideTiming`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package ru.aensidhe.dreamclock.immich

import java.time.Duration

object SlideTiming {
    fun durationFor(
        slide: RenderSlide,
        photoSeconds: Int,
        analogSeconds: Int,
    ): Duration =
        when (slide) {
            is RenderPhoto, is RenderPairedPhoto -> Duration.ofSeconds(photoSeconds.toLong())
            RenderClock -> Duration.ofSeconds(analogSeconds.toLong())
        }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.SlideTimingTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/immich/SlideTiming.kt \
        app/src/test/kotlin/ru/aensidhe/dreamclock/immich/SlideTimingTest.kt
git commit -m "feat: :robot: pick per-slide display duration for the deck"
```

---

## Task 4: Overlay suppression decision

**Files:**
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/OverlaySuppression.kt`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/immich/OverlaySuppressionTest.kt`

Spec rule (Captions and overlay coexistence): on a paired-portrait slide whose left photo has a non-empty caption, suppress the clock overlay's bottom-left group; the top-left digital time stays. A single photo never suppresses (its caption is bottom-right, the overlay bottom-left is clear).

**Interfaces:**
- Consumes: `RenderSlide`/`RenderPairedPhoto`/`RenderPhoto` (Task 2).
- Produces: `object OverlaySuppression { fun suppressBottomLeft(slide: RenderSlide): Boolean }`.

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.immich

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import ru.aensidhe.dreamclock.core.photos.CaptionLines

class OverlaySuppressionTest {
    private fun photo(caption: CaptionLines?) = RenderPhoto("p", "t", caption)

    @Test
    fun `a paired slide with a captioned left photo suppresses the bottom-left group`() {
        val slide = RenderPairedPhoto(photo(CaptionLines("19 July 2026 | 14:32", "Berlin, Germany")), photo(null))
        assertTrue(OverlaySuppression.suppressBottomLeft(slide))
    }

    @Test
    fun `a paired slide with no left caption does not suppress`() {
        assertFalse(OverlaySuppression.suppressBottomLeft(RenderPairedPhoto(photo(null), photo(CaptionLines("d", null)))))
    }

    @Test
    fun `a single photo never suppresses`() {
        assertFalse(OverlaySuppression.suppressBottomLeft(photo(CaptionLines("d", "l"))))
    }

    @Test
    fun `the clock slide never suppresses`() {
        assertFalse(OverlaySuppression.suppressBottomLeft(RenderClock))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.OverlaySuppressionTest'`
Expected: FAIL — unresolved reference `OverlaySuppression`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package ru.aensidhe.dreamclock.immich

object OverlaySuppression {
    fun suppressBottomLeft(slide: RenderSlide): Boolean = slide is RenderPairedPhoto && slide.left.caption != null
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.OverlaySuppressionTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/immich/OverlaySuppression.kt \
        app/src/test/kotlin/ru/aensidhe/dreamclock/immich/OverlaySuppressionTest.kt
git commit -m "feat: :robot: decide when a paired caption hides the overlay bottom-left"
```

---

## Task 5: Credentials seam

**Files:**
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/CredentialsStore.kt`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/immich/CredentialsStoreTest.kt`

This is the seam that Plan 4 replaces with a Keystore-backed implementation. Plan 3 ships only the interface and two trivial implementations.

**Interfaces:**
- Consumes: `ImmichCredentials` (existing).
- Produces:
  ```kotlin
  interface CredentialsStore { fun current(): ImmichCredentials? }
  object NoCredentialsStore : CredentialsStore
  class StaticCredentialsStore(credentials: ImmichCredentials?) : CredentialsStore
  ```

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.immich

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class CredentialsStoreTest {
    @Test
    fun `the no-credentials store yields nothing`() {
        assertNull(NoCredentialsStore.current())
    }

    @Test
    fun `a static store yields its credentials`() {
        val creds = ImmichCredentials("https://immich.example", "secret")
        assertEquals(creds, StaticCredentialsStore(creds).current())
    }

    @Test
    fun `a static store built from null yields nothing`() {
        assertNull(StaticCredentialsStore(null).current())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.CredentialsStoreTest'`
Expected: FAIL — unresolved reference `CredentialsStore`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package ru.aensidhe.dreamclock.immich

interface CredentialsStore {
    fun current(): ImmichCredentials?
}

object NoCredentialsStore : CredentialsStore {
    override fun current(): ImmichCredentials? = null
}

class StaticCredentialsStore(
    private val credentials: ImmichCredentials?,
) : CredentialsStore {
    override fun current(): ImmichCredentials? = credentials
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.CredentialsStoreTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/immich/CredentialsStore.kt \
        app/src/test/kotlin/ru/aensidhe/dreamclock/immich/CredentialsStoreTest.kt
git commit -m "feat: :robot: add credentials store seam with a no-credentials default"
```

---

## Task 6: Coil image loader

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts:58-85` (dependencies block)
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/ImmichImageLoader.kt`

No unit test — this is Android/Coil glue validated on-device in Task 12. The gate for this task is that the module compiles.

- [ ] **Step 1: Add the Coil version and libraries to the catalog**

In `gradle/libs.versions.toml` under `[versions]` add (confirm the newest stable Coil 3.x resolves during Step 4; bump if a later 3.x is current):

```toml
coil3 = "3.2.0"
```

Under `[libraries]` add:

```toml
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil3" }
coil-network-okhttp = { module = "io.coil-kt.coil3:coil-network-okhttp", version.ref = "coil3" }
```

- [ ] **Step 2: Add the dependencies to the app module**

In `app/build.gradle.kts`, inside `dependencies { … }`, after the `implementation(libs.kotlinx.serialization.json)` line add:

```kotlin
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
```

- [ ] **Step 3: Write the image loader**

`app/src/main/kotlin/ru/aensidhe/dreamclock/ui/ImmichImageLoader.kt`:

```kotlin
package ru.aensidhe.dreamclock.ui

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

private const val DISK_CACHE_BYTES = 256L * 1024 * 1024

object ImmichImageLoader {
    fun create(
        context: Context,
        apiKey: String,
    ): ImageLoader {
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("x-api-key", apiKey).build())
                }
                .build()
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("immich_images").toOkioPath())
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If Coil fails to resolve, set `coil3` to the newest stable 3.x and re-run.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
        app/src/main/kotlin/ru/aensidhe/dreamclock/ui/ImmichImageLoader.kt
git commit -m "feat: :robot: add a coil image loader with the immich api key header"
```

---

## Task 7: Photo composables

**Files:**
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/PhotoSlide.kt`

No unit test — Compose UI, validated on-device in Task 12. Gate is compilation. Captions render at bottom-right (spec); each paired half owns its caption.

- [ ] **Step 1: Write the composables**

```kotlin
package ru.aensidhe.dreamclock.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import ru.aensidhe.dreamclock.core.photos.CaptionLines
import ru.aensidhe.dreamclock.immich.RenderPairedPhoto
import ru.aensidhe.dreamclock.immich.RenderPhoto

@Composable
fun PhotoSlide(
    render: RenderPhoto,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        PhotoImage(render.previewUrl, render.placeholderUrl, imageLoader, Modifier.fillMaxSize())
        render.caption?.let { CaptionBlock(it, Modifier.align(Alignment.BottomEnd).padding(24.dp)) }
    }
}

@Composable
fun PairedPhotoSlide(
    render: RenderPairedPhoto,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxSize()) {
        PhotoSlide(render.left, imageLoader, Modifier.weight(1f).fillMaxHeight())
        PhotoSlide(render.right, imageLoader, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun PhotoImage(
    previewUrl: String,
    placeholderUrl: String,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    val context = LocalPlatformContext.current
    Box(modifier) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(placeholderUrl).build(),
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        AsyncImage(
            model = ImageRequest.Builder(context).data(previewUrl).crossfade(true).build(),
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun CaptionBlock(
    lines: CaptionLines,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        lines.dateTime?.let { Text(it, color = Color.White, fontSize = 20.sp) }
        lines.location?.let { Text(it, color = Color.White, fontSize = 20.sp) }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/ui/PhotoSlide.kt
git commit -m "feat: :robot: render photo and paired-portrait slides with captions"
```

---

## Task 8: Overlay suppression parameter

**Files:**
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/ClockOverlay.kt`

Add a `suppressBottomLeft` parameter (default false, so nothing else needs changing yet). When true, the status + colloquial column is not drawn; the top-left digital time is untouched.

- [ ] **Step 1: Add the parameter and gate the bottom-left group**

Replace the `ClockOverlay` function signature and body so it reads:

```kotlin
@Composable
fun ClockOverlay(
    ui: ClockUiState,
    mode: ColorRenderMode,
    suppressBottomLeft: Boolean = false,
) {
    Box(Modifier.fillMaxSize().padding(PaddingValues(start = 48.dp, top = 48.dp, end = 48.dp, bottom = 16.dp))) {
        Text(
            ui.digital,
            Modifier.align(Alignment.TopStart),
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        if (!suppressBottomLeft) {
            Box(Modifier.align(Alignment.BottomStart)) {
                mode.RenderOverlay(ui.state) { textColor ->
                    Column(horizontalAlignment = Alignment.Start) {
                        ui.statusText?.takeIf { it.isNotBlank() }?.let { Text(it, color = textColor, fontSize = 24.sp) }
                        ui.colloquial?.let { Text(it, color = textColor, fontSize = 24.sp) }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (existing callers still compile via the default).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/ui/ClockOverlay.kt
git commit -m "feat: :robot: let the overlay hide its bottom-left group on demand"
```

---

## Task 9: Deck model and SlideDeck rewrite

**Files:**
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/SlideDeckModel.kt`
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/SlideDeck.kt`

No unit test — the deck is coroutine + Compose glue validated on-device. Gate is compilation. The deck advances one slide at a time, computes the upcoming slide one ahead to preload its images through Coil, crossfades between slides, and reports the current slide's suppression flag. When `deck`/`imageLoader` is null the deck degrades to feature-1 behaviour (analog face when `showAnalog`, else black) and reports no suppression.

**Interfaces:**
- Consumes: `SlideDriver`, `SlideResolver`, `RenderSlide`/`RenderPhoto`/`RenderPairedPhoto`/`RenderClock`, `SlideTiming`, `OverlaySuppression` (earlier tasks); `PhotoSlide`/`PairedPhotoSlide` (Task 7); `AnalogClockSlide` (existing).
- Produces:
  ```kotlin
  class SlideDeckModel(driver: SlideDriver, resolver: SlideResolver, photoSeconds: Int, analogSeconds: Int) {
      val photoSeconds: Int
      val analogSeconds: Int
      fun nextRender(now: Instant): RenderSlide
      fun preload(slide: RenderSlide, imageLoader: ImageLoader, context: PlatformContext)
  }
  @Composable fun SlideDeck(
      deck: SlideDeckModel?, imageLoader: ImageLoader?, showAnalog: Boolean,
      now: LocalDateTime, secondHandColor: Color, onSuppressBottomLeft: (Boolean) -> Unit,
  )
  ```

- [ ] **Step 1: Write the deck model**

`SlideDeckModel.kt`:

```kotlin
package ru.aensidhe.dreamclock.ui

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ImageRequest
import java.time.Instant
import ru.aensidhe.dreamclock.immich.RenderClock
import ru.aensidhe.dreamclock.immich.RenderPairedPhoto
import ru.aensidhe.dreamclock.immich.RenderPhoto
import ru.aensidhe.dreamclock.immich.RenderSlide
import ru.aensidhe.dreamclock.immich.SlideDriver
import ru.aensidhe.dreamclock.immich.SlideResolver

class SlideDeckModel(
    private val driver: SlideDriver,
    private val resolver: SlideResolver,
    val photoSeconds: Int,
    val analogSeconds: Int,
) {
    fun nextRender(now: Instant): RenderSlide = resolver.resolve(driver.next(now))

    fun preload(
        slide: RenderSlide,
        imageLoader: ImageLoader,
        context: PlatformContext,
    ) {
        urlsOf(slide).forEach { imageLoader.enqueue(ImageRequest.Builder(context).data(it).build()) }
    }

    private fun urlsOf(slide: RenderSlide): List<String> =
        when (slide) {
            is RenderPhoto -> listOf(slide.previewUrl, slide.placeholderUrl)
            is RenderPairedPhoto -> urlsOf(slide.left) + urlsOf(slide.right)
            RenderClock -> emptyList()
        }
}
```

- [ ] **Step 2: Rewrite SlideDeck**

Replace the whole of `SlideDeck.kt` with:

```kotlin
package ru.aensidhe.dreamclock.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import ru.aensidhe.dreamclock.immich.OverlaySuppression
import ru.aensidhe.dreamclock.immich.RenderClock
import ru.aensidhe.dreamclock.immich.RenderPairedPhoto
import ru.aensidhe.dreamclock.immich.RenderPhoto
import ru.aensidhe.dreamclock.immich.SlideTiming

@Composable
fun SlideDeck(
    deck: SlideDeckModel?,
    imageLoader: ImageLoader?,
    showAnalog: Boolean,
    now: LocalDateTime,
    secondHandColor: Color,
    onSuppressBottomLeft: (Boolean) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (deck == null || imageLoader == null) {
            LaunchedEffect(Unit) { onSuppressBottomLeft(false) }
            if (showAnalog) AnalogClockSlide(now, secondHandColor)
            return@Box
        }
        val context = LocalPlatformContext.current
        var current by remember(deck) { mutableStateOf(deck.nextRender(Instant.now())) }
        LaunchedEffect(deck) {
            var shown = current
            while (true) {
                onSuppressBottomLeft(OverlaySuppression.suppressBottomLeft(shown))
                val upcoming = deck.nextRender(Instant.now())
                deck.preload(upcoming, imageLoader, context)
                delay(SlideTiming.durationFor(shown, deck.photoSeconds, deck.analogSeconds).toMillis())
                current = upcoming
                shown = upcoming
            }
        }
        Crossfade(targetState = current, label = "slide") { slide ->
            when (slide) {
                is RenderPhoto -> PhotoSlide(slide, imageLoader, Modifier.fillMaxSize())
                is RenderPairedPhoto -> PairedPhotoSlide(slide, imageLoader, Modifier.fillMaxSize())
                RenderClock -> if (showAnalog) AnalogClockSlide(now, secondHandColor)
            }
        }
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: FAIL — `DreamRoot` still calls the old `SlideDeck(showAnalog, now, secondHandColor)`. That is fixed in Task 10. To confirm only the expected breakage, check the error names `DreamRoot.kt`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/ui/SlideDeckModel.kt \
        app/src/main/kotlin/ru/aensidhe/dreamclock/ui/SlideDeck.kt
git commit -m "feat: :robot: drive the slide deck from the resolved photo stream"
```

---

## Task 10: DreamRoot wiring

**Files:**
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/DreamRoot.kt`

Thread the deck, image loader, and a suppression state through to `SlideDeck` and `ClockOverlay`. Gate is compilation (`DreamContent` still calls the old `DreamRoot` — fixed in Task 11).

- [ ] **Step 1: Rewrite DreamRoot**

Replace the whole of `DreamRoot.kt` with:

```kotlin
package ru.aensidhe.dreamclock.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import coil3.ImageLoader
import ru.aensidhe.dreamclock.ui.colorrender.ColorRenderMode

@Composable
fun DreamRoot(
    state: ClockUiState,
    showAnalog: Boolean,
    mode: ColorRenderMode,
    deck: SlideDeckModel?,
    imageLoader: ImageLoader?,
) {
    var suppressBottomLeft by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        SlideDeck(
            deck = deck,
            imageLoader = imageLoader,
            showAnalog = showAnalog,
            now = java.time.LocalDateTime.now(),
            secondHandColor = stateColor(state.state),
            onSuppressBottomLeft = { suppressBottomLeft = it },
        )
        ClockOverlay(ui = state, mode = mode, suppressBottomLeft = suppressBottomLeft)
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: FAIL — `DreamContent.kt` still calls the old `DreamRoot(state, showAnalog, mode)`. Confirm the error names `DreamContent.kt` only.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/ui/DreamRoot.kt
git commit -m "feat: :robot: thread the photo deck and suppression through DreamRoot"
```

---

## Task 11: DreamContent build-out and credential bootstrap

**Files:**
- Modify: `app/build.gradle.kts` (add `buildConfig` feature + `local.properties` fields)
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/BuildConfigCredentials.kt`
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamContent.kt`
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/dream/TvDreamService.kt:80`
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamPreviewActivity.kt:35`

`local.properties` is gitignored, so the host and key never enter source control. With no `immich.*` entries the bootstrap returns `NoCredentialsStore` and the deck stays null — the production default. Plan 4 replaces `BuildConfigCredentials` with the Keystore-backed store.

- [ ] **Step 1: Enable buildConfig and inject the local.properties fields**

In `app/build.gradle.kts`, add `buildConfig = true` to `buildFeatures`:

```kotlin
    buildFeatures {
        compose = true
        buildConfig = true
    }
```

At the top of the file (above `plugins { … }`) add the imports:

```kotlin
import java.util.Properties
```

Inside `android { defaultConfig { … } }`, after `versionName = "0.1.0"`, add:

```kotlin
        val immichProps =
            Properties().apply {
                val file = rootProject.file("local.properties")
                if (file.exists()) file.inputStream().use { load(it) }
            }
        buildConfigField("String", "IMMICH_HOST", "\"${immichProps.getProperty("immich.host", "")}\"")
        buildConfigField("String", "IMMICH_KEY", "\"${immichProps.getProperty("immich.key", "")}\"")
```

- [ ] **Step 2: Write the bootstrap**

`app/src/main/kotlin/ru/aensidhe/dreamclock/immich/BuildConfigCredentials.kt`:

```kotlin
package ru.aensidhe.dreamclock.immich

import ru.aensidhe.dreamclock.BuildConfig

object BuildConfigCredentials {
    fun store(): CredentialsStore {
        val host = BuildConfig.IMMICH_HOST
        val key = BuildConfig.IMMICH_KEY
        return if (host.isNotBlank() && key.isNotBlank()) {
            StaticCredentialsStore(ImmichCredentials(host, key))
        } else {
            NoCredentialsStore
        }
    }
}
```

- [ ] **Step 3: Build the deck inside DreamContent**

Read the current `DreamContent.kt` first (it also holds `localizedStatusText`; leave that untouched). Replace the `DreamContent` function with the version below and add the imports it needs. Do not remove existing imports still in use.

```kotlin
@Composable
internal fun DreamContent(
    viewModel: ClockViewModel,
    repository: SettingsRepository,
    credentialsStore: CredentialsStore = NoCredentialsStore,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by
        repository.settings.collectAsStateWithLifecycle(
            initialValue = SettingsSerializer.defaultValue,
        )
    val context = LocalContext.current
    val credentials = remember(credentialsStore) { credentialsStore.current() }
    val imageLoader =
        remember(credentials) {
            credentials?.let { ImmichImageLoader.create(context, it.apiKey) }
        }
    val deck by
        produceState<SlideDeckModel?>(
            initialValue = null,
            credentials,
            settings.photosEnabled,
            settings.daysEitherSide,
            settings.maxYearsBack,
            settings.analogEveryNSlides,
            settings.photoIntervalSeconds,
            settings.analogSlideSeconds,
            settings.maxClockGapSeconds,
            settings.language,
        ) {
            value = buildSlideDeck(credentials, settings)
        }
    DreamRoot(
        state = uiState,
        showAnalog = settings.showAnalogSlide,
        mode = settings.colorRenderMode.toColorRenderMode(),
        deck = deck,
        imageLoader = imageLoader,
    )
}

private suspend fun buildSlideDeck(
    credentials: ImmichCredentials?,
    settings: Settings,
): SlideDeckModel? {
    if (credentials == null || !settings.photosEnabled) return null
    val repository =
        ImmichRepository(
            apiFactory = { host -> ImmichClient.api(host) },
            today = { LocalDate.now() },
            zone = ZoneId.systemDefault(),
        )
    val config = PhotoFetchConfig(settings.daysEitherSide, settings.maxYearsBack)
    val assets =
        runCatching { repository.loadAssets(credentials, config) }.getOrDefault(emptyList())
    if (!PhotoFallback.shouldShowPhotos(enabled = true, hasCredentials = true, assetCount = assets.size)) {
        return null
    }
    val locale =
        if (effectiveLocale(settings.language, Locale.getDefault()).language == "ru") {
            ClockLocale.RU
        } else {
            ClockLocale.EN
        }
    val pool = AssetPool(assets, Random(System.nanoTime()))
    val planner = SlidePlanner(settings.analogEveryNSlides.coerceAtLeast(1))
    val driver =
        SlideDriver(
            assets = pool.endlessSequence().iterator(),
            planner = planner,
            maxGap = Duration.ofSeconds(settings.maxClockGapSeconds.toLong()),
            lastClockAt = Instant.now(),
        )
    val resolver = SlideResolver(credentials.host, assets.associate { it.id to it.caption }, locale)
    return SlideDeckModel(driver, resolver, settings.photoIntervalSeconds, settings.analogSlideSeconds)
}
```

Imports to add to `DreamContent.kt` (keep the existing ones):

```kotlin
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.random.Random
import ru.aensidhe.dreamclock.core.photos.SlidePlanner
import ru.aensidhe.dreamclock.core.time.ClockLocale
import ru.aensidhe.dreamclock.immich.AssetPool
import ru.aensidhe.dreamclock.immich.CredentialsStore
import ru.aensidhe.dreamclock.immich.ImmichClient
import ru.aensidhe.dreamclock.immich.ImmichCredentials
import ru.aensidhe.dreamclock.immich.ImmichRepository
import ru.aensidhe.dreamclock.immich.NoCredentialsStore
import ru.aensidhe.dreamclock.immich.PhotoFallback
import ru.aensidhe.dreamclock.immich.PhotoFetchConfig
import ru.aensidhe.dreamclock.immich.SlideDriver
import ru.aensidhe.dreamclock.immich.SlideResolver
import ru.aensidhe.dreamclock.settings.SettingsSerializer
import ru.aensidhe.dreamclock.settings.effectiveLocale
import ru.aensidhe.dreamclock.ui.ImmichImageLoader
import ru.aensidhe.dreamclock.ui.SlideDeckModel
```

(Some of these may already be imported. Let ktlint's import ordering settle in the gate; remove any that end up unused.)

- [ ] **Step 4: Pass the store from both entry points**

In `TvDreamService.kt:80` change:

```kotlin
            setContent { DreamContent(viewModel, repository) }
```

to:

```kotlin
            setContent { DreamContent(viewModel, repository, BuildConfigCredentials.store()) }
```

In `DreamPreviewActivity.kt:35` change:

```kotlin
        setContent { DreamContent(viewModel, repository) }
```

to:

```kotlin
        setContent { DreamContent(viewModel, repository, BuildConfigCredentials.store()) }
```

Add `import ru.aensidhe.dreamclock.immich.BuildConfigCredentials` to both files.

- [ ] **Step 5: Verify the whole module compiles and the pure units still pass**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. With no `immich.*` in `local.properties`, the deck is null and behaviour is identical to feature 1.

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts \
        app/src/main/kotlin/ru/aensidhe/dreamclock/immich/BuildConfigCredentials.kt \
        app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamContent.kt \
        app/src/main/kotlin/ru/aensidhe/dreamclock/dream/TvDreamService.kt \
        app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamPreviewActivity.kt
git commit -m "feat: :robot: build the photo deck behind a credentials store"
```

---

## Task 12: Gate and on-device validation

**Files:** none (verification only).

- [ ] **Step 1: Run the full CI gate**

Run: `./gradlew ktlintCheck detekt :core:test :app:testDebugUnitTest assemble`
Expected: BUILD SUCCESSFUL. Fix any ktlint/detekt findings (keep lines ≤ 120) and re-run until green. Commit any lint-only fixes:

```bash
git add -u
git commit -m "style: :robot: satisfy linters for the photo rendering deck"
```

(Skip the commit if nothing changed.)

- [ ] **Step 2: Validate the clock fallback with no credentials**

Ensure `local.properties` has no `immich.*` entries. Install and open the dream (or `DreamPreviewActivity`). Expected: the feature-1 clock behaves exactly as before — analog face when the analog slide is on, digital overlay always present, no crash. This proves the null-deck path.

- [ ] **Step 3: Validate the photo path with real credentials**

Add to the gitignored `local.properties` (never commit these):

```properties
immich.host=https://your-immich-host
immich.key=your-read-only-api-key
```

Set `photos_enabled = true` in settings (or temporarily default it true in `SettingsSerializer` for the test run, then revert). Rebuild and open the dream. Verify on-device:
- Photos load (preview sharp, brief thumbnail placeholder), one landscape per slide.
- Two portraits pair side by side, each with its own bottom-right caption (date/time over city, country in the selected language).
- On a paired slide whose left photo has a caption, the overlay's bottom-left status/colloquial group disappears while the top-left digital time stays.
- The analog clock slide appears on the count cadence and at least every `max_clock_gap_seconds`.
- Slides crossfade; the next image is already loaded when it appears.
- Captions omit missing parts; a photo with no EXIF shows no caption.

- [ ] **Step 4: Confirm no secrets are staged**

Run: `git status --porcelain` and `git diff --cached`
Expected: `local.properties` is untracked/ignored and never staged. If `photos_enabled` was flipped in `SettingsSerializer` for the test, revert it.

- [ ] **Step 5: Final gate**

Run: `./gradlew ktlintCheck detekt :core:test :app:testDebugUnitTest assemble`
Expected: BUILD SUCCESSFUL. The branch is ready to rebase onto main and open a PR.

---

## Roadmap after this plan

- Plan 4 — Settings surface + manual credentials: the `SettingsScreen` Immich section (enable toggle, host/key fields, numeric steppers, a live status line via a repository health probe, Ru/En labels) plus a Keystore-backed `CredentialsStore` implementation that replaces `BuildConfigCredentials`, so host/key entered in the UI are stored securely (key in Keystore, host in proto). The milestone that makes the photo flow usable and on-device testable end to end; also adds periodic date-rollover refetch.
- Plan 5 — Credentials pairing polish: local-network QR pairing on top of Plan 4's manual entry — the Ktor `PairingServer`, `PairingCrypto` (AES-GCM), `QrCode` (ZXing), the served phone page (WebCrypto, two modes), and the email/password mint path with least-privilege keys.
- Plan 6 — Video and audio: add `video_audio_mode` to the proto; broaden the Immich fetch and mapper to videos; a Media3 `VideoSlide` composable replacing the resolver's `VideoSlide -> RenderClock` shortcut; schedule-aware three-way audio.

## Self-Review

Spec coverage: photo/paired rendering (Tasks 2, 7), bottom-right captions via `PhotoCaption` (Tasks 2, 7), `preview`+`thumbnail` variants (Task 1, 7), Coil disk cache (Task 6), crossfade + preload (Task 9), per-slide durations M2/M (Task 3, 9), caption-vs-clock suppression (Tasks 4, 8, 9, 10), clock fallback when nothing usable (Tasks 9, 11 via `PhotoFallback` and null deck), `CredentialsStore` seam with no-credentials default (Tasks 5, 11), on-device validation (Task 12). Deferred by design and noted: videos/audio (Plan 5), Keystore/pairing (Plan 4), settings UI + rollover refetch (Plan 6).

Type consistency: `RenderPhoto(previewUrl, placeholderUrl, caption)`, `RenderPairedPhoto(left, right)`, `RenderClock` used identically across Tasks 2–4, 7, 9. `SlideDeckModel(driver, resolver, photoSeconds, analogSeconds)` and `SlideDeck(deck, imageLoader, showAnalog, now, secondHandColor, onSuppressBottomLeft)` match between Tasks 9 and 10. `ClockOverlay(..., suppressBottomLeft)` matches Tasks 8 and 10. `ImmichImageLoader.create(context, apiKey)` matches Tasks 6 and 11. The `:core` `PairedPhotoSlide` (data) versus `:app` `PairedPhotoSlide` (composable) clash is called out and kept in separate packages.
