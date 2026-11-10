# Research: SwiftUI vs Compose Multiplatform for iOS over `:shared`

> **Throwaway research note** for #344 (map #335, `docs/native-migration-plan.md` M8). Lives on the
> `research/m8-ios-ui` branch only, never merged. Facts and costs only, each with its source. It
> makes **no decision**: that belongs to the grilling ticket it blocks (#345). Gathered 2026-11-09
> against the tree at `34ba7a5` (`feat/native-routing`).

Sources are primary throughout: JetBrains' Kotlin and Compose Multiplatform (CMP) docs and release
notes, the CMP source (`JetBrains/compose-multiplatform-core`, `jb-main` at `c1d032e`, read in a
sparse clone), Maven Central's Gradle module metadata for each artifact, Apple's developer
documentation, Touchlab's SKIE docs and releases, and this repo. Where a source was silent, the note
says so instead of filling the gap.

## The answer in brief

- **Either way, `:shared` is the cheap half.** Its `commonMain` (77 files, ~9.7k lines) has no JVM
  import, and its three dependencies all publish iOS artifacts. What blocks iOS targets is the
  **Kotlin pin**, not the code: the YAML library `:shared` already uses was built with a newer Kotlin
  than the one `:shared` compiles with, and Kotlin/Native gives no forward-compatibility guarantee.
  M7's unpin removes that block. Five `jvmMain` files (380 lines, `java.io.File`,
  `java.text.Normalizer`, `java.time`) need iOS `actual`s.
- **Either way, about a fifth of `:app` is platform glue that nothing reuses**: ~32 files and ~4.7k
  of its ~20.7k lines. That covers the Activity, the View sheet, `RunnerService` and its
  notifications and cues, SAF, the file share, billing and the reminder alarm. All of it gets
  rewritten for iOS whichever UI is picked, and the runner's background behaviour lives there.
- **What CMP would actually save is the other ~16k lines of Compose screens**, and that saving
  isn't free. Every `R.` reference (796 of them) moves to CMP's `Res`, the `@StringRes Int` ids and
  the `Resources`-taking helpers change type, `pluralText`'s zero step needs a common port, the
  three generators (`:strings`, `:theme`, `:art`) need to emit `composeResources`, and the screens
  move out of `:app` into a multiplatform module. That is a refactor of the Android app as well as
  new iOS work.
- **CMP on iOS is Stable** (JetBrains, 1.8.0, May 2025), and its VoiceOver mapping covers every
  semantic Kettle uses today. LiveRegion only arrived in 1.12.0, though, and Dynamic Type is a
  linear scale capped at 1.8×, against iOS's non-linear one. Fully native text editing is still
  experimental.
- **SwiftUI means writing every screen a second time.** It gets native accessibility and Dynamic
  Type for free, and puts the runner's Live Activity (which has to be SwiftUI anyway) in the same
  language as everything else. It also needs a Kotlin-to-Swift bridge for `:shared`'s 40 sealed
  interfaces. Swift export, the bridge that would do that well, is still Alpha. SKIE is mature, but
  it is a compiler plugin that each Kotlin bump has to wait for.
- **Background cues don't separate the two options.** The cue paths are all Apple frameworks:
  UserNotifications, AVAudioSession, and ActivityKit with its SwiftUI widget extension. They have
  to be written for iOS either way, and ActivityKit can't be called from Kotlin at all. Under CMP
  the runner would be Kotlin plus a Swift island. Under SwiftUI it is Swift throughout.

---

## 1. Compose Multiplatform on iOS: stability, accessibility, text input

### Stability and versions

- **Stable since 1.8.0.** JetBrains' release post is titled "Compose Multiplatform 1.8.0 Released:
  Compose Multiplatform for iOS Is Stable and Production-Ready"
  ([blog.jetbrains.com, May 2025](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/)).
  The post itself returned 403 to the fetcher. Its title and date come from the search index, and
  its body (including the "~9 MB" size figure that secondary coverage quotes) was **not read**.
- **Latest stable is 1.12.2** (15 Oct 2026), after 1.12.1 (22 Sep) and 1.12.0 (25 Aug). 1.13.0 is in
  alpha and moves to Kotlin 2.4.20
  ([releases](https://github.com/JetBrains/compose-multiplatform/releases)).
- **It maps to Jetpack Compose 1.12.1.** iOS 14 is the minimum (raised from 13 in 1.11). The docs
  ask for Kotlin ≥ 2.1.0, and "upgrade to Kotlin 2.2.20 for projects targeting platforms with
  rapidly evolving support, such as iOS"
  ([compatibility](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)).
  The 1.10 and 1.11 notes put native targets' "latest features" at Kotlin 2.2.20 and 2.3.10
  ([1.10](https://kotlinlang.org/docs/multiplatform/whats-new-compose-110.html),
  [1.11](https://kotlinlang.org/docs/multiplatform/whats-new-compose-111.html)). `:shared` is on
  **2.1.20** today (`gradle/libs.versions.toml`).
- **The recent changes are iOS polish, not new foundations.** 1.11 made concurrent rendering the
  default and dropped `iosX64`. 1.12 improved lazy-list scrolling and moved Skia to m150
  ([1.12 notes](https://kotlinlang.org/docs/multiplatform/whats-new-compose-112.html)). 1.12.1
  fixed a crash when iOS read a disposed `AccessibilityElement`, and 1.12.2 a `TextField`-and-`Dialog`
  focus crash ([releases](https://github.com/JetBrains/compose-multiplatform/releases)).
- **Not available in `commonMain`** in 1.12.2: `imeNestedScroll`, `systemGestureExclusion`,
  `magnifier`, `LocalOverscrollConfiguration`, `animatedVectorResource`, material3-adaptive and
  window-size-class. **Android-only by nature:** `Context`, `LocalContext`, `LocalConfiguration`,
  `Activity`, `ComposeView`, `LocalView`, `Bitmap`
  ([Android-only components](https://kotlinlang.org/docs/multiplatform/compose-android-only-components.html)).
  Lifecycle, ViewModel and Navigation are multiplatform
  ([CMP vs Jetpack](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-and-jetpack-compose.html)).

### VoiceOver

- **Mapping.** "Semantics data produced by Compose APIs is … mapped to native objects and properties
  that are consumed by iOS Accessibility Services". `testTag` maps to `accessibilityIdentifier`, and
  XCTest's `performAccessibilityAudit()` works
  ([iOS accessibility](https://kotlinlang.org/docs/multiplatform/compose-ios-accessibility.html)).
  1.8.2 added RTL handling, traversal groups and text fields' editable trait
  ([1.8 notes](https://kotlinlang.org/docs/multiplatform/whats-new-compose-180.html)). 1.12.0 added
  **`LiveRegion`**, `VerbatimTtsAnnotation` and `LocaleList` for VoiceOver
  ([releases](https://github.com/JetBrains/compose-multiplatform/releases)).
- **What the source maps, checked against what Kettle uses.** The CMP source
  (`compose/ui/ui/src/iosMain/.../platform/Accessibility.ios.kt` and
  `accessibility/SemanticConfigurationUtils.ios.kt`) maps `Role`, `Heading`, `Selected`, `Disabled`,
  `StateDescription`, `ToggleableState`, `ProgressBarRangeInfo`/`SetProgress`, `EditableText`,
  `LiveRegion` (posted as `UIAccessibilityAnnouncementNotification`), `CustomActions` (as
  `UIAccessibilityCustomAction`), `TraversalIndex`/`IsTraversalGroup` and
  `HideFromAccessibility`/`InvisibleToUser`.
  Kettle's `:app` uses `Role.` (61 hits in 32 files), `heading()` (23/20), `contentDescription`
  (41/19), `clearAndSetSemantics` (20/9), `selected =` (8/6), `liveRegion`, `stateDescription` and
  `customActions` (1 file each). All of those are in the mapped set.
- **Two Android View calls.** `announceForAccessibility` is used in 2 files. It's an Android `View`
  API, so it needs an iOS counterpart (a `liveRegion`, or `UIAccessibilityPostNotification`).
- **The docs say nothing about Switch Control or known limitations.** The accessibility page is
  also inconsistent: it says `AccessibilitySyncOptions` "has been removed" in 1.8.0 and still
  documents it as experimental.

### Dynamic Type

No JetBrains doc page covers it. The source does
(`compose/ui/ui/src/iosMain/.../scene/FontScale.ios.kt`):

- `fontScale` follows `traitCollection.preferredContentSizeCategory` and updates on trait changes.
  So `sp` text does respond to the iOS Text Size setting.
- The scale is a **fixed linear table**: XS 0.8 … Large 1.0 … XXXL 1.3, then the five
  accessibility sizes at 1.4, 1.5, 1.6, 1.7 and 1.8. The source's own comment: "These values don't
  match the scale shown by Text Size because iOS uses non-linear scaling calculated by
  UIFontMetrics, while Compose uses linear scaling." So CMP's largest text stays smaller than
  native at the accessibility sizes, and is scaled evenly across text styles where SwiftUI's text
  styles each grow by their own curve.
- Kettle's house rule (`minHeight`, never `height`, on controls) carries over unchanged. The
  large-text pass on device is still needed under either option.

### Text input

- **The default text field is Compose's own text input**, which CMP calls "the stable choice for
  consistency across platforms".
- **Fully native iOS editing is experimental.** 1.11 added a `UITextInput`/`UIKeyInput`-backed
  implementation (native caret, selection, system menu with Autofill, Translate and Search) behind
  `PlatformImeOptions { usingNativeTextInput(true) }` with `@ExperimentalComposeUiApi`
  ([1.11 notes](https://kotlinlang.org/docs/multiplatform/whats-new-compose-111.html)).
- **Keyboard traits.** 1.9 added `PlatformImeOptions` for UIKit keyboard type, autocorrection and
  return key ([1.9 notes](https://kotlinlang.org/docs/multiplatform/whats-new-compose-190.html)).
  1.10 added custom `inputView`/`inputAccessoryView`
  ([1.10](https://kotlinlang.org/docs/multiplatform/whats-new-compose-110.html)).
- **Recent text-field fixes are still landing.** 1.12.0 restyled selection handles to match iOS
  and fixed a Scribble crash, and 1.12.2 fixed a focus crash
  ([releases](https://github.com/JetBrains/compose-multiplatform/releases)).
- **Kettle's text surface is small:** `TextField`/`BasicTextField` appear in 3 files
  (`form/FormField.kt`, `list/SearchBar.kt`, `runner/RunnerOverlayViews.kt`), and `KeyboardOptions`
  in 1. Most numeric entry goes through the app's own pad (`PadEntry`, `RunnerOverlayViews.kt`),
  not the system keyboard.

### Mixing the two

CMP and SwiftUI interoperate in both directions
([SwiftUI integration](https://kotlinlang.org/docs/multiplatform/compose-swiftui-integration.html)):

- Compose embeds in SwiftUI as `ComposeUIViewController` inside a `UIViewControllerRepresentable`.
- SwiftUI embeds in Compose as `UIKitViewController` around a `UIHostingController`.
- Since 1.10, interop views size to their intrinsic content
  ([1.10](https://kotlinlang.org/docs/multiplatform/whats-new-compose-110.html)).
- Caveats from the same page: the Info.plist needs `CADisableMinimumFrameDurationOnPhone` ("Without
  it, the app will crash at runtime"), SwiftUI can't be written in Kotlin, and Swift sees Kotlin
  entry points under file-derived names (`Main_iosKt`).

So a hybrid is possible: CMP screens with a native SwiftUI sheet or Live Activity, or SwiftUI
screens with one Compose screen. It isn't a strict either/or.

### Testing

Common Compose UI tests (`runComposeUiTest`) are **Experimental**. They run on the iOS simulator,
and on Android only as instrumented tests: "common tests can't run through `android (local)` test
configurations" ([testing](https://kotlinlang.org/docs/multiplatform/compose-test.html)). Kettle's
58 Robolectric test files (~13.3k lines in `app/src/test`) could keep testing the shared screens on
Android, but they would not exercise iOS. Whether Robolectric's `@Config(qualifiers = "pt")` still
selects the locale once strings come from CMP's `Res` instead of Android resources is **unverified**,
and it matters, since driving a screen in `pt` is how AGENTS.md catches hardcoded strings.

---

## 2. How much of `native/android/app` CMP would actually reuse

I counted `app/src/main/java/com/casco/kettle/shell` with a script (`grep`-level, so approximate):
**131 files, 20,743 lines.**

### Tier A: platform glue, rewritten for iOS under either option (~32 files, ~4,650 lines)

| Area | Files | What is Android-only |
|---|---|---|
| Entry | `MainActivity.kt`, `rn/ExpoActivityListeners.kt` | `ComponentActivity`, lifecycle observers, Expo (gone at M7) |
| The View sheet | `nav/ModalSheet.kt`, `SheetStage.kt`, `SheetMotion.kt`, `SheetChrome.kt`, `NativeFrames.kt`, `ShellLayout.kt` | `FrameLayout`/`ImageView`/`View`, `ValueAnimator`, `drawToBitmap`, `VelocityTracker`, `AccessibilityDelegateCompat`, `OnBackPressedCallback` |
| Runner platform | `runner/RunnerService.kt`, `RunnerNotifications.kt`, `RunnerCues.kt`, `RunnerHost.kt`, `RunnerLoop.kt`, `LaunchRecovery.kt`, `RunnerRoute.kt` | `Service`/FGS, `PowerManager`, `HandlerThread`, `SoundPool`, `AudioFocusRequest`, `NotificationCompat`, the permission launcher, haptic constants, `AccessibilityManager` |
| System integrations | `reminder/RestDayReminder.kt`, `settings/SafBackupFolder.kt`, `export/FileShare.kt`, `importer/ImportRoute.kt`, `system/Clipboard.kt`, `support/TipBilling.kt`, `settings/SettingsRoute.kt` | `AlarmManager`, SAF/`DocumentsContract`, `FileProvider` intents, `ActivityResultContracts`, `ClipboardManager`, the Play Billing activity |
| Files and assets | `files/*.kt` (6) | `Context`, `AssetManager`, `Handler`/`Looper` |
| Locale and theme | `i18n/LocaleFormats.kt`, `i18n/PluralText.kt`, `theme/SystemBars.kt`, `theme/PreferredScheme.kt` | ICU `LocaleData`, `DateFormat`, `Resources`, `Configuration`, `WindowInsetsControllerCompat` |

Some of these (`RunnerRoute`, `SettingsRoute`, `ImportRoute`) mix composables in with the platform
calls, so a CMP port would split them rather than rewrite them whole.

**The View sheet is the expensive one for CMP.** It is what #229 (a Compose sheet) would replace,
and the map lists #229 as out of scope. CMP on iOS needs *some* non-View sheet: a Compose one
(#229's work, done first), or SwiftUI's `.sheet` hosting Compose content.

### Tier B: Compose screens that would move to `commonMain` (~99 files, ~16,100 lines)

The script counts 69 files (~10.5k lines) with no Android import and no `Context`/`Activity` at
all. The rest touch Android only lightly: `@StringRes`/`@DrawableRes` annotations, `android.util.Log`,
a `Resources` parameter, `BackHandler`, `ReportDrawnWhen`. But **none of Tier B is
copy-paste-portable as written**, because the whole UI is threaded through Android resources:

- **796 `R.` references** (`R.string`, `R.plurals`, `R.drawable`) in 69 files, and **509
  `stringResource`/`pluralText` calls** in 64. CMP's equivalent is `Res.string.x` with its own
  `stringResource`/`pluralStringResource`, from the strings XML in `composeResources/values*`
  ([resources usage](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-usage.html)).
  Mechanical, but it touches every screen.
- **Typed ids.** Ids passed around as `@StringRes Int` (`nav/Route.kt`, `WorkoutShapeText.kt`,
  `StarterPacks.kt`, the editors) become `StringResource` objects. Code that formats outside
  composition through `Resources` (`RunnerText.kt`, `StepText.kt`, `ComingUp.kt`,
  `IntervalScreen.kt`, `EntryResults.kt`, `Readings.kt`, `BackupSection.kt`) has to switch to CMP's
  **`suspend` `getString`/`getPluralString`**, which changes how those helpers are called.
- **`pluralText`'s zero step** (`i18n/PluralText.kt`, the `pt` "0 sessão" fix that AGENTS.md
  requires) is built on `Resources.getQuantityString` and needs a common port. The CMP docs don't
  say which plural rules `pluralStringResource` applies on iOS.
- **The generators.** `:strings` writes Android `res/values*/strings.xml`, `:art` writes
  `drawable/*.xml` VectorDrawables plus an `R.drawable` lookup, and `:theme` copies fonts into
  `res/font`. CMP reads the same strings-XML shape, Android vector XML ("without references to
  Android resources"), and TTF/OTF fonts. It also accepts a directory **generated by a Gradle task**
  through `compose.resources { customDirectory(..., directoryProvider = tasks.register…) }`
  ([resources setup](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-setup.html)).
  So the generators retarget rather than get rewritten. `:art`'s generated `R.drawable` lookup
  becomes `Res.drawable`.
- **Libraries.** `material3` is used in 59 files, and CMP ships a material3 port. The workout
  editor's `sh.calvin.reorderable` 3.1.0 already publishes `iosArm64`/`iosSimulatorArm64` (its
  Maven module metadata). `navigation-compose` (one `NavHost`, in `nav/Shell.kt`) has a
  multiplatform counterpart.
- **The module split.** These screens live in an Android application module today. CMP needs them
  in a multiplatform library module with `androidTarget()` and iOS targets, with `:app` reduced to a
  host. That changes the Android build and its lint, detekt and ktlint setup, on the shipping app.

**Rough net.** CMP would reuse most of ~16k lines of screen code, once a repo-wide `R`→`Res`
migration and a module split land on the Android app first. SwiftUI reuses none of those lines and
writes the equivalent screens in Swift, but leaves the Android app untouched. Neither reuses Tier A.

---

## 3. What `:shared` needs for iOS, under either option

### Code

- **`commonMain`: 77 files, ~9.7k lines, no `java.*`/`javax.*`/`kotlin.jvm` import.** The only
  `expect`s are `String.toNfd()`/`toNfc()` (`domain/Normalization.kt`). Number formatting doesn't
  lean on the platform: `jsNumber` (`JsKey.kt`) spells ECMAScript's `Number::toString` by hand,
  precisely because Kotlin's `toString` differs. So the conformance goldens shouldn't drift by
  platform. That is untested until an iOS test target runs the corpus.
- **The caveat.** With only a JVM target, nothing compiles `commonMain` against a non-JVM stdlib,
  so "portable" here is read off the imports and hasn't been compiled. Adding the targets is what
  proves it.
- **`jvmMain`: 5 files, 380 lines**, all of them needing an iOS `actual` or an `iosMain` twin:
  - `Normalization.jvm.kt`: `java.text.Normalizer`. On iOS that's Foundation's
    `decomposedStringWithCanonicalMapping`/`precomposedStringWithCanonicalMapping`, through
    `platform.Foundation`.
  - `storage/StoredFileReads.kt`, `StoredFileWrites.kt`, `PreparedLibrary.kt`, `Backup.kt`:
    `java.io.File`, `FileOutputStream`, `IOException`, `java.time.Instant`/`DateTimeFormatter`. Either
    an `expect` file layer over Foundation (`NSFileManager`), or a multiplatform I/O library. The
    second would be a new dependency, and the no-phoning-home rule applies to it.
- **Tests.** `jvmTest` (47 files) runs the conformance corpus by reading `conformance/` from disk
  (`ConformanceCorpus` in `shared/build.gradle.kts`). An iOS test target would need its own way to
  reach those files.

### Dependencies (Maven Central's `.module` and `kotlin-tooling-metadata.json`)

| Artifact | In use | iOS variants published | Built with Kotlin |
|---|---|---|---|
| `it.krzeminski:snakeyaml-engine-kmp` | 4.0.1 | `iosArm64`, `iosSimulatorArm64`, `iosX64` | **2.2.10** (4.0.1); 5.0.0 (latest) is **2.4.10** |
| `org.jetbrains.kotlinx:kotlinx-datetime` | 0.6.2 | same three | 1.9.21 (0.6.2); 0.8.0 is latest, 0.8.0 built with 2.1.20 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.8.1 | same three | (1.12.0 is latest) |

All three can go to iOS. **The blocker is the compiler.** `:shared` compiles with Kotlin 2.1.20,
and snakeyaml-engine-kmp 4.0.1's klibs were produced by 2.2.10. Kotlin's own rule for klibs:
"Forward compatibility is not guaranteed. For example, the 2.0.x compiler is not guaranteed to read
binaries produced by the 2.1.x compiler"
([evolution principles](https://kotlinlang.org/docs/kotlin-evolution-principles.html)). On the JVM
the same pairing works on a best-effort one-version window, which is presumably why it builds today.
**So iOS targets realistically wait for M7's Kotlin unpin**, which is also what CMP's ≥ 2.2.20 asks
for. The datetime move to 0.7+ that M7 already plans (`Instant`/`Clock` into `kotlin.time`) lands
before iOS rather than during it.

Also from the Kotlin docs, and true under either option: a distributed Kotlin/Native binary has to
ship third-party licence notices (Kotlin, Apache Harmony, libbacktrace, the Unicode database, etc.)
([Kotlin/Native binary licenses](https://kotlinlang.org/docs/native-binary-licenses.html)). That
belongs in Phase 5 of `ios-plan.md`.

### Only for SwiftUI: how Swift sees `:shared`

The shape of `:shared`'s API: **40 sealed interfaces, 72 data classes, 16 enums, no `suspend`, no
`Flow`**. The runner is a pure reducer (`reduceRunner`, `RunnerCommand`/`RunnerEffect`, both
sealed) plus `nextTimeUpdateAt` (`RunnerWake.kt`).

- **Objective-C export (the default, stable).** Enums arrive as Swift *classes*, so `switch` needs a
  `default`. Sealed classes get no special mapping. Generics only on classes, not interfaces.
  Collections bridge to `NSArray`/`NSDictionary` with a copy. Pure Swift APIs can't be called from
  Kotlin ([Obj-C interop](https://kotlinlang.org/docs/native-objc-interop.html)). For a codebase of
  sealed interfaces, that means hand-written casts in Swift.
- **Swift export: "Alpha … breaking changes are expected."** Enums become Swift enums, sealed
  hierarchies become Swift enums with `.sealedType()` for exhaustive `switch`, `suspend` becomes
  `async`, and flows become `AsyncSequence`. The limits: only `final` classes, generics erased to
  their bounds, `List`/`Set`/`Map` inheritors ignored, and direct Xcode integration only
  ([Swift export](https://kotlinlang.org/docs/native-swift-export.html)).
- **SKIE (Touchlab).** Exhaustive Swift enums for sealed classes and enums, `async` for `suspend`,
  `AsyncSequence` for Flow, and overloads for default arguments
  ([skie.touchlab.co](https://skie.touchlab.co/)). It supports "Kotlin versions from 2.0.0 up to
  2.4.10" on its intro page, aims at two feature releases, and usually follows a new Kotlin "within
  a couple of working days" ([intro](https://skie.touchlab.co/intro)). The latest GitHub release,
  0.10.15 (25 Sep 2026), added Kotlin 2.4.20, and Maven Central lists 0.10.16
  ([releases](https://github.com/touchlab/SKIE/releases)). It is a **compiler plugin**, so every
  Kotlin bump waits for a matching SKIE.

Under CMP almost none of this matters. The screens call `:shared` in Kotlin, and only a thin
entry point (`ComposeUIViewController`) and the Swift islands from §4 cross into Swift.

---

## 4. The background-cue constraints (`docs/ios-plan.md` Phase 4) and what they do to each option

What `ios-plan.md` establishes, and the native iOS runner inherits (its #308 banner says Phase 4's
*reasoning* still stands):

- Once the app is suspended, in-process timers stop. So does in-app audio, unless it is a
  background-audio session.
- The guaranteed cue is a **local notification** scheduled for the step's end, with a sound and
  `interruptionLevel = .timeSensitive` so Focus doesn't swallow it. That needs the
  `com.apple.developer.usernotifications.time-sensitive` entitlement, or iOS downgrades it. It fires
  once and can't tick.
- The optional upgrade is **background audio** (`UIBackgroundModes: audio` plus an audio session
  that really plays), and it's subject to App Review.
- **Live Activities** are named as a good fit and explicitly deferred to a plan of their own.

What Apple's docs add:

- **Suspension is the norm.** "Typically, an app is in a suspended state when it's in the
  background", and modes such as audio are the exception ("Use background execution modes
  sparingly … If an alternative to executing in the background exists, use the alternative")
  ([background modes](https://developer.apple.com/documentation/xcode/configuring-background-execution-modes)).
  `beginBackgroundTask` buys finite time to finish work, after which "the system puts your app into
  the suspended state"
  ([extending background time](https://developer.apple.com/documentation/uikit/extending-your-app-s-background-execution-time)).
  Neither is a timer mechanism.
- **Background audio.** `AVAudioSession.Category.playback` keeps playing through the Silent switch
  and screen lock, and it needs the `audio` value in `UIBackgroundModes` to continue in the
  background
  ([playback](https://developer.apple.com/documentation/avfaudio/avaudiosession/category-swift.struct/playback)).
  App Review 2.5.4: "Multitasking apps may only use background services for their intended purposes"
  ([guidelines](https://developer.apple.com/app-store/review/guidelines/)).
- **Time Sensitive** notifications "can break through system controls such as Notification Summary
  and Focus" (iOS 15+)
  ([timeSensitive](https://developer.apple.com/documentation/usernotifications/unnotificationinterruptionlevel/timesensitive)).
  A scheduled request "remains active until its trigger condition is met", and the system presents
  it when the app isn't running
  ([local notifications](https://developer.apple.com/documentation/usernotifications/scheduling-a-notification-locally-from-your-app)).
- **Live Activities.** Their UI is built "with a widget extension, WidgetKit, and SwiftUI"
  ([ActivityKit](https://developer.apple.com/documentation/activitykit)). They last up to 8 hours
  active and 12 on the Lock Screen, carry at most 4 KB of data, and can be updated from the
  background. An update can carry an `AlertConfiguration` with a sound
  ([displaying live data](https://developer.apple.com/documentation/activitykit/displaying-live-data-with-live-activities)).
  SwiftUI's `Text(timerInterval:pauseTime:countsDown:showsHours:)` (iOS 16+) "displays a timer
  counting within the provided interval"
  ([Text](https://developer.apple.com/documentation/swiftui/text/init(timerinterval:pausetime:countsdown:showshours:))).
  The page doesn't explicitly say it ticks while the app is suspended.

What that means for the choice:

- **The engine is already shaped for suspension, under either option.** `StepClock` is kept in
  wall-clock epoch milliseconds "so a rebuild survives a kill or a reboot" (`RunnerClock.kt`), and
  `nextTimeUpdateAt` (`RunnerWake.kt`) computes the next moment anything changes. That moment is
  exactly what an iOS shell would hand to `UNTimeIntervalNotificationTrigger` before suspending, and
  re-derive on resume. It all lives in `commonMain`, so both options reuse it.
- **Everything that actually cues is an Apple framework.** UserNotifications, AVFAudio and haptics
  can be called from Kotlin through Kotlin/Native's `platform.*` libraries, which import system
  frameworks from their Objective-C headers
  ([Obj-C interop](https://kotlinlang.org/docs/native-objc-interop.html)). So a CMP app *can* own
  its cues in Kotlin, while a SwiftUI app writes them in Swift. That's roughly a wash.
- **ActivityKit is the asymmetry.** "Pure Swift modules are not yet supported" from Kotlin
  ([Obj-C interop](https://kotlinlang.org/docs/native-objc-interop.html)), and ActivityKit is
  a Swift API whose UI must be SwiftUI in a widget extension. If Live Activities ever come in, a CMP
  app carries a Swift/SwiftUI island with a bridge to the Kotlin runner. A SwiftUI app doesn't need
  the bridge. They are deferred today, so this is a future cost, not an M8 one.
- **The keep-awake question** (`ios-plan.md`'s Watch section) is
  `UIApplication.isIdleTimerDisabled` under either option.
- **Verification is the same either way.** None of this can be checked on a simulator
  (`ios-plan.md` Phase 4). There's no `session-check.sh` for iOS. Whatever M8 builds needs its own
  device procedure, and that cost is the same for both.

---

## 5. Cost and risk side by side

| | SwiftUI | Compose Multiplatform |
|---|---|---|
| **Screens** | Written a second time: ~16k lines of Compose as their Swift equivalent, then every future screen change made twice | Mostly reused, after an `R`→`Res` migration (796 refs, typed ids, `suspend` string helpers, the `pluralText` port) and a move to a KMP UI module, both on the shipping Android app |
| **Platform glue** (~4.7k lines: runner service, cues, files, share, billing, reminder, entry) | Written in Swift | Written in Kotlin (`platform.*`) or Swift, the same scope |
| **The sheet** | SwiftUI `.sheet`; the Android View sheet is untouched | Needs a non-View sheet first (#229, out of the M7 map's scope), or a SwiftUI sheet via interop |
| **`:shared` for iOS** | iOS targets, `jvmMain` actuals, Kotlin unpin (M7) | Same |
| **Kotlin→Swift bridge** | Needed for every screen. Obj-C export is stable but clumsy for 40 sealed interfaces; Swift export is Alpha; SKIE is mature but a compiler plugin each Kotlin bump waits for | A thin entry point plus Swift islands only |
| **VoiceOver** | Native | Mapped, covering all of Kettle's current semantics. LiveRegion only since 1.12.0; `announceForAccessibility` needs replacing |
| **Dynamic Type** | Native, non-linear, per text style | Linear table capped at 1.8× (`FontScale.ios.kt`), so smaller than native at the accessibility sizes |
| **Text input** | Native | Compose's own by default. Native editing is experimental (1.11+). Kettle's surface is 3 files |
| **Look and feel** | iOS-native controls | Kettle's Material-derived, theme-driven UI drawn by Skia. The house style is custom already (`ListRow`, `theme.json`), which narrows the gap. Whether it's acceptable on iOS is a judgment call, not a fact |
| **i18n** | A generator from the locale bundles into an Apple strings format, with `pt`'s zero rule re-solved (not researched here) | `:strings` retargets to `composeResources`. Plural rule handling on iOS is undocumented |
| **Tests** | XCTest; Robolectric covers only Android | Robolectric keeps testing the shared screens on Android, though `pt`-qualifier behaviour with `Res` is unverified. Common UI tests are Experimental |
| **Live Activity** (deferred) | Same language, no bridge | Swift/SwiftUI island plus a bridge |
| **Toolchain coupling** | SKIE (if used) pins Kotlin bumps | CMP pins Kotlin bumps too: it tracks the latest Kotlin, and 1.13 already needs 2.4.20 |
| **Where the risk sits** | Volume: two UIs to keep in step, and every feature twice | Android churn before iOS starts, plus the iOS-fidelity gaps above |

## Open questions this note couldn't settle from the sources

1. Does Robolectric's `@Config(qualifiers = "pt")` still pick `pt` strings when they come from CMP's
   `Res` instead of Android resources? That needs a ten-minute spike.
2. Which plural rules does CMP's `pluralStringResource` apply on iOS, and does it ever select `zero`
   for Portuguese? The docs only say "the same as for quantity strings on Android".
3. Does `Text(timerInterval:)` in a Live Activity keep counting while the app is suspended? Apple's
   page implies it but doesn't say so.
4. The CMP 1.8.0 post's body (the size overhead in particular) wasn't read, because it returned 403.
5. `commonMain`'s portability is read off its imports. It hasn't been compiled for iOS.
