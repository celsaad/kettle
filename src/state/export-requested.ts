/**
 * Export from native History (#202) and native Library (#234): the shell asks, and RN shares, with the
 * share sheet the RN screens used. Export stays RN's until M5, when it moves native with Settings,
 * which shares the same files; until then one implementation serves every screen, rather than two
 * that could disagree.
 */
import { useSessionHistoryStore } from '@/state/session-history-store';
import { exportLibrary, exportSession, exportSessions } from '@/storage/export';

/**
 * What each export message shares: one session's file (`exportSession`, by `id`; one without a string
 * `id` shares nothing), the whole log (`exportHistory`), which is the raw `sessions`, since an archive
 * is the user's own files, or the library file (`exportLibrary`). One map for both the dispatcher's
 * question and the answer, since a type known to one and not the other is dropped unheard: the first
 * cut of Library's Export was, and only a device showed it.
 */
// The shell's JsBridge.export and JsBridge.exportLibrary. The two share no code, so change both.
const SHARES: Record<string, (message: Record<string, unknown>) => Promise<void>> = {
  exportSession: async ({ id }) => {
    if (typeof id === 'string') await exportSession(id);
  },
  exportHistory: () => exportSessions(useSessionHistoryStore.getState().sessions),
  exportLibrary: () => exportLibrary(),
};

/** Whether a shell message of this `type` is an export, so `hooks/safe-brownfield.ts` hands it here. */
export function isExportRequest(type: unknown): boolean {
  return typeof type === 'string' && Object.hasOwn(SHARES, type);
}

/**
 * Shares what `message` asks for, as `SHARES` says. Never rejects: it runs from the native module's
 * listener, where a throw has nowhere useful to go, and a message this JS doesn't know (a newer shell)
 * is ignored.
 */
export async function onExportRequested(message: Record<string, unknown>): Promise<void> {
  if (!isExportRequest(message.type)) return;
  try {
    await SHARES[message.type as string](message);
  } catch (error) {
    console.warn(`Exporting for the shell (${String(message.type)}) failed`, error);
  }
}
