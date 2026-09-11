import { fireEvent, screen } from '@testing-library/react-native';
// Named import rather than the default: `i18next.changeLanguage(...)` trips the same lint rule the
// other screen tests note.
import { changeLanguage } from 'i18next';

import AnalyticsScreen from '@/app/analytics';
import type { Exercise, Session, SessionEntry } from '@/domain/types';
import { useLibraryStore } from '@/state/library-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { aLibrary, anExercise } from '@/test-support/library';
import { aSession } from '@/test-support/sessions';
import { renderScreen } from '@/test-support/render';

/**
 * The Stats screen's wiring — the parts that only exist once a screen is involved.
 *
 * The windowing, the measure, the fixed-hold rule and the calendar's arithmetic are
 * `exercise-progress.test.ts`'s and `history-stats.test.ts`'s job. What these cover is what the screen
 * adds: that kilograms reach the reader through the unit preference, that a delta is signed, that flat
 * rows fold away until asked for, which empty line shows when, and that the strings are translated.
 */
jest.mock('expo-router', () => require('@/test-support/expo-router'));

const rdl = anExercise({ id: 'rdl', name: 'Dumbbell RDL' });
const hurdler = anExercise({
  id: 'hurdler',
  name: 'Hurdler',
  type: 'timed_hold',
  config: { sets: 1, holdSecMin: 45, restSec: 30 },
} as Partial<Exercise>);

/**
 * Dated relative to now, because the screen reads the clock: both selectors default `now` to the real
 * one, so a fixed date here would drift out of the window and the rows would quietly disappear a couple
 * of months after this was written.
 */
function daysAgo(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return date.toISOString();
}

function loaded(weightKg: number): SessionEntry {
  return { exercise: 'rdl', type: 'reps', sets: [{ reps: 8, weightKg, restTakenSec: 60 }] };
}

function setUp(exercises: Exercise[], sessions: Session[]) {
  useLibraryStore.setState({ library: aLibrary({ exercises }), status: 'ready' });
  useSessionHistoryStore.setState({ sessions, status: 'ready' });
}

/** One weekly session per weight, oldest first, the last one a week ago. */
function withSessions(weights: number[]) {
  setUp(
    [rdl],
    weights.map((weightKg, index) =>
      aSession({ startedAt: daysAgo((weights.length - index) * 7), entries: [loaded(weightKg)] }),
    ),
  );
}

it('names the exercise, what it is at now, and what it moved by', async () => {
  withSessions([10, 12]);

  await renderScreen(<AnalyticsScreen />);

  // The name is user data and renders verbatim. 12 kg beat 10 kg inside the window, so it is marked.
  expect(screen.getByText('Dumbbell RDL')).toBeTruthy();
  expect(screen.getByText('12 kg · best ever')).toBeTruthy();
  expect(screen.getByText('+2 kg')).toBeTruthy();
});

it('signs a loss without dressing it as a gain or a record', async () => {
  withSessions([12, 10]);

  await renderScreen(<AnalyticsScreen />);

  expect(screen.getByText('−2 kg')).toBeTruthy();
  expect(screen.getByText('10 kg')).toBeTruthy();
});

/**
 * A flat row is a real answer, so it stays reachable — but behind one row, so a dozen of them can't
 * bury the exercise that moved. And a flat window gets words, not "+0", once it is shown.
 */
it('folds a flat exercise away until the held-steady row is opened', async () => {
  withSessions([10, 10]);

  await renderScreen(<AnalyticsScreen />);

  expect(screen.getByText('Nothing moved in the last 8 weeks.')).toBeTruthy();
  expect(screen.queryByText('Dumbbell RDL')).toBeNull();
  const toggle = screen.getByRole('button', { name: /1 held steady/ });
  expect(toggle.props.accessibilityState).toEqual({ expanded: false });

  await fireEvent.press(toggle);

  expect(screen.getByText('Dumbbell RDL')).toBeTruthy();
  expect(screen.getByText('no change')).toBeTruthy();
  expect(screen.queryByText('+0 kg')).toBeNull();
  expect(screen.getByRole('button', { name: /1 held steady/ }).props.accessibilityState).toEqual({ expanded: true });
});

// One session is not a trend, and the screen says so rather than showing an empty heading.
it('invites a second session instead of listing a single one', async () => {
  withSessions([10]);

  await renderScreen(<AnalyticsScreen />);

  expect(screen.getByText('Train something twice and its trend shows up here.')).toBeTruthy();
  expect(screen.queryByText('Dumbbell RDL')).toBeNull();
});

/**
 * Someone whose repeated work is all fixed-length stretching has trained plenty twice. "Train something
 * twice" would be telling them to do what they did, so they are told why there's no trend instead.
 */
it('explains an empty section when everything repeated was a fixed-length hold', async () => {
  const stretch = (days: number) =>
    aSession({
      startedAt: daysAgo(days),
      entries: [{ exercise: 'hurdler', type: 'timed_hold', sets: [{ holdSec: 45, restTakenSec: 30 }] }],
    });
  setUp([hurdler], [stretch(14), stretch(7)]);

  await renderScreen(<AnalyticsScreen />);

  expect(screen.getByText(/was a hold that stops at a set time/)).toBeTruthy();
  expect(screen.queryByText('Train something twice and its trend shows up here.')).toBeNull();
  expect(screen.queryByText('Hurdler')).toBeNull();
});

it('says fixed-length holds were left out when other rows are shown', async () => {
  const session = (days: number, weightKg: number) =>
    aSession({
      startedAt: daysAgo(days),
      entries: [loaded(weightKg), { exercise: 'hurdler', type: 'timed_hold', sets: [{ holdSec: 45, restTakenSec: 30 }] }],
    });
  setUp([rdl, hurdler], [session(14, 10), session(7, 12)]);

  await renderScreen(<AnalyticsScreen />);

  expect(screen.getByText(/Holds that stop at a set time can't go up/)).toBeTruthy();
  expect(screen.getByText('Dumbbell RDL')).toBeTruthy();
  expect(screen.queryByText('Hurdler')).toBeNull();
});

/**
 * The cells are hidden from a screen reader, so each week's row has to say what its cells show. The
 * current week is the one row whose contents this test controls completely.
 */
it("names each calendar week's sessions and time for a screen reader", async () => {
  const now = new Date();
  const start = new Date(now.getTime() - 60 * 60_000);
  setUp(
    [rdl],
    [aSession({ startedAt: start.toISOString(), endedAt: new Date(start.getTime() + 30 * 60_000).toISOString() })],
  );

  await renderScreen(<AnalyticsScreen />);

  expect(screen.getByLabelText(/^Week of .+: 1 session, 0h 30m$/)).toBeTruthy();
});

// History's header already says the all-time totals, one screen back, in the same words.
it('does not repeat the all-time totals', async () => {
  withSessions([10, 12]);

  await renderScreen(<AnalyticsScreen />);

  expect(screen.queryByText('All time')).toBeNull();
});

/**
 * Driven in `pt` because an English-locale assertion cannot catch a hardcoded English string — the key
 * and the literal it returns render identically. Only a rendered key path or an untranslated word
 * fails here.
 */
it('is translated', async () => {
  withSessions([10, 12]);
  await changeLanguage('pt');

  await renderScreen(<AnalyticsScreen />);

  expect(screen.getByText('Ficando mais forte')).toBeTruthy();
  expect(screen.getByText('Dias de treino · últimas 4 semanas')).toBeTruthy();
  expect(screen.getByText('Descanso')).toBeTruthy();
  expect(screen.getByText('12 kg · melhor marca')).toBeTruthy();
  // The exercise name is the user's and is never translated, sitting among strings that are.
  expect(screen.getByText('Dumbbell RDL')).toBeTruthy();
});
