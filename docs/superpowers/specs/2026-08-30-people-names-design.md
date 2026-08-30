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

- Placement: the first line of the existing bottom-right caption block, above
  date/time and location, at the caption's 20 sp size. (On-device feedback
  moved it up from a larger third line.)
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
  1. drop hidden people and people with no name. A `null` name, an empty
     string, and a whitespace-only string all mean "no name" and are treated
     identically — Immich represents an unnamed face as `""` and some versions
     as `null`;
  2. sort by the smallest `boundingBoxX1` across the person's faces; people
     with no face coordinate sort last, preserving arrival order among
     themselves;
  3. trim names and drop duplicates, keeping the first occurrence.
- `MINT_PERMISSIONS` and the key-permission hint strings are unchanged.
  Verified against Immich `v2.7.5` source: `POST /search/metadata` is
  decorated `@Authenticated({ permission: Permission.AssetRead })`
  (`server/src/controllers/search.controller.ts`), the OpenAPI spec carries
  `x-immich-permission: asset.read` for it, `withPeople` selects faces and
  people in `server/src/utils/database.ts`, and `mapAsset` in
  `server/src/dtos/asset-response.dto.ts` attaches `people` with no further
  permission check. The existing keys return people as they are.

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
  by the toggle; names always travel with the asset load. The toggle is one of
  the keys that rebuild the slide deck, so it takes effect when the deck is
  next rebuilt, the same way the language setting does.
- `CaptionBlock` draws `lines.people` as the first `Text`, above date and
  location, at the same 20 sp, capped at two rows with an ellipsis.
- `OverlaySuppression` is untouched: a caption with only a people line is still
  a non-null caption, so the paired-slide overlay rule keeps working. The rule
  therefore fires slightly more often than before: a left photo with no date
  and no location but with recognised people now counts as captioned.
- New strings in `values/strings_settings.xml` and
  `values-ru/strings_settings.xml`.

## Testing

- `:core`, TDD with case tables:
  - `PeopleListTest` — zero, one, two, and three names, in RU and EN.
  - `PhotoCaptionTest` — a people-only caption, all three lines present, and
    the existing cases still passing with the default empty list.
- `:app`, pragmatic:
  - `ImmichModelsTest` — decode a search fixture containing a named person, a
    person with `"name": ""`, a person with `"name": null`, a hidden person,
    and a person without faces.
  - `AssetMapperTest` — filtering (hidden, `null` name, empty name, blank
    name all dropped), left-to-right ordering, trailing placement of people
    without coordinates, and de-duplication.
  - `SlideResolverTest` — the toggle strips names; on, the third line is
    present.
- On-device: a photo with a recognised, named face shows the name; the toggle
  hides it on the next slide; a photo without faces is unchanged; a photo with
  many recognised people, single and paired, to check the two-row cap.

## Risks

- Permission: settled for `v2.7.5` (see Data layer). If a future Immich gates
  embedded people on `person.read`, the fix is to add it to
  `MINT_PERMISSIONS` and both hint strings and re-pair once; manual-key users
  add the permission in Immich.
- Response size: `withPeople` adds a few hundred bytes per face to each page.
  Pages are fetched once per day per year, so the cost is negligible.
- Shape drift: at `v2.7.5` `person.name` is a non-null column defaulting to
  `""`, so unnamed people arrive as `"name": ""`. `name` is still declared
  `String?` so that a future `null` cannot fail decoding of a whole page.
  Downstream, `null` and `""` are the same case: no name.
- Face coordinates: `boundingBoxX1` is emitted through
  `transformFaceBoundingBox`, so it already accounts for Immich edits
  (crops, rotations) and is safe to use for ordering.
