# Kettle

A backend-free React Native app for planning and tracking workouts. Exercises and workout
templates live in a hand-editable YAML library; completed sessions are written by the app to a
local, append-only store. No server, no account — fully local, portable, and power-user friendly.

See [`docs/exercise-tracker-product-plan.md`](docs/exercise-tracker-product-plan.md) for the full
product plan (data model, file formats, roadmap).

## Status

The UI is implemented against the current design (`Kettle Screens.dc.html`): a 4-tab shell (Today,
Library, Build, History, plus a Programs tab), a live session runner with working
hold/reps/rest/hiit/emom/amrap/cardio timers, and an import/merge sheet, with light/dark mode
following the system theme.

The app is backed by real local storage, not mock data. `exercises.yaml` and `sessions/*.yaml` are
read/written via `expo-file-system` (validated with zod on every load), library import merges by id
with a real pre-merge summary, both the library and individual sessions can be exported/shared, and
the session timer is wall-clock-based (survives backgrounding, uses keep-awake, haptics, audio cues,
a pre-session 3-2-1 countdown, and a local notification fallback) with incremental per-set flush to
disk, a finish-session-early option that keeps whatever was already logged, and a one-level `goPrev()`
un-flush so stepping back to redo a set/round retracts what was just written instead of leaving stale
data behind. Starting a workout that has nothing runnable (no blocks, or every exercise's sets/rounds/
minutes at 0) shows a "Nothing to run" screen instead of a blank, stuck one. Library exercises are editable in-app (add/edit/delete, with a guard against deleting one
still referenced by a workout); workouts have full CRUD including delete (with a guard against
deleting one still referenced by a program), support circuits/supersets (round-robin blocks with
configurable rest), blocks can be reordered by press-and-hold drag, and a workout's exercise/circuit
pickers can create a brand-new exercise inline (no detour to Library) via a "+ New exercise" option in
the picker itself. Multi-week programs (periodized
wrappers around workouts, with per-week overrides and multi-session-per-week support) drive the Today
tab's "next up" card and now have full in-app CRUD too, including per-week overrides: create/edit/
delete a program, add/remove weeks, set each week's number/day/workout/notes, and add/edit/remove a
week's per-exercise or per-circuit config overrides — a FAB on the Programs tab, a pencil icon on the
program detail screen. Programs are no longer YAML-only for anything; hand-editing `exercises.yaml`
is now a power-user option rather than a requirement. The Programs tab still explains the format
in-app for anyone who wants it (a "?" button and empty-state guidance opening a guide screen, not a
link to a doc — this repo is private, so a real user can't read `docs/authoring-exercises-yaml.md`
anyway). Editing an exercise also shows a "Recent" section, when it's actually been logged: a small
bar chart of a per-type "volume" metric (total weight moved, time under tension, rounds, distance —
whichever fits the exercise's type) across the last few sessions, oldest to newest, plus the same
history as an exact-values list underneath, newest first. The Today tab's "next up" card tracks
which week/day of the active program a session was actually for (persisted on the session itself),
so it correctly suggests the week *after* whichever one you last did — including jumping ahead or
redoing a week out of order — rather than guessing from a completed-session count that drifted from
reality the moment you didn't progress in strict lockstep; it also shows a stat row (current daily
streak, this week's session count, this week's time) above the card. Any workout can also be started
directly from the Build tab — each workout card has a small play button that runs it as a one-off,
with no program involved.

See [`docs/implementation-plan.md`](docs/implementation-plan.md) for what shipped, the scope calls
made along the way, and what's genuinely still open — there's a known web-only bug where leaving the
session screen (completing a session, finishing early, or closing the "Nothing to run" state) shows a
crash redbox (from `useKeepAwake`'s cleanup racing the browser Wake Lock API) — dismissible, not
data-destructive, but unfixed.
Note also: web (`npx expo start --web`) has no persistence — `expo-file-system` doesn't support it,
so the web build degrades to an ephemeral in-memory library rather than crashing.

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
  app/                  routes (Expo Router): (tabs)/ for the tab screens, session.tsx,
                         import.tsx, and exercise-editor.tsx as modal routes
  components/           shared UI (themed primitives, session runner sub-views, kettle mark)
  constants/            theme tokens
  domain/               types, zod schemas, YAML<->domain mapping, library merge logic
  storage/              file I/O (expo-file-system): library, sessions, export
  state/                zustand stores (library, session history) + derived-display selectors
  hooks/                theme context, session runner state machine
```

## Scripts

```bash
npm run typecheck   # tsc --noEmit
npm run lint        # oxlint
```

Both run in CI (`.github/workflows/ci.yml`) on every push and pull request.
