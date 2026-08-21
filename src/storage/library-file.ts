// Straight from i18next rather than from `@/i18n`, matching `domain/format.ts`: importing our own
// module would pull `expo-localization` into every test that touches storage, and this only needs the
// language the UI is already rendering in. `@/i18n` is imported at module scope in `app/_layout.tsx`
// and hydration runs from an effect, so it is always initialised by the time this is read; an
// uninitialised instance reports no language and falls back to the English seed.
import i18next from 'i18next';

import { parseLibraryYaml, repairLibraryBounds, serializeLibraryYaml } from '@/domain/yaml-mapping';
import type { Library } from '@/domain/types';
import { ensureStorageReady, isFileStorageSupported, quarantineFile, storagePaths } from '@/storage/paths';
import { seedLibraryFor } from '@/storage/seed-library';

export type LoadLibraryResult = { ok: true; library: Library } | { ok: false; error: string };

/**
 * Reads exercises.yaml, seeding it from defaults on first launch. Never throws.
 * On web (unsupported by expo-file-system) this degrades to an in-memory seed library so the UI
 * stays browsable — nothing persists there, matching the pre-refactor mock-data web experience.
 *
 * The seed is picked in the user's language and then **frozen**: from the moment it's written it is
 * user data, so a later language change never renames it. Note that the reseed path below shares that
 * pick — a library corrupted after the user switched device language comes back in the new one, which
 * is the honest behaviour for content that has to be regenerated from scratch anyway.
 */
export async function loadLibrary(): Promise<LoadLibraryResult> {
  const seed = seedLibraryFor(i18next.language);

  if (!isFileStorageSupported) return { ok: true, library: seed };

  ensureStorageReady();

  if (!storagePaths.libraryFile.exists) {
    await saveLibrary(seed);
    return { ok: true, library: seed };
  }

  const text = await storagePaths.libraryFile.text();
  const result = parseLibraryYaml(text);
  if (result.ok) return { ok: true, library: result.data };

  // The persisted file predates a breaking schema change, or is otherwise corrupt. This is the app's
  // own auto-loaded storage rather than a user-picked import, so it self-heals instead of leaving the
  // app on a blank screen — but "self-heal" used to mean overwriting the only copy of the user's
  // library with the seed, which is a destructive recovery.
  //
  // So: repair first. Adding the repeat-count ceilings is exactly the kind of change that turns a
  // file which parsed yesterday into one that doesn't, and clamping it back into range keeps
  // everything else the user wrote (see repairLibraryBounds).
  const repaired = repairLibraryBounds(text);
  if (repaired) {
    const retry = parseLibraryYaml(repaired);
    if (retry.ok) {
      console.warn(`exercises.yaml held out-of-range values (${result.error.detail}); clamped them and kept the rest`);
      await saveLibrary(retry.data);
      return { ok: true, library: retry.data };
    }
  }

  // Beyond repair. Reseed, but never over the top of it — the file moves aside first, so the recovery
  // costs the user their *access* to the library and not the library.
  console.warn(`exercises.yaml failed to parse (${result.error.kind}), quarantining and reseeding: ${result.error.detail}`);
  quarantine(text);
  await saveLibrary(seed);
  return { ok: true, library: seed };
}

/**
 * Copies the unreadable library aside before it is replaced. Never throws: this runs on the recovery
 * path, and a failure to *rescue* the old file must not also block the reseed that gets the app
 * running again. A failed rescue is logged and the user is no worse off than before this existed.
 */
function quarantine(text: string): void {
  try {
    const file = quarantineFile(new Date().toISOString().replaceAll(/[:.]/g, '-'));
    if (!file.exists) file.create({ intermediates: true, overwrite: true });
    file.write(text);
  } catch (error) {
    console.warn(`could not quarantine the unreadable exercises.yaml: ${(error as Error).message}`);
  }
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
