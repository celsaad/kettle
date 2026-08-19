// Straight from i18next rather than from `@/i18n`, matching `domain/format.ts`: importing our own
// module would pull `expo-localization` into every test that touches storage, and this only needs the
// language the UI is already rendering in. `@/i18n` is imported at module scope in `app/_layout.tsx`
// and hydration runs from an effect, so it is always initialised by the time this is read; an
// uninitialised instance reports no language and falls back to the English seed.
import i18next from 'i18next';

import { parseLibraryYaml, serializeLibraryYaml } from '@/domain/yaml-mapping';
import type { Library } from '@/domain/types';
import { ensureStorageReady, isFileStorageSupported, storagePaths } from '@/storage/paths';
import { seedLibraryFor } from '@/storage/seed-library';

export type LoadLibraryResult = { ok: true; library: Library } | { ok: false; error: string };

/**
 * Copies an unparseable `exercises.yaml` aside before the reseed below overwrites it.
 *
 * The reseed is a recovery for the *app* and, without this, a total loss for the *user*: that file is
 * everything they ever authored, and the only notice was a `console.warn` nobody on a device can
 * read. The path that makes this urgent is not exotic corruption — it is **the app tightening its own
 * schema**. A release that adds an upper bound to `sets` turns a file that loaded yesterday into one
 * that doesn't, and it does so precisely for the users whose numbers the new bound targets. They would
 * have opened the app to find the seed library and no explanation.
 *
 * Best-effort by construction: a quarantine that failed must never be what keeps the app on a blank
 * screen, so every error here is swallowed and the reseed runs regardless. One fixed filename rather
 * than a timestamped series — the pathological case is a corrupt file being re-quarantined on every
 * launch, and a series would fill the user's storage with copies of the same broken document.
 */
function quarantineLibrary(text: string): boolean {
  try {
    const quarantined = storagePaths.quarantinedLibraryFile;
    if (!quarantined.exists) quarantined.create({ intermediates: true, overwrite: true });
    quarantined.write(text);
    return true;
  } catch {
    return false;
  }
}

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
  if (!result.ok) {
    // The persisted file predates a breaking schema change (or is otherwise corrupt) — this is the
    // app's own auto-loaded storage, not a user-picked import, so self-heal by reseeding rather than
    // leaving the app stuck on a blank screen. Hand-edited imports still get a hard error (see import.tsx).
    const kept = quarantineLibrary(text);
    console.warn(
      `exercises.yaml failed to parse (${result.error.kind}), reseeding: ${result.error.detail}` +
        (kept ? ' — the original was kept as exercises.unreadable.yaml' : ' — the original could not be kept'),
    );
    await saveLibrary(seed);
    return { ok: true, library: seed };
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
