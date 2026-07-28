import { EMPTY_SUPPORTER_STATE, supporterStateSchema, type SupporterState } from '@/domain/tip';
import { ensureStorageReady, isFileStorageSupported, storagePaths } from '@/storage/paths';

/**
 * Reads supporter.json. Never throws — a tip jar that breaks Settings on a bad read would cost more
 * than it earns.
 *
 * Unlike a user-picked import, this file is written only by the app, so a parse failure has no user
 * to report to and nothing to fix by hand. It self-heals to the empty state, matching how
 * `loadLibrary` reseeds a corrupt exercises.yaml. The cost of being wrong is forgetting a past tip,
 * not losing workout data.
 */
export async function loadSupporterState(): Promise<SupporterState> {
  if (!isFileStorageSupported) return EMPTY_SUPPORTER_STATE;

  try {
    ensureStorageReady();
    if (!storagePaths.supporterFile.exists) return EMPTY_SUPPORTER_STATE;

    const parsed = supporterStateSchema.safeParse(JSON.parse(await storagePaths.supporterFile.text()));
    return parsed.success ? parsed.data : EMPTY_SUPPORTER_STATE;
  } catch {
    return EMPTY_SUPPORTER_STATE;
  }
}

/** Persists supporter state. Returns whether the write landed, so a failed tip isn't shown as saved. */
export async function saveSupporterState(state: SupporterState): Promise<boolean> {
  if (!isFileStorageSupported) return false;

  try {
    ensureStorageReady();
    if (!storagePaths.supporterFile.exists) {
      storagePaths.supporterFile.create({ intermediates: true, overwrite: true });
    }
    storagePaths.supporterFile.write(JSON.stringify(state));
    return true;
  } catch {
    return false;
  }
}
