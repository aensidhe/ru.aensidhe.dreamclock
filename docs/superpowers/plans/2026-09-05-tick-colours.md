# Tick Colours Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Colour the 60 minute ticks of the analog face by the schedule — each tick shows the state of that minute of the current hour, ticks 56–59 carry a growing share of the coming hour's colour, and the whole ring snaps at hh:00.

**Architecture:** A pure `:core` function `ScheduleEngine.minuteStates` maps (now, schedule) to 60 per-minute states. A plain `:app` function `tickStyles` calls it for the current hour and the next, maps states through the existing `stateColor`, and builds 60 `TickStyle` entries (solid for 0–55, duo with fractions 1/5..4/5 for 56–59). `AnalogClockSlide.drawTicks` renders each tick as one or two pure-colour segments. `DreamRoot` computes the list, memoised per displayed hour, and plumbs it through `SlideDeck`.

**Tech Stack:** Kotlin 2.4.0, Jetpack Compose, JUnit 5 + kotlin.test, Gradle `./gradlew verify`.

**Spec:** `docs/superpowers/specs/2026-08-31-tick-colours-design.md` — read it before starting; it defines the semantics and the settled hour-boundary transition.

## Global Constraints

- Branch: work on the existing `tick-colours` branch in this worktree. Do not create worktrees or new branches.
- Canonical gate: `./gradlew verify` (ktlint, detekt, all unit tests, assemble). Run from the repo root.
- Commits: Conventional Commits with `:robot:` after the type, e.g. `feat: :robot: add minuteStates`. No Co-Authored-By, no attribution trailers of any kind.
- Stage files by name. Never stage or touch `.serena/project.yml` (pre-existing local drift stays uncommitted).
- Serena's Kotlin language server is down this session; built-in Read/Edit are the authorized fallback for Kotlin files.
- Shell: plain single-purpose commands; no `$(...)`, no heredocs, no multi-line strings. Commit messages via one or more `-m` flags.
- Markdown files: no bold/italic inline emphasis in prose.
- No adb, no installDebug — the TV is offline; on-device validation happens later by manual APK sideload.
- Colour constants (from `StateColors.kt`): PLAY = 0xFF7CB342, PREPARE = 0xFFFFB300, SLEEP = 0xFF5E35B1.

---

### Task 1: `ScheduleEngine.minuteStates` in `:core`

**Files:**
- Modify: `core/src/main/kotlin/ru/aensidhe/dreamclock/core/schedule/ScheduleEngine.kt`
- Test: `core/src/test/kotlin/ru/aensidhe/dreamclock/core/schedule/MinuteStatesTest.kt` (create)

**Interfaces:**
- Consumes: existing `ScheduleEngine.activeState(now: LocalDateTime, schedule: Schedule): ActiveState`, `Schedule`, `DaySchedule`, `Window`, `StateType`.
- Produces: `ScheduleEngine.minuteStates(now: LocalDateTime, schedule: Schedule): List<StateType>` — exactly 60 entries, entry i = state of minute i of the hour containing `now`. Task 2 calls this.

- [ ] **Step 1: Write the failing tests**

Create `core/src/test/kotlin/ru/aensidhe/dreamclock/core/schedule/MinuteStatesTest.kt`:

```kotlin
package ru.aensidhe.dreamclock.core.schedule

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class MinuteStatesTest {
    private val default =
        DaySchedule(
            listOf(
                Window(LocalTime.MIDNIGHT, StateType.SLEEP),
                Window(LocalTime.of(6, 0), StateType.PLAY),
                Window(LocalTime.of(20, 0), StateType.PREPARE),
                Window(LocalTime.of(21, 0), StateType.SLEEP),
            ),
        )

    private fun at(
        h: Int,
        mi: Int,
    ) = LocalDateTime.of(2026, 9, 5, h, mi)

    @Test
    fun `always returns 60 entries`() {
        val s = Schedule(default)
        assertEquals(60, ScheduleEngine.minuteStates(at(0, 0), s).size)
        assertEquals(60, ScheduleEngine.minuteStates(at(23, 59), s).size)
    }

    @Test
    fun `uniform hour returns 60 identical states`() {
        val s = Schedule(default)
        val states = ScheduleEngine.minuteStates(at(10, 17), s)
        assertTrue(states.all { it == StateType.PLAY })
    }

    @Test
    fun `mid-hour window edge splits at its minute`() {
        val day =
            DaySchedule(
                listOf(
                    Window(LocalTime.MIDNIGHT, StateType.SLEEP),
                    Window(LocalTime.of(20, 30), StateType.PREPARE),
                ),
            )
        val states = ScheduleEngine.minuteStates(at(20, 5), Schedule(day))
        assertTrue(states.subList(0, 30).all { it == StateType.SLEEP })
        assertTrue(states.subList(30, 60).all { it == StateType.PREPARE })
    }

    @Test
    fun `date override day resolves through the override`() {
        val holiday = DaySchedule(listOf(Window(LocalTime.MIDNIGHT, StateType.PLAY)))
        val s = Schedule(default, overrides = mapOf(LocalDate.of(2026, 9, 5) to holiday))
        val states = ScheduleEngine.minuteStates(at(3, 0), s)
        assertTrue(states.all { it == StateType.PLAY })
    }

    @Test
    fun `plusHours across midnight resolves through the next day's override`() {
        val holiday = DaySchedule(listOf(Window(LocalTime.MIDNIGHT, StateType.PLAY)))
        val s = Schedule(default, overrides = mapOf(LocalDate.of(2026, 9, 6) to holiday))
        val states = ScheduleEngine.minuteStates(at(23, 30).plusHours(1), s)
        assertTrue(states.all { it == StateType.PLAY })
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "ru.aensidhe.dreamclock.core.schedule.MinuteStatesTest"`
Expected: FAIL to compile — `minuteStates` unresolved.

- [ ] **Step 3: Implement `minuteStates`**

In `core/src/main/kotlin/ru/aensidhe/dreamclock/core/schedule/ScheduleEngine.kt`, add inside `object ScheduleEngine` (after `activeState`):

```kotlin
    fun minuteStates(
        now: LocalDateTime,
        schedule: Schedule,
    ): List<StateType> = List(MINUTES_PER_HOUR) { minute -> activeState(now.withMinute(minute), schedule).state }

    private const val MINUTES_PER_HOUR = 60
```

(`const val` is legal inside an `object`; no companion needed.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "ru.aensidhe.dreamclock.core.schedule.MinuteStatesTest"`
Expected: 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/ru/aensidhe/dreamclock/core/schedule/ScheduleEngine.kt core/src/test/kotlin/ru/aensidhe/dreamclock/core/schedule/MinuteStatesTest.kt
git commit -m "feat: :robot: add ScheduleEngine.minuteStates for the tick ring"
```

---

### Task 2: `TickStyle` and `tickStyles` in `:app`

**Files:**
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/TickStyles.kt`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/ui/TickStylesTest.kt` (create)

**Interfaces:**
- Consumes: `ScheduleEngine.minuteStates(now, schedule): List<StateType>` (Task 1); existing `stateColor(state: StateType): Color` from `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/StateColors.kt`.
- Produces: `data class TickStyle(val base: Color, val incoming: Color, val incomingFraction: Float)` and `fun tickStyles(now: LocalDateTime, schedule: Schedule): List<TickStyle>` (size 60). Task 3 consumes both.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/kotlin/ru/aensidhe/dreamclock/ui/TickStylesTest.kt`:

```kotlin
package ru.aensidhe.dreamclock.ui

import androidx.compose.ui.graphics.Color
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import ru.aensidhe.dreamclock.core.schedule.DaySchedule
import ru.aensidhe.dreamclock.core.schedule.Schedule
import ru.aensidhe.dreamclock.core.schedule.StateType
import ru.aensidhe.dreamclock.core.schedule.Window

class TickStylesTest {
    private val play = Color(0xFF7CB342)
    private val prepare = Color(0xFFFFB300)

    private val schedule =
        Schedule(
            DaySchedule(
                listOf(
                    Window(LocalTime.MIDNIGHT, StateType.SLEEP),
                    Window(LocalTime.of(6, 0), StateType.PLAY),
                    Window(LocalTime.of(20, 0), StateType.PREPARE),
                    Window(LocalTime.of(21, 0), StateType.SLEEP),
                ),
            ),
        )

    private fun at(
        h: Int,
        mi: Int,
    ) = LocalDateTime.of(2026, 9, 5, h, mi)

    @Test
    fun `returns 60 entries`() {
        assertEquals(60, tickStyles(at(10, 0), schedule).size)
    }

    @Test
    fun `ticks 0-55 are solid in the current hour's colour`() {
        val styles = tickStyles(at(19, 10), schedule)
        for (i in 0..55) {
            assertEquals(play, styles[i].base)
            assertEquals(0f, styles[i].incomingFraction)
        }
    }

    @Test
    fun `tail ticks carry the next hour's colour with growing fractions`() {
        val styles = tickStyles(at(19, 10), schedule)
        val fractions = listOf(0.2f, 0.4f, 0.6f, 0.8f)
        for (i in 56..59) {
            assertEquals(play, styles[i].base)
            assertEquals(prepare, styles[i].incoming)
            assertEquals(fractions[i - 56], styles[i].incomingFraction)
        }
    }

    @Test
    fun `same-state hours make the tail invisible`() {
        val styles = tickStyles(at(10, 0), schedule)
        for (i in 56..59) {
            assertEquals(styles[i].base, styles[i].incoming)
        }
    }

    @Test
    fun `mid-hour edge splits the base colours at its minute`() {
        val styles = tickStyles(at(20, 45), schedule)
        assertTrue((0..55).all { styles[it].base == prepare })
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.aensidhe.dreamclock.ui.TickStylesTest"`
Expected: FAIL to compile — `TickStyle`/`tickStyles` unresolved.

- [ ] **Step 3: Implement `TickStyles.kt`**

Create `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/TickStyles.kt`:

```kotlin
package ru.aensidhe.dreamclock.ui

import androidx.compose.ui.graphics.Color
import java.time.LocalDateTime
import ru.aensidhe.dreamclock.core.schedule.Schedule
import ru.aensidhe.dreamclock.core.schedule.ScheduleEngine

/**
 * One tick's paint: [base] fills the tick; [incoming] takes over the outer [incomingFraction]
 * of its length, announcing the coming hour on the last four ticks (zero elsewhere).
 */
data class TickStyle(
    val base: Color,
    val incoming: Color,
    val incomingFraction: Float,
)

private const val TICK_COUNT = 60
private const val TAIL_START = 56
private const val TAIL_SPAN = 5f

fun tickStyles(
    now: LocalDateTime,
    schedule: Schedule,
): List<TickStyle> {
    val current = ScheduleEngine.minuteStates(now, schedule).map(::stateColor)
    val next = ScheduleEngine.minuteStates(now.plusHours(1), schedule).map(::stateColor)
    return List(TICK_COUNT) { i ->
        if (i >= TAIL_START) {
            TickStyle(current[i], next[i], (i - TAIL_START + 1) / TAIL_SPAN)
        } else {
            TickStyle(current[i], current[i], 0f)
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.aensidhe.dreamclock.ui.TickStylesTest"`
Expected: 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/ui/TickStyles.kt app/src/test/kotlin/ru/aensidhe/dreamclock/ui/TickStylesTest.kt
git commit -m "feat: :robot: map minute states to per-tick styles with the duo-tail"
```

---

### Task 3: Render and wire the coloured ring

**Files:**
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/AnalogClockSlide.kt`
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/SlideDeck.kt`
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/DreamRoot.kt`
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/ui/ClockViewModel.kt`
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamContent.kt`

**Interfaces:**
- Consumes: `TickStyle`, `tickStyles(now, schedule)` (Task 2).
- Produces: `AnalogClockSlide(now: LocalDateTime, secondHandColor: Color, tickStyles: List<TickStyle>)`; `SlideDeck` gains the same `tickStyles: List<TickStyle>` parameter; `DreamRoot` gains `schedule: Schedule`; `ClockViewModel.schedule` becomes a public `val`.

No new unit tests (Compose plumbing; the assembly logic was tested in Task 2). The gate is a green `./gradlew verify`.

- [ ] **Step 1: Rework `AnalogClockSlide.kt`**

Remove the `tickColor` file-level constant (line 24). Change the composable signature and the `drawTicks` call:

```kotlin
@Composable
fun AnalogClockSlide(
    now: LocalDateTime,
    secondHandColor: Color,
    tickStyles: List<TickStyle>,
) {
```

and inside the Canvas block: `drawTicks(center, radius, tickStyles)`.

Replace `drawTicks` entirely with:

```kotlin
private fun DrawScope.drawTicks(
    center: Offset,
    radius: Float,
    tickStyles: List<TickStyle>,
) {
    for (i in 0 until 60) {
        val angle = (i / 60f * 2f * Math.PI - Math.PI / 2f).toFloat()
        val hour = i % 5 == 0
        val inner = radius - if (hour) radius * 0.09f else radius * MINUTE_TICK_FRACTION
        val width = if (hour) max(3f, radius * 0.014f) else max(1.5f, radius * 0.007f)
        val style = tickStyles[i]
        val split = radius - (radius - inner) * style.incomingFraction

        fun segment(
            from: Float,
            to: Float,
            color: Color,
        ) {
            drawLine(
                color = color,
                start = Offset(center.x + cos(angle) * from, center.y + sin(angle) * from),
                end = Offset(center.x + cos(angle) * to, center.y + sin(angle) * to),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }
        segment(inner, split, style.base)
        if (style.incomingFraction > 0f) {
            segment(split, radius, style.incoming)
        }
    }
}
```

The incoming segment is drawn second so its round cap covers the joint; the outer segment carries the coming hour's colour, per the spec (split direction is an on-device call — this is the starting choice).

- [ ] **Step 2: Plumb through `SlideDeck.kt`**

Add `tickStyles: List<TickStyle>` to the `SlideDeck` parameter list (after `secondHandColor`), and pass it at both `AnalogClockSlide` call sites (lines 44 and 77):

```kotlin
if (showAnalog) AnalogClockSlide(now, secondHandColor, tickStyles)
```

- [ ] **Step 3: Expose the schedule from `ClockViewModel.kt`**

Change the constructor property `private val schedule: Schedule` to `val schedule: Schedule` (line 57).

- [ ] **Step 4: Compute the list in `DreamRoot.kt`**

Add parameters and the memoised computation. The full reworked `DreamRoot`:

```kotlin
@Composable
fun DreamRoot(
    state: ClockUiState,
    schedule: Schedule,
    showAnalog: Boolean,
    deck: SlideDeckModel?,
    imageLoader: ImageLoader?,
    everyXthMinute: Int,
    photoSeconds: Int,
    analogSeconds: Int,
) {
    var suppressBottomLeft by remember { mutableStateOf(false) }
    val now = LocalDateTime.now()
    val ticks = remember(now.truncatedTo(ChronoUnit.HOURS), schedule) { tickStyles(now, schedule) }
    Box(Modifier.fillMaxSize()) {
        SlideDeck(
            deck = deck,
            imageLoader = imageLoader,
            showAnalog = showAnalog,
            now = now,
            secondHandColor = stateColor(state.state),
            tickStyles = ticks,
            everyXthMinute = everyXthMinute,
            photoSeconds = photoSeconds,
            analogSeconds = analogSeconds,
            onSuppressBottomLeft = { suppressBottomLeft = it },
        )
        ClockOverlay(ui = state, suppressBottomLeft = suppressBottomLeft)
    }
}
```

Add imports: `java.time.LocalDateTime`, `java.time.temporal.ChronoUnit`, `ru.aensidhe.dreamclock.core.schedule.Schedule`. The `remember` key `now.truncatedTo(ChronoUnit.HOURS)` recomputes the 120 `activeState` calls only when the displayed hour (or date) changes, per the spec; per-second recompositions reuse the remembered list.

- [ ] **Step 5: Pass the schedule in `DreamContent.kt`**

In the `DreamRoot(...)` call (around line 142), add `schedule = viewModel.schedule,` after `state = uiState,`.

- [ ] **Step 6: Run the full gate**

Run: `./gradlew verify`
Expected: BUILD SUCCESSFUL — ktlint, detekt, all unit tests, assemble, both modules.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/ui/AnalogClockSlide.kt app/src/main/kotlin/ru/aensidhe/dreamclock/ui/SlideDeck.kt app/src/main/kotlin/ru/aensidhe/dreamclock/ui/DreamRoot.kt app/src/main/kotlin/ru/aensidhe/dreamclock/ui/ClockViewModel.kt app/src/main/kotlin/ru/aensidhe/dreamclock/dream/DreamContent.kt
git commit -m "feat: :robot: colour the analog ticks by schedule with a duo-tail"
```

---

### Task 4: Reword feature 4 in README.md and CLAUDE.md

**Files:**
- Modify: `README.md` (feature list item 4, lines 29–31; Status section, lines 41–46)
- Modify: `CLAUDE.md` (Build order item 4; Architecture `stateColor` bullet; Current status paragraph)

**Interfaces:** none — documentation only. The mirror rule in CLAUDE.md requires the feature list and both status sections to change in the same commit.

- [ ] **Step 1: Reword README feature 4 and status**

Replace the item 4 block:

```markdown
4. Tick colours (built) — the minute ticks show the current hour: each tick
   takes the colour of the state the schedule assigns to that minute, in sync
   with the status text, and the last four ticks carry a growing share of the
   coming hour's colour before the ring turns over on the hour.
```

In the Status section, replace the sentence `Features 4–6 are planned.` with:

```markdown
Feature 4's schedule-coloured tick ring is built, pending on-device
validation. Features 5–6 are planned.
```

- [ ] **Step 2: Reword CLAUDE.md feature 4, architecture, and status**

Replace the Build order item 4 block:

```markdown
4. Tick colours (built) — the ticks are the minutes of the current hour, each
   coloured by the state the schedule assigns to that minute; ticks 56–59
   carry a growing share of the coming hour's colour and the ring snaps at
   hh:00
```

Replace the `stateColor` architecture bullet (which still claims the face stays neutral):

```markdown
- `stateColor` — maps the active state to its colour; it tints the overlay
  status/colloquial text, the second hand, and the minute-tick ring.
```

In the Current status paragraph, replace `Features 4–6 are not yet built.` with:

```markdown
Feature 4 (tick colours) is built, pending on-device validation. Features 5–6
are not yet built.
```

Exact current wording may differ slightly — match the sentences that name features 4–6 and preserve surrounding text. No bold/italic emphasis anywhere.

- [ ] **Step 3: Commit**

```bash
git add README.md CLAUDE.md
git commit -m "docs: :robot: reword feature 4 to the minutes-of-current-hour ring"
```

---

### Task 5: Final gate and sideload APK

**Files:** none modified.

**Interfaces:** none.

- [ ] **Step 1: Run the full gate once more on the completed branch**

Run: `./gradlew verify`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Build the debug APK for manual sideload**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL; APK at `app/build/outputs/apk/debug/app-debug.apk`. Report that path — the user carries it to the TV with LocalSend; there is no adb.

- [ ] **Step 3: Confirm the branch is clean**

Run: `git status`
Expected: only `.serena/project.yml` modified (pre-existing drift; leave it), nothing else uncommitted.

Integration (push, PR, CI, `git merge --ff-only` to main) follows the project workflow via superpowers:finishing-a-development-branch after review — it is not part of this task.
