import { fireEvent, screen } from '@testing-library/react-native';
// Named import rather than the default: `i18next.changeLanguage(...)` trips the same lint rule the
// other screen tests note.
import { changeLanguage } from 'i18next';

import ProgramDetailScreen from '@/app/program-detail';
import { useLibraryStore } from '@/state/library-store';
import { router, setSearchParams } from '@/test-support/expo-router';
import { aLibrary, aWorkout } from '@/test-support/library';
import { renderScreen } from '@/test-support/render';

/**
 * The week list, and what a rest week is allowed to offer.
 *
 * A rest week has nothing to start, and the screen's per-week button is what would otherwise start
 * it: `startWeek` pushes straight to `/session` with the week's params, which for a rest week lands
 * on a screen that can only tell you there is nothing to run. The absent button is the fix, and it is
 * the thing worth pinning.
 */
jest.mock('expo-router', () => require('@/test-support/expo-router'));

const program = {
  id: 'base',
  name: 'Base building',
  weeks: [
    { week: 1, day: 'Day 1', workoutId: 'push-day' },
    { week: 1, day: 'Day 2', restDay: true as const, notes: 'Walk, nothing heavy.' },
  ],
};

beforeEach(() => {
  setSearchParams({ programId: 'base' });
  useLibraryStore.setState({
    library: aLibrary({ workouts: [aWorkout({ id: 'push-day', name: 'Push day' })], programs: [program] }),
    status: 'ready',
  });
});

it('lists a training week with its workout and a way to start it', async () => {
  await renderScreen(<ProgramDetailScreen />);

  expect(screen.getByText('Push day')).toBeTruthy();
  // By label, not by text: the start control is an icon now, so the label is the only name it has —
  // and the label has to say *which* week, since a column of identical triangles otherwise announces
  // itself the same way all the way down.
  expect(screen.getByLabelText('Start Week 1 · Day 1')).toBeTruthy();
});

it('names a rest week as one instead of looking its workout up', async () => {
  await renderScreen(<ProgramDetailScreen />);

  expect(screen.getByText('Rest day')).toBeTruthy();
  expect(screen.getByText('Walk, nothing heavy.')).toBeTruthy();
});

it('offers exactly one start button — the training week has one, the rest week does not', async () => {
  await renderScreen(<ProgramDetailScreen />);

  expect(screen.getAllByLabelText(/^Start Week/)).toHaveLength(1);
});

it('starts the training week, not the rest week', async () => {
  await renderScreen(<ProgramDetailScreen />);

  await fireEvent.press(screen.getByLabelText('Start Week 1 · Day 1'));

  expect(router.push).toHaveBeenCalledWith({
    pathname: '/session',
    params: { programId: 'base', week: '1', day: 'Day 1' },
  });
});

it('is translated', async () => {
  // In pt, because an English assertion cannot tell `t('programDetail.restDay')` from a hardcoded
  // "Rest day" — both render identically.
  await changeLanguage('pt');

  await renderScreen(<ProgramDetailScreen />);

  expect(screen.getByText('Dia de descanso')).toBeTruthy();
  expect(screen.getByLabelText('Começar Semana 1 · Day 1')).toBeTruthy();
  // Day labels and notes are user data and stay in the language they were written in.
  expect(screen.getByText('Walk, nothing heavy.')).toBeTruthy();
});
