# Research: the Data Safety re-check after removal (#339)

Throwaway research note for the M7 map (#335), not for merging. Base: `feat/native-routing` at
`34ba7a5` (#309), with the RN AAR (`com.casco.kettle:brownfield:1.0.0`) in mavenLocal.

## Answer

**M7's removal does not move the Data Safety form.** Removing the AAR deletes every network-capable
library Kettle carried but never triggered (OkHttp, Fresco's OkHttp image pipeline, RN's networking,
expo-asset/-filesystem's downloaders, expo-webview). It adds nothing that transmits, and it leaves the
merged permission set exactly as it is. What stays and *can* reach the network is Google-owned and
was already in today's Play binary:

1. **Play Billing Library 9.1.0 has its own usage logging.** It ships diagnostics to Google through
   `datatransport`/CCT, but only once a `BillingClient` exists, and Kettle builds one only on the
   Support screen. **This predates M7.** The logging has been in PBL since 6.0, and Kettle has
   carried PBL since the tip jar shipped: 9.1.0 under expo-iap by the time of #189, and natively
   from M4. The #189 note missed it: it said PBL talks to the Play Store app
   over IPC, which is incomplete. Whether this logging needs declaring is a separate, pre-existing
   question; see "The one open question" below. It's not an M7 edit.
2. **emoji2's startup font query** goes to Google Play services' font provider. That's an IPC call;
   the fetch and the logging are Play services' own (see below). Compose UI pulls emoji2 in, so it
   stays even if `appcompat` goes.

Nothing else left in the release classpath opens or schedules a connection. There's no WorkManager,
no Firebase product (only Firebase's *encoder* libraries, which do serialization), and no analytics
or crash SDK.

## Method

- `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` on the base: 243 distinct
  coordinates, which resolved.
- The same command with the AAR's five lines in `app/build.gradle.kts` commented out
  (`brownfield`, `expo.modules.brownfield`, `expo.core`, `expo.modules`, `expo.modules.splashscreen`).
  That's the post-removal classpath: 176 coordinates. The edit was reverted afterwards.
- `:app:processReleaseManifest` both ways, diffing the merged release manifests.
- Bytecode of the remaining network-adjacent AARs (`billing-9.1.0`, `transport-backend-cct-3.1.8`,
  `reorderable-android-3.1.0`) read with `javap` from the Gradle cache.
- The release build has **no `isMinifyEnabled`**, so R8 doesn't run. Every class on the classpath
  ships, referenced or not.

## What leaves with the AAR

By `group:artifact`. These are absent from the post-removal tree:

- **RN and its runtime:** `com.facebook.react:react-android` 0.86.3,
  `com.facebook.hermes:hermes-android`, `fbjni`, `soloader` (×3), `yoga:proguard-annotations`,
  `infer-annotation`.
- **Network-capable code, now gone:** `com.squareup.okhttp3:okhttp` 4.9.2 and
  `okhttp-urlconnection`, the whole Fresco image pipeline (18 artifacts, including
  `imagepipeline-okhttp3`), `bolts-tasks`, `expo.modules.asset` (remote asset download),
  `expo.modules.filesystem` (download/upload APIs), `expo.modules.webview`, and `expo.modules.logbox`.
  Kettle's JS draws nothing since #309, so none of it was ever invoked in release.
- **Expo modules:** `expo.core`, `expo.modules` (core), `brownfield`, `constants`, `jsonutils`,
  `manifests`, `font`, `keepawake`, `localization`, `sharing`, `splashscreen`, `systemui`, and
  `pika-api`.
- **Their utilities:** `gson`, `commons-codec`, `commons-io`, `zstd-kmp*` (×4), `org.brotli:dec`,
  `kotlin-reflect`, `jsr305`, `error_prone_annotations`, `material` (Views), `cardview`,
  `constraintlayout`, `coordinatorlayout`, `recyclerview`, `swiperefreshlayout`, `viewpager2`,
  `tracing-ktx`.
- **`androidx.core:core-splashscreen`.** It came in through expo-splash-screen, and the map's
  splash decision adds it back explicitly.

Coming the other way, only `androidx.legacy:legacy-support-core-utils`,
`localbroadcastmanager` and `print`, all at 1.0.0, newly *named*. The rest are version shifts:
`androidx.core` drops 1.17→1.16, `appcompat` 1.7.1→1.7.0 and coroutines 1.10→1.9, because RN's pins
no longer lift them. None of them touch the network. The unpin PRs re-raise the versions anyway.

**Merged release manifest, before vs after:** the `<uses-permission>` list is **identical**. What
disappears is `expo.modules.updates.*` meta-data (`ENABLED=false`; the module itself was never on
the classpath), `org.unimodules…react-native-headless`, `com.facebook.soloader.enabled`, the
`FileSystemFileProvider` and `SharingFileProvider` providers, and the `<queries>` entry for
`android.intent.action.SEND` (expo-sharing's). That last one doesn't affect Data Safety. The removal
PR should still check that the shell's own share sheet doesn't depend on it (`createChooser` doesn't
need package visibility).

`INTERNET` and `ACCESS_NETWORK_STATE` stay after removal. `INTERNET` is declared by the shell's own
manifest *and* by `transport-backend-cct`'s, and `ACCESS_NETWORK_STATE` by `transport-backend-cct`
and `transport-runtime`. Dropping the shell's line wouldn't remove `INTERNET` from the APK.

## What stays, and which of it can reach the network

The remaining tree has the AndroidX and Compose stack, `:shared` (kotlinx-datetime,
kotlinx-serialization-json, snakeyaml-engine-kmp with okio and urlencoder-lib), navigation-compose,
Play Billing and its transitive Google artifacts, and `sh.calvin.reorderable`.

| Artifact (as resolved) | Can it open/schedule a connection? | What, when | Does Kettle trigger it? |
|---|---|---|---|
| `com.android.billingclient:billing:9.1.0` | **Yes, two ways.** (a) IPC to the Play Store app (`InAppBillingService.BIND`). (b) Its own logger: `BillingClientImpl` builds a `BillingLogger` (`zzdt`) that calls `TransportRuntime.initialize`, gets a transport for log source `PLAY_BILLING_LIBRARY` with `CCTDestination.INSTANCE`, and `send`s proto events (`zzkw`). | Per Google's PBL 6.0 release note: "additional logging, which provides insight into API usage (such as success and failure) and service connection issues. This information will be used to improve the performance of the Play Billing Library and provide better support for errors." The proto's field names are obfuscated, so the exact payload can't be read from bytecode. | **Only on the Support screen.** `PlayTipBilling` is constructed in `SupportScreen.kt` (`remember { PlayTipBilling(activity) }`); nothing else builds a `BillingClient`. The request sets no `setObfuscatedAccountId`/`ProfileId`. |
| `com.google.android.datatransport:transport-runtime` / `transport-backend-cct` 3.x | **Yes.** This is the upload path for (b). Events are persisted in a local SQLite store (`events`, `event_payloads` tables) and uploaded later by `JobInfoSchedulerService` (JobScheduler, `NETWORK_TYPE_ANY` unless flagged unmetered) or `AlarmManagerSchedulerBroadcastReceiver`. | Endpoints, decoded from `CCTDestination`'s split strings: `https://firebaselogging.googleapis.com/v0cc/log/batch?format=json_proto3` and `https://firebaselogging-pa.googleapis.com/v1/firelog/legacy/batchlog`. `CctTransportBackend` attaches client metadata: `sdk-version`, `model`, `hardware`, `device`, `product`, `os-uild`, `manufacturer`, `fingerprint`, `locale`, `country`, `mcc_mnc`, `application_build`, `net-type`, `mobile-subtype`, `tz-offset`. There's no advertising ID or Android ID among its keys. | Only through PBL. `TransportRuntime.initialize` is reached from billing's logger alone; no Kettle code references `datatransport`. Nothing is scheduled until an event is queued. |
| `com.google.firebase:firebase-encoders`, `-json`, `-proto` | No. | Serialization helpers for datatransport. They aren't a Firebase product, and there's no `google-services.json` or `FirebaseApp`. | n/a |
| `com.google.android.gms:play-services-base` 18.5.0, `-basement` 18.9.0, `-tasks` 18.2.0 | No by themselves. They contribute `GoogleApiActivity` (a resolution UI) and the `gms.version` meta-data. | Google: these SDKs "don't collect any end-user data". | Not referenced by Kettle. |
| `com.google.android.gms:play-services-location` 19.0.0 + `play-services-places-placereport` 17.0.0 | Could call into Play services' fused location provider if invoked. | Its AAR manifest declares **no permissions**, and the merged manifest has no `ACCESS_*_LOCATION`, so location calls would fail even if made. Google publishes no Data safety guidance for it (Play SDK Index: "Not provided"). | **Dead code.** PBL's POM declares it at compile scope, but nothing in `billing-9.1.0`'s 607 classes references `com/google/android/gms/location`, and neither does Kettle. It ships unreferenced because R8 is off. |
| `androidx.emoji2:emoji2` 1.4.0 (via Compose UI and appcompat) | Indirectly. `EmojiCompatInitializer` (androidx.startup) asks the system's downloadable-font provider for the emoji font after the first activity resumes. | Android docs: "On devices powered by Google Play services, the app sends this query at startup to Google Play services. The request for a font is logged by Google Play services and downloadable fonts." The app's side is an IPC font query; the download is Play services'. | Yes, every launch. This predates M7 and stays, since Compose UI depends on emoji2. It can be opted out of by removing the initializer's meta-data (`tools:node="remove"`). |
| `androidx.profileinstaller` 1.4.0 | No. | It "allows libraries and applications to … bundle ART profile information with an APK, and … install those profiles after application launch". On-device only; its receiver requires `android.permission.DUMP`. | Runs at startup via androidx.startup, but writes locally. |
| `androidx.startup`, `lifecycle-process`, `window`, the rest of AndroidX/Compose, `navigation-compose`, kotlinx | No. | | |
| `com.squareup.okio:okio` 3.16.0, `net.thauvin.erik.urlencoder`, `it.krzeminski:snakeyaml-engine-kmp` | No. Okio is an I/O library and opens no connections on its own; urlencoder is string encoding. | | Used by `:shared`'s YAML parser on local files. |
| `sh.calvin.reorderable:reorderable` 3.1.0 (#226) | **No.** Its release AAR declares no permissions, and none of its 171 classes reference `java/net`, `android/net`, OkHttp or datatransport. Its dependencies are Compose and the Kotlin stdlib. | | The editor's drag. |

**WorkManager:** not on the classpath, before or after. **Telemetry hooks:** none outside PBL's
logger above. Kettle's own code has no network call. The only `java.net` reference in `:app` and
`:shared` is `URLDecoder` in `shell/nav/Route.kt`.

## The one open question (pre-existing, outside M7)

Play's rule: "'Collect' means transmitting data from your app off a user's device. This includes user
data transmitted off device from your app by libraries and/or SDKs used in your app." Its
*Diagnostics* type covers "any technical diagnostics". PBL's logger fits that description literally.
The only relevant exemption is for payment services such as "Google Play's billing system", provided
"your app never accesses this information" and "the payment service collects this information
directly from the user". Kettle never accesses the log events. Whether Google counts PBL's own
diagnostics as part of "Google Play's billing system" isn't stated anywhere I could find:

- Google's Play services disclosure page covers only base, basement, OSS-licenses and tasks.
- Play's SDK Index has no Data safety guidance for `com.android.billingclient:billing`.

The declaration has stood alongside this logging since the tip jar shipped, so M7 neither creates
nor fixes it. It's a call the owner can make on its own: keep reading it as part of the billing
system (the status quo), declare *App info and performance → Diagnostics* as collected by the PBL
SDK, or ask Play developer support. The decision log's tip-jar entry ("Play Billing keeps the
transaction inside Google Play") would want a clause about it either way, since that sentence is now
known to be incomplete.

## Recommendation for the map

- **Data Safety form: no edit for M7.** The removal deletes network-capable code that was never
  triggered, and adds none.
- **The re-check after the unpin PRs.** The unpins move billing (and billing-ktx, if adopted), so
  re-run the same `dependencies` diff on the release candidate. Watch for a new transitive
  Google/Firebase artifact or a manifest permission. A PBL bump could change what its logger sends,
  and the release notes are the only first-party record of that.
- **Optional hardening (not M7 scope):** enabling R8 would drop the unreferenced
  `play-services-location`. Removing `EmojiCompatInitializer` would end the startup font query, at
  the cost of tofu for newer emoji on API ≤ 30.

## What I couldn't verify

- **The exact fields of PBL's log proto** (`zzkw` and its nested messages) are obfuscated protobuf
  lite. Only the release note describes what's in it.
- **Whether Google treats PBL's logging as covered by the billing-system exemption.** It's
  undocumented. The conclusion above is the policy text applied to the bytecode, not a ruling.
- **The post-removal tree comes from commenting out the AAR's dependency lines**, not from the real
  removal PR. The removal may also drop `appcompat`/`fragment-ktx` (they exist for
  `BrownfieldActivity`), which only shrinks the tree further.
- No device traffic capture was done. "Only on the Support screen" is read from the code paths, not
  observed.

## Sources

- Play Console Help, *Provide information for Google Play's Data safety section*:
  https://support.google.com/googleplay/android-developer/answer/10787469
- Play Billing Library release notes (6.0 "Added additional logging"; 9.1.0):
  https://developer.android.com/google/play/billing/release-notes
- Google Play services data disclosure (base/basement/tasks "don't collect any end-user data"):
  https://developers.google.com/android/guides/play-data-disclosure
- Play SDK Index, play-services-location (Data safety guidance "Not provided"):
  https://play.google.com/sdks/details/com-google-android-gms-play-services-location
- emoji2, "Impact of automatic EmojiCompat configuration":
  https://developer.android.com/develop/ui/views/text-and-emoji/emoji2
- ProfileInstaller release notes (library purpose):
  https://developer.android.com/jetpack/androidx/releases/profileinstaller
- datatransport source, firebase/firebase-android-sdk `transport/transport-runtime/…/scheduling/`
  (`JobInfoScheduler.java`, `SchedulerConfig.java`, `persistence/SchemaManager.java`):
  https://github.com/firebase/firebase-android-sdk/tree/main/transport
- Gradle cache bytecode via `javap`: `com.android.billingclient:billing:9.1.0` (`BillingClientImpl`,
  `zzdt`, `zzds`, POM), `transport-backend-cct:3.1.8` (`CCTDestination`, `CctTransportBackend`,
  manifest), `play-services-location:19.0.0` (manifest), `reorderable-android:3.1.0` (release AAR)
- Repo: `native/android/app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, the merged release
  manifests and `manifest-merger-release-report.txt`, `shell/support/TipBilling.kt`,
  `shell/support/SupportScreen.kt`, `docs/decisions.md` (tip-jar entry), `research/play-billing.md`
  on `origin/research/play-billing` (#189)
