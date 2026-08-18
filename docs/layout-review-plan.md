# Layout review plan

> **Status: executed**, all five phases. Kept for its rationale rather than as a backlog — in
> particular for the list of the review's claims that are *false*, checked against the code, and for
> the four places the plan itself turned out to be wrong on device. Both lists exist so the same
> arguments aren't made again from the same screenshots.

## Where this came from

An external design review of Kettle's screens, delivered as `README.md` + annotated HTML sketches
("Design review with layout changes", August 2026). It was written against **screenshots of a build
that predates the Today→Workouts tab merge**, which is why several of its premises describe an app
that no longer exists.

Every factual claim in it was checked against the code and against a real 20-workout library
(`cali-2.0-kettle.yaml`) before anything was accepted. This file records the rejections as well as the
acceptances — the review will otherwise be re-proposed from the same screenshots, and four of its
sections argue from things that are not true.

## Rejected, with the evidence

Keep this section. It is the reason not to re-open these.

| Review item | Why it is rejected |
|---|---|
| §6c "the HIIT chip is a real bug — fix the data path regardless" | Not a bug. `90:90 knee drops` is authored `type: hiit`, so the chip is right; `SessionNextCard` previews the **next** step, and the following exercise is a `timed_hold`, so `NEXT: hold` is also right. The reviewer read a preview of the next step as a description of the current one. |
| §7 "Stats and History disagree (13/119 vs 16/120) — dedupe the totals" | They call the same `historyStats()` from `history-stats.ts` on the same store array ([history.tsx:159](../src/app/(tabs)/history.tsx#L159), [analytics.tsx:55](../src/app/analytics.tsx#L55)). There is no second implementation. The screenshots were taken at different times, or one had a search active. |
| §9 "runner triangles unlabelled" | `session.previousStep` / `session.doneNextStep`, `minHeight: 64`. |
| §9 "the row play circle is unlabelled" | `build.startAccessibility`, with the workout name interpolated. |
| §9 "−/+/SWAP are under 44px" | 26px with `hitSlop={12}` → 50px effective, which is the documented house mechanism. |
| §9 "−/+/SWAP are under AA" | `#9a9384` on `#17140d` measures **6.0:1**. Passes AA for normal text. |
| §9 "RPE selection is carried by fill alone" | Fill **plus** a text-colour inversion (`RunnerColors.text` → `RunnerColors.background`) **plus** `accessibilityState={{ selected }}`. Survives greyscale and screen readers. |
| §8 "the newest build has five tabs, go back to four" | It is already four. The proposed fix removes Programs, which would make it three — Programs is a destination you browse and edit in, not a picker. |
| §3 "the theme has no divider token, which is why everything became a card" | `border` exists at [theme.ts:27](../src/constants/theme.ts#L27) and is exactly what the cards already draw with. |
| §1 "the selected sort pill is a fourth accent meaning" | Selected pills fill with `theme.text`, not the accent. The accent-overload diagnosis is still right, but with three uses, not four. |
| §6f "the runner prints three lines explaining what EMOM means" | That is the exercise's own `notes:` field — user data, rendered verbatim by house rule. Moving it to the get-ready screen would hide content the author chose to put there. The *layout* half of the complaint is accepted; see Phase 3. |

## Phase 1 — the Workouts screen

The screen the complaint was actually about: too crowded, and a `+` FAB competing with a
"Start an empty session" button.

**The two controls are not redundant** — `+` creates a workout, "Start an empty session" starts an
ad-hoc session. They read as peers because they are *styled* as peers: one accent fill bottom-right,
one accent-outlined pill in the title row. The fix is placement and weight, not deletion.

- **"Start an empty session" stays in the title row, and loses its accent.** Its label goes from
  `accentText` to `textSecondary`, matching the gear beside it, so the title row reads as one row of
  quiet chrome and the accent budget goes to the card's Start button.

  This plan originally said to move it under the card as a text link. **That is wrong, and the reason
  is recorded in the code**: the comment on `emptySessionButton` in
  [index.tsx](../src/app/(tabs)/index.tsx) says the control has already been three things — a bare
  text link under the card ("genuinely easy to miss on a device — it read as a caption"), then a
  full-width outlined button there ("a peer of `Start session` in both width and position, which
  overstates a secondary action"), and now the pill. Moving it back is re-treading a placement already
  rejected on device.

  The complaint was never that it is mispositioned; it is that it and the FAB compete. They compete
  because **both wear the accent**, not because of where they sit. Take the accent off the secondary
  one and the competition goes with it, at no cost in rows.
- **The FAB keeps its accent fill.** It is the list's create action and belongs with the list.
  Rejecting §2's "make it the last row of the list": with 20+ workouts that is a scroll to reach a
  create action, and the FAB's overlap is better solved by list padding.
- **Row play circles go neutral** — `backgroundSelected` behind a `text`-coloured triangle instead of
  `accentSoft` + accent. Twenty accent circles down one screen is most of the accent-overload §1
  correctly diagnoses, and this is the cheapest way to spend that budget on the one primary.
- **Tighten the next-up card's internal rhythm**: `Spacing.three` (16) between the label, name and
  summary drops to `Spacing.two` (8). Worth ~24px, and it is the tallest single element on the screen.

Explicitly **not** doing §4 (one job per row, delete the play circle). Losing it means every unqueued
workout costs row → editor → scroll → Start, and the review's justification ("the one workout you
start without browsing is Next up") assumes an active program — with none, `flatFallback` picks one
workout of the day and every other start goes through a row.

## Phase 2 — shared tab chrome

All four tabs stack the same header: title → count line → search → [filter pills] → [sort pills].
§2 is right that this is a block too many; its prescription is too blunt.

- **Fold the count into the search placeholder** (`Search 26 workouts`) and drop the count line — on
  **Workouts, Library and Programs only**. Saves a row on three screens.
- **History keeps its summary line.** It is not a static count: it carries the match count *and* the
  totals, and both narrow with the search, which a placeholder cannot do (the placeholder is gone the
  moment you type). §2's table also lists a sort row on History that does not exist.
- **Keep the screen titles.** §2 wants them deleted because the tab bar names the screen. It does on
  native; on web the tab bar is `_layout.web.tsx`, and the title row is also what the header actions
  (Import/Export, Stats/Export all, `?`, the gear) are laid out against. Revisit after Phase 4 if the
  screens still read as crowded — it is a one-line change either way.
- **Sort: deleted outright.** ~~Collapse, do not delete.~~ The collapse was built — a trigger in the
  header opening a bottom sheet — and rejected on sight as worse than the pills it replaced: it turned
  a control you could read at a glance into one you had to open, to choose between three options, on
  a screen that also has a search box. §2 was right the first time. The whole feature goes: the
  control, `domain/list-sort.ts`, and the `listSort` preference.

  Every list now renders in **library file order**, which was already the shipped default (`custom` on
  all three) and is the only order a hand-written, hand-shared file can be said to have an opinion
  about. Nothing changes for anyone who never touched the control.

  One compatibility note, now pinned by a test in `preferences-file.test.ts`: **every
  `preferences.json` in the field still carries `listSort`**, since the field was removed from the app
  and not from anyone's disk. Zod objects strip unknown keys rather than rejecting them, which is the
  only reason those files still load — a `.strict()` there would turn every existing install into a
  failed parse and silently reset the preferences they *did* set.

- **Library's type shortcuts stay, and grow.** They are not sort; they are the one control on that
  screen that answers a question the search box cannot ("show me the holds"). `emom`, `amrap` and
  `cardio` join the four that were there, so the row now covers every `ExerciseType` except `rest`
  (which is block structure and is filtered off the screen entirely). The row **scrolls horizontally**
  rather than wrapping: seven pills wrap to two rows on a phone and to three at a raised text size, so
  a row meant to save space started taking a variable amount of it.

## Phase 3 — the runner

**Its own PR.** High-risk file area, and disjoint from everything above. Verify by running a real
session, not by reasoning — the product plan calls timer reliability the make-or-break issue.

### 3a. The progress indicators (the one verified defect)

§5 is the best finding in the review and it checks out against real data:

```
block-count dist: {"1":4, "5":1, "7":14, "9":1}
max circuit members: 27  ->  "03 - Stretch and mobility"
```

- A 27-member circuit needs `27×3px + 26×2px = 133px` inside `SessionProgressDots`' **116px** track.
  It overflows, and the active segment is 6px among 3px neighbours — invisible at arm's length. That
  is the exact workout the reviewer screenshotted.
- Four workouts have a single block, so the block track renders **one accent dash with nothing after
  it**, which reads as "finished" for the whole session.

**Fix: both levels become one proportional bar** — two flex children (`done` / `remaining`), which
survives 2 members and 50. Nothing renders below two steps, so a single-block workout no longer draws
a permanently-full indicator that reads as finished from the first second.

~~Keep the block track discrete: "which of 7 blocks" is a real quantity and the dots say it well at
the counts that actually occur.~~ **Wrong, and caught on device.** Discrete dashes above a continuous
bar, stacked twenty pixels apart in the same header, is two visual languages for the same kind of
statement — it reads as a rendering fault, not as a distinction. Keeping the block count legible was
not worth that, and §5 had it right: both are bars.

The two levels are told apart by position (session on top), weight, and the kicker captioning the
lower one. **Weight descends with scope** — 4px session, 3px circuit — which was also caught on
device: the first attempt made the circuit bar *thicker*, and an inner bar outweighing the outer one
states the hierarchy backwards. The `calm` hue stays as a fourth signal, never the only one.

### 3b. The duplicate advance control

In [session-hold.tsx](../src/components/session-hold.tsx#L192-L214) the right-hand circle and the
full-width "Done set" button **both call `onDone`** — two controls, one action, one screen. Delete the
circle. Same check for `SessionInterval`.

Rejecting §6b's actual prescription (three equal-width `Back`/`Pause`/`Next` buttons). The hierarchy
there is deliberate: the comment at [session-hold.tsx:202-208](../src/components/session-hold.tsx#L202-L208)
records that Pause *used* to have the filled treatment and was demoted on purpose, because advancing
is the common action and pausing is the interruption. Flattening all three to equal weight
re-introduces exactly what that change fixed.

### 3c. Cheap and uncontested

- **§6d — make both reps-stepper buttons ghost.** The asymmetric accent-filled `+` reads as the
  screen's primary and competes with the actual one below it. They are one control and should look
  like one pair.
- **§6g — swap the get-ready weights.** The countdown numeral is worthless two seconds in; the
  workout name is what is being checked. Name large, countdown small.
- **Cap `notes` at `numberOfLines={2}`** in the runner step screens. This is the salvageable half of
  §6f: the content stays (it is user data), but an unbounded note stops owning the band directly under
  the exercise name on every repetition of every minute.

### 3d. Not doing

- **§6c, delete the mode chip.** It exists in one mode family, not four (`SessionInterval` only), the
  bug behind the argument is not a bug, and during an EMOM the pulsing live dot is the only thing on
  screen saying the clock is running.
- **§6a, rebuild all four modes on one skeleton.** The right instinct, but it is a rewrite of the
  highest-risk screens in the app to buy consistency that 3a–3c mostly deliver. Revisit only if the
  modes still feel inconsistent on device afterwards.

## Phase 4 — cards vs rows

§3's principle is right: *a card is for a single discrete privileged object; a list of peers is rows.*
Applied narrowly.

- **Workouts, Programs *and* Library** → rows. `minHeight: 56`, one hairline separator in the existing
  `border` token, no fill, no radius. Both lines free to wrap.

  ~~Library deferred until Workouts has been seen on device, since its rows carry a type badge that
  may need the fill to read.~~ **Done together instead.** The three screens held byte-identical copies
  of the card style, so converting two of them would have shipped a deliberate mismatch between
  sibling lists — which is the exact failure the runner's two progress indicators had just
  demonstrated, twice, in Phase 3. The badge carries its own per-type background and reads fine
  without a card behind it. All three now share one `ListRow`, so they cannot drift again.
- ~~**History session cards stay cards**, since they expand in place and a disclosure container is
  not a peer row.~~ **Converted too.** The argument was right about *shape* and wrong about *surface*:
  History's row still expands and still holds its detail, but keeping the fill made it the one tab
  whose list looked boxed while the other three did not. It uses the exported metrics rather than
  `ListRow` itself, because its container is vertical with a pressable header inside.

  The rule *inside* an expanded row went with the fill. At card weight it separated two parts of one
  boxed object; against hairline-separated rows it read as the boundary between two rows and split the
  session in half.
- ~~**Settings needed nothing**, since `ActionRow` is already a row with a chevron.~~ **Half right.**
  It has the chevron, but it drew itself on a filled bordered surface — a fourth copy of the card
  style — which left Settings as the last screen still using the old language. It now takes a top
  hairline per row instead. A **top border rather than a separator component**, because these are
  conditionally-rendered siblings in a section rather than a `FlatList`, so there is nothing to hang
  `ItemSeparatorComponent` off.

- **Every list gains a `ListHeaderRule`** — the same hairline, closing the header. Without a fill on
  the rows a list has no visible top edge: the search box just stops and names begin, so the first row
  reads as one more piece of header.

- **The next-up card is uncarded.** §2 asked for this and it was initially skipped, on the grounds
  that the card is a single privileged object. Two things overturned that on device: once the list
  below became rows it was the only drawn box on the screen, so it read as left over rather than as
  privileged; and its own padding sat on top of the screen's, starting every line 32px from the edge
  while every workout name below started at 16px — the card looked *narrower* than the list it
  introduces. Type scale and the accent fill inside it mark it as primary; the frame was redundant.

- **"Start an empty session" moved under the Start button after all** — the fourth home for this
  control, and the one §2 named. Phase 1 kept it in the title row and argued placement was never the
  complaint. That was answering the wrong objection: a bordered pill sharing a row with the screen
  title and the settings gear has nowhere to go when the text size is raised, and that row overflows.
  Left-aligned text weight, not a centred second button.

## Phase 5 — Stats, "am I getting stronger?" — **done**

The one genuinely new idea in the review (§7).

The current [analytics.tsx](../src/app/analytics.tsx) answers "am I turning up" (this-week tiles,
all-time tiles, `WeekBars`). It has no answer at all to "am I getting stronger", which is the question
that would make the screen worth a second visit.

Foundations already exist: `exerciseHistory`, `entryVolume`, `personalBestFor` in
`state/selectors/`, and `VolumeChart`, currently used only by `exercise-editor.tsx`.

Built as sketched: one hairline-separated row per exercise trained more than once in the window — name
and current value on the left, a seven-session sparkline, and the signed change right-aligned.

Three rules the sketch didn't name, all decided while building and all pinned by tests:

- **The measure is `entryBest`'s decision, not this screen's.** That function already owns "what counts
  as doing more" for the completion screen's records and the runner's live best-marker, so it is
  reused rather than restated — including its exclusions. `hiit` and `emom` are bounded by the
  exercise's own config, so a rise there reports that the *workout* was edited; `cardio` needs
  route-comparison rules the app doesn't have. Stats therefore covers strength work and holds, and
  says nothing about conditioning.
- **Last-minus-first, not best-minus-worst.** A peak-based delta reports a personal best as ongoing
  progress forever after.
- **One measure per exercise, taken from its most recent session.** An exercise that gained a dumbbell
  mid-window has points in reps *and* kilograms; the older kind is dropped rather than converted.

The delta deliberately has **no red/green pair** — the one number on the screen that can be negative
is the one that would most tempt it. A dip is information, not a failure, and colouring it as one
turns a deload week into a scolding.

Deliberately deferred from §7: the `4w`/`12w`/`All` window control (needs the rows first to be worth
switching), and the push/pull/legs/core balance breakdown (needs a muscle-group field the YAML format
does not have — that is a format change with three mirrors, not a layout change).

Keeping: all-time totals, demoted to one quiet line at the foot. Stats stays behind History's `Stats`
link and does **not** become a tab.

## Tests

Per phase, alongside the change:

- **Phase 1** — the empty-session link renders under the card and still routes with `adhoc=1`; the FAB
  survives; a `pt` case, since new placement means touched strings.
- **Phase 2** — the placeholder carries the count on the three screens that lose their count line;
  History's summary line still narrows while searching; the sort control reports and changes the
  persisted `ListSort`.
- **Phase 3** — `session-steps.ts` is where the pure parts live, but these are view changes:
  `SessionProgressDots` at 1, 2, 27 and 50 segments; the block track absent at `total === 1`; one
  advance control per step screen. **Prove the 27-segment case fails against the current component**
  before fixing it.
- **Phase 4** — row rendering is layout, so mostly device work; keep the existing list-order and
  search assertions passing unchanged (they read `testID="workout-card-name"`, not the card shape).

Locale keys land in **both** `en.json` and `pt.json` in the same commit, per the parity rule.

## Not in scope

- `activeProgram`'s `library.programs[0]` fallback — an imported program is not the active one until a
  session is logged against it. Real, known, written up in the merge plan, and unrelated to layout.
- Renaming the `today.*` locale namespace.
- Translating `program-guide.tsx`.
- Any change to `use-session-runner.ts` — Phase 3 is view-layer only.

## Verification

Tests cannot reach any of this. Each phase ends by driving the running app per
[verifying-in-the-browser.md](verifying-in-the-browser.md), plus a device pass for Phase 3:

- Workouts: the list is reachable under the card at default *and* at 200% text scale; the empty-session
  link is distinguishable from the FAB at a glance.
- Runner: a real session of `03 - Stretch and mobility` (the 27-member circuit) — the circuit bar reads
  as a bar from across the room, and a single-block workout no longer looks finished at step one.
- Both themes, both locales. Portuguese runs 20–30% longer and the sort control (Phase 2) is the new
  string most likely to break a row.
