# SDK 57 API notes

> **Reference, verified against `node_modules`, not against published docs.** Read this before using
> `expo-file-system`, `expo-notifications` or `expo-iap` — for two of the three, the docs site and
> most training data describe an older API that will typecheck-fail or crash at import. The general
> rule is in `AGENTS.md`: read the versioned docs at https://docs.expo.dev/versions/v57.0.0/ rather
> than recalling older Expo. This file is what that rule turned up the first time it was applied.
>
> Everything below was confirmed from the installed `.d.ts` files. If a version bumps, re-confirm the
> same way rather than trusting this page.

**Installed** (all via `pnpm expo install`, so versions are SDK-57-pinned): `js-yaml`, `zod`,
`zustand`, `expo-file-system`, `expo-keep-awake`, `expo-haptics`, `expo-notifications`,
`expo-sharing`. No `expo-document-picker` — the new `expo-file-system` API exposes
`File.pickFileAsync()` directly, so a separate picker dependency wasn't needed.

- `expo-notifications` and `expo-sharing` are registered in `app.json` `plugins` (the latter was
  auto-added by `expo install`).
- ✅ **API-version risk resolved:** SDK 57's `expo-file-system` is the class-based `File` / `Directory`
  / `Paths` API (confirmed from `node_modules/expo-file-system/build/*.d.ts`), not the legacy
  `readAsStringAsync`/`documentDirectory` functions. Key shapes used in `src/storage/`:
  `Paths.document`, `new Directory(...)` / `new File(...)`, sync `.exists`, sync `.create(options)`,
  async `.text()`, sync `.write(content)`, sync `.list()` (filter results with `instanceof File`).
- ✅ **expo-notifications API confirmed** from `node_modules/expo-notifications/build/*.d.ts`:
  `scheduleNotificationAsync({ content, trigger: { type: SchedulableTriggerInputTypes.TIME_INTERVAL,
  seconds, repeats } })`, `cancelScheduledNotificationAsync(id)`, `setNotificationHandler(...)`,
  `requestPermissionsAsync()`.
- ✅ **expo-iap API confirmed** from `node_modules/expo-iap/build/*.d.ts` (installed at 4.7.2 — the
  published docs site still shows 3.4 examples, and the `hyochan/expo-iap` repo is archived in favour
  of the OpenIAP monorepo, so the types are the only trustworthy source). Shapes used in
  `src/app/support.tsx`: `useIAP({ onPurchaseSuccess, onPurchaseError, onError })` returning
  `{ connected, products, fetchProducts, requestPurchase }`; `fetchProducts({ skus, type: 'in-app' })`;
  `requestPurchase({ request: { google: { skus } }, type: 'in-app' })` (`android` is deprecated in
  favour of `google`); the **module-level** `finishTransaction({ purchase, isConsumable })`, used
  instead of the hook's so the success handler doesn't close over a binding the hook hasn't produced
  yet; `ErrorCode.UserCancelled` (kebab-case values — the `E_`-prefixed codes are long gone);
  `Product.displayPrice` (the store's localized string; `price` is `number | null` on Android, hence
  never used for ordering) and `Purchase.purchaseState: 'pending' | 'purchased' | 'unknown'`.
- ⚠️ **Web is not a persistence target.** `expo-file-system` has no web implementation. The storage
  layer detects this (`isFileStorageSupported` in `paths.ts`) and degrades gracefully: on web,
  `loadLibrary()` returns the seed library in-memory (no persistence, matching the old mock-data web
  experience) and `listSessions()`/all writes are no-ops instead of throwing. This was a real bug
  caught mid-implementation — `src/storage/paths.ts` originally constructed `Directory`/`File`
  instances at module-import time, which crashed `expo export --platform web` (and would have crashed
  the web runtime too); fixed by making path resolution lazy.

