# Kettle

A backend-free React Native app for planning and tracking workouts. Exercises and workout
templates live in a hand-editable YAML library; completed sessions are written by the app to a
local, append-only store. No server, no account — fully local, portable, and power-user friendly.

See [`docs/exercise-tracker-product-plan.md`](docs/exercise-tracker-product-plan.md) for the full
product plan (data model, file formats, roadmap).

## Status

The UI is implemented against the current design (`Kettle Screens.dc.html`): a 4-tab shell (Today,
Library, Build, History), a live session runner with working hold/reps/rest timers, and an
import/merge sheet — all running on mock data, with light/dark mode following the system theme.

Not yet implemented: reading/writing `exercises.yaml` and `sessions/`, library import/merge,
export, and the wall-clock-drift-hardened timer engine (keep-awake, background notifications).
These are separate, larger workstreams described in the product plan.

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
  app/                  routes (Expo Router): (tabs)/ for the tab screens, session.tsx and
                         import.tsx as modal routes
  components/           shared UI (themed primitives, session runner sub-views, kettle mark)
  constants/            theme tokens, mock data
  hooks/                theme context, session runner state machine
```

## Scripts

```bash
npm run typecheck   # tsc --noEmit
npm run lint        # oxlint
```

Both run in CI (`.github/workflows/ci.yml`) on every push and pull request.
