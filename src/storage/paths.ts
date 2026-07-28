import { Directory, File, Paths } from 'expo-file-system';
import { Platform } from 'react-native';

/** expo-file-system has no web implementation; storage degrades to no-op/unsupported there. */
export const isFileStorageSupported = Platform.OS !== 'web';

type ResolvedPaths = { root: Directory; sessionsDir: Directory; libraryFile: File; supporterFile: File };

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
    resolved = { root, sessionsDir, libraryFile, supporterFile };
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
};

export function sessionFile(id: string): File {
  return new File(resolvePaths().sessionsDir, `${id}.yaml`);
}

/** Ensures the app document directory layout exists. Safe to call repeatedly. */
export function ensureStorageReady(): void {
  const { root, sessionsDir } = resolvePaths();
  if (!root.exists) root.create({ intermediates: true, idempotent: true });
  if (!sessionsDir.exists) sessionsDir.create({ intermediates: true, idempotent: true });
}
