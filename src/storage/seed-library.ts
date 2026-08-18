import type { Library, ProgramWeek } from '@/domain/types';
import { localizeSeed } from '@/storage/seed-translations';

/**
 * A scheduled day off, written out longhand everywhere it happens — including the two at the end of
 * each week, which is the part that's easy to leave implicit and wrong to.
 *
 * The home screen spends one rest entry per calendar day, so a three-session week written as three
 * entries rolls into the next week the day after the last session. Seven entries make the week a week.
 */
function restDay(week: number, day: string, notes?: string): ProgramWeek {
  return { week, day, restDay: true, notes };
}

/**
 * Written to exercises.yaml the first time the app launches and no library file exists yet, so the
 * app never opens empty — and it is also the web build's entire library and what a corrupt
 * exercises.yaml is reset to, which is why it stays deliberately small.
 *
 * **This is the structural definition, and it is written in English.** The names, notes and day
 * labels a non-English user actually sees come from `seed-translations.ts`, applied over this by
 * `seedLibraryFor` below — so an edit here (a new exercise, a changed week) is a content change every
 * language's table then owes a string for, and `seed-library.test.ts` fails the ones that don't.
 *
 * This is curated starter content, not a format demo: two four-week programs a new user can press
 * start on without building anything first, plus two standalone workouts so all seven exercise
 * types have a live example. Three authoring constraints it has to respect, none of them local to
 * this file:
 *
 * - `foundations` must stay **first**: `activeProgram` (state/selectors/next-up.ts) falls back to
 *   `programs[0]` until a session has been run, and there is no starter-program picker — so the
 *   no-equipment program is what a fresh install lands on, and the dumbbell one is browse-only
 *   until it's explicitly started.
 * - **Weeks run in the order they are written here.** Both programs train on days 1, 3 and 5 and
 *   rest on the other four, so a reordered block is a reordered week. (Day labels used to have to
 *   sort alphabetically into training order too, which is why they are `Day 1`…`Day 7`;
 *   `sortedProgramWeeks` no longer looks at the label at all, so that constraint is gone and the
 *   names are now free.)
 * - Every week is enumerated and each override is repeated in every later week it still applies to:
 *   weeks resolve sparsely (a gap is skipped, not filled), and overrides don't carry forward.
 *
 * `notes` describe the progression model and nothing else — no form cues, no injury or diet
 * advice — so the app keeps explaining its own data rather than prescribing training. No
 * `targetWeightKg` is seeded: loads are the user's to set.
 */
export const seedLibrary: Library = {
  version: 1,
  exercises: [
    { id: 'rest', name: 'Rest', type: 'rest', config: { durationSec: 90 } },

    // Foundations — nothing here needs equipment.
    {
      id: 'pushups',
      name: 'Push-ups',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 8, targetRepsMax: 15, restSec: 60 },
      notes: 'Stop 2 reps shy of failure. Hit 15 on every set twice before moving to a harder variation.',
    },
    {
      id: 'bodyweight-squats',
      name: 'Bodyweight Squats',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 12, targetRepsMax: 20, restSec: 60 },
    },
    {
      id: 'inverted-rows',
      name: 'Inverted Rows',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 6, targetRepsMax: 12, restSec: 75 },
      notes: 'Under a table or a low bar. Walk your feet further out to make it harder once you own the top of the range.',
    },
    {
      id: 'split-squats',
      name: 'Split Squats',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 8, targetRepsMax: 12, restSec: 60 },
      notes: 'Reps are per leg — log the weaker side, so the number you beat next time is the honest one.',
    },
    {
      id: 'glute-bridge',
      name: 'Glute Bridge',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 12, targetRepsMax: 20, restSec: 45 },
    },
    {
      id: 'plank',
      name: 'Plank',
      type: 'timed_hold',
      config: { sets: 3, holdSecMin: 20, holdSecMax: 45, restSec: 45 },
      notes: 'Raise the whole range by 5s once you hit 45s on every set.',
    },
    { id: 'mountain-climbers', name: 'Mountain Climbers', type: 'hiit', config: { workSec: 30, restSec: 30, rounds: 4 } },

    // Dumbbell Strength — one adjustable pair covers all of it.
    {
      id: 'db-goblet-squat',
      name: 'Dumbbell Goblet Squat',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 8, targetRepsMax: 12, restSec: 90 },
      notes:
        'No weight is seeded — set yours on the first session, then add the smallest jump you own once you hit 12 on every set.',
    },
    {
      id: 'db-floor-press',
      name: 'Dumbbell Floor Press',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 8, targetRepsMax: 12, restSec: 90 },
      notes: 'Pressed off the floor, so it needs no bench. Add weight once you hit 12 on every set.',
    },
    {
      id: 'db-row',
      name: 'Dumbbell Row',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 8, targetRepsMax: 12, restSec: 90 },
      notes: 'Reps are per side.',
    },
    {
      id: 'db-romanian-deadlift',
      name: 'Dumbbell Romanian Deadlift',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 8, targetRepsMax: 12, restSec: 90 },
    },
    {
      id: 'db-overhead-press',
      name: 'Dumbbell Overhead Press',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 6, targetRepsMax: 10, restSec: 90 },
    },
    {
      id: 'farmers-carry',
      name: "Farmer's Carry",
      type: 'timed_hold',
      config: { sets: 3, holdSecMin: 30, holdSecMax: 45, restSec: 60 },
      notes: 'Add time before adding weight.',
    },

    // Standalone conditioning — the types the two programs don't otherwise cover.
    { id: 'burpees', name: 'Burpees', type: 'hiit', config: { workSec: 40, restSec: 20, rounds: 4 } },
    {
      id: 'emom-pushups',
      name: 'EMOM Push-ups',
      type: 'emom',
      config: { intervalSec: 60, totalMinutes: 10, targetReps: 8 },
      notes:
        'Every minute on the minute: 8 push-ups, then rest whatever is left of the minute. Cut the target once you stop finishing inside it.',
    },
    {
      id: 'amrap-12-bodyweight',
      name: 'AMRAP 12',
      type: 'amrap',
      config: { timeCapSec: 720 },
      notes:
        'One round is 10 push-ups, 15 bodyweight squats, 10 inverted rows. The app times the cap and counts rounds — the movements live here in the notes, since an AMRAP has no movement list of its own.',
    },
    {
      id: 'easy-cardio',
      name: 'Easy Cardio Cooldown',
      type: 'cardio',
      config: { durationSec: 600 },
      notes: 'Run, bike, row, walk — whatever you have. Add a distance to the config if you want that tracked too.',
    },
  ],
  workouts: [
    {
      id: 'foundations-push',
      name: 'Foundations · Push',
      blocks: [
        { kind: 'exercise', exerciseId: 'pushups' },
        { kind: 'exercise', exerciseId: 'rest' },
        { kind: 'exercise', exerciseId: 'plank' },
        { kind: 'exercise', exerciseId: 'rest', configOverride: { durationSec: 60 } },
        { kind: 'exercise', exerciseId: 'mountain-climbers' },
      ],
    },
    {
      id: 'foundations-legs',
      name: 'Foundations · Legs',
      blocks: [
        { kind: 'exercise', exerciseId: 'bodyweight-squats' },
        { kind: 'exercise', exerciseId: 'rest' },
        { kind: 'exercise', exerciseId: 'split-squats' },
        { kind: 'exercise', exerciseId: 'rest' },
        { kind: 'exercise', exerciseId: 'glute-bridge' },
      ],
    },
    {
      id: 'foundations-pull',
      name: 'Foundations · Pull',
      blocks: [
        { kind: 'exercise', exerciseId: 'inverted-rows' },
        { kind: 'exercise', exerciseId: 'rest', configOverride: { durationSec: 120 } },
        {
          kind: 'circuit',
          id: 'foundations-finisher',
          rounds: 3,
          restBetweenExercisesSec: 15,
          restBetweenRoundsSec: 60,
          // Members are reps/timed_hold on purpose: a circuit repeats a member once per round and
          // ignores its own `sets`, but a hiit/emom/amrap member would run its whole internal
          // timing on every visit — four 30/30 rounds of mountain climbers, three times over.
          members: [{ exerciseId: 'pushups' }, { exerciseId: 'bodyweight-squats' }, { exerciseId: 'plank' }],
        },
      ],
    },
    {
      id: 'db-full-body-a',
      name: 'Dumbbell Full Body A',
      blocks: [
        { kind: 'exercise', exerciseId: 'db-goblet-squat' },
        { kind: 'exercise', exerciseId: 'rest' },
        { kind: 'exercise', exerciseId: 'db-floor-press' },
        { kind: 'exercise', exerciseId: 'rest' },
        { kind: 'exercise', exerciseId: 'db-row' },
      ],
    },
    {
      id: 'db-full-body-b',
      name: 'Dumbbell Full Body B',
      blocks: [
        { kind: 'exercise', exerciseId: 'db-romanian-deadlift' },
        { kind: 'exercise', exerciseId: 'rest' },
        { kind: 'exercise', exerciseId: 'db-overhead-press' },
        { kind: 'exercise', exerciseId: 'rest' },
        { kind: 'exercise', exerciseId: 'farmers-carry' },
      ],
    },
    {
      id: 'mixed-conditioning',
      name: 'Mixed Conditioning',
      blocks: [
        { kind: 'exercise', exerciseId: 'burpees' },
        { kind: 'exercise', exerciseId: 'rest' },
        { kind: 'exercise', exerciseId: 'amrap-12-bodyweight' },
        { kind: 'exercise', exerciseId: 'rest', configOverride: { durationSec: 120 } },
        { kind: 'exercise', exerciseId: 'easy-cardio' },
      ],
    },
    {
      id: 'emom-10',
      name: 'EMOM 10',
      blocks: [{ kind: 'exercise', exerciseId: 'emom-pushups' }],
    },
  ],
  programs: [
    {
      id: 'foundations',
      name: 'Foundations · 4 Weeks · No Equipment',
      weeks: [
        {
          week: 1,
          day: 'Day 1',
          workoutId: 'foundations-push',
          notes: 'Baseline week. Log where you actually land in each range — don’t chase the top of it yet.',
        },
        restDay(1, 'Day 2', 'Rest is where the last session turns into something. Walk if you like; nothing heavy.'),
        { week: 1, day: 'Day 3', workoutId: 'foundations-legs' },
        restDay(1, 'Day 4'),
        { week: 1, day: 'Day 5', workoutId: 'foundations-pull' },
        restDay(1, 'Day 6'),
        restDay(1, 'Day 7'),

        {
          week: 2,
          day: 'Day 1',
          workoutId: 'foundations-push',
          notes: 'Same three sessions. Aim for one more rep than you logged in week 1, on every set.',
        },
        restDay(2, 'Day 2'),
        { week: 2, day: 'Day 3', workoutId: 'foundations-legs' },
        restDay(2, 'Day 4'),
        { week: 2, day: 'Day 5', workoutId: 'foundations-pull' },
        restDay(2, 'Day 6'),
        restDay(2, 'Day 7'),

        {
          week: 3,
          day: 'Day 1',
          workoutId: 'foundations-push',
          notes: 'A fourth set everywhere. Keep the reps where they were rather than pushing both at once.',
          overrides: [
            { kind: 'exercise', exerciseId: 'pushups', config: { sets: 4 } },
            { kind: 'exercise', exerciseId: 'plank', config: { sets: 4 } },
          ],
        },
        restDay(3, 'Day 2'),
        {
          week: 3,
          day: 'Day 3',
          workoutId: 'foundations-legs',
          overrides: [
            { kind: 'exercise', exerciseId: 'bodyweight-squats', config: { sets: 4 } },
            { kind: 'exercise', exerciseId: 'split-squats', config: { sets: 4 } },
          ],
        },
        restDay(3, 'Day 4'),
        {
          week: 3,
          day: 'Day 5',
          workoutId: 'foundations-pull',
          overrides: [{ kind: 'exercise', exerciseId: 'inverted-rows', config: { sets: 4 } }],
        },
        restDay(3, 'Day 6'),
        restDay(3, 'Day 7'),

        // Week 4 repeats week 3's overrides verbatim: an override applies to its own week only.
        {
          week: 4,
          day: 'Day 1',
          workoutId: 'foundations-push',
          notes: 'Heaviest week of the block. Now push the reps toward the top of each range.',
          overrides: [
            { kind: 'exercise', exerciseId: 'pushups', config: { sets: 4 } },
            { kind: 'exercise', exerciseId: 'plank', config: { sets: 4 } },
          ],
        },
        restDay(4, 'Day 2'),
        {
          week: 4,
          day: 'Day 3',
          workoutId: 'foundations-legs',
          overrides: [
            { kind: 'exercise', exerciseId: 'bodyweight-squats', config: { sets: 4 } },
            { kind: 'exercise', exerciseId: 'split-squats', config: { sets: 4 } },
          ],
        },
        restDay(4, 'Day 4'),
        {
          week: 4,
          day: 'Day 5',
          workoutId: 'foundations-pull',
          notes:
            'Last session of the block, and a fourth finisher round. Completing it loops back to week 1 — run it again with the top of each range as your new floor.',
          overrides: [
            { kind: 'exercise', exerciseId: 'inverted-rows', config: { sets: 4 } },
            { kind: 'block', blockId: 'foundations-finisher', config: { rounds: 4 } },
          ],
        },
        restDay(4, 'Day 6'),
        restDay(4, 'Day 7'),
      ],
    },
    {
      id: 'dumbbell-strength',
      name: 'Dumbbell Strength · 4 Weeks',
      weeks: [
        // Three sessions a week alternating two full-body workouts, so each lands twice a fortnight.
        {
          week: 1,
          day: 'Day 1',
          workoutId: 'db-full-body-a',
          notes: 'Baseline week. Pick weights you could stop 2 reps shy of failure with, and write them into each exercise.',
        },
        restDay(1, 'Day 2', 'A day between sessions is part of the plan, not a gap in it.'),
        { week: 1, day: 'Day 3', workoutId: 'db-full-body-b' },
        restDay(1, 'Day 4'),
        { week: 1, day: 'Day 5', workoutId: 'db-full-body-a' },
        restDay(1, 'Day 6'),
        restDay(1, 'Day 7'),

        {
          week: 2,
          day: 'Day 1',
          workoutId: 'db-full-body-b',
          notes: 'The A/B order flips this week. Add the smallest jump you own to anything you hit the top of the range on.',
        },
        restDay(2, 'Day 2'),
        { week: 2, day: 'Day 3', workoutId: 'db-full-body-a' },
        restDay(2, 'Day 4'),
        { week: 2, day: 'Day 5', workoutId: 'db-full-body-b' },
        restDay(2, 'Day 6'),
        restDay(2, 'Day 7'),

        {
          week: 3,
          day: 'Day 1',
          workoutId: 'db-full-body-a',
          notes: 'A fourth set everywhere. Hold the weights where they are this week.',
          overrides: [
            { kind: 'exercise', exerciseId: 'db-goblet-squat', config: { sets: 4 } },
            { kind: 'exercise', exerciseId: 'db-floor-press', config: { sets: 4 } },
            { kind: 'exercise', exerciseId: 'db-row', config: { sets: 4 } },
          ],
        },
        restDay(3, 'Day 2'),
        {
          week: 3,
          day: 'Day 3',
          workoutId: 'db-full-body-b',
          overrides: [
            { kind: 'exercise', exerciseId: 'db-romanian-deadlift', config: { sets: 4 } },
            { kind: 'exercise', exerciseId: 'db-overhead-press', config: { sets: 4 } },
            { kind: 'exercise', exerciseId: 'farmers-carry', config: { sets: 4 } },
          ],
        },
        restDay(3, 'Day 4'),
        {
          week: 3,
          day: 'Day 5',
          workoutId: 'db-full-body-a',
          overrides: [
            { kind: 'exercise', exerciseId: 'db-goblet-squat', config: { sets: 4 } },
            { kind: 'exercise', exerciseId: 'db-floor-press', config: { sets: 4 } },
            { kind: 'exercise', exerciseId: 'db-row', config: { sets: 4 } },
          ],
        },
        restDay(3, 'Day 6'),
        restDay(3, 'Day 7'),

        {
          week: 4,
          day: 'Day 1',
          workoutId: 'db-full-body-b',
          notes: 'Heaviest week of the block: four sets, and add weight wherever week 3 felt easy.',
          overrides: [
            { kind: 'exercise', exerciseId: 'db-romanian-deadlift', config: { sets: 4 } },
            { kind: 'exercise', exerciseId: 'db-overhead-press', config: { sets: 4 } },
            { kind: 'exercise', exerciseId: 'farmers-carry', config: { sets: 4 } },
          ],
        },
        restDay(4, 'Day 2'),
        {
          week: 4,
          day: 'Day 3',
          workoutId: 'db-full-body-a',
          overrides: [
            { kind: 'exercise', exerciseId: 'db-goblet-squat', config: { sets: 4 } },
            { kind: 'exercise', exerciseId: 'db-floor-press', config: { sets: 4 } },
            { kind: 'exercise', exerciseId: 'db-row', config: { sets: 4 } },
          ],
        },
        restDay(4, 'Day 4'),
        {
          week: 4,
          day: 'Day 5',
          workoutId: 'db-full-body-b',
          notes:
            'Last session of the block. Completing it loops back to week 1 — run it again from the weights you finished on.',
          overrides: [
            { kind: 'exercise', exerciseId: 'db-romanian-deadlift', config: { sets: 4 } },
            { kind: 'exercise', exerciseId: 'db-overhead-press', config: { sets: 4 } },
            { kind: 'exercise', exerciseId: 'farmers-carry', config: { sets: 4 } },
          ],
        },
        restDay(4, 'Day 6'),
        restDay(4, 'Day 7'),
      ],
    },
  ],
};

/**
 * The seed as the user's language will read it — the single entry point anything seeding a library
 * should call. Falls back to the English structure above for any language with no table.
 *
 * The language is a parameter rather than read here so it stays testable per-language; `library-file`
 * passes whatever i18next is currently on, which is the language the rest of the UI is rendering in.
 */
export function seedLibraryFor(language: string | undefined): Library {
  return localizeSeed(seedLibrary, language);
}
