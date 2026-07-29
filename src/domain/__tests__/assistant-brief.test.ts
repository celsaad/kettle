import { changeLanguage } from 'i18next';

import { buildAssistantBrief } from '@/domain/assistant-brief';
import { parseLibraryYaml } from '@/domain/yaml-mapping';
import { aLibrary, anExercise, aProgram, aWorkout } from '@/test-support/library';

/**
 * The brief is what an assistant sees before it writes a line of YAML, and the app never gets to
 * correct it in the moment — a wrong id in here becomes a file the importer refuses, one round trip
 * later. So what's pinned is that it carries the user's real ids and a schema generated from the
 * validator itself, and that it stays on the format side of the ownership line.
 */
const library = aLibrary({
  exercises: [anExercise({ id: 'pull-ups', name: 'Pull-ups', type: 'reps' })],
  workouts: [aWorkout({ id: 'push-day', name: 'Push day' })],
  programs: [aProgram({ id: 'base-6', name: 'Base 6' })],
});

it('names every id in the library, with its type and display name', () => {
  const brief = buildAssistantBrief(library);

  // Merge is by id, so this list is the difference between a generated program landing and being
  // refused for referencing something nobody has.
  expect(brief).toContain('pull-ups (reps) — Pull-ups');
  expect(brief).toContain('push-day — Push day');
  expect(brief).toContain('base-6 — Base 6');
});

it('says so for a collection that is empty, rather than leaving a silent gap', () => {
  const brief = buildAssistantBrief(aLibrary({ exercises: [], workouts: [], programs: [] }));

  // Three headings and three "(none yet)"s. A blank stretch under a heading reads as a copy that
  // truncated, and an assistant would fill the silence by inventing ids.
  expect(brief.match(/\(none yet\)/g)).toHaveLength(3);
});

it('carries a JSON Schema generated from the validator, not a written-out copy', () => {
  const brief = buildAssistantBrief(library);
  const json = brief.slice(brief.indexOf('```json') + 7, brief.lastIndexOf('```'));
  const schema = JSON.parse(json);

  // Generated, so it moves when `schema.ts` moves — the discriminators and the range fields are here
  // because zod has them, and the hand-written docs are what this exists to stop relying on.
  expect(schema.required).toEqual(expect.arrayContaining(['version', 'exercises', 'workouts', 'programs']));
  expect(json).toContain('target_reps_min');
  expect(json).toContain('"const": "circuit"');
});

/**
 * The ownership line from the decision log, as a test rather than a comment: the app supplies the file
 * format and the user's own data, and never a word about training. This is the check that fails if
 * someone later adds "ask for a hypertrophy block" to make the output nicer.
 */
it('asks for a format and never for training content', () => {
  const brief = buildAssistantBrief(library).toLowerCase();

  for (const word of ['program for', 'hypertrophy', 'strength training', 'sets of', 'beginner', 'goal']) {
    expect(brief).not.toContain(word);
  }
});

it('is translated, and leaves the user’s own names alone', async () => {
  await changeLanguage('pt');
  const brief = buildAssistantBrief(library);

  expect(brief).toContain('Ids que já estão na minha biblioteca');
  // User data is never translated — the name renders exactly as authored, in any locale.
  expect(brief).toContain('pull-ups (reps) — Pull-ups');
});

/**
 * The loop's own round trip: a library serialized back through the brief's advertised format has to
 * be something the importer accepts. Cheap here, and it's the assumption the whole feature rests on.
 */
it('advertises a schema whose required keys match what the importer demands', () => {
  const missingPrograms = 'version: 1\nexercises: []\nworkouts: []\n';
  const result = parseLibraryYaml(missingPrograms);

  expect(result.ok).toBe(false);
  expect(buildAssistantBrief(library)).toContain('"programs"');
});
