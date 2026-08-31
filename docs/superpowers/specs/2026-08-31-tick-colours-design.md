# Feature 4 — Tick colours design

Status: approved design, ready for implementation planning.
Date: 2026-08-31.

## Goal

Colour the 60 minute ticks of the analog face by the schedule, so the ring
around the dial reads as the current state and quietly announces the next
one. The ticks are the minutes of the current hour: tick i carries the
colour of the state the schedule assigns to minute i of the hour now
showing. In the last two minutes of an hour the coming hour floods in
behind the minute hand, so a state change never lands as a whole-ring snap.

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
- Two-minute preview instead of a snap or a crossfade: during minutes 58
  and 59, ticks the minute hand has passed repaint to the coming hour.
- Numerals stay white; the coloured ring does the signalling.

## Semantics

Let m be the current minute (0–59) and H the current hour of the local
date-time now. Tick i (i = 0..59, tick 0 at 12 o'clock) shows the state of:

- minute i of hour H, when m <= 57;
- minute i of hour H+1, when m >= 58 and i < m (the preview: ticks strictly
  behind the minute hand);
- minute i of hour H, when m >= 58 and i >= m.

Hour H+1 rolls into the next day after 23:xx; the schedule's date
resolution (overrides, then day-of-week, then default) applies to the
rolled date, exactly as `ScheduleEngine.activeState` already resolves it.
Seconds never affect tick colours; the mapping changes only when the
displayed minute changes.

Consequences:

- Uniform hours give a single-colour ring equal to the status text colour;
  transitions between identical colours are invisible.
- Before 20:00 and 21:00 (prepare, sleep in the default schedule) the new
  colour sweeps in behind the hand during 19:58–19:59 and 20:58–20:59; at
  hh:00 at most the last two ticks still change.
- A window starting mid-hour (the model allows any LocalTime) splits the
  ring at that minute for the whole hour.
- Hour ticks are minutes 0, 5, 10, … and colour by the same rule; their
  length and width emphasis is unchanged.

## Pure logic (`:core`, package `schedule`)

`ScheduleEngine` gains:

    fun minuteStates(
        now: LocalDateTime,
        schedule: Schedule,
    ): List<StateType>

Returns exactly 60 entries, index i per tick i, computed by delegating to
`activeState` at the date-time each tick stands for (now with the minute
replaced, plus one hour for preview ticks). Sixty `activeState` calls once
a minute is negligible.

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
  - m = 57 shows no preview; m = 58 and m = 59 preview exactly the ticks
    behind the hand;
  - 23:58 previews into the next day and honours a date override for that
    day;
  - the list always has 60 entries.
- `:app`: no new unit tests; the change is a parameter plumbed through
  Compose code.
- On-device: the ring matches the status text colour during a steady hour;
  amber floods in behind the hand at 19:58; purple at 20:58; no visible
  snap at 20:00 or 21:00.

## Risks

- The preview is invisible between same-state hours, so on-device
  validation must be timed around 19:58 or 20:58; a wrong preview rule
  could otherwise go unnoticed for days.
- The second hand and the ring now both encode the current state; if the
  doubled signal looks noisy on the TV, dropping the second-hand tint is a
  one-line follow-up, deliberately not part of this feature.
