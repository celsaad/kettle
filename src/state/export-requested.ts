/**
 * Export from native History (#202): the shell's History asks, and RN shares, with the share sheet RN
 * History used. Export stays RN's until M5, when it moves native with Settings, which shares the same
 * files; until then one implementation serves both screens, rather than two that could disagree.
 */
import { useSessionHistoryStore } from '@/state/session-history-store';
import { exportSession, exportSessions } from '@/storage/export';

/**
 * Shares what `message` asks for: one session's file (`exportSession`, by `id`) or the whole log
 * (`exportHistory`), which is the raw `sessions`, since an archive is the user's own files. Never
 * rejects: it runs from the native module's listener, where a throw has nowhere useful to go, and a
 * message this JS doesn't know (a newer shell) is ignored.
 */
export async function onExportRequested(message: Record<string, unknown>): Promise<void> {
  try {
    // The shell's JsBridge.export. The two share no code, so change both.
    if (message.type === 'exportSession' && typeof message.id === 'string') await exportSession(message.id);
    else if (message.type === 'exportHistory') await exportSessions(useSessionHistoryStore.getState().sessions);
  } catch (error) {
    console.warn(`Exporting for the shell (${String(message.type)}) failed`, error);
  }
}
