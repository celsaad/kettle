import { Directory, File, Paths } from 'expo-file-system';
import { Platform } from 'react-native';

/** expo-file-system has no web implementation; storage degrades to no-op/unsupported there. */
export const isFileStorageSupported = Platform.OS !== 'web';

type ResolvedPaths = {
  root: Directory;
  sessionsDir: Directory;
  libraryFile: File;
  quarantinedLibraryFile: File;
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
    // Where a library that no longer parses is set aside before the reseed overwrites it. Same
    // directory and a `.yaml` name on purpose: the recovery is "open it, fix the line the warning
    // named, import it back", and a file in the cache dir or under a name the picker filters out
    // would not survive long enough to be recovered. See `quarantineLibrary` in library-file.ts.
    const quarantinedLibraryFile = new File(root, 'exercises.unreadable.yaml');
    // JSON, not YAML: app-owned purchase state, deliberately outside the library the user hand-edits.
    const supporterFile = new File(root, 'supporter.json');
    // Same reasoning: app settings aren't part of the library the user exports and shares.
    const preferencesFile = new File(root, 'preferences.json');
    resolved = { root, sessionsDir, libraryFile, quarantinedLibraryFile, supporterFile, preferencesFile };
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
  get quarantinedLibraryFile() {
    return resolvePaths().quarantinedLibraryFile;
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
