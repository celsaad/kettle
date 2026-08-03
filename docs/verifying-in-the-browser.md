# Verifying in the browser

Tests cover the logic layer and five screens, but layout, animation, real audio and file writes are
still only verified by driving the running app. Doing this wrong wastes a lot of time, so:

- `pnpm expo start --web --port <port>`; poll with curl, it can take 60–90s. Use a distinct port if
  anything else might be running. Curl-ing the port only proves the dev server is listening — Metro
  bundles on request, so fetching the `.bundle` URL out of the served HTML is what actually exercises
  module resolution.
- Playwright is **not** a project dependency — install it in a scratch dir outside the repo. If the
  browser build is missing: `pnpm dlx playwright install chromium-headless-shell`.
- **Never `page.goto` mid-flow.** A full reload resets the in-memory library, destroying anything you
  just created. Drive everything through real in-app navigation in one page instance.
- **React Navigation keeps previous screens mounted but hidden**, so the same text matches several
  times. Use `.filter({ visible: true }).last()`, not bare `.last()`. A bare `input` index will hit
  the Library screen's hidden search box — scope numeric fields with `input[inputmode="numeric"]`.
- **The app boots in the browser's locale, so your selectors are probably Portuguese.** `text=Library`
  times out at 30s against a pt-BR machine and reads as a broken app rather than a wrong selector.
  Either dump `body.innerText` first and write selectors against what's actually there, or launch the
  context with `locale: 'en-US'` — but the pt run is the more valuable one, since an English pass
  can't catch a hardcoded English string (same reason the screen tests drive `pt`).
- To get a session into history: Build tab → the small round play button on a workout card (starts it
  ad-hoc) → repeatedly click whichever is visible of `Done set →`, `Log set → Rest`, `Skip rest →`,
  then `Done`.
- **`react-native-web` implements only part of the a11y API**, so a browser check under-reports it:
  `accessibilityRole`/`accessibilityLabel` map to `role`/`aria-label`, but `accessibilityActions`,
  `onAccessibilityAction` and `accessibilityValue` are dropped with no warning. The block-reorder
  handle looks actionless in the DOM and works fine under TalkBack. Assert those in jest, not here.
- Always capture `page.on('console')` and `page.on('pageerror')`. The app is currently clean apart
  from one expo-notifications web warning, so any error means investigate.
- Actually read your screenshots. Don't claim something renders correctly without having looked.
- **A green browser run is not evidence that a gesture works.** Block reorder shipped three
  device-only failures in a row that a Playwright drag reported as perfect every time: Android's
  `ScrollView` swallowing the long-press, `onEnd`'s `success` flag arriving `false` for a cancelled
  pan (web sends `END`, so the drop committed there and nowhere else), and `onLayout` never
  populating the measurement array at all (it does fire on web). Each looked like a fixed feature
  from here. If a change touches gesture arbitration, native layout measurement or touch targets,
  a browser pass can only rule things *out* — say so rather than reporting it verified.

Two constraints from `AGENTS.md` § "Platform constraints" bite hardest here, and are repeated because
a browser check is exactly where they mislead: the web build has **no persistence** (an ephemeral
in-memory seed library), and **`Alert.alert` is a no-op on web** — so a browser check of any confirm
flow verifies nothing unless you patch it at runtime in the test script.
