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
import type { Exercise, ExerciseType } from '@/domain/types';

/** `min` is the smallest accepted value, defaulting to 1 — see validateConfig. */
export type FieldDef = { key: string; label: string; unit?: string; optional?: boolean; min?: number };

export const TYPE_OPTIONS: { type: ExerciseType; label: string }[] = [
  { type: 'reps', label: 'Reps' },
  { type: 'timed_hold', label: 'Hold' },
  { type: 'hiit', label: 'HIIT' },
  { type: 'emom', label: 'EMOM' },
  { type: 'amrap', label: 'AMRAP' },
  { type: 'cardio', label: 'Cardio' },
  { type: 'rest', label: 'Rest' },
];

export const CONFIG_FIELDS: Record<ExerciseType, FieldDef[]> = {
  hiit: [
    { key: 'workSec', label: 'Work', unit: 'sec' },
    { key: 'restSec', label: 'Rest', unit: 'sec', min: 0 },
    { key: 'rounds', label: 'Rounds' },
  ],
  emom: [
    { key: 'intervalSec', label: 'Interval', unit: 'sec' },
    { key: 'totalMinutes', label: 'Total', unit: 'min' },
    { key: 'targetReps', label: 'Target reps', optional: true },
  ],
  amrap: [{ key: 'timeCapSec', label: 'Time cap', unit: 'sec' }],
  reps: [
    { key: 'sets', label: 'Sets' },
    { key: 'targetRepsMin', label: 'Target reps' },
    { key: 'targetRepsMax', label: 'Target reps (max)', optional: true },
    { key: 'targetWeightKg', label: 'Weight', unit: 'kg', optional: true },
    { key: 'restSec', label: 'Rest', unit: 'sec', min: 0 },
  ],
  timed_hold: [
    { key: 'sets', label: 'Sets' },
    { key: 'holdSecMin', label: 'Hold', unit: 'sec' },
    { key: 'holdSecMax', label: 'Hold (max)', unit: 'sec', optional: true },
    { key: 'restSec', label: 'Rest', unit: 'sec', min: 0 },
  ],
  cardio: [
    { key: 'durationSec', label: 'Duration', unit: 'sec', optional: true },
    { key: 'distanceMeters', label: 'Distance', unit: 'm', optional: true },
  ],
  rest: [{ key: 'durationSec', label: 'Duration', unit: 'sec', min: 0 }],
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
    if (!raw) {
      if (field.optional) continue;
      return `${field.label} is required.`;
    }
    const parsed = Number(raw);
    if (!Number.isFinite(parsed)) return `${field.label} must be a number.`;
    const min = field.min ?? 1;
    if (parsed < min) return `${field.label} must be at least ${min}.`;
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
