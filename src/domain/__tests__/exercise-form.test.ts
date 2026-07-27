import { buildExercise, configToStrings, validateConfig } from '@/domain/exercise-form';
import type { ExerciseType } from '@/domain/types';

describe('validateConfig', () => {
  it('rejects a required field left blank', () => {
    expect(validateConfig('reps', { sets: '', targetRepsMin: '10', restSec: '45' })).toBe('Sets is required.');
  });

  it('rejects a required field of 0', () => {
    expect(validateConfig('hiit', { workSec: '0', restSec: '20', rounds: '4' })).toBe('Work must be at least 1.');
  });

  it('rejects a non-numeric value', () => {
    expect(validateConfig('emom', { intervalSec: 'abc', totalMinutes: '10' })).toBe('Interval must be a number.');
  });

  // The whole point of FieldDef.min: required fields default to a floor of 1, but the rest-length
  // fields declare `min: 0` because the schema's `nonnegative()` allows a zero-length rest.
  it('accepts 0 for rest-length fields (restSec, and rest exercise durationSec)', () => {
    expect(validateConfig('hiit', { workSec: '40', restSec: '0', rounds: '4' })).toBeNull();
    expect(validateConfig('reps', { sets: '3', targetRepsMin: '10', restSec: '0' })).toBeNull();
    expect(validateConfig('timed_hold', { sets: '3', holdSecMin: '15', restSec: '0' })).toBeNull();
    expect(validateConfig('rest', { durationSec: '0' })).toBeNull();
  });

  it('accepts an omitted optional field', () => {
    expect(validateConfig('emom', { intervalSec: '60', totalMinutes: '10' })).toBeNull();
    expect(validateConfig('cardio', {})).toBeNull();
  });

  it('returns null for a fully valid config', () => {
    expect(validateConfig('reps', { sets: '3', targetRepsMin: '10', targetRepsMax: '12', targetWeightKg: '60', restSec: '90' })).toBeNull();
  });
});

describe('buildExercise', () => {
  it('builds a hiit exercise and trims notes', () => {
    const exercise = buildExercise('burpees', 'Burpees', 'hiit', { workSec: '40', restSec: '20', rounds: '4' }, '  go hard  ');
    expect(exercise).toEqual({
      id: 'burpees',
      name: 'Burpees',
      type: 'hiit',
      config: { workSec: 40, restSec: 20, rounds: 4 },
      notes: 'go hard',
    });
  });

  it('omits optional fields left blank', () => {
    const exercise = buildExercise(
      'pushups',
      'Push-ups',
      'reps',
      { sets: '3', targetRepsMin: '10', targetRepsMax: '', targetWeightKg: '', restSec: '45' },
      '',
    );
    expect(exercise.type === 'reps' && exercise.config.targetRepsMax).toBeUndefined();
    expect(exercise.type === 'reps' && exercise.config.targetWeightKg).toBeUndefined();
    expect(exercise.notes).toBeUndefined();
  });

  it('builds an emom exercise with the optional target reps supplied', () => {
    const exercise = buildExercise('clean', 'Clean', 'emom', { intervalSec: '30', totalMinutes: '10', targetReps: '3' }, '');
    expect(exercise.config).toEqual({ intervalSec: 30, totalMinutes: 10, targetReps: 3 });
  });

  it('builds a bare cardio exercise with no distance or duration', () => {
    const exercise = buildExercise('walk', 'Walk', 'cardio', {}, '');
    expect(exercise.config).toEqual({ durationSec: undefined, distanceMeters: undefined });
  });

  it('builds a rest exercise', () => {
    const exercise = buildExercise('rest', 'Rest', 'rest', { durationSec: '90' }, '');
    expect(exercise.config).toEqual({ durationSec: 90 });
  });
});

describe('configToStrings round-trips with buildExercise', () => {
  const cases: { type: ExerciseType; values: Record<string, string>; notes: string }[] = [
    { type: 'hiit', values: { workSec: '40', restSec: '20', rounds: '4' }, notes: 'Fast' },
    { type: 'emom', values: { intervalSec: '60', totalMinutes: '10', targetReps: '3' }, notes: '' },
    { type: 'emom', values: { intervalSec: '90', totalMinutes: '9' }, notes: '' },
    { type: 'amrap', values: { timeCapSec: '600' }, notes: '' },
    { type: 'reps', values: { sets: '5', targetRepsMin: '3', targetRepsMax: '5', targetWeightKg: '80', restSec: '180' }, notes: 'Brace' },
    { type: 'reps', values: { sets: '3', targetRepsMin: '12', restSec: '45' }, notes: '' },
    { type: 'timed_hold', values: { sets: '4', holdSecMin: '15', holdSecMax: '25', restSec: '60' }, notes: '' },
    { type: 'cardio', values: { durationSec: '480', distanceMeters: '2000' }, notes: '' },
    { type: 'cardio', values: {}, notes: '' },
    { type: 'rest', values: { durationSec: '90' }, notes: '' },
  ];

  it.each(cases)('round-trips a $type exercise', ({ type, values, notes }) => {
    const built = buildExercise('id', 'Name', type, values, notes);
    const stringified = configToStrings(built);
    const rebuilt = buildExercise('id', 'Name', type, stringified, notes);
    expect(rebuilt).toEqual(built);
  });
});
