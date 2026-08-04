/**
 * The session editor's data model: which fields each logged entry type exposes, how a `SessionEntry`
 * round-trips to the string state a form holds, and the validation that path must run itself.
 *
 * Separate from the screen for the same reason `exercise-form.ts` is: none of it touches React, so it
 * belongs with the domain and can be tested without rendering anything. It is a sibling of that module
 * rather than an extension of it because the two describe different things — that one edits an
 * exercise's *plan* (sets, targets, rest), this one edits what was actually *logged*.
 */
import { t } from 'i18next';

import type { SessionEntry } from '@/domain/types';
import { fromDisplayWeight, toDisplayWeight, type UnitSystem } from '@/domain/units';

/**
 * Everything the editor can rewrite. `emom` is deliberately absent: its log is a per-minute rep list,
 * which is a list editor rather than a row of fields, and the issue scoped it out. `rest` is absent
 * because a rest entry has no number a user would recognise as theirs — History already hides them.
 *
 * Anything not in here renders read-only, so a new entry type is visible-but-uneditable by default
 * rather than silently dropped.
 */
export type EditableEntryType = 'reps' | 'timed_hold' | 'hiit' | 'amrap' | 'cardio';
export type EditableEntry = Extract<SessionEntry, { type: EditableEntryType }>;

export function isEditableEntry(entry: SessionEntry): entry is EditableEntry {
  return entry.type !== 'emom' && entry.type !== 'rest';
}

/**
 * `label` is an i18next key resolved at the point of use, matching `exercise-form.ts` — this module
 * has no React tree to hook `useTranslation()` into. `unit` is the suffix beside the label, and
 * `'weight'` is the one that isn't literal: it renders as kg or lb and its value converts both ways.
 *
 * `min` defaults to **0** here, unlike the exercise form's 1. That form describes a plan, where zero
 * sets is meaningless; this one describes what happened, and a set you failed at zero reps is a real
 * thing the runner can log.
 */
export type EntryFieldDef = {
  key: string;
  label: string;
  unit?: string;
  optional?: boolean;
  min?: number;
  max?: number;
  /** Counts, not measurements: 8.5 reps is not something the log should be able to hold. */
  integer?: boolean;
};

/** The one field whose value is not stored in the unit it is shown in. Named once so both users agree. */
const LOAD_FIELD = 'weightKg';

export const ENTRY_FIELDS: Record<EditableEntryType, EntryFieldDef[]> = {
  reps: [
    { key: 'reps', label: 'sessionEditor.field.reps', integer: true },
    { key: LOAD_FIELD, label: 'sessionEditor.field.load', unit: 'weight', optional: true },
    // Blank means it wasn't recorded, which is different from 1. The scale itself starts at 1.
    { key: 'rpe', label: 'sessionEditor.field.rpe', optional: true, min: 1, max: 10 },
  ],
  timed_hold: [{ key: 'holdSec', label: 'sessionEditor.field.hold', unit: 'sec' }],
  hiit: [{ key: 'roundsCompleted', label: 'sessionEditor.field.rounds', integer: true }],
  amrap: [
    { key: 'roundsCompleted', label: 'sessionEditor.field.rounds', integer: true },
    { key: 'extraReps', label: 'sessionEditor.field.extraReps', optional: true, integer: true },
  ],
  cardio: [
    { key: 'durationSec', label: 'sessionEditor.field.duration', unit: 'sec', optional: true },
    { key: 'distanceMeters', label: 'sessionEditor.field.distance', unit: 'm', optional: true },
  ],
};

export type SetValues = Record<string, string>;

/**
 * One row of the form: what the user can type, plus the parts of the logged set that have no field and
 * still have to survive the round trip.
 *
 * Carrying those here rather than reading them back off the original entry is what makes removal safe.
 * Removing a set is an array splice, and after one splice a form row's position no longer matches the
 * logged set it came from — anything that went looking for `original.sets[index]` at save time would
 * quietly staple set 3's rest onto set 2.
 *
 * `weightKg` is the *stored* kilograms behind whatever `values[LOAD_FIELD]` is displaying. It is the
 * drift guard from `UnitContext.previousWeightKg`, kept per row so a caller cannot forget to pass it:
 * without it, opening a 100 kg set in pounds and editing something else rewrites it as 100.02.
 */
export type SetForm = { values: SetValues; restTakenSec: number; weightKg?: number };

/** A logged entry → the form rows for it. Load is converted to the user's unit; nothing else is. */
export function entryToSetForms(entry: EditableEntry, unitSystem: UnitSystem): SetForm[] {
  switch (entry.type) {
    case 'reps':
      return entry.sets.map((set) => ({
        values: {
          reps: String(set.reps),
          [LOAD_FIELD]: set.weightKg === undefined ? '' : String(toDisplayWeight(set.weightKg, unitSystem)),
          rpe: set.rpe === undefined ? '' : String(set.rpe),
        },
        restTakenSec: set.restTakenSec,
        weightKg: set.weightKg,
      }));
    case 'timed_hold':
      return entry.sets.map((set) => ({
        values: { holdSec: String(set.holdSec) },
        restTakenSec: set.restTakenSec,
      }));
    // The remaining three log one result for the whole entry rather than a list of sets, so they get a
    // single row. That keeps one shape through the screen, the validation and the remove path instead
    // of a per-set branch and a single-value branch that have to agree.
    case 'hiit':
      return [{ values: { roundsCompleted: String(entry.roundsCompleted) }, restTakenSec: 0 }];
    case 'amrap':
      return [
        {
          values: {
            roundsCompleted: String(entry.roundsCompleted),
            extraReps: entry.extraReps === undefined ? '' : String(entry.extraReps),
          },
          restTakenSec: 0,
        },
      ];
    case 'cardio':
      return [
        {
          values: {
            durationSec: entry.durationSec === undefined ? '' : String(entry.durationSec),
            distanceMeters: entry.distanceMeters === undefined ? '' : String(entry.distanceMeters),
          },
          restTakenSec: 0,
        },
      ];
  }
}

/**
 * Checks every row of one entry's form and returns the first problem, or null. The editor writes
 * straight to the session store and never passes through `schema.ts`, so this is the only thing
 * standing between a typo and a log entry claiming an RPE of 88.
 */
export function validateEntryForm(type: EditableEntryType, forms: SetForm[]): string | null {
  for (const form of forms) {
    for (const field of ENTRY_FIELDS[type]) {
      const raw = form.values[field.key]?.trim() ?? '';
      const label = t(field.label);
      if (!raw) {
        if (field.optional) continue;
        return t('exerciseForm.error.required', { label });
      }
      const parsed = Number(raw);
      if (!Number.isFinite(parsed)) return t('exerciseForm.error.mustBeNumber', { label });
      const min = field.min ?? 0;
      if (parsed < min) return t('exerciseForm.error.mustBeAtLeast', { label, min });
      if (field.max !== undefined && parsed > field.max)
        return t('exerciseForm.error.mustBeAtMost', { label, max: field.max });
      if (field.integer && !Number.isInteger(parsed)) return t('exerciseForm.error.mustBeWhole', { label });
    }
    // Both cardio fields are individually optional — the format allows either one alone — but an entry
    // with neither records nothing at all, and the per-field loop can't see that.
    if (type === 'cardio' && !form.values.durationSec?.trim() && !form.values.distanceMeters?.trim()) {
      return t('sessionEditor.error.cardioNeedsOne');
    }
  }
  return null;
}

/**
 * The form rows back into a logged entry, keeping the original's exercise and type.
 *
 * Returns **null when there are no rows left**, which is the caller's signal to drop the entry from
 * the session rather than write an exercise recorded as having done nothing.
 */
export function buildEntry(original: EditableEntry, forms: SetForm[], unitSystem: UnitSystem): EditableEntry | null {
  if (forms.length === 0) return null;

  const exercise = original.exercise;
  const num = (form: SetForm, key: string) => Number(form.values[key] ?? '') || 0;
  const optionalNum = (form: SetForm, key: string) => {
    const raw = form.values[key]?.trim();
    if (!raw) return undefined;
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? parsed : undefined;
  };

  /** The load field, back in kilograms — see SetForm.weightKg for why the untouched case is special. */
  const loadKg = (form: SetForm) => {
    const entered = optionalNum(form, LOAD_FIELD);
    if (entered === undefined) return undefined;
    if (form.weightKg !== undefined && entered === toDisplayWeight(form.weightKg, unitSystem)) return form.weightKg;
    return fromDisplayWeight(entered, unitSystem);
  };

  switch (original.type) {
    case 'reps':
      return {
        exercise,
        type: 'reps',
        sets: forms.map((form) => ({
          reps: num(form, 'reps'),
          weightKg: loadKg(form),
          rpe: optionalNum(form, 'rpe'),
          restTakenSec: form.restTakenSec,
        })),
      };
    case 'timed_hold':
      return {
        exercise,
        type: 'timed_hold',
        sets: forms.map((form) => ({ holdSec: num(form, 'holdSec'), restTakenSec: form.restTakenSec })),
      };
    case 'hiit':
      return { exercise, type: 'hiit', roundsCompleted: num(forms[0], 'roundsCompleted') };
    case 'amrap':
      return {
        exercise,
        type: 'amrap',
        roundsCompleted: num(forms[0], 'roundsCompleted'),
        extraReps: optionalNum(forms[0], 'extraReps'),
      };
    case 'cardio':
      return {
        exercise,
        type: 'cardio',
        durationSec: optionalNum(forms[0], 'durationSec'),
        distanceMeters: optionalNum(forms[0], 'distanceMeters'),
      };
  }
}
