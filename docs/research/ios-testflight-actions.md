# Research: TestFlight uploads from GitHub Actions

> **Throwaway research note** for #400 (map #397, `docs/native-migration-plan.md` M8). It lives only
> on the `research/ios-testflight-actions` branch and is never merged. It records facts, each with its
> source; the hand-off tickets make the decisions. Gathered 2026-11-13 against `master` at `ffdb231`.
> Nothing was built or uploaded: there is no iOS project yet, and every "first run checks" item below
> is still open.

**How facts were read.** Runner contents come from the `actions/runner-images` readmes and GitHub's
docs and changelog. Apple tool behaviour comes from the help text of the tools themselves, run on this
Mac's Xcode 27.2 (build 27B52): `xcodebuild -help` and `xcrun altool --help`. Those are the primary
source for the exact flags. Apple policy comes from developer.apple.com, read through its JSON
documentation endpoints where the HTML is script-rendered. Kotlin behaviour comes from kotlinlang.org
and from the Kotlin Gradle plugin's source at the release tag. Forum threads are marked as such
wherever they are the only source.

---

## 1. The answer in brief

- **Runner:** `runs-on: xcode-27`. Xcode 27 isn't on any `macos-*` image. GitHub ships it as its
  own image, generally available since 2026-10-15, on macOS 27 arm64. It is a **standard** runner,
  free and unlimited on a public repo. `macos-latest` is still macOS 26 with Xcode 26.6.
- **Kotlin vs Xcode:** M7's Kotlin 2.4.21 was tested up to **Xcode 26.4**. On Xcode 27 the Kotlin
  Gradle plugin prints a warning and carries on. Kotlin 2.5.0, at RC since 2026-11-11, is tested up
  to Xcode 27.0. Apple's floor for uploads today is Xcode 26.
- **Build:** use the Kotlin Gradle plugin's direct integration. Xcode's Run Script phase calls
  `./gradlew :<module>:embedAndSignAppleFrameworkForXcode`, then the job runs
  `xcodebuild archive`, then `xcodebuild -exportArchive` with `method = app-store-connect`. Set
  `manageAppVersionAndBuildNumber = NO`, or Xcode rewrites the build number.
- **Upload:** `xcrun altool --upload-package <ipa> --api-key … --api-issuer … --p8-file-path …`.
  `--upload-app` has been deprecated since 2022. `notarytool` doesn't apply, because it is for
  Developer ID software outside the store.
- **Signing (recommended):** manual signing. Store the Apple Distribution `.p12` and the App Store
  profile as secrets and import them into a temporary keychain, following GitHub's own recipe. Use
  a separate, non-Admin App Store Connect API key for the upload only. The alternative is automatic
  "cloud" signing with an API key. It needs fewer secrets, but in practice it needs an **Admin**
  team key, and a team key reaches every app on the account. §3 has the comparison.
- **Build numbers:** keep `CURRENT_PROJECT_VERSION` (→ `CFBundleVersion`) in an iOS version file
  that `/bump` moves. Check `MARKETING_VERSION` against `gradle.properties`' `kettle.versionName`
  in the job. Upload only on a manual dispatch. A merge build reuses the last build number, and App
  Store Connect identifies each build by its build string.
- **Phoning home:** set `ITSAppUsesNonExemptEncryption = NO`. Ship a `PrivacyInfo.xcprivacy` that
  declares Compose Multiplatform's `stat`/`fstat`/`mach_absolute_time` use. Leave Gradle build scans
  off, which is the default. Nothing on this path adds an SDK to the app.

---

## 2. Runner image

| Label | OS | Xcode (default) | Arch, size | Free on a public repo |
|---|---|---|---|---|
| `xcode-27` | macOS 27.0.1 | 27.0, **27.1 (default)**, 27.2 | arm64, 3-core M1, 7 GB RAM, 14 GB SSD | yes, standard runner |
| `macos-26` = `macos-latest` | macOS 26.7.2 | 26.0.1 … 26.4.1, 26.5, **26.6 (default)** | same | yes |
| `macos-15` | macOS 15 | Xcode 16.x | same | yes |
| `xcode-27-xlarge`, `macos-*-xlarge`, `-large` | — | — | larger runners | **billed** even on public repos |

Sources:
- [runner-images README](https://github.com/actions/runner-images) for the labels.
- [xcode-27-arm64 readme](https://github.com/actions/runner-images/blob/main/images/macos/xcode-27-arm64-Readme.md)
  and [macos-26-arm64 readme](https://github.com/actions/runner-images/blob/main/images/macos/macos-26-arm64-Readme.md),
  image versions 20261104 and 20261102.
- [GitHub-hosted runners reference](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)
  lists `xcode-27` in the arm64 standard row and says standard runners are "free and unlimited on
  public repositories".
- [Billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions): "Larger
  runners are billed for public repositories".
- [Changelog, 2026-10-15](https://github.blog/changelog/2026-10-15-xcode-27-runner-image-generally-available)
  announced GA and said "each image is tied to one major Xcode version, not to a specific macOS
  version". [Issue #14404](https://github.com/actions/runner-images/issues/14404) is the preview
  history.

What else the images carry: both have JDK 11/17/21/25, with **21 the default**, exported as
`JAVA_HOME_<n>_arm64`, plus Gradle 9.8.2, Kotlin 2.4.21, fastlane 2.241.2 and CocoaPods 1.17.0.
Kettle needs none of the last four. The Gradle wrapper and the Kotlin plugin bring their own.

Limits worth planning around:
- **5 concurrent macOS jobs** on the Free plan
  ([limits](https://docs.github.com/en/actions/reference/limits)). One `concurrency:` group, as
  `android.yml` has, keeps that a non-issue.
- "During periods of high demand, jobs that run on macOS runners may take longer to start" (runner
  reference). 6 h job limit.
- **7 GB of RAM.** `native/android/gradle.properties` gives Gradle `-Xmx4g`. Kotlin/Native's link
  step runs inside that daemon unless it is configured otherwise, and Xcode's compile runs beside
  it. JetBrains suggests `-Xmx3g` as the starting heap
  ([compilation time](https://kotlinlang.org/docs/native-improving-compilation-time.html)). Expect
  to tune this on the first real run.
- **Pin the Xcode, not just the image.** The `xcode-27` image moves its default within 27.x: today
  27.1, while this Mac has 27.2. Set `DEVELOPER_DIR=/Applications/Xcode_27.2.app/Contents/Developer`,
  using the path the readme lists, so local and CI builds match. This is the same reason
  `android.yml` pins its JDK rather than taking the image default.

**Ruled out for this repo:** the `-xlarge` labels (billed), the Intel `macos-26-intel` (slower, and
Kotlin/Native would build x86 host tools), and a self-hosted runner on this Mac. GitHub's own doc
warns that a self-hosted runner keeps the keychain and profile after the job unless a cleanup step
deletes them ([signing doc](https://docs.github.com/en/actions/how-tos/deploy/deploy-to-third-party-platforms/sign-xcode-applications)).

### Kotlin's tested Xcode

The Kotlin Gradle plugin compares the selected Xcode to a `maxTested` constant. Above it, the plugin
reports `XcodeVersionTooHighWarning`, which is a warning and doesn't fail the build. It can be
silenced with `kotlin.apple.xcodeCompatibility.nowarn=true`.

| Kotlin | `XcodeVersion.maxTested` | Source |
|---|---|---|
| 2.4.21 (M7's pin, latest stable, 2026-10-08) | **26.4** | [`Xcode.kt@v2.4.21`](https://github.com/JetBrains/kotlin/blob/v2.4.21/native/utils/src/org/jetbrains/kotlin/konan/target/Xcode.kt), [`XcodeVersionService.kt@v2.4.21`](https://github.com/JetBrains/kotlin/blob/v2.4.21/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/mpp/apple/XcodeVersionService.kt) |
| 2.5.0-RC (2026-11-11) | **27.0** | [`Xcode.kt@v2.5.0-RC`](https://github.com/JetBrains/kotlin/blob/v2.5.0-RC/native/utils/src/org/jetbrains/kotlin/konan/target/Xcode.kt) |

The [KMP compatibility guide](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html)
says the same for 2.4.20–2.4.21: Xcode 26.4. Apple's floor is lower:
["Apps uploaded to App Store Connect must be built with Xcode 26 or later"](https://developer.apple.com/news/upcoming-requirements/),
since 2026-04-28. No Xcode 27 requirement has been announced as of this note. So there are two
consistent setups:

1. **`xcode-27` with Xcode 27.x and the warning**, until Kotlin 2.5 is stable *and* stable Compose
   Multiplatform supports it. M8 commits M7 to "Kotlin … no newer than stable CMP supports". This
   matches this Mac.
2. **`macos-26` with `DEVELOPER_DIR` on Xcode 26.4.1**, which is inside Kotlin 2.4.21's tested range
   and Apple's floor. It doesn't match this Mac's Xcode 27.2, so a local/CI difference would be
   possible.

Option 1 is the one the map assumes. The warning is the cost.

---

## 3. Signing: manual import vs automatic (cloud) signing

### A. Manual: certificate and profile as secrets

GitHub's documented recipe
([Installing an Apple certificate on macOS runners](https://docs.github.com/en/actions/how-tos/deploy/deploy-to-third-party-platforms/sign-xcode-applications))
uses four secrets:
- `BUILD_CERTIFICATE_BASE64`, the `.p12`;
- `P12_PASSWORD`;
- `BUILD_PROVISION_PROFILE_BASE64`, the `.mobileprovision`;
- `KEYCHAIN_PASSWORD`, any random string.

One step decodes them into `$RUNNER_TEMP`, then runs `security create-keychain`,
`set-keychain-settings -lut 21600`, `unlock-keychain`, `import … -A -t cert -f pkcs12`,
`set-key-partition-list -S apple-tool:,apple:` and `list-keychain -d user -s`, and copies the profile
into `~/Library/MobileDevice/Provisioning Profiles`. On a GitHub-hosted runner the VM is destroyed
after the job. A cleanup step is still cheap and keeps the habit `android.yml`'s "Shred the
keystore" set.

The archive is built with `CODE_SIGN_STYLE = Manual`, the profile and `Apple Distribution` in the
Release configuration, and the export uses:
- `signingStyle = manual`;
- `signingCertificate = "Apple Distribution"`, an automatic selector;
- `provisioningProfiles = { "com.casco.kettle": "<profile name or UUID>" }`.

Source: `xcodebuild -help`, ExportOptions keys.

The upload then needs an App Store Connect API key. That's three more secrets: key ID, issuer ID and
the `.p8`. Its role can be below Admin. The App Store Connect role descriptions give App Manager
"app development and delivery" and Developer "development and delivery of an app"
([role permissions](https://developer.apple.com/help/app-store-connect/reference/account-management/role-permissions)).
Confirm in the rendered [roles table](https://developer.apple.com/help/account/access/roles) that
the chosen role can **upload builds** before generating the key. The text extraction of that table
loses its checkmarks.

What breaks it, and how often:
- **The certificate expires.** Its expiry is printed in Certificates, Identifiers & Profiles. When
  it expires or is revoked, "you'll no longer be able to upload new apps or updates signed with the
  expired or revoked certificate". Revoking also risks "Invalid Binary" on uploaded-but-unsubmitted
  builds. Apps already on the store are unaffected. Only one Apple Distribution certificate type is
  allowed per team, and only Account Holder or Admin can create it
  ([certificates overview](https://developer.apple.com/help/account/certificates/certificates-overview)).
  A renewal is a yearly calendar event, and a workflow step can fail early by reading the `.p12`'s
  `notAfter` date.
- **The profile goes stale.** An App Store profile "contains a single distribution certificate"
  ([create a profile](https://developer.apple.com/help/account/provisioning-profiles/create-an-app-store-provisioning-profile/)),
  so it has to be regenerated with every new certificate. It also has to be regenerated whenever a
  capability changes on the App ID. Kettle's known capability is the Time Sensitive Notifications
  entitlement in `docs/ios-plan.md`. Each regeneration means re-uploading
  `BUILD_PROVISION_PROFILE_BASE64`.
- **Seven secrets** in total, against `android.yml`'s four.

### B. Automatic: an API key and `-allowProvisioningUpdates`

`xcodebuild -help` (Xcode 27.2) for `-allowProvisioningUpdates`: "For automatically signed targets,
xcodebuild will create and update profiles, app IDs, and certificates … Requires … an App Store
Connect authentication key to be specified via the `-authenticationKeyPath`,
`-authenticationKeyID`, and `-authenticationKeyIssuerID` parameters". For ExportOptions'
`signingStyle = automatic`, "Xcode will create provisioning profiles and managed cloud signing
certificates as necessary". The distribution certificate's private key then never exists on the
runner: Xcode "will cloud sign any apps … for distribution"
([cloud-managed certificates](https://developer.apple.com/help/account/certificates/cloud-managed-certificates)).

So the secrets drop to three: the API key's ID, issuer and `.p8`. A profile can't go stale, because
it is regenerated on demand after a capability change.

What breaks it:
- **The key's role.** Exporting with cloud signing fails with "Cloud signing permission error … You
  haven't been given access to cloud-managed distribution certificates" unless the key has the
  **Admin** role. There is no per-key toggle for that access. This is from a
  [developer-forums thread](https://developer.apple.com/forums/thread/698117), 2022, with no Apple
  reply. Later threads report the same. Apple's docs don't state the API-key rule either way, so
  treat it as likely and test it once.
- **The key's reach.** Only *team* keys can use provisioning: "Individual keys aren't able to use
  Provisioning endpoints". "Team API keys can access all apps, regardless of their role", and "keys
  with the Admin role … can … create new users and delete users". The private key "is available
  for download a single time"
  ([creating API keys](https://developer.apple.com/documentation/appstoreconnectapi/creating-api-keys-for-app-store-connect-api)).
  So a leaked Admin team key controls the whole developer account, not just Kettle.
- **The archive step.** Cloud signing covers *distribution*. An automatically signed `archive` on a
  clean runner still signs with an Apple Development identity, which `-allowProvisioningUpdates`
  would have to create or fetch on every ephemeral VM. **Not verified here.** It is the first thing a
  spike of option B would have to check, together with whether certificates pile up in the portal.
- Errors from the portal surface mid-export. The forum threads report opaque codes such as 7495 and
  409/7460 that were fixed server-side, which is a dependency on Apple's backend during every build.

### Recommendation: A, manual signing, with a non-Admin upload key

For one maintainer on a public repo, A is less fragile in the way that matters:
- It fails **predictably** and early: a missing secret fails at import, an expired certificate fails
  at a date check, and a stale profile fails at archive.
- It is the documented GitHub recipe.
- It mirrors `android.yml` step for step: restore secrets → build → shred → check.
- The credential that sits in the repo's secrets can sign Kettle and upload builds, not administer
  the account.

B's real saving is the yearly renewal and the profile regeneration after a capability change. Both
are rare, scheduled and recoverable. In exchange, B puts an Admin team key in a public repo's
secrets and adds an unverified archive-signing step.

The fork-safety argument from `docs/building-android.md` carries over unchanged. Secrets never reach
a workflow triggered from a fork, so the workflow must never get a `pull_request` trigger.

The `ios-plan.md` claim that "a certificate is revocable and replaceable" holds, with the one caveat
above: revoking it can invalidate builds that are uploaded but not yet submitted.

---

## 4. The build: Gradle inside Xcode, then archive, export and upload

**Integration.** JetBrains' guidance for a monorepo with no CocoaPods dependencies is **direct
integration**
([iOS integration overview](https://kotlinlang.org/docs/multiplatform/multiplatform-ios-integration-overview.html),
[direct integration](https://kotlinlang.org/docs/multiplatform/multiplatform-direct-integration.html)).
A Run Script phase before Compile Sources runs:

```sh
if [ "YES" = "$OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then exit 0; fi
cd "$SRCROOT/<path to native/android>"
./gradlew :<module>:embedAndSignAppleFrameworkForXcode
```

The phase needs the **User Script Sandboxing** setting off (`ENABLE_USER_SCRIPT_SANDBOXING = NO`).
The task registers only when the iOS target declares `binaries.framework`. It reads Xcode's
environment directly:
- `CONFIGURATION`, `PLATFORM_NAME`, `ARCHS`, `BUILT_PRODUCTS_DIR`, `TARGET_BUILD_DIR`;
- `EXPANDED_CODE_SIGN_IDENTITY`, used to sign the embedded framework;
- `ENABLE_USER_SCRIPT_SANDBOXING`, `ACTION`, `DWARF_DSYM_FOLDER_PATH`.

Source: [`XcodeEnvironment.kt`](https://github.com/JetBrains/kotlin/blob/master/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/mpp/apple/XcodeEnvironment.kt).

So **`xcodebuild archive` drives Gradle itself**. CI needs no separate framework step, and the same
project builds from Xcode on this Mac.

An **XCFramework** (`-create-xcframework`, or KMP's `assembleXCFramework`) is the "remote"
integration, meant for shipping shared code as a versioned third-party dependency. It adds a publish
step and nothing a single repo needs. **Reject it** unless the direct script phase proves unworkable
under `archive`.

Three consequences for the job:
- **Signing secrets never pass through Gradle.** Xcode signs and Gradle only reads
  `EXPANDED_CODE_SIGN_IDENTITY`, the name of the identity, not a password. `android.yml`'s reason
  for `--no-configuration-cache` (the passwords would be written into the cached configuration)
  doesn't apply. The iOS job can keep `gradle.properties`' configuration cache.
- **`org.gradle.java.installations.fromEnv=JAVA_HOME_21_X64`** in `gradle.properties` names the
  Linux variable. On the arm64 images it is `JAVA_HOME_21_arm64` (readmes above). This only matters
  if the iOS build configures something that resolves `:app`'s JDK 21 test toolchain. Check it on the
  first run.
- **Gradle's own network use.** Build scans are opt-in (`build-scan-publish`), and so are the
  dependency graph and Develocity injection. `setup-gradle`'s job summary is local
  ([setup-gradle docs](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md)). None of
  this reaches the app either way.

**Archive and export.** The ExportOptions keys below are from `xcodebuild -help` on Xcode 27.2:

| Key | Value | Why |
|---|---|---|
| `method` | `app-store-connect` | `app-store` is the deprecated spelling |
| `destination` | `export` | upload as a separate step (below), so a merge build can export without uploading |
| `manageAppVersionAndBuildNumber` | **`NO`** | "Should Xcode manage the app's build number when uploading … Defaults to YES." Left on, it overrides the repo's build number (§5) |
| `signingStyle` / `signingCertificate` / `provisioningProfiles` / `teamID` | manual, as §3 A | |
| `uploadSymbols` | `YES` (default) | dSYMs go to Apple with the build, which symbolicates TestFlight crash reports. Developer → Apple, not app → anyone |
| `testFlightInternalTestingOnly` | `YES` while M8 is internal-only | "this build cannot be distributed via external TestFlight or the App Store". It fits a build for the owner's iPad mini and stops accidental external distribution |

**Upload.** The [upload-builds help](https://developer.apple.com/help/app-store-connect/manage-builds/upload-builds)
lists Xcode, altool, Transporter, the App Store Connect API (`POST /v1/builduploads`) and Xcode Cloud,
with no deprecation of any method. It also says "Upload for all target types is supported for
Transporter and altool".
[altool's own guide](https://help.apple.com/asc/appsaltool/en.lproj/static.html) says "The
`--upload-app` command is deprecated. Use the `--upload-package` command instead", and dates that to
2022-04-01. Xcode 27.2's `altool --help` lists:

```
altool --upload-package <file> Authentication [Options]     # --wait: wait for processing
--api-key <id> --api-issuer <id> [--p8-file-path <path> | --auth-string <key>]
```

The key file is otherwise searched for as `AuthKey_<id>.p8` in `./private_keys`, `~/private_keys`,
`~/.private_keys`, `~/.appstoreconnect/private_keys` or `$API_PRIVATE_KEYS_DIR`. Prefer
`--p8-file-path` to a file in `$RUNNER_TEMP`. `--auth-string` would put the key on a command line.

`notarytool` is for Developer ID software notarised outside the store. Apple stopped accepting
`altool` *notarisation* on 2023-11-01
([upcoming requirements](https://developer.apple.com/news/upcoming-requirements/)), but that is the
only altool removal, and it doesn't touch App Store uploads.

---

## 5. Build numbers alongside `gradle.properties`

Facts:
- `CFBundleVersion` is "one to three period-separated integers … only numeric characters (0-9) and
  periods … required by the App Store"
  ([CFBundleVersion](https://developer.apple.com/documentation/bundleresources/information-property-list/cfbundleversion)).
- App Store Connect identifies a build by its bundle ID, its version and its "build string … used to
  uniquely identify the build throughout the system" (upload-builds help).
- Uploading a build string that already exists, or one lower than an earlier upload of the same
  version, is refused. That rule appears in the developer forums, for example
  [threads/693838](https://developer.apple.com/forums/thread/693838) and
  [threads/759988](https://developer.apple.com/forums/thread/759988), not in Apple's docs.

`/bump`'s rule satisfies it under every reading: one integer, always +1, never reset (from
`.claude/commands/bump.md`).

Shape, matching `/bump`'s own suggestion of `native/ios/Version.xcconfig`:

```
// native/ios/Version.xcconfig: CURRENT_PROJECT_VERSION is iOS's build number, which /bump moves.
MARKETING_VERSION = 0.16.0
CURRENT_PROJECT_VERSION = 1
```

`Info.plist` reads `$(MARKETING_VERSION)` and `$(CURRENT_PROJECT_VERSION)`, which is Xcode's default
since `GENERATE_INFOPLIST_FILE`. `/bump` finds a second version file, so it asks "Android, iOS, or
both?", which is its existing behaviour.

The shared `versionName` then exists twice: as `kettle.versionName` in `gradle.properties` and as
`MARKETING_VERSION` in the xcconfig. Two ways to keep them honest:
1. **Check, don't derive.** A job step fails unless `MARKETING_VERSION` equals `kettle.versionName`.
   This keeps local Xcode builds self-contained and fits the repo's "check rather than trust" habit,
   like `android.yml`'s certificate pin. Recommended.
2. Override on the command line: `xcodebuild … MARKETING_VERSION=$(sed -n 's/^kettle.versionName=//p' …)`.
   One source, but a local Xcode build then reads a different value than CI does.

**Upload only on `workflow_dispatch`.** `android.yml` builds on every merge because a merge build is
just an artifact. An iOS merge build would carry the *same* `CFBundleVersion` until the next `/bump`,
so a second upload would be refused. Mirror `android.yml`'s triggers:
- `push` to `master` archives and exports, as a smoke test that keeps the cache warm.
- `workflow_dispatch` with an `upload: true` input also uploads.

`docs/ios-plan.md` already says to start on dispatch only until signing exists.

Choosing `github.run_number` as the build number would avoid the reuse, but it breaks `/bump`'s
rule that each platform's number moves only when that platform ships. Not recommended.

The changelog heading (`## <version> — versionCode <n>, <date>`) names only Android's build number.
`ios-plan.md` Phase 6 already leaves "how a heading names iOS's" to M8. That is still open.

---

## 6. Anything that would phone home

- **Export compliance.** Set `ITSAppUsesNonExemptEncryption` to `NO`. Without the key, "App Store
  Connect walks you through an export compliance questionnaire every time you upload"
  ([ITSAppUsesNonExemptEncryption](https://developer.apple.com/documentation/bundleresources/information-property-list/itsappusesnonexemptencryption)).
  `NO` covers an app that "uses no encryption, or only uses encryption that's exempt", and Kettle
  makes no network calls. This is `ios-plan.md`'s `usesNonExemptEncryption: false`, which needs
  carrying into the new Info.plist.
- **Privacy manifest.** "Using Compose Multiplatform may result in `fstat`, `stat` and
  `mach_absolute_time` usages in your binary … Apple can still flag them". The declared reasons are
  `0A2A.1` for `stat`/`fstat` and `35F9.1` for `mach_absolute_time`. A missing declaration produces
  an ITMS-91053 email or rejection on upload
  ([KMP privacy manifest](https://kotlinlang.org/docs/multiplatform/multiplatform-privacy-manifest.html)).
  Apple requires approved reasons for listed APIs on every upload since 2024-05-01 (upcoming
  requirements). This isn't phoning home, but the upload fails without it, so it belongs in the
  first TestFlight PR.
- **Crash reporting.** Nothing on this path adds an SDK. TestFlight's crash reports and feedback are
  Apple's, collected by the OS and TestFlight, not by Kettle's code. `uploadSymbols` only sends dSYMs
  with the build. What App Privacy says about that is out of this map's scope; the map puts privacy
  labels past TestFlight.
- **Build-time network**, for completeness, none of it in the app:
  - Kotlin/Native downloads its toolchain into `~/.konan` on a cold run;
  - Gradle resolves dependencies;
  - `setup-gradle`'s "enhanced" cache provider is a proprietary library (`basic` is the MIT
    `@actions/cache` one).

---

## 7. Caching

- **Gradle:** `gradle/actions/setup-gradle@v4`, as in `android.yml`. It caches wrapper
  distributions and `~/.gradle/caches`, including `build-cache-1`, and writes the cache only from
  the default branch ([setup-gradle docs](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md)).
  **It doesn't cover `~/.konan`.** Extra include paths are relative to Gradle User Home.
- **Kotlin/Native:** "configure your environment to preserve `~/.konan` between builds", or
  relocate it with the `konan.data.dir` Gradle property
  ([compilation time](https://kotlinlang.org/docs/native-improving-compilation-time.html)). Use
  `actions/cache@v4` on `~/.konan`, keyed on `runner.os`, the Xcode version and
  `hashFiles('native/android/gradle/libs.versions.toml')`, where the Kotlin version lives. A fixed
  key per Kotlin version is right here, unlike the rolling key `ci.yml` uses for jest: the toolchain
  for one Kotlin version doesn't change.
- **DerivedData:** not worth caching at first. The Xcode side is a thin shell, and Kotlin dominates.
  Note that `ios-plan.md`'s "cache `Pods/` and DerivedData keyed on `pnpm-lock.yaml`" was written
  for the RN app. There are no Pods and no pnpm in the iOS build now.

---

## 8. Step-by-step job outline

`.github/workflows/ios.yml`. Each step lists the section it comes from. Secret names are
placeholders, and no values belong anywhere in the repo.

```yaml
on:
  push: { branches: [master], paths-ignore: [same list as android.yml] }   # add once signing exists (ios-plan)
  workflow_dispatch:
    inputs: { upload: { type: boolean, default: false } }                   # §5
  # never pull_request: secrets (§3, building-android.md)
concurrency: { group: ios-build, cancel-in-progress: false }                # §2, 5-job macOS cap
jobs:
  build:
    runs-on: xcode-27                                                       # §2
    timeout-minutes: 60                                                     # first-run guess; tighten after
    env:
      DEVELOPER_DIR: /Applications/Xcode_27.2.app/Contents/Developer       # §2 pin
```

1. **`actions/checkout`.**
2. **`actions/setup-java`**, JDK 21 (and 17 if the shared build still compiles for it), pinned as in
   `android.yml`. §2, §4.
3. **`gradle/actions/setup-gradle@v4`.** §7.
4. **`actions/cache@v4` on `~/.konan`**, keyed on OS, Xcode and `libs.versions.toml`. §7.
5. **Check versions.** Fail unless `MARKETING_VERSION` equals `kettle.versionName`. Print
   `CURRENT_PROJECT_VERSION`. §5.
6. **Install signing**, using GitHub's recipe and these secrets:
   - `IOS_DIST_CERT_P12_BASE64`, `IOS_DIST_CERT_P12_PASSWORD`;
   - `IOS_APPSTORE_PROFILE_BASE64`, `IOS_KEYCHAIN_PASSWORD`.

   Each is checked non-empty first, with an `::error::` naming the setup doc, as `android.yml` does.
   Fail if the certificate's `notAfter` is under 30 days away. §3 A.
7. **`xcodebuild archive`**:

   ```sh
   xcodebuild archive -project native/ios/Kettle.xcodeproj -scheme Kettle -configuration Release \
     -destination 'generic/platform=iOS' -archivePath "$RUNNER_TEMP/Kettle.xcarchive"
   ```

   The Run Script phase runs `embedAndSignAppleFrameworkForXcode` inside it. §4.
8. **`xcodebuild -exportArchive`**:

   ```sh
   xcodebuild -exportArchive -archivePath … -exportPath "$RUNNER_TEMP/export" \
     -exportOptionsPlist native/ios/ExportOptions.plist
   ```

   The plist is committed and holds no secrets: `app-store-connect`, `export`,
   `manageAppVersionAndBuildNumber=NO`, manual signing and `testFlightInternalTestingOnly`. §4.
9. **Delete the keychain and profile**, `if: always()`, before any artifact upload, as `android.yml`
   shreds its keystore. §3 A.
10. **Check the output.** Read `CFBundleVersion`/`CFBundleShortVersionString` from the `.ipa`'s
    Info.plist and compare them to step 5. Confirm `ITSAppUsesNonExemptEncryption` is `false`
    (§6). Run `codesign -dv` and check the signing authority is "Apple Distribution: …" and the
    Team ID is the expected one. That is the analogue of `UPLOAD_CERT_SHA256`, and a Team ID is
    public.
11. **`actions/upload-artifact@v4`**: the `.ipa` and dSYMs, `kettle-ios-run${{ github.run_number }}`,
    14-day retention, as `android.yml`.
12. **Upload**, only `if: inputs.upload`. Write the `.p8` from `ASC_API_KEY_P8` into `$RUNNER_TEMP`,
    then:

    ```sh
    xcrun altool --upload-package "$RUNNER_TEMP/export/Kettle.ipa" \
      --api-key "$ASC_API_KEY_ID" --api-issuer "$ASC_API_ISSUER_ID" \
      --p8-file-path "$RUNNER_TEMP/AuthKey.p8" --wait
    ```

    Delete the `.p8` with `if: always()`. Use a non-Admin team key whose role can upload builds. §3,
    §4.

One-time setup, for a `docs/building-ios.md` that `ios-plan.md` Phase 6 already names. The account
holder does these by hand:
- Register the App ID `com.casco.kettle` with Time Sensitive Notifications.
- Create the App Store Connect app record.
- Create the Apple Distribution certificate and export it as `.p12`.
- Create the App Store profile.
- Generate a team API key at the least role that uploads.
- Set the seven secrets with `gh secret set`, as in `building-android.md`.

---

## 9. What this contradicts or adds to the map and the plans

- **"Which `macos-*` image carries Xcode 27"**: none does. It is the `xcode-27` label, a separate
  image since GitHub moved to one image per Xcode major. `macos-latest` is macOS 26 with Xcode 26.6.
- **Kotlin 2.4.21 (M7's pin) was tested up to Xcode 26.4.** Xcode 27, on this Mac and on `xcode-27`,
  gets a build warning, not a failure, until Kotlin 2.5 (RC on 2026-11-11), and that waits on stable
  CMP. It doesn't contradict "Kotlin no newer than stable CMP supports", but the map should know
  that the first iOS build runs outside Kotlin's tested Xcode range, or else pins Xcode 26.4.1 on
  `macos-26`.
- **`ios-plan.md`'s caching advice is stale.** "Cache `Pods/` and DerivedData keyed on
  `pnpm-lock.yaml`" was written for RN. The real caches are Gradle and `~/.konan`.
- **The upload can't follow every merge.** Mirroring `android.yml` holds for the *build*. An upload
  on every merge would reuse `CFBundleVersion` and be refused, so upload is dispatch-only. This
  sharpens the map's "uploaded by a GitHub Actions macOS job, mirroring `android.yml`" rather than
  contradicting it.
- **`manageAppVersionAndBuildNumber` defaults to YES.** Left alone, Xcode's export would override
  `/bump`'s per-platform build number. It must be `NO` in the committed ExportOptions.
- **Signing:** `ios-plan.md` already says "certificate and provisioning profile, as repository
  secrets". This note agrees, and adds the reasons against cloud signing: an Admin team key, and an
  unverified archive identity.
- **`gradle.properties`' `JAVA_HOME_21_X64`** doesn't exist on the arm64 macOS images, where the
  variable is `JAVA_HOME_21_arm64`. Harmless unless the iOS build resolves `:app`'s test toolchain.
- **A 7 GB runner against `-Xmx4g`** may need tuning for Kotlin/Native linking.

## 10. Open, for the first real run

- Does `xcodebuild archive` with the direct-integration script phase work on a clean `xcode-27` VM,
  and how long does a cold `~/.konan` take?
- Option B, only if ever reconsidered: does an automatically signed archive on an ephemeral runner
  create an Apple Development certificate per run?
- Which App Store Connect role is the least that can upload, read off the rendered roles table?
- Does the `apple-privacy-manifests` Gradle plugin place the manifest correctly under
  `embedAndSignAppleFrameworkForXcode`? JetBrains' page doesn't say
  ([KT-67603](https://youtrack.jetbrains.com/issue/KT-67603)). A hand-written app-level
  `PrivacyInfo.xcprivacy` is the fallback.
