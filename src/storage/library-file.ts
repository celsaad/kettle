import { parseLibraryYaml } from '@/domain/yaml-mapping';
import type { Library } from '@/domain/types';
import { storagePaths } from '@/storage/paths';

export type LoadLibraryResult = { ok: true; library: Library } | { ok: false; error: string };

/**
 * Reads exercises.yaml. Never throws, and never writes: native is the library's only writer from the
 * library flip (#220), and it seeds a missing file and repairs or quarantines and reseeds a corrupt one
 * at launch, before RN starts (`LibraryGate` in the shell). So a file this can't read is an error to
 * report, not one to heal: healing it here would make RN a second writer, racing native's own writes.
 *
 * iOS and Expo Go read the file like Android and get no seed: M5 keeps no RN fallback for unhosted
 * builds (#220).
 */
export async function loadLibrary(): Promise<LoadLibraryResult> {
  try {
    if (!storagePaths.libraryFile.exists) return { ok: false, error: 'exercises.yaml does not exist' };
    const result = parseLibraryYaml(await storagePaths.libraryFile.text());
    if (result.ok) return { ok: true, library: result.data };
    return { ok: false, error: `exercises.yaml failed to parse (${result.error.kind}): ${result.error.detail}` };
  } catch (error) {
    return { ok: false, error: `exercises.yaml could not be read: ${(error as Error).message}` };
  }
}
