import { BackSquat, BarbellRow, BenchPress, Deadlift, OverheadPressBarbell, PullUps } from './barbell';
import { BodyweightSquats, GluteBridge, InvertedRows, MountainClimbers, Plank, PushUps, SplitSquats } from './bodyweight';
import { AmrapBodyweight, Burpees, EasyCardio, EmomPushUps } from './conditioning';
import { FarmersCarry, FloorPress, GobletSquat, OverheadPress, Row, RomanianDeadlift } from './dumbbell';
import {
  CleanAndPress,
  Halo,
  KettlebellGobletSquat,
  KettlebellRow,
  KettlebellSwing,
  RackCarry,
  SingleLegDeadlift,
} from './kettlebell';
import { BandRow, HeelRaises, HipHinge, SeatedMarch, SitToStand, StandingBalance, Walk, WallPushUps } from './low-impact';
import type { ExerciseArtComponent } from './types';

/**
 * Which drawing belongs to which exercise, keyed by **id**, across the seed *and* the bundled content
 * packs. One map rather than one per source: a drawing is chosen by id, and nothing downstream knows
 * or cares which library an id arrived from — a pack merged into somebody's library is their library.
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

  // Steady & Strong.
  'ss-sit-to-stand': SitToStand,
  'ss-wall-pushup': WallPushUps,
  'ss-heel-raises': HeelRaises,
  'ss-seated-march': SeatedMarch,
  'ss-standing-balance': StandingBalance,
  'ss-band-row': BandRow,
  'ss-hip-hinge': HipHinge,
  'ss-walk': Walk,

  // Barbell Gym. The cooldown is the first id to reuse another's drawing — the note above said that
  // was fine and expected, and this is the case: "ten easy minutes on whatever is free" is the same
  // picture whether the gym around it has barbells in it or not.
  'gym-back-squat': BackSquat,
  'gym-bench-press': BenchPress,
  'gym-deadlift': Deadlift,
  'gym-overhead-press': OverheadPressBarbell,
  'gym-barbell-row': BarbellRow,
  'gym-pullups': PullUps,
  'gym-cooldown': EasyCardio,

  // Kettlebell Basics.
  'kb-swing': KettlebellSwing,
  'kb-goblet-squat': KettlebellGobletSquat,
  'kb-clean-press': CleanAndPress,
  'kb-single-leg-deadlift': SingleLegDeadlift,
  'kb-row': KettlebellRow,
  'kb-halo': Halo,
  'kb-carry': RackCarry,
};

/**
 * Shipped exercises that deliberately have no drawing. Rest is the whole list — one entry per
 * library, since every pack ships its own rest rather than borrowing the seed's — because there is no
 * posture to draw, and a figure sitting down would suggest one.
 *
 * This exists so the coverage test can tell "we decided against art here" from "someone added an
 * exercise and forgot the drawing" — without it, the second case is silent.
 */
export const NO_ART: readonly string[] = ['rest', 'ss-rest', 'gym-rest', 'kb-rest'];
