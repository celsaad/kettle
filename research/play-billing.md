# Research: moving the tip jar from expo-iap to the Play Billing Library

> Research note for #189 (parent #186), written 2026-10-27 against `master` at 2694b8b. A snapshot,
> not a live doc: versions and policy dates go stale. Throwaway branch, not for merging.

The question: what does it take for the native shell (`native/android`) to run the tip jar on the
Play Billing Library (PBL) while `expo-iap` is still inside the RN AAR it hosts?

## Short answers

1. **Coexistence: no conflict, because there is only one PBL.** `expo-iap` does not bundle or shade
   PBL. It pulls `com.android.billingclient:billing:9.1.0` as an ordinary Maven dependency, so a
   shell declaration of the same coordinate is deduplicated by Gradle, which keeps the highest
   version. Declare **9.1.0**, the current release and the one `expo-iap` already resolves.
2. **SKUs and semantics: unchanged.** `tip_small`/`tip_medium`/`tip_large` are Play Console product
   IDs, not anything `expo-iap` owns. `finishTransaction({ isConsumable: true })` is a wrapper over
   PBL's `consumeAsync`, which also acknowledges the purchase. The native port calls
   `consumeAsync`/`consumePurchase` directly.
3. **An `expo-iap`-era purchase survives the update.** It lives in Play, keyed to the package and the
   account, not to the library that started it. The native client sees it through
   `queryPurchasesAsync` and can consume it. Only a client that never queries loses it, and **today's
   screen never queries.** So a pending tip that completes while Support is closed is already
   auto-refunded after three days. The port should fix that, not carry it over.
4. **The degrade:** `isIapAvailable` exists because a JS module can be missing from a binary. A native
   screen can't be missing its own dependency, so that case goes away. What remains is the runtime
   case: no connection or `BILLING_UNAVAILABLE`. That shows the existing
   `support.storeUnavailablePlay` copy, and the past-tip card still renders.
5. **Data Safety: no change.** Play's own help names "Google Play's billing system" as a payment
   service whose collection the app doesn't declare. Kettle sends nothing off the device. Dropping
   `expo-iap` also removes the dormant OpenIAP purchase-verification code that could post to
   `kit.openiap.dev`. Kettle never calls it, but the binary stops carrying it.

## 1. Can the shell's PBL coexist with expo-iap's?

### What expo-iap actually ships

- `node_modules/expo-iap/package.json`: **4.7.2**. Its `android/build.gradle` has no direct
  `BillingClient` dependency ("Use OpenIAP Google module only; avoid direct BillingClient
  dependency"). Instead it declares `io.github.hyochan.openiap:openiap-google:${googleVersionString}`.
  `node_modules/expo-iap/openiap-versions.json` pins `"google": "2.5.2"`.
- The published module in mavenLocal,
  `~/.m2/repository/expo/modules/iap/expo.modules.iap/4.7.2/expo.modules.iap-4.7.2.pom` (and its
  `.module`), declares `openiap-google` `2.5.2` at `runtime` scope.
- `openiap-google-2.5.2.pom` (Gradle cache) declares `com.android.billingclient:billing` **9.1.0**
  at `compile` scope. Its description is "OpenIAP Android library using Google Play Billing v9.1".
- **Nothing is shaded.** Neither `classes.jar` (expo.modules.iap's or openiap-google's) contains a
  `com/android/billingclient/` class, and neither AAR has a `libs/` folder. The only billing classes
  in the APK come from the one `billing` artifact.

### What Gradle resolves on the real project

This was run, not inferred, with JDK 17 and the RN AAR in mavenLocal:

```
$ ./gradlew :app:dependencyInsight --configuration releaseRuntimeClasspath --dependency com.android.billingclient
com.android.billingclient:billing:9.1.0
\--- io.github.hyochan.openiap:openiap-google:2.5.2
     \--- expo.modules.iap:expo.modules.iap:4.7.2
          \--- host.exp.exponent:expo.core:57.0.22
               +--- releaseRuntimeClasspath
               \--- com.casco.kettle:brownfield:1.0.0
```

On `releaseCompileClasspath` the same query finds nothing. PBL reaches `:app` only at runtime, so
the shell has to declare it to compile against it. It can't lean on the transitive copy.

### What the shell should declare, and the rules for skew

- `implementation("com.android.billingclient:billing-ktx:9.1.0")`, or `billing` for the plain
  artifact. That matches the version already in the APK and is the current release: the release
  notes list 9.1.0 (2026-06-18) as the newest, after 9.0.0 (2026-05-19). `billing-ktx` adds the
  coroutine extensions (`queryProductDetails`, `consumePurchase`, …) and depends on `billing` itself.
- Gradle's default conflict resolution keeps one version, the highest. The shell can't end up with
  two PBLs. The skew risk runs the other way: **if the shell declares a later version, openiap-google
  runs on a PBL it wasn't built against.** A minor bump is safe in practice, since 9.1.0 removed no
  APIs and neither did 9.0.0. A major bump that removes APIs could throw `NoSuchMethodError` inside
  `expo-iap`. While `expo-iap` is in the AAR, keep the shell's PBL inside 9.x, or check openiap's
  release that targets the new major first.
- Requirements: 9.x needs `targetSdk` ≥ 35 and AndroidX Core ≥ 1.9 for the 9.0 blocked-Play-Store
  handling. `:app` is at `targetSdk = 36`, `compileSdk = 36`, `minSdk = 24`, so it clears both.
  PBL's stated `minSdk` is 23.
- Play's deadline: since 2026-08-31, new apps and updates must use **PBL 8 or later** (extension
  available to 2026-11-01). Either path, today's 9.1.0 or the shell's, already satisfies it.

### Two BillingClients at once?

There is no conflict in practice. `ExpoIapModule` builds its `OpenIapModule` `by lazy`, and the
`BillingClient` is only created on `initConnection`, which only `useIAP` calls (from
`support.tsx`'s `TipTiers`). Once Support is native and `support.tsx` is deleted (the M4 definition
of done), `expo-iap` stays registered in the AAR but never opens a connection. Don't keep both
screens live at once: a purchase result goes to the `PurchasesUpdatedListener` of the client that
launched the flow.

Sources: `node_modules/expo-iap/android/build.gradle`, `node_modules/expo-iap/openiap-versions.json`,
the two POMs above, `native/android/app/build.gradle.kts`, `native/android/gradle/libs.versions.toml`,
https://developer.android.com/google/play/billing/release-notes,
https://developer.android.com/google/play/billing/integrate.

## 2. Do the SKUs and the consume/acknowledge semantics carry over?

**SKUs: yes, as they are.** `TIP_SKUS` in `src/domain/tip.ts` are Play Console one-time product IDs
(`tip_small`, `tip_medium`, `tip_large`). The comment there already records that they're permanent.
A native port queries the same IDs with `QueryProductDetailsParams.Product.setProductType(INAPP)`,
which is what `expo-iap`'s `type: 'in-app'` maps to. `TIP_TIERS` ordering and `toTipTierOffers`
(drop unknown SKUs, skip missing ones) are pure logic and port into `:shared` unchanged.
`displayPrice` becomes the one-time offer's `formattedPrice`. openiap-google reads it from
`ProductDetails.getOneTimePurchaseOfferDetailsList()` and sets the offer token on
`ProductDetailsParams`, and the native code should do the same.

**Semantics: same Play-side behaviour, made explicit.** The bytecode of openiap-google 2.5.2 calls
exactly these PBL entry points (`javap` over its `classes.jar`): `consumeAsync`,
`acknowledgePurchase`, `queryPurchasesAsync`, `launchBillingFlow`, `queryProductDetailsAsync`, and
`enablePendingPurchases(PendingPurchasesParams…enableOneTimeProducts())`. `isConsumable: true`
routes to `consumeAsync`. Per the integrate guide, for a client-only app "use `consumeAsync()` …
Both methods fulfill the acknowledgement requirement". So the native rules are:

| Today (`support.tsx`)                                                   | Native                                                                                                                                                   |
| ----------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `purchaseState === 'pending'` → show `support.pending`, don't finish    | `Purchase.purchaseState == PENDING` → same; never consume or acknowledge a PENDING purchase (the docs say so explicitly)                                   |
| `tierForSku(productId)`, `recordTip`, then `finishTransaction(consumable)` | `PURCHASED` → `recordTip` → `consumePurchase`. Consider consuming *first*: today a failed consume after a successful record leaves the purchase to be recorded twice by any later reconcile |
| `ErrorCode.UserCancelled` → no message                                  | `USER_CANCELED` → no message                                                                                                                              |
| `onPurchaseError` → `support.failed`                                    | other codes → `support.failed`                                                                                                                            |

`enablePendingPurchases(...enableOneTimeProducts())` is mandatory: "Your app must enable and support
pending transactions for one-time products."

**One thing the issue didn't ask but the port has to know:** `docs/native-migration-plan.md` M4 says
Support "writes nothing to files". That's wrong. `recordTip` writes `exercise-tracker/supporter.json`
(`src/storage/paths.ts`), and RN's Settings reads it (`src/app/settings.tsx` hydrates `useTipStore` on
mount to show the Support row's thank-you). So native Support becomes a writer of a file that an RN
screen reads. That needs the same path-constant-in-both-places treatment `MainActivity.kt` gives
`preferences.json`, and Settings has to re-read on focus.

## 3. What happens to a pending or unacknowledged purchase made through expo-iap?

Facts from the integrate guide:

- A purchase must be acknowledged (consume counts) "within three days so that the purchase isn't
  automatically refunded and entitlement revoked".
- "The three-day acknowledgement window begins only when the purchase state transitions from
  'PENDING' to 'PURCHASED'."
- "`onPurchasesUpdated` … If your application is not running or your app doesn't have an active
  Google Play Billing library connection, `onPurchasesUpdated` won't be invoked." Apps should call
  `queryPurchasesAsync()` on connection and in `onResume()` to catch purchases that became
  `PURCHASED` while the app wasn't running.

What that means here:

- **The update itself changes nothing.** The purchase record is Play's, keyed to the package name and
  the user's account. `expo-iap` keeps no local state that matters (its `markPurchaseDelivered`
  dedupe is in-memory). A native `BillingClient` in the same package sees every unconsumed purchase
  through `queryPurchasesAsync(INAPP)`, whichever library started it.
- **Today's screen never calls `getAvailablePurchases`.** `useIAP` opens the connection on mount and
  calls `endConnection` on unmount (`node_modules/expo-iap/src/useIAP.ts`), and it doesn't query
  purchases on connect for in-app products. So a slow-payment tip (boleto, some cards) that completes
  after the user leaves Support is never consumed. Play refunds it three days after it reaches
  `PURCHASED`, and until then the tier is "owned", so a retip of that tier gets `ITEM_ALREADY_OWNED`.
  This is a latent bug in the RN screen now, independent of the migration.
- **So the native port should reconcile:** on a successful connection (Support open, and optionally
  app resume), `queryPurchasesAsync(INAPP)`. For each `PURCHASED` purchase of a tip SKU, record and
  consume it. Skip `PENDING`. Purchases made under `expo-iap` before the update fall into that same
  sweep with no special migration code, provided the user opens Support within three days of the
  payment completing. If reconciliation should run without Support being opened, it has to hang off
  app start or resume, which costs a billing connection on a cold start the tip store deliberately
  stays out of today.
- On `ITEM_ALREADY_OWNED`, the errors guide says to call `queryPurchasesAsync()` and retry if not
  owned. The same reconcile covers it.

## 4. The native equivalent of safe-iap.ts's degrade

`safe-iap.ts` guards a *binary* fact: the `ExpoIap` native module may be absent (a build without a
rebuild, Expo Go). In Kotlin, PBL is a compile-time dependency of `:app`, so that failure can't
exist. The remaining failures are runtime, and the errors guide sorts them like this:

| Signal                                                     | Meaning (errors guide)                                                                                     | Screen                                                                                                                 |
| ---------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `startConnection` → `BILLING_UNAVAILABLE`                  | Play Store outdated, unsupported country, enterprise-disabled, payment method unusable, Play Store blocked (kids mode; 9.0+) | `support.storeUnavailablePlay`. "Automatic retries are unlikely to help."                                               |
| `SERVICE_UNAVAILABLE`, `NETWORK_ERROR`, `ERROR`            | Transient                                                                                                   | Simple retry while the screen is open, then the same unavailable copy                                                   |
| `SERVICE_DISCONNECTED` / `onBillingServiceDisconnected`    | Connection severed                                                                                          | Build the client with `.enableAutoServiceReconnection()` (PBL 8+) and let the library retry                            |
| Product query returns none of the three SKUs               | Console misconfigured or not yet propagated                                                                 | Same as today's `toTipTierOffers`: fewer buttons. Zero offers needs its own state, not today's spinner that never stops |
| No Play Store at all (sideloaded GitHub APK on a de-Googled phone) | `startConnection` fails / never connects                                                            | Unavailable copy. Not a crash                                                                                           |

Keep the parts of today's screen that are about degrading rather than about the module: the past-tip
card renders whatever billing does, nothing throws out of the screen, and every failure ends in
copy rather than an error boundary. Wrap the `BillingClient` in a small class that exposes a
`StateFlow` of `Unavailable | Loading | Ready(offers)`, the same role `safe-iap.ts` plays. Then the
Compose screen has one branch and the class can be tested with a fake.

Note: today's screen spins forever if the store connects but returns zero products
(`offers.length === 0` shows `support.loading`). The native port shouldn't copy that.

## 5. Does PBL change the Data Safety declaration?

**No.** From Play Console Help, "Provide information for Google Play's Data safety section":

- "Collect" means "transmitting data from your app off a user's device". Data "only processed locally
  on the user's device and not sent off device" isn't disclosed.
- If the app uses "a service such as PayPal, Google Pay, or Google Play's billing system", it doesn't
  declare "collection of the data that the payment service collects", provided "your app never
  accesses this information". Kettle reads only product IDs, prices, purchase state and the purchase
  token. It keeps them on the device (`supporter.json` holds a count, a timestamp and a tier) and
  sends nothing to a server of its own, because it has none.
- SDK traffic counts toward the app's declaration ("user data transmitted off device from your app by
  libraries and/or SDKs"). PBL talks to the on-device Play Store app over IPC. Any data it moves is
  Google Play's billing system collecting it, which falls under the exemption above. That's no
  different from today, since today's binary already runs PBL 9.1.0 underneath `expo-iap`.

Moving to PBL makes the declaration *easier* to stand behind. openiap-google 2.5.2 carries
purchase-verification helpers whose bytecode holds the endpoints
`https://kit.openiap.dev/v1/purchase/verify` and
`https://androidpublisher.googleapis.com/androidpublisher/v3/applications`
(`dev/hyo/openiap/utils/PurchaseVerificationValidatorKt`). Kettle never calls them, so nothing is
sent and nothing needs declaring today. But it's third-party networking code in the APK, and its
manifest also merges `android.permission.INTERNET` (the shell already declares it). Deleting
`expo-iap` removes that code.

The one way a native port *could* change the answer is to pass `setObfuscatedAccountId` /
`setObfuscatedProfileId` or send a purchase token anywhere. Today's request
(`tipPurchaseRequest`) sets neither, and the port shouldn't either.

## What I couldn't verify

- **Whether two `BillingClient` instances in one process route purchase updates only to the
  launching client.** Not documented on the pages read, and moot if `support.tsx` is deleted in the
  same PR (M4's rule). Not tested on a device.
- **openiap-google's behaviour is read from bytecode, not source.** The Gradle cache has no sources
  jar. The method references are exact. The control flow (for example that `isConsumable` routes to
  `consumeAsync`) is inferred from the API surface plus the `expo-iap` JS docs, not traced line by
  line.
- **`billing-ktx` coordinates** come from the integrate page's note that the KTX module "uses the
  same pattern". It wasn't resolved in Gradle.
- **Data Safety** is the policy text applied to this app's behaviour, not a ruling from Google.

## Sources

- Play Billing release notes: https://developer.android.com/google/play/billing/release-notes
- Integrate the library: https://developer.android.com/google/play/billing/integrate
- Handle BillingResult response codes: https://developer.android.com/google/play/billing/errors
- Data safety section help: https://support.google.com/googleplay/android-developer/answer/10787469
- `node_modules/expo-iap/` 4.7.2: `android/build.gradle`, `openiap-versions.json`,
  `android/src/main/java/expo/modules/iap/ExpoIapModule.kt`, `src/useIAP.ts`
- `~/.m2/repository/expo/modules/iap/expo.modules.iap/4.7.2/` (POM, `.module`, release AAR)
- `~/.gradle/caches/modules-2/files-2.1/io.github.hyochan.openiap/openiap-google/2.5.2/` (POM, AAR)
- `./gradlew :app:dependencyInsight` on `releaseRuntimeClasspath` / `releaseCompileClasspath`
- Repo: `src/domain/tip.ts`, `src/app/support.tsx`, `src/hooks/safe-iap.ts`,
  `src/state/tip-store.ts`, `src/storage/tip-file.ts`, `src/storage/paths.ts`,
  `src/app/settings.tsx`, `docs/decisions.md` (tip-jar entry), `docs/native-migration-plan.md` (M4),
  `native/android/app/build.gradle.kts`, `native/android/app/src/main/java/com/casco/kettle/shell/MainActivity.kt`
