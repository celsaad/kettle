# Migrating to native — plan

> **M0 executed; the spike recommends go.** Its report is under M0 below. Every hard gate passed, and
> the one partial answer (Q3) re-scopes M3 rather than blocking it. **M1 has landed**: the
> conformance corpus, the native skeleton (`native/android`), a Kotlin runner for every corpus suite,
> the generated string resources and the generated Compose theme. **M2 has shipped**: 0.11.2 is the
> shell on production, hosting today's app from the RN AAR, and its landmark held: a release cycle
> with no regressions. **M3 has shipped**: 0.12.0 is on production, with one RN surface
> steered by messages (#152), a native tab bar and back stack around it (#153), native-presented
> sheets (#155) and the `filesChanged` re-read (#154). **M4 has shipped**: 0.13.0 carries Support,
> the program guide, exercise progress, Stats and History as Compose screens, which meets its
> landmark. M1–M4 are tracked as GitHub issues #141–#156, #194 and
> #196–#202, whose open/closed state is the live record. **M5 is done**: every ticket (#230–#245,
> map #219) is closed and 0.14.0 carries it. Programs, Program detail, Library, Home, Settings, Import
> and the four editors are Compose screens, and native owns the preferences, edited-session and
> library files, which meets its landmark: the only RN left is the session runner. **M6 is done**:
> 0.15.0 carries the native runner (#308, map #292), and routing moved native after it (#309), on
> `master` and shipping with M7. **M7 is ticketed** (map #335): one removal PR (#357), eight unpin
> PRs (#364, #356, #358–#363) and one release (#365), with retiring the TS domain (#348–#354) after
> it. **M8's UI is decided: Compose Multiplatform** (#345). Written against the tree at `f1b3fea`,
> SDK 57.
> **Everything after M0 was conditional on a "go"**: had the spike failed, this file would carry a
> banner saying so and the decision log the reason, so the idea isn't re-proposed from scratch.

Kettle moves from Expo / React Native to Kotlin + Jetpack Compose on Android and, later, to iOS
with Compose Multiplatform (M8). It does that **incrementally, inside the shipping app**: a native shell becomes the
app Play installs, it hosts the not-yet-migrated React Native screens, and each milestone deletes some
RN code. At no point is there a second app, a parallel rewrite branch, or a big-bang cutover.

The plan is built on four constraints. They come first because each one, if dropped, turns a
milestone into a rewrite.

1. **Every milestone ships to Play.** It is done when it's in production. A milestone that is done
   only on a branch hasn't landed.
2. **The files are the contract, not memory.** The library YAML and the one-file-per-session log are
   already the source of truth. Native and RN code share *files*, never in-memory state, so the
   boundary needs no state-sync system.
3. **One writer per file, at any time.** Each file has one owning side, which writes it, while the
   other side only reads. Ownership moves in one named milestone, never gradually.
4. **The format can't fork.** `schema.ts` and its mirrors stay the definition of the format. The
   native parser has to pass the same conformance fixtures the TS one does, until the TS one is
   deleted.

## Shape of the coexistence

```
Play install (com.casco.kettle, same upload key, same versionCode line)
└── native/android  — Kotlin + Compose shell. Owns the entry point, navigation, theme.
    ├── native screens (grow each milestone)
    ├── :shared     — Kotlin Multiplatform: domain + storage + format. iOS reuses it in M8.
    └── kettle-rn.aar — built from the existing Expo project by `expo-brownfield build:android`.
                        Hosts the RN screens not yet migrated. Shrinks each milestone.
```

This is Expo's **isolated** brownfield approach (https://docs.expo.dev/brownfield/isolated-approach/).
The existing project at the repo root keeps building as it does today and is packaged as an AAR. The
native app consumes that AAR, opens RN screens through `BrownfieldActivity.showReactNativeFragment()`,
and talks to JS over `BrownfieldMessaging`.

Isolated is chosen over the integrated approach because it leaves the root project untouched: `pnpm
test`, lint, the web build and the browser verification keep working until the milestone that deletes
them (for web, M6: #298). **Expo marks brownfield support as alpha, and its dev client doesn't support brownfield.** Those
are the two risks Milestone 0 exists to retire.

## Milestones

Each one lists its deliverables, the **landmark** that says it's done, and a rough size for one
developer working with an agent. Treat the sizes as a calibration to revisit after M0, not a
commitment.

### M0 — Spike and go/no-go · ~1–2 weeks

A throwaway branch that answers the questions that would sink the plan late if left unasked.

Deliverables:

- A minimal `native/android` Compose app that boots, shows one native screen, and opens the *real*
  Kettle RN UI from an `expo-brownfield` AAR.
- A spike report, appended to this file, that answers each of these yes or no with evidence:
  1. **The AAR builds on GitHub Actions without EAS**, the way `android.yml` builds today.
  2. **Two RN surfaces share one JS runtime.** A zustand store written on one surface is visible on
     the other. If they don't share it, every surface boots its own copy of the stores, and the
     "re-read on focus" rule below has to cover RN-to-RN as well.
  3. **expo-router runs inside the fragment**, opened at a deep link (`kettle://history`) passed from
     native. If it does, M2 and M3 stay cheap. If it doesn't, RN screens must become separately
     registered roots before M3, which adds a milestone.
  4. **Both sides resolve the same files directory.** `expo-file-system`'s document directory and
     the shell's `filesDir` point at the same path, so an existing install keeps its library and
     history across the M2 update.
  5. **A `BrownfieldMessaging` round trip works**, native → JS → native, with a payload.
  6. **Back and predictive back behave correctly** across a native → RN → native stack.
  7. **The dev loop is workable**: Metro feeding the debug AAR, with fast refresh, on this Windows
     machine. There is no dev client here.
  8. **Cost**: the APK size delta and the cold-start delta against the current release, measured on
     a real device.

**Landmark: the go/no-go decision, written down.** Questions 1, 2, 4 and 6 are hard gates. A "no" on
3, 5, 7 or 8 makes the plan more expensive, so re-size it before deciding.

#### Spike report (October 2026)

**Recommendation: go.** The four hard gates pass. Q3 is a partial yes that changes how M3 works but
doesn't block it, and nothing else came back worse than expected.

The spike ran on branch `spike/brownfield`, which never merges. It used a Compose host
(`com.casco.kettle.spike`) consuming the release and debug AARs, plus a JS probe that reported its
runtime id, mounted surfaces, initial URL, route and document directory over `BrownfieldMessaging`.
Every device answer below was observed on a Galaxy S25+ (Android 16, arm64), with expo-brownfield
57.0.24 and React Native 0.86.

| # | Question | Answer | Evidence |
|---|---|---|---|
| 1 | AAR builds on Actions without EAS | **Yes** | `spike-brownfield.yml`: prebuild, then `expo-brownfield build:android --release`, then the host's `assembleRelease`. Green in ~14 min with a warm Gradle cache. |
| 2 | Surfaces share one JS runtime | **Yes** | Successive surfaces reported the same runtime id, and the runtime outlives its last surface: with zero mounted, the library store still answered with every exercise. |
| 3 | expo-router opens at a deep link | **Partly** | The **first** surface opens at the link: `kettle://history` lands on History. A **later** surface in the same runtime reaches the route and then resets to `/`, in 3 of 3 runs. See "What changes" below. |
| 4 | Same files directory | **Yes** | expo-file-system's `Paths.document` is `file:///data/user/0/<pkg>/files/`, identical to the host's `filesDir`. |
| 5 | Messaging round trip | **Yes** | ~2 ms, native → JS → native, with a payload. |
| 6 | Back across native → RN → native | **Yes, hardware back** | With `setNativeBackEnabled(false)`, JS takes back while it has somewhere to go (a tab returns to Workouts first, as in today's app), then the activity finishes back to native. **Predictive back is untested**: today's app disables it (`predictiveBackGestureEnabled: false`) and the spike matched that. |
| 7 | Dev loop on Windows | **Yes, with one workaround** | Metro and fast refresh work: an edited string reached the phone in 3 s. Expo's `expo-brownfield` CLI can't run on Windows, because it spawns `./gradlew`, which `cmd` doesn't resolve. The Gradle task it wraps (`publishBrownfieldDebugPublicationToMavenLocal`) works. An AAR rebuild is needed only when native code or dependencies change: 32 min cold, 15 min for a new ABI on a warm cache. |
| 8 | Size and cold start | **No size cost; RN launch cost not yet measured** | See below. |

**Q8 in numbers.** Both builds are arm64 release builds, unminified.

APK size:

| | Today's app (`:app:assembleRelease`) | Shell + AAR |
|---|---|---|
| APK | 50.3 MB | 46.3 MB |
| Native libraries | 20.8 MB | 20.0 MB |
| dex | 49.8 MB | 49.7 MB |
| JS bundle | 4.5 MB | 4.5 MB |

The shell is smaller only because it doesn't yet carry the app's icons and splash, which it gains
back in M2.

Cold start, `am start -W`, median of 5:

| | Median | Range |
|---|---|---|
| The Play 0.9.0 install, to first frame | 615 ms | 578–659 |
| The shell's native screen | 129 ms | 116–151 |
| Opening its RN activity | 67 ms | 58–75 |

The native screen is fast because React Native initialises lazily, when the first RN surface opens.
In today's app it initialises before anything is drawn. **What the spike can't show is time to a
rendered home screen**, because adb has no marker for it. The RN activity's first frame comes before
its JS has rendered, and that work is the same code either way. So the comparison that matters for
M2 is still open (see below).

**Build facts any real host inherits.** Each cost the spike a failed build.

- **The AAR's POM gives every Expo module runtime scope.** A host that calls `BrownfieldMessaging`
  has to declare `expo.modules.brownfield` itself, pinned to `package.json`'s expo-brownfield
  version.
- **expo-modules-core's published AAR carries an unstripped `libworklets.so`.** It's the same build
  id as react-native-worklets' copy, so the host needs `pickFirsts += "**/libworklets.so"`.
- **Config plugins that write into `android/app` don't reach the AAR.** The AAR's manifest carries
  expo-notifications' meta-data, but the icon and color it names were written into the app module,
  so the host's resource link fails until it supplies them. Anything else a plugin writes there
  (icons, splash, strings, manifest entries) has the same problem. M2 has to inventory it.
- **The host needs `ndkVersion` set** to React Native's version, or AGP can't strip the AAR's
  native libraries and packages them with symbols.
- **Messages sent before JS has loaded are dropped silently**, with no queue and no error.
  Native-to-JS traffic waits for a "ready" message from JS.
- **Numbers cross the bridge as `Double`.**
- **RN reports `intent.data` as the initial URL only under `ACTION_VIEW`.** Set the action on any
  intent that carries a link.

**What changes because of Q3.** M3 as written opens every route as its own surface at a deep link,
and a second surface can't be routed that way. Two ways through, cheapest first:

1. **One long-lived RN surface, steered by messages.** Native sends `{ type: "navigate", url }` and
   JS calls `router.navigate`. Routing inside a running router is ordinary expo-router use, but
   it's untested here.
2. **One registered root component per RN screen**, dropping expo-router. This is the fallback
   question 3 already named, and it costs the milestone it said it would.

**M3 opens with a half-day test of option 1**, and the plan adds a week of contingency to M3 until
that test says which option applies. M2 is unaffected: it has one surface, and the first surface
routes correctly. *(Answered by #152: option 1 holds, with a dismiss-first rule. See M3.)*

**Added to M2's checks:**

- **Time to a rendered home screen**, measured with a Macrobenchmark against the Play build. This is
  the part of Q8 that adb couldn't answer. *(Tooling landed with #150: `scripts/startup-benchmark.ps1`.
  "The Play build" is today's app built release from the same commit as `com.casco.kettle.bench`,
  beside the shell's `.bench2`. The Play release can't stand in for itself: it has no marker, so it
  could never report a time to Home. Benchmarking the real install would also force-stop it, reset
  its compiled code, and time its owner's library against a seed. The end point is Home's
  `reportFullyDrawn`, which both builds call from the same JS.)*

  Measured on the S25+ on 2026-10-22, at `3ddcb88`: cold start, 15 iterations each, arm64 release,
  unminified, seed library.

  | To Home drawn | Compilation | Median | Range |
  |---|---|---|---|
  | Today's app | None | 565 ms | 512–612 |
  | Today's app | Full | 532 ms | 513–716 |
  | Shell | None | 696 ms | 531–739 |
  | Shell | Full | 670 ms | 661–736 |

  **The shell is ~130–140 ms slower to Home (+23–26%).** In both builds, time to full display equals
  time to initial display, give or take a few ms. expo-splash-screen holds the activity's first frame
  until JS hides the splash, so the first frame is Home in either build. That also means the spike's
  615 ms "to first frame" for the Play install was already time to Home.

  **Accepted for M2, not ticketed.** The cost is the RN-hosting path itself, and the migration deletes
  that path: Home renders natively once its milestone ships, and RN leaves the app entirely after that.
  Tuning a path that's being removed isn't worth the time, and 670–700 ms to Home is still well under
  a second. It isn't startup timing either: both builds load React Native inside the measured window,
  today's app in `Application.onCreate` and the shell in its activity's `onCreate`, so loading it
  earlier saves nothing on a cold start. The cause wasn't traced. M7 reruns the benchmark against the
  all-native app.
- **An inventory of what config plugins write into `android/app`.** *(Landed with #148; what stays a
  copy is in the decision log's single-source entry.)*
- **The Windows dev loop as a documented script**: publish the AAR with Gradle, then Metro.
  *(Landed: `scripts/native-dev.ps1`.)*

### M1 — Foundations · ~3–4 weeks

Nothing user-visible ships in M1. It builds the pieces every later milestone depends on.

- **The `:shared` KMP module**: types, schema validation, snake_case ↔ camelCase mapping, merge,
  program resolution, units and name search (`search.ts`, held to
  `conformance/search.yaml`), ported from `src/domain/`. The selectors in `src/state/selectors/`
  that the native screens will need are ported when those screens are, not here. *(Units landed with
  the skeleton, name search with #142, the YAML loader and session files with #143, and the library file with
  #144, and repair, merge, program resolution and overrides with #145.)* The module has a JVM target
  only, which `:app` consumes as it is: `androidTarget()` waits for the first code that needs an
  Android API, and the iOS targets for M8.
- **A conformance corpus** in `conformance/` *(TS side and the Kotlin runner landed; see its README
  for the contract)*:
  YAML inputs plus generated golden outputs (parsed library and session, refusal paths, repair,
  merge, program resolution, overrides, units). The inputs are drawn from the existing domain tests
  and `site/examples/*.yaml`. The docs samples stay with `docs-samples.test.ts`, since extracting
  them from markdown is a TS-only job. **Both** jest and the Kotlin tests run it. A fixture one side
  passes and the other fails is a fork of the format, and it fails CI.
- **Generated resources**, so translations and colors stay single-sourced while both UIs exist:
  - `en.json`, `pt.json` and `ja.json` → `values{,-pt,-ja}/strings.xml`, plurals included.
    Android's plural rules never select `zero` for Portuguese, so the generator emits an explicit
    zero branch for every `_zero` key. That is the same "0 sessão" bug the bundles already guard
    against, and it gets a test. *(Landed: `:strings`, run by `:app`'s build into a build directory,
    so nothing generated is committed. Call sites use `pluralText`, never `pluralStringResource`.)*
  - `constants/theme.ts` → a Compose theme (colors, `Spacing`, the always-dark `RunnerColors`).
    *(Landed: the values moved to `constants/theme.json`, which `:theme` reads the way `:strings`
    reads the bundles; `theme.ts` re-exports it. Why a data file rather than parsing the TS is in the
    decision log, along with the M5 content it applies to next.)*
- **CI**: a Gradle job for `native/android` and `:shared`, with ktlint and detekt at zero warnings
  (the same rule `pnpm run lint` has), alongside the existing pnpm jobs. *(Landed: the `native` job
  in `ci.yml`.)*

**Landmark: the conformance corpus is green on both implementations in CI**, and a translated string
added to `en.json` shows up in a Compose preview in all three languages with no hand edit.

### M2 — The shell ships · ~2 weeks · shipped as 0.11.2

> **Shipped** (#148–#151); the CHANGELOG has the release. **The landmark held**: 0.11.2 ran a full
> release cycle, until 0.12.0 replaced it, with no regression reports. What it taught every later milestone (#170) is in the
> decision log's entry on the shell's share of `app.json`.

The native shell becomes the app on Play while still showing **100% RN content**: one RN surface
running the existing expo-router app, unchanged. Users see nothing different, and that is the point.
This milestone retires the riskiest thing in the plan, replacing what Play installs, while nothing
else is changing.

- `android.yml` builds the AAR, then the shell's `.aab`. Same package, same upload key, the next
  `versionCode`. Read the signing entry in the decision log first. Nothing about which key signs
  what may change in this milestone. *(Landed: `building-android.md` describes the pipeline, and the
  workflow fails a build that doesn't carry the pinned upload certificate.)*
- An update-in-place test on a device: install the current Play build, create a library and a
  session, update to the shell, and confirm everything is still there.
- Internal track → closed testing → production. Don't skip steps.

**Landmark: a production release whose entry point is native**, with no regression reports through
one full release cycle. Every later milestone ships through this pipeline.

### M3 — Native owns navigation · ~2–3 weeks (contingency released, see the routing test) · shipped as 0.12.0

> **Shipped** (#152–#156); the CHANGELOG has the release.

- **The routing test passed (#152): one long-lived RN surface, steered by messages.** Native sends
  `{ type: "navigate", url }` (`JsBridge.navigate`), and `safe-brownfield.ts` routes it. Every tab and
  modal route was reached by message, with back checked after each; the evidence is the matrix in
  `scripts/route-check.ps1`, rerunnable against a `.dev` build. So expo-router survives as the router
  *inside* the surface, registered roots are not needed, and the contingency week goes.
  - **`router.navigate` alone is wrong** from anything above the tabs. Sent from a modal or the runner,
    it pushes a *second* copy of the tabs over them, so back walks through the stale screens. It
    also left a running session alive underneath with the leave guard never asked. `dismissTo` gets
    modal → tab right but does nothing between tabs.
  - **The rule that held: dismiss everything above the tabs, then navigate**, and only if the
    dismissal happened. A modal opened by message therefore backs out to its tab, and a second modal
    replaces the first rather than stacking. Over the runner, its leave guard holds the dismissal and
    the message is dropped. The guard first closes whatever sheet is open in the runner (Coming up,
    the exercise picker, a swap), and only then asks "Finish session?". The user's answer decides
    either way.
- Native tab bar and back stack, built with Compose Navigation. Every route still renders RN, in the
  one surface native steers. Per-surface deep links stay out (Q3). *(Landed with #153. The constraint
  it found: **the surface can't go inside Compose.** In an `AndroidView`, RN's whole tree dropped out
  of the accessibility tree on a device, so it stays a plain view beside the Compose ones. Dropped on
  purpose: RN's bar scrolled a tab's list to the top when its tab was tapped again, and the native
  bar doesn't. Each tab's native port brings it back for its own screen.)*
- Modal routes (editors, import, settings) become native-presented sheets around RN content.
  **Answered: the one surface doesn't move.** A sheet can't hold RN content in a second surface
  (Q3), and a Compose sheet would put the surface in an `AndroidView`, which hides RN from TalkBack
  (#153). So the sheet is View-based and in-window. Native gives the surface a top margin, rounded
  top corners and a slide over a scrim and a picture of the tab, and never reparents it. #177 proved
  that on a device: TalkBack, the keyboard, insets, return and back all held, and #155 built it. RN
  stays the router, and the runner stays RN-presented until M6.
- A `BrownfieldMessaging` event, `filesChanged(kind)`, that RN stores answer by re-reading. This is
  the **re-read-on-focus** rule that makes constraint 3 work in both directions. *(For M5: a
  re-read refreshes every screen that reads a store, but RN's open editors and the runner copy what
  they show once, so they have to be replaced before native writes those files, not after. And a
  re-read of a corrupt library reseeds it from RN, a second writer. M5 answers that by making RN's
  load parse-only and native's writes atomic, #220.)*

**Landmark: no navigation happens inside RN.** Every screen transition the user sees is a native
one, and an RN screen can be swapped for a native one by changing one route registration.

### M4 — Read-only screens · ~3–4 weeks

> **Shipped in 0.13.0** (#194, #196–#202, all closed), charted on the wayfinder map #186, whose
> closed tickets hold the reasoning.

These screens mostly read files, so they make the easiest first ports. Suggested order, smallest
first: support → program guide → exercise progress → stats (`analytics`) → history. Each port ships on
its own, in the next release after it lands.

**"Read-only" has two exceptions, both settled before any code.**

- **History deletes finished session files** (#187). That is M4's one ownership move: native deletes,
  then sends `filesChanged('sessions')`. It needs no guard, since neither the runner nor the session
  editor can be on screen with History.
- **Support writes the supporter file**, which RN Settings reads until M5 (#188). Native becomes its
  only writer, its format is frozen until M5, and `filesChanged` gains a `supporter` kind, deleted with
  RN Settings. *(Deleted in the preferences flip, #238.)*

**History's and Library's Exports stay RN's until M5** (#202, #234). Export and share move native
with Settings, which shares the same files, so the native screens ask instead: `JsBridge.export` sends
`exportSession` or `exportHistory`, `JsBridge.exportLibrary` sends `exportLibrary`, and RN's
`state/export-requested.ts` shares with the sheet the RN screens used. All three messages, and that
file, go when export does. *(Gone in the preferences flip, #238: both screens share through
`FileShare`.)*

**Support is the tip jar**, so it also needs billing. Its native port moves the tip jar to the Play
Billing Library in M4, a step pulled forward from M7, and keeps `safe-iap.ts`'s rule: if billing is
unavailable, the screen still works without it. The alternative, keeping Support in RN until M7, was
rejected. It would leave `expo-iap` in the AAR for three milestones just to serve one screen. The
facts the port rests on (one PBL in the APK, SKUs unchanged, no Data Safety change) are in #189.

**RN stays the router through M4** (#190). A ported screen keeps an empty RN stub route at its path,
native draws the Compose screen whenever RN reports that path, and native sheets go into #155's sheet
(#191). The stubs go when routing moves native, after M6 (#223).

Every screen is ported to the same definition of done:

- The Compose screen, and the selector functions it calls ported into `:shared` one by one, with their
  tests (#192). Logic RN keeps past M4 (`entryBest`, `recordKey`, the session summaries) joins
  `conformance/records.yaml` instead.
- Compose UI tests covering whatever the screen's jest tests covered: the same cases, and the same
  "drive it in `pt`" rule for translation (the recipe is in #193).
- Accessibility with the same bar as AGENTS.md: a TalkBack label only where the children don't name
  the element, roles and states set, 48dp targets, and no fixed heights on controls. These are asserted
  in tests; a device TalkBack pass waits for one full pass at the end of the migration.
- **The RN screen and its jest test are deleted in the same PR**, and its route shrinks to the stub.
  A screen that exists in both forms is a fork waiting to happen.

**Landmark: History and Stats are native in production.**

### M5 — Library ownership flips · ~5–7 weeks · done, in 0.14.0

> **Done** (#230–#245 and #228, all closed), charted on the wayfinder map #219, whose closed tickets
> hold the reasoning. 0.14.0 carries all three flips; the CHANGELOG has the release.

The largest milestone, because it moves ownership of the library file, of edited session files and
of the preferences file. **Routing does not move in M5** (#223): RN stays the router behind M4's stub
routes until the runner is native (see M6).

- **Native**: the library, workouts list (Home), programs, program detail, the four editors
  (exercise, workout, program, session), import and Settings, together with `validateConfig`-style
  form validation, content packs, the seed library, `library-translation`, export/share, the SAF
  backup folder (`storage/backup.ts`) and the rest-day reminder (#221, pulled forward from M7).
- **Shared artifacts, not ports.** The packs and the seed move into `src/content/` as library YAML
  plus translation JSON that both sides read (#224, #230; the decision log's single-source entry).
  The exercise drawings become `.svg` files there too, drawn by RN and generated into
  VectorDrawables by a `:art` module (#225, #228), so iOS reuses them in M8.

**Read-only screens first.** Home, Library, Programs and Program detail write nothing, so they port
M4-style, one Play release each, before any flip: Programs → Program detail → Library → Home. Home
goes last, carrying the splash exit and the startup marker (`reportFullyDrawn`) to native.

**Then three flips, one per file** (#222). Constraint 3 is read per file: each file moves in one
named release, not all three in one. One release each, in this order:

1. **Preferences**: native Settings and the theme, the rest-day reminder switched on, and the
   backup folder. The folder counts as a file under constraint 3 and moves here, since its URI
   lives in the preferences file. Export and share move native here too.
2. **Sessions**: the native session editor. It only reads the library, so it doesn't wait for the
   library flip. *(Released in #240: RN's editor and its store edit path are deleted.)*
3. **Library**: the exercise, workout and program editors, import, the seed and the packs.
   *(Released in #245: `Flips.kt` is gone, RN's editors, Import and library writes are deleted,
   native seeds and repairs before RN starts, and the runner's adopt is `setTargetWeight`.)*

**The RN runner still writes, so "native is the only writer" needs a protocol** (#220). After the
flips the runner writes only *new* session files. Its other writes become requests that native
performs: `setTargetWeight` (the load `adoptPreviousLoad` adopts) at the library flip, and
`sessionFinished` → `backupResult` (the finish backup, whose failure the completion screen still
warns about) at the preferences flip. Both go with the RN runner in M6.

- **RN's library load becomes parse-only.** It runs on every `filesChanged('library')`, so it must
  not seed, save a repair, or quarantine and reseed. Native seeds and repairs at launch, and RN's
  start waits for that job, so the ordering is a dependency in code (#221).
- **Native writes every file it owns atomically** (write-then-rename). This replaces M3's "harmless
  only while native never writes an unreadable file" with a guarantee.
- **Corrupt-library handling carries over unchanged**: quarantine the bad file and reseed, now in
  native.
- **Weight handling carries over unchanged**: kilograms are stored, and `previousWeightKg` is passed
  through every edit path.
- **No RN fallback for unhosted builds.** Web browsed an in-memory seed read from `src/content/`,
  with no writes, until the web build ended in M6 (#298). The iOS RN build loses the reminder until
  iOS goes native.

**Writer screens land unrouted**, behind a `Flips.kt` pair (#231): every `Flip` on in the debug
source set, none in release (which `:benchmark` builds too). `.dev` draws the native writer while
Play keeps the RN screen, so no file has two writers in production. `.dev` accepts two writers in the
meantime. Each flip release deletes its `Flip` entry and moves its routes in with the read-only ones,
along with the RN writers, and **the RN screen and its jest test are deleted in the flip
release's PR**, not the port's.

Every port follows M4's definition of done otherwise: selectors and form logic into `:shared` with
Kotlin tests (#192), Compose UI tests mirroring the jest cases and driven in `pt` (#193), a11y
asserted in tests, feature freeze, no redesign.

**Landmark: the only RN left is the session runner**, plus whatever it renders, behind RN's router
and stubs.

### M6 — The session runner · ~4–6 weeks · done, the flip in 0.15.0

> **Done** (#298, #302–#309, #315, all closed), charted on the wayfinder map #292. 0.15.0 is the flip
> only; routing (#309) is merged and reaches Play with M7's release (#365), whose gate is also its.

AGENTS.md calls the runner "make-or-break", so it moves **whole and last**, never split across the
boundary. Charted by the M6 map (#292); its acceptance is the runner inventory (#294).

**Release shape.** Every slice below lands **dark**, behind a `nativeRunner` flag in `Flips.kt`
(debug on, release off), so `.dev` runs the native runner while Play keeps RN. Then **one flip
release** goes through closed testing and its PR deletes the RN runner, and then routing moves.
The web build went first (#298), so the RN runner's deletion has no web exit to keep.

- **Groundwork (#302).** One `openRunner` for every entry point. With the flag on it opens a
  full-screen native runner (tab bar hidden) behind a `/session-native` stub, and `runnerDone`
  hands back.
- **The step model (#303)** (`session-steps.ts`, `session-outline.ts`) into `:shared`, held to the TS
  by `conformance/steps.yaml` while both runners exist. Plus one plan resolver that the UI uses to
  pick a screen and the service uses to own the plan.
- **The engine (#304)**: the runner as a pure state machine in `:shared`. **Pause belongs to the step's
  clock** and clears on a step change, which deliberately differs from the RN runner.
- **The foreground service (#305)** (`health` type, #293) owns the clock, the count-in, every write
  and **every cue**. Sounds play from it whether the runner is visible or not. The per-step scheduled
  notifications go, and a step that ends while the runner isn't visible posts an alert instead
  (#296). Its write rereads native's files directly, and `setTargetWeight` and `sessionFinished` →
  `backupResult` (#220) become direct calls.
- **The Compose UI (#306)**: the leave guard (one back chain, the number pad first), Coming up, the
  swap and add pickers, the number pad and completion. Exits that leave the *app* keep the session
  running. Exits inside the app are guarded and end in Finish (#297).
- **Process death (#307).** The session file alone can't rebuild a running session, so the service
  also keeps a private, disposable **runner-state file**. That doesn't bend the format rule under
  "Not doing", which covers files the user owns. After a kill, a blocking Resume / Finish prompt
  appears at the next launch (#295). This is the fix for the stranded session.
- **The flip release (#308).** It needs Play's first foreground-service declaration. Its PR deletes
  the RN runner, its messages and its packages (`expo-audio`, `expo-haptics`, `expo-keep-awake`, and
  `expo-notifications`, brought forward from M7). Closed testing comes first: at least one week and
  five real sessions on the S25+, plus the full device script (backgrounding, screen lock, a phone
  call mid-rest, process death mid-set, the cue and exit checks). Production waits for both.

The foreground service is also what `docs/watch-remote-plan.md` needs, so this milestone makes that
plan a few days of work rather than a project.

**Landmark: the runner is native in production** and survives a week of closed testing with no lost
session.

**Then routing moved to native (#309)**, before M7 deletes RN. It was planned as a release of its
own, and instead rides M7's (#365), since nothing about it is worth a Play release while RN still
ships an empty surface. It was
planned for M5, and moved here because every hard question it raised (a cut-down expo-router for one
route, a hand-back message pair, forwarding back to RN, the View sheet kept only so RN could sit above
it) came from the runner still being RN. Once RN draws nothing it is mostly deletion:
- `TabNavigator` becomes a `Navigator` that owns the back stack, with a runner slot that drops
  navigation while a session runs.
- `shellRoutes` becomes a typed native route table.
- The RN tabs, sheets and stubs (#190), expo-router, and every steering and route-report message
  go.
- `kettle://` links are parsed natively, `kettle://session` included.
- `NavigateReceiver` and the route-check scripts stay, retargeted.
- The View sheet stays as it is; whether a Compose sheet should follow is #229.

**Gate: a `:benchmark` cold-start run**, folded into M7's: `.bench` can't be built from 0.15.0, so
the one run against #168's baseline in #365 is routing's gate too.

### M7 — React Native removed (Android) · ticketed

> **Ticketed** on the wayfinder map #335, whose closed tickets hold the reasoning and the research
> (#336–#339, #344). The hand-off issues are the spec; this section is their outline.

**Release shape.** **One Play release**, reached by **sequential PRs on `master`**, each green,
shippable and revertible on its own. There's no flag, because removal can't land dark. **No other
Play release goes out in between**, so the toolchain moves can't reach production half-done. The
toolchain moves ride the same release as removal rather than following it.

**Removal, one PR (#357).** Its acceptance list is the removal inventory (#336). In outline:
- **Native replaces what Expo supplied:** `MainActivity` on `ComponentActivity` with a platform theme
  (appcompat out); Expo's pre-draw splash hold ported as it is (the 400 ms fade, `SplashScreenView`
  on 31+, the API 31–33 stop-crash workaround), on `core-splashscreen`; `enableEdgeToEdge()`, which
  Expo did below API 35; and the three libraries the shell used only transitively, declared.
- **Native builds with no Node.** No `node_modules`, no `app.json`: the version moves to
  `native/android/gradle.properties` (starting from Play's 0.15.0 / 21, not `app.json`'s), the label
  and brand colours become resources, and the six font faces are committed with their OFL licences.
  The locale bundles, `theme.json`, `src/content/` and `conformance/` keep their paths until the TS
  domain is retired.
- **The whole Expo layer goes**: the AAR, expo-brownfield, Hermes, `index.ts`, `app.json`,
  `eas.json`, the metro files, `patches/`, the prebuild and every `expo-*`/`react*` dependency. **The
  RN iOS route closes for good**; `app.json`'s iOS facts move into `ios-plan.md` for M8.
- **The APK has no native code left**, so the NDK, `pickFirsts`, ABI filter and ccache go, and CI's
  two native jobs fold into one Node-free `./gradlew build`.
- The device scripts lose their AAR, Metro, `adb reverse` and prebuild steps, each `.sh` with its
  `.ps1` twin.
- `/bump` and `/release` find platforms by version file. Each platform keeps its own build number;
  `versionName` is shared.
- The Node project that's left is the TS domain, zod, plain jest (on babel-jest), oxlint, oxfmt and
  knip, until the TS domain is retired.

**Unpinning, eight PRs, all after removal (#342).** The version set is #337's, the newest stable
inside every published compatibility table, held no newer than stable Compose Multiplatform supports
(M8):

1. `minSdk` 26, with core library desugaring deleted (#364). navigation-compose stays: it wasn't
   unused, since `Shell.kt`'s `NavHost` switches the tabs and keeps each one's state. Android 7.x
   installs stay on 0.15.0; that was the owner's call, without a usage threshold (#340).
2. Kotlin 2.4.21 and the libraries pinned to the old compiler (#356).
3. kotlinx-datetime 0.8, `Instant` and `Clock` to `kotlin.time` (#358).
4. snakeyaml-engine-kmp 5, after two TAB conformance cases (#359).
5. billing-ktx in place of `PlayTipBilling`'s coroutine wrappers (#360).
6. AGP 9.3.3, Gradle 9.7.1 and built-in Kotlin (#361). detekt stays 1.23.8 (2.0 is alpha) and has to
   be proven still failing a build.
7. The stable Compose BOM and `compileSdk` 37 (#362). material3 1.4.0 is a downgrade from the
   1.5 alpha the beta pin carried, accepted as it is.
8. `targetSdk` 37 and Robolectric 4.17, with `lint.xml`'s `OldTargetApi` exception deleted (#363).
   #338 found no code change for 37 itself, only two device checks.

Only 2 → 6 → 7 → 8 is a hard chain.

**The release (#365)** goes through M6's gate:
- closed testing on the S25+ for at least a week and five real sessions, restarted by a lost
  session, crash or ANR;
- `session-check`, `route-check` and #308's by-hand checks;
- **the device TalkBack pass** this plan deferred to the end of the migration, which blocks on reach,
  name and state, and tickets the rest;
- **the startup benchmark rerun**, all-native against `.bench` rebuilt from #168's merge commit (M2's
  numbers check that the baseline still lands where it did), recorded beside M2's table;
- three install paths: fresh, over 0.15.0 and over 0.14.0.

**Data Safety (#339, #347).** Removal changes nothing on the form. The re-check found that Play
Billing logs its own usage to Google; the form stays "no data collected", read as part of Google
Play's billing system, and Kettle's own copy says "no network requests of its own" (#355). The
release re-runs the dependency diff on its candidate.

**Landmark: the Android APK contains no JS engine** (no `lib/`, no bundle, in the `.aab` and Play's
universal APK).

**Then the TS domain is retired (#346)**, in seven PRs after the release (#348–#354): every
conformance golden written from Kotlin with zero diff, so `:shared` and the corpus define the
format; `library-schema.json` frozen and hand-maintained; each jest guard moved beside what it
protects (`:strings`, `:art`, `:app`, a new `:repo-checks`); Node down to oxfmt; and the shared data
out of `src/` to the top level. Constraint 4's "until the TS one is deleted" ends there.

### M8 — iOS · sized after M7 · UI decided: Compose Multiplatform

iOS has never shipped (see `docs/ios-plan.md`), so it has no brownfield phase. It starts native,
reusing `:shared` for the domain, storage and format.

**The UI is Compose Multiplatform (#345)**, because SwiftUI would make every later screen change land
twice, which for one maintainer outweighs CMP's one-off Android reshaping and its iOS gaps. Why
SwiftUI was rejected is in the decision log. SwiftUI appears only as islands: the iOS sheet is
SwiftUI's `.sheet` hosting Compose (so #229 stays optional), and a Live Activity, if one is ever
built. Swift sees a thin hand-written facade over the default Obj-C export: no SKIE, no Swift export,
no reshaping of `:shared`'s sealed interfaces. Screens port as they are, plus a fixed list of
platform conventions that don't count as redesign (the sheet, edge-swipe back, safe areas, iOS
haptics), switched in `:theme` or the shell rather than per-platform screens.

**What it commits M7 to:** Kotlin and the Compose BOM no newer than stable CMP supports.

**After M7's release**, one PR adds `iosArm64` and `iosSimulatorArm64` to `:shared` with a macOS CI
job that compiles the klibs and runs the conformance corpus on the simulator. The production file
layer that replaces `java.io.File`/`java.time` is M8's.

**Phase 1, Android groundwork**, gated only on Xcode:
- First, two spikes. Does Robolectric's `@Config(qualifiers = "pt")` still pick Portuguese from CMP's
  `Res`, and does its plural lookup pick Portuguese zero? A failed `pt` spike blocks the resource
  migration until translation can be tested another way.
- Then each as a sequential PR that ships to Play: `R` → `Res`, a common `pluralText`, `:strings`,
  `:art` and `:theme` retargeted to `composeResources`, and the screens moved into a KMP UI module.

**Phase 2, iOS.** The simulator covers a Dynamic Type spike (a `UIFontMetrics`-derived `fontScale`
past CMP's 1.8× linear cap; if it fails, the cap is accepted and documented), VoiceOver and the sheet
interop. TestFlight and the background-cue checks wait for a Mac, a device and the developer account,
exactly as `ios-plan.md` already says. That plan's Phase 5, changing the decision log and the README,
applies here too.

## Rules for the duration

- **Feature freeze per screen.** Once a screen is scheduled for the current milestone, its RN version
  gets bug fixes only. New features land on the native side.
- **Format changes go through both parsers in one PR.** The conformance corpus makes this enforceable
  rather than a reminder, and "Changing the YAML format" in AGENTS.md still applies to all three
  docs mirrors.
- **No phoning home** holds for every Gradle dependency, not just the ones that look like SDKs. Check
  transitive dependencies of anything new.
- **Docs that die with RN** are rewritten or deleted in the milestone that kills their subject, not
  after: `verifying-in-the-browser.md` (deleted in M6 with the web build), `sdk-57-api-notes.md`
  (M7), the jest sections of AGENTS.md (shrinking from M4 on), and `building-android.md` (M2).

## Not doing

- **No change to the YAML format, the file layout or the storage locations.** Users' libraries and
  logs must survive every milestone untouched. A format change during the migration would have to
  land in two parsers and three mirrors at once.
- **No redesign.** Screens are ported as they are. A redesign gets its own plan *after* the screen is
  native, so a regression can always be traced to either the port or the redesign, never both.
- **No integrated brownfield, and no RN-host-with-native-views approach.** Both leave RN in charge of
  navigation until a final big-bang flip, which is exactly what this plan is built to avoid.
- **The web build is not replaced.** It ended in M6 (#298), ahead of the RN runner's deletion. The
  landing site is static and unaffected.
- **No new network-using SDK** (crash reporting, analytics, remote config) slips in as part of the
  move. The zero-data-collected claim is unchanged.
