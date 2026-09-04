# Feature 4 — Tick colours design

Status: complete — the hour-boundary transition is settled (duo-tail plus
snap, below); approved and implemented, pending on-device validation.
Date: 2026-08-31, transition settled 2026-09-05.

## Goal

Colour the 60 minute ticks of the analog face by the schedule, so the ring
around the dial reads as the current state and quietly announces the next
one. The ticks are the minutes of the current hour: tick i carries the
colour of the state the schedule assigns to minute i of the hour now
showing. The last four ticks carry a duo-coloured tail announcing the
coming hour, and the whole ring snaps to the new hour exactly as the
minute hand crosses 12 — a telegraphed jumping-hour event, not an
unannounced flip.

## Scope

In scope:

- A pure `:core` function mapping (now, schedule) to 60 per-minute states.
- Colouring the ticks in `AnalogClockSlide` from those states.
- Rewording feature 4 in README.md and CLAUDE.md to this meaning (the
  earlier wording promised the whole day around a 12-hour dial).

Out of scope:

- Numeral colours: the 1–12 stay neutral white.
- Hands: the hour and minute hands stay white; the second hand keeps its
  existing current-state tint, now redundant with the ring, left as is.
- Any schedule editing (feature 5) or settings changes. The feature has no
  toggle.

## Decisions

Reached in conversation, later choices overriding earlier ones:

- Ticks mean minutes of the current hour, not hours of the day. This
  replaced the original 12-hour reading, whose AM/PM ambiguity had driven
  the early questions.
- Fixed hour, not a rolling next-60-minutes window: at 19:30 every tick
  shows 19:xx, so the ring normally matches the status text colour and the
  tick under the minute hand always does.
- Numerals stay white; the coloured ring does the signalling.
- Hour-boundary transition: a static duo-coloured tail on ticks 56–59 plus
  an instant whole-ring snap at hh:00. The tail is part of the hour's
  fixed mapping — always on, no trigger moment, no mid-hour repaint — so
  the snap arrives announced, and it fires exactly as the minute hand
  crosses 12, reading as the hour turning (the jumping-hour reading).
- Duo ticks are split into two pure-state-colour segments, never blended
  hues: earlier crossfade and gradient-ramp candidates died precisely
  because interpolated colours match no state.

## Semantics

Let H be the current hour of the local date-time now. Tick i (i = 0..59,
tick 0 at 12 o'clock) shows the state of minute i of hour H, resolved
through the schedule's date resolution for today (overrides, then
day-of-week, then default), exactly as `ScheduleEngine.activeState`
resolves it. The mapping changes only when the displayed hour changes —
instantly, at hh:00.

The duo-tail: ticks 56–59 each carry a second colour, the state of the
same minute i of hour H+1 (resolved for the date-time an hour ahead, so
the midnight tail correctly previews tomorrow's overrides and
day-of-week). Each duo tick is drawn as two pure-colour segments along
its length: the outer segment shows the coming hour's colour and its
share grows across the tail — 1/5 at tick 56, 2/5 at 57, 3/5 at 58, 4/5
at 59. Tick 55 stays solid; tick 59 never fully arrives — the hh:00 snap
completes it. The exact split direction (outer versus inner segment for
the coming colour) is a rendering choice validated on-device.

Consequences:

- Uniform hours give a single-colour ring equal to the status text colour;
  transitions between identical colours are invisible, and a duo tick
  whose two states match renders solid with no special-casing.
- A window starting mid-hour (the model allows any LocalTime) splits the
  ring at that minute for the whole hour.
- Hour ticks are minutes 0, 5, 10, … and colour by the same rule; their
  length and width emphasis is unchanged. The tail indices 56–59 are all
  short minute ticks, so a long hour tick is never split.

## Rejected transition candidates (history)

Kept so the reasoning is not re-litigated:

- Instant whole-ring repaint with no announcement: too sudden.
- Rolling re-mapping (tick i shows the next time the minute hand reaches
  it): most of the dial describes the coming hour even early in the
  current one.
- Fixed two-minute preview (at :58, ticks behind the hand repaint to the
  coming hour): the activation at :58 is itself a mass flip.
- Sweep-wash animation at hh:00 (new colours wipe clockwise over ~a
  minute).
- Crossfade at hh:00 and a gradient ramp over the last minutes: both pass
  through hues that match no state.
- Growing wash-in preview (a front sweeps from 12 through the spent ticks
  over the last 10 minutes, one tick per 10 s, complete at hh:00).

The settled duo-tail escapes these because it is static (part of the
hour's mapping, no trigger moment), uses only pure state hues, and turns
the hh:00 repaint into an event announced for the whole preceding hour.

## Pure logic (`:core`, package `schedule`)

`ScheduleEngine` gains:

    fun minuteStates(
        now: LocalDateTime,
        schedule: Schedule,
    ): List<StateType>

Returns exactly 60 entries, index i per tick i, computed by delegating to
`activeState` at the date-time each tick stands for (now with the minute
replaced). Sixty `activeState` calls once an hour is negligible.

The signature does not grow for the duo-tail: the caller invokes it
twice, at `now` and at `now.plusHours(1)`, and pairs the last four
entries. Date resolution for the next hour (midnight rollover into a new
day-of-week or override) falls out of `activeState` for free.

## UI (`:app`)

- `AnalogClockSlide` takes a per-tick colour list (size 60) where each
  entry is either a solid colour or a pair with a split fraction; the
  shared `tickColor` constant is removed. `drawTicks` draws a solid tick
  as today, and a duo tick as two collinear segments along the tick's
  length, split at the given fraction, each in a pure state colour.
- The caller computes `ScheduleEngine.minuteStates` for `now` and
  `now.plusHours(1)`, maps states through the existing `stateColor`,
  builds solids for ticks 0–55 and pairs with fractions 1/5..4/5 for
  ticks 56–59, and recomputes only when the displayed hour changes, not
  on the per-second updates that drive the hands.
- `ClockOverlay`, `stateColor`, and the schedule wiring are unchanged.

## Docs

README.md feature 4 and CLAUDE.md feature 4 are reworded to the
minutes-of-the-current-hour meaning, and both status sections flip to
built, in the same commit, per the mirror rule in CLAUDE.md.

## Testing

- `:core`, TDD with case tables (new `MinuteStatesTest`):
  - a uniform hour returns 60 identical states;
  - an hour containing a mid-hour window edge splits at the right index;
  - a date-override day resolves through the override;
  - the list always has 60 entries;
  - called at 23:xx of a day before an override (or a day-of-week
    change), `minuteStates(now.plusHours(1))` resolves through the next
    day's schedule.
- `:app`: no new unit tests for the Compose plumbing; if the
  tick-colour-list assembly (solids plus tail pairs) lands in a plain
  function, a small unit test covers the tail indices and fractions.
- On-device: the ring matches the status text colour during a steady
  hour; around 19:56–20:00 the duo-tail appears green-over-amber and the
  ring snaps to amber exactly as the minute hand crosses 12; same around
  21:00 for amber-to-purple.

## Risks

- The transition is invisible between same-state hours, so on-device
  validation must be timed around 20:00 or 21:00; a wrong tail or snap
  could otherwise go unnoticed for days.
- The duo split lives inside short minute ticks, small at TV distance;
  if the two segments are illegible the fractions or tick sizing may
  need on-device tuning (the split direction is likewise settled there).
- The second hand and the ring now both encode the current state; if the
  doubled signal looks noisy on the TV, dropping the second-hand tint is a
  one-line follow-up, deliberately not part of this feature.
