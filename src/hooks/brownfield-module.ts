/**
 * The native shell's module, and what can be asked of it without a router: split from
 * `safe-brownfield.ts`, which needs expo-router for the messages it acts on, so a reader that only asks
 * whether the shell is there doesn't pull the router in.
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
