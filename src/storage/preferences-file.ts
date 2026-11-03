import { parsePreferences, type Preferences } from '@/domain/preferences';
import { ensureStorageReady, isFileStorageSupported, storagePaths } from '@/storage/paths';

/**
 * Reads preferences.json, which native Settings writes (#238). Never throws, and returns `null` — not a default — when there is nothing
 * usable stored.
 *
 * `null` is the whole contract: it says "the user has never answered this", which the store turns
 * into "follow the device". A corrupt or half-written file lands in the same place as a missing one,
 * matching how `loadLibrary` reseeds: this file is app-owned, so
 * a parse error has no user who could go and fix it by hand, and the cost of being wrong is one
 * setting reverting to the device default rather than any workout data being lost.
 */
export async function loadPreferences(): Promise<Preferences | null> {
  if (!isFileStorageSupported) return null;

  try {
    ensureStorageReady();
    if (!storagePaths.preferencesFile.exists) return null;

    return parsePreferences(await storagePaths.preferencesFile.text());
  } catch {
    return null;
  }
}
