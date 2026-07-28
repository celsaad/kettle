import { fireEvent, screen } from '@testing-library/react-native';
import { changeLanguage } from 'i18next';
import { Alert } from 'react-native';

import ProgramEditorScreen from '@/app/program-editor';
import type { Library, Program } from '@/domain/types';
import { useLibraryStore } from '@/state/library-store';
import { saveLibrary } from '@/storage/library-file';
import { router, setSearchParams } from '@/test-support/expo-router';
import { aLibrary, anExercise, aProgram, aWorkout } from '@/test-support/library';
import { pressAlertButton, renderScreen } from '@/test-support/render';

/**
 * The five validation gates in `save()`, and the week list's local state.
 *
 * The gates matter more than they look: a program week is addressed by the composite `week + day`,
 * which is what `resolveWorkoutForWeek` resolves against and what a session records as its position.
 * Anything that gets past these writes a program some week of which is unreachable.
 */
jest.mock('@/storage/library-file', () => ({
  loadLibrary: jest.fn(),
  saveLibrary: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('expo-router', () => require('@/test-support/expo-router'));

const savedLibrary = saveLibrary as jest.MockedFunction<typeof saveLibrary>;

function persisted(): Library {
  expect(savedLibrary).toHaveBeenCalled();
  return savedLibrary.mock.calls.at(-1)![0];
}

const pushDay = aWorkout({ id: 'push-day', name: 'Push day' });
const pullDay = aWorkout({ id: 'pull-day', name: 'Pull day' });

const withWorkouts = () => aLibrary({ exercises: [anExercise()], workouts: [pushDay, pullDay] });

/** Names the draft and adds `count` weeks, which is the shortest path to a saveable program. */
async function draftWith(count: number, name = 'Base') {
  await renderScreen(<ProgramEditorScreen />);
  await fireEvent.changeText(screen.getByPlaceholderText('e.g. 6-Week Pull Progression'), name);
  for (let week = 0; week < count; week++) await fireEvent.press(screen.getByText('+ Add week'));
}

beforeEach(() => {
  setSearchParams({});
  useLibraryStore.setState({ library: withWorkouts(), status: 'ready' });
});

describe('validation', () => {
  it('refuses a blank name', async () => {
    await renderScreen(<ProgramEditorScreen />);

    await fireEvent.press(screen.getByText('Save'));

    expect(screen.getByText('Name is required.')).toBeTruthy();
    expect(savedLibrary).not.toHaveBeenCalled();
  });

  it('refuses a program with no weeks', async () => {
    // A zero-week program is a dead end everywhere downstream: `nextWeekAfter` indexes into the
    // sorted weeks and Today's "next up" has nothing to suggest.
    await draftWith(0);

    await fireEvent.press(screen.getByText('Save'));

    expect(screen.getByText('Add at least one week.')).toBeTruthy();
    expect(savedLibrary).not.toHaveBeenCalled();
  });

  it('refuses the same week twice when neither has a day', async () => {
    await draftWith(2);
    // Both weeks now read 1, which is the composite key colliding.
    await fireEvent.press(screen.getAllByText('−')[1]);

    await fireEvent.press(screen.getByText('Save'));

    expect(screen.getByText('Week 1 is used twice — give one a different day, or remove the duplicate.')).toBeTruthy();
    expect(savedLibrary).not.toHaveBeenCalled();
  });

  it('allows the same week number when the days differ', async () => {
    // The whole point of `day`: two sessions inside one week. The key is `week + day`, so this is a
    // distinct entry rather than a duplicate — and rejecting it would break multi-session weeks.
    await draftWith(2);
    await fireEvent.press(screen.getAllByText('−')[1]);
    const dayFields = screen.getAllByPlaceholderText('e.g. Monday — only needed for 2+ sessions in one week');
    await fireEvent.changeText(dayFields[0], 'Monday');
    await fireEvent.changeText(dayFields[1], 'Thursday');

    await fireEvent.press(screen.getByText('Save'));

    expect(persisted().programs[0].weeks).toEqual([
      { week: 1, day: 'Monday', workoutId: 'push-day' },
      { week: 1, day: 'Thursday', workoutId: 'push-day' },
    ]);
  });

  it('refuses a name that yields no id', async () => {
    await draftWith(1, '🏋️');

    await fireEvent.press(screen.getByText('Save'));

    expect(screen.getByText('Could not derive an id from that name.')).toBeTruthy();
    expect(savedLibrary).not.toHaveBeenCalled();
  });

  it('keeps an existing id when the program is renamed', async () => {
    setSearchParams({ id: 'base' });
    useLibraryStore.setState({
      library: aLibrary({ workouts: [pushDay], programs: [aProgram({ id: 'base', name: 'Base' })] }),
    });
    await renderScreen(<ProgramEditorScreen />);

    await fireEvent.changeText(screen.getByDisplayValue('Base'), 'Winter block');
    await fireEvent.press(screen.getByText('Save'));

    // Sessions record `programId`, so re-deriving the id on rename would orphan the history that
    // already points at this program.
    expect(persisted().programs[0].id).toBe('base');
    expect(persisted().programs[0].name).toBe('Winter block');
  });
});

describe('weeks', () => {
  it('numbers a new week past the highest already there, not by count', async () => {
    await draftWith(2);
    // Push the second week to 5, then remove the first. Counting would call the next one 2 and
    // collide on re-add; the max+1 rule is what keeps numbering monotonic as weeks come and go.
    for (let press = 0; press < 3; press++) await fireEvent.press(screen.getAllByText('+')[1]);
    await fireEvent.press(screen.getByLabelText('Remove week 1'));

    await fireEvent.press(screen.getByText('+ Add week'));
    await fireEvent.press(screen.getByText('Save'));

    expect(persisted().programs[0].weeks.map((week) => week.week)).toEqual([5, 6]);
  });

  it('defaults a new week to the first workout', async () => {
    await draftWith(1);

    // Not left blank: an unset workout is one of the save gates, so defaulting means the common path
    // (one workout repeated across weeks) needs no picker interaction at all.
    expect(screen.getByText('Push day')).toBeTruthy();
  });

  it('renders no i18next key paths on a week card', async () => {
    await draftWith(1);

    // Three forms in this app have shipped rendering a key path where a label belonged, so this
    // asserts the class rather than one string: nothing on a week card may read like a key.
    expect(screen.queryByText(/^[a-z][a-zA-Z]*\.[a-zA-Z.]+$/)).toBeNull();
  });

  it('actually translates its week-card labels', async () => {
    // Deliberately driven in pt, because **an English-locale test cannot catch a hardcoded English
    // string** — `t('programEditor.overridesOptional')` and the literal "Overrides · optional" render
    // identically. Only switching locale tells them apart, and this file had that exact bug.
    await changeLanguage('pt');
    await renderScreen(<ProgramEditorScreen />);
    await fireEvent.press(screen.getByText('+ Adicionar semana'));

    expect(screen.getByText('Ajustes · opcional')).toBeTruthy();
    expect(screen.getByText('Semana')).toBeTruthy();
    expect(screen.queryByText('Overrides · optional')).toBeNull();
  });

  it('does not let a week number fall below one', async () => {
    await draftWith(1);

    for (let press = 0; press < 3; press++) await fireEvent.press(screen.getByText('−'));
    await fireEvent.press(screen.getByText('Save'));

    // Week numbers are 1-based throughout — `resolveWorkoutForWeek` and the schema's `positive()`
    // both assume it, so a 0th or -1st week resolves to nothing.
    expect(persisted().programs[0].weeks[0].week).toBe(1);
  });

  it('changes a week to another workout', async () => {
    await draftWith(1);

    await fireEvent.press(screen.getByText('Push day'));
    await fireEvent.press(screen.getAllByText('Pull day')[0]);
    await fireEvent.press(screen.getByText('Save'));

    expect(persisted().programs[0].weeks[0].workoutId).toBe('pull-day');
  });

  it('removes a week', async () => {
    await draftWith(2);

    await fireEvent.press(screen.getByLabelText('Remove week 1'));

    expect(screen.getByText('1 week')).toBeTruthy();
  });

  it('explains itself instead of offering an unusable add button', async () => {
    // Every week needs a workout and there are none to pick, so adding one could only produce a week
    // that fails validation. The button is disabled and the reason says where to go instead.
    useLibraryStore.setState({ library: aLibrary({ workouts: [] }) });
    await renderScreen(<ProgramEditorScreen />);

    await fireEvent.press(screen.getByText('+ Add week'));

    expect(screen.getByText('No workouts yet — create one in Build first, then come back to add weeks here.')).toBeTruthy();
    expect(screen.getByText('0 weeks')).toBeTruthy();
  });
});

it('deletes a program once confirmed', async () => {
  const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => {});
  const program: Program = aProgram({ id: 'base', name: 'Base' });
  setSearchParams({ id: 'base' });
  useLibraryStore.setState({ library: aLibrary({ workouts: [pushDay], programs: [program] }) });
  await renderScreen(<ProgramEditorScreen />);

  await fireEvent.press(screen.getByText('Delete program'));

  // No in-use guard here, unlike workouts and exercises: nothing in the library references a program,
  // so deleting one can't orphan anything. Past sessions keep their `programId` as a plain string.
  expect(savedLibrary).not.toHaveBeenCalled();
  await pressAlertButton(alert, 'destructive');

  expect(persisted().programs).toHaveLength(0);
  expect(router.back).toHaveBeenCalled();
});
