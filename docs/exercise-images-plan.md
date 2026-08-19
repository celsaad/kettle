# Images on exercises — plan

> **Not executed.** Forward-looking; nothing below has shipped. Written against the tree at `c62233d`.
>
> **Revised once, and the revision is the point.** The first draft designed a user-supplied photo per
> exercise: a file picker, an app-owned `images/` directory, a new YAML field and its three mirrors.
> That is now the **rejected** path, kept at the bottom because the survey behind it is expensive to
> re-run and the constraint that killed the obvious option still binds. What replaced it is roughly a
> tenth of the work and needs no format change at all.

An exercise can only explain itself in prose. `notes` is a coaching cue
(`src/i18n/locales/en.json:493` — "Coaching cue…"), and a cue is a reminder for a move you already
know.

**The rule, in one line:** every **seeded** exercise gets a **bundled line drawing keyed by its id**.
Nothing is picked, nothing is stored, nothing is exported, and `schema.ts` does not change.

## What this is, and what it deliberately is not

Worth stating before the design, because the backlog entry's opening line — "a move demonstrated beats
a move described" — is *not* what this delivers, and reusing that sentence to justify it would be
drift.

**It is first-run polish on the starter library.** A fresh install lands on `foundations` with no
library of its own; seventeen drawings make that content look made rather than seeded, on the screens
a new user sees first.

**It is not "teach me the move."** It serves the exercises that need explaining least — nobody needs a
diagram of a push-up — and it can never reach the case that does: an exercise from a program the user
imported, or one an assistant generated. That case needs the user-supplied path at the bottom of this
file, and this plan does not preclude it: if that ever ships, a user's own image overrides the bundled
art and the art becomes the fallback. Nothing built here gets thrown away.

Take that trade knowingly. It buys a large fraction of the perceived value for a small fraction of the
cost, and it ships on web, which the photo design never could.

## Why keying on the id is safe

The load-bearing fact, and the one that could have killed this idea:

**Seed exercise ids are language-independent.** `seed-library.ts` is a single **English structural**
definition with hardcoded ids (`pushups`, `bodyweight-squats`), and `seed-translations.ts` replaces
only `name` and `notes`, keyed by those same ids. So an id→art map is correct in `en`, in `pt`, and in
any future language, for free — a Portuguese user's *Flexões* is still `pushups` underneath.

Two consequences, both fine:

- **A seeded exercise is user data the moment it's written.** The user can rename it, retype it, or
  change its config; the id survives all of it. So the art follows the exercise through a rename,
  which is right, and would follow it through a *repurposing* — a `pushups` entry edited into
  something else keeps the push-up drawing. Low stakes, and not worth guarding against.
- **A user's own exercise can land on a seeded id** — delete the seeded `plank`, create your own
  "Plank", and `slugify` gives it `plank` again, art included. That is a feature, not a collision.

## The art

The set exists: **seventeen drawings, one per seeded exercise id except `rest`** — `pushups`,
`bodyweight-squats`, `inverted-rows`, `split-squats`, `glute-bridge`, `plank`, `mountain-climbers`,
`db-goblet-squat`, `db-floor-press`, `db-row`, `db-romanian-deadlift`, `db-overhead-press`,
`farmers-carry`, `burpees`, `emom-pushups`, `amrap-12-bodyweight`, `easy-cardio`. Consistent
single-colour outline style, uniform stroke weight. That is complete coverage on day one, which is
what makes this a small piece of work rather than an art project.

### It is raster, and that decides most of the design

The set was generated from a prompt in Gemini and came back as **one 2816×1536 contact sheet**, not as
SVG. There is no vector source and there isn't going to be one for free. Three consequences, and the
first is the one that constrains everything downstream:

**The drawings are roughly 300×220 native pixels each.** On a 3× screen that is about **100dp** before
it goes soft — a small illustration, not the full-width one the first draft assumed. So either the
art is used small, or it is regenerated larger. **Regenerate**: prompt one image per exercise instead
of a seventeen-up sheet and each comes back at the generator's full output size rather than at a
seventeenth of it. That pass is prompting, not drawing, and it is the single highest-value hour in
this plan — it also removes the baked-in numerals (below) at the source instead of by editing.

The risk of regenerating one at a time is **style drift** across seventeen prompts. Keep one prompt,
vary only the movement, and pass the existing sheet as a style reference; then look at all seventeen
together before accepting any of them. Consistency across the set matters more than any single
drawing, because they are seen as a set.

### Vectors are worth wanting, but not for the reason they look like they are

Tracing the set to SVG is the obvious next thought, usually motivated by app size. **Size is the weak
argument; resolution is the strong one** — vectors are the only thing that lifts the ~100dp ceiling
above, and that ceiling is the single biggest constraint in this plan.

The size case, in numbers, so it isn't re-argued from intuition:

- The whole `assets/` directory today is **272 KB**. `kettle-mark.tsx` — hand-authored, nine shapes —
  is **1.26 KB**.
- **A tracer transcribes art, it does not simplify it.** These figures carry faces, hair, shoe laces,
  a bench, a pull-up frame; every contour becomes path nodes, and outline tracers (potrace and its
  descendants) turn each *stroke* into a thin closed polygon with roughly double the nodes. A traced
  figure at this detail level is realistically 20–60 KB of path data — seventeen of them is 0.3–1 MB,
  the same order as the alpha PNGs, except it lives in the **JS bundle** and is parsed at startup
  rather than decoded lazily on render.

So auto-tracing *this* art buys the ugliness of a trace without the size back.

**The lever is simpler art, not a different file format.** `KettleMark` is 1.26 KB because it is nine
shapes. Regenerate iconographic rather than illustrative — uniform stroke, no facial features, no
shoe detail, no background props — and a traced figure drops to a few KB, putting the whole set near
40 KB. That same simplification is what makes the drawings legible at 100–140dp, where the current
level of detail turns to mud. **Legibility, size and vectorisation all want the same thing**, which
is what makes this worth doing rather than a trade.

Two mechanics that decide whether a trace is usable:

- **Centerline, not outline.** Illustrator's Image Trace, `autotrace -centerline` or vtracer's
  centerline mode follow the stroke; the default outline mode is what produces the doubled-node mess.
  Clean uniform-stroke line art is close to ideal input for centerline tracing — at **1024px+**. At
  the 300px of the contact sheet it is not, which is the whole reason the regeneration pass comes
  first.
- **A model can author SVG directly**, which is a different act from generating a raster and lands in
  the 1–3 KB range. Quality on human poses is hit-or-miss, so it is worth an hour on two exercises,
  not a commitment for the set.

**Settle it with a spike, not an argument.** Trace two — `pushups` (simple) and
`db-romanian-deadlift` (complex, carries motion arrows) — from regenerated high-resolution art, then
compare KB and look at them beside the PNG. If they hold up, the whole set goes vector and the
resolution ceiling disappears with it. If they don't, tinted PNGs ship and nothing else in this plan
changes: the map, the id keying, the a11y work and the tests are identical either way, and only the
`EXERCISE_ART` values differ (a `require` versus a component). **That is why this decision does not
block anything else** — it is the last reversible call, not the first.

**Licence is not a blocker here, but note it once.** Google's terms for Gemini permit commercial use
of generated output — worth confirming against the tier actually used. The residual is that
AI-generated images generally aren't copyrightable, so the set can't be exclusive; that costs nothing
for shipping, and there is no attribution obligation to find a home for.

### Two of them have configuration baked into the picture

`emom-pushups` carries a stopwatch reading **60s**, `amrap-12-bodyweight` a dial reading **12 min**.
Those numbers are `config.interval_sec` and `config.time_cap_sec` — **user-editable values**. Change
the EMOM to 90 seconds and the drawing is simply wrong, and it is wrong in the one place the app
claims to be showing you your own data.

Prompt those two without the numerals during the regeneration pass. The remaining figure work (a
push-up under a timer; three movements around a dial) still says "this is timed", which is all the
picture needs to say. `easy-cardio` is fine as drawn — a treadmill and a heart assert nothing
falsifiable.

### They are detail-view art, not thumbnails

The set is drawn at a level of detail — faces, hair, shoes, a bench, a pull-up frame — that reads at
card size and turns to mud at 44px. `plank` and `pushups` are both prone side-views and are nearly
indistinguishable when small. Combined with the resolution ceiling above, that puts the usable window
at roughly **100–140dp**: big enough to read the movement, small enough to stay sharp. That decides
where they render (below).

## Design

Everything from "The map" down is **identical in both formats**; only the values in `EXERCISE_ART`
differ. The next section is the raster half, and applies only if the vector spike above comes back
negative — if it comes back positive, the assets are components under `src/components/exercise-art/`
following `kettle-mark.tsx`, taking a `color` prop instead of a tint, and neither the alpha step nor
the density suffix exists.

### If it ships raster: the two preparation steps

Files land in `assets/images/exercise-art/`, beside the icons already there. Two things happen to each
drawing before it is committed, and neither is optional:

**Cut the white background to alpha.** The generated art is navy strokes on solid white. Left that
way it is a white card floating in a dark theme, and the session runner is dark *always*. Converted to
a transparent, single-channel-ish alpha mask — a luminance-to-alpha threshold, which is exactly what
clean line art on white is best case for — it becomes a **tintable** asset, and one file then serves
both themes. This is the raster equivalent of `KettleMark`'s `color` prop, and it is what buys back
the recolouring that losing SVG appeared to cost.

**Name the file `<id>@3x.png`.** Metro reads the density suffix, so a ~300px asset declared at 3×
lays out as ~100dp instead of ~300dp. Getting this wrong is not a subtle bug — it is a drawing three
times too big — and there is no 1× or 2× variant to ship, which is fine: metro downsamples from the
3× and there is nothing smaller to serve.

### The map

`src/components/exercise-art/index.ts`, holding either components or asset handles:

```ts
export const EXERCISE_ART: Record<string, ExerciseArt> = {
  pushups: PushUps, // or: require('../../../assets/images/exercise-art/pushups@3x.png')
  'emom-pushups': PushUps,
  …
};
```

**Ids map many-to-one.** `pushups` and `emom-pushups` are the same movement and share one entry.

**If raster wins, the map must use explicit `require` literals, not a template string.** Metro
resolves static assets at build time, so ``require(`./${id}.png`)`` does not work — every file has to
be named. That is a constraint rather than a style choice, and it is why the map is a real artefact
instead of a naming convention.

### Rendering and theming

One small component takes the colour from `useTheme` — or from `RunnerColors` inside the runner, which
is always dark regardless of scheme — and applies it as a `color` prop on the SVG, or as `tintColor`
on the image. Vector or raster, the drawing renders in exactly **one** colour, which is a real
constraint on the art: anything relying on two tones is lost. This set is single-weight outline
throughout, so nothing is lost in practice.

Contrast-check that colour against every surface it lands on, per the house rule.

If it ships raster, `expo-image` is **already a dependency** and imported by nothing today, so it
costs no install; plain RN `Image` with a `tintColor` style would also do.

**No picker, no `expo-file-system`, no permission, no new dependency either way.**

### Where it renders

**The exercise editor** (`src/app/exercise-editor.tsx`) is the home. It doubles as the detail screen —
a Library row pushes straight to it (`src/app/(tabs)/library.tsx:47`) — so "what is this move" already
lands there. The drawing sits above the notes field, centred, and only when the id is in the map. No
control, no placeholder, no empty frame when absent.

**Size depends on which format won.** Raster is capped at the **100–140dp** the source resolution
allows, and stretching past it is what makes the softness visible. Vector has no such ceiling — which
is the real prize in the spike above, and the point at which a full-width illustration becomes
available.

**The runner's next-up card** (`src/components/session-next-card.tsx`) is the tempting one and is
**deferred, not scheduled**. Three reasons: the art is not legible at that size (above); the runner
screens are `flex: 1` with `space-between` and **no ScrollView**, so overflow clips the bottom where
the primary action lives — which is why that card already hides itself above `fontScale` 1.5; and the
runner is the file the product plan calls the make-or-break one. If it's tried, it gets its own pass
and a real device at large text, not a jest assertion.

**Not in scope:** Library list thumbnails (`ListRow` is a hairline row whose height is a shared metric
two other screens copy — a leading image is a layout argument of its own), a full-screen viewer, and
animation.

## Accessibility and i18n

This is where the bundled set beats the photo design outright rather than just costing less.

The art is **app content, not user data**, so it can carry a real description — and that description
gets translated. A user-supplied photo had no alt text to author, which forced marking it decorative;
here each drawing gets an `accessibilityLabel` describing the *movement* ("Push-up: hands under
shoulders, body in a straight line"), keyed in **both** `en.json` and `pt.json`.

Keep the description about the movement, not the picture, and hold it to the seed's own content bar
from the decision log: it describes the app's data, it does not coach, and it gives no injury advice.

The drawing is not interactive, so it needs no role, no 44px target, and a fixed `height` is correct.

## Tests

Small, because the surface is small:

- **Coverage guard**, mirroring `seed-library.test.ts`'s parity check: every id in `seedLibrary.exercises`
  either has an entry in `EXERCISE_ART` or appears on an explicit `NO_ART` list (today just `rest`).
  Adding a seeded exercise without a drawing then becomes a decision rather than an oversight.
- **Label parity**: every id in the map has a description key present in both locale bundles.
- **Editor screen**: renders the art for a seeded id, renders nothing for an unknown one and does not
  throw. Drive one of these in **`pt`** — an English-locale assertion cannot catch a hardcoded string.

Not tested, and looked at in the running app instead: how the drawings actually sit in the editor, and
contrast in both themes.

## Bundle weight

Measure it; don't argue it. The baseline is **272 KB for the whole `assets/` directory** today.

- **Iconographic vectors:** ~1–3 KB each, so the set lands near 40 KB — better than the current icon
  set. This is the outcome the spike is trying to reach.
- **Alpha PNGs:** tens of KB each, a few hundred KB for the set. Paid once in the APK rather than per
  render, and unremarkable against what's already shipped.
- **Traced-but-not-simplified vectors:** the bad outcome — 20–60 KB each *and* in the JS bundle. If
  the spike lands here, take the PNGs.

If a number comes out unreasonable, the lever is **how simple the art is**, not compression settings
or output dimensions.

## What this avoids

Worth listing, because it is the entire argument for this shape over the one below. None of the
following is touched: `schema.ts`, `types.ts`, `yaml-mapping.ts`, `merge.ts`, the three format
mirrors, the four `site/examples/*.yaml` libraries, `storage/`, `app.json` permissions, the export
path, the backup folder, and the Data Safety declaration. There is no new dependency and no new
failure mode at runtime — a missing drawing is a map lookup returning `undefined`.

## Rejected: a user-supplied image per exercise

Kept because the survey is expensive to re-run and one part of it binds any future attempt.

**The app may not fetch a remote image**, and this is the part that outlives the rest. An `Image` with
a `{ uri: 'https://…' }` source hands whatever host the YAML names the user's IP and a timestamped
record of which exercise they were looking at. The listing declares zero data collected/shared, and
the decision log has already rejected RevenueCat, a cloud backend and Android auto-backup on that same
line. It also fails in the one place the app gets used: a basement gym with no signal. A remote source
needs the Data Safety argument made **first**, in its own decision-log entry.

That leaves local bytes, and three shapes, all costed:

- **A filename in the YAML, bytes in an app-owned `images/`** — the strongest of the three, and still
  the fallback if this ever comes back. It needs: a filename-only rule refusing `..`, `/` and schemes
  (a mailed YAML is untrusted input, and it would otherwise be a file-read the app performs on the
  author's behalf); orphan sweeping, since `merge.ts` replaces whole objects by `id` and an import
  that omits `image` drops the reference; a size cap, because nothing in the tree can downscale; and
  the admission that `exportLibrary` shares one file, so a mailed library arrives without its
  pictures.
- **Base64 in the YAML** — self-contained, and it ends hand-editability, which is the product. The
  library is re-parsed on every hydrate; two photos are ~5 MB against a file that is kilobytes today.
- **Images in the backup folder** — that folder is Android-only, optional, and a *destination*. Making
  it the source of truth means the picture of your own squat renders only on Android and only after a
  trip through Settings.

**Two API facts found while costing it**, worth keeping regardless — they belong in
[`sdk-57-api-notes.md`](sdk-57-api-notes.md) if any of this is revisited:

- **No dependency is needed to pick a file.** `File.pickFileAsync({ mimeTypes: ['image/*'] })` is in
  the installed `expo-file-system` (`node_modules/expo-file-system/build/File.d.ts:48`) and opens
  `ACTION_OPEN_DOCUMENT` on Android — **no runtime permission, no manifest entry**. The obvious
  choice, `expo-image-picker`, adds `CAMERA`, `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE` per
  the SDK 57 docs, against an `app.json` that declares one permission and blocks two on purpose.
- **`pickFileAsync` reports every failure as a user cancellation.** Its whole body sits in one `try`
  and the `catch` returns `{ result: null, canceled: true }`
  (`node_modules/expo-file-system/src/File.ts:127-136`), so a provider error or a `SecurityException`
  is indistinguishable from someone backing out. This is the **opposite** of
  `Directory.pickDirectoryAsync`, which throws and needs `ERR_PICKER_CANCELLED` picked out —
  `backup.ts`'s pattern written from memory here would never fire.

## Order of work

1. **Regenerate the seventeen individually at full output size**, one prompt varying only the movement,
   **iconographic rather than illustrative**, without the numerals on `emom-pushups` and
   `amrap-12-bodyweight`. Review all seventeen together for style drift before accepting any. This
   gates everything else, and it is prompting rather than drawing.
2. **The vector spike**: centerline-trace `pushups` and `db-romanian-deadlift`, compare against the
   PNGs on size and on looks, and pick the format for the set.
3. Prepare the assets in whichever format won — components under `src/components/exercise-art/`, or
   background-to-alpha PNGs named `<id>@3x.png` under `assets/images/exercise-art/`. Check the total
   size here either way.
4. `index.ts` map + the `NO_ART` list + the coverage test.
5. Descriptions in both locale bundles + the parity test.
6. Editor rendering, contrast-checked in both themes.
7. `CHANGELOG.md` under `## Unreleased`, in the users' register.
8. `pnpm test`, `typecheck`, `format`, `lint`; prune the `open-work.md` bullet to what's left of it.

The runner card is **not** on this list. It is a separate pass with a device.
