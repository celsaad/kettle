# Rest days in programs — plan

> **Executed; kept for its rationale, not as a backlog.** The approach for letting a program week say
> "today you rest", agreed before any code was written. The reasoning that outlives the commit — the
> elapsed-days rule, the two rejected alternatives, and the `merge.ts` trap the type change exposed —
> is in [`decisions.md`](decisions.md).
>
> Two decisions were taken up front and are recorded under "Decisions taken" below: the YAML spelling
> (`rest_day: true`) and how a rest day clears itself off the home screen (elapsed calendar days, no
> new persisted state).
>
> Three things came out differently from the plan below. The **optional** `format-mirrors.test.ts`
> extension was **not** done, so the `weeks[]` tables in the two mirrors are still checked by eye —
> it stands alone as a follow-up, exactly as described under "Optional, and easy to cut". A
> **`program-detail.test.tsx`** was written as its own file rather than folded into `programs.test.tsx`.
> And `seed-library.test.ts`'s `structureOf` needed `restDay` added to it: which days are rest is
> structure rather than string, so a translation that turned a training day into a day off would
> otherwise have passed the parity guard.

## The problem

A program can only say "run this workout". Every entry in `weeks:` requires a `workout:`
([`schema.ts:163`](../src/domain/schema.ts#L163)), so a four-day-a-week block is written as four
entries and the three days off it depends on are simply absent from the file.

That has three consequences:

- **The file is a lie about the program.** A push/pull/legs split written as three consecutive days
  reads as three days back to back, because that is all the format can say.
- **The home screen queues a workout on your day off.** `nextUpView` takes the slot after your last
  logged session and offers a Start button. Finish Monday's session and Tuesday's card says "Week 1 ·
  Day 2 — Pull Day", on the day the program wants you resting.
- **The only workaround is worse.** Today you can point a week at a workout containing a `rest`
  exercise. That runs a session, writes a session file, and counts toward "sessions this week" — a day
  off recorded as training.

Note what this is *not*: the `rest` **exercise** type ([`types.ts:29`](../src/domain/types.ts#L29))
is intra-workout rest between sets, and the **rest-day reminder**
([`use-rest-day-reminder.ts`](../src/hooks/use-rest-day-reminder.ts)) is an opt-in nudge derived from
the log. Neither is a program's scheduled day off, and this plan touches neither.

## The rule, in one line

**A program week entry with `rest_day: true` and no `workout:` is a slot in the rotation that is
never run**, and it clears off the home screen once a calendar day has passed since the last logged
session.

## Decisions taken

### The spelling is `rest_day: true`, `workout:` omitted

```yaml
weeks:
  - week: 1
    day: Day 1
    workout: full-body-a
  - week: 1
    day: Day 2
    rest_day: true
    notes: Walk 20 minutes. Nothing that leaves you sore.
```

`workout:` stays **required on every non-rest week**, so a dropped or misspelled `workout:` line is
still an import error rather than a silent rest day — which is what rules out inferring rest from an
absent workout. `rest_day` rather than `rest` because `rest` is already an exercise type and
`rest_sec` a config key in four of the seven types; one word with two meanings in one file is how
authors get it wrong.

Schema rules, all as `.refine`s on `rawProgramWeekSchema`
([`schema.ts:157`](../src/domain/schema.ts#L157)):

| rule | why |
| --- | --- |
| `workout` required unless `rest_day: true` | a dropped line stays an error |
| `rest_day: true` forbids `workout` | one slot, one meaning |
| `rest_day: true` forbids `overrides` | nothing to override |
| a program needs ≥1 non-rest week | an all-rest program has nothing to queue, ever |

`notes` and `day` stay allowed and are the whole point of a rest entry beyond the gap itself.
`rest_day: false` is accepted but pointless; the domain type keeps it optional so existing files and
the editor's output are unchanged.

### It clears itself by elapsed calendar days

The pointer arithmetic in `nextWeekAfter` ([`selectors.ts:182`](../src/state/selectors.ts#L182)) is
derived entirely from the log — "the slot after whichever one the most recent tracked session was
for". A rest day is never logged, so on its own it would pin the home screen to the rest card
forever.

The rule that unpins it, with `R` consecutive rest slots following the last logged session:

```
restsServed = max(0, daysSince(lastSession) - 1)
restsServed >= R  →  show the next workout
otherwise         →  show rest slot number restsServed + 1
```

Walked through, for one rest slot between Monday's and Wednesday's sessions:

| day | elapsed | card |
| --- | --- | --- |
| Mon, after training | 0 | Rest day · Week 1 · Day 2 |
| Tue | 1 | Rest day · Week 1 · Day 2 |
| Wed | 2 | Week 1 · Day 3 — Pull Day |

Two rest slots take two days, and so on. `daysSince` steps with `setDate()`, not by subtracting
86_400_000ms — the same DST correction `currentStreak` and `restDayReminderAt` already carry, and for
the same reason.

**Rejected: a persisted "rest day done" flag.** More explicit and never guesses, but it adds
persisted state that web silently loses (web has no persistence at all), and a user who never taps
the button stays stuck on the rest card. **Rejected: skipping rest slots entirely** and showing them
only as a footnote — it cannot get stuck, but then the app never says today is a rest day, which is
most of what the feature is for.

The escape hatch replaces neither: the rest card carries a **"Train anyway"** link straight to the
next non-rest slot, so nobody is ever blocked by the arithmetic being wrong for them.

## What the types force

`ProgramWeek` becomes a union rather than growing an optional flag:

```ts
export type ProgramWeek =
  | { week: number; day?: string; workoutId: string; restDay?: false; notes?: string; overrides?: ProgramOverride[] }
  | { week: number; day?: string; restDay: true; notes?: string };
```

Every existing `week.workoutId` read fails typecheck until it is guarded, and that is the point —
the compiler enumerates the sites that have to think about rest instead of leaving them to be found
by hand. There are six in `src/` (below), which is small enough for the union to be worth it.

`NextUpView` ([`selectors.ts:130`](../src/state/selectors.ts#L130)) becomes a union for the same
reason — a rest card has no workout, no exercises and no `sessionParams`:

```ts
export type NextUpView =
  | { kind: 'workout'; workout; exercises; weekNumber; weekDay; weekNotes; sessionParams }
  | { kind: 'rest'; weekNumber: number; weekDay: string | null; weekNotes: string | null;
      skipTo: { programId: string; week: string; day?: string } | null }
```

`skipTo` is null only for the pathological all-rest program the schema now refuses on import, but
which the in-app editor could still produce; the card then simply has no link.

`nextUpView` takes `now: Date = new Date()` so the elapsed-days rule is testable without mocking the
clock — the same "the caller owns the clock" split `serializeSessionArchiveYaml` already uses.

`resolveWorkoutForWeek` keeps its signature and returns `null` for a rest week, with a guard and a
comment. Callers that need to tell "rest" from "no such week" ask `findProgramWeek(...)?.restDay`,
which is already exported. **Considered and rejected:** widening its return to
`{kind:'rest'} | {kind:'workout',…} | null`. It removes a real ambiguity, but only two callers care
and both already hold the week — the churn is not worth it.

## Files, in the order they should be touched

### Domain and schema

| file | change |
| --- | --- |
| [`types.ts:74`](../src/domain/types.ts#L74) | `ProgramWeek` union above |
| [`schema.ts:157`](../src/domain/schema.ts#L157) | `workout` optional, `rest_day`, the four refines |
| [`yaml-mapping.ts:285-303`](../src/domain/yaml-mapping.ts#L285-L303) | `programWeekToDomain` / `programWeekToRaw` both branch; a rest week round-trips with no `workout:` key |
| [`program.ts:37`](../src/domain/program.ts#L37) | `resolveWorkoutForWeek` returns null for a rest week, before the workout lookup |
| [`merge.ts:78`](../src/domain/merge.ts#L78) | the `unknownWorkout` check skips rest weeks — otherwise every rest day fails import |

### State

| file | change |
| --- | --- |
| [`selectors.ts:130-214`](../src/state/selectors.ts#L130-L214) | `NextUpView` union; `nextUpView` grows the rest branch, the elapsed-days rule and `skipTo`. `nextWeekAfter` is **unchanged** — it still returns the literal next slot, and the skipping lives one level up where the clock is |

### Screens

| file | change |
| --- | --- |
| [`index.tsx:120-192`](../src/app/(tabs)/index.tsx#L120-L192) | rest branch of the Next-up card: week/day label, notes, no chips, no summary line, no Start button, "Train anyway" link. The "Start an empty session" button below is untouched and stays the way to train on a rest day |
| [`program-detail.tsx:81-130`](../src/app/program-detail.tsx#L81-L130) | rest weeks render as a card with a "Rest" badge in place of the workout name and **no Start button** |
| [`program-editor.tsx`](../src/app/program-editor.tsx) | a "Rest day" toggle per week card (`accessibilityRole="switch"` + `accessibilityState.checked`); toggling on hides the workout picker and the override editor. `save()`'s `!week.workoutId` check ([:75](../src/app/program-editor.tsx#L75)) only applies to non-rest weeks, and gets a new check that at least one week is trainable |
| [`session.tsx:107-115`](../src/app/session.tsx#L107-L115) | a deep link or stale navigation to a rest week currently resolves to null and renders a blank screen; it gets the existing "nothing to run" empty state with its own message |
| [`workout-editor.tsx:157`](../src/app/workout-editor.tsx#L157) | the "used by N programs" check reads `week.workoutId`; guard for the union |
| [`program-guide.tsx`](../src/app/program-guide.tsx) | a section for rest days after §5, with the sample above. Deliberately English-only, like the rest of that screen |

### i18n

New keys in **both** [`en.json`](../src/i18n/locales/en.json) and
[`pt.json`](../src/i18n/locales/pt.json), at exact parity: the rest card's label, "Train anyway",
program detail's Rest badge, the editor's toggle label and its new validation error, and session.tsx's
rest message. Day labels and week notes are user data and render verbatim, untranslated, as always.

### Seed library and published examples

The format change is only half of it — every program this repo ships still describes a rhythm with no
days off, and they are what most people read first.

| file | change |
| --- | --- |
| [`seed-library.ts:230-400`](../src/storage/seed-library.ts#L230-L400) | both programs (`foundations`, `dumbbell-strength`) |
| `site/examples/beginner-full-body.yaml` | 3 days/week |
| `site/examples/bodyweight-no-equipment.yaml` | 3 days/week |
| `site/examples/push-pull-legs-6-weeks.yaml` | 3 days/week |
| `site/examples/conditioning-hiit.yaml` | 2 days/week |

**Fill the week out completely** rather than dropping a rest slot between training days and leaving
the weekend implied. The elapsed-days rule counts rest slots against real days, so a 3-day program
written as five slots (train/rest/train/rest/train) advances to next week's Day 1 the day after the
last session — the exact behaviour rest days exist to fix. Seven slots per week is what makes the
rotation honest.

Day labels are sorted, not file-ordered, by `nextWeekAfter`, so the rest entries need labels that sort
into place: `Day 1 · Push`, `Day 2 · Rest`, `Day 3 · Pull`, … Renumbering the existing labels is part
of the change, not a side effect of it. `push-pull-legs-6-weeks.yaml` already carries a comment
explaining that ordering — it should be extended rather than duplicated.

Each program keeps at most one rest entry carrying `notes`, saying what an easy day is for; the rest
are bare. Six identical "rest" notes per week is noise.

### The three format mirrors — same PR, no exceptions

Per `AGENTS.md` → "Changing the YAML format", a new field lands in all three or it lands broken:

1. [`docs/authoring-exercises-yaml.md`](authoring-exercises-yaml.md) — the program section (~L217-280)
   gets `rest_day` in its field list, a rest entry in the complete sample, and one paragraph on the
   home-screen behaviour.
2. [`docs/product-plan.md`](product-plan.md) §4 — the file-format section and the program sample at
   ~L449.
3. [`site/format.html`](../site/format.html) — the `weeks[]` table at ~L663-700 gains a
   `weeks[].rest_day` row, `weeks[].workout` changes from required to "required unless `rest_day`",
   the program sample at ~L635 gains a rest entry, and the checked full library sample at ~L840 gains
   one too. **This is the copy an outside author actually reads and the easiest of the three to
   forget.**

`site/examples.html` describes the example libraries in prose and is not checked by anything — if the
day labels get renumbered, its descriptions need re-reading.

Run `pnpm run format` after editing `site/format.html`. It is **not** on the oxfmt exclude list, and
hand-editing it and skipping `format` is what broke CI on #58.

### Everything else

- [`CHANGELOG.md`](../CHANGELOG.md) `## Unreleased` — the user-facing write-up. This is the exception
  to "don't append shipped features to docs".
- [`AGENTS.md`](../AGENTS.md) docs index — a line for this file.
- [`docs/decisions.md`](decisions.md) — the elapsed-days rule and the two rejected alternatives. It
  qualifies: a constraint that shapes future work, and something rejected so it isn't re-proposed.
- [`docs/open-work.md`](open-work.md) — nothing to prune; rest days were never on the backlog.

## Tests

Free, once the code lands — these already exist and will fail if the change is wrong:

- `site-examples.test.ts` parses all four example libraries and merges each into the seed. A rest
  entry the schema refuses, or a renumbered day that collides, fails here.
- `docs-samples.test.ts` and `site-samples.test.ts` run every complete library sample in the docs and
  on the site through the real `parseLibraryYaml`. A mirror teaching a shape the schema refuses fails.
- `seed-library.test.ts` ([:104](../src/storage/__tests__/seed-library.test.ts#L104),
  [:197](../src/storage/__tests__/seed-library.test.ts#L197)) maps `week.workoutId` and needs the
  union guard before it compiles.

To write:

| suite | cases |
| --- | --- |
| `schema` (via `yaml-mapping.test.ts`) | each of the four refines refuses; a valid rest week parses; a rest week round-trips through `serializeLibraryYaml` with no `workout:` key |
| `program.test.ts` | `resolveWorkoutForWeek` returns null for a rest week; `findProgramWeek` still finds it; `programWeekNumbers` counts its week |
| `merge.test.ts` | a rest week does **not** trip `unknownWorkout` — this is the regression most likely to be introduced |
| `selectors.test.ts` | the elapsed-days table above, driven through the injected `now`: 0/1/2 days with one rest slot, two consecutive rest slots, a rest slot at the very start of a program with no logged session, `skipTo` pointing at the next non-rest slot, and `skipTo === null` for an all-rest program |
| `today.test.tsx` | the rest card renders the day label and notes and has **no** Start button; "Train anyway" navigates with the next slot's params. Drive one assertion in `pt` — an English-locale assertion cannot catch a hardcoded English string |
| `program-editor.test.tsx` | toggling rest hides the workout picker; saving a rest week persists `restDay: true` and no `workoutId`; a program of only rest weeks is refused |
| `program-detail` | a rest week's card has no Start button (new file, or fold into `programs.test.tsx`) |

**Prove the `merge.ts` and elapsed-days tests fail against the bug they pin** by reintroducing it —
both are the kind that pass either way if written carelessly.

## Optional, and easy to cut

`format-mirrors.test.ts` parses the **exercise** type tables in the two mirrors and diffs them against
`schema.ts`, so nothing checks the `weeks[]` table in either — the exact eyeball pass that failed the
first time it mattered, and this change adds a row to it. Extending the parser to the program-week
table (`weeks[].x` rows in both mirrors, diffed against `rawProgramWeekSchema`) is maybe 40 lines and
closes that gap permanently. Worth doing while the table is already open; cut it if the PR is getting
long, and it stands alone as a follow-up.

## Deliberately not in scope

Named up front, because each is a rewrite if discovered halfway.

- **Streak semantics are unchanged.** `currentStreak` counts consecutive calendar days with a logged
  session, so a rest day still breaks the streak — follow a program perfectly and the number reads 1.
  That is arguably wrong once rest days exist, but "days you did the right thing" is a different stat
  from "days you trained", and choosing between them is its own decision with its own history
  implications. Flagged here so it is a known consequence rather than a bug report.
- **No calendar dates.** A program week's `day` stays a freeform label, never bound to a real weekday.
  The elapsed-days rule counts days since your last session, not days of the week.
- **No rest-day notifications.** The existing opt-in reminder is derived from the log and stays that
  way.
- **No logged rest.** A rest day writes no session file and appears nowhere in History.
- **Program detail's sort is left alone** — it orders by week number only, while `nextWeekAfter`
  orders by `(week, day)`. Rest days make the mismatch more visible, and aligning them is a
  one-line change worth doing, but it changes the order of existing multi-day programs on that
  screen. Raise it separately rather than smuggling it in here.

## Verification beyond the suite

Per `docs/verifying-in-the-browser.md`, driven in the running app:

1. Import a two-day program with a rest day between and confirm the Today card shows rest, then
   confirm "Train anyway" starts the right workout.
2. Log a session, reopen, and confirm the card is the rest card; there is no way to fake elapsed days
   in the browser, so the day-2 transition is covered by the injected-clock unit test rather than by
   hand.
3. Open program detail for a seeded program and confirm the rest rows read correctly and offer
   nothing to start.
4. Round-trip: export the library, confirm `rest_day: true` in the file, re-import it, confirm no
   diff.
