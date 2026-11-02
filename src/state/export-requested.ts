/**
 * Export from native History (#202) and native Library (#234): the shell asks, and RN shares, with the
 * share sheet the RN screens used. Export stays RN's until M5, when it moves native with Settings,
 * which shares the same files; until then one implementation serves every screen, rather than two
 * that could disagree.
 */
import { useSessionHistoryStore } from '@/state/session-history-store';
import { exportLibrary, exportSession, exportSessions } from '@/storage/export';

// The shell's JsBridge.export and JsBridge.exportLibrary. The two share no code, so change both.
const EXPORT_REQUESTS: readonly unknown[] = ['exportSession', 'exportHistory', 'exportLibrary'];

/**
 * Whether a shell message of this `type` is one `onExportRequested` shares, so the dispatcher in
 * `hooks/safe-brownfield.ts` hands it here. One list beside the handler, since a type known here but
 * not there is dropped unheard: the first cut of Library's Export was, and only a device showed it.
 */
export function isExportRequest(type: unknown): boolean {
  return EXPORT_REQUESTS.includes(type);
}

/**
 * Shares what `message` asks for: one session's file (`exportSession`, by `id`), the whole log
 * (`exportHistory`), which is the raw `sessions`, since an archive is the user's own files, or the
 * library file (`exportLibrary`). Never rejects: it runs from the native module's listener, where a
 * throw has nowhere useful to go, and a message this JS doesn't know (a newer shell) is ignored.
 */
export async function onExportRequested(message: Record<string, unknown>): Promise<void> {
  try {
    if (message.type === 'exportSession' && typeof message.id === 'string') await exportSession(message.id);
    else if (message.type === 'exportHistory') await exportSessions(useSessionHistoryStore.getState().sessions);
    else if (message.type === 'exportLibrary') await exportLibrary();
  } catch (error) {
    console.warn(`Exporting for the shell (${String(message.type)}) failed`, error);
  }
}
