import { fireEvent, screen } from '@testing-library/react-native';
// Named import rather than the default: `i18next.changeLanguage(...)` trips the same lint rule the
// other screen tests note.
import { changeLanguage } from 'i18next';

import BuildScreen from '@/app/(tabs)/build';
import { DEFAULT_LIST_SORTS } from '@/domain/preferences';
import type { Session } from '@/domain/types';
import { useLibraryStore } from '@/state/library-store';
import { usePreferencesStore } from '@/state/preferences-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { aLibrary, anExercise, aWorkout } from '@/test-support/library';
import { renderScreen } from '@/test-support/render';

/**
 * The list-order control, from the tap to what the list actually shows.
 *
 * Build is the one of the three screens where the ordering is easiest to read back — the cards are
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
    preferences: { unitSystem: 'metric', themePreference: 'system', listSort: DEFAULT_LIST_SORTS },
  });
});

/**
 * The card headings top to bottom — the list's order as a reader sees it, read straight off the tree
 * rather than re-derived from the fixtures. Every fixture name ends in "day", and nothing else on the
 * screen does.
 */
function order(): string[] {
  return screen
    .getAllByText(/day$/)
    .map((node) => node.props.children)
    .filter((child): child is string => typeof child === 'string');
}

it('lists workouts in the order the library file wrote them by default', async () => {
  await renderScreen(<BuildScreen />);

  expect(order()).toEqual(['Zercher day', 'Bench day', 'Amrap day']);
});

it('reorders by name when A–Z is chosen', async () => {
  await renderScreen(<BuildScreen />);

  await fireEvent.press(screen.getByText('A–Z'));

  expect(order()).toEqual(['Amrap day', 'Bench day', 'Zercher day']);
});

// Never-trained workouts sink rather than disappearing — the thing a "recent" list most easily gets
// wrong, since a brand-new workout is exactly the one you're about to run.
it('puts the most recently trained first and the never-trained last', async () => {
  await renderScreen(<BuildScreen />);

  await fireEvent.press(screen.getByText('Recent'));

  expect(order()).toEqual(['Bench day', 'Zercher day', 'Amrap day']);
});

it('remembers the choice, so the next visit opens in the same order', async () => {
  await renderScreen(<BuildScreen />);

  await fireEvent.press(screen.getByText('A–Z'));

  expect(usePreferencesStore.getState().preferences.listSort.workouts).toBe('name');
});

// A control that can't change anything is worse than an absent one, and one workout is the state a
// new install is closest to.
it('stays away entirely when there is nothing to order', async () => {
  useLibraryStore.setState({ library: aLibrary({ exercises: [anExercise()], workouts: [zercher] }), status: 'ready' });

  await renderScreen(<BuildScreen />);

  expect(screen.queryByText('A–Z')).toBeNull();
});

/**
 * Driven in `pt` because an English-locale assertion cannot catch a hardcoded English string —
 * `t('sort.name')` and the literal it returns render identically. Only a rendered key path or an
 * untranslated word fails here.
 */
it('is translated', async () => {
  await changeLanguage('pt');

  await renderScreen(<BuildScreen />);

  expect(screen.getByText('Ordenar')).toBeTruthy();
  expect(screen.getByText('Sua ordem')).toBeTruthy();
  expect(screen.getByText('Recentes')).toBeTruthy();
});
