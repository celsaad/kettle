# Research: one compatible toolchain set once React Native's pin is gone

> **Throwaway research note** for #337 (map #335, `docs/native-migration-plan.md` M7). It lives only on
> the `research/m7-toolchain-set` branch and is never merged. It records facts, each with its source;
> the unpin hand-off tickets make the decisions. Gathered 2026-11-09 against the post-routing tree
> (`34ba7a5`). Nothing was built: every "first build checks" item below is still open.

**How versions were read.** "Newest stable" means the newest non-prerelease version in each
artifact's `maven-metadata.xml` on Maven Central, Google Maven or the Gradle Plugin Portal, read on
2026-11-09, and the newest release on `services.gradle.org/versions`. Facts about a library's build
come from its published binaries: `aar-metadata.properties` inside the AAR, the POM's `kotlin-stdlib`
dependency, and `kotlin.Metadata mv=` read with `javap`. Behaviour comes from release notes and source
at the release tag. File references are paths under `native/android/` at `34ba7a5`.

---

## 1. The answer in one table

The "proposed" column is the newest set that sits inside **every** published compatibility table. A
second set that runs past them, newest of everything, is in §2.

| Dependency | Current (`gradle/libs.versions.toml`) | Newest stable (2026-11-09) | Proposed | Moves with |
|---|---|---|---|---|
| Kotlin (KGP, the Compose compiler plugin, the stdlib) | 2.1.20 | **2.4.21** (2.5.0 is at Beta2) | **2.4.21** | AGP: built-in Kotlin needs KGP ≥ 2.2.10 |
| AGP | 8.12.0 | **9.5.1** | **9.3.3** | Kotlin, Gradle, Compose BOM |
| Gradle (wrapper) | 9.3.1 | **9.9.0** | **9.7.1** | AGP (≥ 9.5.0 for 9.3), Kotlin (≤ 9.7.0 tested) |
| Compose BOM | 2025.05.01 plus pins `composeShipped` 1.11.0-beta02 / `material3Shipped` 1.5.0-alpha17 | **2026.09.00** (ui/foundation/animation/runtime 1.12.1, material3 **1.4.0**) | **2026.09.00, pins deleted** | AGP ≥ 9.1, `compileSdk` 37 |
| `compileSdk` (not a library, but forced) | 36 | 37 (AGP 9.3 supports up to 37) | **37** | the Compose BOM |
| detekt | 1.23.8 (+ `detektKotlin` 2.0.21 pin) | **1.23.8**. 2.0.0 is still alpha (alpha.6, 2026-08-04) | **1.23.8, pin kept** | nothing (see §3.4) |
| ktlint / ktlint-gradle | 1.8.0 / 14.2.0 | 1.8.0 / 14.2.0 | **no change** | nothing |
| snakeyaml-engine-kmp | 4.0.1 | **5.0.0** (2026-10-08) | **5.0.0** | Kotlin ≥ 2.3 |
| Play Billing | `billing` 9.1.0 | 9.1.0 (2026-06-18) | **`billing-ktx` 9.1.0** | Kotlin ≥ 2.2 |
| sh.calvin.reorderable | 3.1.0 | **3.1.0** (2026-04-20, still the latest) | **no change** | nothing |
| kotlinx-datetime | 0.6.2 | **0.8.0** (2026-05-07) | **0.8.0** | Kotlin ≥ 2.3 (stable `kotlin.time.Instant`) |
| kotlinx-serialization-json | 1.8.1 | **1.12.0** (2026-10-19, built on Kotlin 2.4.10) | **1.12.0** | Kotlin ≥ 2.3 |
| activity-compose | 1.10.1 | 1.13.0 | 1.13.0 | the Compose BOM (needs `compileSdk` 36) |
| navigation-compose | 2.9.0 | 2.10.2 (needs `compileSdk` 37, AGP ≥ 9.1) | **delete it**: nothing imports `androidx.navigation` | — |
| robolectric | 4.16.1 | 4.17 (2026-09-10, "supports SDK 37") | 4.17 if `targetSdk` goes to 37 | `targetSdk` (#337 doesn't decide it) |
| benchmark-macro / uiautomator / androidx.test.ext:junit | 1.4.1 / 2.3.0 / 1.2.1 | 1.5.0 / 2.4.1 / 1.3.0 | newest | Kotlin (the toml says they're held for the same reason) |
| desugar_jdk_libs | 2.1.5 | — | goes with the minSdk decision, not with this table | minSdk (§3.9) |

Not researched in depth: the last three library rows, and junit 6.1.3, which is already the newest.

Sources for the version column: the `maven-metadata.xml` of each artifact, as described above.
Release dates: GitHub releases (detekt, snakeyaml-engine-kmp, reorderable, kotlinx-datetime,
kotlinx.serialization, robolectric), and the Play Billing release notes.

---

## 2. Why AGP 9.3 and Gradle 9.7, and not the newest of each

Three published tables meet here:

- **Kotlin's KGP table**: "2.4.20–2.4.21 | Gradle 7.6.3–9.7.0 | AGP 8.5.2–9.3.1". Kotlin 2.5 has no
  row yet. Source: <https://kotlinlang.org/docs/gradle-configure-project.html> (the compatibility
  table).
- **AGP 9.3**: minimum Gradle **9.5.0**, JDK 17, max API 37. The latest patch is 9.3.3. Source:
  <https://developer.android.com/build/releases/agp-9-3-0-release-notes>.
- **AGP 9.5**: minimum Gradle **9.6.0**, max API 37. Source:
  <https://developer.android.com/build/releases/gradle-plugin>.

So **Kotlin 2.4.21 + AGP 9.3.x + Gradle 9.5.0–9.7.x** is the newest set inside every table. 9.3.3 is a
patch past KGP's tested 9.3.1, which is the one liberty taken. The **newest-of-everything set**,
Kotlin 2.4.21 + AGP 9.5.1 + Gradle 9.9.0, puts AGP two minors past KGP's tested range and Gradle two
minors past it. The repo already runs out of range today, which is evidence that tested ranges aren't
hard limits: KGP 2.1.20 is tested only to Gradle 8.12.1, and the wrapper is on 9.3.1
(`gradle/wrapper/gradle-wrapper.properties`). Choosing between the two sets is the hand-off's call.
The in-table set is recommended because it costs nothing in features this code uses. AGP 9.4 and 9.5
add backup testing and per-module DSL opt-out, and neither is used here.

---

## 3. Per dependency: what the move breaks in this code

### 3.1 AGP 8.12 → 9.x (the expensive one)

AGP 9.0 is the major version, and every item below comes from its release notes
(<https://developer.android.com/build/releases/agp-9-0-0-release-notes>) unless noted.

- **Built-in Kotlin is on by default, and `org.jetbrains.kotlin.android` "is not compatible with the
  new DSL".** Applying it fails with "The 'org.jetbrains.kotlin.android' plugin is no longer required
  for Kotlin support since AGP 9.0." (<https://developer.android.com/build/migrate-to-built-in-kotlin>).
  To change:
  - `app/build.gradle.kts:7` and `benchmark/build.gradle.kts:5`: drop `alias(libs.plugins.kotlin.android)`.
  - `build.gradle.kts:8`: drop the `apply false` line.
  - `gradle/libs.versions.toml`: drop the `kotlin-android` plugin entry.
  - `kotlin { compilerOptions { … } }` stays valid. The `jvmTarget` line becomes optional ("defaults to
    `android.compileOptions.targetCompatibility`").
- **KGP version.** AGP 9.0 "has a runtime dependency on KGP 2.2.10". To use a higher one, put it on
  the build classpath. The root `alias(libs.plugins.kotlin.jvm) apply false` already does that.
  **First build checks** that `buildEnvironment` resolves KGP 2.4.21, not 2.2.10.
- **Kotlin sources may no longer go into the `java` set.** "With built-in Kotlin, the only supported
  option is to add the directories to the `AndroidSourceSet.kotlin` set", and generated directories
  go through `variant.sources.kotlin`. Three places touch this, and the second one's comment names the
  old Kotlin 2.1 behaviour as its reason:
  - `app/build.gradle.kts:133-134` adds `src/steering/java`, which holds `NavigateReceiver.kt`, with
    `java.srcDir`.
  - `app/build.gradle.kts:299` and `:302` add the generated `ArtDrawables.kt` and `GeneratedTheme.kt`
    through `variant.sources.java`.
  - Whether `src/main/java/**.kt` is still compiled by default isn't stated in the AGP docs I read.
    **First build checks**.
- **`android.defaults.buildfeatures.resvalues` defaults to false.** The three `resValue(...)` calls
  (`app/build.gradle.kts:64,68,76`) fail unless `buildFeatures { resValues = true }` is set. All three
  read `app.json` and move or go in the removal and splash tickets, so this only bites if AGP moves
  first.
- **Other default flips, each harmless here as far as the code shows:**
  - `onlyEnableUnitTestForTheTestedBuildType=true` makes the `beforeVariants` unit-test disabling at
    `app/build.gradle.kts:293` partly redundant.
  - `uniquePackageNames`: there are no library modules.
  - `r8.strictFullModeForKeepRules` and `optimizedResourceShrinking`: release doesn't minify today, and
    there's no `isMinifyEnabled` in the build.
  - `defaultTargetSdkToCompileSdkIfUnset`: `targetSdk` is set explicitly.
  - `dependency.useConstraints=false`: the Compose per-group pins rely on Compose's own Gradle-module
    constraints, not AGP's.
- **The old variant API and `BaseExtension` are gone with `newDsl=true`.** The shell's own scripts use
  only `androidComponents` (`onVariants`, `beforeVariants`, `HostTestBuilder`), so they're already on
  the new API. The exposure is third-party plugins, which §3.4 covers.
- `experimentalProperties["android.experimental.self-instrumenting"]` (`benchmark/build.gradle.kts:33`):
  none of the AGP 9 notes I read mention it either way. **First build checks** (`:benchmark` assembles).
- NDK: AGP 9 defaults to r28c. `ndkVersion = rnNdkVersion` (`app/build.gradle.kts:50`) goes with the AAR.

### 3.2 Kotlin 2.1.20 → 2.4.21

Sources: <https://kotlinlang.org/docs/compatibility-guide-23.html> and `compatibility-guide-24.html`.

- **The annotation use-site default changes in 2.4.0.** "When you don't specify a use-site target, the
  compiler now uses `param` and `property` if they apply." This touches `@StringRes val label` and
  `@DrawableRes val icon` on constructor properties (`app/src/main/java/com/casco/kettle/shell/nav/Route.kt:42-43`,
  `…/library/LibraryScreen.kt:177`). It's binary only, and nothing reflects on them. Android lint
  might see the annotation in more places. **First build checks**.
- **The Compose compiler's old Gradle options are errors in 2.4.0** (`enableStrongSkippingMode` and
  others). The build sets none of them, and the `kotlin-compose` plugin already takes Kotlin's
  version (`gradle/libs.versions.toml`, `version.ref = "kotlin"`), so it moves for free.
- **`KotlinDependencyHandler.platform()` is removed.** `shared/build.gradle.kts` already calls Gradle's
  `project.dependencies.platform(...)`, so nothing changes.
- **Every module sets `allWarningsAsErrors`.** Any new 2.2–2.4 warning fails the build. The ones the
  guides list are source patterns: `javaClass` references, `readLine()`, `return` in an
  expression-bodied function without a type. I didn't grep for every one of them. **First build checks.**
- **Syntax ceiling.** detekt 1.23.8 parses with Kotlin 2.0.21's PSI, and ktlint 1.8.0 with 2.2.21's
  (from each tool's `gradle/libs.versions.toml` at its release tag). Kotlin 2.4 *compiles* newer
  syntax, but while detekt stays on 1.x the code shouldn't use anything newer than 2.0. That rules out
  guard conditions in `when`, `$$` multi-dollar strings, non-local `break`/`continue`, context
  parameters and explicit backing fields. That's a rule for review, not a build failure, until it is one.

### 3.3 Compose BOM 2025.05.01 + pins → 2026.09.00, no pins

- **Forced moves, from the AARs' `aar-metadata.properties`:**

  | Artifact | `minCompileSdk` | `minAndroidGradlePluginVersion` |
  |---|---|---|
  | ui-android 1.12.1 | 37 | 9.1.0 |
  | foundation-android 1.12.1 | 37 | 9.1.0 |
  | navigation-compose 2.10.2 | 37 | 9.1.0 |
  | material3 1.4.0 | 35 | — |
  | activity-compose 1.13.0 | 36 | — |

  The foundation release notes say compileSdk 37 "requires AGP 9.2.0 or later", which is stricter
  than the AAR's 9.1.0. Either way, **the stable BOM cannot land before AGP 9.2+ and `compileSdk` 37.**
  Sources: the AARs on Google Maven, and
  <https://developer.android.com/jetpack/androidx/releases/compose-foundation>.
- **What the BOM maps to** (its POM, `androidx/compose/compose-bom/2026.09.00`): ui, foundation,
  animation and runtime at **1.12.1**, so 1.11.0-beta02 moves *up*. material3 is at **1.4.0**, so
  1.5.0-alpha17 moves *down*. The four `compose-*` entries and `compose-material3` in the toml
  (`gradle/libs.versions.toml:18-19, 52-57`) and the "Over the BOM" block (`app/build.gradle.kts:358-363`)
  become plain BOM-managed coordinates.
- **The material3 downgrade, against what the code uses.** The code imports only Text, Surface,
  MaterialTheme, Icon, TextButton, CircularProgressIndicator, light/darkColorScheme, NavigationBar,
  NavigationBarItem, NavigationBarItemDefaults, LocalTextStyle, LocalContentColor, HorizontalDivider,
  ColorScheme, ButtonDefaults and AlertDialog. All of them are in 1.4.0. A grep for the 1.5-only
  symbols the release notes add (`ComposeMaterial3Flags`, `LocalMaterialTheme`,
  `isTextButtonContentPaddingFixEnabled`, `AlertDialogDefaults.IconSize`, expressive APIs) finds
  nothing. So the downgrade should compile. **What it changes visually** (from
  <https://developer.android.com/jetpack/androidx/releases/compose-material3>):
  - 1.5.0-alpha14/15 "Update `TextButtons` to have correct material3 paddings specs". It's on by
    default, with `isTextButtonContentPaddingFixEnabled` as the opt-out. So at 1.4.0, **TextButton
    content padding reverts to the old spec** in `theme/DialogAction.kt:41,50` (every dialog's actions)
    and `support/SupportScreen.kt:193` (Retry).
  - 1.5.0-alpha16 fixes "icon-label padding" on an item with an undisplayed `selectedLeadingIcon`. The
    entry doesn't name the component, so it may or may not be the nav bar.
  - 1.5.0-alpha15 moves MaterialTheme onto a single CompositionLocal internally. Nothing reads the old
    locals by name.
  - I only read alpha09–alpha17's notes. alpha01–08 are unread, so the **bar and dialog need a
    side-by-side look on a device**, as the toml's own comment ("material3's bar and sheet move with
    it") warns.
  - 1.5.0 is at **rc01 (2026-11-04)**, so a stable 1.5.0 and a BOM that carries it are probably weeks
    away. Waiting for it avoids the downgrade altogether. That's the hand-off's call.
- **The "sheet"** in that comment is the shell's own `nav/ModalSheet.kt` and friends, not a material3
  component. Nothing in `app/src/main` imports a material3 sheet, so the BOM touches it only through
  foundation and animation 1.12.
- Foundation 1.11.x → 1.12.x behaviour changes (pausable composition, text-field flags): the page I
  read had no "important changes since 1.11.0" summary. **The device pass checks**, alongside the
  material3 look.
- **navigation-compose is declared (`app/build.gradle.kts:366`) but nothing imports it**
  (`grep -r "import androidx.navigation"` is empty since routing went native in #309). It's the one
  Compose-family artifact that also needs compileSdk 37 and AGP 9.1, so deleting it beats moving it.

### 3.4 detekt: stay on 1.23.8

- **There is no stable 2.x.** The releases page lists 2.0.0-alpha.0 through alpha.6 (newest
  2026-08-04), all prereleases. 1.23.8 (2025-02-20) is "Latest". Source:
  <https://github.com/detekt/detekt/releases>.
- **1.23.8's own row:** Gradle 8.12.1, Kotlin 2.0.21, AGP 8.8.1, JDK 21. It's already used past it:
  Gradle 9.3.1 and AGP 8.12 today. Source: <https://detekt.dev/docs/introduction/compatibility/>.
- **Kotlin can move without detekt.** detekt runs its own compiler classpath, and
  `build.gradle.kts:43-48` already pins that configuration to `detektKotlin` 2.0.21 while the build
  compiles with 2.1.20. That mechanism doesn't care what the build's Kotlin is, so it keeps working at
  2.4.21. The limit is the syntax ceiling in §3.2.
- **AGP 9 + detekt 1.23.8.** 1.23.8's Android integration
  (`detekt-gradle-plugin/.../internal/DetektAndroid.kt` at `v1.23.8`) finds Android projects with
  `extensions.findByType(BaseExtension::class.java)` and walks `applicationVariants`. Under the new
  DSL that lookup returns null, so it creates no `detekt<Variant>` tasks. That isn't a crash: issue
  detekt#8908 reports exactly this ("stopped creating Android variant specific tasks") for 2.0-alpha
  on AGP 9. **This build doesn't use the variant tasks.** It runs the plain `detekt` task over
  `source.setFrom(files("src"))` (`build.gradle.kts:32-35`), and that's what CI calls (`ci.yml:122`).
  So 1.23.8 should keep working under AGP 9. I didn't run it. **The first AGP 9 build is the check**,
  and the riskiest single question in this note.
- **If the hand-off takes 2.0 anyway, or when it goes stable, this is what changes against
  `detekt.yml`.** It's read from the default config and rule source at `v2.0.0-alpha.6`:
  - The plugin id goes from `io.gitlab.arturbosch.detekt` to `dev.detekt`, and the task class from
    `io.gitlab.arturbosch.detekt.Detekt` (`build.gradle.kts:1`) to `dev.detekt.gradle.Detekt`.
    `jvmTarget` is still a task property.
  - `detektKotlin` and the resolution pin can go. alpha.6 is built against Kotlin 2.4.10, Gradle
    9.6.1 and AGP 9.3.1, tested on JDK 25.
  - `style>UnusedImports` (`detekt.yml:12`) becomes **`UnusedImport`**.
  - `style>UnusedPrivateMember` (`detekt.yml:14`) is **split** into `UnusedPrivateClass`,
    `UnusedPrivateFunction` and `UnusedPrivateProperty`. The `ignoreAnnotated: ['Preview', 'LocalePreviews']`
    belongs on `UnusedPrivateFunction`.
  - `style>ForbiddenImport>imports` (`detekt.yml:20`) becomes **`forbiddenImports`**. The
    `value`/`reason` entries keep their shape (`valuesWithReason()` in `ForbiddenImport.kt`).
  - `naming>FunctionNaming` with `ignoreAnnotated` is unchanged, and `ignoreAnnotated` is still a
    common key (`IGNORE_ANNOTATED_KEY` in `dev.detekt.api.Config`). `config>warningsAsErrors` is still
    there.
  - Rule-set renames in alpha.2 (`documentation`→`comments`, `empty`→`emptyblocks`,
    `bugs`→`potentialbugs`) don't touch `detekt.yml`, which configures only `naming` and `style`.
  - 2.0's `config>validation: true` fails the build on any key it doesn't know. So the yml edit and
    the version bump have to land in one commit.
  - Open 2.0 issues with AGP built-in Kotlin are about type resolution (#9304, #9402, #9198). Type
    resolution isn't used here.

### 3.5 ktlint: nothing to move

ktlint 1.8.0 and ktlint-gradle 14.2.0 are both the newest stable. ktlint 2.0.0 is at ALPHA-4 and
moves its coordinates to `io.github.ktlint`. ktlint-gradle **14.1.0 "Adds support for Android
projects using the new DSL"** (built-in Kotlin), so the AGP 9 move needs nothing from it. 1.8.0's
release notes promote five rules to standard, but they already apply today because the version
doesn't change. Sources: <https://github.com/pinterest/ktlint/releases>,
<https://github.com/JLLeitschuh/ktlint-gradle/releases>.

### 3.6 snakeyaml-engine-kmp 4.0.1 → 5.0.0

Sources: the release notes for <https://github.com/krzema12/snakeyaml-engine-kmp/releases/tag/v5.0.0>,
and its PR #737, "Behaviour changes worth a release note".

- **It's built with Kotlin 2.4.10** (POM: `kotlin-stdlib` 2.4.10), so it needs a compiler that reads
  2.4 metadata, which means Kotlin ≥ 2.3. It moves with or after Kotlin.
- **Non-scalar mapping keys now throw** unless `allowNonScalarKeys = true`. `YamlLoad.kt` already
  refuses them in its own event pass (`MappingKeys.checkKey`, "complex keys are not supported"),
  which runs before `Load`. So the message the user sees doesn't change. Keep the default.
- **`LoadSettings` gains a constructor parameter** (binary break on the JVM). `YamlLoad.kt:310-315`
  uses named arguments and recompiles, so nothing changes.
- **A stricter TAB rule (upstream issue 99).** "A TAB is now accepted as separation after a token
  (`a:<TAB>value`, `-<TAB>item`), but a block key or entry after leading spaces plus a TAB … is now
  rejected." That's a parse-result change, so it's exactly what `conformance/` exists to hold against
  js-yaml. No case in the corpus has a TAB in it today (`grep` over `conformance/*.yaml`). **Add one
  in the same PR**: `a:\tvalue`, and `-\titem`, run through both the TS and the Kotlin runner.
- Comment attachment after block scalars changed (issue 92). The loader doesn't read comments, so
  nothing changes.

### 3.7 Play Billing: `billing` 9.1.0 → `billing-ktx` 9.1.0

- **The version doesn't move.** 9.1.0 (2026-06-18) is the newest. The 8.x removals
  (`queryPurchaseHistory`, `querySkuDetailsAsync`, the no-arg `enablePendingPurchases`) are already
  behind this code. Source: <https://developer.android.com/google/play/billing/release-notes>.
- **Why ktx waits for Kotlin:** `billing-ktx-9.1.0.aar`'s classes carry `kotlin.Metadata mv=[2,3,0]`,
  read with `javap`. That confirms the toml's "built with Kotlin 2.3", so it needs a Kotlin ≥ 2.2
  compiler.
- **The extension functions 9.1.0 ships** (`com.android.billingclient.api.BillingClientKotlinKt`, from
  `javap`) and what each replaces in `app/src/main/java/com/casco/kettle/shell/support/TipBilling.kt`:

  | ktx | returns | replaces |
  |---|---|---|
  | `suspend fun BillingClient.queryProductDetails(QueryProductDetailsParams)` | `ProductDetailsResult(billingResult, productDetailsList: List<ProductDetails>?)` | the `suspendCancellableCoroutine` around `queryProductDetailsAsync` in `products()` |
  | `suspend fun BillingClient.queryPurchasesAsync(QueryPurchasesParams)` | `PurchasesResult(billingResult, purchasesList)` | the one in `purchases()` |
  | `suspend fun BillingClient.consumePurchase(ConsumeParams)` | `ConsumeResult(billingResult, purchaseToken: String?)` | the one in `consume()` |

  `ProductDetailsResult.productDetailsList` is `@RecentlyNullable`, so `?: return null` stays.
  ktx's result **drops `unfetchedProductList`**, which `QueryProductDetailsResult` (8.0+) carries;
  nothing here reads it. **`connect()` stays hand-written**, because ktx has no `startConnection`
  wrapper. After the change, `kotlinx.coroutines.suspendCancellableCoroutine` and `kotlin.coroutines.resume`
  are still imported for `connect()`. ktx implements each wrapper with a `CompletableDeferred`, which
  matches the cancellation behaviour of today's code: the callback still fires after a cancel and is
  ignored.
- It can move on its own once Kotlin is ≥ 2.2. `TipBilling` is the boundary Support's tests fake, so
  only `PlayTipBilling` changes and no test should.

### 3.8 sh.calvin.reorderable: no move

3.1.0 (2026-04-20) is still the newest GitHub release and the newest on Maven Central. Its POM depends
on `kotlin-stdlib` 1.9.0 and JetBrains Compose 1.7.0, so the Kotlin and Compose moves can't break
reading it. The toml's "a release built with Kotlin 2.3 or later would not [be readable]" stops
applying once Kotlin is 2.4: any future release becomes takeable. Its use is
`WorkoutEditorScreen.kt:86-87` (`ReorderableItem`, `rememberReorderableLazyListState`). Compose
foundation 1.12 under it is **a device check of the drag** (#226's behaviour), not a compile risk.

### 3.9 kotlinx-datetime 0.6.2 → 0.8.0

Sources: the release notes for <https://github.com/Kotlin/kotlinx-datetime/releases/tag/v0.7.0>,
v0.7.1 and v0.8.0, the README's "Deprecation of `Instant`", and the source at `v0.8.0` and Kotlin
`v2.4.21`.

- **0.7.0 removed `kotlinx.datetime.Instant` and `Clock`, and 0.7.1 restored them as typealiases of
  `kotlin.time.*`.** In 0.8.0 those aliases are `@Deprecated(level = WARNING)`
  (`core/common/src/DeprecatedInstant.kt`, `DeprecatedClock.kt`). With `allWarningsAsErrors`, **every
  import has to change in the same commit**: 22 `import kotlinx.datetime.Instant` and 5
  `import kotlinx.datetime.Clock` across 23 files, which `grep -rl "kotlinx.datetime.Instant\|kotlinx.datetime.Clock"`
  lists. They're in `:shared`'s `domain/` (ExerciseEditing, ExerciseProgress, NextUp,
  ProgressChartLayout, RestDayReminder, HistoryStats, SessionSummary) and their tests, and in `:app`'s
  home, progress, reminder, stats and i18n/LocaleFormats plus their tests. It's mechanical: swap the
  import for `kotlin.time.Instant` / `kotlin.time.Clock`.
- **`kotlin.time.Instant` is `@SinceKotlin("2.3")` with `@WasExperimental(ExperimentalTime::class)`**
  (stdlib `kotlin/time/Instant.kt` at `v2.4.21`). On the current Kotlin 2.1.20 it would need
  `@OptIn(ExperimentalTime::class)` everywhere, which is why this has to wait for Kotlin ≥ 2.3.
- **`LocalDate.dayOfMonth` is deprecated (WARNING) in favour of `day`**, so these become errors too:
  `history/SessionRow.kt:104` and `sessioneditor/SessionEditorScreen.kt:177`. `monthNumber` isn't used.
- **`Instant.parse` semantics.** `kotlin.time.Instant.parse` accepts the offset forms the tests use
  (`"2026-03-09T18:00:00-04:00"`, `HistoryStatsTest.kt:178-195`) and throws `IllegalArgumentException`.
  So `SessionSummary.kt:42-47`'s `catch (_: IllegalArgumentException)` still catches it, and
  `Instant.parseOrNull` can replace the try/catch. Its grammar is "ISO-8601-1:2019, 5.4.2.1b), excluding
  the format without the offset". The `session`/`records` conformance cases rerun on the Kotlin side
  are what prove nothing the TS side accepts is now refused.
- **`DayOfWeek` is no longer a typealias of `java.time.DayOfWeek`.** The code uses
  `DayOfWeek(isoNumber)` (`LocaleFormats.kt`), `.ordinal` (`HistoryStats.kt:165`) and the enum
  constants, and all of them are common API. Nothing converts to `java.time`.
- **`TimeZone.UTC.id` is now `"UTC"`, not `"Z"`.** `LocaleFormats.kt` passes `zone.id` to
  `java.util.TimeZone.getTimeZone`, which maps both to a zero offset. No visible change.
- **Desugaring isn't touched by this move.** 0.8.0's JVM artifact, the one Android consumes, still
  imports `java.time` (`core/jvm/src/LocalDate.kt`). Android below API 26 still needs desugaring
  whatever datetime version is used. That stays the minSdk decision. `kotlin.time.Instant` itself is
  pure Kotlin.
- kotlinx-serialization isn't used to serialize `Instant` (the runner's JSON is hand-written), so the
  README's "update serialization to ≥ 1.9.0" note doesn't apply. It moves anyway (§1).

---

## 4. What has to travel together

```
Kotlin 2.4.21 ──┬── kotlinx-datetime 0.8.0   (stable kotlin.time.Instant needs ≥ 2.3; one commit for all imports)
                ├── snakeyaml-engine-kmp 5.0.0 (2.4 metadata needs ≥ 2.3; add the TAB conformance case)
                ├── kotlinx-serialization 1.12.0 (built on 2.4.10)
                ├── billing-ktx 9.1.0 (2.3 metadata needs ≥ 2.2)
                └── Compose compiler plugin (same version, automatic)

AGP 9.3.x ── needs KGP ≥ 2.2.10 (built-in Kotlin) and Gradle ≥ 9.5.0
         └── Compose BOM 2026.09.00 ── needs AGP ≥ 9.1 (AAR) / 9.2 (release notes) and compileSdk 37
                                    └── activity-compose 1.13.0; navigation-compose deleted, not moved
```

- **Kotlin can move before AGP.** KGP 2.4.21 is tested with AGP from 8.5.2, so the current AGP 8.12
  and Gradle 9.3.1 sit inside its range. The 2.1 pin was only the RN AAR's. So one possible order is:
  1. Kotlin, together with everything hanging off it: datetime, snakeyaml, serialization, ktx.
  2. AGP + Gradle + the built-in Kotlin migration.
  3. The BOM + compileSdk 37 + the material3 look.

  Each step is green and revertible on its own, which is the map's rule.
- **AGP can't move without the built-in Kotlin edits** (§3.1) and the `resValues` flag, if the
  `app.json` `resValue`s are still there.
- **The Compose BOM can't move before AGP ≥ 9.2 and `compileSdk` 37.** And `compileSdk` 37 would make
  `targetSdk` 37 (the separate targetSdk ticket) want Robolectric 4.17, because `:app`'s tests run at
  `targetSdk` (`app/build.gradle.kts`, the Robolectric/JDK 21 comment).
- **detekt and ktlint move with nothing.** Keep detekt 1.23.8 and its 2.0.21 pin. Whenever detekt
  2.0 goes stable, its bump and the four `detekt.yml` edits in §3.4 are one commit.
- **reorderable doesn't move.**
- **Before or after removal?** Nothing above *requires* the AAR to be gone. It's a binary AAR, so the
  shell's Kotlin doesn't recompile it, and the newer stdlib wins resolution. But AGP 9 on the
  brownfield build adds unknowns that removal deletes: `pickFirsts` for RN's `.so`s,
  `ndkVersion = rnNdkVersion`, `mavenLocal()` Expo modules, and three `app.json` `resValue`s that
  would need `resValues = true`. Doing AGP and the BOM **after** removal makes those hand-offs
  smaller. Kotlin and its dependents are independent of the AAR and could go either side of it.

## 5. Open items no document settled (each is "the first build" or "the device")

1. KGP actually resolves to 2.4.21 under AGP 9.3 with the root `apply false` alias (§3.1).
2. Whether `.kt` files under `src/main/java` still compile by default with built-in Kotlin (§3.1).
3. detekt 1.23.8's plain `detekt` task under AGP 9 / built-in Kotlin (§3.4). It's the single biggest
   risk to "stay on 1.23.8".
4. Kotlin 2.2–2.4 warnings under `allWarningsAsErrors`, and lint's view of the param+property
   annotation target (§3.2).
5. `android.experimental.self-instrumenting` under AGP 9 (`:benchmark`).
6. The material3 1.4.0 downgrade on a device: TextButton padding in every dialog, the nav bar, and
   AlertDialog. Or wait for material3 1.5.0 stable, which was at rc01 on 2026-11-04 (§3.3).
7. A TAB case in `conformance/` for snakeyaml 5's issue-99 rule, checked against js-yaml (§3.6).
8. Reorderable drag and the sheet motion on foundation/animation 1.12 (§3.3, §3.8).
