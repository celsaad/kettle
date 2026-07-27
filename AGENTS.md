# Expo HAS CHANGED

Read the exact versioned docs at https://docs.expo.dev/versions/v57.0.0/ before writing any code.
This applies to library choices too — verify what's current for SDK 57 rather than recalling older Expo.

# Kettle

Backend-free workout tracker. A hand-editable YAML library (exercises, workouts, multi-week programs)
plus an append-only local session log. No server, no account.

## Commands

- `npm run typecheck` — `tsc --noEmit`
- `npm run lint` — oxlint. **Four pre-existing `unicorn/no-array-sort` / `no-array-reverse` warnings
  are accepted**; leave them alone and don't add new warning categories.
- No test runner exists yet — no test script, no test files. This is the project's largest gap.

## Where things live

- `src/domain/` — pure logic, no I/O. `types.ts` (domain model), `schema.ts` (zod validation of raw
  YAML), `yaml-mapping.ts`, `merge.ts` (import merges by `id`, whole-object replace), `program.ts`
  (week resolution + override application).
- `src/storage/` — all file I/O, via `expo-file-system`'s **class-based `File`/`Directory` API**.
  One file per session (`session-files.ts`), so a mid-workout flush never rewrites history.
- `src/state/` — zustand stores (`library-store`, `session-history-store`) and `selectors.ts`, which
  holds most derived/display logic.
- `src/hooks/use-session-runner.ts` — the wall-clock session engine. The product plan calls timer
  reliability "the make-or-break issue"; treat this file as high-risk and verify changes by running a
  real session, not by reasoning alone. `buildSteps` is exported and pure.
- `src/app/` — expo-router routes. `src/components/` — shared UI.

**YAML is snake_case, domain code is camelCase**, bridged only in `yaml-mapping.ts`. Program
`overrides` are partial *raw* (snake_case) patches — that asymmetry is deliberate and load-bearing.

## Platform constraints that change how you work

- **Web has no persistence.** `expo-file-system` doesn't support it, so the web build runs on an
  ephemeral in-memory seed library. Every storage function is guarded by `isFileStorageSupported`.
- **`Alert.alert` is a no-op on web** — react-native-web ships literally `class Alert { static
  alert() {} }`. Confirm dialogs (deletes, finish-session) silently do nothing in the browser, so a
  browser check of a confirm flow verifies nothing unless you patch it at runtime in the test script.
- Adding a route file requires regenerating `.expo/types/router.d.ts` (briefly run the dev server) or
  `router.push('/new-route')` fails typecheck.

## Verifying in the browser

There's no test suite, so changes are verified by driving the running app. Doing this wrong wastes a
lot of time, so:

- `npx expo start --web --port <port>`; poll with curl, it can take 60–90s. Use a distinct port if
  anything else might be running.
- Playwright is **not** a project dependency — install it in a scratch dir outside the repo. If the
  browser build is missing: `npx playwright install chromium-headless-shell`.
- **Never `page.goto` mid-flow.** A full reload resets the in-memory library, destroying anything you
  just created. Drive everything through real in-app navigation in one page instance.
- **React Navigation keeps previous screens mounted but hidden**, so the same text matches several
  times. Use `.filter({ visible: true }).last()`, not bare `.last()`. A bare `input` index will hit
  the Library screen's hidden search box — scope numeric fields with `input[inputmode="numeric"]`.
- To get a session into history: Build tab → the small round play button on a workout card (starts it
  ad-hoc) → repeatedly click whichever is visible of `Done set ↑`, `Log set → Rest`, `Skip rest →`,
  then `Done`.
- Always capture `page.on('console')` and `page.on('pageerror')`. The app is currently clean apart
  from one expo-notifications web warning, so any error means investigate.
- Actually read your screenshots. Don't claim something renders correctly without having looked.

## Conventions

- Modal screens: register in `src/app/_layout.tsx` with `presentation: 'modal', headerShown: false`,
  and open with the shared `ModalHeader` (grabber + pinned close button; it owns the top spacing, so
  the screen's own `scrollContent` has no `paddingTop`).
- Theming is strict: `useTheme`, `ThemedText`, `ThemedView`, `Spacing.*`, `MaxContentWidth`. The
  session runner is always dark (`RunnerColors`) regardless of scheme.
- In-app forms write straight to the store and never pass through the zod schema, so they need their
  own validation (see `validateConfig` in `exercise-editor.tsx`).
- **Comments explain why, not what** — trade-offs, root causes, and deliberate scope cuts. Match the
  surrounding density; don't annotate self-evident lines.

## Docs

`docs/implementation-plan.md` is the living record of what shipped, the scope calls made, and what's
genuinely still open. `docs/exercise-tracker-product-plan.md` holds the data model and roadmap. Check
them before assuming something is missing — but verify against the code, since they have drifted before.
