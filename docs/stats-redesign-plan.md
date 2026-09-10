# Stats redesign — plan

> **Executed; kept for its rationale, not as a backlog.** The approach for reworking the Stats screen
> ("Your numbers", [`analytics.tsx`](../src/app/analytics.tsx)), written down and agreed before any
> code. The target layout was Option A of the design canvas, at four weeks rather than the eight it
> was drawn with (https://claude.ai/code/artifact/18cbce3e-f39b-431c-af3b-3e1064c43c60 — the owner's
> private link). The canvas's other two options are recorded under "Not doing" so they aren't
> re-proposed, and one of them is the named follow-up. The reasoning that outlives the commits — why
> the fixed-hold rule sits in `exerciseProgress` and not `entryBest` — is in
> [`decisions.md`](decisions.md).
>
> Four things came out differently from the plan below. `sessionsPerWeek` was deleted together with
> `WeekBars` in the screen commit rather than in step 2, so that every commit builds on its own. Steps
> 4 and 5 landed as one commit, since the old screen tests asserted the flat rows the rework hides.
> The held-steady toggle is text ("Show" / "Hide"), following import's "Show all", rather than the
> chevron the canvas drew. And the THIS WEEK tile's "2h 0m" moved into a locale key along the way,
> because the calendar's row label needed one. **Not done:** the site screenshot
> (`site/assets/img/stats.jpg`) and its alt text still show the bar chart, since the image needs a
> device capture, and the large-text check on a device is still open.

## The problem

On a real log the screen is a few useful numbers followed by a long list that says nothing: thirteen
of the first fifteen "Getting stronger" rows read `45s · no change`, and will read that forever.

- **Most of the rows are structurally unable to move.** A `timed_hold` with a fixed target ends
  itself at `holdSecMax ?? holdSecMin`, and the logged `holdSec` is clamped to that end
  ([`use-session-runner.ts:489`](../src/hooks/use-session-runner.ts#L489)). So a 45s stretch logs 45s
  every time; the only direction it can go is down, by ending early. That is the same reason
  `entryBest` already excludes `hiit` and `emom` — the number is bounded by the exercise's own config
  — and fixed-target holds never got the same treatment. A mobility routine is made of them.
- **Nothing ranks or filters the list.** Every exercise trained twice in eight weeks, most recently
  trained first, so one stretching session puts a dozen flat rows above the ring work that moved.
- **The top half repeats itself.** The all-time tiles are totals, which the screen's own comment
  calls the numbers "nobody asks twice". [`layout-review-plan.md`](layout-review-plan.md) Phase 5
  said to demote them to one line at the foot; that never happened.

## Decisions taken

1. **A training calendar replaces `WeekBars`, over four weeks.** Rows are weeks (oldest first), columns
   are days starting on the calendar's first weekday, each day shaded by minutes trained, today ringed,
   the week's session count in a right-hand column. It keeps the one thing the bars said (turning up,
   per week) and adds which days, how long, and where the gaps are. **Four weeks, not eight**, because
   eight rows of seven cells is too much at large text sizes, and four still shows a lapse.

2. **Getting stronger stays at eight weeks.** Four weeks is two or three sessions of most exercises —
   too few to call a trend. The two sections answer different questions, so they don't have to share
   a window; each heading names its own.

3. **Fixed-target holds leave the trend — in `exerciseProgress`, not in `entryBest`.** An earlier draft
   of this said `entryBest`; that was wrong. Records and the runner's live marker can't fire on a
   fixed hold anyway: the hold stops at the target, a tie is not a record, so nothing can beat a full
   hold. Putting the rule in `entryBest` would thread the library into the runner for no change in
   behaviour. Concretely:
   - "Fixed" means the exercise's **current** library config has `holdSecMin` and no `holdSecMax`.
     Range holds (which can climb toward the top) and max-effort holds (no target) stay.
   - An exercise no longer in the library is kept — there is no config to judge it by.
   - The log doesn't record the config a session ran under, so an exercise changed from fixed to
     max-effort brings its capped history with it. Accepted: it corrects itself as new sessions land.
   - A fixed hold cut short is hidden along with the rest. Stopping a stretch early is not "getting
     weaker", and it's the only signal those rows could ever carry.

4. **Only rows that moved are listed; flat ones fold into one row.** `delta !== 0` is a mover. Gains
   first, ranked by relative change (`delta / max(first, 1)`), then dips by relative size; ties break
   on `lastTrainedAt`, then id, as now. Flat rows collapse into a single "4 held steady" row that
   expands in place. Dips keep the accent colour, not red — the existing rule, unchanged.

5. **Records are marked inline, not given a section.** Nearly every record in the window would also be
   a mover, so a separate list would say most things twice. A row carries "best ever" when its latest
   value is the all-time best of its kind **and** that best was first set inside the window by beating
   an earlier value — `sessionRecords`' rules (strictly greater; a first-ever entry is not a record).
   That needs the whole log, so `exerciseProgress` walks every finished session once, oldest first,
   and applies the window only to the points it returns.

6. **The all-time totals go.** Not demoted to a footer line, as the canvas drew them: that line would
   repeat History's header, which is one screen back and says the same thing in the same words.

7. **Shading uses fixed buckets, not a scale relative to the user's own maximum:** under 30 minutes,
   30–44, 45 and over. Fixed buckets are what a legend can name, and a relative scale would re-shade
   the whole month after one long session. The known cost: minutes aren't effort, so a long stretch
   session shades like a hard one. The log has no better measure (`rpe` is optional and per set).

8. **Trained vs rest must clear 3:1 (WCAG 1.4.11); the steps between levels don't have to.** The
   canvas's lowest step does **not** clear it — `#664028` on the empty cell is about 1.6:1 — so the
   levels are re-derived, not copied. The between-level steps are supplementary: each week's row label
   carries its count and time for a screen reader, and the legend names the buckets. New tokens go
   into `constants/theme.ts` for both schemes with their measured ratios, as that file already records.

## Shape of the change

**Selectors**

- `history-stats.ts`: `trainingCalendar(sessions, weeks, now)` → oldest-first weeks, each
  `{ weekStart, sessions, minutes, days }`, each day `{ date, minutes, sessions, level: 0–3, isToday,
  isFuture }`. Built on the existing private `startOfWeek`, so "this week" still has one definition.
  **`sessionsPerWeek` is deleted** — the screen was its only consumer; its tests move to the new
  function. A session counts wherever `thisWeekStats` counts it (unfinished included, at 0 minutes,
  shading at the lowest trained level), so the tile and the calendar can't disagree about this week.
- `exercise-progress.ts`: takes the library's exercises and returns `{ movers, steady,
  fixedHoldsLeftOut }`; each row gains `bestEver`. The window, the two-session floor and the
  one-kind-per-exercise rule are unchanged.

**Components**

- New `components/training-calendar.tsx`; **`week-bars.tsx` is deleted.** Weekday initials come from
  `formatWeekday` with a new `'narrow'` style. Each week row is one accessible element ("Week of
  Aug 17: 6 sessions, 3h 25m"); the cells are hidden from assistive tech, the way `WeekBars` hid its
  bars behind the column. The date column takes the widest label's measured width, so the rows and
  the weekday header stay aligned at any text size; cells keep a fixed height, as decorative geometry.
- `analytics.tsx`: this-week tiles unchanged → calendar (still hidden on an empty log) → Getting
  stronger (movers, then the held-steady row: `accessibilityRole="button"`, `accessibilityState={{
  expanded }}`, 56px row). The all-time tiles are removed. `Sparkline` and `ProgressRow` are reused.

**Empty and edge states**

- No sessions: tiles only, as now.
- Nothing trained twice: the existing "Train something twice…" line.
- Everything trained twice was a fixed hold: a new line saying those can't show a trend, and what can.
  Without it this user's mobility-only weeks would be told to "train something twice".
- Nothing moved: "Nothing moved in the last 8 weeks." above the held-steady row.

**Strings** (all three bundles; `ja` takes only the `_other` forms): the calendar heading, the week
row's accessible label, four legend labels, the reworded body line and its fixed-holds sentence
(shown only when `fixedHoldsLeftOut > 0`), "best ever", "N held steady", "Nothing moved…", and the
fixed-holds-only empty line. `analytics.perWeek` and `analytics.weekBarLabel_*` are removed.

## Order of work

One PR, one commit per step, so each is reviewable on its own:

1. `exerciseProgress` — exclusion, movers/steady, ordering, `bestEver` — with its tests.
2. `trainingCalendar` with its tests; delete `sessionsPerWeek`.
3. Calendar tokens in `theme.ts`, contrast-checked in both schemes.
4. `TrainingCalendar`, the screen rework, the strings; delete `WeekBars`.
5. Screen tests.
6. Docs, changelog, site.

## Tests

- **`exercise-progress.test.ts`** — a fixed hold is left out and counted; a range hold and a max-effort
  hold stay; an exercise missing from the library stays; movers and steady split on `delta`; gains
  rank by relative change and come before dips; `bestEver` is true for a best beaten inside the
  window, false when the best was set inside the window but the latest fell back, false for a
  first-ever entry, false when the latest only ties a best set before the window; kinds don't mix.
  **Reintroduce the bug to prove it:** drop the exclusion and the fixed-hold test must fail.
- **`history-stats.test.ts`** — four weeks oldest first; days start on Monday or Sunday per the
  calendar (mirroring the `thisWeekStats` cases); two sessions in one day sum their minutes and count
  twice; days after `now` are future; today is flagged; the current week's count equals
  `thisWeekStats(...).sessions`; bucket edges at 29/30 and 44/45; a week spanning a DST change still
  has seven consecutive dates (bites on CI, where `TZ` is set).
- **`analytics.test.tsx`** — a flat exercise isn't listed until the held-steady row is pressed, and the
  row reports `expanded`; "best ever" renders; a fixed-holds-only log gets its own line rather than
  "train something twice"; a week row's accessible label; the all-time tiles are gone; and the `pt`
  test moves to the new strings.

## Docs that go stale in the same PR

- `docs/decisions.md` — the `entryBest` entry says Stats covers "strength work and holds". It becomes
  holds that can grow, plus why the rule sits in `exerciseProgress` (decision 3), which spans the
  selector and the runner and so isn't discoverable from one commit.
- `docs/product-plan.md` §"What's genuinely missing" — "charts sessions per week" and the "strength
  work and holds only" limit.
- `docs/open-work.md` — the `listSessions()` cost entry names `sessionsPerWeek`, and
  `exerciseProgress` now walks the whole log rather than the window. Still unmemoised, for the same
  clock reason.
- `CHANGELOG.md` — `## Unreleased`, written for users.
- **`site/index.html` and `site/assets/img/stats.jpg`** — the alt text describes the bar chart, and the
  screenshot shows it. The image needs a device capture at 1080×2204; the alt text changes with it,
  never before it. If the capture can't be taken in this PR, the PR says so and leaves both alone.

## Verification

`pnpm test`, `pnpm run typecheck`, `pnpm run format`, `pnpm run lint`. Then drive the screen in the
browser per [`verifying-in-the-browser.md`](verifying-in-the-browser.md) for layout in both schemes, and
on a device at the largest system font size — that is the case four weeks was chosen for, and a
browser can't reproduce it.

## Not doing

- **An exercise's own chart, one tap from a row** — the canvas's Option C. It is the follow-up, logged
  in [`open-work.md`](open-work.md), and deliberately its own PR.
- **Sets per week by kind of work** (Option B). A 45s stretch counts as one set, the same as a heavy
  set; it needs a three-colour palette; and it would drop sessions per week.
- **A records section** — see decision 5.
- **A name preview on the held-steady row** ("Ring Rows, Face Pulls and 2 more"). Joining names needs
  locale-aware list formatting for a line that only previews what one tap shows.
- **Anything in `entryBest` or the runner**, the **4w/12w/All window control**, **muscle-group
  balance** (a YAML format change), and **performance work** on the log walk (the open-work entry
  stands: measure first).
