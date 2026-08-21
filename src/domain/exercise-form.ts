/**
 * The exercise config form's data model and validation: which fields each exercise type exposes, how
 * a domain Exercise round-trips to the string map a form holds, and the validation the in-app forms
 * must run themselves.
 *
 * Lived in exercise-editor.tsx, which meant two other modules imported logic from a *screen* to reuse
 * it (new-exercise-form.tsx's quick-add and program-override-editor.tsx's override form). None of it
 * touches React, so it belongs with the domain — and being importable without pulling in a screen is
 * what makes it testable.
 */
import { t } from 'i18next';
import { emomIntervalCount, MaxRounds, MaxSets, MaxTotalMinutes } from '@/domain/schema';
import type { Exercise, ExerciseType } from '@/domain/types';
import { fromDisplayWeight, toDisplayWeight, type UnitSystem } from '@/domain/units';

/**
 * `label` is an i18next key rather than display text, resolved with `t()` at the point of use
 * — this module has no React tree to hook `useTranslation()` into, and there's no in-app language
 * switch (the device locale is read once at startup, see `i18n/index.ts`), so resolving eagerly here
 * is safe. `min` is the smallest accepted value, defaulting to 1, `max` the largest (unbounded by
 * default), and `integer` mirrors the schema's `int()` — see validateConfig.
 *
 * `unit` is the suffix shown next to the label. `'weight'` is the one entry that isn't literal: it
 * renders as kg or lb per the user's preference, and its value is converted on the way in and out.
 */
export type FieldDef = {
  key: string;
  label: string;
  unit?: string;
  optional?: boolean;
  min?: number;
  max?: number;
  integer?: boolean;
};

/** The form's only unit-converted field, named once so the three places that special-case it agree. */
const WEIGHT_FIELD = 'targetWeightKg';

export type UnitContext = {
  unitSystem: UnitSystem;
  /**
   * The kilograms the weight field was initialised from, when editing something that already exists.
   *
   * Lets an *untouched* field keep its stored value exactly rather than being rewritten by a lossy
   * display round-trip: 100 kg shows as 220.5 lb, and converting that back lands on 100.02. Without
   * this, opening an exercise in pounds and saving an unrelated change would quietly edit its weight —
   * and, in the override editor, invent an override that the user never asked for.
   */
  previousWeightKg?: number;
};

/** The unit suffix to render beside a field's label, translated where it's a real unit of measure. */
export function fieldUnitLabel(field: FieldDef, unitSystem: UnitSystem): string | undefined {
  if (field.unit !== 'weight') return field.unit;
  return t(unitSystem === 'imperial' ? 'units.lb' : 'units.kg');
}

export const TYPE_OPTIONS: { type: ExerciseType; label: string }[] = [
  { type: 'reps', label: 'exerciseForm.type.reps' },
  { type: 'timed_hold', label: 'exerciseForm.type.hold' },
  { type: 'hiit', label: 'exerciseForm.type.hiit' },
  { type: 'emom', label: 'exerciseForm.type.emom' },
  { type: 'amrap', label: 'exerciseForm.type.amrap' },
  { type: 'cardio', label: 'exerciseForm.type.cardio' },
  { type: 'rest', label: 'exerciseForm.type.rest' },
];

export const CONFIG_FIELDS: Record<ExerciseType, FieldDef[]> = {
  hiit: [
    { key: 'workSec', label: 'exerciseForm.field.work', unit: 'sec' },
    { key: 'restSec', label: 'exerciseForm.field.rest', unit: 'sec', min: 0 },
    { key: 'rounds', label: 'exerciseForm.field.rounds', max: MaxRounds, integer: true },
  ],
  emom: [
    { key: 'intervalSec', label: 'exerciseForm.field.interval', unit: 'sec' },
    { key: 'totalMinutes', label: 'exerciseForm.field.total', unit: 'min', max: MaxTotalMinutes },
    { key: 'targetReps', label: 'exerciseForm.field.targetReps', optional: true, integer: true },
  ],
  amrap: [{ key: 'timeCapSec', label: 'exerciseForm.field.timeCap', unit: 'sec' }],
  reps: [
    { key: 'sets', label: 'exerciseForm.field.sets', max: MaxSets, integer: true },
    { key: 'targetRepsMin', label: 'exerciseForm.field.targetReps', integer: true },
    { key: 'targetRepsMax', label: 'exerciseForm.field.targetRepsMax', optional: true, integer: true },
    // `min: 0` because the schema's `nonnegative()` allows it: a weight of 0 is "bodyweight, no added
    // load" spelled out, and the default floor of 1 made an exercise stored that way unsaveable — and,
    // once the override editor started validating, un-overridable.
    { key: WEIGHT_FIELD, label: 'exerciseForm.field.weight', unit: 'weight', optional: true, min: 0 },
    { key: 'restSec', label: 'exerciseForm.field.rest', unit: 'sec', min: 0 },
  ],
  timed_hold: [
    { key: 'sets', label: 'exerciseForm.field.sets', max: MaxSets, integer: true },
    // Optional, like cardio's duration below: left blank it's a max-effort hold that counts up until
    // you end it, which is the only way to express one.
    { key: 'holdSecMin', label: 'exerciseForm.field.hold', unit: 'sec', optional: true },
    { key: 'holdSecMax', label: 'exerciseForm.field.holdMax', unit: 'sec', optional: true },
    { key: 'restSec', label: 'exerciseForm.field.rest', unit: 'sec', min: 0 },
  ],
  cardio: [
    { key: 'durationSec', label: 'exerciseForm.field.duration', unit: 'sec', optional: true },
    { key: 'distanceMeters', label: 'exerciseForm.field.distance', unit: 'm', optional: true },
  ],
  rest: [{ key: 'durationSec', label: 'exerciseForm.field.duration', unit: 'sec', min: 0 }],
};

/**
 * The per-field half of both validators below: the checks a `FieldDef` can express on its own.
 *
 * Shared rather than written twice because the drift is the whole problem — this loop is the mirror
 * of `schema.ts` for every path that writes to the library without parsing it, and a rule that
 * exists in one copy and not the other is invisible until a user's file fails to load.
 */
function validateFields(fields: FieldDef[], values: Record<string, string>): string | null {
  for (const field of fields) {
    const raw = values[field.key]?.trim() ?? '';
    const label = t(field.label);
    if (!raw) {
      if (field.optional) continue;
      return t('exerciseForm.error.required', { label });
    }
    const parsed = Number(raw);
    if (!Number.isFinite(parsed)) return t('exerciseForm.error.mustBeNumber', { label });
    const min = field.min ?? 1;
    if (parsed < min) return t('exerciseForm.error.mustBeAtLeast', { label, min });
    // Only the counts the runner expands one step per unit of carry a max — see MaxSets in schema.ts
    // for why an unbounded one is a workout that can't be started rather than a long one.
    if (field.max !== undefined && parsed > field.max)
      return t('exerciseForm.error.mustBeAtMost', { label, max: field.max });
    // The schema's `int()`. A fractional `sets` is refused on import, which meant the editor could
    // write one and the *next launch* would fail to parse the library and reseed it — the form's
    // silence here cost the user their whole file, not their edit.
    if (field.integer && !Number.isInteger(parsed)) return t('exerciseForm.error.mustBeWhole', { label });
  }
  return null;
}

/**
 * Mirrors the zod config constraints in `domain/schema.ts` for the in-app forms, which write straight
 * to the library store and so never pass through the schema — imported YAML can't produce a 0-set
 * exercise, but before this the editor happily could, and a workout made of those resolved to zero
 * runnable steps (the "Nothing to run" case in session.tsx).
 *
 * Required fields default to a minimum of 1, matching the schema's `positive()`, and the set, round
 * and minute counts carry the same ceiling it does. The rest-length fields carry an explicit `min: 0`
 * because the schema allows a zero-length rest and rejecting that would be stricter than the format
 * itself. Returns the first problem found, or null if it's valid.
 *
 * The per-field loop can't see the schema's *cross-field* refinements, which is how the hold range
 * went unchecked on this path entirely: an editor could write `hold_sec_max` below `hold_sec_min`,
 * or without one at all, and the store took both — the same file would then be refused on import.
 * Every such rule is enforced after the loop, and there are three of them: EMOM's derived interval
 * count, the rep range and the hold range.
 */
export function validateConfig(type: ExerciseType, values: Record<string, string>): string | null {
  const fieldError = validateFields(CONFIG_FIELDS[type], values);
  if (fieldError) return fieldError;

  // EMOM's ceiling is on the *product*, so the per-field loop can't express it. Missing it here would
  // not cost a rejected import: this form writes straight to the library file, so a config the schema
  // refuses is written to disk and then fails to parse on the next launch, which sends the user's
  // whole library through the reseed path in library-file.ts.
  if (type === 'emom') {
    const intervalSec = Number(values.intervalSec);
    const totalMinutes = Number(values.totalMinutes);
    if (Number.isFinite(intervalSec) && Number.isFinite(totalMinutes) && intervalSec > 0) {
      if (emomIntervalCount(intervalSec, totalMinutes) > MaxRounds) {
        return t('exerciseForm.error.tooManyIntervals', { max: MaxRounds });
      }
    }
  }

  if (type === 'reps') {
    const repsMin = values.targetRepsMin?.trim() ?? '';
    const repsMax = values.targetRepsMax?.trim() ?? '';
    // The twin of the hold range below, and missed for as long. Unlike the hold's, a bare `max` is
    // legal here — `target_reps_min` is required — so the only rule is the ordering.
    if (repsMin && repsMax && Number(repsMax) < Number(repsMin)) return t('exerciseForm.error.repsMaxBelowMin');
  }

  if (type === 'timed_hold') {
    const holdMin = values.holdSecMin?.trim() ?? '';
    const holdMax = values.holdSecMax?.trim() ?? '';
    if (holdMax && !holdMin) return t('exerciseForm.error.holdMaxNeedsMin');
    if (holdMax && holdMin && Number(holdMax) < Number(holdMin)) return t('exerciseForm.error.holdMaxBelowMin');
  }

  return null;
}

/**
 * The circuit-block equivalent, for the one form that edits a block's own params rather than an
 * exercise's: the override editor's block branch.
 *
 * It needs its own field list because a circuit's config isn't an exercise's — and it needs one at
 * all because that branch validated nothing, so a negative rest typed into either free-text field
 * was written to the program file and then silently discarded by `applyBlockOverride`'s re-parse.
 * Labels are the keys the panel itself renders — which already carry their unit, so no `unit` here:
 * the message names the field exactly as the user sees it above the input.
 */
export const BLOCK_CONFIG_FIELDS: FieldDef[] = [
  { key: 'rounds', label: 'exerciseForm.field.rounds', max: MaxRounds, integer: true },
  // `optional`, because the schema has both `.optional()` — and because the panel seeds them from
  // the block, clearing one is how a user says "no rest here". Marking them required turned that into
  // a blocked save, where it had always read as 0.
  { key: 'restBetweenExercisesSec', label: 'overrideEditor.restPerExercise', min: 0, optional: true },
  { key: 'restBetweenRoundsSec', label: 'overrideEditor.restPerRound', min: 0, optional: true },
];

export function validateBlockConfig(values: Record<string, string>): string | null {
  return validateFields(BLOCK_CONFIG_FIELDS, values);
}

/** Config → the string map a form holds. Weight is converted to the user's unit; nothing else is. */
export function configToStrings(exercise: Exercise, unitSystem: UnitSystem): Record<string, string> {
  const values: Record<string, string> = {};
  for (const [key, value] of Object.entries(exercise.config)) {
    if (value === undefined) continue;
    values[key] = key === WEIGHT_FIELD ? String(toDisplayWeight(value as number, unitSystem)) : String(value);
  }
  return values;
}

export function buildExercise(
  id: string,
  name: string,
  type: ExerciseType,
  values: Record<string, string>,
  notes: string,
  units: UnitContext,
): Exercise {
  const num = (key: string) => Number(values[key] ?? 0) || 0;
  const optionalNum = (key: string) => (values[key]?.trim() ? Number(values[key]) : undefined);
  const trimmedNotes = notes.trim() || undefined;

  /** The weight field, back in kilograms — the unit everything downstream of this form stores. */
  const weightKg = () => {
    const entered = optionalNum(WEIGHT_FIELD);
    if (entered === undefined || !Number.isFinite(entered)) return undefined;
    // Unchanged from what was shown? Keep the stored value rather than the conversion of the rounded
    // display of it. See UnitContext.previousWeightKg.
    const { unitSystem, previousWeightKg } = units;
    if (previousWeightKg !== undefined && entered === toDisplayWeight(previousWeightKg, unitSystem)) {
      return previousWeightKg;
    }
    return fromDisplayWeight(entered, unitSystem);
  };

  switch (type) {
    case 'hiit':
      return {
        id,
        name,
        type,
        config: { workSec: num('workSec'), restSec: num('restSec'), rounds: num('rounds') },
        notes: trimmedNotes,
      };
    case 'emom':
      return {
        id,
        name,
        type,
        config: {
          intervalSec: num('intervalSec'),
          totalMinutes: num('totalMinutes'),
          targetReps: optionalNum('targetReps'),
        },
        notes: trimmedNotes,
      };
    case 'amrap':
      return { id, name, type, config: { timeCapSec: num('timeCapSec') }, notes: trimmedNotes };
    case 'reps':
      return {
        id,
        name,
        type,
        config: {
          sets: num('sets'),
          targetRepsMin: num('targetRepsMin'),
          targetRepsMax: optionalNum('targetRepsMax'),
          targetWeightKg: weightKg(),
          restSec: num('restSec'),
        },
        notes: trimmedNotes,
      };
    case 'timed_hold':
      return {
        id,
        name,
        type,
        config: {
          sets: num('sets'),
          holdSecMin: optionalNum('holdSecMin'),
          holdSecMax: optionalNum('holdSecMax'),
          restSec: num('restSec'),
        },
        notes: trimmedNotes,
      };
    case 'cardio':
      return {
        id,
        name,
        type,
        config: { durationSec: optionalNum('durationSec'), distanceMeters: optionalNum('distanceMeters') },
        notes: trimmedNotes,
      };
    case 'rest':
      return { id, name, type, config: { durationSec: num('durationSec') }, notes: trimmedNotes };
  }
}
