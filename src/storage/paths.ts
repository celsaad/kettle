import { Directory, File, Paths } from 'expo-file-system';

const root = new Directory(Paths.document, 'exercise-tracker');
const sessionsDir = new Directory(root, 'sessions');
const libraryFile = new File(root, 'exercises.yaml');

export const storagePaths = { root, sessionsDir, libraryFile };

export function sessionFile(id: string): File {
  return new File(sessionsDir, `${id}.yaml`);
}

/** Ensures the app document directory layout exists. Safe to call repeatedly. */
export function ensureStorageReady(): void {
  if (!root.exists) root.create({ intermediates: true, idempotent: true });
  if (!sessionsDir.exists) sessionsDir.create({ intermediates: true, idempotent: true });
}
