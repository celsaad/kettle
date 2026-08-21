import { fireEvent, screen } from '@testing-library/react-native';
// Named import rather than the default: `i18next.changeLanguage(...)` trips the same lint rule the
// other screen tests note.
import { changeLanguage } from 'i18next';

import ProgramDetailScreen from '@/app/program-detail';
import { useLibraryStore } from '@/state/library-store';
import { router, setSearchParams } from '@/test-support/expo-router';
import { aLibrary, anExercise, aProgram, aWorkout } from '@/test-support/library';
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

/**
 * `unknownKey` is a per-key condition, and reporting it per-override inverted this PR's own thesis:
 * the screen said a change wasn't happening while it happened.
 */
describe('an override where only some keys are unknown', () => {
  it('marks only the key that does nothing', async () => {
    useLibraryStore.setState({
      library: aLibrary({
        exercises: [anExercise({ id: 'pull-ups', name: 'Pull-ups' })],
        workouts: [
          aWorkout({
            id: 'w',
            name: 'W',
            blocks: [
              {
                kind: 'circuit',
                id: 'finisher',
                rounds: 3,
                members: [{ exerciseId: 'pull-ups' }, { exerciseId: 'pull-ups' }],
              },
            ],
          }),
        ],
        programs: [
          aProgram({
            id: 'p',
            name: 'P',
            weeks: [
              {
                week: 1,
                workoutId: 'w',
                overrides: [{ kind: 'block', blockId: 'finisher', config: { rounds: 2, exercisez: 2 } }],
              },
            ],
          }),
        ],
      }),
      status: 'ready',
    });
    setSearchParams({ programId: 'p' });
    await renderScreen(<ProgramDetailScreen />);

    // `rounds: 2` genuinely reaches the runner, so it must not carry a "not applied" note.
    expect(screen.getByText('Circuit (finisher): rounds → 2')).toBeTruthy();
    expect(screen.getByText(/exercisez → 2.*there is no such setting/)).toBeTruthy();
  });
});

/**
 * An exercise override was resolved against the whole library rather than against the week's own
 * workout, so one naming an exercise the week doesn't run merged cleanly, changed nothing, and
 * rendered as applied — the failure this function exists to report, from the one direction it wasn't
 * checking. The block branch has always scoped to the workout.
 *
 * Two taps away: changing a week's workout in the editor keeps the overrides it already had.
 */
it('marks an exercise override the week does not run as not applied', async () => {
  const pullUps = anExercise({ id: 'pull-ups', name: 'Pull-ups' });
  const dips = anExercise({ id: 'dips', name: 'Dips' });
  useLibraryStore.setState({
    library: aLibrary({
      exercises: [pullUps, dips],
      // The week runs pull-ups; the override names dips, which is in the library but not in the week.
      workouts: [aWorkout({ id: 'w', name: 'W', blocks: [{ kind: 'exercise', exerciseId: 'pull-ups' }] })],
      programs: [
        aProgram({
          id: 'p',
          name: 'P',
          weeks: [{ week: 1, workoutId: 'w', overrides: [{ kind: 'exercise', exerciseId: 'dips', config: { sets: 5 } }] }],
        }),
      ],
    }),
    status: 'ready',
  });
  setSearchParams({ programId: 'p' });
  await renderScreen(<ProgramDetailScreen />);

  expect(screen.getByText('not applied — nothing in this week matches')).toBeTruthy();
});

it('still names an exercise the week does run', async () => {
  const pullUps = anExercise({ id: 'pull-ups', name: 'Pull-ups' });
  useLibraryStore.setState({
    library: aLibrary({
      exercises: [pullUps],
      workouts: [aWorkout({ id: 'w', name: 'W', blocks: [{ kind: 'exercise', exerciseId: 'pull-ups' }] })],
      programs: [
        aProgram({
          id: 'p',
          name: 'P',
          weeks: [
            { week: 1, workoutId: 'w', overrides: [{ kind: 'exercise', exerciseId: 'pull-ups', config: { sets: 5 } }] },
          ],
        }),
      ],
    }),
    status: 'ready',
  });
  setSearchParams({ programId: 'p' });
  await renderScreen(<ProgramDetailScreen />);

  expect(screen.getByText('Pull-ups: sets → 5')).toBeTruthy();
  expect(screen.queryByText(/not applied/)).toBeNull();
});

// Driven in pt: an English assertion cannot tell `t('overrideEditor.circuitTitle')` from the literal
// 'Circuit' it replaced, which is how that string sat hardcoded on a line of otherwise-translated ones.
it('translates the circuit label', async () => {
  await changeLanguage('pt');
  const pullUps = anExercise({ id: 'pull-ups', name: 'Pull-ups' });
  useLibraryStore.setState({
    library: aLibrary({
      exercises: [pullUps],
      workouts: [
        aWorkout({
          id: 'w',
          name: 'W',
          blocks: [
            {
              kind: 'circuit',
              id: 'finisher',
              rounds: 3,
              members: [{ exerciseId: 'pull-ups' }, { exerciseId: 'pull-ups' }],
            },
          ],
        }),
      ],
      programs: [
        aProgram({
          id: 'p',
          name: 'P',
          weeks: [{ week: 1, workoutId: 'w', overrides: [{ kind: 'block', blockId: 'finisher', config: { rounds: 2 } }] }],
        }),
      ],
    }),
    status: 'ready',
  });
  setSearchParams({ programId: 'p' });
  await renderScreen(<ProgramDetailScreen />);

  expect(screen.getByText(/Circuito \(finisher\): rounds → 2/)).toBeTruthy();
});
