# Migrating to native — plan

> **M0 executed; the spike recommends go.** Its report is under M0 below. Every hard gate passed, and
> the one partial answer (Q3) re-scopes M3 rather than blocking it. **M1 has landed**: the
> conformance corpus, the native skeleton (`native/android`), a Kotlin runner for every corpus suite,
> the generated string resources and the generated Compose theme. **M2 is under way**: the shell hosts
> today's app from the RN AAR (#148); the Play pipeline, the benchmark and the release have not
> landed. M1–M3 are
> tracked as GitHub issues #141–#156, whose open/closed state is the live record; M4
> onward is ticketed once the M3 routing test (#152) has picked its shape. Written against the tree at
> `54eb9f8`, SDK 57.
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
routes correctly.

**Added to M2's checks:**

- **Time to a rendered home screen**, measured with a Macrobenchmark against the Play build. This is
  the part of Q8 that adb couldn't answer.
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

### M2 — The shell ships · ~2 weeks

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

### M3 — Native owns navigation · ~2–3 weeks, +1 week contingency (see Q3)

- **First, the half-day test from the spike report:** can one long-lived RN surface be steered by
  `navigate` messages? Its answer picks between the two shapes below, and the contingency week goes
  if it's yes.
- Native tab bar and back stack, built with Compose Navigation. Every route still renders RN.
  - **If the test passes,** that's one surface that native steers by message.
  - **If it fails,** each RN screen becomes its own registered root.
  - Per-surface deep links are out either way. The spike showed a second surface can't be routed
    that way (Q3).
- Modal routes (editors, import, settings) become native-presented sheets around RN content.
- **expo-router's role depends on the test:**
  - **If it passes,** expo-router survives as the router *inside* the RN surface.
  - **If it fails,** it's removed.
- A `BrownfieldMessaging` event, `filesChanged(kind)`, that RN stores answer by re-reading. This is
  the **re-read-on-focus** rule that makes constraint 3 work in both directions.

**Landmark: no navigation happens inside RN.** Every screen transition the user sees is a native
one, and an RN screen can be swapped for a native one by changing one route registration.

### M4 — Read-only screens · ~3–4 weeks

These screens read files and never write them, so ownership doesn't move and they make the easiest
first ports. Suggested order, smallest first: support → program guide → exercise progress → stats
(`analytics`) → history.

**Support is the tip jar**, so it writes nothing to files but does need billing. Its native port moves
the tip jar to the Play Billing Library in M4, a step pulled forward from M7, and keeps
`safe-iap.ts`'s rule: if billing is unavailable, the screen still works without it. The alternative,
keeping Support in RN until M7, was rejected. It would leave `expo-iap` in the AAR for three
milestones just to serve one screen.

Every screen is ported to the same definition of done:

- The Compose screen, the selectors it needs ported into `:shared`, and their tests.
- Compose UI tests covering whatever the screen's jest tests covered: the same cases, and the same
  "drive it in `pt`" rule for translation.
- Accessibility with the same bar as AGENTS.md: a TalkBack label only where the children don't name
  the element, roles and states set, 48dp targets, and no fixed heights on controls.
- **The RN screen, its route and its jest test are deleted in the same PR.** A screen that exists in
  both forms is a fork waiting to happen.

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
- **Unpin the Kotlin toolchain.** `native/android/gradle/libs.versions.toml` holds Kotlin and AGP
  at React Native's own versions (`node_modules/react-native/gradle/libs.versions.toml`), so the shell
  and the AAR it hosts build with one toolchain. With the AAR gone, that reason goes too. Move Kotlin
  forward together with three things that move with it:
  - detekt, whose 1.x line runs only on the Kotlin it was built against;
  - the Compose compiler plugin;
  - snakeyaml-engine-kmp, whose newer majors are built with a newer Kotlin than ours can read.

  If an Expo SDK upgrade moves React Native's Kotlin before M7, follow it then instead.
- **Let the shell own its `targetSdk`.** While it hosts the AAR it targets what Expo SDK 57 does, so
  `native/android/app/lint.xml` ignores lint's `OldTargetApi` (#148). Raise the target on the
  shell's own schedule, then delete that exception so lint flags the next one. The same PR can drop
  what the shell reads from the RN project only because it hosts it: `app.json` (version, label,
  colors) and `node_modules` (Expo module versions) in `app/build.gradle.kts`.
- **Review the CI scripts.** `ci.yml` splits the native build in two only because `:app` needs the
  RN AAR: `native-app` installs Node, prebuilds, builds and caches the AAR, installs React Native's
  NDK and borrows `android.yml`'s ccache, and even `native` installs node_modules for `:app`'s
  versions. With the AAR gone, most of that goes; the two jobs may fold back into one, and
  `android.yml` (rewritten for the shell in #149) and `scripts/native-dev.ps1` lose their RN steps
  too.

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
