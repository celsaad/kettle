import { preferencesSchema, type Preferences } from '@/domain/preferences';
import { ensureStorageReady, isFileStorageSupported, storagePaths } from '@/storage/paths';

/**
 * Reads preferences.json. Never throws, and returns `null` — not a default — when there is nothing
 * usable stored.
 *
 * `null` is the whole contract: it says "the user has never answered this", which the store turns
 * into "follow the device". A corrupt or half-written file lands in the same place as a missing one,
 * matching how `loadSupporterState` self-heals and `loadLibrary` reseeds: this file is app-owned, so
 * a parse error has no user who could go and fix it by hand, and the cost of being wrong is one
 * setting reverting to the device default rather than any workout data being lost.
 */
export async function loadPreferences(): Promise<Preferences | null> {
  if (!isFileStorageSupported) return null;

  try {
    ensureStorageReady();
    if (!storagePaths.preferencesFile.exists) return null;

    const parsed = preferencesSchema.safeParse(JSON.parse(await storagePaths.preferencesFile.text()));
    return parsed.success ? parsed.data : null;
  } catch {
    return null;
  }
}

/** Persists preferences. Returns whether the write landed, so the caller can say it won't survive a restart. */
export async function savePreferences(preferences: Preferences): Promise<boolean> {
  if (!isFileStorageSupported) return false;

  try {
    ensureStorageReady();
    if (!storagePaths.preferencesFile.exists) {
      storagePaths.preferencesFile.create({ intermediates: true, overwrite: true });
    }
    storagePaths.preferencesFile.write(JSON.stringify(preferences));
    return true;
  } catch {
    return false;
  }
}
