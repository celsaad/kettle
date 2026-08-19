import { BodyweightSquats, GluteBridge, InvertedRows, MountainClimbers, Plank, PushUps, SplitSquats } from './bodyweight';
import { AmrapBodyweight, Burpees, EasyCardio, EmomPushUps } from './conditioning';
import { FarmersCarry, FloorPress, GobletSquat, OverheadPress, Row, RomanianDeadlift } from './dumbbell';
import type { ExerciseArtComponent } from './types';

/**
 * Which drawing belongs to which exercise, keyed by **id**.
 *
 * The id is what makes this safe across languages. `seed-library.ts` is a single English structural
 * definition with hardcoded ids, and `seed-translations.ts` replaces only `name` and `notes` against
 * those same ids — so a Portuguese user's *Flexões* is still `pushups` underneath, and this map is
 * correct in every language the app will ever ship, for free.
 *
 * Two consequences of keying on the id rather than on the name, both accepted deliberately:
 *
 * - The art follows a **rename**, which is right, and would follow a *repurposing* too — a `pushups`
 *   entry edited into something else keeps the push-up drawing. Low stakes, not worth guarding.
 * - A user's own exercise can land on a seeded id: delete the seeded `plank`, create your own
 *   "Plank", and `slugify` hands it `plank` again, art included. That's a feature.
 *
 * The map is one-to-one today, but nothing requires it to be — two ids sharing a component is fine
 * and expected the moment a second push-up variant is seeded. It didn't happen here only because the
 * EMOM and AMRAP entries earned drawings of their own: they have to say "timed" and "repeated", which
 * the bare movement doesn't.
 */
export const EXERCISE_ART: Record<string, ExerciseArtComponent> = {
  pushups: PushUps,
  'bodyweight-squats': BodyweightSquats,
  'inverted-rows': InvertedRows,
  'split-squats': SplitSquats,
  'glute-bridge': GluteBridge,
  plank: Plank,
  'mountain-climbers': MountainClimbers,
  'db-goblet-squat': GobletSquat,
  'db-floor-press': FloorPress,
  'db-row': Row,
  'db-romanian-deadlift': RomanianDeadlift,
  'db-overhead-press': OverheadPress,
  'farmers-carry': FarmersCarry,
  burpees: Burpees,
  'emom-pushups': EmomPushUps,
  'amrap-12-bodyweight': AmrapBodyweight,
  'easy-cardio': EasyCardio,
};

/**
 * Seeded exercises that deliberately have no drawing. Rest is the whole list: there is no posture to
 * draw, and a figure sitting down would suggest one.
 *
 * This exists so the coverage test can tell "we decided against art here" from "someone added a
 * seeded exercise and forgot the drawing" — without it, the second case is silent.
 */
export const NO_ART: readonly string[] = ['rest'];
