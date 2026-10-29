const mockExportSession = jest.fn();
const mockExportSessions = jest.fn();
jest.mock('@/storage/export', () => ({
  exportSession: (id: string) => mockExportSession(id),
  exportSessions: (sessions: unknown) => mockExportSessions(sessions),
}));

import type { Session } from '@/domain/types';
import { onExportRequested } from '@/state/export-requested';
import { useSessionHistoryStore } from '@/state/session-history-store';

/**
 * Native History's Export and Export all (#202) are RN's until M5, when export moves native with
 * Settings: the shell asks, and this shares what RN's History shared. Never rejects, since it runs
 * from the native module's listener, where a throw has nowhere to go.
 */
const aSession = (id: string, startedAt: string, entries: Session['entries'] = []): Session => ({
  version: 1,
  id,
  workout: 'w',
  program: null,
  programWeek: null,
  programDay: null,
  startedAt,
  endedAt: startedAt,
  entries,
});

beforeEach(() => {
  mockExportSession.mockReset().mockResolvedValue(undefined);
  mockExportSessions.mockReset().mockResolvedValue(undefined);
});

it('shares the one session the shell names', async () => {
  await onExportRequested({ type: 'exportSession', id: 'legs-1' });

  expect(mockExportSession).toHaveBeenCalledWith('legs-1');
  expect(mockExportSessions).not.toHaveBeenCalled();
});

// The archive is the user's own files, so it's the raw log: a session of nothing but skips is in it.
it('shares the whole raw log for Export all', async () => {
  const skipped = aSession('skipped', '2026-07-28T09:00:00.000Z', [
    { exercise: 'dips', type: 'reps', sets: [{ reps: 0, restTakenSec: 0 }] },
  ]);
  const sessions = [aSession('push-1', '2026-07-29T09:00:00.000Z'), skipped];
  useSessionHistoryStore.setState({ sessions });

  await onExportRequested({ type: 'exportHistory' });

  expect(mockExportSessions).toHaveBeenCalledWith(sessions);
});

it('ignores a session export with no id, and messages it does not know', async () => {
  await onExportRequested({ type: 'exportSession' });
  await onExportRequested({ type: 'exportSession', id: 42 });
  await onExportRequested({ type: 'navigate', url: '/history' });

  expect(mockExportSession).not.toHaveBeenCalled();
  expect(mockExportSessions).not.toHaveBeenCalled();
});

// No share sheet on this device, or the user's file gone: nothing to show from a listener, so a warning.
it('swallows a share that fails', async () => {
  const warn = jest.spyOn(console, 'warn').mockImplementation(() => {});
  mockExportSession.mockRejectedValue(new Error('Sharing is not available on this device'));

  await expect(onExportRequested({ type: 'exportSession', id: 'legs-1' })).resolves.toBeUndefined();
  expect(warn).toHaveBeenCalled();
  warn.mockRestore();
});
