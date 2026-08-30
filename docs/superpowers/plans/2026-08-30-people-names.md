# People Names Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Caption each photo slide with a third line naming the people Immich recognised in it, joined as a natural-language list per locale, with a settings toggle to hide it.

**Architecture:** People ride along with each asset via `withPeople = true` on the existing bulk `POST api/search/metadata`; `AssetMapper` reduces them to an ordered list of names in `CaptionSource.people`; a pure `PeopleList` joiner in `:core` produces the third `CaptionLines.people` line; `SlideResolver` honours the toggle and `CaptionBlock` draws the line. No new requests, no new async steps.

**Tech Stack:** Kotlin 2.4.0, Jetpack Compose (androidx.tv.material3), kotlinx.serialization + Retrofit, Proto DataStore, JUnit 5 + kotlin.test, ktlint + detekt via `./gradlew verify`.

**Spec:** `docs/superpowers/specs/2026-08-30-people-names-design.md`

## Global Constraints

- Two Gradle modules: pure-Kotlin `:core` (no Android imports) and Android `:app`. Pure logic goes in `:core`.
- Canonical gate before every commit: `./gradlew verify` (ktlint, detekt, all unit tests, assemble). If ktlint fails on formatting, run `./gradlew ktlintFormat` and re-run `verify`.
- Focused test runs: `./gradlew :core:test --tests '<fully.qualified.TestClass>'` and `./gradlew :app:testDebugUnitTest --tests '<fully.qualified.TestClass>'`.
- Commits: Conventional Commits, `:robot:` after the type, no Co-Authored-By trailer, and a second `-m` with `Claude-Session: https://claude.ai/code/session_01GWwpU4kruwVqXvrYG4B1bp`. Stage files by name.
- Work on branch `people-names` (already exists, contains the spec). Never commit to `main`.
- Code navigation and edits: Serena symbolic tools (`get_symbols_overview`, `find_symbol`, `replace_symbol_body`, `insert_after_symbol`, `replace_content`) for Kotlin; built-in Read/Edit for XML, proto, and Markdown.
- Shell: plain single-purpose commands; no heredocs, no `$(...)`, no multi-line strings. Scratch files go in repo-local `tmp/`, never `/tmp`.
- Markdown prose: no bold or italic for emphasis.
- Do not touch `.serena/project.yml` (unrelated local drift) and do not stage it.
- Do not use the Fable model for anything.
- Immich source for reference lives at `~/sources/github.com/immich-app/immich-v2.7.5` (detached worktree at the family server's tag); permissions are already verified in the spec.
- The TV is not reachable from this machine: no adb, no emulator. UI tasks end at a green `verify`; on-device validation is the user's manual step after the plan.

---

## File map

Created:

- `core/src/main/kotlin/ru/aensidhe/dreamclock/core/photos/PeopleList.kt` — pure joiner: `List<String>` + locale → one line or `null`.
- `core/src/test/kotlin/ru/aensidhe/dreamclock/core/photos/PeopleListTest.kt`

Modified:

- `core/src/main/kotlin/ru/aensidhe/dreamclock/core/photos/PhotoCaption.kt` — `CaptionSource.people`, `CaptionLines.people`, `format` fills the third line.
- `core/src/test/kotlin/ru/aensidhe/dreamclock/core/photos/PhotoCaptionTest.kt`
- `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/ImmichModels.kt` — `ImmichFace`, `ImmichPerson`, `ImmichAsset.people`, `SearchMetadataRequest.withPeople`.
- `app/src/test/kotlin/ru/aensidhe/dreamclock/immich/ImmichModelsTest.kt`
- `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/AssetMapper.kt` — derive ordered names.
- `app/src/test/kotlin/ru/aensidhe/dreamclock/immich/AssetMapperTest.kt`
- `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/SlideResolver.kt` — `showPeople` switch.
- `app/src/test/kotlin/ru/aensidhe/dreamclock/immich/SlideResolverTest.kt`
- `app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamContent.kt:186` — pass the toggle.
- `app/src/main/proto/settings.proto` — `hide_people_names = 17`.
- `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsScreen.kt` — toggle row in `ImmichSection`.
- `app/src/main/res/values/strings_settings.xml`, `app/src/main/res/values-ru/strings_settings.xml` — one new string each.
- `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/PhotoSlide.kt` — third line in `CaptionBlock`.
- `CLAUDE.md`, `README.md` — feature 3 wording and status.

---

### Task 1: `PeopleList.join` in `:core`

**Files:**
- Create: `core/src/main/kotlin/ru/aensidhe/dreamclock/core/photos/PeopleList.kt`
- Test: `core/src/test/kotlin/ru/aensidhe/dreamclock/core/photos/PeopleListTest.kt`

**Interfaces:**
- Consumes: `ru.aensidhe.dreamclock.core.time.ClockLocale` — `enum class ClockLocale { RU, EN }` (exists).
- Produces: `object PeopleList { fun join(names: List<String>, locale: ClockLocale): String? }`. Task 2 calls it.

Rules (from the spec): trim names, drop empty ones; empty list → `null`; one → the name; two → `A и B` / `A and B`; three or more → RU `А, Б и В`, EN `A, B, and C` (Oxford comma in English only).

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.core.photos

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import ru.aensidhe.dreamclock.core.time.ClockLocale

class PeopleListTest {
    @Test
    fun `empty list yields null`() {
        assertNull(PeopleList.join(emptyList(), ClockLocale.EN))
        assertNull(PeopleList.join(emptyList(), ClockLocale.RU))
    }

    @Test
    fun `blank names are dropped and the rest trimmed`() {
        assertNull(PeopleList.join(listOf("", "   "), ClockLocale.EN))
        assertEquals("Anna", PeopleList.join(listOf("  Anna "), ClockLocale.EN))
    }

    @Test
    fun `one name is returned as is`() {
        assertEquals("Anna", PeopleList.join(listOf("Anna"), ClockLocale.EN))
        assertEquals("Аня", PeopleList.join(listOf("Аня"), ClockLocale.RU))
    }

    @Test
    fun `two names use the conjunction without a comma`() {
        assertEquals("Anna and Boris", PeopleList.join(listOf("Anna", "Boris"), ClockLocale.EN))
        assertEquals("Аня и Боря", PeopleList.join(listOf("Аня", "Боря"), ClockLocale.RU))
    }

    @Test
    fun `three names in english use the oxford comma`() {
        assertEquals(
            "Anna, Boris, and Vasya",
            PeopleList.join(listOf("Anna", "Boris", "Vasya"), ClockLocale.EN),
        )
    }

    @Test
    fun `three names in russian have no comma before the conjunction`() {
        assertEquals(
            "Аня, Боря и Вася",
            PeopleList.join(listOf("Аня", "Боря", "Вася"), ClockLocale.RU),
        )
    }

    @Test
    fun `four names follow the same pattern`() {
        assertEquals("A, B, C, and D", PeopleList.join(listOf("A", "B", "C", "D"), ClockLocale.EN))
        assertEquals("А, Б, В и Г", PeopleList.join(listOf("А", "Б", "В", "Г"), ClockLocale.RU))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :core:test --tests 'ru.aensidhe.dreamclock.core.photos.PeopleListTest'`
Expected: compilation failure, `Unresolved reference: PeopleList`.

- [ ] **Step 3: Write the implementation**

`core/src/main/kotlin/ru/aensidhe/dreamclock/core/photos/PeopleList.kt`:

```kotlin
package ru.aensidhe.dreamclock.core.photos

import ru.aensidhe.dreamclock.core.time.ClockLocale

object PeopleList {
    fun join(
        names: List<String>,
        locale: ClockLocale,
    ): String? {
        val clean = names.map { it.trim() }.filter { it.isNotEmpty() }
        return when (clean.size) {
            0 -> null
            1 -> clean[0]
            2 -> "${clean[0]} ${conjunction(locale)} ${clean[1]}"
            else -> clean.dropLast(1).joinToString(", ") + lastSeparator(locale) + clean.last()
        }
    }

    private fun conjunction(locale: ClockLocale): String =
        when (locale) {
            ClockLocale.RU -> "и"
            ClockLocale.EN -> "and"
        }

    // English takes the Oxford comma; Russian never puts a comma before a single «и».
    private fun lastSeparator(locale: ClockLocale): String =
        when (locale) {
            ClockLocale.RU -> " и "
            ClockLocale.EN -> ", and "
        }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :core:test --tests 'ru.aensidhe.dreamclock.core.photos.PeopleListTest'`
Expected: BUILD SUCCESSFUL, 7 tests passed.

- [ ] **Step 5: Gate and commit**

Run: `./gradlew verify` — expected BUILD SUCCESSFUL.

```bash
git add core/src/main/kotlin/ru/aensidhe/dreamclock/core/photos/PeopleList.kt core/src/test/kotlin/ru/aensidhe/dreamclock/core/photos/PeopleListTest.kt
git commit -m "feat(core): :robot: join people names as a locale-aware list" -m "Claude-Session: https://claude.ai/code/session_01GWwpU4kruwVqXvrYG4B1bp"
```

---

### Task 2: Third caption line in `PhotoCaption`

**Files:**
- Modify: `core/src/main/kotlin/ru/aensidhe/dreamclock/core/photos/PhotoCaption.kt`
- Test: `core/src/test/kotlin/ru/aensidhe/dreamclock/core/photos/PhotoCaptionTest.kt`

**Interfaces:**
- Consumes: `PeopleList.join(names: List<String>, locale: ClockLocale): String?` from Task 1.
- Produces:
  - `data class CaptionSource(val takenAt: LocalDateTime?, val city: String?, val country: String?, val people: List<String> = emptyList())`
  - `data class CaptionLines(val dateTime: String?, val location: String?, val people: String? = null)`
  - `PhotoCaption.format(source, locale): CaptionLines?` returns `null` only when all three lines are `null`.
  Tasks 4, 6, and 7 depend on these exact names.

Current bodies (0-based Serena lines 7–16 and 25–43 of `PhotoCaption.kt`):

```kotlin
data class CaptionSource(
    val takenAt: LocalDateTime?,
    val city: String?,
    val country: String?,
)

data class CaptionLines(
    val dateTime: String?,
    val location: String?,
)
```

- [ ] **Step 1: Add failing tests**

Append inside `class PhotoCaptionTest` (keep the existing tests unchanged; they must keep passing thanks to the default parameters):

```kotlin
    @Test
    fun `people line is joined per locale`() {
        val en = PhotoCaption.format(CaptionSource(takenAt, null, null, listOf("Anna", "Boris")), ClockLocale.EN)!!
        assertEquals("Anna and Boris", en.people)
        val ru = PhotoCaption.format(CaptionSource(takenAt, null, null, listOf("Аня", "Боря", "Вася")), ClockLocale.RU)!!
        assertEquals("Аня, Боря и Вася", ru.people)
    }

    @Test
    fun `people only caption is not null`() {
        val c = PhotoCaption.format(CaptionSource(null, null, null, listOf("Anna")), ClockLocale.EN)!!
        assertNull(c.dateTime)
        assertNull(c.location)
        assertEquals("Anna", c.people)
    }

    @Test
    fun `all three lines present`() {
        val c = PhotoCaption.format(CaptionSource(takenAt, "Berlin", "Germany", listOf("Anna")), ClockLocale.EN)!!
        assertEquals(CaptionLines("19 July 2026 | 14:32", "Berlin, Germany", "Anna"), c)
    }

    @Test
    fun `no people leaves the line null`() {
        val c = PhotoCaption.format(CaptionSource(takenAt, "Berlin", null), ClockLocale.EN)!!
        assertNull(c.people)
    }

    @Test
    fun `blank people and nothing else yields null`() {
        assertNull(PhotoCaption.format(CaptionSource(null, null, null, listOf(" ", "")), ClockLocale.EN))
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :core:test --tests 'ru.aensidhe.dreamclock.core.photos.PhotoCaptionTest'`
Expected: compilation failure (too many arguments for `CaptionSource`, unresolved `people`).

- [ ] **Step 3: Implement**

Replace the two data classes and `format`:

```kotlin
data class CaptionSource(
    val takenAt: LocalDateTime?,
    val city: String?,
    val country: String?,
    val people: List<String> = emptyList(),
)

data class CaptionLines(
    val dateTime: String?,
    val location: String?,
    val people: String? = null,
)
```

```kotlin
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
                .joinToString(", ")
                .ifEmpty { null }
        val people = PeopleList.join(source.people, locale)
        val empty = dateTime == null && location == null && people == null
        return if (empty) null else CaptionLines(dateTime, location, people)
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :core:test --tests 'ru.aensidhe.dreamclock.core.photos.PhotoCaptionTest'`
Expected: BUILD SUCCESSFUL, 12 tests passed.

- [ ] **Step 5: Gate and commit**

Run: `./gradlew verify` — expected BUILD SUCCESSFUL (the `:app` tests that build `CaptionSource`/`CaptionLines` positionally still compile because the new parameters have defaults).

```bash
git add core/src/main/kotlin/ru/aensidhe/dreamclock/core/photos/PhotoCaption.kt core/src/test/kotlin/ru/aensidhe/dreamclock/core/photos/PhotoCaptionTest.kt
git commit -m "feat(core): :robot: add a people line to the photo caption" -m "Claude-Session: https://claude.ai/code/session_01GWwpU4kruwVqXvrYG4B1bp"
```

---

### Task 3: Immich people models and `withPeople`

**Files:**
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/ImmichModels.kt`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/immich/ImmichModelsTest.kt`

**Interfaces:**
- Consumes: `immichJson` (exists, `ignoreUnknownKeys = true`), `@Serializable` data classes in the same file.
- Produces (Task 4 depends on the exact names):
  - `@Serializable data class ImmichFace(val boundingBoxX1: Int? = null)`
  - `@Serializable data class ImmichPerson(val id: String, val name: String? = null, val isHidden: Boolean = false, val faces: List<ImmichFace> = emptyList())`
  - `ImmichAsset` gains `val people: List<ImmichPerson> = emptyList()`
  - `SearchMetadataRequest` gains `val withPeople: Boolean = true`

Current `ImmichAsset` and `SearchMetadataRequest` bodies:

```kotlin
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
data class ImmichAsset(
    val id: String,
    val type: String,
    val localDateTime: String? = null,
    val exifInfo: ExifInfo? = null,
)
```

- [ ] **Step 1: Add failing tests**

Append inside `class ImmichModelsTest`:

```kotlin
    @Test
    fun `decodes people with faces, hidden flag, empty and null names`() {
        val fixture =
            """
            {
              "id": "a1",
              "type": "IMAGE",
              "people": [
                {
                  "id": "p1",
                  "name": "Anna",
                  "birthDate": null,
                  "thumbnailPath": "/x",
                  "isHidden": false,
                  "faces": [
                    { "id": "f1", "boundingBoxX1": 120, "boundingBoxY1": 10, "boundingBoxX2": 200, "boundingBoxY2": 90, "imageWidth": 4000, "imageHeight": 3000, "sourceType": "machine-learning" }
                  ]
                },
                { "id": "p2", "name": "", "isHidden": false, "faces": [] },
                { "id": "p3", "name": null, "isHidden": false, "faces": [] },
                { "id": "p4", "name": "Ghost", "isHidden": true, "faces": [] },
                { "id": "p5", "name": "Boris", "isHidden": false }
              ]
            }
            """.trimIndent()
        val asset = immichJson.decodeFromString<ImmichAsset>(fixture)
        assertEquals(5, asset.people.size)
        assertEquals("Anna", asset.people[0].name)
        assertEquals(120, asset.people[0].faces.single().boundingBoxX1)
        assertEquals("", asset.people[1].name)
        assertNull(asset.people[2].name)
        assertTrue(asset.people[3].isHidden)
        assertTrue(asset.people[4].faces.isEmpty())
    }

    @Test
    fun `an asset without a people key decodes to an empty list`() {
        val asset = immichJson.decodeFromString<ImmichAsset>("""{ "id": "a1", "type": "IMAGE" }""")
        assertTrue(asset.people.isEmpty())
    }

    @Test
    fun `search request asks for people`() {
        val encoded = immichJson.encodeToString(SearchMetadataRequest(takenAfter = "A", takenBefore = "B"))
        assertTrue(encoded.contains("\"withPeople\":true"))
    }
```

Add `import kotlin.test.assertNull` to the test file's imports.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.ImmichModelsTest'`
Expected: compilation failure, `Unresolved reference: people`.

- [ ] **Step 3: Implement**

Add `withPeople` to `SearchMetadataRequest`:

```kotlin
@Serializable
data class SearchMetadataRequest(
    val takenAfter: String,
    val takenBefore: String,
    val type: String = "IMAGE",
    val withExif: Boolean = true,
    val withPeople: Boolean = true,
    val page: Int = 1,
    val size: Int = 100,
)
```

Replace `ImmichAsset` and insert the two new classes right after it (use `replace_symbol_body` on `ImmichAsset`, then `insert_after_symbol` on `ImmichAsset`):

```kotlin
@Serializable
data class ImmichAsset(
    val id: String,
    val type: String,
    val localDateTime: String? = null,
    val exifInfo: ExifInfo? = null,
    val people: List<ImmichPerson> = emptyList(),
)

@Serializable
data class ImmichPerson(
    val id: String,
    // Nullable on purpose: Immich stores "" for an unnamed face today, but a null must not
    // fail decoding of a whole page.
    val name: String? = null,
    val isHidden: Boolean = false,
    val faces: List<ImmichFace> = emptyList(),
)

@Serializable
data class ImmichFace(
    val boundingBoxX1: Int? = null,
)
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.ImmichModelsTest'`
Expected: BUILD SUCCESSFUL, 7 tests passed.

- [ ] **Step 5: Gate and commit**

Run: `./gradlew verify` — expected BUILD SUCCESSFUL.

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/immich/ImmichModels.kt app/src/test/kotlin/ru/aensidhe/dreamclock/immich/ImmichModelsTest.kt
git commit -m "feat(immich): :robot: request and decode people on search results" -m "Claude-Session: https://claude.ai/code/session_01GWwpU4kruwVqXvrYG4B1bp"
```

---

### Task 4: `AssetMapper` derives ordered people names

**Files:**
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/AssetMapper.kt`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/immich/AssetMapperTest.kt`

**Interfaces:**
- Consumes: `ImmichPerson`, `ImmichFace` (Task 3); `CaptionSource.people: List<String>` (Task 2).
- Produces: `SlideAsset.caption.people` populated. Rules: drop hidden people; treat `null`, `""`, and whitespace-only names identically as "no name" and drop them; sort by the smallest `boundingBoxX1` across the person's faces, people without any coordinate last in arrival order; trim; de-duplicate keeping the first occurrence.

Current `AssetMapper.toSlideAsset`:

```kotlin
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
```

- [ ] **Step 1: Add failing tests**

Append inside `class AssetMapperTest`:

```kotlin
    private fun person(
        id: String,
        name: String?,
        vararg x: Int,
        hidden: Boolean = false,
    ) = ImmichPerson(id = id, name = name, isHidden = hidden, faces = x.map { ImmichFace(boundingBoxX1 = it) })

    private fun namesOf(vararg people: ImmichPerson): List<String> =
        AssetMapper.toSlideAsset(ImmichAsset(id = "a", type = "IMAGE", people = people.toList()))!!.caption.people

    @Test
    fun `hidden and unnamed people are dropped`() {
        assertEquals(
            listOf("Anna"),
            namesOf(
                person("p1", "Anna", 10),
                person("p2", "", 20),
                person("p3", null, 30),
                person("p4", "   ", 40),
                person("p5", "Ghost", 50, hidden = true),
            ),
        )
    }

    @Test
    fun `names are ordered left to right by the leftmost face`() {
        assertEquals(
            listOf("Left", "Middle", "Right"),
            namesOf(person("p1", "Right", 900), person("p2", "Left", 100, 950), person("p3", "Middle", 500)),
        )
    }

    @Test
    fun `people without coordinates come last in arrival order`() {
        assertEquals(
            listOf("Anna", "NoBoxA", "NoBoxB"),
            namesOf(person("p1", "NoBoxA"), person("p2", "NoBoxB"), person("p3", "Anna", 5)),
        )
    }

    @Test
    fun `names are trimmed and de-duplicated keeping the first`() {
        assertEquals(
            listOf("Anna", "Boris"),
            namesOf(person("p1", " Anna ", 10), person("p2", "Boris", 20), person("p3", "Anna", 30)),
        )
    }

    @Test
    fun `no people yields an empty list`() {
        assertEquals(emptyList<String>(), namesOf())
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.AssetMapperTest'`
Expected: the four new behaviour tests FAIL (empty list returned), `no people yields an empty list` passes.

- [ ] **Step 3: Implement**

Replace `toSlideAsset` and add `peopleNames` after it inside `object AssetMapper`:

```kotlin
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
                    people = peopleNames(asset.people),
                ),
        )
    }

    // sortedBy is stable, so people without a face coordinate keep their arrival order at the end.
    private fun peopleNames(people: List<ImmichPerson>): List<String> =
        people
            .filterNot { it.isHidden }
            .sortedBy { person -> person.faces.mapNotNull { it.boundingBoxX1 }.minOrNull() ?: Int.MAX_VALUE }
            .mapNotNull { person -> person.name?.trim()?.takeIf { it.isNotEmpty() } }
            .distinct()
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.AssetMapperTest'`
Expected: BUILD SUCCESSFUL, 10 tests passed.

- [ ] **Step 5: Gate and commit**

Run: `./gradlew verify` — expected BUILD SUCCESSFUL.

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/immich/AssetMapper.kt app/src/test/kotlin/ru/aensidhe/dreamclock/immich/AssetMapperTest.kt
git commit -m "feat(immich): :robot: map recognised people to ordered caption names" -m "Claude-Session: https://claude.ai/code/session_01GWwpU4kruwVqXvrYG4B1bp"
```

---

### Task 5: `SlideResolver` honours a show-people switch

**Files:**
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/SlideResolver.kt`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/immich/SlideResolverTest.kt`

**Interfaces:**
- Consumes: `CaptionSource.people`, `CaptionLines.people` (Task 2).
- Produces: `class SlideResolver(host: String, captions: Map<String, CaptionSource>, locale: ClockLocale, showPeople: Boolean = true)`. Task 6 passes the fourth argument from settings.

Current class:

```kotlin
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

- [ ] **Step 1: Add failing tests**

In `SlideResolverTest`, add a third caption entry to the `captions` map and two tests:

```kotlin
    private val captions =
        mapOf(
            "p1" to CaptionSource(LocalDateTime.of(2026, 7, 19, 14, 32), "Berlin", "Germany"),
            "p2" to CaptionSource(null, null, null),
            "p3" to CaptionSource(null, null, null, listOf("Anna", "Boris")),
        )
```

```kotlin
    @Test
    fun `people line is shown by default`() {
        val slide = resolver.resolve(SinglePhotoSlide(photo("p3"))) as RenderPhoto
        assertEquals("Anna and Boris", slide.caption?.people)
    }

    @Test
    fun `people line is dropped when showPeople is off`() {
        val quiet = SlideResolver("https://immich.example", captions, ClockLocale.EN, showPeople = false)
        val slide = quiet.resolve(SinglePhotoSlide(photo("p3"))) as RenderPhoto
        assertNull(slide.caption)
        val withDate = quiet.resolve(SinglePhotoSlide(photo("p1"))) as RenderPhoto
        assertEquals("Berlin, Germany", withDate.caption?.location)
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.SlideResolverTest'`
Expected: compilation failure, `Cannot find a parameter with this name: showPeople`.

- [ ] **Step 3: Implement**

```kotlin
class SlideResolver(
    private val host: String,
    private val captions: Map<String, CaptionSource>,
    private val locale: ClockLocale,
    private val showPeople: Boolean = true,
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
            caption = captions[asset.id]?.let { PhotoCaption.format(visible(it), locale) },
        )

    private fun visible(source: CaptionSource): CaptionSource =
        if (showPeople) source else source.copy(people = emptyList())
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.SlideResolverTest'`
Expected: BUILD SUCCESSFUL, 7 tests passed.

- [ ] **Step 5: Gate and commit**

Run: `./gradlew verify` — expected BUILD SUCCESSFUL.

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/immich/SlideResolver.kt app/src/test/kotlin/ru/aensidhe/dreamclock/immich/SlideResolverTest.kt
git commit -m "feat(immich): :robot: let the slide resolver hide people names" -m "Claude-Session: https://claude.ai/code/session_01GWwpU4kruwVqXvrYG4B1bp"
```

---

### Task 6: Setting, toggle row, strings, and wiring

**Files:**
- Modify: `app/src/main/proto/settings.proto`
- Modify: `app/src/main/res/values/strings_settings.xml`
- Modify: `app/src/main/res/values-ru/strings_settings.xml`
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsScreen.kt` (`ImmichSection`)
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamContent.kt:186`

**Interfaces:**
- Consumes: `SlideResolver(..., showPeople: Boolean)` (Task 5); `ToggleRow(focusRequester, label, checked, downFocus, onToggle)` and `StepperRow` in `SettingsScreen.kt` (exist).
- Produces: `Settings.hidePeopleNames` (generated getter `getHidePeopleNames()`, Kotlin property `hidePeopleNames`, builder `setHidePeopleNames`). The field is inverted so that installs predating it show names without a migration; do not add it to `SettingsSerializer.defaultValue`.

There is no unit test for Compose UI in this project; this task's test is a green `./gradlew verify` (proto generation, compile, ktlint, detekt). No test checks that a string exists in both locales; verify both `strings_settings.xml` files by eye.

- [ ] **Step 1: Add the proto field**

In `app/src/main/proto/settings.proto`, after `AddressFamilyProto pairing_address_family = 16;` add:

```proto
  bool hide_people_names = 17;
```

- [ ] **Step 2: Add the strings**

`app/src/main/res/values/strings_settings.xml`, after the `settings_analog_slide_seconds` line:

```xml
    <string name="settings_show_people_names">Show people names</string>
```

`app/src/main/res/values-ru/strings_settings.xml`, same position:

```xml
    <string name="settings_show_people_names">Показывать имена людей</string>
```

- [ ] **Step 3: Add the toggle row after the steppers**

In `ImmichSection` (`SettingsScreen.kt`), the stepper loop currently ends with:

```kotlin
    steppers.forEachIndexed { index, spec ->
        StepperRow(
            label = stringResource(spec.labelRes),
            value = spec.value,
            min = spec.min,
            max = spec.max,
            step = 1,
            downFocus = if (index == steppers.lastIndex) lastStepperDownFocus else null,
            upFocus = if (index == 0) pairButton else null,
        ) { newValue ->
            scope.launch { repository.update { spec.setter(it.toBuilder(), newValue).build() } }
        }
    }
}
```

Change it to (the toggle takes over the `downFocus` hand-off so D-pad down from the last stepper lands on the toggle and down from the toggle lands where it used to):

```kotlin
    steppers.forEachIndexed { index, spec ->
        StepperRow(
            label = stringResource(spec.labelRes),
            value = spec.value,
            min = spec.min,
            max = spec.max,
            step = 1,
            upFocus = if (index == 0) pairButton else null,
        ) { newValue ->
            scope.launch { repository.update { spec.setter(it.toBuilder(), newValue).build() } }
        }
    }
    ToggleRow(
        null,
        stringResource(R.string.settings_show_people_names),
        !settings.hidePeopleNames,
        downFocus = lastStepperDownFocus,
    ) { on ->
        scope.launch { repository.update { it.toBuilder().setHidePeopleNames(!on).build() } }
    }
}
```

Use `replace_content` in regex mode on the `downFocus = if (index == steppers.lastIndex) lastStepperDownFocus else null,\n` line (delete it) and insert the `ToggleRow` block after the loop's closing brace. Check with `find_symbol` on `StepperRow` in `SettingsRows.kt` that `downFocus` has a default of `null` before removing the argument; it does (`downFocus: FocusRequester? = null`).

- [ ] **Step 4: Wire the toggle into the resolver**

`DreamContent.kt` line 186 currently:

```kotlin
    val resolver = SlideResolver(credentials.host, load.assets.associate { it.id to it.caption }, locale)
```

Change to:

```kotlin
    val resolver =
        SlideResolver(
            host = credentials.host,
            captions = load.assets.associate { it.id to it.caption },
            locale = locale,
            showPeople = !settings.hidePeopleNames,
        )
```

- [ ] **Step 5: Gate**

Run: `./gradlew verify`
Expected: BUILD SUCCESSFUL. If ktlint complains about formatting, run `./gradlew ktlintFormat` and re-run.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/proto/settings.proto app/src/main/res/values/strings_settings.xml app/src/main/res/values-ru/strings_settings.xml app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsScreen.kt app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamContent.kt
git commit -m "feat(settings): :robot: add a toggle for people names on photo slides" -m "Claude-Session: https://claude.ai/code/session_01GWwpU4kruwVqXvrYG4B1bp"
```

---

### Task 7: Draw the people line

**Files:**
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/PhotoSlide.kt` (`CaptionBlock`)

**Interfaces:**
- Consumes: `CaptionLines.people: String?` (Task 2).
- Produces: nothing new; the third line renders at 24 sp under date and location.

Current `CaptionBlock`:

```kotlin
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

- [ ] **Step 1: Implement**

Replace the body with:

```kotlin
@Composable
private fun CaptionBlock(
    lines: CaptionLines,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        lines.dateTime?.let { Text(it, color = Color.White, fontSize = 20.sp) }
        lines.location?.let { Text(it, color = Color.White, fontSize = 20.sp) }
        lines.people?.let { Text(it, color = Color.White, fontSize = 24.sp) }
    }
}
```

No new imports are needed (`Text`, `Color`, `sp` are already imported).

- [ ] **Step 2: Gate**

Run: `./gradlew verify`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/ui/PhotoSlide.kt
git commit -m "feat(ui): :robot: draw the people line under the photo caption" -m "Claude-Session: https://claude.ai/code/session_01GWwpU4kruwVqXvrYG4B1bp"
```

---

### Task 8: Project docs

**Files:**
- Modify: `CLAUDE.md` (feature 3 entry and "Current status")
- Modify: `README.md` (feature 3 entry and "Status")

The two files mirror each other; both change in one commit. Status stays honest: built, awaiting on-device validation. The user flips it to "validated" after the APK round.

- [ ] **Step 1: CLAUDE.md**

Replace lines 29–30:

```markdown
3. People names on photo slides — caption the faces Immich recognised. Not
   designed yet; warrants its own brainstorm before any plan
```

with:

```markdown
3. People names on photo slides (built) — a third caption line naming the
   people Immich recognised, joined as a natural list per locale, with a
   settings toggle
```

In "Current status", replace `Features 3–6 are not yet built.` with:

```markdown
Feature 3 (people names) is built and merged, awaiting on-device validation.
Features 4–6 are not yet built.
```

- [ ] **Step 2: README.md**

Replace lines 27–28:

```markdown
3. People names (planned) — photo slides captioned with the names of the people
   Immich recognised in them.
```

with:

```markdown
3. People names (built) — photo slides captioned with the names of the people
   Immich recognised in them, joined the way you would say them aloud.
```

In "Status", replace `Features 3–6 are planned.` with:

```markdown
Feature 3's people captions are built and awaiting on-device validation.
Features 4–6 are planned.
```

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md README.md
git commit -m "docs: :robot: record people names as built, pending on-device validation" -m "Claude-Session: https://claude.ai/code/session_01GWwpU4kruwVqXvrYG4B1bp"
```

---

## After the plan

1. `git rebase main` on `people-names`, push, open a PR so CI runs.
2. `./gradlew :app:assembleDebug`, carry `app/build/outputs/apk/debug/app-debug.apk` to the TV with LocalSend.
3. On-device checks: a photo with a recognised, named face shows the name at the bottom right; the settings toggle hides it after the deck reloads; a photo without faces looks unchanged; a photo with many recognised people, single and paired, shows at most two rows of names; the toggle sits after the last stepper and D-pad down from it still reaches the bottom buttons.
4. Once green and validated: `git switch main && git merge --ff-only people-names && git push`, then flip the two status lines from "awaiting on-device validation" to "validated on-device".
