import { fireEvent, screen } from '@testing-library/react-native';
// Named import rather than the default: `i18next.changeLanguage(...)` trips the same lint rule the
// other screen tests note.
import { changeLanguage } from 'i18next';

import ProgramsScreen from '@/app/(tabs)/programs';
import { useLibraryStore } from '@/state/library-store';
import { usePreferencesStore } from '@/state/preferences-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { aLibrary, aProgram, aWorkout } from '@/test-support/library';
import { renderScreen } from '@/test-support/render';

/**
 * Programs' search, and the empty state it can now produce.
 *
 * The first-run card here is the one most worth not showing by mistake: it invites you to go read the
 * YAML guide, which is a strange answer to a mistyped program name.
 */
jest.mock('@/storage/preferences-file', () => ({
  loadPreferences: jest.fn().mockResolvedValue(null),
  savePreferences: jest.fn().mockResolvedValue(true),
}));

jest.mock('expo-router', () => require('@/test-support/expo-router'));

const base = aProgram({ id: 'base', name: 'Base building' });
const peak = aProgram({ id: 'peak', name: 'Peak week' });

beforeEach(() => {
  useLibraryStore.setState({
    library: aLibrary({ workouts: [aWorkout()], programs: [base, peak] }),
    status: 'ready',
  });
  useSessionHistoryStore.setState({ sessions: [], status: 'ready' });
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

it('lists every program', async () => {
  await renderScreen(<ProgramsScreen />);

  expect(screen.getByText('Base building')).toBeTruthy();
  expect(screen.getByText('Peak week')).toBeTruthy();
});

it('narrows the list to matching names', async () => {
  await renderScreen(<ProgramsScreen />);

  await fireEvent.changeText(screen.getByPlaceholderText(/^Search [0-9]+ programs?$/), 'peak');

  expect(screen.getByText('Peak week')).toBeTruthy();
  expect(screen.queryByText('Base building')).toBeNull();
});

it('says nothing matched rather than offering to teach the YAML format', async () => {
  await renderScreen(<ProgramsScreen />);

  await fireEvent.changeText(screen.getByPlaceholderText(/^Search [0-9]+ programs?$/), 'zzz');

  expect(screen.getByText('Nothing matched')).toBeTruthy();
  expect(screen.queryByText('No programs yet')).toBeNull();
});

it('still tells a library with no programs that it has none yet', async () => {
  useLibraryStore.setState({ library: aLibrary({ workouts: [aWorkout()], programs: [] }), status: 'ready' });

  await renderScreen(<ProgramsScreen />);

  expect(screen.getByText('No programs yet')).toBeTruthy();
  expect(screen.queryByPlaceholderText(/^Search [0-9]+ programs?$/)).toBeNull();
});

// An English-locale assertion can't catch a hardcoded English string, so the new copy is checked in pt.
it('is translated', async () => {
  await changeLanguage('pt');

  await renderScreen(<ProgramsScreen />);
  await fireEvent.changeText(screen.getByPlaceholderText(/^Buscar [0-9]+ programas?$/), 'zzz');

  expect(screen.getByText('Nada encontrado')).toBeTruthy();
});

// `program.weeks` holds one entry per week *and day*, so counting entries over-reported: a four-week
// program with notes or overrides on two days of every week claimed "8 weeks with notes or
// overrides" next to a "Weeks 1–4" range. Reintroduce the bug by counting entries instead of
// distinct week numbers and this fails on the 8.
it('counts distinct weeks with notes or overrides, not the day entries inside them', async () => {
  const everyWeekTwice = aProgram({
    id: 'block',
    name: 'Four week block',
    weeks: [1, 2, 3, 4].flatMap((week) => [
      { week, day: 'Day 1', workoutId: 'push-day', notes: 'Push a little harder.' },
      { week, day: 'Day 3', workoutId: 'push-day', notes: 'And again.' },
    ]),
  });
  useLibraryStore.setState({
    library: aLibrary({ workouts: [aWorkout()], programs: [everyWeekTwice] }),
    status: 'ready',
  });

  await renderScreen(<ProgramsScreen />);

  expect(screen.getByText('Weeks 1–4')).toBeTruthy();
  expect(screen.getByText('4 weeks with notes or overrides')).toBeTruthy();
});
