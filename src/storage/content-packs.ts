import type { Library, ProgramOverride, ProgramWeek } from '@/domain/types';
import { contentPackTranslations } from '@/storage/content-pack-translations';
import { localizeLibrary, translationFor } from '@/storage/library-translation';

/**
 * Curated libraries the app ships **without seeding** — browsable on the import screen and merged in
 * on a tap, exactly as a hand-written file would be.
 *
 * **Why these aren't in `seed-library.ts`.** The seed is written once, on the first launch that finds
 * no `exercises.yaml`, which makes it structurally incapable of reaching a device that already has
 * the app — the population most likely to want more content. It is also the web build's entire
 * library and the target a corrupt library is reset to, so every exercise added to it enlarges the
 * thing a recovery hands back (decision log). Packs answer both: they arrive with an app update,
 * every install can take them or leave them, and the seed stays the small honest thing a reseed can
 * restore.
 *
 * **Ids are prefixed per pack, and that is the load-bearing part.** `mergeLibraries` replaces whole
 * objects by id, so an unprefixed `squat` in a pack would overwrite a `squat` the user has written
 * and logged against for months. Every id here carries its pack's prefix — the same promise
 * `site/examples/*.yaml` makes, held here by `content-packs.test.ts`.
 *
 * Each pack ships its own rest exercise rather than referencing the seed's. That looks like
 * duplication and isn't: a merge validates references against the *merged* library, so a pack
 * pointing at `rest` would be refused outright for anyone who had deleted or renamed it.
 *
 * The content bar is the seed's, unchanged: `notes` describe **the app's own progression model** —
 * how a range is beaten, what a rep counts, which number to log — and never form, injury or diet.
 * That line is what keeps the app describing its data rather than prescribing training, and it is
 * why a pack that suits older adults still says nothing about anybody's body.
 */
export type ContentPack = {
  id: string;
  /**
   * The prefix every id in `library` carries. Declared rather than inferred so the promise above is
   * a value the test can check the content against — inferring it from the content would make the
   * test agree with whatever the content happened to say, including a typo.
   */
  idPrefix: string;
  /**
   * The structural definition, in English. Name and blurb are **not** here: those are the import
   * screen's own chrome, they re-render on a language change, and they live in the locale bundles
   * under `import.packs.<id>`. What a pack *writes* is frozen at merge time; what the row says about
   * it is not.
   */
  library: Library;
};

/**
 * A scheduled day off. Longhand for the same reason `seed-library.ts` writes them longhand: the home
 * screen spends one rest entry per calendar day, so a three-session week written as three entries
 * rolls into the next week the day after the last session. Seven entries make the week a week.
 */
function restDay(week: number, day: string): ProgramWeek {
  return { week, day, restDay: true };
}

type TrainingDay = { workoutId: string; notes?: string; overrides?: ProgramOverride[] };

/**
 * One week of the three-on/four-off shape all three packs use, written days 1 through 7 in order.
 *
 * Weeks run in the order the array holds them, so this helper is where that ordering is decided once
 * instead of across eighty-four hand-written entries — which is also the only place it could go
 * wrong. It takes exactly three training days on purpose: a pack wanting a different split writes its
 * weeks out by hand rather than growing a parameter here.
 */
function threeDayWeek(week: number, [first, second, third]: [TrainingDay, TrainingDay, TrainingDay]): ProgramWeek[] {
  return [
    { week, day: 'Day 1', ...first },
    restDay(week, 'Day 2'),
    { week, day: 'Day 3', ...second },
    restDay(week, 'Day 4'),
    { week, day: 'Day 5', ...third },
    restDay(week, 'Day 6'),
    restDay(week, 'Day 7'),
  ];
}

/**
 * The fourth-set bump weeks 3 and 4 of every pack program apply, spelled out per week rather than
 * carried forward — overrides don't inherit, so week 4 repeats week 3's or silently drops back to
 * three sets.
 */
function fourSets(exerciseIds: string[]): ProgramOverride[] {
  return exerciseIds.map((exerciseId) => ({ kind: 'exercise', exerciseId, config: { sets: 4 } }));
}

// --- Steady & Strong -------------------------------------------------------------------------
// Standing and seated work with a chair or a wall for support, and nothing that starts or ends on
// the floor. That constraint is the whole pack: it is what makes it runnable by someone who would
// otherwise stop at the first push-up.

const steadyStrengthLibrary: Library = {
  version: 1,
  exercises: [
    { id: 'ss-rest', name: 'Rest', type: 'rest', config: { durationSec: 60 } },
    {
      id: 'ss-sit-to-stand',
      name: 'Sit to Stand',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 6, targetRepsMax: 12, restSec: 60 },
      notes: 'Up from a chair and back down. Hit 12 on every set twice before the program adds a fourth.',
    },
    {
      id: 'ss-wall-pushup',
      name: 'Wall Push-ups',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 8, targetRepsMax: 15, restSec: 45 },
      notes: 'Step your feet further from the wall to make it harder once you own 15 on every set.',
    },
    {
      id: 'ss-heel-raises',
      name: 'Heel Raises',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 10, targetRepsMax: 20, restSec: 45 },
      notes: 'A chair back or a counter is the support. Take a hand off it before you add reps.',
    },
    {
      id: 'ss-seated-march',
      name: 'Seated March',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 10, targetRepsMax: 20, restSec: 45 },
      notes: 'One left and one right is two reps, so the number you log counts both legs.',
    },
    {
      id: 'ss-standing-balance',
      name: 'Standing Balance',
      type: 'timed_hold',
      config: { sets: 3, holdSecMin: 10, holdSecMax: 30, restSec: 45 },
      notes: 'Held per side. Raise the whole range by 5s once you hit 30s on every set.',
    },
    {
      id: 'ss-band-row',
      name: 'Band Row',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 8, targetRepsMax: 12, restSec: 60 },
      notes: 'The band is the load, so a step further back is the smallest jump you have once you hit 12 on every set.',
    },
    {
      id: 'ss-hip-hinge',
      name: 'Hip Hinge',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 8, targetRepsMax: 12, restSec: 60 },
      notes: 'Hands on a chair back. Move them down the chair as the range grows.',
    },
    {
      id: 'ss-walk',
      name: 'Walk',
      type: 'cardio',
      config: { durationSec: 900 },
      notes: 'Fifteen minutes to start. Add a distance to the config if you want that tracked too.',
    },
  ],
  workouts: [
    {
      id: 'ss-strength-a',
      name: 'Steady · Strength A',
      blocks: [
        { kind: 'exercise', exerciseId: 'ss-sit-to-stand' },
        { kind: 'exercise', exerciseId: 'ss-rest' },
        { kind: 'exercise', exerciseId: 'ss-wall-pushup' },
        { kind: 'exercise', exerciseId: 'ss-rest' },
        { kind: 'exercise', exerciseId: 'ss-heel-raises' },
      ],
    },
    {
      id: 'ss-strength-b',
      name: 'Steady · Strength B',
      blocks: [
        { kind: 'exercise', exerciseId: 'ss-hip-hinge' },
        { kind: 'exercise', exerciseId: 'ss-rest' },
        { kind: 'exercise', exerciseId: 'ss-band-row' },
        { kind: 'exercise', exerciseId: 'ss-rest' },
        { kind: 'exercise', exerciseId: 'ss-seated-march' },
      ],
    },
    {
      id: 'ss-balance-walk',
      name: 'Steady · Balance & Walk',
      blocks: [
        { kind: 'exercise', exerciseId: 'ss-standing-balance' },
        { kind: 'exercise', exerciseId: 'ss-rest' },
        { kind: 'exercise', exerciseId: 'ss-walk' },
      ],
    },
  ],
  programs: [
    {
      id: 'ss-4-weeks',
      name: 'Steady & Strong · 4 Weeks',
      weeks: [
        ...threeDayWeek(1, [
          {
            workoutId: 'ss-strength-a',
            notes: 'Baseline week. Log where you actually land in each range — the bottom of it is a real answer.',
          },
          { workoutId: 'ss-strength-b' },
          { workoutId: 'ss-balance-walk' },
        ]),
        ...threeDayWeek(2, [
          { workoutId: 'ss-strength-a', notes: 'Same three sessions. One more rep than week 1, on every set.' },
          { workoutId: 'ss-strength-b' },
          { workoutId: 'ss-balance-walk' },
        ]),
        ...threeDayWeek(3, [
          {
            workoutId: 'ss-strength-a',
            notes: 'A fourth set on the strength days. Keep the reps where they were rather than pushing both at once.',
            overrides: fourSets(['ss-sit-to-stand', 'ss-wall-pushup', 'ss-heel-raises']),
          },
          { workoutId: 'ss-strength-b', overrides: fourSets(['ss-hip-hinge', 'ss-band-row', 'ss-seated-march']) },
          { workoutId: 'ss-balance-walk' },
        ]),
        ...threeDayWeek(4, [
          {
            workoutId: 'ss-strength-a',
            notes: 'Last week of the block. Now push the reps toward the top of each range.',
            overrides: fourSets(['ss-sit-to-stand', 'ss-wall-pushup', 'ss-heel-raises']),
          },
          { workoutId: 'ss-strength-b', overrides: fourSets(['ss-hip-hinge', 'ss-band-row', 'ss-seated-march']) },
          {
            workoutId: 'ss-balance-walk',
            notes: 'Completing this loops back to week 1 — run it again with the top of each range as your new floor.',
          },
        ]),
      ],
    },
  ],
};

// --- Barbell Gym -----------------------------------------------------------------------------
// Six lifts across two alternating days, a bar and a rack. Deliberately not a machine circuit:
// machines vary too much between gyms for a shipped library to name one and still be right.

const barbellGymLibrary: Library = {
  version: 1,
  exercises: [
    { id: 'gym-rest', name: 'Rest', type: 'rest', config: { durationSec: 150 } },
    {
      id: 'gym-back-squat',
      name: 'Back Squat',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 5, targetRepsMax: 8, restSec: 180 },
      notes:
        'No weight is seeded — set yours on the first session, then add the smallest plate pair the gym has once you hit 8 on every set.',
    },
    {
      id: 'gym-bench-press',
      name: 'Bench Press',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 5, targetRepsMax: 8, restSec: 180 },
      notes: 'Add weight once you hit 8 on every set, not on your best one.',
    },
    {
      id: 'gym-deadlift',
      name: 'Deadlift',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 3, targetRepsMax: 5, restSec: 180 },
      notes: 'A shorter range than the rest of the block, so the top of it arrives sooner. Add weight at 5 on every set.',
    },
    {
      id: 'gym-overhead-press',
      name: 'Overhead Press',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 5, targetRepsMax: 8, restSec: 150 },
      notes: 'The smallest jump you own is a big one here. Expect to sit at the top of the range for a while.',
    },
    {
      id: 'gym-barbell-row',
      name: 'Barbell Row',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 6, targetRepsMax: 10, restSec: 150 },
    },
    {
      id: 'gym-pullups',
      name: 'Pull-ups',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 3, targetRepsMax: 8, restSec: 120 },
      notes: 'Log assisted reps as reps. The number you beat next time is your own either way.',
    },
    {
      id: 'gym-cooldown',
      name: 'Cooldown',
      type: 'cardio',
      config: { durationSec: 600 },
      notes: 'Bike, row, walk — whatever is free. Add a distance to the config if you want that tracked too.',
    },
  ],
  workouts: [
    {
      id: 'gym-day-a',
      name: 'Gym · Day A',
      blocks: [
        { kind: 'exercise', exerciseId: 'gym-back-squat' },
        { kind: 'exercise', exerciseId: 'gym-rest' },
        { kind: 'exercise', exerciseId: 'gym-bench-press' },
        { kind: 'exercise', exerciseId: 'gym-rest' },
        { kind: 'exercise', exerciseId: 'gym-barbell-row' },
        { kind: 'exercise', exerciseId: 'gym-rest', configOverride: { durationSec: 90 } },
        { kind: 'exercise', exerciseId: 'gym-cooldown' },
      ],
    },
    {
      id: 'gym-day-b',
      name: 'Gym · Day B',
      blocks: [
        { kind: 'exercise', exerciseId: 'gym-deadlift' },
        { kind: 'exercise', exerciseId: 'gym-rest' },
        { kind: 'exercise', exerciseId: 'gym-overhead-press' },
        { kind: 'exercise', exerciseId: 'gym-rest' },
        { kind: 'exercise', exerciseId: 'gym-pullups' },
        { kind: 'exercise', exerciseId: 'gym-rest', configOverride: { durationSec: 90 } },
        { kind: 'exercise', exerciseId: 'gym-cooldown' },
      ],
    },
  ],
  programs: [
    {
      id: 'gym-4-weeks',
      name: 'Barbell Gym · 4 Weeks',
      weeks: [
        // A/B/A then B/A/B, so each day lands three times a fortnight rather than twice a week.
        ...threeDayWeek(1, [
          {
            workoutId: 'gym-day-a',
            notes: 'Baseline week. Pick weights you could stop 2 reps shy of failure with, and write them into each lift.',
          },
          { workoutId: 'gym-day-b' },
          { workoutId: 'gym-day-a' },
        ]),
        ...threeDayWeek(2, [
          {
            workoutId: 'gym-day-b',
            notes:
              'The A/B order flips this week. Add the smallest jump you own to anything you hit the top of the range on.',
          },
          { workoutId: 'gym-day-a' },
          { workoutId: 'gym-day-b' },
        ]),
        ...threeDayWeek(3, [
          {
            workoutId: 'gym-day-a',
            notes: 'A fourth set on the main lifts. Hold the weights where they are this week.',
            overrides: fourSets(['gym-back-squat', 'gym-bench-press', 'gym-barbell-row']),
          },
          { workoutId: 'gym-day-b', overrides: fourSets(['gym-deadlift', 'gym-overhead-press', 'gym-pullups']) },
          { workoutId: 'gym-day-a', overrides: fourSets(['gym-back-squat', 'gym-bench-press', 'gym-barbell-row']) },
        ]),
        ...threeDayWeek(4, [
          {
            workoutId: 'gym-day-b',
            notes: 'Heaviest week of the block: four sets, and add weight wherever week 3 felt easy.',
            overrides: fourSets(['gym-deadlift', 'gym-overhead-press', 'gym-pullups']),
          },
          { workoutId: 'gym-day-a', overrides: fourSets(['gym-back-squat', 'gym-bench-press', 'gym-barbell-row']) },
          {
            workoutId: 'gym-day-b',
            notes:
              'Last session of the block. Completing it loops back to week 1 — run it again from the weights you finished on.',
            overrides: fourSets(['gym-deadlift', 'gym-overhead-press', 'gym-pullups']),
          },
        ]),
      ],
    },
  ],
};

// --- Kettlebell Basics -----------------------------------------------------------------------
// One bell covers all of it. The swing is a `hiit` entry rather than a `reps` one because that is how
// it is actually run — timed work against timed rest — and it gives the pack a live example of a
// type the other two don't use.

const kettlebellLibrary: Library = {
  version: 1,
  exercises: [
    { id: 'kb-rest', name: 'Rest', type: 'rest', config: { durationSec: 75 } },
    {
      id: 'kb-swing',
      name: 'Kettlebell Swing',
      type: 'hiit',
      config: { workSec: 30, restSec: 30, rounds: 8 },
      notes: 'Work and rest are both 30s, so the whole block is eight minutes. Add rounds before you add weight.',
    },
    {
      id: 'kb-goblet-squat',
      name: 'Kettlebell Goblet Squat',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 8, targetRepsMax: 12, restSec: 90 },
      notes: 'A single bell held at the chest. Add weight once you hit 12 on every set.',
    },
    {
      id: 'kb-clean-press',
      name: 'Clean & Press',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 5, targetRepsMax: 8, restSec: 90 },
      notes: 'Reps are per side — log the weaker one, so the number you beat next time is the honest one.',
    },
    {
      id: 'kb-single-leg-deadlift',
      name: 'Single-leg Deadlift',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 6, targetRepsMax: 10, restSec: 75 },
      notes: 'Reps are per leg.',
    },
    {
      id: 'kb-row',
      name: 'Kettlebell Row',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 8, targetRepsMax: 12, restSec: 75 },
      notes: 'Reps are per side.',
    },
    {
      id: 'kb-halo',
      name: 'Halo',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 5, targetRepsMax: 10, restSec: 60 },
      notes: 'One circle each way is one rep.',
    },
    {
      id: 'kb-carry',
      name: 'Rack Carry',
      type: 'timed_hold',
      config: { sets: 3, holdSecMin: 30, holdSecMax: 60, restSec: 60 },
      notes: 'Add time before adding weight.',
    },
  ],
  workouts: [
    {
      id: 'kb-strength-a',
      name: 'Kettlebell · Strength A',
      blocks: [
        { kind: 'exercise', exerciseId: 'kb-goblet-squat' },
        { kind: 'exercise', exerciseId: 'kb-rest' },
        { kind: 'exercise', exerciseId: 'kb-row' },
        { kind: 'exercise', exerciseId: 'kb-rest' },
        { kind: 'exercise', exerciseId: 'kb-carry' },
      ],
    },
    {
      id: 'kb-strength-b',
      name: 'Kettlebell · Strength B',
      blocks: [
        { kind: 'exercise', exerciseId: 'kb-clean-press' },
        { kind: 'exercise', exerciseId: 'kb-rest' },
        { kind: 'exercise', exerciseId: 'kb-single-leg-deadlift' },
        { kind: 'exercise', exerciseId: 'kb-rest' },
        { kind: 'exercise', exerciseId: 'kb-halo' },
      ],
    },
    {
      id: 'kb-swing-day',
      name: 'Kettlebell · Swings',
      blocks: [
        { kind: 'exercise', exerciseId: 'kb-swing' },
        { kind: 'exercise', exerciseId: 'kb-rest', configOverride: { durationSec: 120 } },
        { kind: 'exercise', exerciseId: 'kb-carry' },
      ],
    },
  ],
  programs: [
    {
      id: 'kb-4-weeks',
      name: 'Kettlebell Basics · 4 Weeks',
      weeks: [
        ...threeDayWeek(1, [
          {
            workoutId: 'kb-strength-a',
            notes: 'Baseline week. One bell is enough — write its weight into each exercise as you go.',
          },
          { workoutId: 'kb-strength-b' },
          { workoutId: 'kb-swing-day' },
        ]),
        ...threeDayWeek(2, [
          {
            workoutId: 'kb-strength-a',
            notes: 'Same three sessions. Beat week 1 by a rep per set before you touch the weight.',
          },
          { workoutId: 'kb-strength-b' },
          { workoutId: 'kb-swing-day' },
        ]),
        ...threeDayWeek(3, [
          {
            workoutId: 'kb-strength-a',
            notes: 'A fourth set on the strength days, and two more rounds of swings.',
            overrides: fourSets(['kb-goblet-squat', 'kb-row', 'kb-carry']),
          },
          { workoutId: 'kb-strength-b', overrides: fourSets(['kb-clean-press', 'kb-single-leg-deadlift', 'kb-halo']) },
          { workoutId: 'kb-swing-day', overrides: [{ kind: 'exercise', exerciseId: 'kb-swing', config: { rounds: 10 } }] },
        ]),
        ...threeDayWeek(4, [
          {
            workoutId: 'kb-strength-a',
            notes: 'Heaviest week of the block. Add weight wherever week 3 felt easy.',
            overrides: fourSets(['kb-goblet-squat', 'kb-row', 'kb-carry']),
          },
          { workoutId: 'kb-strength-b', overrides: fourSets(['kb-clean-press', 'kb-single-leg-deadlift', 'kb-halo']) },
          {
            workoutId: 'kb-swing-day',
            notes:
              'Last session of the block. Completing it loops back to week 1 — run it again from the weight you finished on.',
            overrides: [{ kind: 'exercise', exerciseId: 'kb-swing', config: { rounds: 10 } }],
          },
        ]),
      ],
    },
  ],
};

/**
 * Every pack the app ships, in the order the import screen lists them.
 *
 * Order is display order and nothing else — unlike `seedLibrary.programs[0]`, no pack is anybody's
 * default, because a pack only exists once somebody has chosen it.
 */
export const contentPacks: ContentPack[] = [
  { id: 'steady-strength', idPrefix: 'ss-', library: steadyStrengthLibrary },
  { id: 'barbell-gym', idPrefix: 'gym-', library: barbellGymLibrary },
  { id: 'kettlebell-basics', idPrefix: 'kb-', library: kettlebellLibrary },
];

/**
 * A pack's library as the user's language will read it — the single entry point anything merging a
 * pack should call.
 *
 * The same freeze applies as to the seed: the strings are picked once, at merge time, and from that
 * moment the merged content is the user's own data. Switching device language afterwards renames
 * nothing, which is exactly what the never-translate-user-data rule requires.
 */
export function contentPackLibrary(pack: ContentPack, language: string | undefined): Library {
  return localizeLibrary(pack.library, translationFor(contentPackTranslations[pack.id] ?? {}, language));
}

/** What a pack row reports it is about to add, counted off the structure so it can't drift from it. */
export function contentPackCounts(pack: ContentPack): { exercises: number; workouts: number; programs: number } {
  return {
    exercises: pack.library.exercises.length,
    workouts: pack.library.workouts.length,
    programs: pack.library.programs.length,
  };
}
