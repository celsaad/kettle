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
data behind. Library exercises are editable in-app (add/edit/delete, with a guard against deleting one
still referenced by a workout); workouts have full CRUD including delete (with a guard against
deleting one still referenced by a program), support circuits/supersets (round-robin blocks with
configurable rest), and blocks can be reordered by press-and-hold drag. Multi-week programs (periodized
wrappers around workouts, with per-week overrides and multi-session-per-week support) are supported and
drive the Today tab's "next up" card, though they can only be authored by hand-editing `exercises.yaml`
— there's no in-app program editor yet.

See [`docs/implementation-plan.md`](docs/implementation-plan.md) for what shipped, the scope calls
made along the way, and what's genuinely still open: an in-app program editor, per-exercise
progression stats, and charts are not implemented.
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
