# Immich Data Layer Implementation Plan (Plan 2 of 6)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the `:app` Immich data layer — the Retrofit client and serialization models, the per-year windowed fetch with full pagination, asset normalization, the seeded shuffle pool, and the runtime slide-driver that ties `SlidePlanner` + `ClockGapPolicy` to wall-clock time — all unit-tested against OkHttp `MockWebServer` and an injected clock, with no Compose, credentials UI, or on-device dependency.

**Architecture:** A new `ru.aensidhe.dreamclock.immich` package in `:app`. It consumes the pure `:core` `photos` types from Plan 1 (`SimilarTimeWindows`, `YearWalk`, `AssetOrientation`, `SlidePlanner`, `ClockGapPolicy`, `PhotoCaption` inputs) and exposes a data-and-driver surface the next plan's Compose slideshow will render. Credentials arrive as a plain `ImmichCredentials(host, apiKey)` value object passed in per call; where those credentials come from (manual entry, Keystore, pairing) is a later plan. This plan ships a tested library and changes no on-screen behaviour: the dream still shows the feature-1 clock.

**Tech Stack:** Kotlin 2.4.0 (Android), Retrofit 2.11 + OkHttp 4.12, kotlinx.serialization JSON 1.7.3, `java.time`, kotlin.test + JUnit 5, OkHttp `MockWebServer` (test only).

## Global Constraints

- Package root for all new code: `ru.aensidhe.dreamclock.immich` (in module `:app`).
- New logic depends only on `:core` `ru.aensidhe.dreamclock.core.photos` and JVM/Android libraries — no Compose imports in this package.
- Credentials are never persisted here: `ImmichCredentials` is passed in per call. Secret storage is a later plan.
- Immich `takenAfter` / `takenBefore` MUST carry a trailing offset (`Z` or `±HH:mm`); format with `DateTimeFormatter.ISO_OFFSET_DATE_TIME` from a zoned value, never a zone-less `LocalDateTime` (the server rejects the latter with 400).
- This plan fetches images only (`type = "IMAGE"` in the search request; the mapper drops non-image assets). Videos are added in a later plan.
- JDK toolchain 21 (already set). Tests use `kotlin.test` assertions with `org.junit.jupiter.api.Test` on the JUnit Platform; network tests use `kotlinx.coroutines.runBlocking` (real IO against `MockWebServer`).
- ktlint + detekt must pass: 4-space indent, trailing commas on multi-line argument/parameter lists, no wildcard imports, lines within the configured max length (wrap long `assertEquals`/JSON lines).
- Commit convention: Conventional Commits with a `:robot:` marker after the type. No `Co-Authored-By` trailer.
- Per-task test command: `./gradlew :app:testDebugUnitTest`. Pre-commit gate: `./gradlew :app:ktlintFormat :app:ktlintCheck :app:detekt :core:test :app:testDebugUnitTest`.

---

### Task 1: Build setup — dependencies and serialization plugin

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: nothing.
- Produces: Retrofit, OkHttp, kotlinx.serialization JSON, the Retrofit kotlinx-serialization converter on the `:app` `implementation` classpath; `MockWebServer` on `testImplementation`; the `kotlin.plugin.serialization` Gradle plugin applied to `:app`.

- [ ] **Step 1: Add versions and libraries to the catalog**

In `gradle/libs.versions.toml`, under `[versions]` add:

```toml
retrofit = "2.11.0"
okhttp = "4.12.0"
kotlinxSerialization = "1.7.3"
```

Under `[libraries]` add:

```toml
retrofit = { module = "com.squareup.retrofit2:retrofit", version.ref = "retrofit" }
retrofit-kotlinx-serialization = { module = "com.squareup.retrofit2:converter-kotlinx-serialization", version.ref = "retrofit" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "okhttp" }
```

Under `[plugins]` add:

```toml
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 2: Apply the plugin and add the dependencies in the app module**

In `app/build.gradle.kts`, add to the `plugins { }` block (after the existing `alias(libs.plugins.protobuf)` line is fine):

```kotlin
    alias(libs.plugins.kotlin.serialization)
```

In the `dependencies { }` block, add after `implementation(libs.kotlinx.coroutines.core)`:

```kotlin
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
```

And add after `testImplementation(libs.kotlinx.coroutines.test)`:

```kotlin
    testImplementation(libs.okhttp.mockwebserver)
```

- [ ] **Step 3: Verify the module still configures and compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (dependencies resolve; nothing uses them yet).

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: :robot: add retrofit, okhttp, kotlinx-serialization for immich client"
```

---

### Task 2: Photo settings — proto fields and defaults

**Files:**
- Modify: `app/src/main/proto/settings.proto`
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsSerializer.kt`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/settings/SettingsRepositoryTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: new `Settings` fields `photos_enabled`, `immich_host`, `days_either_side`, `max_years_back`, `photo_interval_seconds`, `analog_every_n_slides`, `max_clock_gap_seconds`, `analog_slide_seconds` with defaults set in `SettingsSerializer.defaultValue`. The API key is deliberately not here (secret storage is a later plan). `video_audio_mode` is deliberately not here (the video plan adds it).

- [ ] **Step 1: Write the failing test**

Add these two test methods inside the existing `SettingsRepositoryTest` class in `app/src/test/kotlin/ru/aensidhe/dreamclock/settings/SettingsRepositoryTest.kt`:

```kotlin
    @Test
    fun `photo settings have sensible defaults`() =
        runTest {
            val s = SettingsRepository.inMemory().settings.first()
            assertEquals(false, s.photosEnabled)
            assertEquals("", s.immichHost)
            assertEquals(3, s.daysEitherSide)
            assertEquals(0, s.maxYearsBack)
            assertEquals(8, s.photoIntervalSeconds)
            assertEquals(5, s.analogEveryNSlides)
            assertEquals(300, s.maxClockGapSeconds)
            assertEquals(10, s.analogSlideSeconds)
        }

    @Test
    fun `photos enabled round-trips`() =
        runTest {
            val repo = SettingsRepository.inMemory()
            repo.update { it.toBuilder().setPhotosEnabled(true).setImmichHost("https://immich.lan").build() }
            val s = repo.settings.first()
            assertEquals(true, s.photosEnabled)
            assertEquals("https://immich.lan", s.immichHost)
        }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.settings.SettingsRepositoryTest'`
Expected: FAIL — compilation error, `photosEnabled` / `daysEitherSide` / etc. unresolved (proto not regenerated yet).

- [ ] **Step 3: Add the proto fields**

Replace the `Settings` message in `app/src/main/proto/settings.proto` with:

```proto
message Settings {
  Language language = 1;
  bool show_colloquial = 2;
  bool show_seconds = 3;
  bool show_analog_slide = 4;
  ColorRenderModeProto color_render_mode = 5;
  bool photos_enabled = 6;
  string immich_host = 7;
  int32 days_either_side = 8;
  int32 max_years_back = 9;
  int32 photo_interval_seconds = 10;
  int32 analog_every_n_slides = 11;
  int32 max_clock_gap_seconds = 12;
  int32 analog_slide_seconds = 13;
}
```

- [ ] **Step 4: Set the non-zero defaults**

proto3 scalars default to zero/empty, so set the meaningful defaults in code. Replace the `defaultValue` initializer in `SettingsSerializer.kt` with:

```kotlin
    override val defaultValue: Settings =
        Settings
            .newBuilder()
            .setShowColloquial(true)
            .setShowSeconds(true)
            .setShowAnalogSlide(true)
            .setPhotosEnabled(false)
            .setImmichHost("")
            .setDaysEitherSide(3)
            .setMaxYearsBack(0)
            .setPhotoIntervalSeconds(8)
            .setAnalogEveryNSlides(5)
            .setMaxClockGapSeconds(300)
            .setAnalogSlideSeconds(10)
            .build()
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.settings.SettingsRepositoryTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
./gradlew :app:ktlintFormat
git add app/src/main/proto/settings.proto app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsSerializer.kt app/src/test/kotlin/ru/aensidhe/dreamclock/settings/SettingsRepositoryTest.kt
git commit -m "feat: :robot: add immich photo settings fields with defaults"
```

---

### Task 3: Immich serialization models

**Files:**
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/ImmichModels.kt`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/immich/ImmichModelsTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `@Serializable data class SearchMetadataRequest(takenAfter, takenBefore, type = "IMAGE", withExif = true, page = 1, size = 100)`
  - `@Serializable data class SearchResponse(assets: SearchAssets)`
  - `@Serializable data class SearchAssets(total = 0, count = 0, items = emptyList(), nextPage: String? = null)`
  - `@Serializable data class ImmichAsset(id, type, localDateTime: String? = null, exifInfo: ExifInfo? = null)`
  - `@Serializable data class ExifInfo(dateTimeOriginal, city, country, exifImageWidth, exifImageHeight, orientation — all nullable)`
  - `internal val immichJson: Json` configured with `ignoreUnknownKeys = true` and `encodeDefaults = true` (shared by the client and tests).

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.immich

import kotlinx.serialization.encodeToString
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ImmichModelsTest {
    @Test
    fun `decodes a search response and ignores unknown keys`() {
        val fixture =
            """
            {
              "albums": { "items": [] },
              "assets": {
                "total": 2,
                "count": 2,
                "items": [
                  {
                    "id": "a1",
                    "type": "IMAGE",
                    "localDateTime": "2026-07-19T14:32:00.000Z",
                    "exifInfo": {
                      "dateTimeOriginal": "2026-07-19T14:32:00.000+02:00",
                      "city": "Berlin",
                      "country": "Germany",
                      "exifImageWidth": 3000,
                      "exifImageHeight": 4000,
                      "orientation": "1"
                    }
                  },
                  { "id": "v1", "type": "VIDEO", "exifInfo": null }
                ],
                "nextPage": "2"
              }
            }
            """.trimIndent()
        val decoded = immichJson.decodeFromString<SearchResponse>(fixture)
        assertEquals(2, decoded.assets.total)
        assertEquals(2, decoded.assets.items.size)
        assertEquals("a1", decoded.assets.items[0].id)
        assertEquals("Berlin", decoded.assets.items[0].exifInfo?.city)
        assertEquals("2", decoded.assets.nextPage)
    }

    @Test
    fun `encodes a request with defaults included`() {
        val encoded = immichJson.encodeToString(SearchMetadataRequest(takenAfter = "A", takenBefore = "B"))
        assertTrue(encoded.contains("\"takenAfter\":\"A\""))
        assertTrue(encoded.contains("\"takenBefore\":\"B\""))
        assertTrue(encoded.contains("\"type\":\"IMAGE\""))
        assertTrue(encoded.contains("\"withExif\":true"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.ImmichModelsTest'`
Expected: FAIL — model types and `immichJson` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package ru.aensidhe.dreamclock.immich

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val immichJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

@Serializable
data class SearchMetadataRequest(
    val takenAfter: String,
    val takenBefore: String,
    val type: String = "IMAGE",
    val withExif: Boolean = true,
    val page: Int = 1,
    val size: Int = 100,
)

@Serializable
data class SearchResponse(
    val assets: SearchAssets,
)

@Serializable
data class SearchAssets(
    val total: Int = 0,
    val count: Int = 0,
    val items: List<ImmichAsset> = emptyList(),
    val nextPage: String? = null,
)

@Serializable
data class ImmichAsset(
    val id: String,
    val type: String,
    val localDateTime: String? = null,
    val exifInfo: ExifInfo? = null,
)

@Serializable
data class ExifInfo(
    val dateTimeOriginal: String? = null,
    val city: String? = null,
    val country: String? = null,
    val exifImageWidth: Int? = null,
    val exifImageHeight: Int? = null,
    val orientation: String? = null,
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.ImmichModelsTest'`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
./gradlew :app:ktlintFormat
git add app/src/main/kotlin/ru/aensidhe/dreamclock/immich/ImmichModels.kt app/src/test/kotlin/ru/aensidhe/dreamclock/immich/ImmichModelsTest.kt
git commit -m "feat: :robot: add immich search serialization models"
```

---

### Task 4: Retrofit API and client factory

**Files:**
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/ImmichApi.kt`
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/ImmichClient.kt`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/immich/ImmichClientTest.kt`

**Interfaces:**
- Consumes: `SearchMetadataRequest`, `SearchResponse`, `immichJson` (Task 3).
- Produces:
  - `interface ImmichApi { suspend fun searchMetadata(apiKey: String, request: SearchMetadataRequest): SearchResponse }` — `@POST("api/search/metadata")`, `@Header("x-api-key")`, `@Body`.
  - `fun interface ImmichApiFactory { fun create(host: String): ImmichApi }`.
  - `object ImmichClient { fun api(host: String, client: OkHttpClient = OkHttpClient()): ImmichApi }` — builds Retrofit with a normalized trailing-slash base URL and the kotlinx-serialization converter over `immichJson`.

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.immich

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ImmichClientTest {
    @Test
    fun `searchMetadata posts to the right path with api key and body`() =
        runBlocking {
            val server = MockWebServer()
            server.enqueue(
                MockResponse()
                    .addHeader("Content-Type", "application/json")
                    .setBody("{\"assets\":{\"total\":0,\"count\":0,\"items\":[],\"nextPage\":null}}"),
            )
            server.start()
            try {
                val api = ImmichClient.api(server.url("/").toString())
                val response = api.searchMetadata("secret-key", SearchMetadataRequest(takenAfter = "A", takenBefore = "B"))
                assertEquals(0, response.assets.total)

                val recorded = server.takeRequest()
                assertEquals("POST", recorded.method)
                assertEquals("/api/search/metadata", recorded.path)
                assertEquals("secret-key", recorded.getHeader("x-api-key"))
                assertTrue(recorded.body.readUtf8().contains("\"takenAfter\":\"A\""))
            } finally {
                server.shutdown()
            }
        }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.ImmichClientTest'`
Expected: FAIL — `ImmichClient` / `ImmichApi` unresolved.

- [ ] **Step 3: Write minimal implementation**

`ImmichApi.kt`:

```kotlin
package ru.aensidhe.dreamclock.immich

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ImmichApi {
    @POST("api/search/metadata")
    suspend fun searchMetadata(
        @Header("x-api-key") apiKey: String,
        @Body request: SearchMetadataRequest,
    ): SearchResponse
}

fun interface ImmichApiFactory {
    fun create(host: String): ImmichApi
}
```

`ImmichClient.kt`:

```kotlin
package ru.aensidhe.dreamclock.immich

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object ImmichClient {
    fun api(
        host: String,
        client: OkHttpClient = OkHttpClient(),
    ): ImmichApi =
        Retrofit
            .Builder()
            .baseUrl(normalizeBaseUrl(host))
            .client(client)
            .addConverterFactory(immichJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ImmichApi::class.java)

    private fun normalizeBaseUrl(host: String): String = if (host.endsWith("/")) host else "$host/"
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.ImmichClientTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
./gradlew :app:ktlintFormat
git add app/src/main/kotlin/ru/aensidhe/dreamclock/immich/ImmichApi.kt app/src/main/kotlin/ru/aensidhe/dreamclock/immich/ImmichClient.kt app/src/test/kotlin/ru/aensidhe/dreamclock/immich/ImmichClientTest.kt
git commit -m "feat: :robot: add immich retrofit api and client factory"
```

---

### Task 5: Search date bounds (window → offset date-time strings)

**Files:**
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/ImmichSearchBounds.kt`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/immich/ImmichSearchBoundsTest.kt`

**Interfaces:**
- Consumes: `ru.aensidhe.dreamclock.core.photos.DateWindow` (Plan 1).
- Produces:
  - `data class ImmichSearchBounds(val takenAfter: String, val takenBefore: String)`
  - `object ImmichSearchBoundsFactory { fun forWindow(window: DateWindow, zone: ZoneId): ImmichSearchBounds }`
  - Semantics: `takenAfter` = start of `fromInclusive` in `zone`; `takenBefore` = `23:59:59` of `toInclusive` in `zone`; both formatted with `ISO_OFFSET_DATE_TIME` (trailing offset guaranteed).

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.immich

import java.time.LocalDate
import java.time.ZoneOffset
import ru.aensidhe.dreamclock.core.photos.DateWindow
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class ImmichSearchBoundsTest {
    @Test
    fun `formats inclusive day bounds with a trailing offset`() {
        val window = DateWindow(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 23))
        val bounds = ImmichSearchBoundsFactory.forWindow(window, ZoneOffset.ofHours(2))
        assertEquals("2026-07-17T00:00:00+02:00", bounds.takenAfter)
        assertEquals("2026-07-23T23:59:59+02:00", bounds.takenBefore)
    }

    @Test
    fun `utc window carries a Z offset`() {
        val window = DateWindow(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1))
        val bounds = ImmichSearchBoundsFactory.forWindow(window, ZoneOffset.UTC)
        assertEquals("2026-01-01T00:00:00Z", bounds.takenAfter)
        assertEquals("2026-01-01T23:59:59Z", bounds.takenBefore)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.ImmichSearchBoundsTest'`
Expected: FAIL — `ImmichSearchBounds` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package ru.aensidhe.dreamclock.immich

import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import ru.aensidhe.dreamclock.core.photos.DateWindow

data class ImmichSearchBounds(
    val takenAfter: String,
    val takenBefore: String,
)

object ImmichSearchBoundsFactory {
    private val FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun forWindow(
        window: DateWindow,
        zone: ZoneId,
    ): ImmichSearchBounds {
        val after = window.fromInclusive.atStartOfDay(zone).toOffsetDateTime()
        val before = window.toInclusive.atTime(LocalTime.of(23, 59, 59)).atZone(zone).toOffsetDateTime()
        return ImmichSearchBounds(after.format(FORMAT), before.format(FORMAT))
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.ImmichSearchBoundsTest'`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
./gradlew :app:ktlintFormat
git add app/src/main/kotlin/ru/aensidhe/dreamclock/immich/ImmichSearchBounds.kt app/src/test/kotlin/ru/aensidhe/dreamclock/immich/ImmichSearchBoundsTest.kt
git commit -m "feat: :robot: add immich search date bounds formatting"
```

---

### Task 6: Asset normalization (ImmichAsset → SlideAsset)

**Files:**
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/SlideAsset.kt`
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/AssetMapper.kt`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/immich/AssetMapperTest.kt`

**Interfaces:**
- Consumes: `ImmichAsset`, `ExifInfo` (Task 3); `AssetOrientation`, `Orientation`, `SlideMediaKind`, `PlannerAsset`, `CaptionSource` (Plan 1 `:core`).
- Produces:
  - `data class SlideAsset(val id: String, val kind: SlideMediaKind, val orientation: Orientation, val caption: CaptionSource)` with `fun toPlannerAsset(): PlannerAsset`.
  - `object AssetMapper { fun toSlideAsset(asset: ImmichAsset): SlideAsset? }` — returns null for non-`IMAGE` types and blank ids; orientation via `AssetOrientation.of` (exif width/height/orientation, defaulting to 0 dimensions when absent → LANDSCAPE); `caption.takenAt` parsed from `dateTimeOriginal` else `localDateTime` (offset or plain), city/country passed through.

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.immich

import java.time.LocalDateTime
import ru.aensidhe.dreamclock.core.photos.Orientation
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class AssetMapperTest {
    @Test
    fun `maps a portrait image with full exif`() {
        val asset =
            ImmichAsset(
                id = "a1",
                type = "IMAGE",
                localDateTime = "2026-07-19T14:32:00.000Z",
                exifInfo =
                    ExifInfo(
                        dateTimeOriginal = "2026-07-19T14:32:00.000+02:00",
                        city = "Berlin",
                        country = "Germany",
                        exifImageWidth = 3000,
                        exifImageHeight = 4000,
                        orientation = "1",
                    ),
            )
        val slide = AssetMapper.toSlideAsset(asset)!!
        assertEquals("a1", slide.id)
        assertEquals(Orientation.PORTRAIT, slide.orientation)
        assertEquals(LocalDateTime.of(2026, 7, 19, 14, 32), slide.caption.takenAt)
        assertEquals("Berlin", slide.caption.city)
        assertEquals("Germany", slide.caption.country)
    }

    @Test
    fun `exif orientation tag rotates a landscape image to portrait`() {
        val asset =
            ImmichAsset(
                id = "a2",
                type = "IMAGE",
                exifInfo = ExifInfo(exifImageWidth = 4000, exifImageHeight = 3000, orientation = "6"),
            )
        assertEquals(Orientation.PORTRAIT, AssetMapper.toSlideAsset(asset)!!.orientation)
    }

    @Test
    fun `image without exif is landscape with an empty caption`() {
        val slide = AssetMapper.toSlideAsset(ImmichAsset(id = "a3", type = "IMAGE"))!!
        assertEquals(Orientation.LANDSCAPE, slide.orientation)
        assertNull(slide.caption.takenAt)
        assertNull(slide.caption.city)
    }

    @Test
    fun `video assets are dropped`() {
        assertNull(AssetMapper.toSlideAsset(ImmichAsset(id = "v1", type = "VIDEO")))
    }

    @Test
    fun `blank id is dropped`() {
        assertNull(AssetMapper.toSlideAsset(ImmichAsset(id = "", type = "IMAGE")))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.AssetMapperTest'`
Expected: FAIL — `SlideAsset` / `AssetMapper` unresolved.

- [ ] **Step 3: Write minimal implementation**

`SlideAsset.kt`:

```kotlin
package ru.aensidhe.dreamclock.immich

import ru.aensidhe.dreamclock.core.photos.CaptionSource
import ru.aensidhe.dreamclock.core.photos.Orientation
import ru.aensidhe.dreamclock.core.photos.PlannerAsset
import ru.aensidhe.dreamclock.core.photos.SlideMediaKind

data class SlideAsset(
    val id: String,
    val kind: SlideMediaKind,
    val orientation: Orientation,
    val caption: CaptionSource,
) {
    fun toPlannerAsset(): PlannerAsset = PlannerAsset(id, kind, orientation)
}
```

`AssetMapper.kt`:

```kotlin
package ru.aensidhe.dreamclock.immich

import java.time.LocalDateTime
import java.time.OffsetDateTime
import ru.aensidhe.dreamclock.core.photos.AssetOrientation
import ru.aensidhe.dreamclock.core.photos.CaptionSource
import ru.aensidhe.dreamclock.core.photos.SlideMediaKind

object AssetMapper {
    fun toSlideAsset(asset: ImmichAsset): SlideAsset? {
        if (asset.type != "IMAGE" || asset.id.isBlank()) return null
        val exif = asset.exifInfo
        val orientation =
            AssetOrientation.of(
                width = exif?.exifImageWidth ?: 0,
                height = exif?.exifImageHeight ?: 0,
                exifOrientation = exif?.orientation?.trim()?.toIntOrNull(),
            )
        return SlideAsset(
            id = asset.id,
            kind = SlideMediaKind.PHOTO,
            orientation = orientation,
            caption =
                CaptionSource(
                    takenAt = parseTakenAt(exif?.dateTimeOriginal ?: asset.localDateTime),
                    city = exif?.city,
                    country = exif?.country,
                ),
        )
    }

    private fun parseTakenAt(value: String?): LocalDateTime? {
        if (value.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(value).toLocalDateTime() }
            .recoverCatching { LocalDateTime.parse(value) }
            .getOrNull()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.AssetMapperTest'`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
./gradlew :app:ktlintFormat
git add app/src/main/kotlin/ru/aensidhe/dreamclock/immich/SlideAsset.kt app/src/main/kotlin/ru/aensidhe/dreamclock/immich/AssetMapper.kt app/src/test/kotlin/ru/aensidhe/dreamclock/immich/AssetMapperTest.kt
git commit -m "feat: :robot: add immich asset normalization to slide assets"
```

---

### Task 7: Seeded shuffle pool

**Files:**
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/AssetPool.kt`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/immich/AssetPoolTest.kt`

**Interfaces:**
- Consumes: `SlideAsset` (Task 6).
- Produces:
  - `class AssetPool(assets: List<SlideAsset>, random: Random) { fun endlessSequence(): Sequence<SlideAsset> }`
  - Semantics: requires a non-empty list; yields the assets shuffled with the injected `Random`, reshuffling with the same `Random` instance each time the pool exhausts, forever. Determinism given a seeded `Random` is what makes it testable and what the shuffle-across-all-years behaviour relies on.

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.immich

import kotlin.random.Random
import ru.aensidhe.dreamclock.core.photos.CaptionSource
import ru.aensidhe.dreamclock.core.photos.Orientation
import ru.aensidhe.dreamclock.core.photos.SlideMediaKind
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class AssetPoolTest {
    private fun asset(id: String) =
        SlideAsset(id, SlideMediaKind.PHOTO, Orientation.LANDSCAPE, CaptionSource(null, null, null))

    @Test
    fun `cycles deterministically with a seeded random`() {
        val items = listOf(asset("a"), asset("b"), asset("c"))
        val expected =
            buildList {
                val r = Random(42)
                repeat(2) { addAll(items.shuffled(r)) }
            }
        val actual = AssetPool(items, Random(42)).endlessSequence().take(6).toList()
        assertEquals(expected, actual)
        assertEquals(6, actual.size)
        assertEquals(listOf("a", "a", "b", "b", "c", "c").sorted(), actual.map { it.id }.sorted())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.AssetPoolTest'`
Expected: FAIL — `AssetPool` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package ru.aensidhe.dreamclock.immich

import kotlin.random.Random

class AssetPool(
    private val assets: List<SlideAsset>,
    private val random: Random,
) {
    init {
        require(assets.isNotEmpty()) { "assets must not be empty" }
    }

    fun endlessSequence(): Sequence<SlideAsset> =
        sequence {
            while (true) {
                yieldAll(assets.shuffled(random))
            }
        }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.AssetPoolTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
./gradlew :app:ktlintFormat
git add app/src/main/kotlin/ru/aensidhe/dreamclock/immich/AssetPool.kt app/src/test/kotlin/ru/aensidhe/dreamclock/immich/AssetPoolTest.kt
git commit -m "feat: :robot: add seeded shuffle asset pool"
```

---

### Task 8: Immich repository (windowed fetch with pagination and year-walk)

**Files:**
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/ImmichCredentials.kt`
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/ImmichRepository.kt`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/immich/ImmichRepositoryTest.kt`

**Interfaces:**
- Consumes: `ImmichApiFactory`, `SearchMetadataRequest` (Task 4); `ImmichSearchBoundsFactory` (Task 5); `AssetMapper`, `SlideAsset` (Task 6); `SimilarTimeWindows`, `YearWalk` (Plan 1 `:core`).
- Produces:
  - `data class ImmichCredentials(val host: String, val apiKey: String)`
  - `data class PhotoFetchConfig(val daysEitherSide: Int, val maxYearsBack: Int, val pageSize: Int = 100)`
  - `class ImmichRepository(apiFactory: ImmichApiFactory, today: () -> LocalDate, zone: ZoneId) { suspend fun loadAssets(credentials: ImmichCredentials, config: PhotoFetchConfig): List<SlideAsset> }`
  - Semantics: for year offsets 0, 1, 2, …, build the window with `SimilarTimeWindows`, page through `searchMetadata` (page 1..N following `nextPage`), map items to `SlideAsset` (images only), pool them all; increment the empty-year streak on a year that yields nothing and reset it otherwise; continue while `YearWalk.shouldQueryNextYear(yearsQueried, consecutiveEmptyYears, maxYearsBack)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.immich

import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ImmichRepositoryTest {
    private fun page(
        ids: List<String>,
        nextPage: String?,
    ): String {
        val items =
            ids.joinToString(",") { id ->
                "{\"id\":\"$id\",\"type\":\"IMAGE\",\"exifInfo\":" +
                    "{\"exifImageWidth\":4000,\"exifImageHeight\":3000,\"city\":\"Berlin\",\"country\":\"Germany\"}}"
            }
        val next = if (nextPage == null) "null" else "\"$nextPage\""
        return "{\"assets\":{\"total\":${ids.size},\"count\":${ids.size},\"items\":[$items],\"nextPage\":$next}}"
    }

    @Test
    fun `loadAssets paginates within a year and stops at the cap`() =
        runBlocking {
            val server = MockWebServer()
            server.enqueue(MockResponse().addHeader("Content-Type", "application/json").setBody(page(listOf("a1", "a2"), "2")))
            server.enqueue(MockResponse().addHeader("Content-Type", "application/json").setBody(page(listOf("a3"), null)))
            server.start()
            try {
                val repo =
                    ImmichRepository(
                        apiFactory = { host -> ImmichClient.api(host) },
                        today = { LocalDate.of(2026, 7, 20) },
                        zone = ZoneOffset.ofHours(2),
                    )
                val assets =
                    repo.loadAssets(
                        ImmichCredentials(server.url("/").toString(), "k"),
                        PhotoFetchConfig(daysEitherSide = 3, maxYearsBack = 1),
                    )
                assertEquals(listOf("a1", "a2", "a3"), assets.map { it.id })
                assertEquals(2, server.requestCount)

                val first = server.takeRequest()
                assertTrue(first.body.readUtf8().contains("\"takenAfter\":\"2026-07-17T00:00:00+02:00\""))
            } finally {
                server.shutdown()
            }
        }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.ImmichRepositoryTest'`
Expected: FAIL — `ImmichRepository` / `ImmichCredentials` unresolved.

- [ ] **Step 3: Write minimal implementation**

`ImmichCredentials.kt`:

```kotlin
package ru.aensidhe.dreamclock.immich

data class ImmichCredentials(
    val host: String,
    val apiKey: String,
)

data class PhotoFetchConfig(
    val daysEitherSide: Int,
    val maxYearsBack: Int,
    val pageSize: Int = 100,
)
```

`ImmichRepository.kt`:

```kotlin
package ru.aensidhe.dreamclock.immich

import java.time.LocalDate
import java.time.ZoneId
import ru.aensidhe.dreamclock.core.photos.SimilarTimeWindows
import ru.aensidhe.dreamclock.core.photos.YearWalk

class ImmichRepository(
    private val apiFactory: ImmichApiFactory,
    private val today: () -> LocalDate,
    private val zone: ZoneId,
) {
    suspend fun loadAssets(
        credentials: ImmichCredentials,
        config: PhotoFetchConfig,
    ): List<SlideAsset> {
        val api = apiFactory.create(credentials.host)
        val all = mutableListOf<SlideAsset>()
        var yearsQueried = 0
        var emptyStreak = 0
        while (true) {
            val year = fetchYear(api, credentials.apiKey, yearsQueried, config)
            all += year
            emptyStreak = if (year.isEmpty()) emptyStreak + 1 else 0
            yearsQueried += 1
            if (!YearWalk.shouldQueryNextYear(yearsQueried, emptyStreak, config.maxYearsBack)) break
        }
        return all
    }

    private suspend fun fetchYear(
        api: ImmichApi,
        apiKey: String,
        yearOffset: Int,
        config: PhotoFetchConfig,
    ): List<SlideAsset> {
        val window = SimilarTimeWindows.windowFor(today(), config.daysEitherSide, yearOffset)
        val bounds = ImmichSearchBoundsFactory.forWindow(window, zone)
        val result = mutableListOf<SlideAsset>()
        var page: Int? = 1
        while (page != null) {
            val response =
                api.searchMetadata(
                    apiKey = apiKey,
                    request =
                        SearchMetadataRequest(
                            takenAfter = bounds.takenAfter,
                            takenBefore = bounds.takenBefore,
                            page = page,
                            size = config.pageSize,
                        ),
                )
            response.assets.items.mapNotNull(AssetMapper::toSlideAsset).forEach(result::add)
            page = response.assets.nextPage?.toIntOrNull()
        }
        return result
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.ImmichRepositoryTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
./gradlew :app:ktlintFormat
git add app/src/main/kotlin/ru/aensidhe/dreamclock/immich/ImmichCredentials.kt app/src/main/kotlin/ru/aensidhe/dreamclock/immich/ImmichRepository.kt app/src/test/kotlin/ru/aensidhe/dreamclock/immich/ImmichRepositoryTest.kt
git commit -m "feat: :robot: add immich repository with paginated year-walk fetch"
```

---

### Task 9: Photo fallback decision

**Files:**
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/PhotoFallback.kt`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/immich/PhotoFallbackTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `object PhotoFallback { fun shouldShowPhotos(enabled: Boolean, hasCredentials: Boolean, assetCount: Int): Boolean }`
  - Semantics: photos show only when the feature is enabled, credentials are present, and at least one asset is in hand; otherwise the deck falls back to the analog clock (the next plan wires this to `SlideDeck`).

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.immich

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class PhotoFallbackTest {
    @Test
    fun `shows photos when enabled with credentials and assets`() {
        assertTrue(PhotoFallback.shouldShowPhotos(enabled = true, hasCredentials = true, assetCount = 5))
    }

    @Test
    fun `hides photos when disabled`() {
        assertFalse(PhotoFallback.shouldShowPhotos(enabled = false, hasCredentials = true, assetCount = 5))
    }

    @Test
    fun `hides photos without credentials`() {
        assertFalse(PhotoFallback.shouldShowPhotos(enabled = true, hasCredentials = false, assetCount = 5))
    }

    @Test
    fun `hides photos when no assets are available`() {
        assertFalse(PhotoFallback.shouldShowPhotos(enabled = true, hasCredentials = true, assetCount = 0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.PhotoFallbackTest'`
Expected: FAIL — `PhotoFallback` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package ru.aensidhe.dreamclock.immich

object PhotoFallback {
    fun shouldShowPhotos(
        enabled: Boolean,
        hasCredentials: Boolean,
        assetCount: Int,
    ): Boolean = enabled && hasCredentials && assetCount > 0
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.PhotoFallbackTest'`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
./gradlew :app:ktlintFormat
git add app/src/main/kotlin/ru/aensidhe/dreamclock/immich/PhotoFallback.kt app/src/test/kotlin/ru/aensidhe/dreamclock/immich/PhotoFallbackTest.kt
git commit -m "feat: :robot: add photo fallback decision"
```

---

### Task 10: Slide driver (planner + gap guarantee at runtime)

**Files:**
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/SlideDriver.kt`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/immich/SlideDriverTest.kt`

**Interfaces:**
- Consumes: `SlideAsset` (Task 6); `SlidePlanner`, `PlannedSlide`, `ClockSlide`, `ClockGapPolicy` (Plan 1 `:core`).
- Produces:
  - `class SlideDriver(assets: Iterator<SlideAsset>, planner: SlidePlanner, maxGap: Duration, lastClockAt: Instant) { fun next(now: Instant): PlannedSlide }`
  - Semantics: at each `next(now)`, if `ClockGapPolicy.shouldForceClock(lastClockAt, now, maxGap)` is true, return `ClockSlide` and set `lastClockAt = now` (the gap guarantee — checked only at slide boundaries, so an in-flight video is never interrupted). Otherwise drain the planner: pull assets and call `planner.offer(asset.toPlannerAsset())` until at least one `PlannedSlide` is buffered, return the first; if it is a `ClockSlide` (from the count cadence), also reset `lastClockAt = now`. In production `assets` is the pool's endless iterator; the test uses a finite one.

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.immich

import java.time.Duration
import java.time.Instant
import ru.aensidhe.dreamclock.core.photos.CaptionSource
import ru.aensidhe.dreamclock.core.photos.ClockSlide
import ru.aensidhe.dreamclock.core.photos.Orientation
import ru.aensidhe.dreamclock.core.photos.SinglePhotoSlide
import ru.aensidhe.dreamclock.core.photos.SlideMediaKind
import ru.aensidhe.dreamclock.core.photos.SlidePlanner
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class SlideDriverTest {
    private fun landscape(id: String) =
        SlideAsset(id, SlideMediaKind.PHOTO, Orientation.LANDSCAPE, CaptionSource(null, null, null))

    private fun idOf(slide: Any): String = (slide as SinglePhotoSlide).asset.id

    @Test
    fun `forces a clock slide once the gap since the last clock is reached`() {
        val base = Instant.parse("2026-07-20T10:00:00Z")
        val driver =
            SlideDriver(
                assets = listOf(landscape("l1"), landscape("l2"), landscape("l3")).iterator(),
                planner = SlidePlanner(analogCadence = 100),
                maxGap = Duration.ofMinutes(5),
                lastClockAt = base,
            )
        assertEquals("l1", idOf(driver.next(base)))
        assertEquals("l2", idOf(driver.next(base.plus(Duration.ofMinutes(1)))))
        assertTrue(driver.next(base.plus(Duration.ofMinutes(6))) is ClockSlide)
        // Clock just fired at +6m, so the gap resets and the next slide is content again.
        assertEquals("l3", idOf(driver.next(base.plus(Duration.ofMinutes(7)))))
    }

    @Test
    fun `count cadence clock also resets the gap timer`() {
        val base = Instant.parse("2026-07-20T10:00:00Z")
        val driver =
            SlideDriver(
                assets = listOf(landscape("l1"), landscape("l2"), landscape("l3")).iterator(),
                planner = SlidePlanner(analogCadence = 1),
                maxGap = Duration.ofMinutes(5),
                lastClockAt = base,
            )
        assertEquals("l1", idOf(driver.next(base)))
        // analogCadence = 1 means a clock is queued after each content slide.
        assertTrue(driver.next(base.plus(Duration.ofMinutes(1))) is ClockSlide)
        assertEquals("l2", idOf(driver.next(base.plus(Duration.ofMinutes(2)))))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.SlideDriverTest'`
Expected: FAIL — `SlideDriver` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package ru.aensidhe.dreamclock.immich

import java.time.Duration
import java.time.Instant
import ru.aensidhe.dreamclock.core.photos.ClockGapPolicy
import ru.aensidhe.dreamclock.core.photos.ClockSlide
import ru.aensidhe.dreamclock.core.photos.PlannedSlide
import ru.aensidhe.dreamclock.core.photos.SlidePlanner

class SlideDriver(
    private val assets: Iterator<SlideAsset>,
    private val planner: SlidePlanner,
    private val maxGap: Duration,
    lastClockAt: Instant,
) {
    private var lastClockAt: Instant = lastClockAt
    private val buffer = ArrayDeque<PlannedSlide>()

    fun next(now: Instant): PlannedSlide {
        if (ClockGapPolicy.shouldForceClock(lastClockAt, now, maxGap)) {
            lastClockAt = now
            return ClockSlide
        }
        while (buffer.isEmpty()) {
            buffer.addAll(planner.offer(assets.next().toPlannerAsset()))
        }
        val slide = buffer.removeFirst()
        if (slide is ClockSlide) lastClockAt = now
        return slide
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.SlideDriverTest'`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
./gradlew :app:ktlintFormat
git add app/src/main/kotlin/ru/aensidhe/dreamclock/immich/SlideDriver.kt app/src/test/kotlin/ru/aensidhe/dreamclock/immich/SlideDriverTest.kt
git commit -m "feat: :robot: add slide driver tying planner to the clock-gap guarantee"
```

---

### Task 11: Full gate

**Files:** none (verification only).

- [ ] **Step 1: Run the full gate**

Run: `./gradlew :app:ktlintFormat :app:ktlintCheck :app:detekt :core:test :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all `immich` tests pass alongside the existing `:app` and `:core` suites.

- [ ] **Step 2: Commit any formatting deltas (only if the working tree is dirty)**

```bash
git add -A app/
git commit -m "style: :robot: apply ktlint formatting to immich package"
```

---

## Subsequent plans (roadmap)

This is phase 2 of 6. Plans 4–6 are ordered so the usable, testable milestone (settings plus manual credentials) comes next, then pairing polish, then video:

3. Photo rendering and deck integration (`:app`): Coil 3 (`preview` + `thumbnail`) `PhotoSlide` / `PairedPhotoSlide` composables with bottom-right captions via `PhotoCaption`, the `SlideDeck` rewrite that renders the `SlideDriver` output with per-slide durations (M2 photo, M analog) and crossfade + Coil preloading, the caption-vs-clock overlay suppression rule, Coil disk caching, and the `DreamContent` wiring behind a `CredentialsStore` interface (a no-credentials default keeps the clock fallback until Plan 4). On-device validation of the photo path.
4. Settings surface + manual credentials (`:app`): the `SettingsScreen` Immich section (enable toggle, host/key fields, numeric steppers, the live health status line via a repository `probe`, Ru/En labels via `SettingsLabels`) plus a Keystore-backed `CredentialsStore` implementation replacing `BuildConfigCredentials`, so host/key entered in the UI are stored securely (key in Keystore, host in proto). This is the milestone that makes the photo flow usable and on-device testable end to end; add periodic date-rollover refetch here.
5. Credentials pairing polish (`:app`): local-network QR pairing on top of Plan 4's manual entry — the Ktor `PairingServer`, `PairingCrypto` (AES-GCM), `QrCode` (ZXing), the served phone page (WebCrypto, two modes), and the email/password key-mint path.
6. Video and audio (`:app`): add `video_audio_mode` to the proto, broaden the fetch/mapper to videos, Media3 `VideoSlide` full-clip playback, and the schedule-aware three-way audio mode.
