# Feature 1 — Kid-Friendly Dual Clock Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the always-on kid-friendly clock: a locale-format digital time plus a spoken colloquial form, coloured by three time-of-day states, running as an Android TV screensaver with a settings screen.

**Architecture:** A pure-Kotlin `:core` module (JDK only, no Android) holds the two logic units — `ColloquialTimeFormatter` (RU/EN) and `ScheduleEngine` — under exhaustive unit tests. An Android `:app` module hosts a `DreamService` that shows a Compose slide deck (analog-clock slide for now) with an always-on `ClockOverlay`, plus a `SettingsActivity` backed by Proto DataStore.

**Tech Stack:** Kotlin, Jetpack Compose, Gradle (Kotlin DSL + version catalog), Proto DataStore, JUnit + kotlin.test, ktlint, detekt, GitHub Actions.

## Global Constraints

- Package / `applicationId`: `ru.aensidhe.dreamclock`.
- `minSdk` 30; `compileSdk` / `targetSdk` 36; Android Gradle Plugin 8.13.2; Kotlin 2.4.0; JDK 21; Gradle 8.14.5 (wrapper-pinned). Static analysis: detekt 1.23.8, ktlint-gradle 14.2.0. The stack stays on the Gradle 8 line so every tool is stable (API 37 / AGP 9 would force Gradle 9, where detekt has no stable release).
- App name is a localized label: `Reverie` (default/EN), `Грёзы` (RU).
- Three states: `PLAY`, `PREPARE`, `SLEEP`. Default colours: PLAY soft green `#7CB342`, PREPARE warm amber `#FFB300`, SLEEP deep plum `#5E35B1`.
- Colour render modes: `TEXT_TINT`, `PANEL_TINT`, `FULL_SCRIM`, `ACCENT`.
- Colloquial time: no part-of-day suffix; 12-hour number; every-minute recompute. Russian one/few/many agreement, feminine minute numerals (одна/две). English past/to pivot at 30 with quarter/half.
- Commits: Conventional Commits with a `:robot:` marker after the type (e.g. `feat: :robot: …`). No `Co-Authored-By` trailer.
- History linear: feature branch → push (CI) → local `git rebase main` + `git merge --ff-only` → push.
- Markdown: no bold/italic emphasis in prose.
- Every task ends green: `./gradlew ktlintCheck detekt test` (plus `assemble` for `:app` tasks).

## File Structure

```
settings.gradle.kts
build.gradle.kts
gradle/libs.versions.toml
gradle/wrapper/…                      (pinned Gradle)
config/detekt/detekt.yml
core/
  build.gradle.kts                    (Kotlin JVM library, no Android)
  src/main/kotlin/ru/aensidhe/dreamclock/core/
    time/ClockLocale.kt
    time/ColloquialTimeFormatter.kt   (interface + factory)
    time/HourMath.kt                  (hour12, comingHour helpers)
    time/RussianNumberWords.kt        (word tables + plural rule)
    time/RuColloquialFormatter.kt
    time/EnglishNumberWords.kt
    time/EnColloquialFormatter.kt
    schedule/StateType.kt
    schedule/Schedule.kt              (Window, DaySchedule, Schedule, ActiveState)
    schedule/ScheduleEngine.kt
  src/test/kotlin/ru/aensidhe/dreamclock/core/…  (mirrors main)
app/
  build.gradle.kts                    (Android application)
  src/main/AndroidManifest.xml
  src/main/proto/settings.proto
  src/main/res/values/strings.xml     (Reverie)
  src/main/res/values-ru/strings.xml  (Грёзы)
  src/main/kotlin/ru/aensidhe/dreamclock/
    settings/Settings.kt              (domain model mapped from proto)
    settings/SettingsSerializer.kt
    settings/SettingsRepository.kt
    ui/StateColors.kt
    ui/colorrender/ColorRenderMode.kt
    ui/colorrender/ColorRenderers.kt
    ui/ClockOverlay.kt
    ui/AnalogClockSlide.kt
    ui/SlideDeck.kt
    ui/ClockViewModel.kt
    dream/TvDreamService.kt
    settings/SettingsActivity.kt
.github/workflows/ci.yml
```

---

## Task 1: Gradle scaffold, version catalog, lint, and empty `:core`

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `config/detekt/detekt.yml`, `core/build.gradle.kts`, `.editorconfig` (sets `ij_kotlin_imports_layout = *` so ktlint accepts lexicographic import order), `.gitignore` additions
- Create: `core/src/test/kotlin/ru/aensidhe/dreamclock/core/ScaffoldSmokeTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: a buildable Gradle project; `./gradlew :core:test ktlintCheck detekt` works.

**Prerequisite:** Gradle must be available once to generate the wrapper (via SDKMAN `sdk install gradle 9.0.0`, Homebrew, or Android Studio). After the wrapper is committed, everything else uses `./gradlew`.

- [ ] **Step 1: Generate and pin the Gradle wrapper**

Run (one-time, needs a Gradle install):
```bash
gradle wrapper --gradle-version 9.0.0 --distribution-type bin
```
Expected: creates `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`.

- [ ] **Step 2: Write the version catalog**

Create `gradle/libs.versions.toml`:
```toml
[versions]
agp = "8.13.2"
kotlin = "2.4.0"
composeBom = "2026.06.00"
coreKtx = "1.15.0"
lifecycle = "2.9.0"
activityCompose = "1.10.0"
datastore = "1.1.1"
protobuf = "4.28.2"
junit = "5.11.0"
ktlint = "14.2.0"
detekt = "1.23.8"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-foundation = { module = "androidx.compose.foundation:foundation" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
datastore = { module = "androidx.datastore:datastore", version.ref = "datastore" }
protobuf-javalite = { module = "com.google.protobuf:protobuf-javalite", version.ref = "protobuf" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ktlint = { id = "org.jlleitschuh.gradle.ktlint", version.ref = "ktlint" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
```
Note: bump any version the toolchain reports as unavailable to its nearest published release; keep AGP on the 8.13 line and `compileSdk`/`minSdk`/`targetSdk` per Global Constraints.

- [ ] **Step 3: Write settings and root build**

Create `settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "reverie"
include(":core", ":app")
```

Create root `build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}
```

- [ ] **Step 4: Write the `:core` build file with lint and JUnit 5**

Create `core/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

kotlin {
    jvmToolchain(21)
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 5: Write detekt config**

Create `config/detekt/detekt.yml`:
```yaml
build:
  maxIssues: 0
style:
  MagicNumber:
    active: false
  ReturnCount:
    active: false
complexity:
  CyclomaticComplexMethod:
    threshold: 20
  LongMethod:
    threshold: 80
```

- [ ] **Step 6: Write the smoke test**

Create `core/src/test/kotlin/ru/aensidhe/dreamclock/core/ScaffoldSmokeTest.kt`:
```kotlin
package ru.aensidhe.dreamclock.core

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class ScaffoldSmokeTest {
    @Test
    fun `build wiring works`() {
        assertEquals(4, 2 + 2)
    }
}
```

- [ ] **Step 7: Run the checks**

Run: `./gradlew :core:test ktlintCheck detekt`
Expected: BUILD SUCCESSFUL; the smoke test passes.

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle core config .gitignore gradlew gradlew.bat
git commit -m "chore: :robot: scaffold gradle project with core module and linters"
```

---

## Task 2: CI pipeline (GitHub Actions)

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: the Gradle build from Task 1.
- Produces: a `build` status check named `build` on push and PR.

- [ ] **Step 1: Write the workflow**

Create `.github/workflows/ci.yml`:
```yaml
name: CI
on:
  push:
  pull_request:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
      - uses: android-actions/setup-android@v3
      - name: Install SDK packages
        run: sdkmanager "platforms;android-37" "build-tools;37.0.0"
      - uses: gradle/actions/setup-gradle@v4
      - name: Check
        run: ./gradlew ktlintCheck detekt test assemble --stacktrace
```

- [ ] **Step 2: Commit and push the branch**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: :robot: add build/test/lint pipeline"
git push -u origin HEAD
```
Expected: the workflow runs on GitHub and the `build` job goes green.

- [ ] **Step 3: Enable the required status check**

After the check has run once, protect main to require it (owner action, or via gh):
```bash
gh api -X PUT repos/aensidhe/ru.aensidhe.dreamclock/branches/main/protection \
  -F required_status_checks.strict=true \
  -F 'required_status_checks.contexts[]=build' \
  -F enforce_admins=false \
  -F required_pull_request_reviews= \
  -F restrictions=
```
Expected: main now rejects pushes whose `build` check is not green.

---

## Task 3: Clock locale, formatter interface, and hour helpers

**Files:**
- Create: `core/src/main/kotlin/ru/aensidhe/dreamclock/core/time/ClockLocale.kt`
- Create: `core/src/main/kotlin/ru/aensidhe/dreamclock/core/time/ColloquialTimeFormatter.kt`
- Create: `core/src/main/kotlin/ru/aensidhe/dreamclock/core/time/HourMath.kt`
- Test: `core/src/test/kotlin/ru/aensidhe/dreamclock/core/time/HourMathTest.kt`

**Interfaces:**
- Produces:
  - `enum class ClockLocale { RU, EN }`
  - `interface ColloquialTimeFormatter { fun format(hour: Int, minute: Int): String }`
  - `fun colloquialFormatter(locale: ClockLocale): ColloquialTimeFormatter`
  - `fun hour12(hour: Int): Int` — 0/12/24 → 12; 13 → 1; 21 → 9
  - `fun comingHour12(hour: Int): Int` — 21 → 10; 23 → 12; 12 → 1; 0 → 1

- [ ] **Step 1: Write the failing test**

Create `core/src/test/kotlin/ru/aensidhe/dreamclock/core/time/HourMathTest.kt`:
```kotlin
package ru.aensidhe.dreamclock.core.time

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class HourMathTest {
    @Test
    fun `hour12 maps 24h to 12h`() {
        assertEquals(12, hour12(0))
        assertEquals(9, hour12(21))
        assertEquals(1, hour12(13))
        assertEquals(12, hour12(12))
    }

    @Test
    fun `comingHour12 is the next 12h hour`() {
        assertEquals(10, comingHour12(21))
        assertEquals(12, comingHour12(23))
        assertEquals(1, comingHour12(12))
        assertEquals(1, comingHour12(0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests '*HourMathTest'`
Expected: FAIL — unresolved reference `hour12`.

- [ ] **Step 3: Write the helpers, interface, and factory**

Create `core/src/main/kotlin/ru/aensidhe/dreamclock/core/time/HourMath.kt`:
```kotlin
package ru.aensidhe.dreamclock.core.time

fun hour12(hour: Int): Int {
    val h = hour % 12
    return if (h == 0) 12 else h
}

fun comingHour12(hour: Int): Int = hour12(hour) % 12 + 1
```

Create `core/src/main/kotlin/ru/aensidhe/dreamclock/core/time/ClockLocale.kt`:
```kotlin
package ru.aensidhe.dreamclock.core.time

enum class ClockLocale { RU, EN }
```

Create `core/src/main/kotlin/ru/aensidhe/dreamclock/core/time/ColloquialTimeFormatter.kt`:
```kotlin
package ru.aensidhe.dreamclock.core.time

interface ColloquialTimeFormatter {
    /** hour in 0..23, minute in 0..59. */
    fun format(hour: Int, minute: Int): String
}

fun colloquialFormatter(locale: ClockLocale): ColloquialTimeFormatter =
    when (locale) {
        ClockLocale.RU -> RuColloquialFormatter
        ClockLocale.EN -> EnColloquialFormatter
    }
```
Note: this will not compile until Tasks 5 and 6 create `RuColloquialFormatter` and `EnColloquialFormatter`. To keep Task 3 green on its own, temporarily stub them at the bottom of this file and delete the stubs in Tasks 5/6:
```kotlin
internal object RuColloquialFormatter : ColloquialTimeFormatter {
    override fun format(hour: Int, minute: Int): String = TODO("Task 5")
}
internal object EnColloquialFormatter : ColloquialTimeFormatter {
    override fun format(hour: Int, minute: Int): String = TODO("Task 6")
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests '*HourMathTest' ktlintCheck detekt`
Expected: PASS; lint clean.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/ru/aensidhe/dreamclock/core/time core/src/test/kotlin/ru/aensidhe/dreamclock/core/time/HourMathTest.kt
git commit -m "feat: :robot: add clock-locale, formatter interface, and hour helpers"
```

---

## Task 4: Russian number words and plural rule

**Files:**
- Create: `core/src/main/kotlin/ru/aensidhe/dreamclock/core/time/RussianNumberWords.kt`
- Test: `core/src/test/kotlin/ru/aensidhe/dreamclock/core/time/RussianNumberWordsTest.kt`

**Interfaces:**
- Produces (all `internal`):
  - `enum class PluralForm { ONE, FEW, MANY }`
  - `fun russianPlural(n: Int): PluralForm`
  - `fun minuteNoun(n: Int): String` — минута/минуты/минут
  - `fun hourNoun(n: Int): String` — час/часа/часов
  - `val hourCardinalNominative: Map<Int, String>` — 1..12, 1 → "час"
  - `val hourCardinalMasculine: Map<Int, String>` — 1..12, 1 → "один"
  - `val comingHourOrdinalGenitive: Map<Int, String>` — 1..12, e.g. 10 → "десятого"
  - `val minuteCardinalFeminine: Map<Int, String>` — 1..29 (feminine одна/две), no 15
  - `val minuteGenitive: Map<Int, String>` — 1..29 (без-form genitive), no 15

- [ ] **Step 1: Write the failing test**

Create `core/src/test/kotlin/ru/aensidhe/dreamclock/core/time/RussianNumberWordsTest.kt`:
```kotlin
package ru.aensidhe.dreamclock.core.time

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class RussianNumberWordsTest {
    @Test
    fun `plural one few many`() {
        assertEquals(PluralForm.ONE, russianPlural(1))
        assertEquals(PluralForm.ONE, russianPlural(21))
        assertEquals(PluralForm.FEW, russianPlural(2))
        assertEquals(PluralForm.FEW, russianPlural(24))
        assertEquals(PluralForm.MANY, russianPlural(5))
        assertEquals(PluralForm.MANY, russianPlural(11))
        assertEquals(PluralForm.MANY, russianPlural(12))
        assertEquals(PluralForm.MANY, russianPlural(14))
        assertEquals(PluralForm.MANY, russianPlural(20))
    }

    @Test
    fun `minute noun agreement`() {
        assertEquals("минута", minuteNoun(1))
        assertEquals("минуты", minuteNoun(3))
        assertEquals("минут", minuteNoun(11))
        assertEquals("минута", minuteNoun(21))
    }

    @Test
    fun `hour noun agreement`() {
        assertEquals("час", hourNoun(1))
        assertEquals("часа", hourNoun(2))
        assertEquals("часов", hourNoun(9))
        assertEquals("часов", hourNoun(12))
    }

    @Test
    fun `word tables`() {
        assertEquals("десятого", comingHourOrdinalGenitive[10])
        assertEquals("десять", hourCardinalNominative[10])
        assertEquals("час", hourCardinalNominative[1])
        assertEquals("двадцать три", minuteCardinalFeminine[23])
        assertEquals("две", minuteCardinalFeminine[2])
        assertEquals("двадцати пяти", minuteGenitive[25])
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests '*RussianNumberWordsTest'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Write the number words**

Create `core/src/main/kotlin/ru/aensidhe/dreamclock/core/time/RussianNumberWords.kt`:
```kotlin
package ru.aensidhe.dreamclock.core.time

internal enum class PluralForm { ONE, FEW, MANY }

internal fun russianPlural(n: Int): PluralForm {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> PluralForm.MANY
        mod10 == 1 -> PluralForm.ONE
        mod10 in 2..4 -> PluralForm.FEW
        else -> PluralForm.MANY
    }
}

internal fun minuteNoun(n: Int): String =
    when (russianPlural(n)) {
        PluralForm.ONE -> "минута"
        PluralForm.FEW -> "минуты"
        PluralForm.MANY -> "минут"
    }

internal fun hourNoun(n: Int): String =
    when (russianPlural(n)) {
        PluralForm.ONE -> "час"
        PluralForm.FEW -> "часа"
        PluralForm.MANY -> "часов"
    }

internal val hourCardinalNominative: Map<Int, String> = mapOf(
    1 to "час", 2 to "два", 3 to "три", 4 to "четыре", 5 to "пять", 6 to "шесть",
    7 to "семь", 8 to "восемь", 9 to "девять", 10 to "десять",
    11 to "одиннадцать", 12 to "двенадцать",
)

internal val hourCardinalMasculine: Map<Int, String> = mapOf(
    1 to "один", 2 to "два", 3 to "три", 4 to "четыре", 5 to "пять", 6 to "шесть",
    7 to "семь", 8 to "восемь", 9 to "девять", 10 to "десять",
    11 to "одиннадцать", 12 to "двенадцать",
)

internal val comingHourOrdinalGenitive: Map<Int, String> = mapOf(
    1 to "первого", 2 to "второго", 3 to "третьего", 4 to "четвёртого",
    5 to "пятого", 6 to "шестого", 7 to "седьмого", 8 to "восьмого",
    9 to "девятого", 10 to "десятого", 11 to "одиннадцатого", 12 to "двенадцатого",
)

internal val minuteCardinalFeminine: Map<Int, String> = mapOf(
    1 to "одна", 2 to "две", 3 to "три", 4 to "четыре", 5 to "пять",
    6 to "шесть", 7 to "семь", 8 to "восемь", 9 to "девять", 10 to "десять",
    11 to "одиннадцать", 12 to "двенадцать", 13 to "тринадцать", 14 to "четырнадцать",
    16 to "шестнадцать", 17 to "семнадцать", 18 to "восемнадцать", 19 to "девятнадцать",
    20 to "двадцать", 21 to "двадцать одна", 22 to "двадцать две", 23 to "двадцать три",
    24 to "двадцать четыре", 25 to "двадцать пять", 26 to "двадцать шесть",
    27 to "двадцать семь", 28 to "двадцать восемь", 29 to "двадцать девять",
)

internal val minuteGenitive: Map<Int, String> = mapOf(
    1 to "одной", 2 to "двух", 3 to "трёх", 4 to "четырёх", 5 to "пяти",
    6 to "шести", 7 to "семи", 8 to "восьми", 9 to "девяти", 10 to "десяти",
    11 to "одиннадцати", 12 to "двенадцати", 13 to "тринадцати", 14 to "четырнадцати",
    16 to "шестнадцати", 17 to "семнадцати", 18 to "восемнадцати", 19 to "девятнадцати",
    20 to "двадцати", 21 to "двадцати одной", 22 to "двадцати двух", 23 to "двадцати трёх",
    24 to "двадцати четырёх", 25 to "двадцати пяти", 26 to "двадцати шести",
    27 to "двадцати семи", 28 to "двадцати восьми", 29 to "двадцати девяти",
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests '*RussianNumberWordsTest' ktlintCheck detekt`
Expected: PASS; lint clean.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/ru/aensidhe/dreamclock/core/time/RussianNumberWords.kt core/src/test/kotlin/ru/aensidhe/dreamclock/core/time/RussianNumberWordsTest.kt
git commit -m "feat: :robot: add russian number-word tables and plural rule"
```

---

## Task 5: Russian colloquial formatter

**Files:**
- Create: `core/src/main/kotlin/ru/aensidhe/dreamclock/core/time/RuColloquialFormatter.kt`
- Modify: `core/src/main/kotlin/ru/aensidhe/dreamclock/core/time/ColloquialTimeFormatter.kt` (remove the `RuColloquialFormatter` stub from Task 3)
- Test: `core/src/test/kotlin/ru/aensidhe/dreamclock/core/time/RuColloquialFormatterTest.kt`

**Interfaces:**
- Produces: `object RuColloquialFormatter : ColloquialTimeFormatter`.
- Consumes: everything from Tasks 3 and 4.

**Rules (m = minute):**
- m == 0 → exact hour: `hourCardinalMasculine[h12] + " " + hourNoun(h12)`, but h12 == 1 → just `час`.
- m in 1..14 or 16..29 → `minuteCardinalFeminine[m] + " " + minuteNoun(m) + " " + comingHourOrdinalGenitive[coming]`.
- m == 15 → `"четверть " + comingHourOrdinalGenitive[coming]`.
- m == 30 → `"половина " + comingHourOrdinalGenitive[coming]`.
- m == 45 → `"без четверти " + hourCardinalNominative[coming]`.
- m in 31..44 or 46..59 → rem = 60 - m; `"без " + minuteGenitive[rem] + " " + hourCardinalNominative[coming]`.

- [ ] **Step 1: Write the failing test (case table)**

Create `core/src/test/kotlin/ru/aensidhe/dreamclock/core/time/RuColloquialFormatterTest.kt`:
```kotlin
package ru.aensidhe.dreamclock.core.time

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class RuColloquialFormatterTest {
    private fun ru(h: Int, m: Int) = RuColloquialFormatter.format(h, m)

    @Test
    fun `exact hours`() {
        assertEquals("девять часов", ru(21, 0))
        assertEquals("час", ru(13, 0))
        assertEquals("два часа", ru(14, 0))
        assertEquals("двенадцать часов", ru(12, 0))
        assertEquals("двенадцать часов", ru(0, 0))
    }

    @Test
    fun `minutes into the coming hour`() {
        assertEquals("одна минута десятого", ru(21, 1))
        assertEquals("пять минут десятого", ru(21, 5))
        assertEquals("двадцать три минуты десятого", ru(21, 23))
        assertEquals("двадцать одна минута десятого", ru(21, 21))
    }

    @Test
    fun `quarter and half`() {
        assertEquals("четверть десятого", ru(21, 15))
        assertEquals("половина десятого", ru(21, 30))
    }

    @Test
    fun `to the next hour`() {
        assertEquals("без четверти десять", ru(21, 45))
        assertEquals("без пяти десять", ru(21, 55))
        assertEquals("без двадцати пяти десять", ru(21, 35))
        assertEquals("без одной десять", ru(21, 59))
    }

    @Test
    fun `wrap around noon and midnight`() {
        assertEquals("без четверти час", ru(12, 45))
        assertEquals("пять минут первого", ru(0, 5))
        assertEquals("без пяти двенадцать", ru(23, 55))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests '*RuColloquialFormatterTest'`
Expected: FAIL — `TODO("Task 5")` or unresolved `RuColloquialFormatter`.

- [ ] **Step 3: Remove the Task 3 stub**

In `ColloquialTimeFormatter.kt`, delete the `internal object RuColloquialFormatter { … }` stub.

- [ ] **Step 4: Write the formatter**

Create `core/src/main/kotlin/ru/aensidhe/dreamclock/core/time/RuColloquialFormatter.kt`:
```kotlin
package ru.aensidhe.dreamclock.core.time

object RuColloquialFormatter : ColloquialTimeFormatter {
    override fun format(hour: Int, minute: Int): String {
        val h12 = hour12(hour)
        val coming = comingHour12(hour)
        val comingOrdinal = comingHourOrdinalGenitive.getValue(coming)
        val comingCardinal = hourCardinalNominative.getValue(coming)
        return when {
            minute == 0 -> exactHour(h12)
            minute == 15 -> "четверть $comingOrdinal"
            minute == 30 -> "половина $comingOrdinal"
            minute == 45 -> "без четверти $comingCardinal"
            minute < 30 -> {
                val num = minuteCardinalFeminine.getValue(minute)
                "$num ${minuteNoun(minute)} $comingOrdinal"
            }
            else -> {
                val rem = 60 - minute
                "без ${minuteGenitive.getValue(rem)} $comingCardinal"
            }
        }
    }

    private fun exactHour(h12: Int): String =
        if (h12 == 1) "час" else "${hourCardinalMasculine.getValue(h12)} ${hourNoun(h12)}"
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests '*RuColloquialFormatterTest' ktlintCheck detekt`
Expected: PASS; lint clean.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/kotlin/ru/aensidhe/dreamclock/core/time/RuColloquialFormatter.kt core/src/main/kotlin/ru/aensidhe/dreamclock/core/time/ColloquialTimeFormatter.kt core/src/test/kotlin/ru/aensidhe/dreamclock/core/time/RuColloquialFormatterTest.kt
git commit -m "feat: :robot: add russian colloquial time formatter"
```

---

## Task 6: English colloquial formatter

**Files:**
- Create: `core/src/main/kotlin/ru/aensidhe/dreamclock/core/time/EnglishNumberWords.kt`
- Create: `core/src/main/kotlin/ru/aensidhe/dreamclock/core/time/EnColloquialFormatter.kt`
- Modify: `core/src/main/kotlin/ru/aensidhe/dreamclock/core/time/ColloquialTimeFormatter.kt` (remove the `EnColloquialFormatter` stub)
- Test: `core/src/test/kotlin/ru/aensidhe/dreamclock/core/time/EnColloquialFormatterTest.kt`

**Interfaces:**
- Produces:
  - `internal fun englishCardinal(n: Int): String` — 1..59, e.g. 23 → "twenty-three"
  - `internal val englishHour: Map<Int, String>` — 1..12, e.g. 9 → "nine"
  - `object EnColloquialFormatter : ColloquialTimeFormatter`

**Rules (m = minute):**
- m == 0 → `englishHour[h12] + " o'clock"`.
- m == 15 → `"quarter past " + englishHour[h12]`.
- m == 30 → `"half past " + englishHour[h12]`.
- m == 45 → `"quarter to " + englishHour[coming]`.
- m in 1..29 → `englishCardinal(m) + " past " + englishHour[h12]`.
- m in 31..59 (not 45) → rem = 60 - m; `englishCardinal(rem) + " to " + englishHour[coming]`.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/kotlin/ru/aensidhe/dreamclock/core/time/EnColloquialFormatterTest.kt`:
```kotlin
package ru.aensidhe.dreamclock.core.time

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class EnColloquialFormatterTest {
    private fun en(h: Int, m: Int) = EnColloquialFormatter.format(h, m)

    @Test
    fun `exact and simple`() {
        assertEquals("nine o'clock", en(21, 0))
        assertEquals("twelve o'clock", en(0, 0))
        assertEquals("five past nine", en(21, 5))
        assertEquals("twenty-three past nine", en(21, 23))
    }

    @Test
    fun `quarter half to`() {
        assertEquals("quarter past nine", en(21, 15))
        assertEquals("half past nine", en(21, 30))
        assertEquals("quarter to ten", en(21, 45))
        assertEquals("five to ten", en(21, 55))
        assertEquals("twenty-five to ten", en(21, 35))
    }

    @Test
    fun `wrap around noon`() {
        assertEquals("quarter to one", en(12, 45))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests '*EnColloquialFormatterTest'`
Expected: FAIL.

- [ ] **Step 3: Write the English number words**

Create `core/src/main/kotlin/ru/aensidhe/dreamclock/core/time/EnglishNumberWords.kt`:
```kotlin
package ru.aensidhe.dreamclock.core.time

internal val englishHour: Map<Int, String> = mapOf(
    1 to "one", 2 to "two", 3 to "three", 4 to "four", 5 to "five", 6 to "six",
    7 to "seven", 8 to "eight", 9 to "nine", 10 to "ten", 11 to "eleven", 12 to "twelve",
)

private val ones = listOf(
    "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
    "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
    "seventeen", "eighteen", "nineteen",
)

private val tens = mapOf(20 to "twenty", 30 to "thirty", 40 to "forty", 50 to "fifty")

internal fun englishCardinal(n: Int): String {
    require(n in 1..59) { "minute out of range: $n" }
    if (n < 20) return ones[n]
    val tensPart = tens.getValue(n / 10 * 10)
    val onesPart = n % 10
    return if (onesPart == 0) tensPart else "$tensPart-${ones[onesPart]}"
}
```

- [ ] **Step 4: Remove the Task 3 stub and write the formatter**

In `ColloquialTimeFormatter.kt`, delete the `internal object EnColloquialFormatter { … }` stub.

Create `core/src/main/kotlin/ru/aensidhe/dreamclock/core/time/EnColloquialFormatter.kt`:
```kotlin
package ru.aensidhe.dreamclock.core.time

object EnColloquialFormatter : ColloquialTimeFormatter {
    override fun format(hour: Int, minute: Int): String {
        val h12 = hour12(hour)
        val coming = comingHour12(hour)
        return when {
            minute == 0 -> "${englishHour.getValue(h12)} o'clock"
            minute == 15 -> "quarter past ${englishHour.getValue(h12)}"
            minute == 30 -> "half past ${englishHour.getValue(h12)}"
            minute == 45 -> "quarter to ${englishHour.getValue(coming)}"
            minute < 30 -> "${englishCardinal(minute)} past ${englishHour.getValue(h12)}"
            else -> "${englishCardinal(60 - minute)} to ${englishHour.getValue(coming)}"
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test ktlintCheck detekt`
Expected: PASS; lint clean; the full `:core` time suite is green.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/kotlin/ru/aensidhe/dreamclock/core/time/EnglishNumberWords.kt core/src/main/kotlin/ru/aensidhe/dreamclock/core/time/EnColloquialFormatter.kt core/src/main/kotlin/ru/aensidhe/dreamclock/core/time/ColloquialTimeFormatter.kt core/src/test/kotlin/ru/aensidhe/dreamclock/core/time/EnColloquialFormatterTest.kt
git commit -m "feat: :robot: add english colloquial time formatter"
```

---

## Task 7: Schedule model

**Files:**
- Create: `core/src/main/kotlin/ru/aensidhe/dreamclock/core/schedule/StateType.kt`
- Create: `core/src/main/kotlin/ru/aensidhe/dreamclock/core/schedule/Schedule.kt`
- Test: `core/src/test/kotlin/ru/aensidhe/dreamclock/core/schedule/ScheduleTest.kt`

**Interfaces:**
- Produces:
  - `enum class StateType { PLAY, PREPARE, SLEEP }`
  - `data class Window(val start: LocalTime, val state: StateType, val textOverride: String? = null)`
  - `class DaySchedule(windows: List<Window>)` — sorts by `start`; requires a window at `00:00`; exposes `val windows: List<Window>`
  - `data class Schedule(val default: DaySchedule, val byDayOfWeek: Map<DayOfWeek, DaySchedule> = emptyMap(), val overrides: Map<LocalDate, DaySchedule> = emptyMap())`
  - `data class ActiveState(val state: StateType, val textOverride: String?)`

- [ ] **Step 1: Write the failing test**

Create `core/src/test/kotlin/ru/aensidhe/dreamclock/core/schedule/ScheduleTest.kt`:
```kotlin
package ru.aensidhe.dreamclock.core.schedule

import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class ScheduleTest {
    @Test
    fun `day schedule sorts windows`() {
        val day = DaySchedule(
            listOf(
                Window(LocalTime.of(20, 0), StateType.PREPARE),
                Window(LocalTime.MIDNIGHT, StateType.SLEEP),
                Window(LocalTime.of(7, 0), StateType.PLAY),
            ),
        )
        assertEquals(
            listOf(LocalTime.MIDNIGHT, LocalTime.of(7, 0), LocalTime.of(20, 0)),
            day.windows.map { it.start },
        )
    }

    @Test
    fun `day schedule requires midnight window`() {
        assertFailsWith<IllegalArgumentException> {
            DaySchedule(listOf(Window(LocalTime.of(7, 0), StateType.PLAY)))
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests '*ScheduleTest'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Write the model**

Create `core/src/main/kotlin/ru/aensidhe/dreamclock/core/schedule/StateType.kt`:
```kotlin
package ru.aensidhe.dreamclock.core.schedule

enum class StateType { PLAY, PREPARE, SLEEP }
```

Create `core/src/main/kotlin/ru/aensidhe/dreamclock/core/schedule/Schedule.kt`:
```kotlin
package ru.aensidhe.dreamclock.core.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class Window(
    val start: LocalTime,
    val state: StateType,
    val textOverride: String? = null,
)

class DaySchedule(windows: List<Window>) {
    val windows: List<Window> = windows.sortedBy { it.start }

    init {
        require(this.windows.firstOrNull()?.start == LocalTime.MIDNIGHT) {
            "a day schedule must start with a 00:00 window"
        }
    }
}

data class Schedule(
    val default: DaySchedule,
    val byDayOfWeek: Map<DayOfWeek, DaySchedule> = emptyMap(),
    val overrides: Map<LocalDate, DaySchedule> = emptyMap(),
)

data class ActiveState(val state: StateType, val textOverride: String?)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests '*ScheduleTest' ktlintCheck detekt`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/ru/aensidhe/dreamclock/core/schedule core/src/test/kotlin/ru/aensidhe/dreamclock/core/schedule/ScheduleTest.kt
git commit -m "feat: :robot: add schedule model"
```

---

## Task 8: Schedule engine

**Files:**
- Create: `core/src/main/kotlin/ru/aensidhe/dreamclock/core/schedule/ScheduleEngine.kt`
- Test: `core/src/test/kotlin/ru/aensidhe/dreamclock/core/schedule/ScheduleEngineTest.kt`

**Interfaces:**
- Produces: `object ScheduleEngine { fun activeState(now: LocalDateTime, schedule: Schedule): ActiveState }`
- Resolution: `overrides[now.date]` else `byDayOfWeek[now.dayOfWeek]` else `default`; then the last window with `start <= now.time`.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/kotlin/ru/aensidhe/dreamclock/core/schedule/ScheduleEngineTest.kt`:
```kotlin
package ru.aensidhe.dreamclock.core.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class ScheduleEngineTest {
    private val default = DaySchedule(
        listOf(
            Window(LocalTime.MIDNIGHT, StateType.SLEEP),
            Window(LocalTime.of(7, 0), StateType.PLAY),
            Window(LocalTime.of(20, 0), StateType.PREPARE),
            Window(LocalTime.of(21, 0), StateType.SLEEP, "спать"),
        ),
    )

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi)

    @Test
    fun `picks window by time of day`() {
        val s = Schedule(default)
        assertEquals(StateType.SLEEP, ScheduleEngine.activeState(at(2026, 7, 13, 6, 59), s).state)
        assertEquals(StateType.PLAY, ScheduleEngine.activeState(at(2026, 7, 13, 7, 0), s).state)
        assertEquals(StateType.PREPARE, ScheduleEngine.activeState(at(2026, 7, 13, 20, 30), s).state)
        val night = ScheduleEngine.activeState(at(2026, 7, 13, 21, 0), s)
        assertEquals(StateType.SLEEP, night.state)
        assertEquals("спать", night.textOverride)
    }

    @Test
    fun `day-of-week overrides default`() {
        val weekend = DaySchedule(
            listOf(
                Window(LocalTime.MIDNIGHT, StateType.SLEEP),
                Window(LocalTime.of(9, 0), StateType.PLAY),
            ),
        )
        val s = Schedule(default, byDayOfWeek = mapOf(DayOfWeek.SUNDAY to weekend))
        // 2026-07-12 is a Sunday.
        assertEquals(StateType.SLEEP, ScheduleEngine.activeState(at(2026, 7, 12, 8, 0), s).state)
        assertEquals(StateType.PLAY, ScheduleEngine.activeState(at(2026, 7, 12, 9, 0), s).state)
    }

    @Test
    fun `date override wins over everything`() {
        val holiday = DaySchedule(listOf(Window(LocalTime.MIDNIGHT, StateType.PLAY)))
        val s = Schedule(
            default,
            byDayOfWeek = mapOf(DayOfWeek.MONDAY to default),
            overrides = mapOf(LocalDate.of(2026, 7, 13) to holiday),
        )
        assertEquals(StateType.PLAY, ScheduleEngine.activeState(at(2026, 7, 13, 3, 0), s).state)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests '*ScheduleEngineTest'`
Expected: FAIL — unresolved `ScheduleEngine`.

- [ ] **Step 3: Write the engine**

Create `core/src/main/kotlin/ru/aensidhe/dreamclock/core/schedule/ScheduleEngine.kt`:
```kotlin
package ru.aensidhe.dreamclock.core.schedule

import java.time.LocalDateTime

object ScheduleEngine {
    fun activeState(now: LocalDateTime, schedule: Schedule): ActiveState {
        val day = schedule.overrides[now.toLocalDate()]
            ?: schedule.byDayOfWeek[now.dayOfWeek]
            ?: schedule.default
        val time = now.toLocalTime()
        val window = day.windows.last { it.start <= time }
        return ActiveState(window.state, window.textOverride)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test ktlintCheck detekt`
Expected: PASS; the entire `:core` module is green.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/ru/aensidhe/dreamclock/core/schedule/ScheduleEngine.kt core/src/test/kotlin/ru/aensidhe/dreamclock/core/schedule/ScheduleEngineTest.kt
git commit -m "feat: :robot: add schedule engine with layered resolution"
```

---

> **Gate:** Tasks 9–15 build the Android `:app` module and require the Android SDK (API 36) installed with `local.properties` pointing at it (`sdk.dir=…`). They also need the confirmed device minSdk (30). Do not start them until the SDK is available.

## Task 9: App module, manifest, localized labels, DreamService registration

**Files:**
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-ru/strings.xml`
- Create: `app/src/main/res/xml/dream_info.xml`
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/dream/TvDreamService.kt`

**Interfaces:**
- Produces: a `TvDreamService : DreamService` registered as a TV screensaver; app assembles.

- [ ] **Step 1: Write the app build file**

Create `app/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "ru.aensidhe.dreamclock"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.aensidhe.dreamclock"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures { compose = true }

    kotlinOptions { jvmTarget = "21" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.datastore)
    implementation(libs.protobuf.javalite)
}
```

- [ ] **Step 2: Write localized labels**

Create `app/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">Reverie</string>
</resources>
```

Create `app/src/main/res/values-ru/strings.xml`:
```xml
<resources>
    <string name="app_name">Грёзы</string>
</resources>
```

- [ ] **Step 3: Write the dream metadata and manifest**

Create `app/src/main/res/xml/dream_info.xml`:
```xml
<dream xmlns:android="http://schemas.android.com/apk/res/android"
    android:settingsActivity="ru.aensidhe.dreamclock/.settings.SettingsActivity" />
```

Create `app/src/main/AndroidManifest.xml`:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true">

        <service
            android:name=".dream.TvDreamService"
            android:exported="true"
            android:permission="android.permission.BIND_DREAM_SERVICE">
            <intent-filter>
                <action android:name="android.service.dreams.DreamService" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
            <meta-data
                android:name="android.service.dream"
                android:resource="@xml/dream_info" />
        </service>

        <activity
            android:name=".settings.SettingsActivity"
            android:exported="true"
            android:label="@string/app_name">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 4: Write a minimal DreamService**

Create `app/src/main/kotlin/ru/aensidhe/dreamclock/dream/TvDreamService.kt`:
```kotlin
package ru.aensidhe.dreamclock.dream

import android.service.dreams.DreamService

class TvDreamService : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        isInteractive = false
        setContentView(android.widget.FrameLayout(this))
    }
}
```
Note: `SettingsActivity` is referenced by the manifest; Task 14 creates it. To assemble now, create a temporary empty `SettingsActivity` stub and replace it in Task 14:
```kotlin
package ru.aensidhe.dreamclock.settings
import android.app.Activity
class SettingsActivity : Activity()
```

- [ ] **Step 5: Assemble**

Run: `./gradlew :app:assembleDebug ktlintCheck detekt`
Expected: BUILD SUCCESSFUL; APK produced.

- [ ] **Step 6: Commit**

```bash
git add app
git commit -m "feat: :robot: add app module, manifest, localized labels, and dream service"
```

---

## Task 10: Settings model and Proto DataStore repository

**Files:**
- Create: `app/src/main/proto/settings.proto`
- Modify: `app/build.gradle.kts` (add the protobuf plugin + codegen)
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsSerializer.kt`
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsRepository.kt`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/settings/SettingsRepositoryTest.kt`

**Interfaces:**
- Produces:
  - Proto-generated `Settings` message with fields: `language` (enum FOLLOW_SYSTEM/RU/EN), `show_colloquial` (bool), `show_seconds` (bool), `show_analog_slide` (bool), `color_render_mode` (enum matching `ColorRenderMode`).
  - `class SettingsRepository(context)` exposing `val settings: Flow<Settings>` and `suspend fun update(transform)`.

- [ ] **Step 1: Add the protobuf plugin to the catalog and app build**

Add to `gradle/libs.versions.toml` under `[versions]`: `protobufPlugin = "0.9.4"`, and under `[plugins]`:
```toml
protobuf = { id = "com.google.protobuf", version.ref = "protobufPlugin" }
```
Add to `app/build.gradle.kts` plugins block: `alias(libs.plugins.protobuf)` and the codegen config:
```kotlin
protobuf {
    protoc { artifact = "com.google.protobuf:protoc:4.28.2" }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") { option("lite") }
            }
        }
    }
}
```

- [ ] **Step 2: Write the failing repository test**

Create `app/src/test/kotlin/ru/aensidhe/dreamclock/settings/SettingsRepositoryTest.kt`:
```kotlin
package ru.aensidhe.dreamclock.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class SettingsRepositoryTest {
    @Test
    fun `defaults then update round-trips`() = runTest {
        val repo = SettingsRepository.inMemory()
        assertTrue(repo.settings.first().showColloquial)
        repo.update { it.toBuilder().setShowSeconds(false).build() }
        assertEquals(false, repo.settings.first().showSeconds)
    }
}
```

- [ ] **Step 3: Write the proto**

Create `app/src/main/proto/settings.proto`:
```proto
syntax = "proto3";
option java_package = "ru.aensidhe.dreamclock.settings";
option java_multiple_files = true;

enum Language { FOLLOW_SYSTEM = 0; RU = 1; EN = 2; }
enum ColorRenderModeProto { TEXT_TINT = 0; PANEL_TINT = 1; FULL_SCRIM = 2; ACCENT = 3; }

message Settings {
  Language language = 1;
  bool show_colloquial = 2;
  bool show_seconds = 3;
  bool show_analog_slide = 4;
  ColorRenderModeProto color_render_mode = 5;
}
```

- [ ] **Step 4: Write the serializer and repository**

Create `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsSerializer.kt`:
```kotlin
package ru.aensidhe.dreamclock.settings

import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream

object SettingsSerializer : Serializer<Settings> {
    override val defaultValue: Settings =
        Settings.newBuilder()
            .setShowColloquial(true)
            .setShowSeconds(true)
            .setShowAnalogSlide(true)
            .build()

    override suspend fun readFrom(input: InputStream): Settings = Settings.parseFrom(input)

    override suspend fun writeTo(t: Settings, output: OutputStream) = t.writeTo(output)
}
```

Create `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsRepository.kt`:
```kotlin
package ru.aensidhe.dreamclock.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow

private val Context.settingsStore: DataStore<Settings> by dataStore(
    fileName = "settings.pb",
    serializer = SettingsSerializer,
)

class SettingsRepository private constructor(private val store: DataStore<Settings>) {
    val settings: Flow<Settings> = store.data

    suspend fun update(transform: (Settings) -> Settings) {
        store.updateData(transform)
    }

    companion object {
        fun from(context: Context): SettingsRepository =
            SettingsRepository(context.settingsStore)

        // Backed by an in-memory DataStore for unit tests; see test source set.
        fun inMemory(): SettingsRepository =
            SettingsRepository(InMemorySettingsDataStore())
    }
}
```
Note: add a tiny `InMemorySettingsDataStore` implementing `DataStore<Settings>` in the test source set (holds a `MutableStateFlow`), so the repository test runs on the JVM without Android.

- [ ] **Step 5: Run tests and assemble**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug ktlintCheck detekt`
Expected: PASS; APK builds.

- [ ] **Step 6: Commit**

```bash
git add app gradle/libs.versions.toml
git commit -m "feat: :robot: add proto datastore settings repository"
```

---

## Task 11: State colours and colour render modes

**Files:**
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/StateColors.kt`
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/colorrender/ColorRenderMode.kt`
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/colorrender/ColorRenderers.kt`

**Interfaces:**
- Produces:
  - `fun stateColor(state: StateType): Color` — PLAY `#7CB342`, PREPARE `#FFB300`, SLEEP `#5E35B1`
  - `enum class ColorRenderMode { TEXT_TINT, PANEL_TINT, FULL_SCRIM, ACCENT }`
  - `@Composable fun ColorRenderMode.RenderOverlay(state: StateType, content: @Composable (textColor: Color) -> Unit)` — wraps the text block, supplying the text colour and any panel/scrim/accent per the mode.

- [ ] **Step 1: Write state colours**

Create `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/StateColors.kt`:
```kotlin
package ru.aensidhe.dreamclock.ui

import androidx.compose.ui.graphics.Color
import ru.aensidhe.dreamclock.core.schedule.StateType

fun stateColor(state: StateType): Color =
    when (state) {
        StateType.PLAY -> Color(0xFF7CB342)
        StateType.PREPARE -> Color(0xFFFFB300)
        StateType.SLEEP -> Color(0xFF5E35B1)
    }
```

- [ ] **Step 2: Write the render mode enum and renderers**

Create `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/colorrender/ColorRenderMode.kt`:
```kotlin
package ru.aensidhe.dreamclock.ui.colorrender

enum class ColorRenderMode { TEXT_TINT, PANEL_TINT, FULL_SCRIM, ACCENT }
```

Create `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/colorrender/ColorRenderers.kt`:
```kotlin
package ru.aensidhe.dreamclock.ui.colorrender

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.aensidhe.dreamclock.core.schedule.StateType
import ru.aensidhe.dreamclock.ui.stateColor

private val nearWhite = Color(0xFFF5F5F5)

@Composable
fun ColorRenderMode.RenderOverlay(
    state: StateType,
    content: @Composable (textColor: Color) -> Unit,
) {
    val color = stateColor(state)
    when (this) {
        ColorRenderMode.TEXT_TINT -> content(color)
        ColorRenderMode.PANEL_TINT ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(color.copy(alpha = 0.35f))
                    .padding(16.dp),
            ) { content(nearWhite) }
        ColorRenderMode.FULL_SCRIM ->
            Box(Modifier.background(color.copy(alpha = 0.20f))) { content(nearWhite) }
        ColorRenderMode.ACCENT ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.9f))
                    .padding(4.dp),
            ) { content(nearWhite) }
    }
}
```
Note: FULL_SCRIM's wash normally covers the whole screen; here it is scoped to the overlay box for isolation. Task 15 decides final placement on-device.

- [ ] **Step 3: Assemble and lint**

Run: `./gradlew :app:assembleDebug ktlintCheck detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/ui
git commit -m "feat: :robot: add state colours and colour render modes"
```

---

## Task 12: Clock view model (time + active state stream)

**Files:**
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/ClockViewModel.kt`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/ui/ClockFormattingTest.kt`

**Interfaces:**
- Produces:
  - `data class ClockUiState(val digital: String, val colloquial: String?, val statusText: String?, val state: StateType)`
  - `fun buildClockUiState(now: LocalDateTime, settings: Settings, schedule: Schedule, statusTextFor: (StateType) -> String): ClockUiState` — pure function, unit-tested
  - `class ClockViewModel(...)` emitting `StateFlow<ClockUiState>` ticking each second

- [ ] **Step 1: Write the failing test for the pure builder**

Create `app/src/test/kotlin/ru/aensidhe/dreamclock/ui/ClockFormattingTest.kt`:
```kotlin
package ru.aensidhe.dreamclock.ui

import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import ru.aensidhe.dreamclock.core.schedule.DaySchedule
import ru.aensidhe.dreamclock.core.schedule.Schedule
import ru.aensidhe.dreamclock.core.schedule.StateType
import ru.aensidhe.dreamclock.core.schedule.Window
import ru.aensidhe.dreamclock.settings.Language
import ru.aensidhe.dreamclock.settings.Settings

class ClockFormattingTest {
    private val schedule = Schedule(
        DaySchedule(
            listOf(
                Window(LocalTime.MIDNIGHT, StateType.SLEEP),
                Window(LocalTime.of(21, 0), StateType.SLEEP),
            ),
        ),
    )

    @Test
    fun `russian digital and colloquial with seconds`() {
        val settings = Settings.newBuilder()
            .setLanguage(Language.RU)
            .setShowColloquial(true)
            .setShowSeconds(true)
            .build()
        val ui = buildClockUiState(
            LocalDateTime.of(2026, 7, 13, 21, 45, 5),
            settings,
            schedule,
        ) { "спать" }
        assertEquals("21:45:05", ui.digital)
        assertEquals("без четверти десять", ui.colloquial)
        assertEquals("спать", ui.statusText)
    }

    @Test
    fun `seconds off and colloquial off`() {
        val settings = Settings.newBuilder()
            .setLanguage(Language.RU)
            .setShowColloquial(false)
            .setShowSeconds(false)
            .build()
        val ui = buildClockUiState(
            LocalDateTime.of(2026, 7, 13, 21, 45, 5),
            settings,
            schedule,
        ) { "спать" }
        assertEquals("21:45", ui.digital)
        assertEquals(null, ui.colloquial)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*ClockFormattingTest'`
Expected: FAIL — unresolved `buildClockUiState`.

- [ ] **Step 3: Write the builder and view model**

Create `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/ClockViewModel.kt`:
```kotlin
package ru.aensidhe.dreamclock.ui

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import ru.aensidhe.dreamclock.core.schedule.Schedule
import ru.aensidhe.dreamclock.core.schedule.ScheduleEngine
import ru.aensidhe.dreamclock.core.schedule.StateType
import ru.aensidhe.dreamclock.core.time.ClockLocale
import ru.aensidhe.dreamclock.core.time.colloquialFormatter
import ru.aensidhe.dreamclock.settings.Language
import ru.aensidhe.dreamclock.settings.Settings

data class ClockUiState(
    val digital: String,
    val colloquial: String?,
    val statusText: String?,
    val state: StateType,
)

private val withSeconds = DateTimeFormatter.ofPattern("HH:mm:ss")
private val withoutSeconds = DateTimeFormatter.ofPattern("HH:mm")

fun buildClockUiState(
    now: LocalDateTime,
    settings: Settings,
    schedule: Schedule,
    statusTextFor: (StateType) -> String,
): ClockUiState {
    val active = ScheduleEngine.activeState(now, schedule)
    val digital = now.format(if (settings.showSeconds) withSeconds else withoutSeconds)
    val colloquial = if (settings.showColloquial) {
        colloquialFormatter(settings.clockLocale()).format(now.hour, now.minute)
    } else {
        null
    }
    val status = active.textOverride ?: statusTextFor(active.state)
    return ClockUiState(digital, colloquial, status, active.state)
}

private fun Settings.clockLocale(): ClockLocale =
    when (language) {
        Language.EN -> ClockLocale.EN
        else -> ClockLocale.RU
    }
```
Note: the ticking `ClockViewModel` (a `StateFlow<ClockUiState>` emitting each second via a coroutine) wraps `buildClockUiState`; add it in the same file. Its emission is exercised manually on-device in Task 15, not unit-tested.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*ClockFormattingTest' ktlintCheck detekt`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/ui/ClockViewModel.kt app/src/test/kotlin/ru/aensidhe/dreamclock/ui/ClockFormattingTest.kt
git commit -m "feat: :robot: add clock ui-state builder and view model"
```

---

## Task 13: ClockOverlay and analog-clock slide

**Files:**
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/ClockOverlay.kt`
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/AnalogClockSlide.kt`
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/SlideDeck.kt`

**Interfaces:**
- Produces:
  - `@Composable fun ClockOverlay(ui: ClockUiState, mode: ColorRenderMode)`
  - `@Composable fun AnalogClockSlide(now: LocalDateTime)`
  - `@Composable fun SlideDeck(showAnalog: Boolean, now: LocalDateTime)`

- [ ] **Step 1: Write the analog slide**

Create `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/AnalogClockSlide.kt`:
```kotlin
package ru.aensidhe.dreamclock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import java.time.LocalDateTime
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun AnalogClockSlide(now: LocalDateTime) {
    Canvas(Modifier.fillMaxSize()) {
        val radius = min(size.width, size.height) / 2f * 0.8f
        val center = Offset(size.width / 2f, size.height / 2f)
        fun hand(fraction: Float, length: Float, color: Color, width: Float) {
            val angle = (fraction * 2f * Math.PI - Math.PI / 2f).toFloat()
            drawLine(
                color = color,
                start = center,
                end = Offset(center.x + cos(angle) * radius * length, center.y + sin(angle) * radius * length),
                strokeWidth = width,
            )
        }
        hand((now.hour % 12 + now.minute / 60f) / 12f, 0.5f, Color.White, 12f)
        hand(now.minute / 60f, 0.8f, Color.White, 8f)
        hand(now.second / 60f, 0.9f, Color(0xFFFFB300), 4f)
    }
}
```

- [ ] **Step 2: Write the slide deck**

Create `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/SlideDeck.kt`:
```kotlin
package ru.aensidhe.dreamclock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import java.time.LocalDateTime

@Composable
fun SlideDeck(showAnalog: Boolean, now: LocalDateTime) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (showAnalog) AnalogClockSlide(now)
    }
}
```

- [ ] **Step 3: Write the overlay**

Create `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/ClockOverlay.kt`:
```kotlin
package ru.aensidhe.dreamclock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.aensidhe.dreamclock.ui.colorrender.ColorRenderMode
import ru.aensidhe.dreamclock.ui.colorrender.RenderOverlay

@Composable
fun ClockOverlay(ui: ClockUiState, mode: ColorRenderMode) {
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(ui.digital, color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Bold)
        mode.RenderOverlay(ui.state) { textColor ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ui.colloquial?.let { Text(it, color = textColor, fontSize = 40.sp) }
                ui.statusText?.let { Text(it, color = textColor, fontSize = 32.sp) }
            }
        }
    }
}
```

- [ ] **Step 4: Assemble and lint**

Run: `./gradlew :app:assembleDebug ktlintCheck detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/ui
git commit -m "feat: :robot: add clock overlay, analog slide, and slide deck"
```

---

## Task 14: SettingsActivity

**Files:**
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsActivity.kt` (replace the Task 9 stub)
- Create: `app/src/main/res/values/strings_settings.xml`, `app/src/main/res/values-ru/strings_settings.xml`

**Interfaces:**
- Consumes: `SettingsRepository`, `ColorRenderMode`, `Language`.
- Produces: a Compose settings screen with toggles (colloquial, seconds, analog slide), a language selector, and a colour-render-mode selector, persisted via `SettingsRepository`.

- [ ] **Step 1: Write settings labels**

Create `app/src/main/res/values/strings_settings.xml`:
```xml
<resources>
    <string name="settings_colloquial">Spoken time</string>
    <string name="settings_seconds">Show seconds</string>
    <string name="settings_analog">Analog clock slide</string>
    <string name="settings_language">Language</string>
    <string name="settings_render_mode">Colour style</string>
</resources>
```

Create `app/src/main/res/values-ru/strings_settings.xml`:
```xml
<resources>
    <string name="settings_colloquial">Время прописью</string>
    <string name="settings_seconds">Показывать секунды</string>
    <string name="settings_analog">Слайд с часами</string>
    <string name="settings_language">Язык</string>
    <string name="settings_render_mode">Стиль цвета</string>
</resources>
```

- [ ] **Step 2: Write the activity**

Replace `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsActivity.kt`:
```kotlin
package ru.aensidhe.dreamclock.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = SettingsRepository.from(this)
        setContent {
            MaterialTheme {
                val settings by repo.settings.collectAsStateWithLifecycle(
                    initialValue = SettingsSerializer.defaultValue,
                )
                Column(Modifier.padding(32.dp)) {
                    ToggleRow(getString(R.string.settings_colloquial), settings.showColloquial) { on ->
                        lifecycleScope.launch { repo.update { it.toBuilder().setShowColloquial(on).build() } }
                    }
                    ToggleRow(getString(R.string.settings_seconds), settings.showSeconds) { on ->
                        lifecycleScope.launch { repo.update { it.toBuilder().setShowSeconds(on).build() } }
                    }
                    ToggleRow(getString(R.string.settings_analog), settings.showAnalogSlide) { on ->
                        lifecycleScope.launch { repo.update { it.toBuilder().setShowAnalogSlide(on).build() } }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(label, Modifier.padding(end = 16.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
```
Note: language and colour-render-mode selectors follow the same `repo.update` pattern with a segmented/radio control; add them below the toggles.

- [ ] **Step 3: Assemble and lint**

Run: `./gradlew :app:assembleDebug ktlintCheck detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app
git commit -m "feat: :robot: add settings activity"
```

---

## Task 15: Wire the DreamService and verify on-device

**Files:**
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/dream/TvDreamService.kt`
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/DreamRoot.kt`
- Create: `app/src/main/res/values/status_texts.xml`, `app/src/main/res/values-ru/status_texts.xml`

**Interfaces:**
- Consumes: `SlideDeck`, `ClockOverlay`, `ClockViewModel`, `SettingsRepository`.
- Produces: a running screensaver showing the slide deck with the live clock overlay.

- [ ] **Step 1: Write per-state status texts**

Create `app/src/main/res/values/status_texts.xml`:
```xml
<resources>
    <string name="status_play"></string>
    <string name="status_prepare">time to get ready for bed</string>
    <string name="status_sleep">time to sleep</string>
</resources>
```

Create `app/src/main/res/values-ru/status_texts.xml`:
```xml
<resources>
    <string name="status_play"></string>
    <string name="status_prepare">пора готовиться ко сну</string>
    <string name="status_sleep">пора спать</string>
</resources>
```

- [ ] **Step 2: Write the composable root**

Create `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/DreamRoot.kt`:
```kotlin
package ru.aensidhe.dreamclock.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import ru.aensidhe.dreamclock.ui.colorrender.ColorRenderMode

@Composable
fun DreamRoot(state: ClockUiState, showAnalog: Boolean, mode: ColorRenderMode) {
    Box(Modifier.fillMaxSize()) {
        SlideDeck(showAnalog = showAnalog, now = java.time.LocalDateTime.now())
        ClockOverlay(ui = state, mode = mode)
    }
}
```

- [ ] **Step 3: Host Compose in the DreamService**

Replace `TvDreamService.kt` to host a `ComposeView` collecting `ClockViewModel` and `SettingsRepository`, rendering `DreamRoot`. (Use `setContentView(ComposeView(this).apply { setContent { … } })`, and a `savedStateRegistry`/lifecycle owner for the DreamService as required by Compose.)

- [ ] **Step 4: Assemble and run the full check**

Run: `./gradlew ktlintCheck detekt test assembleDebug`
Expected: BUILD SUCCESSFUL; all `:core` and `:app` unit tests green.

- [ ] **Step 5: Install and verify on a device**

Run: `./gradlew :app:installDebug` then start the dream:
```bash
adb shell am start -n ru.aensidhe.dreamclock/.settings.SettingsActivity
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME  # then trigger screensaver
```
Verify manually on the Shield or Xiaomi Stick:
- The digital clock ticks each second; toggling seconds off drops them.
- Colloquial line matches expectations (spot-check a "без четверти" and a "half past").
- Colours change across PLAY/PREPARE/SLEEP; each render mode looks as intended.
- The analog slide shows and hides with its toggle.
- Switching language flips both the digital locale intent and the colloquial language.

- [ ] **Step 6: Commit**

```bash
git add app
git commit -m "feat: :robot: wire dream service to slide deck and clock overlay"
```

---

## Self-Review

- Spec coverage: dual clock (Tasks 5,6,12), three states + colours (Tasks 7,11), colloquial rules incl. declension (Tasks 4,5,6), analog slide (Task 13), colour render modes (Task 11), schedule model with layered resolution (Tasks 7,8), settings surface incl. language switch (Tasks 10,14), localized labels (Task 9), DreamService (Tasks 9,15), CI + lint + TDD (Tasks 1,2). Features 2 and 3 remain deferred, as specified.
- Type consistency: `ColloquialTimeFormatter.format(hour, minute)`, `ScheduleEngine.activeState(now, schedule)`, `buildClockUiState(...)`, `ColorRenderMode`, and `Settings` fields are used consistently across tasks.
- Open items carried from the spec: the winning colour render mode is chosen after Task 15's on-device comparison; nothing else blocks.
