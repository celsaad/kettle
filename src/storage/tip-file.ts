import { EMPTY_SUPPORTER_STATE, parseSupporterState, type SupporterState } from '@/domain/tip';
import { ensureStorageReady, isFileStorageSupported, storagePaths } from '@/storage/paths';

/**
 * Reads supporter.json. Never throws — a tip jar that breaks Settings on a bad read would cost more
 * than it earns.
 *
 * Unlike a user-picked import, this file is written only by the app, so a parse failure has no user
 * to report to and nothing to fix by hand. It self-heals to the empty state, matching how
 * `loadLibrary` reseeds a corrupt exercises.yaml. The cost of being wrong is forgetting a past tip,
 * not losing workout data.
 *
 * There is no save beside it: the native shell's Support screen is the file's only writer from M4
 * (docs/native-migration-plan.md), and tells RN to read it again with a `filesChanged` message.
 */
export async function loadSupporterState(): Promise<SupporterState> {
  if (!isFileStorageSupported) return EMPTY_SUPPORTER_STATE;

  try {
    ensureStorageReady();
    if (!storagePaths.supporterFile.exists) return EMPTY_SUPPORTER_STATE;

    return parseSupporterState(await storagePaths.supporterFile.text());
  } catch {
    return EMPTY_SUPPORTER_STATE;
  }
}
