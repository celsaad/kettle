import { buildExercise, configToStrings, fieldUnitLabel, CONFIG_FIELDS, validateConfig } from '@/domain/exercise-form';
import type { Exercise, ExerciseType } from '@/domain/types';
import { UNIT_SYSTEMS, type UnitSystem } from '@/domain/units';

const metric = { unitSystem: 'metric' as const };

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

  /**
   * The other end of `min`, and the one that isn't taste: the runner builds one step per set, per
   * round and per EMOM minute, so an in-app editor writing 200000 of them produced a workout that
   * could not be started at all. Mirrors the ceilings in schema.ts, which the same numbers come from.
   */
  it('rejects a count past its ceiling', () => {
    expect(validateConfig('reps', { sets: '501', targetRepsMin: '10', restSec: '45' })).toBe('Sets can be at most 500.');
    expect(validateConfig('timed_hold', { sets: '501', holdSecMin: '15', restSec: '30' })).toBe('Sets can be at most 500.');
    expect(validateConfig('hiit', { workSec: '40', restSec: '20', rounds: '501' })).toBe('Rounds can be at most 500.');
    expect(validateConfig('emom', { intervalSec: '60', totalMinutes: '1441' })).toBe('Total can be at most 1440.');
  });

  it('accepts a count at exactly its ceiling', () => {
    expect(validateConfig('reps', { sets: '500', targetRepsMin: '10', restSec: '45' })).toBeNull();
    expect(validateConfig('hiit', { workSec: '40', restSec: '20', rounds: '500' })).toBeNull();
    expect(validateConfig('emom', { intervalSec: '60', totalMinutes: '1440' })).toBeNull();
  });

  // Only the counts that drive step expansion carry a max — a rest or a hold length is a duration,
  // and capping one would be the form being stricter than the format for no reason.
  it('leaves the fields with no ceiling uncapped', () => {
    expect(validateConfig('reps', { sets: '3', targetRepsMin: '999999', restSec: '999999' })).toBeNull();
    expect(validateConfig('amrap', { timeCapSec: '999999' })).toBeNull();
  });

  it('returns null for a fully valid config', () => {
    expect(
      validateConfig('reps', { sets: '3', targetRepsMin: '10', targetRepsMax: '12', targetWeightKg: '60', restSec: '90' }),
    ).toBeNull();
  });

  /**
   * The per-field loop can't express these, and until the hold target became optional neither was
   * checked on this path at all — so the editor could write a config the importer would refuse.
   */
  describe('the hold range, which needs cross-field rules', () => {
    it('accepts a hold with no target, which is how a max-effort hold is written', () => {
      expect(validateConfig('timed_hold', { sets: '3', restSec: '60' })).toBeNull();
    });

    it('rejects a maximum with no minimum, which is a range with one end', () => {
      expect(validateConfig('timed_hold', { sets: '3', holdSecMax: '25', restSec: '60' })).toBe(
        'A hold range needs a minimum as well as a maximum.',
      );
    });

    // The schema has always refused this; the form quietly took it.
    it('rejects a maximum below the minimum', () => {
      expect(validateConfig('timed_hold', { sets: '3', holdSecMin: '25', holdSecMax: '15', restSec: '60' })).toBe(
        "The maximum hold can't be shorter than the minimum.",
      );
    });

    it('accepts a maximum equal to the minimum, as the schema does', () => {
      expect(validateConfig('timed_hold', { sets: '3', holdSecMin: '20', holdSecMax: '20', restSec: '60' })).toBeNull();
    });
  });
});

describe('buildExercise', () => {
  it('builds a hiit exercise and trims notes', () => {
    const exercise = buildExercise(
      'burpees',
      'Burpees',
      'hiit',
      { workSec: '40', restSec: '20', rounds: '4' },
      '  go hard  ',
      metric,
    );
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
      metric,
    );
    expect(exercise.type === 'reps' && exercise.config.targetRepsMax).toBeUndefined();
    expect(exercise.type === 'reps' && exercise.config.targetWeightKg).toBeUndefined();
    expect(exercise.notes).toBeUndefined();
  });

  it('builds an emom exercise with the optional target reps supplied', () => {
    const exercise = buildExercise(
      'clean',
      'Clean',
      'emom',
      { intervalSec: '30', totalMinutes: '10', targetReps: '3' },
      '',
      metric,
    );
    expect(exercise.config).toEqual({ intervalSec: 30, totalMinutes: 10, targetReps: 3 });
  });

  it('builds a bare cardio exercise with no distance or duration', () => {
    const exercise = buildExercise('walk', 'Walk', 'cardio', {}, '', metric);
    expect(exercise.config).toEqual({ durationSec: undefined, distanceMeters: undefined });
  });

  it('builds a rest exercise', () => {
    const exercise = buildExercise('rest', 'Rest', 'rest', { durationSec: '90' }, '', metric);
    expect(exercise.config).toEqual({ durationSec: 90 });
  });
});

describe('configToStrings round-trips with buildExercise', () => {
  const cases: { type: ExerciseType; values: Record<string, string>; notes: string }[] = [
    { type: 'hiit', values: { workSec: '40', restSec: '20', rounds: '4' }, notes: 'Fast' },
    { type: 'emom', values: { intervalSec: '60', totalMinutes: '10', targetReps: '3' }, notes: '' },
    { type: 'emom', values: { intervalSec: '90', totalMinutes: '9' }, notes: '' },
    { type: 'amrap', values: { timeCapSec: '600' }, notes: '' },
    {
      type: 'reps',
      values: { sets: '5', targetRepsMin: '3', targetRepsMax: '5', targetWeightKg: '80', restSec: '180' },
      notes: 'Brace',
    },
    { type: 'reps', values: { sets: '3', targetRepsMin: '12', restSec: '45' }, notes: '' },
    { type: 'timed_hold', values: { sets: '4', holdSecMin: '15', holdSecMax: '25', restSec: '60' }, notes: '' },
    { type: 'cardio', values: { durationSec: '480', distanceMeters: '2000' }, notes: '' },
    { type: 'cardio', values: {}, notes: '' },
    { type: 'rest', values: { durationSec: '90' }, notes: '' },
  ];

  // Run in both unit systems: the imperial pass is what pins the conversion, since the weight field is
  // the only one that changes on the way through the form.
  const matrix = UNIT_SYSTEMS.flatMap((unitSystem) => cases.map((testCase) => ({ ...testCase, unitSystem })));

  it.each(matrix)('round-trips a $type exercise in $unitSystem', ({ type, values, notes, unitSystem }) => {
    const built = buildExercise('id', 'Name', type, values, notes, { unitSystem });
    const stringified = configToStrings(built, unitSystem);
    const previousWeightKg = built.type === 'reps' ? built.config.targetWeightKg : undefined;
    const rebuilt = buildExercise('id', 'Name', type, stringified, notes, { unitSystem, previousWeightKg });
    expect(rebuilt).toEqual(built);
  });
});

/**
 * The unit boundary. Storage is always kilograms (see domain/units.ts); everything here is about the
 * form being the only place that isn't.
 */
describe('weight conversion in the exercise form', () => {
  const weightField = CONFIG_FIELDS.reps.find((field) => field.key === 'targetWeightKg')!;

  const repsWith = (targetWeightKg: number): Exercise => ({
    id: 'bench',
    name: 'Bench',
    type: 'reps',
    config: { sets: 5, targetRepsMin: 5, targetWeightKg, restSec: 120 },
  });

  const build = (weight: string, unitSystem: UnitSystem, previousWeightKg?: number) =>
    buildExercise('bench', 'Bench', 'reps', { sets: '5', targetRepsMin: '5', targetWeightKg: weight, restSec: '120' }, '', {
      unitSystem,
      previousWeightKg,
    });

  it('labels the weight field per the active system, and leaves every other unit alone', () => {
    expect(fieldUnitLabel(weightField, 'metric')).toBe('kg');
    expect(fieldUnitLabel(weightField, 'imperial')).toBe('lb');
    expect(
      fieldUnitLabel(
        CONFIG_FIELDS.reps.find((field) => field.key === 'restSec')!,
        'imperial',
      ),
    ).toBe('sec');
  });

  it('shows a stored kilogram value in pounds', () => {
    expect(configToStrings(repsWith(60), 'imperial').targetWeightKg).toBe('132.3');
    expect(configToStrings(repsWith(60), 'metric').targetWeightKg).toBe('60');
  });

  it('stores a pound entry as kilograms', () => {
    const built = build('135', 'imperial');
    expect(built.type === 'reps' && built.config.targetWeightKg).toBe(61.23);
  });

  /**
   * The pairing that matters to the person typing: a value entered in pounds has to come back as the
   * number they typed, not as 134.99. That's why the display rounds to 0.1 lb while storage keeps
   * 0.01 kg — see the precision note in domain/units.ts.
   */
  it.each(['135', '45', '2.5', '225', '137.5'])('redisplays %s lb as itself after a trip through kg', (entered) => {
    const built = build(entered, 'imperial');
    expect(configToStrings(built, 'imperial').targetWeightKg).toBe(entered);
  });

  /**
   * The other direction is lossy under any fixed rounding, so it's solved by not converting at all:
   * 100 kg shows as 220.5 lb, and 220.5 lb converts back to 100.02. Without previousWeightKg, opening
   * an exercise in pounds and saving an unrelated change would quietly move its weight.
   */
  it('leaves a kilogram-authored weight untouched when the field was not edited', () => {
    const shown = configToStrings(repsWith(100), 'imperial').targetWeightKg;
    expect(shown).toBe('220.5');

    const built = build(shown, 'imperial', 100);
    expect(built.type === 'reps' && built.config.targetWeightKg).toBe(100);
  });

  it('does convert once the field actually changes', () => {
    const built = build('225', 'imperial', 100);
    expect(built.type === 'reps' && built.config.targetWeightKg).toBe(102.06);
  });

  it('keeps a blank weight blank rather than storing a zero load', () => {
    const built = build('', 'imperial', 100);
    expect(built.type === 'reps' && built.config.targetWeightKg).toBeUndefined();
  });
});
