import {
  buildExercise,
  configToStrings,
  fieldUnitLabel,
  CONFIG_FIELDS,
  validateBlockConfig,
  validateConfig,
} from '@/domain/exercise-form';
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

/**
 * The in-app forms write straight to the library file, so they mirror the schema's ceilings rather
 * than relying on the import path to catch them: a config the schema refuses would be written to disk
 * and then fail to parse on the next launch, sending the whole library through the reseed path.
 */
describe('validateConfig ceilings', () => {
  it('rejects a set count past the ceiling, and accepts the ceiling itself', () => {
    expect(validateConfig('reps', { sets: '501', targetRepsMin: '5', restSec: '60' })).toBe('Sets can be at most 500.');
    expect(validateConfig('reps', { sets: '500', targetRepsMin: '5', restSec: '60' })).toBeNull();
  });

  it('rejects a round count past the ceiling', () => {
    expect(validateConfig('hiit', { workSec: '40', restSec: '20', rounds: '501' })).toBe('Rounds can be at most 500.');
  });

  it('rejects a block longer than the day-length ceiling', () => {
    expect(validateConfig('emom', { intervalSec: '300', totalMinutes: '1441' })).toBe('Total can be at most 1440.');
  });

  /**
   * EMOM's bound is on the *product*, so the per-field loop can't express it — the same shape as the
   * hold range, and the easiest kind of rule to forget when mirroring the schema by hand. Both values
   * below are individually in range.
   */
  it('rejects an emom whose interval count multiplies out past the ceiling', () => {
    expect(validateConfig('emom', { intervalSec: '1', totalMinutes: '60' })).toBe(
      'That works out to more than 500 intervals. Use a longer interval or a shorter block.',
    );
    expect(validateConfig('emom', { intervalSec: '60', totalMinutes: '500' })).toBeNull();
  });
});

/**
 * The schema's `int()`, which this mirror had never carried. The cost of the gap wasn't a rejected
 * edit: the form writes straight to the library *file*, so `sets: 2.5` was saved, and the next launch
 * failed to parse it and reseeded — the user lost the whole library, not the one exercise.
 */
describe('validateConfig whole-number fields', () => {
  it.each([
    ['reps', { sets: '2.5', targetRepsMin: '5', restSec: '60' }, 'Sets must be a whole number.'],
    ['reps', { sets: '3', targetRepsMin: '5.5', restSec: '60' }, 'Target reps must be a whole number.'],
    ['timed_hold', { sets: '1.5', holdSecMin: '30', restSec: '60' }, 'Sets must be a whole number.'],
    ['hiit', { workSec: '40', restSec: '20', rounds: '4.5' }, 'Rounds must be a whole number.'],
    ['emom', { intervalSec: '60', totalMinutes: '10', targetReps: '3.5' }, 'Target reps must be a whole number.'],
  ])('rejects a fractional %s field', (type, values, message) => {
    expect(validateConfig(type as ExerciseType, values)).toBe(message);
  });

  // The fields the schema leaves as plain `number` stay fractional: a 2.5-second interval is legal.
  it('leaves the non-integer fields alone', () => {
    expect(validateConfig('hiit', { workSec: '40.5', restSec: '20.5', rounds: '4' })).toBeNull();
    expect(validateConfig('timed_hold', { sets: '3', holdSecMin: '30.5', restSec: '60' })).toBeNull();
  });
});

describe('validateConfig cross-field rules', () => {
  /**
   * The twin of the hold range, and missed for as long. It matters more now that the override editor
   * validates: a max below the min passes a per-field check, and `applyExerciseOverride` then drops
   * the whole override without saying so.
   */
  it('rejects a rep range whose maximum is below its minimum', () => {
    expect(validateConfig('reps', { sets: '3', targetRepsMin: '10', targetRepsMax: '5', restSec: '60' })).toBe(
      "The maximum reps can't be fewer than the minimum.",
    );
  });

  it('accepts a rep range that is equal at both ends, as the schema does', () => {
    expect(validateConfig('reps', { sets: '3', targetRepsMin: '5', targetRepsMax: '5', restSec: '60' })).toBeNull();
  });
});

/**
 * `target_weight` is `nonnegative()`, so 0 — "bodyweight, no added load", spelled out — is a legal
 * stored value. The form's default floor of 1 made such an exercise unsaveable, and once the override
 * editor started validating, un-overridable: the weight field is seeded from the exercise, so every
 * confirm failed on a field the user never touched.
 */
it('accepts a target weight of 0, which the schema allows', () => {
  expect(validateConfig('reps', { sets: '3', targetRepsMin: '5', targetWeightKg: '0', restSec: '60' })).toBeNull();
});

/**
 * The override editor's block branch validated nothing at all, so a negative rest reached the program
 * file and was then silently discarded by `applyBlockOverride`'s re-parse.
 */
describe('validateBlockConfig', () => {
  const valid = { rounds: '3', restBetweenExercisesSec: '15', restBetweenRoundsSec: '60' };

  it('accepts a circuit config the schema accepts, zero rest included', () => {
    expect(validateBlockConfig(valid)).toBeNull();
    expect(validateBlockConfig({ ...valid, restBetweenExercisesSec: '0', restBetweenRoundsSec: '0' })).toBeNull();
  });

  it('rejects a negative rest in either field', () => {
    expect(validateBlockConfig({ ...valid, restBetweenExercisesSec: '-5' })).toBe('Rest/exercise (sec) must be at least 0.');
    expect(validateBlockConfig({ ...valid, restBetweenRoundsSec: '-5' })).toBe('Rest/round (sec) must be at least 0.');
  });

  it('rejects a round count the schema would refuse', () => {
    expect(validateBlockConfig({ ...valid, rounds: '0' })).toBe('Rounds must be at least 1.');
    expect(validateBlockConfig({ ...valid, rounds: '501' })).toBe('Rounds can be at most 500.');
    expect(validateBlockConfig({ ...valid, rounds: '2.5' })).toBe('Rounds must be a whole number.');
  });
});

/**
 * Both circuit rest fields are `.optional()` in the schema, and the panel seeds them from the block —
 * so clearing one is how a user says "no rest here", and it had always read back as 0. Marking them
 * required turned that into a blocked save.
 */
it('treats a cleared circuit rest as absent, not as a missing required field', () => {
  expect(validateBlockConfig({ rounds: '3', restBetweenExercisesSec: '', restBetweenRoundsSec: '' })).toBeNull();
  expect(validateBlockConfig({ rounds: '3' })).toBeNull();
});
