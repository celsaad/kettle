# Up next sheet — plan

> **Executed; kept for its rationale, and for the device checks it leaves open.** Written against the
> tree at `2c70582`, before any code, and agreed from a design canvas
> (https://claude.ai/code/artifact/ff56f717-1875-48c1-8927-06de6fd60983 — the owner's private link)
> that ran three layouts against the seed library and against a real circuit-heavy library. The target
> is header option **H1**, sheet layout **B**, circuit display **option C**. The other layouts are
> under "Not doing" so they aren't re-proposed.
>
> Wherever the build differed from the text below, the text was corrected in the same commit, so what
> follows is what shipped. Three things it didn't foresee:
>
> - The swap and add pickers needed a small restructure rather than two props each, because they
>   render inside `content`.
> - The swap picker reopened by itself after its hold ended under it. That is the same stale-state
>   shape as the sheet's index, and it's now fixed the same way.
> - The runner's two private clock formatters became one shared `formatClock`.
>
> Checked in the browser in en and pt: layout, closing, and the runner leaving the accessibility tree.
> **Not done:** the device checks. Those are hardware back closing only the sheet, TalkBack and
> VoiceOver focus staying inside it, and the layout at large text sizes, and a browser pass can't
> reach any of them.

The runner answers "what now" and, through the Next card, "what's one step after this". Nothing
answers "what's left": how many rounds of this circuit, what the finisher is, whether the long stretch
is nearly over. This adds a read-only sheet that does, opened from the workout name at the top of
every step screen.

On screen the sheet is titled **Coming up**, not "Up next": Home already says NEXT UP for the next
*workout*, and the runner's card says NEXT, so a third near-identical label would blur all three.

## Decisions taken

1. **It opens from the workout name, marked by a chevron and nothing else.** The name in the runner
   header ([`session.tsx:429`](../src/app/session.tsx#L429)) becomes a `Pressable` wrapping the name
   and a 12px chevron in the same muted tone. A chip and a brightened title were both drawn and
   rejected as too loud for a header read mid-set. `hitSlop` brings it to 44px so the row doesn't
   grow. It gets `accessibilityRole="button"`, `accessibilityState={{ expanded }}`, and an
   `accessibilityHint` rather than a label: the visible name is already its accessible name, and
   Voice Control matches visible words.

   The header is on every step screen and never drops at large text sizes. That's why it beat making
   the Next card tappable: the card disappears above 1.5× text
   ([`session-next-card.tsx:16`](../src/components/session-next-card.tsx#L16)), which would have taken
   the feature away from exactly the people who most need a list they can read.

2. **A sheet inside the runner, not an expo-router modal route.** The step list is `useState` inside
   `useSessionRunner` and changes mid-session (add set, drop set, swap, ad-hoc add). A route could
   only read it if runner state moved into a store, which rewires the highest-risk file in the app
   for a view that only reads. The swap picker made the same call for the same reasons
   ([`session-exercise-picker.tsx:31`](../src/components/session-exercise-picker.tsx#L31)).
   `ActiveSession` renders it at the `SafeAreaView` level rather than inside `content`, so the dim
   covers the whole screen and the sheet reaches the bottom edge.

3. **Layout B, the timeline.** The sheet starts just below the runner header, at a fixed inset (the
   canvas used 104pt on an 844pt screen), not a measured one, since `onLayout` costs a frame of
   visible jump. It has a pinned header strip: the title on the left and a × on the right (32px
   visible, `hitSlop` to 44). The strip sits outside the `ScrollView`, for `ModalHeader`'s reason: a
   close control that scrolls away is a close control you have to go looking for. Below it is a rail
   of nodes: NOW at the top, then what's left, then an **END OF WORKOUT** terminal node. The backdrop
   and the × close it; nothing else in it is interactive.

   **This deliberately breaks the list house rule** (hairline rows, no fills). It is a different kind
   of list, not a subtly different copy of the normal one, and it lives inside the always-dark runner
   with its own vocabulary. That exception goes in the decision log when it ships. Otherwise the next
   review reads it as drift.

4. **The NOW row follows the step kind.** The canvas only drew it over a rest, and the sheet opens
   from every step screen:
   - **rest:** the countdown (`restRemainingSec`) and "of 1:15".
   - **interval:** the countdown, or elapsed time for a count-up cardio.
   - **hold:** elapsed time counting up (`holdElapsedSec`), the number the hold screen shows.
   - **reps:** the exercise name and "set 1 of 2", with no clock.

   The node and the number use the runner's existing hue split: calm for rest, warm accent for work.
   The clock is **not** a live region, which would announce every second. It takes the rest numeral's
   `maxFontSizeMultiplier` of 1.3.

   The live number is the answer to the auto-close (item 7): the sheet is about to vanish, and it says
   so.

5. **Circuits: option C.**
   - **The first round with work left is spelled out**, under its own crumb text ("CIRCUIT · ROUND 2
     OF 3"): each remaining member with its target. "First round with work left" rather than "the
     current round" is what makes a rest *between* rounds, or the last member of a round, show the
     next round in full rather than an empty one. A single-round circuit says "CIRCUIT · 10 LEFT"
     instead of "ROUND 1 OF 1".
   - **The rounds after it collapse into one node**, "1 MORE ROUND" / "2 MORE ROUNDS", listing member
     names. The canvas's layout B drew "ROUND 3 OF 3" here. The count form is option C's own wording
     and reads right at any number of rounds left.
   - **A later circuit is one node:** "CIRCUIT · 3 ROUNDS" and its member names.

6. **Long lists scroll, one name per line.** No clamping and no "+3 more". The sheet is read-only, so
   a hidden name would be unreachable. The real stress cases are a mobility circuit of 27 holds and a
   workout of four five-member circuits back to back, and both scroll.

7. **It closes itself when the step changes, and on hardware back.** The open state is the index it
   was opened at, `upcomingOpenAt: number | null`, and the sheet shows while that equals
   `runner.stepIndex`. It's cleared in the render that sees the index move, the way the runner's
   per-step reset adjusts state during render, rather than merely left to stop matching. A stale index
   would reopen the sheet the moment Prev stepped back onto it.
   Keying on the index alone is enough *here*, unlike the runner's per-step reset (see the decision
   log): the two mutations that don't move the index, swap and ad-hoc add, start from controls the
   sheet covers.

   **Android hardware back closes the sheet and nothing else**, through a `BackHandler` listener
   that returns `true` while it's open. Without it, back falls through to the session route. That is
   its own bug; see "Found while planning".

8. **Circuit names are not part of this.** A circuit block has no name in the YAML, only an optional
   `id`, so four circuits in one workout all read "CIRCUIT · N ROUNDS" and only their member names tell
   them apart. An optional `name` is the right fix and reads best on the canvas, but it is a format
   change, with its own gate: `schema.ts`, all three mirrors, `format-mirrors.test.ts`. It's the named
   follow-up. The sheet takes a name as soon as one exists. Numbering ("CIRCUIT 3 OF 4") was drawn and
   rejected: it says where a circuit sits, not what it is, and the rail already shows where.

## Not doing

- **No jump, skip, reorder or swap from the list.** Session entries are append-only, undo is one
  level, and a circuit's steps are interleaved. A row that moved the runner would be a second way to
  break all three.
- **No time-remaining estimate.** A reps set has no duration, so
  [`workout-shape.ts`](../src/state/selectors/workout-shape.ts) counts only its rest. "About 12 min
  left" would undercount most on strength days, which is when people would ask.
- **No flat step list.** 5×5 is 25 rows, and a 3×4 circuit repeats every name three times.
- **No drawing per row.** Twenty 56px drawings would double the list's height for decoration. The Next
  card keeps its own.
- **No animation.** The swap picker doesn't animate either, and a sheet that slides in adds a
  reduced-motion branch for no information.
- **Layouts A (rows) and C (ledger)** from the canvas. A was cheapest and matched the picker, but gave
  no sense of position beyond order, and it has no NOW row to make the auto-close legible. C's
  one-line "2×" ledger was densest, but its count column had to be learned, and clamping names to one
  line hid most of a five-member circuit.

## Shape of the code

1. **`src/hooks/session-outline.ts`, new and pure.** `upcomingOutline(steps, stepIndex)` returns
   descriptors, never strings (house rule: no user-facing string in the logic layer):

   ```ts
   type WorkStep = Exclude<RunnerStep, { kind: 'rest' }>; // the sheet formats targets from it
   type UpcomingItem =
     | { kind: 'exercise'; memberKey: string; step: WorkStep; left: number; unit: 'set' | 'round' | 'minute' | null; started: boolean }
     | { kind: 'rest'; seconds: number } // standalone Rest blocks only
     | { kind: 'round'; blockIndex: number; round: number; rounds: number; members: UpcomingMember[] } // spelled out
     | { kind: 'moreRounds'; blockIndex: number; count: number; names: string[] }
     | { kind: 'circuit'; blockIndex: number; rounds: number; names: string[] };
   // upcomingOutline(steps, stepIndex): UpcomingItem[]
   ```

   Built from the **live steps, never `workout.blocks`**. Swaps, added sets, program overrides and ad
   hoc sessions then come for free, and a workout cut short by `MaxStepsPerWorkout` lists exactly what
   the runner will run. The rules that aren't obvious:
   - **Outside circuits, group by `memberKey`, not `blockIndex`.** A swap issues a new key, so the
     substitute shows under its own name with the remaining count. `started` is whether the current
     step belongs to that member, which is what picks "2 more sets" over "2 sets".
   - **Inside circuits, count rounds from `step.circuit.round`, never by counting a member's steps.**
     A `hiit` member runs its own full round count on every visit
     ([`session-steps.ts:125`](../src/hooks/session-steps.ts#L125)), so counting steps turns three
     visits of an 8-round HIIT into "24". Consecutive steps of one member within a visit fold into
     one entry. The rounds are the ones *present in the list*, so a truncated workout counts only
     what will run.
   - **Inside the circuit you're in, the rest of the visit in progress is the NOW row's.** A HIIT
     member's remaining intervals are not "the next member", so they aren't listed as one.
   - **Rest steps carry no name** (the `rest` variant has no `exerciseName`), so a standalone Rest
     block renders a translated "Rest", not the library's name for it.
   - **Never throws.** `stepIndex` past the end is how a parked ad-hoc session looks
     ([decision log](decisions.md)), and it returns an empty outline. A throw here ends the session:
     [`session.tsx`'s error boundary](../src/app/session.tsx#L47) abandons it.
   - **The END OF WORKOUT node is the sheet's call**, from `runner.isAdHoc`, not the outline's. An
     ad-hoc session parks rather than ending when its queue runs out, so it gets no END node.

2. **Move `formatHoldTarget` and `formatRepsTarget` out of
   [`use-session-runner.ts`](../src/hooks/use-session-runner.ts#L118)** and into `session-steps.ts`,
   exported. The sheet has to format targets exactly as the Next card does, and importing the runner
   initialises `expo-audio` on import, which is why `session-steps.ts` exists at all.

3. **The runner exposes `upcoming`**, as `useMemo(() => upcomingOutline(steps, stepIndex), [steps,
   stepIndex])`. The runner re-renders every second. `steps` only changes identity on a mutation.

4. **`src/components/session-upcoming.tsx`, new.** It uses `RunnerColors` and `ThemedText` types
   throughout, since `ListRow` and `ModalHeader` read the shell theme. The item list is a memoised
   child keyed on the outline, so the 1Hz NOW clock doesn't re-render thirty rows.

5. **`session.tsx`:** the header `Pressable`, `upcomingOpenAt` and the sheet render. The `BackHandler`
   listener lives in the sheet component instead, which is mounted exactly while the sheet is open.

6. **i18n**, in all three bundles, under `session.upcoming.*`:
   - `title`, `hint`, `now`, `nowRest`, `end`, `rest`, `detail`, and the three target forms
   - counted forms: `sets`/`moreSets`, `rounds`/`moreRounds`, `minutes`/`moreMinutes`, and the
     kickers `circuitRounds`, `circuitLeft`, `roundsAfter`

   Details reuse `preview.*` and `session.circuit.crumb` rather than a parallel copy. `ja` takes only
   the `_other` forms. Exercise names are interpolated, never translated.

## Accessibility

- **The header control:** item 1.
- **Focus stays in the sheet.** The runner beneath is `aria-hidden` while it's open, which RN maps onto
  both native props and react-native-web honours. The overlay root also carries
  `accessibilityViewIsModal` and the VoiceOver escape gesture: the root rather than the sheet, because
  iOS hides only a modal view's siblings. **The swap and add pickers had neither.** They got the same
  treatment in this PR, which took a small restructure rather than one line each: they render inside
  `content`, so the runner inside it is wrapped in a view that goes hidden instead.
- **The rail and nodes carry no text**, so there is nothing to hide. The backdrop is what's hidden
  (`aria-hidden`, which react-native-web honours where it drops the native props): the × is the named
  way out, and a second "Close" spanning the screen would only be one more thing to swipe past.
- **Each entry is one accessible element that reads its own children**: a name and its detail, or the
  NOW row's kicker, name, clock and caption. There's no composed label that could drift from what's on
  screen. Circuit kickers are headers, and the names under them read one by one.
- **No new colours.** The calm node against `backgroundElement` estimates at about 4.2:1 and the warm
  one at about 4.7:1, both over the 3:1 a meaningful graphic needs. Measure them and record them in
  `theme.ts`, as the others are recorded.
- **Large text:** the sheet scrolls, so nothing clips. The runner screens clip rather than scroll,
  which is the reason the entry point isn't the Next card.

## Tests

`src/hooks/__tests__/session-outline.test.ts`, beside `build-steps.test.ts`. The fixtures are real
`buildSteps` output from small workouts, not hand-built step arrays, so the tests follow the expansion
if it changes. One fixture copies the *shape* of a circuit-heavy library: four circuits, `hiit`
members at `rounds: 1`, single-round warm-up and finisher. It does not copy anyone's actual file.

- mid-sets of a reps exercise, and of a hold
- resting after the last set of an exercise
- a standalone Rest block, and inter-set rest staying hidden
- mid-round in a circuit
- the rest *between* rounds, where the next round is the one spelled out
- the last member of the last round, with no "more rounds" node
- a single-round circuit, counted as "N left"
- a `hiit` member inside a circuit: one entry per visit, rounds not inflated. **Prove it fails** by
  counting steps instead.
- mid-way through a standalone `hiit`, with the remaining rounds
- after `swapExerciseForMember`, showing the new name and the remaining count
- after `addSetForMember`
- an ad-hoc queue, and a parked ad-hoc session (index past the end, empty)
- the last step, empty
- a truncated step list, ending where the runner stops
- a circuit with a member whose exercise is missing, with member counts over the resolved members

In [`session.test.tsx`](../src/app/__tests__/session.test.tsx):

- tapping the name opens the sheet
- it closes when the rest ends (fake timers, `async` `act`)
- the × and the backdrop close it
- the screen is driven in `pt`, since an English assertion can't catch a hardcoded string

**Not testable here, so checked by hand:**

- in the browser ([`verifying-in-the-browser.md`](verifying-in-the-browser.md)): the layout at large
  text, and the scrolling
- on a device: hardware back closing only the sheet, and TalkBack/VoiceOver focus staying inside it

## Order of work

1. `session-outline.ts` and its tests. It is pure, so it lands first and on its own.
2. Move the two target formatters, with no behaviour change.
3. The runner exposes `upcoming`.
4. The sheet component and the locale keys.
5. The header control, `upcomingOpenAt`, and `BackHandler`.
6. The picker's two accessibility props.
7. The screen tests, then the browser and device checks.
8. On shipping: the decision-log entry for the rail exception, a `CHANGELOG.md` Unreleased entry, and
   pruning the open-work bullet.

## Found while planning — outside this scope

**Leaving the runner by back or swipe strands the session unfinished.** The session is a
`presentation: 'modal'` route ([`_layout.tsx:113`](../src/app/_layout.tsx#L113)). Nothing in `src`
intercepts Android hardware back, and nothing sets `gestureEnabled: false`, so iOS's swipe-down
dismiss is on. The runner has no unmount cleanup: only its completion paths call `completeSession`
([`use-session-runner.ts:609`](../src/hooks/use-session-runner.ts#L609), `:827`), and only the
error boundary calls `abandonActiveSession`. So a session left that way keeps no `endedAt`. Its sets
are on disk, but by the error boundary's own account it counts as zero minutes and `exerciseHistory`
skips it.

Code-verified; the gestures themselves still want a device. It isn't fixed here, because the fix is a
product call: confirm before leaving, or finish-and-save on leave. This sheet takes back while it's
open, so it adds no new way in. Logged in [`open-work.md`](open-work.md).
