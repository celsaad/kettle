# Migrating to native — plan

> **M0 executed; the spike recommends go.** Its report is under M0 below. Every hard gate passed, and
> the one partial answer (Q3) re-scopes M3 rather than blocking it. **M1 has landed**: the
> conformance corpus, the native skeleton (`native/android`), a Kotlin runner for every corpus suite,
> the generated string resources and the generated Compose theme. **M2 has shipped**: 0.11.2 is the
> shell on production, hosting today's app from the RN AAR, and its landmark held: a release cycle
> with no regressions. **M3 has shipped**: 0.12.0 is on production, with one RN surface
> steered by messages (#152), a native tab bar and back stack around it (#153), native-presented
> sheets (#155) and the `filesChanged` re-read (#154). M1–M3 are tracked as
> GitHub issues #141–#156, whose open/closed state is the live record; M4 is ticketed (see M4); M5 onward is ticketed next. Written against the tree at `54eb9f8`, SDK 57.
> **Everything after M0 was conditional on a "go"**: had the spike failed, this file would carry a
> banner saying so and the decision log the reason, so the idea isn't re-proposed from scratch.

Kettle moves from Expo / React Native to Kotlin + Jetpack Compose on Android and, later, Swift +
SwiftUI on iOS. It does that **incrementally, inside the shipping app**: a native shell becomes the
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
them. **Expo marks brownfield support as alpha, and its dev client doesn't support brownfield.** Those
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
  re-read of a corrupt library reseeds it from RN, a second writer, which is harmless only while
  native never writes an unreadable file.)*

**Landmark: no navigation happens inside RN.** Every screen transition the user sees is a native
one, and an RN screen can be swapped for a native one by changing one route registration.

### M4 — Read-only screens · ~3–4 weeks

> **Ticketed** (#194, #196–#202), charted on the wayfinder map #186, whose closed tickets hold the
> reasoning. The issues' open/closed state is the live record.

These screens mostly read files, so they make the easiest first ports. Suggested order, smallest
first: support → program guide → exercise progress → stats (`analytics`) → history. Each port ships on
its own, in the next release after it lands.

**"Read-only" has two exceptions, both settled before any code.**

- **History deletes finished session files** (#187). That is M4's one ownership move: native deletes,
  then sends `filesChanged('sessions')`. It needs no guard, since neither the runner nor the session
  editor can be on screen with History.
- **Support writes the supporter file**, which RN Settings reads until M5 (#188). Native becomes its
  only writer, its format is frozen until M5, and `filesChanged` gains a `supporter` kind, deleted with
  RN Settings.

**History's Export stays RN's until M5** (#202). Export and share move native with Settings, which
shares the same files, so native History asks instead: `JsBridge.export` sends `exportSession` or
`exportHistory`, and RN's `state/export-requested.ts` shares with the sheet RN History used. Both
messages, and that file, go when export does.

**Support is the tip jar**, so it also needs billing. Its native port moves the tip jar to the Play
Billing Library in M4, a step pulled forward from M7, and keeps `safe-iap.ts`'s rule: if billing is
unavailable, the screen still works without it. The alternative, keeping Support in RN until M7, was
rejected. It would leave `expo-iap` in the AAR for three milestones just to serve one screen. The
facts the port rests on (one PBL in the APK, SKUs unchanged, no Data Safety change) are in #189.

**RN stays the router through M4** (#190). A ported screen keeps an empty RN stub route at its path,
native draws the Compose screen whenever RN reports that path, and native sheets go into #155's sheet
(#191). The stubs go at the M5 flip.

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

### M5 — Library ownership flips · ~5–7 weeks

The largest milestone, because it moves ownership of the library file and of the session log for
edits.

- **Native**: the library, workouts list, programs, program detail, the four editors (exercise,
  workout, program, session), import and Settings, together with `validateConfig`-style form
  validation, content packs, the seed library, `library-translation`, export/share and the SAF backup
  folder (`storage/backup.ts`). The packs and the seed are data, so they move into files both sides
  read rather than being ported (see the decision log's single-source entry).
- **Ownership move, in one release**: native becomes the only writer of the library file, of
  edited session files, and of the preferences file. The RN runner, which is still RN, keeps writing
  *new* session files. Its stores re-read on `filesChanged`.
- **The native screens land unrouted** and switch on together in that one release, so no file ever
  has two writers in production.
- **Routing moves to native in the same release**, and M4's RN stub routes are deleted with it (#190).

Settings and the preferences file weren't placed in the first draft of this plan. They move here,
not in M4, because Settings writes. Native reads the preferences file from M3 on, for the theme its
chrome draws, but it doesn't write the file before this flip.
- Corrupt-library handling carries over unchanged: reseed and keep the bad file, the way
  `library-file.ts` does.
- **Weight handling carries over unchanged**: kilograms are stored, and `previousWeightKg` is passed
  through every edit path.

**Landmark: the only RN left is the session runner**, plus whatever it renders.

### M6 — The session runner · ~4–6 weeks

AGENTS.md calls the runner "make-or-break", so it moves **whole and last**, never split across the
boundary.

- A native foreground service that owns the wall-clock session state, with the step model
  (`session-steps.ts` → `:shared`). The Compose runner UI binds to that service.
- Sounds, haptics, keep-awake, announcements, and the time-sensitive and background cue
  notifications, moved from `expo-audio`, `expo-haptics`, `expo-keep-awake` and
  `expo-notifications` to the platform APIs.
- The leave-guard, the Coming up sheet, the swap and add pickers, and the number pad.
- Verified the way the runner always has to be: **real sessions on a real device**, including
  backgrounding, screen lock, a phone call mid-rest, and process death mid-set. Then a closed-testing
  track for at least one week of real use before production.

The foreground service is also what `docs/watch-remote-plan.md` needs, so this milestone makes that
plan a few days of work rather than a project.

**Landmark: the runner is native in production** and survives a week of closed testing with no lost
session.

### M7 — React Native removed (Android) · ~1–2 weeks

- Delete the AAR, `expo-brownfield`, Hermes and the RN surfaces.
- Move the rest-day reminder off `expo-notifications`. (The tip jar already moved to Play Billing
  with Support in M4.)
- Re-check the **Data Safety** claim: list every Gradle dependency that touches the network and
  confirm none transmits anything.
- **Drop core library desugaring.** `:app` turns it on (#201) because kotlinx-datetime is `java.time`
  underneath, which Android has only from API 26, and `minSdk` is 24 only because the RN app the shell
  replaces is (Expo SDK 57's floor). With RN gone, raise `minSdk` to 26 and delete
  `isCoreLibraryDesugaringEnabled` and the `desugar_jdk_libs` dependency together.
- **Unpin the Kotlin toolchain.** `native/android/gradle/libs.versions.toml` holds Kotlin and AGP
  at React Native's own versions (`node_modules/react-native/gradle/libs.versions.toml`), so the shell
  and the AAR it hosts build with one toolchain. With the AAR gone, that reason goes too. Move Kotlin
  forward together with the four things that move with it:
  - detekt, whose 1.x line runs only on the Kotlin it was built against;
  - the Compose compiler plugin;
  - snakeyaml-engine-kmp, whose newer majors are built with a newer Kotlin than ours can read;
  - Play Billing's `billing-ktx`, which Support (#198) couldn't use: 9.1.0 is built with Kotlin 2.3.
    Swap the plain `billing` artifact for it, and replace `PlayTipBilling`'s hand-written coroutine
    wrappers with ktx's `queryProductDetails`, `queryPurchasesAsync` and `consumePurchase`.
  - `kotlinx-datetime`, held at 0.6.x since exercise progress (#200) brought it into `:shared`:
    0.7 needs a newer Kotlin. It also moves `Instant` and `Clock` into the standard library's
    `kotlin.time`, so the upgrade changes those imports wherever the selectors and screens use them.

  If an Expo SDK upgrade moves React Native's Kotlin before M7, follow it then instead.
- **Let the shell own its `targetSdk`.** While it hosts the AAR it targets what Expo SDK 57 does, so
  `native/android/app/lint.xml` ignores lint's `OldTargetApi` (#148). Raise the target on the
  shell's own schedule, then delete that exception so lint flags the next one. The same PR can drop
  what the shell reads from the RN project only because it hosts it: `app.json` (version, label,
  colors, interface style) and `node_modules` (Expo module versions) in `app/build.gradle.kts`.
- **Review the CI scripts.** `ci.yml` splits the native build in two only because `:app` needs the
  RN AAR: `native-app` installs Node, prebuilds, builds and caches the AAR, installs React Native's
  NDK and borrows `android.yml`'s ccache, and even `native` installs node_modules for `:app`'s
  versions. With the AAR gone, most of that goes; the two jobs may fold back into one, and
  `android.yml` (rewritten for the shell in #149) and `scripts/native-dev.ps1` lose their RN steps
  too.
- **Rerun the startup benchmark, all-native against today's app** (M2's numbers, #150). Two things
  have to survive the deletion:
  - **The marker.** `modules/startup-marker` goes with RN, so native Home calls the same
    `reportFullyDrawn` itself once its content is drawn (Compose's `ReportDrawnWhen`). Then
    `timeToFullDisplayMs` still means "Home drawn", and the numbers stay comparable.
  - **The baseline.** Today's app can't be built from a tree without RN. Build `.bench` from the
    commit that merged #168 (`git worktree`, then the script's first step as it is today) rather than
    reusing M2's table: the same phone a year on, on a newer Android, isn't the same baseline. M2's
    numbers are the check that the rebuilt baseline still lands where it did.

  `scripts/startup-benchmark.ps1` loses its AAR step and gains the worktree build. Same S25+, same
  iterations and compilation modes. Record the result beside M2's table.

**Landmark: the Android APK contains no JS engine.** The root Expo project survives only if M8
still needs it (see below).

### M8 — iOS · sized after M7

iOS has never shipped (see `docs/ios-plan.md`), so it has no brownfield phase. It starts native,
reusing `:shared` for the domain, storage and format, which leaves only the UI to write. The plan
leaves one decision open here and gives it a deadline, M7:

- **SwiftUI** — a fully native UI, with every screen written a second time.
- **Compose Multiplatform for iOS** — one UI codebase across both platforms, at the cost of a UI
  that isn't SwiftUI.

Either way it is gated on a Mac, a device and the developer account, exactly as `ios-plan.md`
already says. That plan's Phase 5, changing the decision log and the README, applies here too.

## Rules for the duration

- **Feature freeze per screen.** Once a screen is scheduled for the current milestone, its RN version
  gets bug fixes only. New features land on the native side.
- **Format changes go through both parsers in one PR.** The conformance corpus makes this enforceable
  rather than a reminder, and "Changing the YAML format" in AGENTS.md still applies to all three
  docs mirrors.
- **No phoning home** holds for every Gradle dependency, not just the ones that look like SDKs. Check
  transitive dependencies of anything new.
- **Docs that die with RN** are rewritten or deleted in the milestone that kills their subject, not
  after: `verifying-in-the-browser.md` (M7), `sdk-57-api-notes.md` (M7), the jest sections of
  AGENTS.md (shrinking from M4 on), and `building-android.md` (M2).

## Not doing

- **No change to the YAML format, the file layout or the storage locations.** Users' libraries and
  logs must survive every milestone untouched. A format change during the migration would have to
  land in two parsers and three mirrors at once.
- **No redesign.** Screens are ported as they are. A redesign gets its own plan *after* the screen is
  native, so a regression can always be traced to either the port or the redesign, never both.
- **No integrated brownfield, and no RN-host-with-native-views approach.** Both leave RN in charge of
  navigation until a final big-bang flip, which is exactly what this plan is built to avoid.
- **The web build is not replaced.** It ends at M7. The landing site is static and unaffected.
- **No new network-using SDK** (crash reporting, analytics, remote config) slips in as part of the
  move. The zero-data-collected claim is unchanged.
