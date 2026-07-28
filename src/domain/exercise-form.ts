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
import i18next from '@/i18n';
import type { Exercise, ExerciseType } from '@/domain/types';

/**
 * `label` is an i18next key rather than display text, resolved with `i18next.t()` at the point of use
 * — this module has no React tree to hook `useTranslation()` into, and there's no in-app language
 * switch (the device locale is read once at startup, see `i18n/index.ts`), so resolving eagerly here
 * is safe. `min` is the smallest accepted value, defaulting to 1 — see validateConfig.
 */
export type FieldDef = { key: string; label: string; unit?: string; optional?: boolean; min?: number };

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
    { key: 'rounds', label: 'exerciseForm.field.rounds' },
  ],
  emom: [
    { key: 'intervalSec', label: 'exerciseForm.field.interval', unit: 'sec' },
    { key: 'totalMinutes', label: 'exerciseForm.field.total', unit: 'min' },
    { key: 'targetReps', label: 'exerciseForm.field.targetReps', optional: true },
  ],
  amrap: [{ key: 'timeCapSec', label: 'exerciseForm.field.timeCap', unit: 'sec' }],
  reps: [
    { key: 'sets', label: 'exerciseForm.field.sets' },
    { key: 'targetRepsMin', label: 'exerciseForm.field.targetReps' },
    { key: 'targetRepsMax', label: 'exerciseForm.field.targetRepsMax', optional: true },
    { key: 'targetWeightKg', label: 'exerciseForm.field.weight', unit: 'kg', optional: true },
    { key: 'restSec', label: 'exerciseForm.field.rest', unit: 'sec', min: 0 },
  ],
  timed_hold: [
    { key: 'sets', label: 'exerciseForm.field.sets' },
    { key: 'holdSecMin', label: 'exerciseForm.field.hold', unit: 'sec' },
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
 * Mirrors the zod config constraints in `domain/schema.ts` for the in-app forms, which write straight
 * to the library store and so never pass through the schema — imported YAML can't produce a 0-set
 * exercise, but before this the editor happily could, and a workout made of those resolved to zero
 * runnable steps (the "Nothing to run" case in session.tsx).
 *
 * Required fields default to a minimum of 1, matching the schema's `positive()`. The rest-length
 * fields carry an explicit `min: 0` because the schema allows a zero-length rest and rejecting that
 * would be stricter than the format itself. Returns the first problem found, or null if it's valid.
 */
export function validateConfig(type: ExerciseType, values: Record<string, string>): string | null {
  for (const field of CONFIG_FIELDS[type]) {
    const raw = values[field.key]?.trim() ?? '';
    const label = i18next.t(field.label);
    if (!raw) {
      if (field.optional) continue;
      return i18next.t('exerciseForm.error.required', { label });
    }
    const parsed = Number(raw);
    if (!Number.isFinite(parsed)) return i18next.t('exerciseForm.error.mustBeNumber', { label });
    const min = field.min ?? 1;
    if (parsed < min) return i18next.t('exerciseForm.error.mustBeAtLeast', { label, min });
  }
  return null;
}

export function configToStrings(exercise: Exercise): Record<string, string> {
  const values: Record<string, string> = {};
  for (const [key, value] of Object.entries(exercise.config)) {
    if (value !== undefined) values[key] = String(value);
  }
  return values;
}

export function buildExercise(
  id: string,
  name: string,
  type: ExerciseType,
  values: Record<string, string>,
  notes: string,
): Exercise {
  const num = (key: string) => Number(values[key] ?? 0) || 0;
  const optionalNum = (key: string) => (values[key]?.trim() ? Number(values[key]) : undefined);
  const trimmedNotes = notes.trim() || undefined;

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
        config: { intervalSec: num('intervalSec'), totalMinutes: num('totalMinutes'), targetReps: optionalNum('targetReps') },
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
          targetWeightKg: optionalNum('targetWeightKg'),
          restSec: num('restSec'),
        },
        notes: trimmedNotes,
      };
    case 'timed_hold':
      return {
        id,
        name,
        type,
        config: { sets: num('sets'), holdSecMin: num('holdSecMin'), holdSecMax: optionalNum('holdSecMax'), restSec: num('restSec') },
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
