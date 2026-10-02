# Migrating to native — plan

> **Not executed.** A forward-looking plan; nothing below has been built. Written against the tree at
> `54eb9f8`, SDK 57. Milestone 0 is a spike with an explicit go/no-go, and **everything after it is
> conditional on a "go"**: if the spike fails its exit criteria, this file gets a banner saying so and
> the decision log gets the reason, so the idea isn't re-proposed from scratch.

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

### M1 — Foundations · ~3–4 weeks

Nothing user-visible ships in M1. It builds the pieces every later milestone depends on.

- **The `:shared` KMP module**: types, schema validation, snake_case ↔ camelCase mapping, merge,
  program resolution and units, ported from `src/domain/`. The selectors in `src/state/selectors/`
  that the native screens will need are ported when those screens are, not here.
- **A conformance corpus** in `conformance/` *(TS side landed; see its README for the contract)*:
  YAML inputs plus generated golden outputs (parsed library and session, refusal paths, repair,
  merge, program resolution, overrides, units). The inputs are drawn from the existing domain tests
  and `site/examples/*.yaml`. The docs samples stay with `docs-samples.test.ts`, since extracting
  them from markdown is a TS-only job. **Both** jest and the Kotlin tests run it. A fixture one side
  passes and the other fails is a fork of the format, and it fails CI.
- **Generated resources**, so translations and colors stay single-sourced while both UIs exist:
  - `en.json`, `pt.json` and `ja.json` → `values{,-pt,-ja}/strings.xml`, plurals included.
    Android's plural rules never select `zero` for Portuguese, so the generator emits an explicit
    zero branch for every `_zero` key. That is the same "0 sessão" bug the bundles already guard
    against, and it gets a test.
  - `constants/theme.ts` → a Compose theme (colors, `Spacing`, the always-dark `RunnerColors`).
- **CI**: a Gradle job for `native/android` and `:shared`, with ktlint and detekt at zero warnings
  (the same rule `pnpm run lint` has), alongside the existing pnpm jobs.

**Landmark: the conformance corpus is green on both implementations in CI**, and a translated string
added to `en.json` shows up in a Compose preview in all three languages with no hand edit.

### M2 — The shell ships · ~2 weeks

The native shell becomes the app on Play while still showing **100% RN content**: one RN surface
running the existing expo-router app, unchanged. Users see nothing different, and that is the point.
This milestone retires the riskiest thing in the plan, replacing what Play installs, while nothing
else is changing.

- `android.yml` builds the AAR, then the shell's `.aab`. Same package, same upload key, the next
  `versionCode`. Read the signing entry in the decision log first. Nothing about which key signs
  what may change in this milestone.
- An update-in-place test on a device: install the current Play build, create a library and a
  session, update to the shell, and confirm everything is still there.
- Internal track → closed testing → production. Don't skip steps.

**Landmark: a production release whose entry point is native**, with no regression reports through
one full release cycle. Every later milestone ships through this pipeline.

### M3 — Native owns navigation · ~2–3 weeks

- Native tab bar and back stack, built with Compose Navigation. Every route still renders RN, each
  opened at its deep link.
- Modal routes (editors, import, settings) become native-presented sheets around RN content.
- expo-router is reduced to a deep-link dispatcher for the RN surfaces, or removed if the M0
  answer to question 3 forced separate roots.
- A `BrownfieldMessaging` event, `filesChanged(kind)`, that RN stores answer by re-reading. This is
  the **re-read-on-focus** rule that makes constraint 3 work in both directions.

**Landmark: no navigation happens inside RN.** Every screen transition the user sees is a native
one, and an RN screen can be swapped for a native one by changing one route registration.

### M4 — Read-only screens · ~3–4 weeks

These screens read files and never write them, so ownership doesn't move and they make the easiest
first ports. Suggested order, smallest first: support → program guide → exercise progress → stats
(`analytics`) → history.

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
  workout, program, session) and import, together with `validateConfig`-style form validation,
  content packs, the seed library, `library-translation`, export/share and the SAF backup folder
  (`storage/backup.ts`).
- **Ownership move, in one release**: native becomes the only writer of the library file and of
  edited session files. The RN runner, which is still RN, keeps writing *new* session files. Its
  stores re-read on `filesChanged`.
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
- Move the tip jar from `expo-iap` to the Play Billing Library directly, degrading as gracefully as
  `safe-iap.ts` does now. Move the rest-day reminder off `expo-notifications`.
- Re-check the **Data Safety** claim: list every Gradle dependency that touches the network and
  confirm none transmits anything.

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
