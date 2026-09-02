/**
 * The content of the background cue, which is the only cue left once iOS suspends the app.
 *
 * `use-session-runner.test.tsx` already covers *when* this is scheduled and cancelled. What is
 * untested — and what shipped wrong — is what the notification is made of: a request with no `sound`
 * produces a silent banner, which for a rest ending with the phone in a pocket is the same as no cue
 * at all. Neither field has any effect on Android (8+ takes the sound from the channel and has no
 * interruption levels), so nothing but a test or an iPhone can tell they are there.
 */
const mockSchedule = jest.fn();

jest.mock('expo-constants', () => ({
  __esModule: true,
  default: { executionEnvironment: 'standalone' },
  ExecutionEnvironment: { StoreClient: 'storeClient', Standalone: 'standalone', Bare: 'bare' },
}));

jest.mock('expo-notifications', () => ({
  setNotificationHandler: jest.fn(),
  scheduleNotificationAsync: (...args: unknown[]) => mockSchedule(...args),
  cancelScheduledNotificationAsync: jest.fn().mockResolvedValue(undefined),
  requestPermissionsAsync: jest.fn().mockResolvedValue({ granted: true }),
  SchedulableTriggerInputTypes: { TIME_INTERVAL: 'timeInterval', DATE: 'date' },
}));

import { scheduleRestDayReminder, scheduleStepCompleteNotification } from '@/hooks/safe-notifications';

beforeEach(() => {
  mockSchedule.mockResolvedValue('notification-id');
});

it('makes the step cue audible and lets it through Focus', async () => {
  await scheduleStepCompleteNotification('Rest done', 'Back to it', 30);

  expect(mockSchedule).toHaveBeenCalledWith(
    expect.objectContaining({
      content: { title: 'Rest done', body: 'Back to it', sound: 'default', interruptionLevel: 'timeSensitive' },
    }),
  );
});

/**
 * Deliberately not the same treatment. The rest-day nudge is a reminder days out, not a timer the
 * user started thirty seconds ago — `timeSensitive` on it would break through a Focus mode that was
 * switched on precisely to avoid this kind of thing.
 */
it('leaves the rest-day reminder ordinary', async () => {
  await scheduleRestDayReminder('Rest day', 'Nothing today', new Date('2026-09-05T09:00:00Z'));

  const content = mockSchedule.mock.calls[0][0].content;
  expect(content.interruptionLevel).toBeUndefined();
});
