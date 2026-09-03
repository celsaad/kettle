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

import { scheduleRestDayReminder, scheduleStepCompleteNotification, stepCueContent } from '@/hooks/safe-notifications';

beforeEach(() => {
  mockSchedule.mockResolvedValue('notification-id');
});

it('makes the step cue audible and lets it through Focus', async () => {
  await scheduleStepCompleteNotification('Rest done', 'Back to it', 30, true);

  expect(mockSchedule).toHaveBeenCalledWith(
    expect.objectContaining({
      content: { title: 'Rest done', body: 'Back to it', sound: 'default', interruptionLevel: 'timeSensitive' },
    }),
  );
});

/**
 * The switch in Settings says it is the only way to quiet the cues, and this is one of them — it just
 * arrives while the app is suspended. On iOS (which is what jest runs these as) the banner still
 * arrives and still breaks through Focus; it simply doesn't ding.
 */
it('schedules a silent cue when session sounds are off', async () => {
  await scheduleStepCompleteNotification('Rest done', 'Back to it', 30, false);

  const content = mockSchedule.mock.calls[0][0].content;
  expect(content.sound).toBe(false);
  expect(content.interruptionLevel).toBe('timeSensitive');
});

/**
 * The same preference has to mean two different payloads, because `false` is not neutral on Android:
 * a boolean `sound` with no `vibrate` key makes `ExpoNotificationBuilder` call `setSilent(true)`,
 * which on O+ drops the heads-up alert as well — "regardless of channel", in its own words. A rest
 * cue landing quietly in the shade is the exact failure this feature exists to prevent, so Android
 * keeps its channel and gets no key at all.
 *
 * Pure, because jest runs every hook here as iOS: the Android arm has no other way to be seen, and a
 * module-level `Platform` mock costs a second file and leaves `expo-modules-core` warning through
 * every run.
 */
describe('stepCueContent', () => {
  it('asks for the default sound on both platforms when the switch is on', () => {
    expect(stepCueContent('t', 'b', true, 'ios').sound).toBe('default');
    expect(stepCueContent('t', 'b', true, 'android').sound).toBe('default');
  });

  it('silences iOS explicitly', () => {
    expect(stepCueContent('t', 'b', false, 'ios').sound).toBe(false);
  });

  // Absent, not present-and-undefined: the Android builder reads a boolean straight out of the
  // payload, so an undefined that survives serialization is a `false` with extra steps.
  it('sends Android no sound key at all, leaving the channel in charge', () => {
    expect(Object.keys(stepCueContent('t', 'b', false, 'android'))).not.toContain('sound');
  });

  it('always asks for the level, on both', () => {
    for (const os of ['ios', 'android']) {
      for (const on of [true, false]) {
        expect(stepCueContent('t', 'b', on, os).interruptionLevel).toBe('timeSensitive');
      }
    }
  });
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
