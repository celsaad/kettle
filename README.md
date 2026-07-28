# Kettle

A backend-free React Native app for planning and tracking workouts. Exercises and workout
templates live in a hand-editable YAML library; completed sessions are written by the app to a
local, append-only store. No server, no account — fully local, portable, and power-user friendly.

See [`docs/exercise-tracker-product-plan.md`](docs/exercise-tracker-product-plan.md) for the full
product plan (data model, file formats, roadmap), and
[`docs/authoring-exercises-yaml.md`](docs/authoring-exercises-yaml.md) for the YAML reference.

## Status

A five-tab shell (Today, Library, Build, History, Programs) over real local storage — there is no
mock data left. `exercises.yaml` and `sessions/*.yaml` are read and written via `expo-file-system`,
validated with zod on every load. Light/dark mode follows the system theme.

**Session runner.** Live timers for all seven exercise types (hiit / emom / amrap / reps /
timed_hold / cardio / rest). Timing is wall-clock-based, so it survives backgrounding; it uses
keep-awake, haptics, audio cues, a pre-session 3-2-1 countdown, and a local notification as a
background fallback. Every completed set flushes to disk incrementally, so a crash loses at most the
set in progress. You can finish early (keeping whatever was logged) and step back one level with
`goPrev()`, which un-flushes what it just wrote rather than leaving stale data behind. A workout with
nothing runnable shows a "Nothing to run" screen instead of a blank, stuck one.

**Authoring.** Exercises, workouts and multi-week programs all have full in-app CRUD, including
delete behind in-use guards (an exercise still referenced by a workout, a workout still referenced by
a program). Workouts support circuits/supersets as round-robin blocks with configurable rest, blocks
reorder by press-and-hold drag, and the exercise/circuit pickers can create a new exercise inline via
"+ New exercise" instead of detouring to Library. Programs cover per-week overrides — add/remove
weeks, set each week's number/day/workout/notes, and edit per-exercise or per-circuit config patches.
Hand-editing `exercises.yaml` is a power-user option, not a requirement; the Programs tab explains
the format in-app via a guide screen.

**History and progression.** Sessions can be searched by workout name (stat tiles and header narrow
to match) and deleted from the expanded card. Editing an exercise shows a "Recent" section once it's
been logged: a bar chart of a per-type volume metric (weight moved, time under tension, rounds,
distance) across recent sessions oldest-to-newest, over an exact-values list newest-first. Today's
"next up" card tracks which week/day of the active program each session was actually for, persisted
on the session, so jumping ahead or redoing a week out of order still suggests the right week. Above
it sits a stat row: current daily streak, this week's session count, this week's time. Any workout
can also be started ad-hoc from the Build tab's play button, with no program involved.

**Accessibility and i18n.** Both have landed and are house rules for new work rather than open
workstreams. Every interactive control carries a role and label, touch targets are 44px minimum,
colors are contrast-checked against each surface they sit on, the runner stays usable at large
accessibility text sizes, and it announces step transitions to screen readers while respecting
reduce-motion. The UI ships in English and Brazilian Portuguese (323 keys at parity), with dates,
numbers, first-day-of-week and kg/lb all following the device locale by default. User data —
exercise, workout and program names, and notes — is never translated.

**Settings** covers appearance (light/dark/system), display units (kg/lb, seeded from the device's
measurement system — storage stays metric, so an exported library reads the same everywhere), library
export/import, and library counts, and
reaches an optional tip jar (three one-off amounts via Google Play Billing, Android only). It gates
nothing, every feature is free, and it exists only to offset the Play developer fee. No ads, no
account, no subscription, and no third-party purchase or analytics SDK, so the Play Data Safety
declaration stays at zero data collected.

See [`docs/implementation-plan.md`](docs/implementation-plan.md) for what shipped, the scope calls
made along the way, and what's genuinely still open.

Note that web (`npx expo start --web`) has no persistence — `expo-file-system` doesn't support it, so
the web build degrades to an ephemeral in-memory seed library rather than crashing.

## Get started

```bash
npm install
npx expo start
```

In the output, you'll find options to open the app in a

- [development build](https://docs.expo.dev/develop/development-builds/introduction/)
- [Android emulator](https://docs.expo.dev/workflow/android-studio-emulator/)
- [iOS simulator](https://docs.expo.dev/workflow/ios-simulator/)
- [Expo Go](https://expo.dev/go), a limited sandbox for trying out app development with Expo
- the web (`npx expo start --web`)

This project uses [Expo Router](https://docs.expo.dev/router/introduction) file-based routing;
screens live under `src/app`.

## Project structure

```
src/
  app/                  routes (Expo Router): (tabs)/ for the five tab screens, plus session,
                         import, settings, support, the program guide, and the exercise /
                         workout / program editors and program detail as modal routes
  components/           shared UI (themed primitives, session runner sub-views, kettle mark)
  constants/            theme tokens, with the measured contrast ratios recorded alongside them
  domain/               types, zod schemas, YAML<->domain mapping, library merge, display formatting,
                         kg/lb conversion
  storage/              file I/O (expo-file-system): library, sessions, export, tip and app preferences
  state/                zustand stores (library, session history, tip, preferences) + derived-display
                         selectors
  hooks/                theme context, session runner and its pure step model, announcements
  i18n/                 i18next setup and the en / pt locale bundles
  test-support/         shared fixtures and the expo-router stand-in used by the test suite
```

## Scripts

```bash
npm run typecheck     # tsc --noEmit
npm run lint          # oxlint
npm test              # jest, via jest-expo
npm run format        # oxfmt (markdown and package.json are excluded on purpose)
```

Typecheck, lint and the test suite all run in CI (`.github/workflows/ci.yml`) on every push and pull
request. The suite is 287 tests across 25 files covering the domain layer, the session runner, and
the highest-branch screens; layout, animation, real audio and file writes are verified by driving the
running app instead.
