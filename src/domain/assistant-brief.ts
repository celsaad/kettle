// Same reasoning as `format.ts`: `t` comes from the i18next singleton directly rather than via
// `@/i18n`, which would pull `expo-localization` into the domain layer and into every test.
import { t } from 'i18next';
import { z } from 'zod';

import { rawLibrarySchema } from '@/domain/schema';
import type { Library } from '@/domain/types';

/**
 * Everything an assistant needs to emit YAML this app will accept: the format, and the ids already in
 * the user's library. Copied out of the import screen and pasted into a chat — the app never talks to
 * a model itself (see the implementation plan's hard constraint).
 *
 * **This carries format and never intent.** No goals, no set/rep prescriptions, no progression
 * scheme, no exercise selection, nothing about the user's body. The test for any line added here is
 * the one in the decision log: if the generated program turns out badly, does this sentence make that
 * Kettle's fault? The app owns the file format; the user owns what goes in the file.
 */
export function buildAssistantBrief(library: Library): string {
  return [t('import.brief.prompt'), inventory(library), schemaSection()].join('\n\n');
}

/**
 * The user's own ids, which is the half a schema can't supply: merge is by `id`, so a generated
 * program referencing `pullups` only lands if that id already exists — and an assistant that can't
 * see the library will invent one. Names and types ride along so it can tell `rest-long` from
 * `rest-short` without guessing from the slug.
 *
 * Names are user data and render verbatim, untranslated, exactly as they do everywhere else.
 */
function inventory(library: Library): string {
  const lines = [
    t('import.brief.inventoryHeading'),
    ...section(
      t('import.brief.exercisesLabel'),
      library.exercises.map((exercise) => `  ${exercise.id} (${exercise.type}) — ${exercise.name}`),
    ),
    ...section(
      t('import.brief.workoutsLabel'),
      library.workouts.map((workout) => `  ${workout.id} — ${workout.name}`),
    ),
    ...section(
      t('import.brief.programsLabel'),
      library.programs.map((program) => `  ${program.id} — ${program.name}`),
    ),
  ];
  return lines.join('\n');
}

/** A labelled list, or the label plus an explicit "none" — a silent gap reads as a copy that failed. */
function section(label: string, entries: string[]): string[] {
  return [label, ...(entries.length > 0 ? entries : [`  ${t('import.brief.none')}`])];
}

/**
 * Generated from `rawLibrarySchema` rather than written out, so it cannot drift from what the importer
 * actually accepts — the reason this exists at all, since `authoring-exercises-yaml.md` is a
 * hand-maintained second copy and has drifted before.
 *
 * `io: 'input'` because that's the side the assistant is writing: what a valid *file* looks like,
 * before defaults are applied.
 *
 * What it can't express is the handful of cross-field rules zod carries as refinements —
 * `target_reps_max >= target_reps_min`, an override targeting exactly one of `exercise`/`block`, no
 * repeated `(week, day)` pair. Those are dropped silently by `toJSONSchema`, so the brief says the
 * importer checks more than the schema shows and points at the error text, rather than restating the
 * rules here and becoming the third copy this was meant to avoid.
 */
function schemaSection(): string {
  const schema = z.toJSONSchema(rawLibrarySchema, { io: 'input' });
  return [t('import.brief.schemaHeading'), '```json', JSON.stringify(schema, null, 2), '```'].join('\n');
}
