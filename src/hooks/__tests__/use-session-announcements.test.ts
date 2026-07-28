import { renderHook } from '@testing-library/react-native';
import { AccessibilityInfo } from 'react-native';

import { useSessionAnnouncements } from '@/hooks/use-session-announcements';

/**
 * The dedupe is the whole feature. The derived announcement string is rebuilt as the runner
 * re-renders — once a second while a timer runs — and speaking it each time would interrupt a screen
 * reader roughly sixty times a minute, mid-set. Only a genuine step change is worth an utterance.
 */
let announce: jest.SpyInstance;

beforeEach(() => {
  jest.spyOn(AccessibilityInfo, 'isScreenReaderEnabled').mockResolvedValue(true);
  jest
    .spyOn(AccessibilityInfo, 'addEventListener')
    .mockReturnValue({ remove: jest.fn() } as unknown as ReturnType<typeof AccessibilityInfo.addEventListener>);
  announce = jest.spyOn(AccessibilityInfo, 'announceForAccessibility').mockImplementation(() => {});
});

it('speaks a new step once', async () => {
  const { rerender } = await renderHook<void, { text: string | null }>(({ text }) => useSessionAnnouncements(text), {
    initialProps: { text: 'Pull-ups, set 1 of 4, target 8 reps' },
  });
  await rerender({ text: 'Pull-ups, set 1 of 4, target 8 reps' });

  expect(announce).toHaveBeenCalledTimes(1);
  expect(announce).toHaveBeenCalledWith('Pull-ups, set 1 of 4, target 8 reps');
});

it('speaks again when the step actually changes', async () => {
  const { rerender } = await renderHook<void, { text: string | null }>(({ text }) => useSessionAnnouncements(text), {
    initialProps: { text: 'Pull-ups, set 1 of 4, target 8 reps' },
  });
  await rerender({ text: 'Rest, 90 seconds' });

  expect(announce).toHaveBeenCalledTimes(2);
  expect(announce).toHaveBeenLastCalledWith('Rest, 90 seconds');
});

it('says nothing when there is no current step', async () => {
  await renderHook(() => useSessionAnnouncements(null));
  expect(announce).not.toHaveBeenCalled();
});

it('stays silent when no screen reader is running', async () => {
  jest.spyOn(AccessibilityInfo, 'isScreenReaderEnabled').mockResolvedValue(false);
  await renderHook(() => useSessionAnnouncements('Pull-ups, set 1 of 4, target 8 reps'));
  expect(announce).not.toHaveBeenCalled();
});
