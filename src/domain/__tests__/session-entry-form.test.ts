import {
  buildEntry,
  entryToSetForms,
  isEditableEntry,
  validateEntryForm,
  type EditableEntry,
  type SetForm,
} from '@/domain/session-entry-form';
import type { SessionEntry } from '@/domain/types';
import { UNIT_SYSTEMS, type UnitSystem } from '@/domain/units';

/**
 * The session editor's data layer. This is where the interesting failure modes live — the screen is
 * inputs and a Save button, but everything that can quietly corrupt a log entry happens here: a load
 * that drifts on a round trip through pounds, a removal that staples the wrong rest onto the wrong
 * set, an entry that survives losing its last set.
 */

const repsEntry: Extract<SessionEntry, { type: 'reps' }> = {
  exercise: 'back-squat',
  type: 'reps',
  sets: [
    { reps: 8, weightKg: 60, rpe: 8, restTakenSec: 90 },
    { reps: 8, weightKg: 60, rpe: 9, restTakenSec: 120 },
    { reps: 6, weightKg: 60, restTakenSec: 75 },
  ],
};

const holdEntry: Extract<SessionEntry, { type: 'timed_hold' }> = {
  exercise: 'plank',
  type: 'timed_hold',
  sets: [
    { holdSec: 45, restTakenSec: 60 },
    { holdSec: 40, restTakenSec: 60 },
  ],
};

describe('isEditableEntry', () => {
  it('accepts the five types the editor has fields for', () => {
    expect(isEditableEntry(repsEntry)).toBe(true);
    expect(isEditableEntry(holdEntry)).toBe(true);
    expect(isEditableEntry({ exercise: 'burpees', type: 'hiit', roundsCompleted: 4 })).toBe(true);
    expect(isEditableEntry({ exercise: 'cindy', type: 'amrap', roundsCompleted: 12, extraReps: 3 })).toBe(true);
    expect(isEditableEntry({ exercise: 'row', type: 'cardio', durationSec: 1200 })).toBe(true);
  });

  // Both are deliberate scope cuts rather than oversights, so they get pinned: an emom's per-minute
  // rep list is a list editor, and a rest entry has no number the user would recognise as theirs.
  it('refuses emom and rest', () => {
    expect(isEditableEntry({ exercise: 'power-clean', type: 'emom', minutes: [{ reps: 3 }, { reps: 3 }] })).toBe(false);
    expect(isEditableEntry({ exercise: 'rest', type: 'rest', restTakenSec: 120 })).toBe(false);
  });
});

describe('entryToSetForms', () => {
  it('gives a per-set type one row per logged set', () => {
    const forms = entryToSetForms(repsEntry, 'metric');
    expect(forms).toHaveLength(3);
    expect(forms[0].values).toEqual({ reps: '8', weightKg: '60', rpe: '8' });
  });

  // The distinction the log actually makes: a bodyweight set has no weight, and an unrecorded RPE is
  // not the same as an RPE of 0. Both have to come back as blank rather than as a number.
  it('shows an absent load and an absent RPE as empty, not as zero', () => {
    const forms = entryToSetForms(repsEntry, 'metric');
    expect(forms[2].values.rpe).toBe('');

    const bodyweight = entryToSetForms(
      { exercise: 'pull-ups', type: 'reps', sets: [{ reps: 8, restTakenSec: 60 }] },
      'metric',
    );
    expect(bodyweight[0].values.weightKg).toBe('');
  });

  it('converts the load for display and carries the stored kilograms alongside it', () => {
    const forms = entryToSetForms(repsEntry, 'imperial');
    expect(forms[0].values.weightKg).toBe('132.3');
    // The guard that makes the round trip lossless — see the drift test below.
    expect(forms[0].weightKg).toBe(60);
  });

  it('keeps each set’s own rest, which has no field and still has to survive a save', () => {
    expect(entryToSetForms(repsEntry, 'metric').map((form) => form.restTakenSec)).toEqual([90, 120, 75]);
  });

  it('gives the one-result types a single row', () => {
    expect(entryToSetForms({ exercise: 'burpees', type: 'hiit', roundsCompleted: 4 }, 'metric')).toEqual([
      { values: { roundsCompleted: '4' }, restTakenSec: 0 },
    ]);
  });
});

describe('validateEntryForm', () => {
  const formsFor = (values: Record<string, string>): SetForm[] => [{ values, restTakenSec: 0 }];

  it('accepts a valid entry', () => {
    expect(validateEntryForm('reps', entryToSetForms(repsEntry, 'metric'))).toBeNull();
  });

  it('rejects a required field left blank', () => {
    expect(validateEntryForm('reps', formsFor({ reps: '', weightKg: '60', rpe: '8' }))).toBe('Reps is required.');
  });

  it('rejects a non-numeric value', () => {
    expect(validateEntryForm('timed_hold', formsFor({ holdSec: 'ages' }))).toBe('Hold must be a number.');
  });

  /**
   * The floor is 0 here where the exercise form's is 1, and that difference is load-bearing: the
   * exercise form describes a plan, where zero sets is meaningless, but this one describes what
   * happened — and the runner can and does log a set failed at zero reps.
   */
  it('accepts a logged zero', () => {
    expect(validateEntryForm('reps', formsFor({ reps: '0', weightKg: '60', rpe: '' }))).toBeNull();
    expect(validateEntryForm('hiit', formsFor({ roundsCompleted: '0' }))).toBeNull();
  });

  it('rejects a negative', () => {
    expect(validateEntryForm('reps', formsFor({ reps: '-2', weightKg: '', rpe: '' }))).toBe('Reps must be at least 0.');
  });

  // RPE is a 1–10 scale, so it is the one field with a ceiling. A mis-tap that turns 8 into 88 is
  // exactly the class of mistake this whole feature exists to fix, and shouldn't be fixable *into*.
  it('rejects an RPE above the scale but accepts its ends', () => {
    expect(validateEntryForm('reps', formsFor({ reps: '8', weightKg: '', rpe: '88' }))).toBe('RPE can be at most 10.');
    expect(validateEntryForm('reps', formsFor({ reps: '8', weightKg: '', rpe: '10' }))).toBeNull();
    expect(validateEntryForm('reps', formsFor({ reps: '8', weightKg: '', rpe: '1' }))).toBeNull();
    expect(validateEntryForm('reps', formsFor({ reps: '8', weightKg: '', rpe: '0' }))).toBe('RPE must be at least 1.');
  });

  it('rejects a fractional count but allows a fractional load', () => {
    expect(validateEntryForm('reps', formsFor({ reps: '8.5', weightKg: '', rpe: '' }))).toBe('Reps must be a whole number.');
    expect(validateEntryForm('reps', formsFor({ reps: '8', weightKg: '62.5', rpe: '' }))).toBeNull();
  });

  // Both cardio fields are individually optional — the format allows either alone — so only a
  // cross-field rule can catch an entry that records neither.
  it('rejects a cardio entry with neither duration nor distance', () => {
    expect(validateEntryForm('cardio', formsFor({ durationSec: '', distanceMeters: '' }))).toBe(
      'Enter a duration or a distance.',
    );
    expect(validateEntryForm('cardio', formsFor({ durationSec: '1200', distanceMeters: '' }))).toBeNull();
    expect(validateEntryForm('cardio', formsFor({ durationSec: '', distanceMeters: '5000' }))).toBeNull();
  });

  it('checks every row, not just the first', () => {
    const forms = entryToSetForms(repsEntry, 'metric');
    forms[2] = { ...forms[2], values: { ...forms[2].values, reps: '' } };
    expect(validateEntryForm('reps', forms)).toBe('Reps is required.');
  });
});

describe('buildEntry', () => {
  it('round-trips an untouched entry unchanged', () => {
    expect(buildEntry(repsEntry, entryToSetForms(repsEntry, 'metric'), 'metric')).toEqual(repsEntry);
    expect(buildEntry(holdEntry, entryToSetForms(holdEntry, 'metric'), 'metric')).toEqual(holdEntry);
  });

  it('applies an edited value and leaves the other sets alone', () => {
    const forms = entryToSetForms(repsEntry, 'metric');
    forms[1] = { ...forms[1], values: { ...forms[1].values, reps: '5' } };
    const built = buildEntry(repsEntry, forms, 'metric');

    expect(built).toEqual({
      ...repsEntry,
      sets: [repsEntry.sets[0], { ...repsEntry.sets[1], reps: 5 }, repsEntry.sets[2]],
    });
  });

  it('clears an optional field back to absent when it is emptied', () => {
    const forms = entryToSetForms(repsEntry, 'metric');
    forms[0] = { ...forms[0], values: { ...forms[0].values, rpe: '', weightKg: '' } };
    const built = buildEntry(repsEntry, forms, 'metric');

    expect(built?.type === 'reps' && built.sets[0]).toEqual({
      reps: 8,
      weightKg: undefined,
      rpe: undefined,
      restTakenSec: 90,
    });
  });

  /**
   * The drift guard, and the reason `SetForm` carries the stored kilograms per row rather than letting
   * the caller supply them.
   *
   * 60 kg displays as 132.3 lb; converting that back lands on 60.01, not 60. So editing an unrelated
   * field on a set logged in kilograms, while the app is set to pounds, used to be enough to rewrite
   * its load. Reintroduce the bug by dropping `form.weightKg` in `buildEntry` and this fails with
   * 60.01.
   */
  it('keeps the stored load exactly when the load field was not touched, in either unit', () => {
    for (const unitSystem of UNIT_SYSTEMS) {
      const forms = entryToSetForms(repsEntry, unitSystem);
      forms[0] = { ...forms[0], values: { ...forms[0].values, reps: '9' } };
      const built = buildEntry(repsEntry, forms, unitSystem);

      expect(built?.type === 'reps' && built.sets[0].weightKg).toBe(60);
    }
  });

  it('converts a load the user did type back to kilograms', () => {
    const forms = entryToSetForms(repsEntry, 'imperial');
    forms[0] = { ...forms[0], values: { ...forms[0].values, weightKg: '135' } };
    const built = buildEntry(repsEntry, forms, 'imperial');

    expect(built?.type === 'reps' && built.sets[0].weightKg).toBe(61.23);
  });

  /**
   * Removal is an array splice, so after one removal a row's position no longer matches the logged set
   * it came from. Anything reading rest back off `original.sets[index]` at save time would put set 3's
   * 75 seconds onto what is now set 2 — silently, and only in the file.
   */
  it('keeps each surviving set’s own rest after a removal', () => {
    const forms = entryToSetForms(repsEntry, 'metric').filter((_, index) => index !== 1);
    const built = buildEntry(repsEntry, forms, 'metric');

    expect(built?.type === 'reps' && built.sets).toEqual([
      { reps: 8, weightKg: 60, rpe: 8, restTakenSec: 90 },
      { reps: 6, weightKg: 60, rpe: undefined, restTakenSec: 75 },
    ]);
  });

  // The caller's signal to drop the entry rather than write an exercise recorded as having done
  // nothing — which is what a `sets: []` entry would be, and it would still show up in History.
  it('returns null when the last row is removed', () => {
    expect(buildEntry(repsEntry, [], 'metric')).toBeNull();
    expect(buildEntry({ exercise: 'burpees', type: 'hiit', roundsCompleted: 4 }, [], 'metric')).toBeNull();
  });

  it('round-trips the one-result types', () => {
    const cases: EditableEntry[] = [
      { exercise: 'burpees', type: 'hiit', roundsCompleted: 4 },
      { exercise: 'cindy', type: 'amrap', roundsCompleted: 12, extraReps: 3 },
      { exercise: 'cindy', type: 'amrap', roundsCompleted: 12, extraReps: undefined },
      { exercise: 'row', type: 'cardio', durationSec: 1200, distanceMeters: undefined },
      { exercise: 'row', type: 'cardio', durationSec: undefined, distanceMeters: 5000 },
    ];
    for (const entry of cases) {
      expect(buildEntry(entry, entryToSetForms(entry, 'metric'), 'metric')).toEqual(entry);
    }
  });

  it('never changes which exercise an entry is for', () => {
    const forms = entryToSetForms(repsEntry, 'metric');
    forms[0] = { ...forms[0], values: { ...forms[0].values, reps: '1' } };
    expect(buildEntry(repsEntry, forms, 'metric')?.exercise).toBe('back-squat');
  });
});

/** Display precision differs per system, so the load is the one field whose round trip is unit-dependent. */
describe('the load round trip, per unit system', () => {
  it.each(UNIT_SYSTEMS)('survives an untouched save in %s', (unitSystem: UnitSystem) => {
    const entry: EditableEntry = {
      exercise: 'bench',
      type: 'reps',
      sets: [{ reps: 5, weightKg: 100, restTakenSec: 180 }],
    };
    expect(buildEntry(entry, entryToSetForms(entry, unitSystem), unitSystem)).toEqual(entry);
  });
});
