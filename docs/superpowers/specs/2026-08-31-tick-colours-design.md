# Feature 4 — Tick colours design

Status: draft — the hour-boundary transition is an open question; everything
else is agreed. Not ready for implementation planning.
Date: 2026-08-31.

## Goal

Colour the 60 minute ticks of the analog face by the schedule, so the ring
around the dial reads as the current state and quietly announces the next
one. The ticks are the minutes of the current hour: tick i carries the
colour of the state the schedule assigns to minute i of the hour now
showing. How the ring transitions at the hour boundary is still open — see
Open question below; a sudden whole-ring change is ruled out.

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
- The hour-boundary transition is undecided (see Open question).

## Semantics

Let H be the current hour of the local date-time now. Tick i (i = 0..59,
tick 0 at 12 o'clock) shows the state of minute i of hour H, resolved
through the schedule's date resolution for today (overrides, then
day-of-week, then default), exactly as `ScheduleEngine.activeState`
resolves it. The mapping changes only when the displayed hour changes;
how that change is presented is the open question below.

Consequences:

- Uniform hours give a single-colour ring equal to the status text colour;
  transitions between identical colours are invisible.
- A window starting mid-hour (the model allows any LocalTime) splits the
  ring at that minute for the whole hour.
- Hour ticks are minutes 0, 5, 10, … and colour by the same rule; their
  length and width emphasis is unchanged.

## Open question — the hour-boundary transition

An instant whole-ring repaint at hh:00 is rejected as too sudden.
Candidates considered and rejected so far:

- Rolling re-mapping (tick i shows the next time the minute hand reaches
  it): rejected — most of the dial describes the coming hour even early in
  the current one.
- Fixed two-minute preview (at :58, ticks behind the hand repaint to the
  coming hour): rejected — the activation at :58 is itself a mass flip.
- Sweep-wash animation at hh:00 (new colours wipe clockwise over ~a
  minute): rejected.
- Crossfade at hh:00 and a gradient ramp over the last minutes: rejected —
  both pass through hues that match no state.
- Growing wash-in preview (a front sweeps from 12 through the spent ticks
  over the last 10 minutes, one tick per 10 s, complete at hh:00):
  rejected.

Re-mapping and previews are not ruled out as concepts; the rejections are
of these concrete behaviours. The feature does not proceed to planning
until this is settled.

## Pure logic (`:core`, package `schedule`)

`ScheduleEngine` gains:

    fun minuteStates(
        now: LocalDateTime,
        schedule: Schedule,
    ): List<StateType>

Returns exactly 60 entries, index i per tick i, computed by delegating to
`activeState` at the date-time each tick stands for (now with the minute
replaced). Sixty `activeState` calls once an hour is negligible. The
signature may grow once the transition behaviour is settled.

## UI (`:app`)

- `AnalogClockSlide` takes a `tickColors: List<Color>` parameter (size 60)
  and `drawTicks` uses `tickColors[i]` instead of the shared `tickColor`
  constant, which is removed.
- The caller computes `ScheduleEngine.minuteStates(now, schedule)`, maps
  each state through the existing `stateColor`, and recomputes only when
  the displayed minute changes, not on the per-second updates that drive
  the hands.
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
  - the list always has 60 entries.
  Transition-specific cases follow once the transition is settled.
- `:app`: no new unit tests; the change is a parameter plumbed through
  Compose code.
- On-device: the ring matches the status text colour during a steady hour;
  the settled transition behaviour is observed around 20:00 and 21:00
  with no sudden whole-ring change.

## Risks

- Any transition is invisible between same-state hours, so on-device
  validation must be timed around 20:00 or 21:00; a wrong transition rule
  could otherwise go unnoticed for days.
- The second hand and the ring now both encode the current state; if the
  doubled signal looks noisy on the TV, dropping the second-hand tint is a
  one-line follow-up, deliberately not part of this feature.
