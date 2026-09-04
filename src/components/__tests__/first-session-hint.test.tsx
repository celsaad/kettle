import { screen } from '@testing-library/react-native';
// Named import rather than the default: `i18next.changeLanguage(...)` trips the same lint rule the
// other component tests note.
import { changeLanguage } from 'i18next';

import { FirstSessionHint } from '@/components/first-session-hint';
import { usePreferencesStore } from '@/state/preferences-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { aSession } from '@/test-support/sessions';
import { renderScreen } from '@/test-support/render';

/**
 * The count-in's one line for a first-timer, and the three conditions that hide it.
 *
 * Tested as the leaf rather than through `SessionCountdown`, which owns a 3-2-1 timer and pulls in
 * `use-session-sounds` — so driving it here would mean fake timers and an `expo-audio` mock to assert
 * on a string that neither of them touches. The wiring between the two is one JSX line; what can
 * actually regress is the gate, and the gate lives in this file.
 */
jest.mock('@/storage/preferences-file', () => ({
  loadPreferences: jest.fn().mockResolvedValue(null),
  savePreferences: jest.fn().mockResolvedValue(true),
}));

/** A first run as the count-in meets it: the log hydrated and empty, cues on. */
beforeEach(() => {
  useSessionHistoryStore.setState({ sessions: [], status: 'ready' });
  usePreferencesStore.setState((state) => ({ preferences: { ...state.preferences, sessionSounds: true } }));
});

function muteSounds() {
  usePreferencesStore.setState((state) => ({ preferences: { ...state.preferences, sessionSounds: false } }));
}

it('shows the hint before the first session', async () => {
  await renderScreen(<FirstSessionHint />);

  expect(screen.getByText(/Put the phone down/)).toBeTruthy();
});

/**
 * The whole point of deriving from the log rather than persisting a "seen" flag. One finished session
 * is enough — the hint is about the runner existing at all, not about mastering it.
 */
it('stays away once anything has been logged', async () => {
  useSessionHistoryStore.setState({ sessions: [aSession({ startedAt: '2026-01-05T09:00:00.000Z' })], status: 'ready' });
  await renderScreen(<FirstSessionHint />);

  expect(screen.queryByText(/Put the phone down/)).toBeNull();
});

/**
 * The sentence promises a sound. Muted, that sound does not play, and the count-in is three seconds
 * with nothing to tap — no chance to notice the advice is wrong and no way to act on it.
 */
it('stays away when session sounds are muted', async () => {
  muteSounds();
  await renderScreen(<FirstSessionHint />);

  expect(screen.queryByText(/Put the phone down/)).toBeNull();
});

/**
 * Driven in `pt`, the only way to tell a keyed string from a hardcoded English one — under an English
 * locale `t()` and the literal render identically.
 */
it('translates the hint', async () => {
  await changeLanguage('pt');
  await renderScreen(<FirstSessionHint />);

  expect(screen.getByText(/Pode largar o celular/)).toBeTruthy();
});
