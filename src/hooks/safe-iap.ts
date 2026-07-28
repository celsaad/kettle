import { requireOptionalNativeModule } from 'expo-modules-core';

/**
 * Whether the Play Billing native module is actually present in the running binary.
 *
 * expo-iap resolves `requireNativeModule('ExpoIap')` *lazily*, on the first call into it — so a
 * missing native side doesn't fail at import where it could be caught, it throws from inside
 * `useIAP` as an uncaught promise rejection ("Cannot find native module 'ExpoIap'"). That's what a
 * user hits after adding the dependency without rebuilding, since native modules can't arrive over
 * a JS reload, and permanently in Expo Go, which ships a fixed set of modules.
 *
 * `requireOptionalNativeModule` returns null instead of throwing, so the screen can check first and
 * show the same "store unavailable" state it already has for a store that won't talk. Same shape as
 * `safe-notifications.ts` and `isFileStorageSupported`: a missing platform capability degrades to an
 * explanation rather than a crash — and here that matters more than usual, because the alternative
 * is an error screen on the one page asking the user for money.
 */
export const isIapAvailable = requireOptionalNativeModule('ExpoIap') !== null;
