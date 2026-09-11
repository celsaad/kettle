import { screen } from '@testing-library/react-native';
// Named import rather than the default: `i18next.changeLanguage(...)` trips the same lint rule the
// other screen tests note.
import { changeLanguage } from 'i18next';

import ExerciseProgressScreen from '@/app/exercise-progress';
import type { SessionEntry } from '@/domain/types';
import { useLibraryStore } from '@/state/library-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { setSearchParams } from '@/test-support/expo-router';
import { aLibrary, anExercise } from '@/test-support/library';
import { aSession } from '@/test-support/sessions';
import { renderScreen } from '@/test-support/render';

/**
 * The exercise progress screen — what a Getting stronger row opens.
 *
 * What the row and this screen measure is `exercise-progress.test.ts`'s job, including which sessions
 * count as a new best. What these cover is the screen's own wiring: that it reads the row for the
 * exercise in its route, prints it through the unit preference, lists the sessions newest first with
 * their marks, degrades to a line rather than a blank when the row is gone, and is translated. The
 * chart itself is hidden from assistive tech and drawn in SVG, so its geometry is checked in the
 * browser, not here.
 */
jest.mock('expo-router', () => require('@/test-support/expo-router'));

const rdl = anExercise({ id: 'rdl', name: 'Dumbbell RDL' });

/** Dated relative to now, because the screen reads the clock through the selector's default `now`. */
function daysAgo(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return date.toISOString();
}

function loaded(weightKg: number): SessionEntry {
  return { exercise: 'rdl', type: 'reps', sets: [{ reps: 8, weightKg, restTakenSec: 60 }] };
}

/** One weekly session per weight, oldest first, the last one a week ago. */
function withSessions(weights: number[]) {
  useLibraryStore.setState({ library: aLibrary({ exercises: [rdl] }), status: 'ready' });
  useSessionHistoryStore.setState({
    sessions: weights.map((weightKg, index) =>
      aSession({ startedAt: daysAgo((weights.length - index) * 7), entries: [loaded(weightKg)] }),
    ),
    status: 'ready',
  });
  setSearchParams({ exerciseId: 'rdl' });
}

it('shows where the exercise is now and how far it moved', async () => {
  withSessions([10, 12, 12]);

  await renderScreen(<ExerciseProgressScreen />);

  // The name is user data and renders verbatim.
  expect(screen.getByText('Dumbbell RDL')).toBeTruthy();
  expect(screen.getByText('+2 kg')).toBeTruthy();
  expect(screen.getByText(/^since /)).toBeTruthy();
});

/**
 * The list is the chart's table: every session, newest first, with the one that set a new best
 * marked. The second session beat the first; the third only matched it, so it is not marked — a tie
 * is not a record.
 */
it('lists every session newest first, marking the one that set a new best', async () => {
  withSessions([10, 12, 12]);

  await renderScreen(<ExerciseProgressScreen />);

  const values = screen
    .getAllByText(/^1[02] kg/)
    .map((node) => node.props.children)
    .map((children) => (Array.isArray(children) ? children.filter((part) => typeof part === 'string').join('') : children));
  // The headline's "12 kg" comes first, then the list: 12, 12 (the new best), 10.
  expect(values).toEqual(['12 kg', '12 kg', '12 kg', '10 kg']);
  expect(screen.getAllByText('12 kg · new best')).toHaveLength(1);
});

// A stale route — the row it was opened from has gone — still names the exercise and says why the
// screen is empty, rather than rendering a blank modal.
it('says there is nothing to chart when the exercise no longer has a row', async () => {
  withSessions([10]);

  await renderScreen(<ExerciseProgressScreen />);

  expect(screen.getByText('Dumbbell RDL')).toBeTruthy();
  expect(screen.getByText('Nothing to chart for this exercise in the last 8 weeks.')).toBeTruthy();
});

/**
 * Driven in `pt` because an English-locale assertion cannot catch a hardcoded English string — the key
 * and the literal it returns render identically.
 */
it('is translated', async () => {
  withSessions([10, 12]);
  await changeLanguage('pt');

  await renderScreen(<ExerciseProgressScreen />);

  expect(screen.getByText('Sessões · últimas 8 semanas')).toBeTruthy();
  expect(screen.getByText(/^desde /)).toBeTruthy();
  expect(screen.getByText('Nova melhor marca')).toBeTruthy();
  // The exercise name is the user's and is never translated.
  expect(screen.getByText('Dumbbell RDL')).toBeTruthy();
});
