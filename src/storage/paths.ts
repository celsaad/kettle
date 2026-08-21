import { Directory, File, Paths } from 'expo-file-system';
import { Platform } from 'react-native';

/** expo-file-system has no web implementation; storage degrades to no-op/unsupported there. */
export const isFileStorageSupported = Platform.OS !== 'web';

type ResolvedPaths = {
  root: Directory;
  sessionsDir: Directory;
  libraryFile: File;
  supporterFile: File;
  preferencesFile: File;
};

let resolved: ResolvedPaths | null = null;

// Lazy: constructing Directory/File touches the native module immediately, which must not happen
// at module-import time (breaks web/SSR bundling, which imports this module transitively).
function resolvePaths(): ResolvedPaths {
  if (!resolved) {
    const root = new Directory(Paths.document, 'exercise-tracker');
    const sessionsDir = new Directory(root, 'sessions');
    const libraryFile = new File(root, 'exercises.yaml');
    // JSON, not YAML: app-owned purchase state, deliberately outside the library the user hand-edits.
    const supporterFile = new File(root, 'supporter.json');
    // Same reasoning: app settings aren't part of the library the user exports and shares.
    const preferencesFile = new File(root, 'preferences.json');
    resolved = { root, sessionsDir, libraryFile, supporterFile, preferencesFile };
  }
  return resolved;
}

export const storagePaths = {
  get root() {
    return resolvePaths().root;
  },
  get sessionsDir() {
    return resolvePaths().sessionsDir;
  },
  get libraryFile() {
    return resolvePaths().libraryFile;
  },
  get supporterFile() {
    return resolvePaths().supporterFile;
  },
  get preferencesFile() {
    return resolvePaths().preferencesFile;
  },
};

export function sessionFile(id: string): File {
  return new File(resolvePaths().sessionsDir, `${id}.yaml`);
}

/**
 * Where a library that cannot be parsed is moved before the app reseeds over it.
 *
 * `loadLibrary` used to overwrite an unreadable `exercises.yaml` with the seed and say nothing, which
 * makes the reseed a *destructive* recovery: the user's whole library is gone, and the only copy of it
 * was the file being replaced. Timestamped, so a second bad launch cannot overwrite the first rescue.
 *
 * Beside the library rather than in the cache directory — the point is that the OS must not reclaim it.
 */
export function quarantineFile(stamp: string): File {
  return new File(resolvePaths().root, `exercises.invalid-${stamp}.yaml`);
}

/**
 * A scratch file for something assembled on the fly to be handed to another app — today, the whole
 * history as one document. Deliberately in the cache directory and outside `storagePaths`: it holds
 * a *copy* of data that already lives under `root`, so the OS reclaiming it costs nothing, and
 * nothing here should ever be read back as if it were the source of truth.
 */
export function cacheFile(name: string): File {
  return new File(Paths.cache, name);
}

/** Ensures the app document directory layout exists. Safe to call repeatedly. */
export function ensureStorageReady(): void {
  const { root, sessionsDir } = resolvePaths();
  if (!root.exists) root.create({ intermediates: true, idempotent: true });
  if (!sessionsDir.exists) sessionsDir.create({ intermediates: true, idempotent: true });
}
