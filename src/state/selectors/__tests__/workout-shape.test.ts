import type { Exercise, Workout } from '@/domain/types';
import { workoutShape } from '@/state/selectors/workout-shape';

/**
 * The one-line description of a workout — block count, the types in it, and an estimated duration.
 *
 * This file used to test `blockChips` and nothing else, while `workoutShape` had no direct coverage
 * anywhere. That was survivable while the next-up card led with a row of chips and the shape was the
 * caption under it. It isn't now: the chips are gone, so this string is the *only* thing on the card
 * describing what you are about to run, and "~13 min" is the part you decide on.
 *
 * Durations are estimates by construction — a `reps` block only knows its rest, and nobody can predict
 * how long eight reps take — so these pin the arithmetic that exists rather than claiming accuracy.
 */
describe('workoutShape', () => {
  const exercises: Exercise[] = [
    { id: 'pullups', name: 'Pull-ups', type: 'reps', config: { sets: 3, targetRepsMin: 6, restSec: 60 } },
    { id: 'burpees', name: 'Burpees', type: 'hiit', config: { workSec: 40, restSec: 20, rounds: 3 } },
    { id: 'plank', name: 'Plank', type: 'timed_hold', config: { sets: 2, holdSecMin: 30, holdSecMax: 60, restSec: 30 } },
    { id: 'breather', name: 'Pausa', type: 'rest', config: { durationSec: 90 } },
  ];

  const shapeOf = (blocks: Workout['blocks']) => workoutShape({ id: 'w', name: 'W', blocks }, exercises);

  it('counts blocks rather than exercises, so a circuit counts once', () => {
    const shape = shapeOf([
      { kind: 'exercise', exerciseId: 'pullups' },
      { kind: 'circuit', rounds: 2, members: [{ exerciseId: 'pullups' }, { exerciseId: 'burpees' }] },
    ]);

    expect(shape.blockCount).toBe(2);
  });

  // The types are what the summary calls "mixed reps + hiit", so duplicates would read as a longer
  // list of the same thing.
  it('lists each type once, however many blocks use it', () => {
    const shape = shapeOf([
      { kind: 'exercise', exerciseId: 'pullups' },
      { kind: 'exercise', exerciseId: 'pullups' },
      { kind: 'exercise', exerciseId: 'burpees' },
    ]);

    expect(shape.types).toEqual(['reps', 'hiit']);
  });

  /**
   * Rest is a block you can author and it takes real time, so it counts towards the estimate — but it
   * is not a *kind of training*, and a workout of squats and rests is a reps workout rather than a
   * mixed one. Both halves of that are asserted here because they pull in opposite directions.
   */
  it('counts rest towards the clock but never towards the types', () => {
    const shape = shapeOf([
      { kind: 'exercise', exerciseId: 'burpees' }, // 3 × (40 + 20) = 180s
      { kind: 'exercise', exerciseId: 'breather' }, // 90s
    ]);

    expect(shape.types).toEqual(['hiit']);
    expect(shape.estimatedMinutes).toBe(5); // 270s, rounded
  });

  it('takes a block-level rest override over the exercise’s own duration', () => {
    const shape = shapeOf([{ kind: 'exercise', exerciseId: 'breather', configOverride: { durationSec: 300 } }]);

    expect(shape.estimatedMinutes).toBe(5);
  });

  // A hold now ends itself at the top of its range, so that is the honest number to estimate from —
  // estimating the minimum would under-report every session that runs to completion.
  it('estimates a hold from the top of its range', () => {
    const shape = shapeOf([{ kind: 'exercise', exerciseId: 'plank' }]);

    // 2 × 60s hold + 1 × 30s rest = 150s
    expect(shape.estimatedMinutes).toBe(3);
  });

  it('multiplies a circuit by its rounds and counts the rest between them', () => {
    const shape = shapeOf([
      {
        kind: 'circuit',
        rounds: 3,
        members: [{ exerciseId: 'plank' }, { exerciseId: 'plank' }],
        restBetweenExercisesSec: 15,
        restBetweenRoundsSec: 60,
      },
    ]);

    // Per round: two 60s visits + one 15s gap = 135s. Three rounds = 405s, plus two 60s round rests.
    expect(shape.estimatedMinutes).toBe(9);
  });

  /**
   * A workout can reference an exercise that no longer exists — the library is hand-edited YAML, and
   * `merge.ts` replaces whole objects on import. The card still has to render, so an orphan block
   * contributes no time and no type while still counting as a block.
   */
  it('survives a block naming an exercise that is gone', () => {
    const shape = shapeOf([
      { kind: 'exercise', exerciseId: 'ghost' },
      { kind: 'exercise', exerciseId: 'burpees' },
    ]);

    expect(shape.blockCount).toBe(2);
    expect(shape.types).toEqual(['hiit']);
    expect(shape.estimatedMinutes).toBe(3);
  });

  // Never "~0 min": every workout takes some time, and a zero would read as a broken estimate rather
  // than as a short session. `reps` is the type that gets here, since it can only count its rest.
  it('never estimates zero, however little it can account for', () => {
    const shape = shapeOf([{ kind: 'exercise', exerciseId: 'ghost' }]);

    expect(shape.estimatedMinutes).toBe(1);
  });
});
