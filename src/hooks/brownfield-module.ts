/**
 * The native shell's module, and the sends to it that need no router: split from `safe-brownfield.ts`
 * so a store can tell the shell something without importing expo-router, which `safe-brownfield.ts`
 * needs for the messages it acts on. The history store is the one that does (#238).
 *
 * expo-brownfield throws on import wherever its native module isn't built in (Expo Go, a dev client
 * from before M2), so it's required lazily and a failure degrades to doing nothing.
 */
type BrownfieldModule = typeof import('expo-brownfield');

let cached: BrownfieldModule | null | undefined;

export function getBrownfield(): BrownfieldModule | null {
  if (cached !== undefined) return cached;
  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    cached = require('expo-brownfield') as BrownfieldModule;
  } catch {
    cached = null;
  }
  return cached;
}

/** Whether the native shell hosts this surface. False in every other build, as `hostedTabOptions` says. */
export function isHosted(): boolean {
  // The shell's MainActivity sets it. The two share no code, so change both.
  return getBrownfield()?.getSharedStateValue('nativeTabBar') === true;
}

/**
 * Tells the shell a session has just been finished and written, so it backs the log up into the
 * user's folder (#220): native owns the folder from the preferences flip, and answers `backupResult`,
 * which `safe-brownfield.ts` hands the history store. Nothing outside the shell backs up, as M5's "no
 * RN fallback for unhosted builds" says. The shell's `JsBridge` reads it. The two share no code, so
 * change both.
 */
export function reportSessionFinished(): void {
  if (!isHosted()) return;
  getBrownfield()?.sendMessage({ type: 'sessionFinished' });
}
