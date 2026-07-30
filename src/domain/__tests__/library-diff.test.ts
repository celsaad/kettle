import { changeLanguage } from 'i18next';

import { diffExercise, diffProgram, diffWorkout } from '@/domain/library-diff';
import type { Exercise, Program, Workout } from '@/domain/types';
import { anExercise, aProgram, aWorkout } from '@/test-support/library';

/**
 * §6's requirement, as arithmetic: merge replaces a matching id wholesale, so this is the only thing
 * standing between a re-import and a silently overwritten local tweak. §12.5 asked whether the preview
 * should be a diff or a count; this is the diff.
 */
const reps = (config: Partial<Extract<Exercise, { type: 'reps' }>['config']> = {}) =>
  anExercise({
    id: 'bench',
    name: 'Bench Press',
    type: 'reps',
    config: { sets: 5, targetRepsMin: 5, targetWeightKg: 60, restSec: 120, ...config },
  }) as Exercise;

it('names the config field that moved, and only that field', () => {
  const changes = diffExercise(reps(), reps({ sets: 6 }), 'metric');

  expect(changes).toEqual([{ label: 'Sets', from: '5', to: '6' }]);
});

it('carries the unit, so a bare number never has to be guessed at', () => {
  const changes = diffExercise(reps(), reps({ targetWeightKg: 65 }), 'metric');

  expect(changes).toEqual([{ label: 'Weight', from: '60 kg', to: '65 kg' }]);
});

/**
 * The house rule that a weight is never formatted without the preference. Shown in kilograms to
 * someone working in pounds, this line would describe a change they didn't make — and the numbers
 * wouldn't match the editor they'd open next to check.
 */
it('converts a weight into the reader’s own units', () => {
  const changes = diffExercise(reps(), reps({ targetWeightKg: 65 }), 'imperial');

  expect(changes).toEqual([{ label: 'Weight', from: '132.3 lb', to: '143.3 lb' }]);
});

it('reads a removed optional field as unset rather than as blank', () => {
  const changes = diffExercise(reps({ targetRepsMax: 8 }), reps(), 'metric');

  expect(changes).toEqual([{ label: 'Target reps (max)', from: '8', to: 'not set' }]);
});

it('reports a type change instead of the config, not alongside it', () => {
  const hold = anExercise({
    id: 'bench',
    name: 'Bench Press',
    type: 'timed_hold',
    config: { sets: 5, holdSecMin: 20, restSec: 120 },
  }) as Exercise;
  const changes = diffExercise(reps(), hold, 'metric');

  // Every key of both configs technically changed; listing them would bury the one line that explains
  // why. The type change is the explanation.
  expect(changes).toEqual([{ label: 'Type', from: 'Reps', to: 'Hold' }]);
});

it('says nothing about an identical definition', () => {
  expect(diffExercise(reps(), reps(), 'metric')).toEqual([]);
});

describe('a long value', () => {
  const long = 'Stop two reps shy of failure on every set, and hold the top for a beat';

  it('is cut short, so the numbers under it stay visible', () => {
    const changes = diffExercise({ ...reps(), notes: long }, { ...reps(), notes: 'Slower down.' }, 'metric');

    expect(changes).toEqual([
      { label: 'Notes', from: 'Stop two reps shy of failure on every set, and h…', to: 'Slower down.' },
    ]);
  });

  /**
   * The trap in truncating: cut *before* comparing, two notes that first differ past the cut look
   * identical and the change disappears from the only screen that would have shown it.
   */
  it('is still compared in full', () => {
    const changes = diffExercise(
      { ...reps(), notes: `${long} — three sets` },
      { ...reps(), notes: `${long} — four sets` },
      'metric',
    );

    expect(changes).toHaveLength(1);
    expect(changes[0].label).toBe('Notes');
  });
});

describe('workouts', () => {
  const push = (blocks: Workout['blocks']) => aWorkout({ id: 'push', name: 'Push', blocks });

  it('reports the block count and which exercise references vanished', () => {
    const before = push([
      { kind: 'exercise', exerciseId: 'bench' },
      { kind: 'exercise', exerciseId: 'dips' },
    ]);
    const after = push([{ kind: 'exercise', exerciseId: 'bench' }]);

    expect(diffWorkout(before, after)).toEqual([
      { label: 'Blocks', from: '2', to: '1' },
      { label: 'Exercises', from: 'dips', to: '—' },
    ]);
  });

  it('sees inside a circuit, so a swapped member is not invisible', () => {
    const before = push([{ kind: 'circuit', rounds: 3, members: [{ exerciseId: 'bench' }, { exerciseId: 'dips' }] }]);
    const after = push([{ kind: 'circuit', rounds: 3, members: [{ exerciseId: 'bench' }, { exerciseId: 'rows' }] }]);

    // Block count is unchanged — one circuit either way — so a count-only diff would call this no
    // change at all.
    expect(diffWorkout(before, after)).toEqual([
      { label: 'Exercises', from: 'dips', to: '—' },
      { label: 'Exercises', from: '—', to: 'rows' },
    ]);
  });
});

describe('programs', () => {
  const base = (weeks: Program['weeks']) => aProgram({ id: 'base', name: 'Base', weeks });

  it('reports the week count and which shared weeks were edited', () => {
    const before = base([
      { week: 1, workoutId: 'push' },
      { week: 2, workoutId: 'push' },
    ]);
    const after = base([
      { week: 1, workoutId: 'push' },
      { week: 2, workoutId: 'pull' },
    ]);

    expect(diffProgram(before, after)).toEqual([{ label: 'Weeks edited', from: '—', to: '2' }]);
  });

  it('counts an added week once, in the count, not twice', () => {
    const before = base([{ week: 1, workoutId: 'push' }]);
    const after = base([
      { week: 1, workoutId: 'push' },
      { week: 2, workoutId: 'push' },
    ]);

    // Week 2 is new, so it isn't also "edited" — reporting both would describe one change twice.
    expect(diffProgram(before, after)).toEqual([{ label: 'Weeks', from: '1', to: '2' }]);
  });

  it('distinguishes two same-numbered weeks by their day label', () => {
    const before = base([
      { week: 1, day: 'Monday', workoutId: 'push' },
      { week: 1, day: 'Thursday', workoutId: 'pull' },
    ]);
    const after = base([
      { week: 1, day: 'Monday', workoutId: 'push' },
      { week: 1, day: 'Thursday', workoutId: 'legs' },
    ]);

    // Keyed by (week, day) — keyed by number alone, the two would collide and the edit would read as
    // week 1 changing twice, or not at all.
    expect(diffProgram(before, after)).toEqual([{ label: 'Weeks edited', from: '—', to: '1 (Thursday)' }]);
  });
});

it('is translated', async () => {
  await changeLanguage('pt');

  expect(diffExercise(reps(), reps({ sets: 6 }), 'metric')).toEqual([{ label: 'Séries', from: '5', to: '6' }]);
  expect(diffExercise(reps({ targetRepsMax: 8 }), reps(), 'metric')[0].to).toBe('não definido');
});
