import { act, renderHook } from '@testing-library/react-native';

// Mocked at our own boundary rather than at `expo-notifications`, so the assertions read as "what got
// scheduled" instead of "what got called on the native module".
const mockSchedule = jest.fn(() => Promise.resolve());
const mockCancel = jest.fn();
const mockRequestPermissions = jest.fn(() => Promise.resolve());
jest.mock('@/hooks/safe-notifications', () => ({
  scheduleRestDayReminder: (...args: unknown[]) => mockSchedule(...(args as [])),
  cancelRestDayReminder: () => mockCancel(),
  requestNotificationPermissions: () => mockRequestPermissions(),
}));

import type { Session } from '@/domain/types';
import { restDayReminderAt, useRestDayReminder } from '@/hooks/use-rest-day-reminder';
import { usePreferencesStore } from '@/state/preferences-store';
import { useSessionHistoryStore } from '@/state/session-history-store';

const aSession = (startedAt: string): Session => ({
  version: 1,
  id: `sess-${startedAt}`,
  workout: 'w',
  program: null,
  programWeek: null,
  programDay: null,
  startedAt,
  endedAt: startedAt,
  entries: [],
});

function setUp({ enabled, sessions }: { enabled: boolean; sessions: Session[] }) {
  usePreferencesStore.setState((state) => ({ preferences: { ...state.preferences, restDayReminder: enabled } }));
  useSessionHistoryStore.setState({ sessions, status: 'ready' });
}

beforeEach(() => {
  jest.useFakeTimers();
  jest.setSystemTime(new Date('2026-07-24T09:00:00'));
});

afterEach(() => {
  useSessionHistoryStore.setState({ sessions: [], status: 'idle' });
});

describe('restDayReminderAt', () => {
  it('lands two calendar days after the session, at the fixed hour', () => {
    expect(restDayReminderAt('2026-07-24T07:30:00')).toEqual(new Date('2026-07-26T18:00:00'));
  });

  /**
   * Steps by calendar day rather than by 48 × 3600 × 1000 ms. On a 23-hour day a fixed offset lands
   * on the wrong date, which would put the nudge a day early — the same bug `currentStreak` carries a
   * `setDate()` fix for.
   */
  it('steps whole days across a DST boundary rather than a fixed 48 hours', () => {
    const at = restDayReminderAt('2026-07-24T07:30:00');
    expect(at.getDate()).toBe(26);
    expect(at.getHours()).toBe(18);
    expect(at.getMinutes()).toBe(0);
  });
});

describe('useRestDayReminder', () => {
  it('schedules nothing and cancels while the preference is off', async () => {
    setUp({ enabled: false, sessions: [aSession('2026-07-24T07:30:00')] });

    await renderHook(() => useRestDayReminder());

    expect(mockSchedule).not.toHaveBeenCalled();
    expect(mockCancel).toHaveBeenCalled();
  });

  it('schedules one reminder for two days after the most recent session', async () => {
    setUp({ enabled: true, sessions: [aSession('2026-07-24T07:30:00')] });

    await renderHook(() => useRestDayReminder());

    expect(mockSchedule).toHaveBeenCalledTimes(1);
    expect(mockSchedule).toHaveBeenCalledWith(expect.any(String), expect.any(String), new Date('2026-07-26T18:00:00'));
    expect(mockRequestPermissions).toHaveBeenCalled();
  });

  // One pending reminder, always. Finishing a session pushes the existing one out rather than adding
  // a second — which is what the fixed identifier in safe-notifications.ts buys.
  it('reschedules rather than stacking when a new session is logged', async () => {
    setUp({ enabled: true, sessions: [aSession('2026-07-24T07:30:00')] });
    await renderHook(() => useRestDayReminder());

    // The store write is the whole trigger — the hook subscribes to it, so nothing has to re-render
    // the hook by hand. It does need its own act scope, since the update comes from outside React.
    await act(async () => {
      useSessionHistoryStore.setState({ sessions: [aSession('2026-07-25T07:30:00'), aSession('2026-07-24T07:30:00')] });
    });

    expect(mockSchedule).toHaveBeenCalledTimes(2);
    expect(mockSchedule).toHaveBeenLastCalledWith(expect.any(String), expect.any(String), new Date('2026-07-27T18:00:00'));
  });

  /**
   * The user has been away long enough that the reminder's moment has passed — and they are opening
   * the app right now, which is the thing it exists to prompt. Firing one immediately would notify
   * someone about what they are already doing.
   */
  it('schedules nothing when the reminder would already be due', async () => {
    setUp({ enabled: true, sessions: [aSession('2026-07-10T07:30:00')] });

    await renderHook(() => useRestDayReminder());

    expect(mockSchedule).not.toHaveBeenCalled();
    expect(mockCancel).toHaveBeenCalled();
  });

  it('schedules nothing on an install with no sessions at all', async () => {
    setUp({ enabled: true, sessions: [] });

    await renderHook(() => useRestDayReminder());

    expect(mockSchedule).not.toHaveBeenCalled();
  });
});
