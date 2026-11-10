# M7 removal inventory: what deleting the AAR and Expo leaves to replace

> **Research, for #336 (map #335).** A point-in-time inventory against the post-routing tree,
> `34ba7a5` (#309, branch `feat/native-routing`), meant as the removal PR's acceptance list. Not
> kept live: once the removal PR lands, its diff is the record and this file is history. Line
> numbers are at `34ba7a5`. Verify against the code before acting on any row, per AGENTS.md.

Each row is marked:

- **delete**: goes with RN/Expo, nothing replaces it.
- **replace**: something the shell gets from RN/Expo today and has to do natively instead.
- **move**: a value or fact that stays but loses its current home (`app.json`, `node_modules`, a
  doc that dies).
- **keep**: looks RN-shaped but must stay (named so nobody deletes it by reflex).

The unpin work (Kotlin/AGP/detekt, Compose BOM, `minSdk`/`targetSdk`, desugaring, billing-ktx,
reorderable, kotlinx-datetime) has tickets of its own under #335. It appears here only where the
removal PR touches the same line, marked *(unpin)*.

## Sources

Everything below was read from these. Nothing was built.

- The tree at `34ba7a5`.
- The published AAR and the Expo module artifacts in `~/.m2/repository`, published Nov 9 21:14, after
  #309's commit (21:04):
  - `com/casco/kettle/brownfield/1.0.0/brownfield-1.0.0-release.aar`, with its `.pom` and `.module`;
  - `host/exp/exponent/expo.core/57.0.22/expo.core-57.0.22.pom`, which lists the modules the AAR
    pulls in at runtime;
  - each listed module's `.aar` and `.pom`.

  mavenLocal also holds stale modules from before #309, such as `expo.modules.ui`, `router`,
  `notifications`, `audio` and `haptics`, published at 21:09 and no longer in `expo.core`'s POM.
  They are excluded below.
- The main checkout's last shell build. These outputs predate #309 slightly, so treat their versions
  as a baseline to diff against, not as the post-routing truth:
  - `native/android/app/build/outputs/logs/manifest-merger-release-report.txt` (Nov 9 21:01);
  - `app/build/intermediates/metadata_library_dependencies_report/benchmark/.../dependencies.pb`
    (20:37).
- Upstream sources as installed in the main checkout's `node_modules`:
  - `expo-splash-screen@57.0.9`, `android/.../SplashScreenManager.kt` and `SplashScreenModule.kt`;
  - `expo-modules-core`, `kotlin/edgeToEdge/EdgeToEdgePackage.kt`;
  - `react-native@0.86.3`, `ReactAndroid/.../views/view/WindowUtil.kt`;
  - `expo-system-ui`, `SystemUIReactActivityLifecycleListener.kt` and `singletons/SystemUI.kt`;
  - `jest-expo`, `jest-preset.js`;
  - `expo/tsconfig.base.json`.
- The Gradle cache's `billing-9.1.0.aar` and `.pom`, `react-android-0.86.3.pom` and
  `reorderable-release.aar`.

---

## 1. What the AAR contributes without being imported

### 1.1 The AAR itself

`brownfield-1.0.0-release.aar` (545 KB) contains:

- `classes.jar`;
- `jni/arm64-v8a/libappmodules.so`;
- `assets/index.android.bundle` and `assets/app.config`;
- an empty `R.txt`, so no resources of its own;
- `AndroidManifest.xml`, which declares `minSdkVersion 24` and four `expo.modules.updates.*`
  meta-data entries (`ENABLED=false` and three others).

Its POM pulls these in:

| Dependency | Version | Scope |
|---|---|---|
| `react-android` | 0.86.3 | compile |
| `hermes-android` | 250829098.0.17 | compile |
| `kotlin-stdlib` | 2.1.20 | compile |
| `expo.core` | 57.0.22 | runtime |

`expo.core` in turn lists the module set that actually ships:

- logbox, brownfield 57.0.24, constants, jsonutils, manifests;
- expo-modules-core (`expo.modules` 57.0.18);
- webview, asset, filesystem 57.0.7, font, keepawake, localization;
- sharing 57.0.19, splashscreen 57.0.9, systemui 57.0.4.

That set agrees with `ExpoModulesPackageList.kt`'s packages: LogBox, Localization, ReactAdapter,
Base, EdgeToEdge, Sharing and SystemUI.

### 1.2 Manifest contributions, as the 21:01 merger report attributes them

| Entry | From | After removal |
|---|---|---|
| `INTERNET` | the shell's own `main/AndroidManifest.xml:13`, **and** expo-filesystem, **and** `com.google.android.datatransport:transport-backend-cct:3.1.8` | **Stays merged regardless.** Play Billing 9.1.0's POM depends on `transport-api`/`-runtime`/`-backend-cct`, `play-services-base` 18.5.0 and `play-services-location` 19.0.0. Deleting line 13 is only a cleanup. This belongs to the **Data Safety re-check** (M7 bullet 3): it is billing's telemetry transport, not ours. |
| `ACCESS_NETWORK_STATE` | `transport-backend-cct` (billing) | Stays. `site/privacy.html:176` blames "the app framework", which becomes untrue: rewrite it to name Play Billing. |
| `READ/WRITE_EXTERNAL_STORAGE` (maxSdk 32) | expo-filesystem; the shell re-declares them at `main/AndroidManifest.xml:34-43` with `tools:replace` | **Decide: delete.** Nothing in the shell needs them: it uses app files, SAF and FileProvider. Without the library entry, the `tools:replace` has nothing to replace. |
| `<queries>` SEND `*/*` | expo-sharing | delete. `FileShare.kt:71-78` uses `createChooser`, which needs no query. |
| `<queries>` OPEN_DOCUMENT_TREE | expo-filesystem, and the shell's own `:62-64` | keep the shell's. |
| `FileSystemFileProvider`, `SharingFileProvider` | expo-filesystem, expo-sharing | delete. The shell has its own `ShellFileProvider` (`:110-118`). |
| meta-data `expo.modules.updates.*` (×4) | the AAR | delete. Inert, `ENABLED=false`. |
| meta-data `org.unimodules.core.AppLoader#react-native-headless`, `com.facebook.soloader.enabled` | expo-modules-core | delete. |
| `androidx.window.extensions`/`sidecar` `uses-library` | `androidx.window:1.5.1`, via react-android | delete. The shell never imports `androidx.window`. |
| `androidx.startup` `EmojiCompatInitializer` | `emoji2` 1.4.0, via appcompat | Goes only if appcompat goes (§2.3). Compose may pull emoji2 back on its own, so check the merged manifest. |
| `READ_APP_BADGE`, `SYSTEM_ALERT_WINDOW` `tools:node="remove"` (`:44-50`) | app.json `blockedPermissions` | **Decide.** With RN gone, nothing requests either. Keep them as guards against a future dependency, or delete. Either way, the comment's "app.json's" goes. |

**Data Safety note:** OkHttp 4.9.2, Fresco and `imagepipeline-okhttp3` arrive only through
react-android's POM. They leave with it, so the APK loses its only general-purpose HTTP client.
Billing's datatransport stays.

### 1.3 Resources the shell resolves transitively

- **`Theme.SplashScreen`** and its `windowSplashScreen*` attributes, used by
  `res/values/styles.xml:11-17`. They come from `androidx.core:core-splashscreen:1.2.0`, which only
  `expo.modules.splashscreen`'s POM pulls in. **Replace:** declare `core-splashscreen` directly, in
  `libs.versions.toml`.
- `splashscreen_background` is a default (#FFFFFF) in expo-splashscreen's `values.xml`. The shell
  overrides it with a `resValue` (`app/build.gradle.kts:64`). **Move** the colour into
  `res/values/colors.xml`.
- `expo_system_ui_user_interface_style` comes from expo-systemui (`light`), overridden at
  `build.gradle.kts:69-77`. **Delete**, along with `SystemUiStyleTest.kt` and the `expo_` filter in
  `i18n/LocalePreviews.kt:39-45`.
- `ExpoLocalization_forcesRTL`/`supportsRTL` (`unset`) come from expo-localization. delete: nothing
  native reads them, and the manifest's `supportsRtl="true"` stands.
- `Theme.AppCompat.DayNight.NoActionBar` (`styles.xml:4`) comes from appcompat, which the shell
  declares itself. See §2.3.

### 1.4 ProGuard rules

The AAR's `proguard.txt` and expo-modules-core's are keep rules for:

- Expo services, packages, modules, records, `ExpoView` and `ComposeProps`;
- `ReactActivityLifecycleListener`;
- `DoNotStrip`.

react-android ships `proguard.txt` too. **They are inert today**: no build type sets
`isMinifyEnabled`, so the release is unminified, which the M0 tables also say. All of them go, and
nothing replaces them. Turning on R8 after removal is optional and out of scope.

### 1.5 Native libraries

The packaged native libraries all come from RN, Hermes and Expo:

- `libappmodules`, `libreactnative`, `libjsi`, `libfbjni`, `libhermestooling` and `libc++_shared`;
- `libexpo-modules-core`, and the unstripped `libworklets` (M0 build fact).

None of the shell's own dependencies ships a `.so`: billing, reorderable, Compose, kotlinx and
snakeyaml. With RN gone **the APK has no native code**. That retires everything that existed to
handle native code:

- `ndkVersion`, the `pickFirsts` and the `abiFilters`/`reactNativeArchitectures` property;
- every `-PreactNativeArchitectures=arm64-v8a`;
- the NDK install action;
- ccache;
- the arm64-only sideload APK rationale;
- the `ChromeOsAbiSupport` lint exception.

The Play bundle stops being ABI-split.

### 1.6 Transitive versions the shell rides on today

From the pre-#309 dependency report: diff these against the removal branch with
`:app:dependencies --configuration releaseRuntimeClasspath`.

| Library | Resolved today | Declared by the shell? | Risk once the AAR goes |
|---|---|---|---|
| `androidx.core:core`/`core-ktx` | 1.17.0, raised by expo-modules-core's `core-ktx:1.17.0` | **No**, yet used directly in 15+ files (`NotificationCompat`, `ViewCompat`, `FileProvider`, `WindowInsetsControllerCompat`…) | It may fall to what Compose and activity ask for, which can be older (billing asks 1.15.0). **Declare it.** |
| `kotlinx-coroutines-android`/`-core` | 1.10.2, from expo-modules-core | **No**, yet 31 `:app` files import `kotlinx.coroutines` | It falls to lifecycle's requirement. **Declare it.** |
| `androidx.appcompat` | 1.7.1, from expo modules | 1.7.0 (`libs.versions.toml:22`) | See §2.3. |
| `androidx.fragment` | 1.8.6 | declared only "because BrownfieldActivity" (`build.gradle.kts:348-350`) | **delete**. |
| `androidx.core:core-splashscreen` | 1.2.0, via expo-splashscreen | no | **replace**: declare (§1.3). |
| Compose ui/foundation/runtime 1.11.0-beta02, material3 1.5.0-alpha17 | pinned by the shell since #309 (`libs.versions.toml:13-19`) | yes | No change at removal *(unpin: stable BOM)*. |
| `kotlin-stdlib` | 2.2.20 resolved; the AAR asks 2.1.20 | via the plugin | none |
| `androidx.window` 1.5.1, `com.google.android.material` 1.13.0 (logbox), OkHttp, Fresco, SoLoader, fbjni | transitive | no | All delete. Nothing imports them (grep). |

`minSdk` 24 is tied to the AAR. Every Expo module and the AAR declare `minSdkVersion 24`, and
`build.gradle.kts:54` plus its comment at `:149-152` say 24 is Expo SDK 57's floor. `targetSdk` 36
is tied to Expo SDK 57 (`lint.xml:11-17`). Both are *(unpin)*.

---

## 2. Shell code (`native/android/app`)

### 2.1 `MainActivity.kt`

| Lines | What | Mark |
|---|---|---|
| 15-17, 43-45 | imports of `BrownfieldActivity`, `ReactNativeHostManager`, `ReactNativeViewFactory`, `PermissionAwareActivity`, `PermissionListener` and `SplashScreenManager` | delete |
| 57-59 | `BrownfieldActivity(), PermissionAwareActivity` | **replace** with `ComponentActivity()`. `BrownfieldActivity` is an `AppCompatActivity`, so a `ComponentActivity` base also drops the need for an AppCompat theme (§2.3). |
| 60-64 | `expoListeners`, `permissionListener`, `permissionResult` and `reactHost` | delete |
| 92-98 | comments: "RN's start, which waits for it", "as in the prebuilt app", the `super.onCreate(null)` rationale (RN fragment) | Rewrite the comments. **Decide** whether `super.onCreate(null)` stays: its only stated reason is the RN fragment, and passing `savedInstanceState` changes restore behaviour. |
| 100, 158, 181-184 | `ExpoActivityListeners` wiring | delete. Its two real jobs are replaced in §2.2. |
| 104 | `LibraryGate.awaitPrepared(this)`, which blocks the main thread so RN never starts before the seed | **keep, or decide.** `recoverRunner` (105) is documented as needing the library ready (`:258-266`), so the block may stay for that alone. The RN half of its reason goes (`LibraryGate.kt:14-18, 24-26, 44-48`). |
| 106-109 | `ReactNativeHostManager.initialize` and `createFrameLayout(this, this, "main")` | delete |
| 114-115 | `surface` passed to `ShellLayout` | delete (§2.4). |
| 122 | `LocalSplashExit provides SplashScreenManager::hide` | **replace** with the native splash's release (§2.2). |
| 173-179 | `onResume` replaying RN's permission result | delete. The shell's own prompts use `rememberLauncherForActivityResult` (`RunnerRoute.kt`, `SettingsRoute.kt`). |
| 186-195 | `onActivityResult` forwarding to `reactHost` | delete. Import uses the ActivityResult API (`ImportRoute.kt`). |
| 197-217 | `requestPermissions(…, PermissionListener)` and `onRequestPermissionsResult` | delete |
| 219-227 | `onConfigurationChanged` and `onWindowFocusChanged` forwarding | delete |
| 229-233 | `invokeDefaultOnBackPressed` | delete. `leave()` (241-243) stays for the back callback at 153. |
| 49-56 | the class KDoc ("the RN surface is still started…", "what ReactActivity and Expo's delegate wrapper did") | rewrite |
| 262, 271-276 | `rn-sessions-swept` / the `LaunchRecovery` sweep of sessions RN's runner stranded (#308) | **keep.** A user can update from 0.14.0 straight to M7's release, and the sweep runs once per install. It is migration code that outlives RN. Retiring it is a later decision. |
| 320-327 | `holdSplash()`: `SplashScreenManager.registerOnActivity` plus `preventAutoHideCalled = true` | **replace** (§2.2). |

### 2.2 What `SplashScreenManager` and `ExpoActivityListeners` do that must be rebuilt natively

**The splash.** `expo-splash-screen@57.0.9`, `SplashScreenManager.kt`, does six things:

1. **Install.** `mainActivity.installSplashScreen()` (`:57`), called before `super.onCreate`
   (`MainActivity.kt:94-95`). The native call is the same androidx `installSplashScreen()`.
2. **Hold.** It does not use `setKeepOnScreenCondition`, and its comment says why: "does not work on
   apis below 33". It adds an `OnPreDrawListener` to `android.R.id.content` that returns `false`
   while `keepSplashScreenOnScreen` is set (`:60-72`). `hide()` clears the flag (`:100-102`).

   The native port can try `splashScreen.setKeepOnScreenCondition { !released }`, but Expo's comment
   is a claim to test, not to trust. **Verify on an API 24-30 AVD and on 31-32.** If it holds badly,
   port the pre-draw listener as Expo has it.
3. **Exit animation.** A 400 ms alpha fade with `AccelerateInterpolator`
   (`SplashScreenOptions.duration = 400L`, `SplashScreenModule.kt:13`). On API 31+ it then calls
   `SplashScreenView.remove()` directly, "to avoid `applyThemesSystemBarAppearance`" (`:38-53`).
   Without a listener, androidx's default exit is the platform's, which looks different. Port the
   listener to keep "the same look" (map decision).
4. **API 31-33 crash workaround.** On the activity's `onStop` it calls
   `clearOnExitAnimationListener()`, for issuetracker 242118185, where `SurfaceControl` is released
   under the exit animation (`:76-97`). **Port it.**
5. **Release.** The release is process-global: `SplashScreenManager` is an `object`, so once `hide()`
   runs, a later activity in the same process is never held. `SplashExit` (`nav/SplashExit.kt:20-33`)
   already fires once per activity after the first read. A per-activity hold is fine and arguably
   more correct.
6. **The `ReactMarker.CONTENT_APPEARED` auto-hide** (`:21-27, 58`). Moot: `preventAutoHideCalled` is
   always true. Nothing replaces it.

The theme half stays as it is:

- `styles.xml:11-17`: `Theme.App.SplashScreen`, `windowSplashScreenBackground`, the animated icon
  `@drawable/splashscreen_logo` and `postSplashScreenTheme`;
- `values-v33/styles.xml`: `icon_preferred`.

`SplashExit.kt:15-18` carries a stale RN rationale ("on a fresh install RN writes the seed only once
it has started"): rewrite it. `HomeScreen.kt:97` and `ShellLayout.kt:30` reference the exit; their
comments are fine.

**Edge to edge.** `ExpoActivityListeners` dispatches `createReactActivityLifecycleListeners` for
every Expo package (`rn/ExpoActivityListeners.kt:23-28`). Two of those listeners do anything for
Kettle:

- **`EdgeToEdgePackage`** (`expo-modules-core/.../edgeToEdge/EdgeToEdgePackage.kt`) reflectively
  calls RN's `updateEdgeToEdgeFeatureFlag` (`WindowUtil.kt:45-69`). With targetSdk 36 that turns the
  flag on and calls `Window.enableEdgeToEdge()` (`WindowUtil.kt:164-207`), which:
  - sets `setDecorFitsSystemWindows(false)`;
  - makes the status bar transparent, and the nav bar transparent on API 29+;
  - turns off `isStatusBarContrastEnforced`;
  - sets `isNavigationBarContrastEnforced` from the theme's `enforceNavigationBarContrast`, default
    true, with nav icons light or dark by night mode when enforced;
  - sets the cutout mode: `ALWAYS` on API 30+, `SHORT_EDGES` on 28-29;
  - below API 29, uses a scrim of `0xe6FFFFFF` (light) or `0x801b1b1b` (dark) on the nav bar.

  **Replace** it with androidx activity's `enableEdgeToEdge(...)` in `onCreate`. Its defaults
  differ: `SystemBarStyle.auto` draws a nav-bar scrim on 3-button navigation. Pass transparent styles
  if the look should match, and check gesture and 3-button nav on the AVD.
  `theme/SystemBars.kt:30-37` already sets the icon appearance on every recomposition. Without this
  call the window is *not* edge to edge below API 35, and every `statusBarsPadding()` (Home,
  Programs, Library, History) and `SheetStage.kt:91`'s status-bar inset read change meaning.
- **`SystemUIReactActivityLifecycleListener`** calls
  `AppCompatDelegate.setDefaultNightMode(FOLLOW_SYSTEM)` from the string resource
  (`SystemUI.kt:15-31`). **Nothing replaces it.** FOLLOW_SYSTEM is AppCompat's default, and with no
  AppCompat activity the configuration simply follows the system. `PreferredKettleTheme` decides
  dark from preferences plus `resources.configuration` (`SystemBars.kt:28`).
- The other listeners (ReactAdapter, LogBox, Localization, Sharing, which throws and is skipped per
  `:18-22`) are RN-only. delete.

So the whole of `rn/ExpoActivityListeners.kt` (48 lines) is **delete**, its edge-to-edge job is
**replace**, and the `shell/rn/` package disappears.

### 2.3 Theme and resources

| File:line | What | Mark |
|---|---|---|
| `res/values/styles.xml:4` | `AppTheme` parent `Theme.AppCompat.DayNight.NoActionBar` | **Decide:** (a) keep appcompat as a direct dependency just for this theme, or (b) move to a platform theme. A `Theme.Material`-family parent with a `values-night` variant, since `Theme.DeviceDefault.DayNight` is API 29+ and minSdk is 24 until *(unpin)*. With `ComponentActivity` nothing else needs AppCompat: no AppCompat dialogs or widgets (`KettleAlertDialog` is Compose; grep). (b) drops appcompat, appcompat-resources and probably emoji2's startup initializer. |
| `styles.xml:5` and `res/drawable/rn_edit_text_material.xml` | RN's `TextInput` background ("React Native's template file, verbatim") | delete. Compose text fields don't read `editTextBackground`. |
| `styles.xml:6`, `res/values/colors.xml:7` | `colorPrimary #023c69`, "Expo's template default, which AppTheme hands RN's own text inputs" | delete |
| `styles.xml:7-8` | transparent status and nav bar colours | Keep with (a). With (b), `enableEdgeToEdge` sets them anyway. |
| `colors.xml:2-5` | header: colours "read from app.json's plugin options by the build" | rewrite, and **move** both colours here (§3.1). |
| `res/drawable-*/splashscreen_logo.png`, `notification_icon.png`, `mipmap-*` | prebuild's resized output, kept as copies (decisions.md, single-source entry) | **keep**, now the only source. `assets/images/*.png` become reference art (§8). |
| `lint.xml:3-9` | `IconLauncherShape`/`IconDuplicates` ignore: "made in app.json" | Keep the ignores, rewrite the comment: the artwork is now edited in `res/`. |

### 2.4 Layout, tests and the rest of `:app`

| File:line | What | Mark |
|---|---|---|
| `nav/ShellLayout.kt:22-23, 35, 54, 56-60` | the `surface: View` parameter, added hidden under everything | delete. The frame starts with `nativeSheet`. |
| `test/.../nav/ShellTest.kt:66, 82, 86, 189-199` | the `surface` stand-in and the test "the RN surface never shows…" | delete |
| `test/.../SystemUiStyleTest.kt` (whole) | asserts `expo_system_ui_user_interface_style == automatic` | delete |
| `i18n/LocalePreviews.kt:39-45` | skips `expo_` resources | delete the filter |
| `files/LibraryGate.kt:14-18, 24-26, 35-48` | RN-start rationale in the KDoc | rewrite (behaviour per §2.1, row 104) |
| `runner/RunnerNotifications.kt:163, 188-191` | `CUES_CHANNEL = "expo_notifications_fallback_notification_channel"` | **keep the id.** Users' per-channel settings live under it. The comment stays as it is. |
| `files/StoragePaths` / `LatestFiles.kt:67` ("under expo-file-system's `Paths.document`"), and `files/exercise-tracker` in session-check | the data directory RN chose | **keep** the path. Reword the comment once nothing is "RN's". |
| `debug.keystore` and `build.gradle.kts:85-93` | "Expo's template debug key" | **keep.** It signs `.dev`, and changing it breaks `.dev` updates in place. Reword. |
| `src/main/AndroidManifest.xml:1-9` | header: "What the prebuilt app's manifest says … matches the prebuilt app's release manifest" | rewrite. There is no prebuilt app to diff against any more. |
| `:13` | `INTERNET` | Optional delete: billing merges it anyway (§1.2). |
| `:34-43` | storage permissions | decide/delete (§1.2) |
| `:44-50` | blockedPermissions removals | decide (§1.2) |
| `:67` | "app.json's predictiveBackGestureEnabled: true" | keep the attribute, reword |
| `src/debug/AndroidManifest.xml:3-5, 14, 16-19` | `SYSTEM_ALERT_WINDOW` (RN dev overlays) and `usesCleartextTraffic` (Metro) | delete. The two DUMP-guarded receivers stay. |
| `src/benchmark/AndroidManifest.xml` | `profileable` plus `NavigateReceiver` | keep |
| Comment drift ("RN's runner", "as RN did", "prebuilt app") across ~60 `:app`/`:shared` files | heritage notes | **Not acceptance criteria.** Rewrite only where a comment asserts something RN-dependent that becomes false, such as the rows above. Most read as history and stay true. |

---

## 3. Gradle build

### 3.1 `app/build.gradle.kts`

| Lines | What | Mark |
|---|---|---|
| 2 | `import groovy.json.JsonSlurper` | delete |
| 11-21 | `rnRoot`, `readJson`, `npmVersion` | delete |
| 23-28 | `rnNdkVersion`, read from `node_modules/react-native/gradle/libs.versions.toml` | delete |
| 30-40 | `expo`/`expoAndroid` from `app.json`, `pluginOption` | delete |
| 47-50 | `ndkVersion = rnNdkVersion` | delete (§1.5) |
| 53 | `applicationId = expoAndroid["package"]` | **move**: the literal `com.casco.kettle`. |
| 54 | `minSdk = 24` | *(unpin)*, tied to the Expo floor. |
| 55 | `targetSdk = 36` | *(unpin)*, tied via `lint.xml:11-17`. |
| 56-59 | `versionCode`/`versionName` from `app.json` | **move** into `native/android`: a literal here, or `gradle.properties` (`kettle.versionCode`/`kettle.versionName`), which reads cleanly for `/bump`. Today they are 20 and `0.14.0`. The release that ships removal is a bump of its own. |
| 60-63 | `manifestPlaceholders["appName"] = expo["name"]` | **move**: the literal `"kettle"`. The `expo_` sentence in the comment goes. |
| 64 | `resValue splashscreen_background` from the expo-splash-screen plugin option `#17140d` | **move** into `colors.xml` (§2.3). |
| 65-68 | `notification_icon_color` from `adaptiveIcon.backgroundColor` `#c05c2b` | **move** into `colors.xml`. |
| 69-77 | `expo_system_ui_user_interface_style` | delete |
| 78-81 | the `reactNativeArchitectures` ABI filter | delete |
| 125-126 | `matchingFallbacks += "release"` ("The RN AAR and the libraries publish debug and release only") | Keep the line, since project libraries need it too. Reword. |
| 137-142 | `jniLibs.pickFirsts` `libc++_shared`, `libworklets` | delete |
| 146-154 | desugaring comment ("Goes at M7 with React Native") | *(unpin)* |
| 205-217, 237-249 | `GenerateTheme.fonts` and `fontsDir = node_modules/@expo-google-fonts` | **move.** Commit the six faces `theme.json` `Fonts` names (`SpaceGrotesk_500Medium`/`600SemiBold`/`700Bold`, `HankenGrotesk_400Regular`/`500Medium`/`600SemiBold`) plus each family's `LICENSE_FONT` (OFL), present in both packages, to a directory under `native/android` (`theme/fonts/`, say). Point `fontsDir` there. `:theme`'s `Main.kt:5, 9-13` and `Theme.kt:20, 34, 53, 106, 116` name the `@expo-google-fonts` layout and need their paths and messages changed with it. |
| 340-347 | `brownfield:1.0.0`, `expo.modules.brownfield`, `expo.core`, `expo.modules` and `expo.modules.splashscreen` at `npmVersion(...)` | delete |
| 348-350 | `appcompat` and `fragment-ktx` "because BrownfieldActivity" | fragment: delete. appcompat: per §2.3's decision. |
| *(new)* | `androidx.core:core-splashscreen`, `androidx.core:core-ktx`, `kotlinx-coroutines-android` | **add** to the catalog and here (§1.6). |
| 170-174, 231-235, 304-307 | sounds, locales, content and theme read in place from the repo root | **keep.** Plain files, not Node, as the map's no-`node_modules` rule allows. The "RN bundles too" wording at 304 becomes untrue. |

### 3.2 The rest of `native/android`

| File:line | What | Mark |
|---|---|---|
| `app/lint.xml:11-17` | `OldTargetApi` ignore | *(unpin: targetSdk)* |
| `app/lint.xml:19-25` | `ChromeOsAbiSupport` ignore (the ABI filter) | delete with the filter |
| `app/lint.xml:27-30` | `UseTomlInstead` ("versions read from node_modules") | delete, and put the new dependencies in the catalog. |
| `settings.gradle.kts:19-21` | `mavenLocal()` ("the RN app as an AAR…") | delete. Nothing else is published there. |
| `settings.gradle.kts:1-5` | header: "`:app` is the shell, which hosts the RN app from its AAR"; `:strings` from "the RN locale bundles" | reword |
| `gradle/libs.versions.toml:2-3` | "AGP and Kotlin are … React Native's own" | *(unpin)* |
| `gradle/libs.versions.toml:13-19` | the Compose pin "inside the RN AAR by way of expo-router" | *(unpin: stable BOM)* |
| `gradle/libs.versions.toml:22-23` | `appcompat`, `fragmentKtx` | fragment: delete. appcompat: §2.3. |
| `benchmark/build.gradle.kts:30-33`, `benchmark/src/main/AndroidManifest.xml:2`, `StartupBenchmark.kt:19` | `.bench` coming back as M7's baseline | keep (§5). |
| `benchmark/rn-app.init.gradle` | the init script that turns the prebuilt project into `.bench` | **delete from this tree.** It exists at #168's merge commit `55901db` (added in `cf51e00`), so the baseline worktree carries its own copy. The rewritten script uses that one. |

---

## 4. CI workflows

| File:line | What | Mark |
|---|---|---|
| `ci.yml:53-73` | the jest transform cache, sized for "88 node_modules packages through babel — react-native, reanimated, worklets, svg, every expo-*" | Rewrite or drop. With plain jest the transform set is the repo's own TS, and the stated rationale is gone. |
| `ci.yml:85-93` | the `native` job's header ("in two jobs, because `:app` hosts the RN AAR") | rewrite |
| `ci.yml:102-111` | `native`: pnpm, node and `pnpm install` "because configuring the build reads `:app`'s versions" | delete |
| `ci.yml:121-122` | `native` runs `:shared:build :strings:build :theme:build ktlintCheck detekt`, leaving out `:art:build` and `:app` | **Replace both jobs** with one `./gradlew build`: JDK 21+17, setup-gradle, no Node. `:art`'s tests are in no job today, so check whether that is deliberate. |
| `ci.yml:135-233` | `native-app` (whole): pnpm install, the install-ndk action, the AAR cache keyed on `pnpm-lock`/`package.json`/`app.json`, ccache restore, `expo prebuild`, `publishBrownfieldAllPublicationToMavenLocal`, AAR save, then `:app:build` | delete, folded into the one job. |
| `.github/actions/install-ndk/action.yml` (whole) | reads `ndkVersion` from `node_modules/react-native` | delete (§1.5) |
| `android.yml:3-7` | header: two Gradle builds, prebuild | rewrite |
| `android.yml:9-15` | the push trigger's justification ("leaves a warm ccache behind") | Keep the trigger, since it is still a release-build smoke test. Drop the ccache half of the reason. |
| `android.yml:41-43` | `timeout-minutes: 90` "A cold cache spends most of its time compiling C++" | Lower it, and reword. |
| `android.yml:50-54` | `ABI_FLAG` | delete. The `apk` variant becomes a universal APK. |
| `android.yml:65-71` | pnpm/setup-node | delete |
| `android.yml:73-82` | JDK 17+21 ("Expo SDK 57 is a JDK 17 toolchain") | Keep the setup, reword. 17 is now the shell's choice *(unpin may move it)*. |
| `android.yml:89-118` | ccache restore and configure | delete |
| `android.yml:120-144` | `pnpm install`, install-ndk, prebuild, the AAR publish | delete |
| `android.yml:159-174` | "Same package and versionCode as the prebuilt app, both read from app.json"; `$ABI_FLAG` on `assembleRelease` | reword, drop the flag |
| `android.yml:176-183` | ccache stats | delete |
| `android.yml:146-157, 185-228` | keystore restore, shred, certificate check, upload | **keep** unchanged |
| `ci.yml:8-37` (`check`), `ci.yml:39-83` (`test`) | the Node jobs | keep. Their `pnpm install` gets lighter (§7). |
| `pages.yml` | the site | unaffected |

---

## 5. Device scripts (each `.sh` with its `.ps1` twin)

| Script | Lines | What | Mark |
|---|---|---|---|
| `native-dev.sh` | 2-9 | header (AAR, Metro, `--skip-aar`, `--prebuild`) | rewrite. What's left is build, install and launch. |
| | 13-14 | `abi` | delete |
| | 16-17, 21-22 | the `--skip-aar` and `--prebuild` options | delete |
| | 31-34 | `pnpm install` when `node_modules` is missing | delete |
| | 36-39 | `expo prebuild` | delete |
| | 41-45 | the AAR publish | delete |
| | 55-67, 71 | `adb reverse 8081`, the Metro start and wait, `wait "$metro"` | delete |
| | 47-53, 69-70 | `:app:assembleDebug`, `install -r --no-streaming`, `am start …/com.casco.kettle.MainActivity` | **keep** |
| `native-dev.ps1` | 1-30 | synopsis and params (the brownfield CLI on Windows, AAR, Metro, `-SkipAar`, `-Prebuild`) | rewrite |
| | 32-33, 39-40 | the params, `$abi` | delete |
| | 51-71 | install, prebuild, AAR | delete |
| | 85-101 | the reverse, Metro and wait | delete |
| | 73-83, 100 | build, install, launch | keep |
| `route-check.sh` / `.ps1` | `.sh` 6-9, `.ps1` 10-12 | precondition "running against Metro … or `--package` … which needs no Metro" / "bundles its JS" | Reword: every build is standalone. |
| | `.sh` 140, `.ps1` 170 | "Start it against Metro first." | Reword to "Start it first." |
| `session-check.sh` | 9-12 | header: "once the RN AAR is in mavenLocal", "starts Metro if nothing answers on 8081" | rewrite |
| | 32 | "Metro stopped if this started it" | delete |
| | 178-185 | `open_session`: `adb reverse` each try, plus a relaunch on try 3 because "the app can't load its JS without" the tunnel | Delete the reverse and the JS rationale. **Keep** the resend loop for messages lost before the activity is up. Whether the try-3 `launch` stays is a judgement: its stated reason goes. |
| | 225, 254 | `metro_pid` and its kill in `restore` | delete |
| | 263-277 | start Metro, wait, the `sleep 8` for adb restarts Expo causes | delete. **This was the flakiest step** (M7 plan bullet). |
| | 314 | `a reverse tcp:8081 tcp:8081` before the launch | delete |
| | 170 | `cues_channel=expo_notifications_fallback_notification_channel` | **keep** (the id, §2.4) |
| `session-check.ps1` | 149 | `$cuesChannel` | keep |
| | 158-162 | the reverse and relaunch, as in `.sh` 178-185 | as `.sh` |
| | 206-228 | the Metro start, wait and settle | delete |
| | 279 | reverse | delete |
| | 514-517 | stopping Metro (the expo.cmd/taskkill note) | delete |
| `startup-benchmark.sh` | 12 | `abi` | delete |
| | 33-36 | the AAR publish | delete |
| | 38 | `"$abi"` on the shell's own gradlew | delete |
| | 2-5 | header | rewrite: it gains the worktree build of `.bench` from `55901db` (#168's merge) with *that* tree's `rn-app.init.gradle`, per the M7 plan's benchmark bullet. |
| `startup-benchmark.ps1` | 39 | `$abi` | delete |
| | 57-61 | the AAR publish | delete |
| | 62-65 | the shell build (drop `$abi`) | keep |
| | 11-15 | synopsis ("comes back in M7 built from a pinned commit") | rewrite once the worktree build exists |
| `.github/pull_request_template.md` | 1-12 | session-check checklist | keep (nothing RN in it) |
| `scripts/update-conformance.mjs`, `update-library-schema.mjs` | | run `pnpm jest <file>` | keep. They work with plain jest. |

The baseline build itself, the worktree at `55901db`, still needs Node, pnpm, Expo and the NDK, on
the machine that builds it. That is fine and intended. It means the M7 benchmark rerun has to happen
from a checkout where those can still be installed, which is a one-off on the bench machine, not a
dependency of the repo.

---

## 6. Skills (`.claude/commands/`)

| File:line | What | Mark |
|---|---|---|
| `bump.md:13-27` | "**`app.json` is the only place the version number lives**": `expo.version`, `expo.android.versionCode` and `expo.ios.buildNumber` move together, enforced by `app-config.test.ts` | **Retarget** to wherever §3.1 puts `versionCode`/`versionName`. **Decide on `ios.buildNumber`.** With `app.json` deleted and iOS native-only (M8), it has no home until M8 creates one. Drop the third field and its invariant, and note in `ios-plan.md` that M8 restores "one integer serves both stores". |
| `bump.md:29-34` | "Two files that look like they should change": `android/` (prebuild) and `package.json`'s `1.0.0` ("Expo doesn't read it") | Rewrite. `android/` no longer exists. `package.json`'s version is still not the app's. |
| `bump.md:58` | "the same version and versionCode you just put in `app.json`" | retarget |
| `bump.md:92` | "Edit `app.json` — all three fields" | retarget |
| `bump.md:94-98` | `pnpm test` "because … `app-config.test.ts` reads `app.json`" | Drop that half, since `store-copy.test.ts` still justifies `pnpm test`. Consider a native check of the version, or none. |
| `bump.md:4` | `allowed-tools` | Fine unless the version moves to a file needing another tool. |
| `release.md:7, 15-17` | "use the version currently in `app.json`", "`app.json` carries the version", "never edits `app.json`" | retarget |
| `release.md:52` | the forbidden substitutes `gh workflow run android.yml -f variant=apk` and "`eas build --profile preview`" | With `eas.json` gone the EAS substitute can't run. Keep the warning's spirit and drop the EAS command. The workflow substitute stays real. |
| `.claude/settings.json:3, 9` | `"expo@claude-plugins-official": true` and `Bash(npx expo config *)` | delete (configuration, so the user's call) |

---

## 7. Root Node project

### 7.1 Files deleted outright (the map's list, confirmed present)

- `app.json`, `eas.json`, `index.ts`;
- `metro.config.js`, `metro.transformer.js`;
- `patches/expo-file-system.patch` and its `patchedDependencies` entry at `pnpm-workspace.yaml:11-16`.
  expo-file-system isn't even a direct dependency any more: it arrives through `expo`.
- `jest.resolver.js`, whose job (the react-native-worklets/RN resolver composition) is gone.
- `src/domain/__tests__/app-config.test.ts`. Its four invariants lose their subject; see §8 for
  where the iOS facts go.
- `.vscode/extensions.json` (`expo.vscode-expo-tools`).
- The `.gitignore` Expo block:
  - `.expo/`, `dist/`, `web-build/` (lines 6-9);
  - `.metro-health-check*` (21);
  - `/ios`, `/android` (43-44, "generated native folders");
  - the expo-cli-generated block with `expo-env.d.ts` (49-53).

  Keep `.jest-cache/` and `node_modules/`.

### 7.2 `package.json`

| Line | Entry | Mark |
|---|---|---|
| 3 | `"main": "index.ts"` | delete |
| 7-8 | `@expo-google-fonts/hanken-grotesk`, `space-grotesk` | delete, once the TTFs are committed (§3.1) |
| 9-15 | `@expo/metro-config`, `expo`, `expo-brownfield`, `expo-localization`, `expo-sharing`, `expo-splash-screen`, `expo-system-ui` | delete |
| 16 | `i18next` | **keep**: `domain/format.ts:9` and `domain/assistant-brief.ts:3` render through `t` |
| 17 | `intl-pluralrules` | delete. It is a Hermes polyfill (`i18n/index.ts:1-7`), and Node has `Intl.PluralRules`. `locale-bundles.test.ts:29, 107` already call it natively. |
| 18 | `js-yaml` | keep (`yaml-mapping.ts`, conformance) |
| 19-21 | `react`, `react-i18next`, `react-native` | delete |
| 22 | `zod` | keep |
| 25-27 | `@types/jest`, `jest` | keep |
| 26 | `@types/react` | delete |
| 28 | `jest-expo` | delete |
| 29-32 | `knip`, `oxfmt`, `oxlint`, `typescript` | keep |
| *(new)* | a TS transform for plain jest | **add.** jest-expo supplies one today (babel-preset-expo through `babel-jest`, `jest-preset.js:36-44`). The cheapest plain-jest equivalent is `babel-jest` (bundled with jest) plus `@babel/preset-env` (`targets: {node: 'current'}`) and `@babel/preset-typescript` in a `babel.config.js`. `@swc/jest` or `ts-jest` are the alternatives. |
| 35-37 | the `start`, `android`, `ios` scripts (`expo …`) | delete |
| 49 | `"preset": "jest-expo"` | delete |
| 53-55 | `setupFilesAfterEnv` | keep the file, trimmed (below) |
| 58-60 | `transformIgnorePatterns` (react-native, expo, `@expo-google-fonts`, svg…) | delete |
| 61-63 | the `.ya?ml` transform to `jest.text-transform.js` | **keep**, since `seed-library.ts` and `content-packs.ts` import YAML as text. Reword the file's comment: it no longer counters a jest-expo asset stub. |
| 64 | `resolver` | delete |
| *(new)* | `moduleNameMapper: {'^@/(.*)$': '<rootDir>/src/$1'}` | **add.** jest-expo maps tsconfig `paths` today (`withTypescriptMapping`, `jest-preset.js:128`). |
| 66 | `testTimeout: 30000` | Drop it, as AGENTS.md:246-248 says: it is RN's screen-test leftover. |

### 7.3 Other root config

- **`jest.setup-after-env.js:11-59`.** The locale reset stays. The RNTL/`act`/`useTranslation`
  rationale goes, and so do `require('intl-pluralrules')` and `initReactI18next`. Init i18next
  directly.
- **`src/i18n/index.ts`.** Delete the polyfill import (7), `expo-localization` (9) and
  `react-i18next` (11). `deviceLanguage()` (44-50) has no device in Node: init with `lng: 'en'`, or
  keep `resources` only and let `jest.setup-after-env.js` own init. The JSDoc on the bundles' plural
  shapes (17-33) stays. `i18n/format.ts:1` imports `currentLocale` from here, and
  `locale-bundles.test.ts:3` imports `resources`.
- **`tsconfig.json:2`** extends `expo/tsconfig.base`. Inline what's still needed:
  - from `node_modules/expo/tsconfig.base.json`, keep `esModuleInterop`, `lib: ["ESNext"]`,
    `module`/`moduleResolution` (`preserve`/`bundler`, or `nodenext`), `moduleDetection: force`,
    `noEmit`, `resolveJsonModule`, `skipLibCheck` and `target: ESNext`;
  - drop `jsx`, `customConditions: ["react-native"]`, `allowJs` and the `DOM` lib.

  The path `@/assets/*` (line 10) is unused (grep), so delete it. `text-imports.d.ts` stays.
- **`.oxlintrc.json`.** Line 3 and line 50 list the `react` plugin, and no `.tsx` remains. Line 9
  turns off `react/react-in-jsx-scope`. Lines 24-29 put react, react-native and `expo*` in the
  domain layer rule: they can stay as a guard, or go. Line 61 ignores `.expo`. AGENTS.md:20's rule
  text follows whatever is chosen.
- **`.oxfmtrc.json:7`** ignores `.expo/**` and `expo-env.d.ts`. delete both.
- **`knip.jsonc`:**
  - 16-19: the `expo-updates` ignore exists because of knip's expo plugin. delete.
  - 23-26: the `@expo-google-fonts` ignores. delete with the packages.
  - 6-8: the `theme.ts` entry is the TS-decision ticket's, not this one's.
  - 21-22: `sharp`/`playwright` stay.
- **`pnpm-workspace.yaml:5-9`.** `nodeLinker: hoisted` was "a requirement of the tooling" (Metro and
  Gradle autolinking), and that tooling leaves. **Decide:** keep it, a no-op that costs nothing, or
  drop it and regenerate the lockfile. The claim in `docs/decisions.md:1139-1143` and
  `README.md:59-61` has to change either way. `virtualStoreDirMaxLength: 60` (18-22) was for
  `@babel` paths on Windows. If babel stays as jest's transform, keep it.
- **`src/` imports of Expo or RN:** only `src/i18n/index.ts:9, 11`. `src/domain`,
  `src/storage`, `src/state/selectors` and `src/test-support` import none (grep). What's left is a
  plain Node project: the TS domain, zod, js-yaml, i18next, plain jest, oxlint, oxfmt and knip,
  until the TS decision.

---

## 8. Docs

Per AGENTS.md's rule, edit what becomes false. Don't append the removal's story; the commit and
`CHANGELOG.md` get it.

| File:line | What becomes false | Mark |
|---|---|---|
| `AGENTS.md:6-8` | the "Expo HAS CHANGED" banner | delete |
| `AGENTS.md:12-15` | pnpm "strips `nodeLinker: hoisted` … that Metro and Gradle autolinking both need" | Reword (pnpm stays). The reason changes, per the §7.3 decision. |
| `AGENTS.md:20` | the layer rule's "no React, Expo or store" | follows `.oxlintrc` |
| `AGENTS.md:25-27` | "`pnpm test` — jest via `jest-expo`" | reword |
| `AGENTS.md:68-71` | `gradlew build` "needs the RN AAR in mavenLocal first (`scripts/native-dev.sh` …)" | delete the precondition |
| `AGENTS.md:113-115` | the `index.ts` bullet | delete |
| `AGENTS.md:122-141` | the `native/android/` paragraph: fonts "out of `node_modules/@expo-google-fonts`", "not part of the Expo one", "**`:app` hosts today's RN app from an AAR**", "AAR to phone to Metro", "Changing `app.json`'s icons, permissions or plugins means copying…", "today's app returns as a baseline in M7" | rewrite |
| `AGENTS.md:229-232` | "The newest of the seven is `app.json` … unreachable on iOS" | Reword: the iOS locale declaration moves to M8 (below). |
| `AGENTS.md:246-248` | `testTimeout` "M7 can drop it" | delete with the setting |
| `AGENTS.md:330-333` | the `docs/sdk-57-api-notes.md` index entry | follows that file (below) |
| `AGENTS.md:343-346` | the migration plan's banner line | the map's amendment ticket |
| `README.md:54-66` | `pnpm expo start`, the "development build / Android emulator / iOS simulator / Expo Go" links | **Replace** with the native dev loop (`scripts/native-dev.sh`, `gradlew build`) and the Node checks. |
| `README.md:59-61` | the nodeLinker claim | per §7.3 |
| `README.md:68-69` | "the RN surface the shell still starts draws nothing until … M7 removes it" | delete |
| `README.md:91` | "jest, via jest-expo" | reword |
| `README.md:50` | "iOS is not planned" | unchanged (ios-plan Phase 5 owns it) |
| `docs/building-android.md:3-9` | banner: shell "hosting the RN app as an AAR"; `eas.json` "kept only for the one-off credentials access" | rewrite. With `eas.json` deleted, say §1's EAS extraction is history. |
| `docs/building-android.md:11-32` | "The pipeline": ccache, `pnpm install`, RN's NDK, two Gradle builds, the AAR | Rewrite as one Gradle build. |
| `docs/building-android.md:34-43` | "Why not EAS" (prebuild-regenerated `android/`) | trim |
| `docs/building-android.md:47-65` | §1 "Get the upload keystore out of EAS" | Keep as history, or cut. It is done, and the secrets exist. |
| `docs/building-android.md:127-216` | "When it runs" (131-132: the AAR, a warm ccache), 164 ("`versionCode` comes from `app.json`"), and "What it costs" (171-216: C++, ccache's knobs) | Rewrite 164. 169-216 mostly delete. |
| `docs/building-android.md:217-` | signing | keep |
| `docs/sdk-57-api-notes.md` (whole) | "dies at M7" per the plan's own list (`native-migration-plan.md:575-578`) | **Move before deleting.** Its SAF findings (`:50`, `:60-100`: `content://` vs `file://`, `takePersistableUriPermission`, `"w"` truncation, the FileHandle descriptor leak) are cited by live native code at `SafBackupFolder.kt:23` and `shared/.../storage/Backup.kt:20`, and by `backup-folder-plan.md:6, 31, 169` and `exercise-images-plan.md:323`. Move the SAF section somewhere native (a decisions.md entry, or a short `docs/saf-notes.md`) and repoint those four citations. Everything else in the file is Expo-API history. |
| `docs/ios-plan.md:1-50` (banner) | still assumes an RN iOS route | Per the map: **the RN iOS route closes for good**, so say so in the banner. M8 is native. |
| `docs/ios-plan.md:5, 32-34, 60, 78-90, 105, 186, 214, 250, 256, 359, 369, 494, 497, 535` | link `app.json` as the home of the iOS config | **Move** the `ios` block's values into ios-plan before deleting `app.json`, so M8 inherits them as facts rather than a dead link: <ul><li>`bundleIdentifier com.casco.kettle`;</li><li>`buildNumber` = versionCode;</li><li>`config.usesNonExemptEncryption: false`;</li><li>`infoPlist.UIFileSharingEnabled` and `LSSupportsOpeningDocumentsInPlace`;</li><li>`entitlements[...usernotifications.time-sensitive]`;</li><li>`supportedLocales.ios: [en, pt, ja]`;</li><li>`ios.icon: assets/expo.icon`.</li></ul>These are also exactly what `app-config.test.ts` asserted. |
| `docs/adding-a-language.md:10, 21-27, 40, 54` | step 3 is `app.json`'s `supportedLocales.ios`; line 40's "pulling `expo-localization` into every test" | Make step 3 an M8 note: iOS needs `CFBundleLocalizations`. It becomes a six-place procedure on Android until then. Reword 40. |
| `docs/decisions.md:143-151` | the oxfmt entry's Metro/`jest-expo` sentence | Light edit, or leave as dated rationale. |
| `docs/decisions.md:1005-1016` | fonts: "**When the last RN screen goes,** the two `@expo-google-fonts` packages stay in `package.json` … and `knip` needs an exception" | **Contradicted by the map** (commit the TTFs with OFL). Rewrite: the rejected option, committing the TTFs, is now the choice, and the reason (no Node in the native build) is stated. |
| `docs/decisions.md:1017-1020` | "`libs.versions.toml` repeats React Native's Kotlin and AGP" | *(unpin)* delete |
| `docs/decisions.md:1021-1035` | "The shell's share of `app.json` (#148)" | Rewrite: version, label and colours live in `native/android`, the icons and manifest are the shell's own, and "copy it in the same PR" no longer applies. |
| `docs/decisions.md:1124-1150` | pnpm: "`nodeLinker: hoisted` is required, not a preference. Metro and Gradle autolinking…" | amend per §7.3 |
| `docs/native-migration-plan.md:489-553, 575-578` | M7's own section, and "Docs that die with RN" | The map's amendment ticket (#335 "Not yet specified"). The inventory confirms its bullets. |
| `docs/product-plan.md:13, 302-305, 400` | "A backend-free React Native app"; the stack table (Expo managed, expo-file-system, expo-sharing); "jest via `jest-expo`" | Edit 13 and 400. The stack table is the original plan, so mark it historical or update it. |
| `docs/open-work.md:22-23` | the pointer to the sdk-57 notes | follows the move |
| `docs/open-work.md:250-251` | Android per-app language "both of which `expo-localization`'s `supportedLocales` writes" | Reword as a native item: `android:localeConfig` plus `locales_config.xml`, written by hand or generated by `:strings`. Nothing is lost on Android today: the plugin option is iOS-only. |
| `site/privacy.html:176` | "Internet and network state — required by the app framework" | Reword: Play Billing (§1.2). Check `:164-170` in the same pass. "Modify audio settings" was expo-audio's `MODIFY_AUDIO_SETTINGS`, which no longer merges, and "keep awake" was partly expo-keep-awake's; both may be stale since #308. |
| `store/README.md:22-25` | npm vs pnpm for the store tools | unaffected |
| `CHANGELOG.md` `## Unreleased` | | the removal's user-facing line goes here (smaller APK, no JS engine) |
| `docs/history.md`, executed plans (`backup-folder`, `exercise-images`, `testing-a11y-i18n`, `up-next`, `watch-remote`, …) | RN references as history | leave, apart from the sdk-57 link targets above |

`assets/`:

- `assets/images/icon.png` stays: `store/build-assets.js:84` reads it for the Play icon.
- `android-icon-*.png` and `splash-icon.png` were prebuild's inputs. **Keep** them as reference art
  or delete them; the committed `res/` is the source now.
- `assets/expo.icon/` is the iOS Icon Composer bundle. **Keep** it for M8 and repoint
  `ios-plan.md:60`.
- `assets/sounds/` stays: `:app` reads it (`build.gradle.kts:170-171, 306-307`).

---

## 9. Open decisions surfaced (for the removal PR or its hand-off)

1. **Where `versionCode`/`versionName` live** in `native/android`: `gradle.properties` or a literal,
   with `/bump` retargeted. Also whether `ios.buildNumber` is dropped until M8.
2. **AppCompat or a platform theme** for `AppTheme` (§2.3). This decides whether appcompat and
   emoji2 stay in the APK.
3. **`setKeepOnScreenCondition` or a ported pre-draw hold** for the splash, checked on an API < 31
   AVD (§2.2).
4. **`enableEdgeToEdge` styles** that reproduce RN's transparent bars, checked on 3-button nav
   (§2.2).
5. **Permission cleanup:**
   - the storage pair: recommended delete;
   - the blockedPermissions guards: keep or delete;
   - `INTERNET`: billing merges it anyway.
6. **`nodeLinker: hoisted`**: keep or drop.
7. **Whether `LibraryGate.awaitPrepared`'s main-thread block stays** for `recoverRunner`'s sake
   alone.
8. **A new home for the SAF notes** before `sdk-57-api-notes.md` is deleted.
9. **The jest TS transform**: babel presets, `@swc/jest` or `ts-jest`.
