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

/**
 * Asks the shell to set `reps` exercise `exerciseId`'s target load to `weightKg` (#220): the runner's
 * one-tap adopt, which native writes, since it owns the library from the library flip. Native answers
 * `filesChanged('library')` once the file has it, and nothing at all when there was nothing to set or
 * the write failed, which stays as silent as it always was.
 *
 * **Kilograms, straight off a logged set, with no display round trip**: the exercise form's path would
 * reintroduce the lossy conversion `previousWeightKg` exists to prevent, turning an adopted 100 kg into
 * 100.02 for a pound user. Nothing outside the shell writes it. The shell's `JsBridge` reads it. The
 * two share no code, so change both.
 */
export function reportTargetWeight(exerciseId: string, weightKg: number): void {
  if (!isHosted()) return;
  getBrownfield()?.sendMessage({ type: 'setTargetWeight', exerciseId, weightKg });
}
