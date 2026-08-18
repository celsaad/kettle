import { screen } from '@testing-library/react-native';
// Named import rather than the default: `i18next.changeLanguage(...)` trips the same lint rule the
// other screen tests note.
import { changeLanguage } from 'i18next';

import AnalyticsScreen from '@/app/analytics';
import { useLibraryStore } from '@/state/library-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { aLibrary, anExercise } from '@/test-support/library';
import { aSession } from '@/test-support/sessions';
import { renderScreen } from '@/test-support/render';

/**
 * The Stats screen's progress rows — the half of it that answers "am I getting stronger" rather than
 * counting what has already happened.
 *
 * The windowing and the measure are `exercise-progress.test.ts`'s job. What these cover is the wiring
 * that only exists once a screen is involved: that kilograms reach the reader through the unit
 * preference, that a delta is signed rather than printed bare, and that the strings are translated.
 */
jest.mock('expo-router', () => require('@/test-support/expo-router'));

const rdl = anExercise({ id: 'rdl', name: 'Dumbbell RDL' });

/**
 * Dated relative to now, because the screen reads the clock: `exerciseProgress` defaults its `now` to
 * the real one, so a fixed date here would drift out of the eight-week window and the rows would
 * quietly disappear a couple of months after this was written.
 */
function daysAgo(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return date.toISOString();
}

function withSessions(weights: number[]) {
  useLibraryStore.setState({ library: aLibrary({ exercises: [rdl] }), status: 'ready' });
  useSessionHistoryStore.setState({
    sessions: weights.map((weightKg, index) =>
      aSession({
        startedAt: daysAgo((weights.length - index) * 7),
        entries: [{ exercise: 'rdl', type: 'reps', sets: [{ reps: 8, weightKg, restTakenSec: 60 }] }],
      }),
    ),
    status: 'ready',
  });
}

it('names the exercise, what it is at now, and what it moved by', async () => {
  withSessions([10, 12]);

  await renderScreen(<AnalyticsScreen />);

  // The name is user data and renders verbatim.
  expect(screen.getByText('Dumbbell RDL')).toBeTruthy();
  expect(screen.getByText('12 kg')).toBeTruthy();
  expect(screen.getByText('+2 kg')).toBeTruthy();
});

/**
 * A flat window gets words, not "+0". Zero change is a real answer to the question this screen asks,
 * and a signed zero reads as a rounding artefact.
 */
it('says a flat exercise is flat rather than printing a signed zero', async () => {
  withSessions([10, 10]);

  await renderScreen(<AnalyticsScreen />);

  expect(screen.getByText('no change')).toBeTruthy();
  expect(screen.queryByText('+0 kg')).toBeNull();
});

it('signs a loss without dressing it as a gain', async () => {
  withSessions([12, 10]);

  await renderScreen(<AnalyticsScreen />);

  expect(screen.getByText('−2 kg')).toBeTruthy();
});

// One session is not a trend, and the screen says so rather than showing an empty heading.
it('invites a second session instead of listing a single one', async () => {
  withSessions([10]);

  await renderScreen(<AnalyticsScreen />);

  expect(screen.getByText('Train something twice and its trend shows up here.')).toBeTruthy();
  expect(screen.queryByText('Dumbbell RDL')).toBeNull();
});

/**
 * Driven in `pt` because an English-locale assertion cannot catch a hardcoded English string — the key
 * and the literal it returns render identically. Only a rendered key path or an untranslated word
 * fails here, and this screen gained six new strings.
 */
it('is translated', async () => {
  withSessions([10, 12]);
  await changeLanguage('pt');

  await renderScreen(<AnalyticsScreen />);

  expect(screen.getByText('Ficando mais forte')).toBeTruthy();
  // The exercise name is the user's and is never translated, sitting among strings that are.
  expect(screen.getByText('Dumbbell RDL')).toBeTruthy();
});
