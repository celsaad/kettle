# Timed holds that end themselves — plan

> **Executed.** Kept for its rationale, not as a backlog — the reasoning that outlives the commit is
> in the decision log's §12.2 entry. Written against `1ad2e82` and shipped on a branch stacked on
> PR #49 rather than on `master`: that PR rewrites `expandExercise`'s `timed_hold` case — the exact
> lines this touches — and edits two of the four format mirrors.
>
> Three things came out differently from the plan below, all recorded in the commit: the logged
> `holdSec` needed **clamping to the hold's end** (a hold ending while backgrounded logged the time
> you were away — 60s for a 25s plank — which is the very overrun this feature removes, arriving
> through the back door); `restTargetSecRef` was **renamed `stepEndSecRef`** and now carries a hold's
> end too, so the catch-up and notification read one field instead of re-branching on step kind; and
> the badge summary needed its own `summary.holdOpen` string, since the shared one renders "3 × s"
> with no duration to interpolate.

A `timed_hold` step counts up and never ends on its own: the only way out is the Done button
([`session-hold.tsx:150`](../src/components/session-hold.tsx#L150)). This plan gives a hold with a
configured target an end of its own, and gives the untargeted "hold as long as you can" case a way to
be expressed for the first time.

**The rule, in one line:** a hold ends by itself at `hold_sec_max ?? hold_sec_min`; a hold with
neither counts up forever, exactly as `cardio` already does without `duration_sec`.

## Why

Three arguments, in the order they matter.

**The log is currently wrong for hands-occupied holds.** In a dead hang or an L-sit you cannot reach
the phone. The milestone chime fires at target
([`use-session-runner.ts:469`](../src/hooks/use-session-runner.ts#L469)) and the clock then keeps
running while you dismount and pick the phone up, so `holdSec` is recorded with several seconds of
dismount in it. That is a data-quality bug, not only an ergonomic one, and it is the reason to do
this at all.

**The codebase already made this decision once.**
[`session-steps.ts:227`](../src/hooks/session-steps.ts#L227) sets `countUp: !hasDuration` for cardio:
a configured duration auto-advances, an absent one counts up. A hold with a target is structurally
the same step. This plan applies an existing decision rather than introducing a new one.

**The progress bar becomes honest.**
[`session-hold.tsx:72`](../src/components/session-hold.tsx#L72) already scales the fill to
`holdSecMax ?? holdSecMin`. Today that bar can sit pegged at 100% indefinitely. Ending the step when
the bar fills makes the bar mean something, and it is why the end is the *top* of the range rather
than the bottom — no second concept is introduced, the end is the number the bar was already drawn
against.

Ending at `hold_sec_min` instead was rejected: it would truncate every max-effort hold to its floor
and kill hold progression, since no set could ever log more than the prescription. Ending at the top
of the range leaves `hold_sec_min` as what §12.2 made it — a mark you cross, with its chime.

The failure path costs nothing either way. Drop out at 22s of a 30s hold and you still press Done and
still log 22; auto-end only touches the set you complete.

## What this is not

Named up front so the scope cut is a decision rather than a discovery.

- **The display still counts up.** §12.2 stands: the number on screen is what gets logged as
  `holdSec`, and a countdown would break that correspondence. The 3-2-1 ticks below give the audible
  countdown without the display change. This is the one call in here a reasonable person could argue
  the other way, so it is written down rather than left implicit.
- **No "keep going" affordance** past the auto-end. It reintroduces the reach-for-the-phone problem
  the feature exists to remove. Open-ended holds are expressed by omitting the target, not by
  overriding the end at run time.
- **No global "auto-end holds" preference.** Whether a hold is prescribed or max-effort is a property
  of the exercise, not of the person — one workout can contain both, which a single switch cannot
  serve.
- **No new weighted-hold or tempo fields.** Those stay in `open-work.md` where they are.

## The format change

`hold_sec_min` becomes optional. This is a YAML format change and takes the four-mirror gate in
`AGENTS.md`.

[`schema.ts:41-50`](../src/domain/schema.ts#L41-L50):

- `hold_sec_min: z.number().positive().optional()`.
- The existing refine (`hold_sec_max >= hold_sec_min`) has to stop comparing against `undefined`.
- Add a second refine: **`hold_sec_max` requires `hold_sec_min`.** A range needs both ends, and a
  bare max is a fixed target written oddly. Rejecting it keeps one shape per meaning.

So the three legal shapes are: neither (open-ended, counts up forever), min only (ends at min), min
and max (ends at max, chimes at min).

[`types.ts:20-21`](../src/domain/types.ts#L20-L21) — `holdSecMin?: number`, and the doc comment above
it now has two optional fields to explain.

[`yaml-mapping.ts:103-104, 179-180`](../src/domain/yaml-mapping.ts#L103-L104) — pass-through both
ways and already undefined-tolerant, but **verify the export round-trip omits the key** rather than
emitting `hold_sec_min: null`. Cardio's optional `duration_sec` goes through the same path, so the
behaviour should already be right; confirm rather than assume.

## Code changes, by layer

### Step model — `session-steps.ts`

`holdTargetSec` becomes optional, and the step gains `holdEndSec?: number`, computed in
`expandExercise` ([lines 85-113](../src/hooks/session-steps.ts#L85-L113)) as
`holdSecMax ?? holdSecMin`. Undefined means no auto-end.

Precomputing it on the step rather than deriving it in the runner follows cardio's `countUp`, which
is decided in this same function for the same reason.

### Runner — `use-session-runner.ts`

The high-risk file. Three effects gate on `isCountdownStep`
([line 164](../src/hooks/use-session-runner.ts#L164)) and an auto-ending hold needs two of the three
but not the first, so the concept has to split rather than widen:

- **`isCountdownStep` keeps its current meaning** — it drives the countdown *display* through
  `restRemainingSec`/`restTargetSecRef`, which a hold must not join.
- **A new "this step has an end" predicate** covers countdown steps *and* holds with `holdEndSec`. It
  is what the background catch-up and the notification fallback key off.

Then:

- **Ticking effect** ([462-486](../src/hooks/use-session-runner.ts#L462-L486)): the hold branch
  already computes `elapsed`. Add `advance()` when `elapsed >= holdEndSec`, and `playTick()` in the
  final three seconds — the same 3-2-1 rest and countdown intervals already get
  ([line 481](../src/hooks/use-session-runner.ts#L481)). For a hold with the phone on the floor the
  ticks are worth more than the auto-advance itself: they are how you know to prepare the dismount.
- **Milestone chime** ([469](../src/hooks/use-session-runner.ts#L469)): guard the now-optional target,
  and **fire it only when `holdTargetSec < holdEndSec`.** On a min-only hold the chime and the
  auto-end land on the same instant, and two sounds in one second reads as a glitch.
- **AppState catch-up** ([490-502](../src/hooks/use-session-runner.ts#L490-L502)): the hold branch
  currently only refreshes `holdElapsedSec`. It must also `advance()` when the hold's end passed while
  backgrounded, the way the countdown branch does. **This is the correctness core of the feature** —
  phone on the floor with the screen asleep is the normal case for a hold, not an edge case, and
  without this a hold that ends off-screen simply overruns.
- **Notification fallback** ([505-525](../src/hooks/use-session-runner.ts#L505-L525)): extend to
  auto-ending holds, with `remaining` computed from `holdEndSec` instead of `restTargetSecRef`. Note
  the dependency-array comment at [521](../src/hooks/use-session-runner.ts#L521) — the ref is
  deliberately not a dependency and the *state* is the change signal. Whatever carries the hold's end
  has to satisfy the same contract or the notification fires at the wrong time.
- **Its copy is hardcoded English** — `'Rest complete'` / `'back to work'` at
  [511](../src/hooks/use-session-runner.ts#L511), against the house rule. Route both through `t()`
  while adding the hold's own copy; adding a second hardcoded string beside the first is not on.
  `scheduleRestCompleteNotification` ([`safe-notifications.ts:49`](../src/hooks/safe-notifications.ts#L49))
  takes title and body as arguments, so only the name is rest-specific — rename to
  `scheduleStepCompleteNotification`. **[`watch-remote-plan.md`](watch-remote-plan.md) carries the same
  fix**; whichever lands first takes it, and the other's entry gets pruned rather than both editing the
  same line.
- **No change to logging.** Auto-advance goes through `advance()`, which commits the hold at
  [348-350](../src/hooks/use-session-runner.ts#L348-L350) from `computeElapsedSec()`. An auto-ended
  hold logs itself exactly as a tapped one does. Worth a test, not worth a change.

### UI — `session-hold.tsx`

- `targetSec` becomes optional; thread `holdEndSec` from
  [`session.tsx:262-271`](../src/app/session.tsx#L262-L271).
- **Open-ended hold: no bar and no marker at all**, not an empty track — there is no scale to draw.
  The `NaN%` guards at [60-76](../src/components/session-hold.tsx#L60-L76) stay: they cover a program
  override writing `hold_sec_min: 0`, which is still unvalidated from either direction. The
  open-ended case is now a first-class path *above* them rather than a second thing they catch.
- **Draw the minimum marker only when there is a range.** With the end at the top, a min-only hold's
  marker sits at 100% — the end of the track, where the fill already arrives. Drop it there.
- **The Done-button comment at [143-149](../src/components/session-hold.tsx#L143-L149) becomes
  false.** It states in as many words that a hold "never auto-advances" and that the button is "the
  *only* way out of the step". It has to be rewritten, and its actual point — that Done is the primary
  action and outranks Pause — survives, since Done is still how you end a hold early or end an
  open-ended one.
- New caption for the untargeted case. `session.hold.caption` is `"target {{target}}s · counting up"`;
  the open-ended one says only that it is counting up.

### a11y and i18n

- New keys in **both** `en.json` and `pt.json`: the open-ended caption, the open-ended announcement
  variant of `session.a11y.hold` (`"hold {{target}} seconds"`, [en.json:196](../src/i18n/locales/en.json#L196))
  used by `use-session-announcements.ts`, and the notification title/body pulled out of the runner.
- No new colours, so no contrast work. No new controls, so no new touch targets.

### Derived display — `selectors.ts`

Both hold estimates read `holdSecMin` directly and will not compile once it is optional:

- `estimateExerciseSeconds` ([38](../src/state/selectors.ts#L38)) — estimate from `holdSecMax ??
  holdSecMin`, since that is now how long the step actually runs, and contribute `0` for an
  open-ended hold. Cardio without a duration already contributes `0` at
  [45](../src/state/selectors.ts#L45), so this is the existing convention rather than a new one.
- `memberVisitSeconds` ([56](../src/state/selectors.ts#L56)) — same treatment.

### In-app editor — `exercise-form.ts`

The forms never touch the zod schema, so the constraints have to be mirrored here by hand.

- [79](../src/domain/exercise-form.ts#L79) — `holdSecMin` gains `optional: true`, exactly as cardio's
  `durationSec` has at [84](../src/domain/exercise-form.ts#L84).
- [195](../src/domain/exercise-form.ts#L195) — `num('holdSecMin')` → `optionalNum('holdSecMin')`.
- `validateConfig` ([100-114](../src/domain/exercise-form.ts#L100-L114)) needs the max-requires-min
  rule, which is its **first cross-field check** — the loop is per-field today. Note while in there
  that it has never enforced `hold_sec_max >= hold_sec_min` either, though the schema does; that gap
  predates this work. Fix it in the same pass if the shape allows, and say so in the commit.

## Documentation and the site

The format change means all four mirrors move together, in the same PR.
`format-mirrors.test.ts` fails until both type tables flip `hold_sec_min` to optional — that is the
gate doing its job, not a surprise.

1. **[`docs/authoring-exercises-yaml.md`](authoring-exercises-yaml.md)** — the type table at line 82,
   plus prose: the three legal shapes, what each one ends at, and the fact that omitting the target is
   how you write a max-effort hold. The samples at 297-298 and 365-366 are complete libraries checked
   by `docs-samples.test.ts`; leave them as ranges and consider adding a targetless one so the new
   shape is exercised by that suite.
2. **[`docs/product-plan.md`](product-plan.md)** — four places, and one of them is currently a lie
   this feature *fixes*:
   - line 59, the type table.
   - line 77, "a `timed_hold` block → a countdown/count-up hold timer".
   - **line 253** already claims that `timed_hold` is among the types that "auto-advance to the next
     block". That is false today. It becomes true here, so the line is corrected by shipping rather
     than by editing — check the surrounding wording still reads right for the open-ended case.
   - line 339, open question 2. It is settled and stays settled; append that count-up display survived
     and auto-end was added on top, so a later reader does not take the settled entry as ruling this
     out.
3. **[`site/format.html`](../site/format.html)** — the table rows at 346-353 and the sample at
   780-781. Easiest of the four to forget and the one an outside author actually reads.
4. **[`docs/decisions.md`](decisions.md)** — amend the §12.2 entry at ~493 rather than adding a new
   one: the display decision is unchanged, the end of a hold is the top of its range, and both
   rejected alternatives (end at the minimum, a global preference) belong there so they are not
   re-proposed. This clears the "reasoning that shapes future work" bar; nothing else here does, and
   the rest goes in the commit message.
5. **[`site/examples/`](../site/examples/)** — three of the four libraries use `hold_sec_min`
   (`beginner-full-body`, `bodyweight-no-equipment`, `push-pull-legs-6-weeks`). They are checked end to
   end by `site-examples.test.ts`, so a rename would break them loudly, but an *optional* field will
   not — read each hold and decide whether auto-end is what that exercise means. A max-effort plank in
   a beginner library is the obvious candidate for the new targetless shape.
6. **[`site/examples.html`](../site/examples.html)** and **[`site/index.html`](../site/index.html)**
   both mention holds and neither is checked by any test. Read the prose; edit only if it describes
   runner behaviour that changes.
7. **[`AGENTS.md`](../AGENTS.md)** docs index and **[`open-work.md`](open-work.md)** — add this plan
   to the index while it is unexecuted, and delete the entry from both when it ships.

## Behaviour change for existing libraries

`hold_sec_min` is required today, so **every hold in every existing library gets an auto-end**, and
since most set only the minimum, most will end at the minimum. Someone running "60s floor, hold to
failure" is cut at 60.

That is defensible — the prescription said 60, and until now there was no way to write "to failure" —
but it is a silent change to behaviour on data users already have, and it is the single most likely
thing to generate a complaint. It belongs in the commit message and in the release note, with the fix
named: delete `hold_sec_min` to get the old count-forever behaviour back.

No migration code. The old files stay valid, they just mean something slightly narrower than they did.

## Tests

- `build-steps.test.ts` — `holdEndSec` for each of the three config shapes.
- `use-session-runner.test.tsx` — auto-advance at the top of a range and at a bare minimum; **no**
  auto-advance for a targetless hold; the milestone chime suppressed when it would coincide with the
  end; an auto-ended hold logging the same `holdSec` a tapped one does; the backgrounded hold that
  ended while away advancing on foreground return. The suite already drives fake timers and asserts on
  `holdElapsedSec` ([480-492](../src/hooks/__tests__/use-session-runner.test.tsx#L480-L492)), so the
  shape to copy is there.
- `session-hold.test.tsx` — no bar and no marker without a target; no marker on a min-only hold.
- Schema tests — `hold_sec_max` without `hold_sec_min` rejected; all three shapes accepted.
- **Prove the background test fails against the bug**, per the house rule. It is the case that cannot
  be checked by eye in a browser and the one most likely to be quietly wrong.
- `format-mirrors.test.ts`, `docs-samples.test.ts`, `site-samples.test.ts`, `site-examples.test.ts`
  all run themselves; they will fail until the mirrors land.

Then drive a real session on a device, per the standing rule on this file: a 15-30s range hold, a
min-only hold, and a targetless one, with the screen allowed to sleep through one of them. The audio
cues and the background path are exactly what jest cannot reach.

## Order of work

Branch off `master` first.

1. Schema, types, mapping — the format change, with its tests.
2. Step model and `selectors.ts`, which is where the optional field breaks compilation.
3. Runner: split the countdown predicate, then auto-advance, ticks, chime guard, background catch-up,
   notification.
4. UI, i18n bundles, a11y announcements.
5. `exercise-form.ts` and its validation.
6. The four mirrors, the decision-log amendment, the site examples.
7. `pnpm test`, `typecheck`, `format`, `lint`; then the device pass.
