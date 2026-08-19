import { fireEvent, screen } from '@testing-library/react-native';
// Named import rather than the default: `i18next.changeLanguage(...)` trips the same lint rule the
// other screen tests note.
import { changeLanguage } from 'i18next';

import WorkoutsScreen from '@/app/(tabs)/index';
import type { Session } from '@/domain/types';
import { useLibraryStore } from '@/state/library-store';
import { usePreferencesStore } from '@/state/preferences-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { aLibrary, anExercise, aWorkout } from '@/test-support/library';
import { renderScreen } from '@/test-support/render';

/**
 * The workout list on the Workouts tab: ordering, searching, and the two empty states. The next-up
 * card that sits above it in the same list header is `workouts-next-up.test.tsx`.
 *
 * This is the one of the three list screens where ordering is easiest to read back — the rows are
 * plain workout names with nothing else that moves — so the wiring is pinned here once rather than
 * three times. `list-sort.test.ts` owns the comparators themselves; what's left for a screen test is
 * that the tap reaches them, that the choice persists, and that the control knows when to stay away.
 */
jest.mock('@/storage/preferences-file', () => ({
  loadPreferences: jest.fn().mockResolvedValue(null),
  savePreferences: jest.fn().mockResolvedValue(true),
}));

jest.mock('expo-router', () => require('@/test-support/expo-router'));

const zercher = aWorkout({ id: 'zercher', name: 'Zercher day' });
const bench = aWorkout({ id: 'bench', name: 'Bench day' });
const never = aWorkout({ id: 'never', name: 'Amrap day' });

function aSession(workout: string, startedAt: string): Session {
  return {
    version: 1,
    id: startedAt,
    workout,
    program: null,
    programWeek: null,
    programDay: null,
    startedAt,
    endedAt: null,
    entries: [],
  };
}

beforeEach(() => {
  useLibraryStore.setState({
    library: aLibrary({ exercises: [anExercise()], workouts: [zercher, bench, never] }),
    status: 'ready',
  });
  useSessionHistoryStore.setState({
    sessions: [aSession('bench', '2026-07-20T09:00:00.000Z'), aSession('zercher', '2026-07-01T09:00:00.000Z')],
    status: 'ready',
  });
  usePreferencesStore.setState({
    status: 'ready',
    preferences: {
      unitSystem: 'metric',
      themePreference: 'system',
      restDayReminder: false,
      sessionSounds: true,
      backupFolderUri: null,
    },
  });
});

/**
 * The card headings top to bottom — the list's order as a reader sees it, read straight off the tree
 * rather than re-derived from the fixtures.
 *
 * By `testID` rather than by text, which is what it used when this screen was the Build tab. The
 * next-up card now sits in the list header and renders a workout name of its own, so a `/day$/` text
 * query returns four nodes for three rows — and *which* extra name it returns rotates by calendar day,
 * because with no program active `workoutOfTheDay` picks by `Date.now()`. Matching the rows directly
 * is what keeps this suite about ordering rather than about what day it is.
 */
function order(): string[] {
  return screen
    .getAllByTestId('workout-card-name')
    .map((node) => node.props.children)
    .filter((child): child is string => typeof child === 'string');
}

/**
 * The list has exactly one order now: the one the file is written in.
 *
 * It used to be three, behind a pill row (`custom` / A–Z / Recent) that this suite covered case by
 * case. The control is gone — sorting a list that already has a search box was chrome on every one of
 * these screens — so file order is not a default any more, it is the whole behaviour, and this is the
 * test that pins it. A library is hand-written and hand-shared, so the order in the file is the order
 * its author meant.
 */
it('lists workouts in the order the library file wrote them', async () => {
  await renderScreen(<WorkoutsScreen />);

  expect(order()).toEqual(['Zercher day', 'Bench day', 'Amrap day']);
});

/**
 * Driven in `pt` because an English-locale assertion cannot catch a hardcoded English string — the
 * key and the literal it returns render identically. Only a rendered key path or an untranslated word
 * fails here.
 *
 * The placeholder carries the count, which is what the deleted line under the title used to say, so
 * this also pins the pluralised form actually reaching the field.
 */
it('is translated', async () => {
  await changeLanguage('pt');

  await renderScreen(<WorkoutsScreen />);

  expect(screen.getByPlaceholderText('Buscar 3 treinos')).toBeTruthy();
});

describe('search', () => {
  it('narrows the list to matching names', async () => {
    await renderScreen(<WorkoutsScreen />);

    await fireEvent.changeText(screen.getByPlaceholderText(/^Search [0-9]+ workouts?$/), 'bench');

    expect(order()).toEqual(['Bench day']);
  });

  it('ignores case and matches anywhere in the name', async () => {
    await renderScreen(<WorkoutsScreen />);

    await fireEvent.changeText(screen.getByPlaceholderText(/^Search [0-9]+ workouts?$/), 'DAY');

    expect(order()).toHaveLength(3);
  });

  /**
   * The regression this feature creates if the two empty states aren't told apart: "No workouts yet —
   * build one from exercises in your library" is right on a fresh install and actively wrong in front
   * of someone with three workouts who mistyped one.
   */
  it('says nothing matched rather than claiming there are no workouts', async () => {
    await renderScreen(<WorkoutsScreen />);

    await fireEvent.changeText(screen.getByPlaceholderText(/^Search [0-9]+ workouts?$/), 'zzz');

    expect(screen.getByText('Nothing matched')).toBeTruthy();
    expect(screen.queryByText('No workouts yet')).toBeNull();
  });

  it('still tells a new library it has no workouts yet', async () => {
    useLibraryStore.setState({ library: aLibrary({ exercises: [anExercise()], workouts: [] }), status: 'ready' });

    await renderScreen(<WorkoutsScreen />);

    expect(screen.getByText('No workouts yet')).toBeTruthy();
    expect(screen.queryByText('Nothing matched')).toBeNull();
  });

  // Nothing to search in, so the box would only be something else to tap past.
  it('offers no search box on an empty library', async () => {
    useLibraryStore.setState({ library: aLibrary({ exercises: [anExercise()], workouts: [] }), status: 'ready' });

    await renderScreen(<WorkoutsScreen />);

    expect(screen.queryByPlaceholderText(/^Search [0-9]+ workouts?$/)).toBeNull();
  });

  // The box keys off the whole library, not the visible subset: tied to what's on screen it would
  // vanish the moment a query narrowed things, moving the list under the finger that's typing.
  //
  // Its count does not narrow either, which the placeholder pattern here allows but the assertion
  // below pins exactly — three workouts stay three while the list under them shows one. A count that
  // tracked the results would be describing the search to someone who cannot see it, since a
  // placeholder is hidden the moment there is text in the field.
  it('keeps the search box, and its count, while a search narrows the list to one', async () => {
    await renderScreen(<WorkoutsScreen />);

    await fireEvent.changeText(screen.getByPlaceholderText(/^Search [0-9]+ workouts?$/), 'bench');

    expect(order()).toEqual(['Bench day']);
    expect(screen.getByPlaceholderText('Search 3 workouts')).toBeTruthy();
  });
});
