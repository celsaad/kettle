import { findProgramWeek, programWeekNumbers, resolveWorkoutForWeek } from '@/domain/program';
import type { Exercise, Library, Program } from '@/domain/types';

const exercises: Exercise[] = [
  { id: 'pushups', name: 'Push-ups', type: 'reps', config: { sets: 3, targetRepsMin: 10, restSec: 45 } },
  { id: 'lsit', name: 'L-Sit', type: 'timed_hold', config: { sets: 3, holdSecMin: 15, restSec: 60 } },
  { id: 'squats', name: 'Squats', type: 'reps', config: { sets: 4, targetRepsMin: 8, restSec: 60 } },
];

const library: Library = {
  version: 1,
  exercises,
  workouts: [
    {
      id: 'full',
      name: 'Full',
      blocks: [
        { kind: 'exercise', exerciseId: 'pushups' },
        {
          kind: 'circuit',
          id: 'finisher',
          rounds: 3,
          restBetweenExercisesSec: 15,
          restBetweenRoundsSec: 60,
          members: [{ exerciseId: 'lsit' }, { exerciseId: 'squats' }],
        },
      ],
    },
  ],
  programs: [],
};

// Weeks deliberately out of week-number order, and week 2 split across two days, to exercise dedupe + sort.
const program: Program = {
  id: 'prog',
  name: 'Prog',
  weeks: [
    {
      week: 3,
      workoutId: 'full',
      overrides: [
        { kind: 'exercise', exerciseId: 'pushups', config: { sets: 6 } },
        { kind: 'block', blockId: 'finisher', config: { rounds: 2 } },
      ],
    },
    { week: 1, workoutId: 'full' },
    { week: 2, workoutId: 'full', day: 'Monday' },
    { week: 2, workoutId: 'full', day: 'Tuesday' },
  ],
};

describe('findProgramWeek', () => {
  it('finds a week by number when no day is given', () => {
    expect(findProgramWeek(program, 1)?.week).toBe(1);
  });

  it('disambiguates same-numbered weeks by day', () => {
    expect(findProgramWeek(program, 2, 'Monday')?.day).toBe('Monday');
    expect(findProgramWeek(program, 2, 'Tuesday')?.day).toBe('Tuesday');
  });

  it('returns undefined for a week that does not exist', () => {
    expect(findProgramWeek(program, 99)).toBeUndefined();
  });

  it('returns undefined when the day does not match any entry for that week', () => {
    expect(findProgramWeek(program, 2, 'Friday')).toBeUndefined();
  });
});

describe('programWeekNumbers', () => {
  it('dedupes repeated week numbers and returns them ascending', () => {
    expect(programWeekNumbers(program)).toEqual([1, 2, 3]);
  });
});

describe('resolveWorkoutForWeek', () => {
  it('resolves the base workout unchanged when the week has no overrides', () => {
    const resolved = resolveWorkoutForWeek(program, 1, library);
    expect(resolved?.workout.id).toBe('full');
    expect(resolved?.workout.blocks).toEqual(library.workouts[0].blocks);
    expect(resolved?.exercises).toEqual(exercises);
  });

  it('applies an exercise override, leaving other exercises untouched', () => {
    const resolved = resolveWorkoutForWeek(program, 3, library);
    const pushups = resolved?.exercises.find((exercise) => exercise.id === 'pushups');
    expect(pushups?.type === 'reps' && pushups.config.sets).toBe(6);

    // Not just equal in value — the same object reference, proving the map didn't touch them.
    const lsit = resolved?.exercises.find((exercise) => exercise.id === 'lsit');
    const squats = resolved?.exercises.find((exercise) => exercise.id === 'squats');
    expect(lsit).toBe(exercises.find((exercise) => exercise.id === 'lsit'));
    expect(squats).toBe(exercises.find((exercise) => exercise.id === 'squats'));
  });

  it('applies a circuit block override targeted by the block id, leaving other blocks untouched', () => {
    const resolved = resolveWorkoutForWeek(program, 3, library);
    const finisher = resolved?.workout.blocks.find((block) => block.kind === 'circuit');
    expect(finisher?.kind === 'circuit' && finisher.rounds).toBe(2);
    // restBetweenExercisesSec wasn't part of the override, so it should survive the merge unchanged.
    expect(finisher?.kind === 'circuit' && finisher.restBetweenExercisesSec).toBe(15);

    const exerciseBlock = resolved?.workout.blocks.find((block) => block.kind === 'exercise');
    expect(exerciseBlock).toBe(library.workouts[0].blocks[0]);
  });

  it('returns null for an unknown week', () => {
    expect(resolveWorkoutForWeek(program, 99, library)).toBeNull();
  });

  it('resolves the correct same-numbered week when day disambiguates', () => {
    const resolved = resolveWorkoutForWeek(program, 2, library, 'Tuesday');
    expect(resolved?.workout.id).toBe('full');
  });
});
