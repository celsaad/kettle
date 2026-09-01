# Making the import path prominent — plan

> **Executed; kept for its rationale, not as a backlog.** Written against the tree at `5859d1c`,
> which is the commit that creates the problem it solves. The slices shipped as separate PRs in the
> order given — D1 and D3 in #108, D4 in #110, D2 in #111, D5 here. What shipped is kept for its
> rationale rather than as a backlog; the "not in scope" list applies to all of it and is the part most likely to be
> re-proposed.

## The problem

`5859d1c` moved the Play listing and the landing page to lead with the wedge — *"Your workouts in a
file you own — hand-written, or drafted by any AI."* That sentence is now the short description, the
`<title>`, the meta description and the `og:*` tags. It is the copy that decides whether someone
installs.

Inside the app, the thing that sentence promises is reachable from exactly two places:

- a caption-sized text link beside **Export** in the Library tab header
  (`src/app/(tabs)/library.tsx:114`),
- a row in Settings → Data, behind the gear on the Workouts screen
  ([`settings.tsx:380`](../src/app/settings.tsx#L380)).

And the first-run card names three steps — start the workout, edit it, plan weeks in Programs — with
no mention of the file, the import, or an assistant
([`first-run-card.tsx`](../src/components/first-run-card.tsx)).

So someone who installed *because of* the wedge opens a screen that sells the timer, on the one axis
where Hevy and Strong already win. That is an acquisition-to-activation mismatch, not a navigation
preference, which is why it is worth a plan rather than a bullet in the backlog.

## The frame: promote the outcome, not the mechanism

"More prominent import" is the wrong ask, and the case against it is already written into this
codebase: surface area is the app's real onboarding problem, and `FirstRunCard` deliberately names
three things and stays quiet about the rest. An **Import** entry promoted as a file operation makes a
training app look like a developer tool to the majority who just want to lift.

The actual finding is narrower. The import screen holds the app's best *content* offer — three
bundled packs, one tap, no YAML in sight — and files it under a plumbing verb, third on the screen,
below Choose file and Paste YAML ([`import.tsx:549`](../src/app/import.tsx#L549)). Everything below
promotes the pack, the program and the ownership claim. Nothing below promotes the word "import".

## The constraint that shapes the sequencing: this cannot be measured

Nothing phones home. That is a product claim backing the Play zero-data-collected declaration, not a
preference — so there is no funnel, no A/B, no activation metric, and no way to learn afterwards
which of these worked. Two consequences, and they are the reason the slices look the way they do:

- **Every slice is copy-and-a-link, cheap and reversible.** No built onboarding flow, no state
  machine, nothing whose value would have to be defended on vibes after a week of work.
- **The device check replaces the metric.** Each slice below names what to look at on a device,
  because that is the only evidence this project can collect.

## What is deliberately not in scope

Named up front, so they are decisions rather than rewrites later:

- **No Import entry in the tab bar, and no second FAB.** Four tabs is the budget; a fifth for a file
  operation inverts the priority this whole plan argues for.
- **The Library header link stays exactly as it is.** It was never the problem — being the *only*
  prominent one was. Once the slices below exist it is a reasonable place for the power user who
  knows what they want.
- **No in-app AI, of any shape.** The listing's credibility rests on "the app has no AI features and
  never contacts a model", and a prominent "Ask an AI" button reads as generation to a casual eye
  even when it only puts a schema on the clipboard. See the ownership entry in
  [`decisions.md`](decisions.md) — the app authors format, never intent.
- **No fetching packs over the network.** Already refused, for the same declaration; packs ship in
  the bundle. See the content-packs entry in [`decisions.md`](decisions.md).
- **No new import capability at all.** Every slice below routes to the `/import` screen that already
  exists. The parse → merge → summary pipeline, `reviewLibrary` as the single entry point, and the
  in-flight source guard from `66618b8` are all untouched by this plan.

## The slices

Four deliverables. D1 and D3 may share a PR if one review is preferred; D2 wants its own. **D4 is not
independent — it depends on D1** and is the one exception to "revert any slice on its own"; the
reason is in D4.

### D1 — A starter program is one tap from the screen the app opens on

**The highest-leverage slice, and the reason this plan exists.** It converts a plumbing screen into an
activation moment for people who will never type YAML.

- **Where:** `src/app/(tabs)/index.tsx`, in the first-run region, on its own condition — **not on
  `isFirstRun`**, see below.
- **The gate is `sessions.length === 0 && all.length > 0`, and this is a correction rather than a
  preference.** Reusing `isFirstRun` (`:129`) looks obvious and inherits a hole: it is
  `sessions.length === 0 && queued !== null`, and `queued` is `nextUp?.kind === 'workout' ? nextUp :
  null` (`:118`) — so a program whose next slot is a rest day nulls it. That user has workouts, so
  D3's empty state does not fire either, and *neither* invitation renders. The comment at `:124`
  calls that case reachable rather than theoretical, and it is: it is a brand-new user on a program
  that opens with a rest day. That gate was shaped for a step reading "Start the workout below",
  which genuinely needs a Start button under it; an import invitation has no such dependency.

  The `all.length > 0` half is what keeps D1 and D3 mutually exclusive: with an empty library, D3's
  empty state owns the screen and this link stays away, so there is still never a second competing
  instruction block. (Gating on `sessions.length === 0` alone — the obvious repair — closes the
  rest-day hole and reopens that one.)
- **Shape:** a text-weight link, routing to `/import`. **Not inside `FirstRunCard`** — that
  component's own comment records that nothing in it is tappable on purpose, because a card
  duplicating navigation invites the broken-button look this codebase has already removed twice. The
  `startEmpty` link at `:201` is the precedent for the *styling*: text weight, `accentText`, an
  alternative rather than a peer of the primary action.
- **Put it directly under `FirstRunCard`, not under `startEmpty`.** Stacking it beneath `startEmpty`
  is the tempting spot and it dilutes that control: the comment above `:185` argues at length that
  the empty-session link works *because* it sits immediately under the Start button and reads as its
  alternative, and two identical `accentText` `smallMedium` links in a column give it a peer and take
  that reading away. Under the card, the link reads as the continuation of the block whose whole job
  is orienting someone new. The cost is honest and worth naming: it puts a row between the card and
  `NextUpCard`, which the device check below is there to settle.
- **Copy names the outcome, not the mechanism** — a starter program you can add, not a library you can
  import. The word "import" belongs on the destination screen, not on the invitation.
- **Obligations:** `accessibilityRole="button"`; 44px via `minHeight`, never `height`; keys in **all
  three** bundles (`en`, `pt`, `ja` — `ja` takes only `_other` for anything with a count).
- **Tests:** a first-run case beside
  [`workouts-next-up.test.tsx`](../src/app/__tests__/workouts-next-up.test.tsx) — present at zero
  sessions, absent once one is logged, routes to `/import` — plus one run driven in `pt`, since an
  English-locale assertion cannot catch a hardcoded English string. **One of the cases is the rest-day
  gate**: zero sessions, a program whose next slot is `rest_day: true`, link present. Reintroduce
  `isFirstRun` as the condition and that case has to fail, or it is pinning nothing.
- **Device check, and it has two halves.** A raised text size, because this row has a history — the
  empty-session control overflowed the title row at large type and moved four times before it landed
  (comment above `:201`). And, at default size, whether the two links still read as a hierarchy rather
  than as a pair; that is the placement question above, and the screenshot decides it, not this file.
- **Done when:** a fresh install reaches the three packs in one tap from the screen it opens on.

### D2 — Import leads with packs for someone who has logged nothing

- **Where:** [`import.tsx`](../src/app/import.tsx), inside the idle block only (`!ready && !applied`).
  The packs section at `:549` moves above Choose file and Paste YAML when `useSessionHistoryStore`
  reports zero sessions.
- **Why state-dependent rather than always:** a returning user opens this screen *to import a file*;
  demoting the file path for them would trade a real job for a first-run nicety. Packs-first for
  everyone was considered and rejected on that.
- **Untouched:** `reviewLibrary` stays the single entry point (packs already go through the ordinary
  import path — one of the four pack rules in [`decisions.md`](decisions.md)), the review and applied
  states keep their order, and the in-flight source guard is not reshaped.
- **The assistant-brief row stays last** (`:596`), whoever is looking. It is the power-user affordance
  on this screen and the one thing here a first-timer is least likely to want; moving it up would
  also put "copy a JSON Schema" in front of the reader this slice exists to serve. Only the three
  source blocks above it reorder.
- **Tests:** [`import.test.tsx`](../src/app/__tests__/import.test.tsx) — assert rendered order at zero
  sessions and at one.
- **Done when:** the first thing a new user sees on `/import` is three programs, not two file verbs.

### D3 — The two places that end in prose get a control instead

Titled for what it does. An earlier draft called this "the two empty states", which was wrong twice
over: only one of the two halves lands in an empty state, and the Programs half reaches far more than
empty-state visitors.

- **Workouts** (`src/app/(tabs)/index.tsx:238`): "No workouts yet — build one from exercises in your
  library" is true and slow. It should also offer the fast path into `/import`. This is the
  empty-library branch, which is exactly the state D1's link stays out of (see D1's gate) — so
  between them every zero-session user gets one invitation and nobody gets two.
- **Programs** (`src/app/(tabs)/programs.tsx:147`): **not a dead end** — it links to
  `/program-guide`, and an earlier read of this that called it one was wrong. The gap is one level
  in: the guide *describes* the route in prose — "Library tab → Import → pick your file → review the
  summary → Merge & import" ([`program-guide.tsx:158`](../src/app/program-guide.tsx#L158)) — to a
  reader who has just finished being told how to write the file. It should end with the control
  instead of directions to it.
- **The control goes in the guide, and that is why the empty state needs nothing of its own.** The
  guide is reached from two places — the empty state at `:147` *and* a permanent `?` header button at
  `:109` — so one control at the end of it serves every Programs visitor, not only the ones with no
  programs. Duplicating it into the empty state would buy nothing and add a second thing to keep
  worded consistently.
- **Push or replace is a decision for this file, not for the device.** `/import` and `/program-guide`
  are both `presentation: 'modal'` ([`_layout.tsx:121`](../src/app/_layout.tsx#L121),
  [`:127`](../src/app/_layout.tsx#L127)), so pushing import from inside the guide leaves the user two
  modals deep and, on dismiss, back mid-scroll in 226 lines of reference they are done with.
  **Default: `replace`** — the guide's last step *is* the import, and dismissing should land on
  Programs, where the imported program has just appeared. Settings → Import is the precedent for
  stacking, but Settings is one short screen and the guide is not. The device check is the Android
  back button and the swipe-dismiss, both on the replaced stack.
- **One caveat on that guide:** its prose is hardcoded English (see D5). The control this slice adds
  gets a proper key in all three bundles anyway — a new string does not inherit the file's debt.
- **Done when:** neither the empty library nor a reader who has just finished the guide has to
  already know where import lives.

### D4 — The first-run card carries the sentence the listing sold

- **Where:** [`first-run-card.tsx`](../src/components/first-run-card.tsx) and `today.firstRun.*`.
- **Shape:** a **copy swap, not a fourth step.** The card's argument for naming exactly three things
  survives this plan intact; what changes is that one of the three carries ownership and outside
  authoring instead of restating what the tab bar already says.
- **The wording constraint is load-bearing:** it may not imply the app generates anything. The model
  stays outside the app, exactly as the listing words it ("drafted by any AI", "paste the result in").
  Ask of the string: if a generated program turns out badly, does this sentence make that Kettle's
  fault? See the ownership entry in [`decisions.md`](decisions.md).
- **Tests:** driven in `pt`.
- **This slice depends on D1, and it is the one place "revert any slice alone" does not hold.** The
  card is untappable by design and this step names no route, so the affordance under the claim is
  D1's link. Ship D4 after D1; if D1 is ever reverted, D4 goes with it, or its step has to name where
  the route lives in words — which is the "step 2 named the tab you are already looking at" mistake
  the card's own comment records fixing.
- **Done when:** someone who installed on the strength of the short description meets that claim again
  within the first minute, in the app.

## Adjacent, and deliberately separate

### D5 — `program-guide.tsx` is 226 lines of hardcoded English

One `t()` call in the whole file, for `common.done`. This is a known gap rather than a discovery —
[`history.md`](history.md) records the prose as "still English and want their own namespace" — but
this plan is what makes it matter more, since D3 routes more people through that screen. It stays its
own PR: ~200 lines of prose across three bundles is larger than every slice above put together, and
bundling it would hide four cheap reversible changes behind one expensive one.

## Sequencing and risk

1. **D1 + D3** — cheapest, and between them they close the mismatch for every zero-session shape:
   D1 for a seeded library, D3 for an empty one, and D1's corrected gate for the rest-day case that
   used to fall between them.
2. **D4** — copy only, but it is the claim the listing makes, so it wants the device check and a
   careful read against the ownership line. **Not before D1**, per its dependency above.
3. **D2** — touches the highest-traffic screen in this plan; ships alone so a revert is clean.
4. **D5** — whenever, independently.

The one real risk is drift in the other direction: four slices each adding an invitation to the same
screen would recreate the surface-area problem the first-run card was written to solve. The guard is
that D1's and D3's entries are mutually exclusive by construction (above), and that nothing here adds
a step to the card.

## When it ships

- `CHANGELOG.md` under `## Unreleased` — D1, D3 and D4 are user-visible copy and belong there, for
  users. `docs/` gets nothing added just because it shipped.
- **Nothing to prune from [`open-work.md`](open-work.md) as of `5859d1c`** — it carries no entry on
  import prominence, discoverability or activation, so this is stated rather than left as an
  instruction to hunt for bullets that do not exist. Check again at ship time; if one has appeared by
  then, it goes rather than gets annotated.
- Flip this file's banner to "Executed; kept for its rationale", and keep the "not in scope" list — it
  is the half most likely to be re-proposed.
