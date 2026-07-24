import { parseLibraryYaml, serializeLibraryYaml } from '@/domain/yaml-mapping';
import type { Library } from '@/domain/types';
import { ensureStorageReady, isFileStorageSupported, storagePaths } from '@/storage/paths';
import { seedLibrary } from '@/storage/seed-library';

export type LoadLibraryResult = { ok: true; library: Library } | { ok: false; error: string };

/**
 * Reads exercises.yaml, seeding it from defaults on first launch. Never throws.
 * On web (unsupported by expo-file-system) this degrades to an in-memory seed library so the UI
 * stays browsable — nothing persists there, matching the pre-refactor mock-data web experience.
 */
export async function loadLibrary(): Promise<LoadLibraryResult> {
  if (!isFileStorageSupported) return { ok: true, library: seedLibrary };

  ensureStorageReady();

  if (!storagePaths.libraryFile.exists) {
    await saveLibrary(seedLibrary);
    return { ok: true, library: seedLibrary };
  }

  const text = await storagePaths.libraryFile.text();
  const result = parseLibraryYaml(text);
  if (!result.ok) {
    // The persisted file predates a breaking schema change (or is otherwise corrupt) — this is the
    // app's own auto-loaded storage, not a user-picked import, so self-heal by reseeding rather than
    // leaving the app stuck on a blank screen. Hand-edited imports still get a hard error (see import.tsx).
    console.warn(`exercises.yaml failed to parse, reseeding: ${result.error}`);
    await saveLibrary(seedLibrary);
    return { ok: true, library: seedLibrary };
  }
  return { ok: true, library: result.data };
}

export async function saveLibrary(library: Library): Promise<void> {
  if (!isFileStorageSupported) return;

  ensureStorageReady();
  const text = serializeLibraryYaml(library);
  if (!storagePaths.libraryFile.exists) {
    storagePaths.libraryFile.create({ intermediates: true, overwrite: true });
  }
  storagePaths.libraryFile.write(text);
}
