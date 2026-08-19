/**
 * The mute gate. `use-session-runner.test.tsx` pins *which* cue fires when; this file pins whether
 * anything is allowed to fire at all.
 *
 * `expo-audio` is mocked because a real `useAudioPlayer` dies on native-module init under jest — the
 * same reason `session-steps.ts` exists apart from the runner. One shared player stub across the
 * three cues: the question here is "did any sound come out", and attributing it to the right file is
 * the other file's job.
 */
const mockPlay = jest.fn();
const mockSeekTo = jest.fn();
const mockSetAudioModeAsync = jest.fn().mockResolvedValue(undefined);

const mockPlayers = new Map<unknown, { play: jest.Mock; seekTo: jest.Mock }>();

jest.mock('expo-audio', () => ({
  setAudioModeAsync: (...args: unknown[]) => mockSetAudioModeAsync(...args),
  // Stable per source, because the real `useAudioPlayer` is: a stub handing back a fresh object each
  // render rebuilds every `useCallback` regardless of its dependencies, which silently passed the
  // stale-closure test below whether or not the gate was in its dependency array.
  useAudioPlayer: (source: unknown) => {
    if (!mockPlayers.has(source)) mockPlayers.set(source, { play: mockPlay, seekTo: mockSeekTo });
    return mockPlayers.get(source)!;
  },
}));

jest.mock('@/storage/preferences-file', () => ({
  loadPreferences: jest.fn().mockResolvedValue(null),
  savePreferences: jest.fn().mockResolvedValue(true),
}));

import { act, renderHook } from '@testing-library/react-native';

import { useSessionSounds } from '@/hooks/use-session-sounds';
import { usePreferencesStore } from '@/state/preferences-store';

function setSounds(sessionSounds: boolean) {
  usePreferencesStore.setState((state) => ({ preferences: { ...state.preferences, sessionSounds } }));
}

beforeEach(() => {
  mockPlay.mockClear();
  mockSeekTo.mockClear();
  setSounds(true);
});

it('plays every cue while sounds are on', async () => {
  const { result } = await renderHook(() => useSessionSounds());

  await act(async () => {
    result.current.playTick();
    result.current.playExerciseChange();
    result.current.playMilestone();
  });

  expect(mockPlay).toHaveBeenCalledTimes(3);
});

// `seekTo` too, not just `play`: a rewind that still ran would leave the next unmuted cue starting
// from a position the muted run put it at.
it('plays nothing while sounds are off', async () => {
  setSounds(false);

  const { result } = await renderHook(() => useSessionSounds());

  await act(async () => {
    result.current.playTick();
    result.current.playExerciseChange();
    result.current.playMilestone();
  });

  expect(mockPlay).not.toHaveBeenCalled();
  expect(mockSeekTo).not.toHaveBeenCalled();
});

/**
 * The regression worth pinning: the callbacks close over the preference, so dropping `enabled` from
 * their dependency arrays leaves a running session playing the cues it was mounted with. Someone
 * muting mid-workout is doing it *because* of the sound they just heard, and the runner is not
 * remounted between steps — reintroducing the missing dep fails this and passes the two above.
 */
it('goes quiet as soon as the setting changes, without a remount', async () => {
  const { result } = await renderHook(() => useSessionSounds());

  await act(async () => void result.current.playTick());
  await act(async () => void setSounds(false));
  await act(async () => void result.current.playTick());

  expect(mockPlay).toHaveBeenCalledTimes(1);
});

// Muting silences the cues; it doesn't hand the phone's silent switch back. The audio mode is set
// once at mount either way, so unmuting mid-session is audible on the next tick.
it('still claims silent-mode playback while muted', async () => {
  setSounds(false);

  await renderHook(() => useSessionSounds());

  expect(mockSetAudioModeAsync).toHaveBeenCalledWith(expect.objectContaining({ playsInSilentMode: true }));
});
