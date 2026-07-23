import { parseLibraryYaml, serializeLibraryYaml } from '@/domain/yaml-mapping';
import type { Library } from '@/domain/types';
import { ensureStorageReady, storagePaths } from '@/storage/paths';
import { seedLibrary } from '@/storage/seed-library';

export type LoadLibraryResult = { ok: true; library: Library } | { ok: false; error: string };

/** Reads exercises.yaml, seeding it from defaults on first launch. Never throws. */
export async function loadLibrary(): Promise<LoadLibraryResult> {
  ensureStorageReady();

  if (!storagePaths.libraryFile.exists) {
    await saveLibrary(seedLibrary);
    return { ok: true, library: seedLibrary };
  }

  const text = await storagePaths.libraryFile.text();
  const result = parseLibraryYaml(text);
  if (!result.ok) return { ok: false, error: result.error };
  return { ok: true, library: result.data };
}

export async function saveLibrary(library: Library): Promise<void> {
  ensureStorageReady();
  const text = serializeLibraryYaml(library);
  if (!storagePaths.libraryFile.exists) {
    storagePaths.libraryFile.create({ intermediates: true, overwrite: true });
  }
  storagePaths.libraryFile.write(text);
}
