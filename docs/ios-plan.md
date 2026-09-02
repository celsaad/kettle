# Shipping Kettle on iOS — plan

> **Partly executed.** Written against the tree at `d80e5e7`, SDK 57. **Three of Phase 2's five
> changes have shipped** — 2.1, 2.2 and 2.5, marked *Shipped* where they appear — along with the
> `app.json` block Phase 1 asks for, minus `supportsTablet`. Everything else is still forward-looking:
> nothing has been built, run, signed or submitted, and no simulator has ever opened this app.
>
> Nothing about the *decision* has changed with it. The two that mattered were bugs on the shipping
> Android app rather than iOS work, and they were worth fixing either way. The decision log still says
> iOS is deferred ([`decisions.md`](decisions.md), the tip-jar entry) and the README still says it
> isn't planned — both remain accurate, and **Phase 5 is where they get changed**, not before.

The app is closer to iOS than the docs suggest. There is no `ios/` tree to write, no architecture to
port, and no storage layer to rethink: `expo prebuild --platform ios` generates the native project the
same way [`android.yml`](../.github/workflows/android.yml) generates `android/`, and every storage
guard in the app is `Platform.OS !== 'web'` rather than `=== 'android'`. What was missing is five
small code changes — three of them now in — a set of deliberate decisions about background
behaviour, and about $99/yr plus a Mac.

**Two of those five were latent bugs rather than iOS work** — a purchase request that could only
succeed on Play (2.1), and a localization gap that would ship the app in English to every Portuguese
and Japanese device (2.5). Both were worth fixing whether or not the App Store decision ever goes
ahead, which is why they are fixed and the decision is still open.

The expensive part is not the code. It's the build infrastructure and the review surface, which is why
those get phases of their own and the code gets one.

## What already works, unchanged

Checked against the tree rather than assumed, because the first draft of this plan assumed three of
these were missing:

| Thing | Where | Why it's already fine |
|---|---|---|
| Native project generation | no `ios/` in the tree | CNG. Same prebuild path Android already uses; nothing is checked in to port |
| App icon | [`assets/expo.icon/`](../assets/expo.icon) via [`app.json`](../app.json) `ios.icon` | Already an Icon Composer bundle, and `icon.json` already declares a `watchOS` circle mask |
| Tab bar | [`(tabs)/_layout.tsx`](../src/app/(tabs)/_layout.tsx) | Every `NativeTabs.Trigger.Icon` already carries `sf={{ default, selected }}` beside its `md=`. The iOS tab bar renders natively today |
| File storage | [`storage/paths.ts:5`](../src/storage/paths.ts#L5) | `isFileStorageSupported = Platform.OS !== 'web'`, so iOS gets the real document directory, not the web degrade |
| Session timing | [`use-session-runner.ts`](../src/hooks/use-session-runner.ts) | Recomputes from `Date.now()` on every `AppState` change instead of accumulating ticks. Written for Android throttling; it is exactly what survives iOS *suspending* the app |
| Confirm dialogs | everywhere `Alert.alert` is used | Real on iOS. The no-op is web only |
| Keyboard | [`session-exercise-picker.tsx:74`](../src/components/session-exercise-picker.tsx#L74) | Already branches `Platform.OS === 'ios' ? 'padding' : undefined` |
| Export / share | [`storage/export.ts`](../src/storage/export.ts) | `expo-sharing` is the iOS share sheet |
| Privacy posture | nothing in `src/` transmits | The App Privacy label mirrors the Play declaration verbatim: nothing collected, nothing shared |

Two consequences worth stating: **the a11y and i18n work carries over free** (roles, labels and the
three locale bundles are platform-neutral, and VoiceOver reads the same props TalkBack does), and
**every domain test stays valid** — nothing in `src/domain/` knows what a platform is.

## Phase 1 — Simulator bring-up, before spending anything

Everything here is free and needs no Apple Developer account. Do it first: it converts the $99
decision from a guess into a judgement about a build you have actually driven.

The `ios` block in [`app.json`](../app.json) is **shipped**, bar one field:

- **`bundleIdentifier`** — `com.casco.kettle`. ✅ The App Store and Play namespaces are independent, so
  reusing the Android package is fine and keeps one identifier in the head. Permanent once submitted.
- **`buildNumber`** — iOS's `versionCode`. String, not integer. ✅ At `"11"`, the same integer as
  `android.versionCode`, so one number serves both stores. `/bump` now edits all three fields and
  `app-config.test.ts` fails if the two ever drift apart — the Phase 6 item below is closed.
- **`supportsTablet`** — **decide this by looking, not by reasoning.** The tempting argument is that
  the layouts already cap themselves with `MaxContentWidth` so an iPad gets a centred column rather
  than stretched rows. But `MaxContentWidth` is **480** ([`theme.ts:96`](../src/constants/theme.ts#L96)),
  and a 480pt column centred on a 13" iPad is mostly empty screen — which then becomes a *required
  screenshot* on the listing. Turning it on may cost more than the App Store filter it buys. Check it
  in the simulator, which is free, and while there confirm whether iPadOS 26's windowing still honours
  `"orientation": "portrait"` on iPad at all.
- The app is `"orientation": "portrait"` globally already; leave it (subject to the check above).

Then `pnpm exec expo prebuild --platform ios && pnpm ios`, and drive a real session in the simulator.
What to look at, in this order — these are where a first iOS run actually breaks:

1. The native tab bar, in both colour schemes. Light mode is the one that caught the Android tab bar
   out (see the comment at the top of `(tabs)/_layout.tsx`).
2. `ModalHeader`'s grabber and pinned close button against the iOS modal sheet presentation, which is
   not the shape Android draws.
3. The session runner end to end — it is always dark (`RunnerColors`) regardless of scheme.
4. Safe-area insets on a notched device profile.
5. The Settings screen, which is where the two platform-gated sections live and where the Play copy is.
6. **The app in Portuguese and Japanese**, by switching the simulator's language. This is not polish —
   see 2.5, which predicts both come up English until `app.json` declares them. It is the cheapest
   possible check for the most invisible failure in this plan.
7. The iPad profile, if `supportsTablet` is under consideration.

**The simulator cannot verify the thing this feature most needs verified** — background audio, the
silent switch, and notification delivery are all Phase 4, on a real device.

## Phase 2 — The five code changes

Small, contained, and testable inline. This is the whole of the iOS-specific application code.

### 2.1 The purchase call is Android-shaped — *shipped*

[`support.tsx:86`](../src/app/support.tsx#L86) sent:

```ts
await requestPurchase({ request: { google: { skus: [sku] } }, type: 'in-app' });
```

`expo-iap` 4.7.2 backs iOS with StoreKit 2 (`openiap-apple`) and takes `apple: { sku }` — singular
`sku`, not a `skus` array. Everything wrapped around this call is already platform-neutral and does not
move: `finishTransaction({ isConsumable: true })`, the `purchaseState === 'pending'` guard, the
`ErrorCode.UserCancelled` branch, and `toTipTierOffers`. Both keys now go in the one request object
so the store picks its own, built by `tipPurchaseRequest` in [`tip.ts`](../src/domain/tip.ts) — which
puts it where typecheck can check it against expo-iap's `RequestPurchaseProps` and where a test can
pin the shape without a React tree. This was the one line where a silent iOS-only failure lands on
the screen that asks the user for money.

`isTipJarSupported` ([`tip-store.ts:11`](../src/state/tip-store.ts#L11)) is already `!== 'web'`, so it
needs nothing. `safe-iap.ts`'s `requireOptionalNativeModule('ExpoIap')` guard works identically on iOS.

### 2.2 Two locale strings name Google Play — *shipped*

`support.why` ("A tip helps cover the Google Play developer fee") and `support.storeUnavailable`
("Couldn't reach the Play Store") in [`en.json`](../src/i18n/locales/en.json) and both siblings. House
rule is that no user-facing string lives outside the bundles, so this is a platform-keyed pair of keys
in all three files, not a `Platform.select` over literals.

They are now `whyPlay`/`whyAppStore` and `storeUnavailablePlay`/`storeUnavailableAppStore`, chosen by
`tipStoreCopyKeys(Platform.OS)` — which takes the platform as an argument rather than reading it, so
both branches are testable without rendering anything. Web takes the Play wording deliberately.

Note the copy is about to become *more* true, not less: on iOS the developer fee is $99/yr recurring
rather than $25 once, which is the strongest version of the argument that screen makes.

### 2.3 App Store Connect products, same SKU ids

[`tip.ts:23`](../src/domain/tip.ts#L23) fixes `tip_small` / `tip_medium` / `tip_large`. Create the same
three ids in App Store Connect as **Consumable** in-app purchases. Same reasoning as Play: consumables
are what make a repeat tip possible, and consumables aren't restorable from either store, so "has
tipped" stays local in `supporter.json` and nothing about that design changes.

**Tips are the entire monetization story, and that survives contact with App Review.** Apple permits
tip jars, and requires exactly what the app already does — the payment goes through IAP rather than a
link out, which is the same rule Play enforces and the same reason `decisions.md` refuses Ko-fi/PayPal.
There is no subscription to justify, nothing is gated, and there is no third-party purchase SDK, so
the review surface here is genuinely small. Two things to get right on the form rather than in code:
the products are **Consumable** (not Non-Consumable, which would make a second tip impossible), and the
listing copy must not call them "donations" — that word carries a nonprofit requirement on both stores.

Worth costing while here: Apple's **Small Business Program** drops the commission from 30% to 15% for
developers under $1M/yr. It is an application, not automatic.

### 2.4 iOS has no backup story, and the fix is config

[`backup.ts:33`](../src/storage/backup.ts#L33) hides the backup-folder feature on iOS, and the decision
log is right about why — iOS grants the picked folder for the app session only and stores no bookmark,
so a folder chosen there would look set and quietly stop being written to. That reasoning stands and
this plan does not reopen it.

But it leaves iOS with *nothing*, and the iOS-native equivalent isn't a picker at all. Two Info.plist
keys — `UIFileSharingEnabled` and `LSSupportsOpeningDocumentsInPlace` — put the app's Documents folder
in the Files app, where the user's own iCloud Drive, Dropbox or Working Copy can already see it. That
is a better fit for the data-ownership pitch than the Android SAF dance: the user doesn't nominate a
folder, they just *have* the folder, and the app writes where it always wrote.

It needs its own Settings copy (the Android section's strings are about choosing and forgetting a
folder, none of which applies), and it falsifies the claim at
[`product-plan.md:389`](product-plan.md#L389) that "this app declares no iOS file sharing" — that line
gets updated in Phase 6, not left to rot.

**This is also the honest version of "iCloud sync" for v1.** See the next section.

### 2.5 The app would very likely ship English-only, and nothing would fail — *shipped*

The most invisible item in this plan, and the cheapest to fix.

[`app.json`](../app.json) lists `"expo-localization"` bare, with no options. Its config plugin only
writes `ios.infoPlist.CFBundleLocalizations` when it is passed `supportedLocales`
(`plugin/build/withExpoLocalization.js:46-48` — the `if (supportedLocales != null)` guard is the whole
mechanism). So an iOS build declares **no** localizations.

That matters because of where the language comes from. `deviceLanguage()`
([`i18n/index.ts:39`](../src/i18n/index.ts#L39)) walks `getLocales()` for the first language it ships a
bundle for, and on iOS `getLocales()` is built from `Locale.preferredLanguages`
(`node_modules/expo-localization/ios/LocalizationModule.swift:149`) — which iOS filters against the
app bundle's declared localizations. With none declared, a Portuguese or Japanese iPhone very likely
reports `en`, `deviceLanguage()` returns `'en'`, and the `pt` and `ja` bundles never load on a device
whose owner never asked for English.

Nothing throws. Nothing warns. i18next's `fallbackLng` renders every key in English exactly as
designed, which is the same property AGENTS.md already flags about a key missing from a bundle: the
failure is silent by construction. Two thirds of the translation work in this repo would sit in the
binary, unreachable.

The fix is one option, and it is **keyed by platform rather than the bare array** this plan first
quoted:

```json
["expo-localization", { "supportedLocales": { "ios": ["en", "pt", "ja"] } }]
```

The bare array is not equivalent, and the difference is invisible from the diff. `supportedLocales`
feeds *both* platform mods: on Android it writes `app/src/main/res/xml/locales_config.xml`, sets
`android:localeConfig` on the manifest and appends `resourceConfigurations` to `build.gradle`'s
`defaultConfig` (`plugin/build/withExpoLocalization.js:69-103`), which strips other locales'
resources from the APK. None of that is wrong — per-app language on Android 13+ is worth having — but
it changes the *shipping* platform's build, and this fix is about the one that ships nothing yet. The
object form does the iOS half alone. Android per-app language is its own decision, and `open-work.md`
is where it goes if it's wanted.

**Verify rather than trust the reasoning** — the exact filtering behaviour of `Locale.preferredLanguages`
is the part worth seeing rather than arguing about, and flipping the simulator's language is already
Phase 1 step 6. **This is still owed**: the option is in `app.json` and the reasoning behind it is
unchanged, but nobody has yet watched a Portuguese simulator come up Portuguese. Do it once with the
option removed and once with it back; if the language changes, the mechanism is confirmed and so is
the fix.

This also meant **anything that adds a language has a seventh place to update**, and it does:
[`docs/adding-a-language.md`](adding-a-language.md) is a seven-place procedure, with `app.json` as
step 3 and `app-config.test.ts` diffing that list against `resources` so the omission fails a test
rather than a phone.

## Phase 3 — iCloud, and the sync question it reopens

The user's own filesystem via the Files app (2.4) is sync by the same mechanism the Android backup
folder uses: *the user's* sync client, watching a folder, with the app transmitting nothing. That keeps
the zero-data claim exactly as it is and needs no entitlement, no container, and no review question.

Real iCloud is a step past that, and it splits into two very different features that should not be
confused:

- **iCloud Drive (documents in a ubiquity container).** The app's Documents folder becomes an iCloud
  container, so the library and the session log appear on every device the user signs in on and in
  Files on the Mac. This is still "the user's files, in the user's cloud" — Apple holds them, the app
  still has no backend and still transmits nothing to *us*. It needs the iCloud capability and a
  container identifier in the entitlements, which is a Phase 4 signing concern.
- **CloudKit key-value or record sync.** Structured, conflict-resolved, app-managed. This is a real
  sync layer, and it is a different product: it needs a merge strategy for sessions written on two
  devices, which the app deliberately does not have — [`merge.ts`](../src/domain/merge.ts) merges the
  *library* by id with whole-object replace, and the session log is append-only-in-fact and
  **export-only** (see the decision log). There is nothing today that reads a session log back in.

The question that decides whether iCloud Drive is a config change or a project is whether SDK 57
exposes a ubiquity container at all. **It does not.** Grepping `node_modules/expo-file-system/build/`
for `icloud|ubiquit|bookmark|securityScoped` returns nothing — no first-party surface, so this needs a
config plugin plus native code. (Checked against the installed module rather than the published docs,
on the principle [`sdk-57-api-notes.md`](sdk-57-api-notes.md) exists to record: two of that file's
three subjects are documented wrong upstream.)

So **both halves of iCloud drop out of the launch**, for different reasons — iCloud Drive because it
needs native work this plan won't carry, CloudKit because it is session-restore wearing a sync costume
and session-restore is already a named follow-up in the backup decision entry. What ships instead is
2.4: the Files app, the user's own sync client, and nothing transmitted. That is most of what "iCloud
sync" means to a user, at the cost of two Info.plist keys.

If iCloud is picked up later it is its own plan, and this paragraph is why it isn't this one. The
practical consequence for Phase 5 is that **no iCloud container entitlement is needed**, which keeps
the signing setup to a certificate and a profile.

## Phase 4 — The background-cue problem, and the Watch

This is the highest-risk phase and the one that needs a real device. AGENTS.md already says to treat
the runner as high-risk and verify it by running a session rather than by reasoning; that applies
double here, because iOS's suspension model is stricter than the one this code was written against.

### What breaks

The runner's comments describe a hold "run with the phone on the floor and the screen asleep". On
Android that works. On iOS, once the app is suspended, `setInterval` stops **and** `expo-audio` goes
silent, so the only thing left is the local-notification fallback — and as configured it will not cue
anyone:

- `scheduleStepCompleteNotification` ([`safe-notifications.ts:53`](../src/hooks/safe-notifications.ts#L53))
  sets no `sound` on the notification content, so a rest ending in a pocket produces a silent banner.
  **This is the whole of the pocket failure, and `sound: 'default'` is the whole of the fix.**

  The `shouldPlaySound: false` in the same file's `setNotificationHandler` looks like a second cause
  and is not: on iOS that handler runs **only while the app is foregrounded**, and a suspended app's
  notification is rendered by the system from the content alone. In the foreground
  [`use-session-sounds.ts`](../src/hooks/use-session-sounds.ts) is already playing the real cue, so the
  handler is correct as written and **flipping it would double-cue.** Leave it alone.
- [`app.json`](../app.json) sets `expo-audio`'s `enableBackgroundPlayback: false`, so the real cues
  cannot fire in the background even in principle.

### The options, verified against the installed module

Read out of `node_modules/expo-notifications` rather than the docs:

| Fact | Where | Consequence |
|---|---|---|
| `NotificationContentInput.sound?: boolean \| 'default' \| 'defaultCritical' \| 'defaultRingtone' \| string` | `build/Notifications.types.d.ts:527` | The fallback can be made audible without any entitlement — `sound: 'default'` |
| Custom sound files are bundled by the config plugin's `sounds` array | `plugin/build/withNotificationsIOS.js:9` | The existing `tick.wav` / `milestone.wav` can be the notification sound, so a backgrounded cue sounds like a foregrounded one |
| `NotificationContentInput.interruptionLevel?: InterruptionLevel` | `build/Notifications.types.d.ts:580` | **`'timeSensitive'` breaks through Focus modes.** A rest timer is the textbook case for it, and "Do Not Disturb ate my rest cue" is otherwise a bug report waiting to happen |
| `'defaultCritical'` needs the critical-alerts entitlement | `build/Notifications.types.d.ts:521` | Don't. It's a special-approval entitlement and this isn't a medical alarm |
| The module ships `ios/PrivacyInfo.xcprivacy` | `node_modules/expo-notifications/ios/` | Expo modules carry their own privacy manifests; Phase 5 is about the app-level one, not these |

So there are two routes, and they are not exclusive:

- **Audible notifications** (`sound` + `interruptionLevel: 'timeSensitive'`). Cheap, no entitlement, no
  review argument, and it works while suspended. It cannot give a *ticking* countdown — it fires once,
  at the end.
- **`UIBackgroundModes: ['audio']`** plus flipping `enableBackgroundPlayback` to `true`, so the real
  3-2-1 tick plays from the pocket. Better UX, and defensible for a timer app, but App Review does
  scrutinise that key and the audio session has to genuinely be doing something.

Recommendation: **ship the first, evaluate the second on device.** The notification route removes the
"no cue at all" failure outright, which is the one that costs a workout; the background-audio route is
an improvement on top and can be cut without the feature being broken. `playsInSilentMode: true` is
already set in [`use-session-sounds.ts`](../src/hooks/use-session-sounds.ts) and stays either way.

### The Watch — and the trap in the current plan

[`watch-remote-plan.md`](watch-remote-plan.md) is written entirely against Wear OS, and its central
argument is that the transport is *the notification*, so there is no watch app to build. **That
argument survives the port better than expected**, and one of its verified facts turns out to have been
understating the case:

- The plan's table notes `categoryIdentifier` is tagged `@platform ios` in the types while Android
  implements it anyway. On iOS it is the native mechanism — notification categories with
  `UNNotificationAction` buttons — so the three buttons the plan specifies (**Done / Next**, **Back**,
  **+30s**) are the same API call on both platforms.
- `opensAppToForeground: false` (`build/Notifications.types.d.ts:670`), which the plan calls the single most
  important flag in the feature, exists on iOS with the same meaning: the action runs without yanking
  the phone out of the pocket.
- Apple Watch bridges iPhone notifications, action buttons included, with no watch app, no separate
  binary and no separate review — the exact property that made the Wear OS version cheap.

**There is an iOS-specific constraint to check first, but it is narrower than it first looks.** iOS
forwards notifications to the Watch only when the iPhone is *locked or asleep*, and the session screen
calls `useKeepAwake` ([`session.tsx`](../src/app/session.tsx)) for the whole duration of a workout.

That reads like a direct collision, and it partly is — but `useKeepAwake` disables the **idle timer**,
which prevents the phone falling asleep on its own. It does not prevent a *manual* lock. And the
scenario this entire feature targets is a phone in a pocket, which got there by the user pressing the
power button. That phone is locked, and forwards to the wrist normally.

So the cost is bounded: the wrist cue is lost when the phone is **face-up and awake on the floor**, and
retained when it is pocketed — which is the case the plan was written for. Worth knowing precisely,
because the imprecise version pre-authorizes an expensive branch that probably isn't needed.

So the update `watch-remote-plan.md` needs is not a translation of the Wear OS design. It is:

1. A verified answer to the keep-awake question on a real Watch — specifically, whether the awake-and-
   unlocked case is common enough in practice to matter, given the pocketed case works.
2. Only if that turns out to be fatal, a re-costing of a **real watchOS app** — which the plan already
   costed and rejected for Wear OS, for reasons (separate binary, separate review, data on the watch)
   that mostly apply again. Named here so it isn't reached for first; the notification route is very
   likely still the right one on iOS.
3. Either way, the plan's banner and its title stop being able to say "the transport is the Android
   notification" without qualification.

**This is explicitly not part of the iOS launch.** It is a plan that needs updating *because* iOS
happens, not a phase of shipping it.

## Phase 5 — Build, sign, submit

Three things gate this and none of them are code.

### The Mac question

`android.yml` went out of its way to avoid EAS, and [`building-android.md`](building-android.md)
explains why: the build is ordinary Gradle and what EAS added was a queue. The iOS equivalent is
ordinary `xcodebuild`, and the same logic transfers.

**The 10× macOS minute multiplier does not apply here, and it is worth saying so explicitly because it
is the obvious reason to be timid and it is wrong.** The multiplier governs *billed* minutes;
`celsaad/kettle` is public (`isPrivate: false`), `macos-latest` is a **standard** runner (3-core M1,
listed in GitHub's standard-runner table for public repositories), and standard runners are free and
unlimited on public repos. Ten times zero is zero.

So the trigger should be decided on the same grounds android.yml decided its own, not on cost. Both of
that workflow's justifications transfer intact — a merge build proves the native build still works
(nothing else in CI compiles a line of it) and it leaves a warm cache behind, and Actions caches only
flow from `master` downward, so `master`'s cache can only be warmed by a run on `master`. **Mirror it:
`push: [master]` with the same `paths-ignore`, plus `workflow_dispatch`.**

Two caveats that are real, unlike the cost one:

- **Start on `workflow_dispatch` only, until Phase 5's signing exists.** Before there is a certificate
  there is nothing to sign with, and a merge-triggered unsigned build is a smoke test whose failure
  mode nobody will read. Add the push trigger when the secrets land.
- **macOS concurrency is capped lower than Linux** on the free plan, so a merge build can queue behind
  itself. `concurrency: group: ios-build` with `cancel-in-progress: false`, same as android.yml.

The caching shape differs — ccache doesn't transfer, since the expensive parts are Swift/ObjC
compilation and CocoaPods resolution. Cache `Pods/` and DerivedData keyed on `pnpm-lock.yaml`, same
rolling-key trick as the existing two workflows.

### Signing

An Apple Distribution certificate and provisioning profile, as repository secrets, mirroring how the
upload keystore is handled today. **The stakes are lower than the Android ones** — the decision log's
signing entry warns that the wrong Android key is the one mistake that cannot be fixed for anyone who
has already installed, because Play App Signing and the upload key are distinct. On iOS, App Store
Connect re-signs and there is no sideload path to strand, so a certificate is revocable and replaceable.
It is still a secret; it just isn't that particular unrecoverable one.

### Submission

- App Store Connect record, bundle id, and the three consumable IAPs from 2.3.
- **`ios.config.usesNonExemptEncryption: false` in [`app.json`](../app.json).** ✅ Shipped. Without
  it *every* build prompts the export-compliance question in App Store Connect and blocks the
  submission until someone answers it by hand. The app genuinely uses no non-exempt encryption —
  there is no network call to encrypt anything for.
- **App Privacy questionnaire**: nothing collected, nothing shared — the same answers as the Play Data
  Safety form, for the same reason. Purchases are not "data collected" when the store handles the
  transaction; app code never sees payment data on either platform.
- **`PrivacyInfo.xcprivacy`**: Expo modules ship their own (confirmed for `expo-notifications`), and
  the app-level manifest declares required-reason API use. Verify what prebuild generates rather than
  assuming it's complete.
- Screenshots. [`store/build-assets.js`](../store/build-assets.js) generates Play sizes today; iOS
  needs 6.9" and, if `supportsTablet` is on, 13" iPad. Extending that script is better than a second
  pipeline — it already exists precisely so there's one copy of each asset rather than two that drift.
- Listing metadata in en, pt and ja, matching the bundles.

## Phase 6 — Docs and commands, which are load-bearing here

The repo's rule is not to write up shipped work in `docs/`. These are the exception the rule names:
constraints that shape future work, and claims that become **false** the moment iOS ships.

- **[`decisions.md`](decisions.md), tip-jar entry** — says "Kettle ships to Google Play only for now"
  and defers the App Store fee "until the app shows traction". That is a decision being reversed, which
  is exactly what the log is for. It also says RevenueCat's real advantage "is cross-platform
  entitlements, which is exactly what's deferred with iOS" — shipping iOS reopens that argument, so
  **re-close it explicitly**: tips are consumables recorded locally in `supporter.json`, restorable
  from neither store, so there is still no entitlement to carry across platforms and the transmit-data
  objection is unchanged.
- **[`decisions.md`](decisions.md), backup entry** — "It is Android-only, and that is a platform fact
  rather than a scope cut" stays true about *folder picking*, and gets a paragraph about the Files-app
  route from 2.4 being the iOS answer instead.
- **[`product-plan.md:389`](product-plan.md#L389)** — "this app declares no iOS file sharing" becomes
  false in 2.4.
- **[`README.md:49`](../README.md#L49)** — "iOS is not planned." A shipped platform is at most an edit
  to an existing bullet; it does not earn the README a paragraph.
- **`docs/building-ios.md`** — new, mirroring `building-android.md`: the certificate setup, the
  workflow, and what it costs.
- **[`watch-remote-plan.md`](watch-remote-plan.md)** — per Phase 4.
- **[`/bump`](../.claude/commands/bump.md)** — ✅ done, ahead of the rest of this phase, because
  `expo.ios.buildNumber` went into `app.json` the moment Phase 1's block did and a documented
  invariant nothing enforces is worth less than no invariant. The command now names three fields, and
  the decision this entry called for went the way it predicted: the two build numbers are kept
  **numerically identical**, so one integer serves both stores and the changelog heading
  (`## <version> — versionCode <n>, <date>`) can keep naming only the versionCode without becoming
  half-true. `app-config.test.ts` asserts the match rather than trusting it, and says what to do if a
  rejected submission ever needs a second build of one version: raise both.
- **[`store-copy.test.ts`](../src/domain/__tests__/store-copy.test.ts)** — a bigger job than a changed
  constant. It asserts Play's whole shape: 80 characters for short, 4000 for full, 500 per release-note
  block, that each heading *declares its own real length*, and that full descriptions keep 30
  characters of headroom. The App Store's limits differ across the board — name 30, subtitle 30,
  promotional text 170 — and none of them map onto a Play heading. This is a **second set of headings
  in the copy file** with its own assertions, not a tweaked number, and the existing test's design (the
  declared length in the heading, because the numbers beside a block have been wrong before) is the
  pattern to copy rather than re-derive.
- **[`/release`](../.claude/commands/release.md)** — its entire section 2 is about the Play-signed
  universal APK, and there is no iOS analogue: nothing gets sideloaded, and TestFlight is not a GitHub
  Release. The command should say plainly that it is the *Android and public-APK* release path, with
  the iOS route named and pointed elsewhere, rather than being silently half-true on a two-platform app.
- **[`store/README.md`](../store/README.md)** — holds Play's rules; App Store rules are a sibling
  document or a clearly-marked second section, not an edit that blurs which store a rule belongs to.

## Order, and what each phase costs

| Phase | Gate | Cost |
|---|---|---|
| 1 Simulator bring-up | A Mac | Free. Highest information per hour in the whole plan |
| 2 The five code changes | — | **3 of 5 shipped** (2.1, 2.2, 2.5 — both latent bugs among them). 2.3 is store-side, 2.4 still open |
| 3 iCloud | — | **Cut.** No SDK 57 surface; 2.4 is what ships instead |
| 4 Background cues | **A real device** | Highest risk. Notification route first, background audio evaluated on top |
| 5 Build, sign, submit | **$99/yr + certificate** | The money gate. Runner minutes are free — the repo is public |
| 6 Docs and commands | — | Small, and the rules above make it non-optional. `/bump` done; the rest waits on shipping |

Phases 1 and 2 were worth doing regardless of whether the App Store decision ever goes ahead: they
cost nothing, they either prove or disprove the premise, and **two of the five changes were bugs the
app had** — 2.1 broke the purchase path on any store but Play, and 2.5 would have handed a Portuguese
or Japanese user an English app while every test in the suite passed. Those are fixed. Phase 1 itself
is untouched: it needs a Mac, and the simulator checks it lists — the tab bar in both schemes, the
modal sheet, the runner, safe-area insets, and the language flip that 2.5 still owes — have not
happened.

## Out of scope, deliberately

Named up front so they're decisions rather than later rewrites:

- **CloudKit / structured sync.** Phase 3 says why: it is session-restore, which is already a named
  follow-up elsewhere, and merging sessions written on two devices is a domain problem the app has
  never had.
- **A standalone watchOS app.** Phase 4 — and only *after* the keep-awake collision is checked, because
  that check is what decides whether the cheap version exists at all.
- **iPad-specific layouts**, even with `supportsTablet: true`. `MaxContentWidth` already keeps it
  honest; a real iPad design is a design project.
- **RevenueCat, and any cross-platform entitlement layer.** Re-refused in Phase 6 with the reasoning,
  so it isn't re-proposed the next time two stores are in the room.
- **Subscriptions.** Tips only, nothing gated, on both platforms. The paywall line in the decision log
  — export stays free regardless — applies unchanged.
- **Universal links, widgets, Live Activities, Shortcuts/App Intents.** Live Activities in particular
  will look tempting the moment Phase 4's background problem is on the table; it is a genuinely good
  fit for a rest timer and it is also a native-code project with no first-party SDK 57 module. If it
  gets picked up, it is its own plan, not a bullet in this one.
