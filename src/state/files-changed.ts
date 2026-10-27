/**
 * The other half of "one writer per file" (docs/native-migration-plan.md, M3): once a native screen
 * writes a file, the shell says which with a `filesChanged` message, and the store that reads that
 * file reads it again, so what RN shows is never older than the disk.
 *
 * A message the shell sends before JS is listening isn't lost: the shell holds it until "ready" (its
 * `JsChannel`), and "ready" comes after the stores' first read anyway.
 */
import { useLibraryStore } from '@/state/library-store';
import { usePreferencesStore } from '@/state/preferences-store';
import { useSessionHistoryStore } from '@/state/session-history-store';

// The shell's FileKind. The two share no code, so change both.
type FileKind = 'library' | 'sessions' | 'preferences';

const reloaders: Record<FileKind, () => Promise<void>> = {
  library: () => useLibraryStore.getState().reload(),
  sessions: () => useSessionHistoryStore.getState().reload(),
  preferences: () => usePreferencesStore.getState().reload(),
};

function isFileKind(kind: unknown): kind is FileKind {
  return typeof kind === 'string' && Object.hasOwn(reloaders, kind);
}

/**
 * Re-reads the file `kind` names. Never rejects: it runs from the native module's listener, where a
 * throw has nowhere useful to go. A kind this JS doesn't know (a newer shell) is ignored, and a read
 * that fails leaves the store showing what it had, which is no worse than before the message.
 */
export async function onFilesChanged(kind: unknown): Promise<void> {
  if (!isFileKind(kind)) return;
  try {
    await reloaders[kind]();
  } catch (error) {
    console.warn(`Re-reading ${kind} after the shell changed it failed; keeping what was loaded`, error);
  }
}
