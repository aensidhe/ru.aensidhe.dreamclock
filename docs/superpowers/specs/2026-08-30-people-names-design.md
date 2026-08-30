# Feature 3 — People names design

Status: approved design, ready for implementation planning.
Date: 2026-08-30.

## Goal

Caption each photo slide with the names of the people Immich recognised in it,
so a child can read who is in the picture. Names are additive to the existing
caption (date, time, location); a photo with no recognised faces looks exactly
as it does today.

## Scope

In scope:

- Fetching per-asset people alongside the existing metadata search.
- A third caption line listing the named, non-hidden people, joined as a
  natural-language list in the selected locale.
- A settings toggle to hide the names, defaulting to shown.

Out of scope:

- Face-anchored labels drawn beside each face. The caption line was chosen over
  bounding-box labels; boxes are read only to order names left to right.
- Any transformation of the name (first word only, nicknames). Names appear
  exactly as entered in Immich.
- Filtering the photo deck by person.
- Videos, which remain deferred with the rest of Plan 6.

## Decisions

- Placement: a third line in the existing bottom-right caption block.
- Name form: verbatim from Immich.
- Joining: locale-aware natural list — RU `Аня, Боря и Вася`, EN
  `Anna, Boris, and Vasya`. English uses the Oxford comma; Russian puts no
  comma before a single «и».
- Setting: a toggle in the Immich section of Settings.
- Fetch path: `withPeople = true` on the existing bulk search, not a per-slide
  asset fetch. People ride with each asset in the daily load, so the slide
  pipeline gains no extra request or async step.

## Data layer (`:app`, package `immich`)

- `SearchMetadataRequest` gains `withPeople: Boolean = true`.
- New serializable models:
  - `ImmichFace(boundingBoxX1: Int? = null)` — only the left edge is needed,
    to sort names in reading order.
  - `ImmichPerson(id: String, name: String? = null, isHidden: Boolean = false,
    faces: List<ImmichFace> = emptyList())`. `name` is nullable because some
    Immich versions send `null` rather than omitting the key.
- `ImmichAsset` gains `people: List<ImmichPerson> = emptyList()`. The shared
  `immichJson` already ignores unknown keys, so extra fields Immich sends on
  people and faces are harmless.
- `AssetMapper.toSlideAsset` derives `CaptionSource.people`:
  1. keep people with `!isHidden` and a non-null, non-blank `name`;
  2. sort by the smallest `boundingBoxX1` across the person's faces; people
     with no face coordinate sort last, preserving arrival order among
     themselves;
  3. trim names and drop duplicates, keeping the first occurrence.
- `MINT_PERMISSIONS` and the key-permission hint strings are unchanged. Immich
  gates the search endpoint on `asset.read` and maps people straight from the
  asset, so the current keys should already return them. See Risks for the
  fallback.

## Pure logic (`:core`, package `photos`)

- `CaptionSource` gains `people: List<String> = emptyList()`. The default keeps
  every existing call site and test compiling.
- `CaptionLines` gains `people: String?` as the third line.
- New `PeopleList` object with `join(names: List<String>, locale: ClockLocale):
  String?`:
  - empty → `null`;
  - one → the name;
  - two → `A и B` / `A and B`;
  - three or more → all but the last joined with `, `, then the conjunction
    and the last name: RU `А, Б и В`, EN `A, B, and C` (Oxford comma).
- `PhotoCaption.format` fills `people` via `PeopleList.join` and returns `null`
  only when all three lines are absent.

## UI and settings (`:app`)

- Proto: `bool hide_people_names = 17;` in `Settings`. The field is inverted
  deliberately: proto3 booleans default to `false`, so installs that predate
  the field show names with no migration and `SettingsSerializer.defaultValue`
  needs no change.
- `ImmichSection` gets a `ToggleRow` labelled "Show people names" /
  "Показывать имена людей", placed after the last stepper. Its displayed value
  is `!hidePeopleNames`. It takes `downFocus = lastStepperDownFocus` from the
  last stepper so D-pad navigation stays continuous; the stepper's own
  `downFocus` becomes `null`.
- `SlideResolver` gains a `showPeople: Boolean` constructor parameter. When it
  is `false`, the resolver formats `source.copy(people = emptyList())`. The
  construction site passes `!settings.hidePeopleNames`. Fetching is not gated
  by the toggle; names always travel with the asset load so the toggle takes
  effect on the next slide without a refetch.
- `CaptionBlock` draws `lines.people` as a third `Text` at 24 sp; date and
  location stay at 20 sp. The size is a starting point to tune on-device.
- `OverlaySuppression` is untouched: a caption with only a people line is still
  a non-null caption, so the paired-slide overlay rule keeps working.
- New strings in `values/strings_settings.xml` and
  `values-ru/strings_settings.xml`.

## Testing

- `:core`, TDD with case tables:
  - `PeopleListTest` — zero, one, two, and three names, in RU and EN.
  - `PhotoCaptionTest` — a people-only caption, all three lines present, and
    the existing cases still passing with the default empty list.
- `:app`, pragmatic:
  - `ImmichModelsTest` — decode a search fixture containing a named person, an
    unnamed person, a hidden person, and a person without faces.
  - `AssetMapperTest` — filtering, left-to-right ordering, trailing placement of
    people without coordinates, and de-duplication.
  - `SlideResolverTest` — the toggle strips names; on, the third line is
    present.
- On-device: a photo with a recognised, named face shows the name; the toggle
  hides it on the next slide; a photo without faces is unchanged.

## Risks

- Permission: if a valid key returns assets without `people`, Immich's version
  requires `person.read` for that data. Fix: add `person.read` to
  `MINT_PERMISSIONS`, both hint strings, and the spec's permission list, and
  re-pair once. Manual-key users add the permission in Immich.
- Response size: `withPeople` adds a few hundred bytes per face to each page.
  Pages are fetched once per day per year, so the cost is negligible.
- Shape drift: Immich may send `name` as `null` on some versions. A JSON `null`
  for a non-nullable `String` would fail decoding of the whole page, which is
  why `name` is `String?` and filtered as blank.
