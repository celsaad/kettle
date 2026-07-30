// `t` from the i18next singleton, same reasoning as `format.ts` and `exercise-form.ts`.
import { t } from 'i18next';

import { CONFIG_FIELDS, configToStrings, fieldUnitLabel, TYPE_OPTIONS } from '@/domain/exercise-form';
import type { Exercise, Program, Workout, WorkoutBlock } from '@/domain/types';
import type { UnitSystem } from '@/domain/units';

/**
 * What an import is about to overwrite, field by field.
 *
 * Merge replaces a matching `id` wholesale (§6), so an id the user has locally tweaked loses that
 * tweak silently — the preview said "1 updated exercise" and nothing about *what*. §6's own
 * instruction is to surface updates clearly so local tweaks aren't lost without being seen, and the
 * AI-authoring path made that sharper: a regenerated library arrives as a wall of updates, and the
 * only interesting part is the handful of numbers that moved.
 *
 * `from`/`to` are already display strings, weights included — a diff that printed kilograms to
 * somebody working in pounds would describe a change they didn't make.
 */
export type FieldChange = { label: string; from: string; to: string };

/**
 * The longest a value gets before it's cut. Every field but `notes` is a number and nowhere near this;
 * a full pair of notes ran to three lines and pushed the changed *numbers* — the part anyone scans a
 * diff for — off the bottom of the row. The whole text is one tap away in the editor.
 */
const MAX_VALUE_LENGTH = 48;

/** Renders an absent value as words, so "60 → not set" reads as a removal rather than as blank. */
function shown(value: string | undefined): string {
  const text = value?.trim();
  if (!text) return t('import.diff.unset');
  return text.length > MAX_VALUE_LENGTH ? `${text.slice(0, MAX_VALUE_LENGTH).trimEnd()}…` : text;
}

/**
 * Compares the **raw** values and truncates only for display. Comparing what's shown would make two
 * notes that first differ past the cut look identical, and the change would vanish from the one screen
 * that exists to show it.
 */
function change(label: string, from: string | undefined, to: string | undefined): FieldChange[] {
  if ((from?.trim() ?? '') === (to?.trim() ?? '')) return [];
  return [{ label, from: shown(from), to: shown(to) }];
}

function typeLabel(exercise: Exercise): string {
  const option = TYPE_OPTIONS.find((candidate) => candidate.type === exercise.type);
  return option ? t(option.label) : exercise.type;
}

/**
 * A type change is reported *instead of* the config, not alongside it. The two configs share no keys
 * once the type moves, so enumerating them would list every field of both as changed — technically
 * true and useless, burying the one line that explains it.
 */
export function diffExercise(before: Exercise, after: Exercise, unitSystem: UnitSystem): FieldChange[] {
  const changes = [
    ...change(t('common.name'), before.name, after.name),
    ...change(t('import.diff.type'), typeLabel(before), typeLabel(after)),
    ...change(t('import.diff.notes'), before.notes, after.notes),
  ];
  if (before.type !== after.type) return changes;

  const beforeValues = configToStrings(before, unitSystem);
  const afterValues = configToStrings(after, unitSystem);
  // Walked in `CONFIG_FIELDS` order rather than `Object.keys` order, so a diff lists fields in the
  // same order the editor shows them.
  for (const field of CONFIG_FIELDS[after.type]) {
    const unit = fieldUnitLabel(field, unitSystem);
    const withUnit = (value: string | undefined) => (value && unit ? `${value} ${unit}` : value);
    changes.push(...change(t(field.label), withUnit(beforeValues[field.key]), withUnit(afterValues[field.key])));
  }
  return changes;
}

/** Every exercise a block reaches, circuit members included — one entry per reference, not per id. */
function referencedExercises(blocks: WorkoutBlock[]): string[] {
  return blocks.flatMap((block) =>
    block.kind === 'exercise' ? [block.exerciseId] : block.members.map((member) => member.exerciseId),
  );
}

/**
 * Coarser than the exercise diff on purpose. A workout's substance is its structure, and a faithful
 * per-block diff of reordered circuits is a diffing algorithm rather than a preview line — while the
 * question the preview has to answer is only "is this the same workout, and does it still contain what
 * I put in it". Block count plus which exercise references appeared or vanished answers that.
 */
export function diffWorkout(before: Workout, after: Workout): FieldChange[] {
  const changes = [
    ...change(t('common.name'), before.name, after.name),
    ...change(t('import.diff.blocks'), String(before.blocks.length), String(after.blocks.length)),
  ];

  const had = referencedExercises(before.blocks);
  const has = referencedExercises(after.blocks);
  const gone = had.filter((id) => !has.includes(id));
  const added = has.filter((id) => !had.includes(id));
  // Ids, not names: an exercise dropped by this same import may no longer have a name to look up, and
  // the id is what the YAML the user is holding actually says.
  if (gone.length > 0) changes.push({ label: t('import.diff.exercises'), from: gone.join(', '), to: '—' });
  if (added.length > 0) changes.push({ label: t('import.diff.exercises'), from: '—', to: added.join(', ') });
  return changes;
}

/**
 * Same reasoning as workouts, one level up: week count, plus how many weeks differ in any way. Which
 * *field* of week 4 moved is a question `program-editor.tsx` answers better than a preview line can.
 */
export function diffProgram(before: Program, after: Program): FieldChange[] {
  const changes = [
    ...change(t('common.name'), before.name, after.name),
    ...change(t('import.diff.weeks'), String(before.weeks.length), String(after.weeks.length)),
  ];

  const key = (week: Program['weeks'][number]) => `${week.week}::${week.day ?? ''}`;
  const previous = new Map(before.weeks.map((week) => [key(week), JSON.stringify(week)]));
  // Only weeks that exist on both sides: one that's new or gone is already accounted for by the count,
  // and counting it here as well would double-report the same edit.
  const edited = after.weeks.filter((week) => {
    const was = previous.get(key(week));
    return was !== undefined && was !== JSON.stringify(week);
  });
  if (edited.length > 0) {
    changes.push({
      label: t('import.diff.weeksChanged'),
      from: '—',
      to: edited.map((week) => (week.day ? `${week.week} (${week.day})` : String(week.week))).join(', '),
    });
  }
  return changes;
}
