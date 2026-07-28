import { mergeLibraries } from '@/domain/merge';
import type { Library } from '@/domain/types';

/** Merge is whole-object replace per id, not a deep field merge — these pin that. */

const base = (): Library => ({
  version: 1,
  exercises: [
    { id: 'pullups', name: 'Pull-ups', type: 'reps', config: { sets: 3, targetRepsMin: 6, restSec: 90 } },
    { id: 'lsit', name: 'L-Sit', type: 'timed_hold', config: { sets: 3, holdSecMin: 15, restSec: 60 } },
  ],
  workouts: [{ id: 'a', name: 'A', blocks: [{ kind: 'exercise', exerciseId: 'pullups' }] }],
  programs: [{ id: 'p', name: 'P', weeks: [{ week: 1, workoutId: 'a' }] }],
});

function expectOk(result: ReturnType<typeof mergeLibraries>) {
  if (!result.ok) throw new Error(`expected merge to succeed, got: ${JSON.stringify(result.error)}`);
  return result;
}

function expectError(result: ReturnType<typeof mergeLibraries>) {
  if (result.ok) throw new Error('expected merge to be refused');
  return result.error;
}

it('adds ids that did not exist', () => {
  const incoming: Library = {
    version: 1,
    exercises: [{ id: 'dips', name: 'Dips', type: 'reps', config: { sets: 4, targetRepsMin: 8, restSec: 60 } }],
    workouts: [],
    programs: [],
  };
  const result = expectOk(mergeLibraries(base(), incoming));
  expect(result.library.exercises.map((exercise) => exercise.id)).toEqual(['pullups', 'lsit', 'dips']);
  expect(result.summary.newExercises).toEqual(['dips']);
  expect(result.summary.updatedExercises).toEqual([]);
});

it('replaces a matching id wholesale rather than deep-merging its config', () => {
  const incoming: Library = {
    version: 1,
    // Same id, fewer optional fields than a deep merge would preserve.
    exercises: [
      { id: 'pullups', name: 'Weighted Pull-ups', type: 'reps', config: { sets: 5, targetRepsMin: 3, restSec: 180 } },
    ],
    workouts: [],
    programs: [],
  };
  const result = expectOk(mergeLibraries(base(), incoming));
  const merged = result.library.exercises.find((exercise) => exercise.id === 'pullups');
  expect(merged?.name).toBe('Weighted Pull-ups');
  expect(merged?.type === 'reps' && merged.config.sets).toBe(5);
  expect(result.summary.updatedExercises).toEqual(['pullups']);
  expect(result.summary.newExercises).toEqual([]);
});

it('leaves untouched ids alone', () => {
  const original = base();
  const incoming: Library = {
    version: 1,
    exercises: [{ id: 'pullups', name: 'Renamed', type: 'reps', config: { sets: 1, targetRepsMin: 1, restSec: 1 } }],
    workouts: [],
    programs: [],
  };
  const result = expectOk(mergeLibraries(original, incoming));
  expect(result.library.exercises.find((exercise) => exercise.id === 'lsit')).toEqual(
    original.exercises.find((exercise) => exercise.id === 'lsit'),
  );
  expect(result.library.workouts).toEqual(original.workouts);
  expect(result.library.programs).toEqual(original.programs);
});

it('reports new and updated ids across all three collections', () => {
  const incoming: Library = {
    version: 1,
    exercises: [{ id: 'lsit', name: 'L-Sit', type: 'timed_hold', config: { sets: 5, holdSecMin: 20, restSec: 45 } }],
    workouts: [
      { id: 'a', name: 'A2', blocks: [] },
      { id: 'b', name: 'B', blocks: [] },
    ],
    programs: [{ id: 'q', name: 'Q', weeks: [{ week: 1, workoutId: 'a' }] }],
  };
  const { summary } = expectOk(mergeLibraries(base(), incoming));
  expect(summary).toEqual({
    newExercises: [],
    updatedExercises: ['lsit'],
    newWorkouts: ['b'],
    updatedWorkouts: ['a'],
    newPrograms: ['q'],
    updatedPrograms: [],
  });
});

// The refusal reasons are descriptors, not sentences — the import screen words them, in whichever
// locale is running. These pin the fields it interpolates; the prose itself is asserted there.
describe('referential integrity', () => {
  it('rejects a workout block pointing at an unknown exercise', () => {
    const incoming: Library = {
      version: 1,
      exercises: [],
      workouts: [{ id: 'bad', name: 'Bad', blocks: [{ kind: 'exercise', exerciseId: 'ghost' }] }],
      programs: [],
    };
    expect(expectError(mergeLibraries(base(), incoming))).toEqual({
      kind: 'unknownExercise',
      workoutId: 'bad',
      exerciseId: 'ghost',
    });
  });

  it('rejects a circuit member pointing at an unknown exercise', () => {
    const incoming: Library = {
      version: 1,
      exercises: [],
      workouts: [
        {
          id: 'bad',
          name: 'Bad',
          blocks: [{ kind: 'circuit', rounds: 2, members: [{ exerciseId: 'pullups' }, { exerciseId: 'ghost' }] }],
        },
      ],
      programs: [],
    };
    expect(mergeLibraries(base(), incoming).ok).toBe(false);
  });

  it('rejects a program week pointing at an unknown workout', () => {
    const incoming: Library = {
      version: 1,
      exercises: [],
      workouts: [],
      programs: [{ id: 'bad', name: 'Bad', weeks: [{ week: 1, workoutId: 'ghost' }] }],
    };
    expect(expectError(mergeLibraries(base(), incoming))).toEqual({
      kind: 'unknownWorkout',
      programId: 'bad',
      week: 1,
      day: undefined,
      workoutId: 'ghost',
    });
  });

  // The day is what distinguishes two weeks with the same number, so it has to reach the message —
  // it used to be baked into the string here, which is why it's carried as its own field now.
  it("carries a week's day through to the refusal, when it has one", () => {
    const incoming: Library = {
      version: 1,
      exercises: [],
      workouts: [],
      programs: [{ id: 'bad', name: 'Bad', weeks: [{ week: 2, day: 'Monday', workoutId: 'ghost' }] }],
    };
    expect(expectError(mergeLibraries(base(), incoming))).toEqual({
      kind: 'unknownWorkout',
      programId: 'bad',
      week: 2,
      day: 'Monday',
      workoutId: 'ghost',
    });
  });

  it('accepts a reference satisfied by the existing library rather than the incoming one', () => {
    const incoming: Library = {
      version: 1,
      exercises: [],
      workouts: [{ id: 'c', name: 'C', blocks: [{ kind: 'exercise', exerciseId: 'lsit' }] }],
      programs: [],
    };
    expect(mergeLibraries(base(), incoming).ok).toBe(true);
  });
});
