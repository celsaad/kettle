/**
 * Weight display units.
 *
 * **Storage stays metric.** The library is a portable file the user is invited to hand-edit, export
 * and share, so `target_weight: 60` has to mean the same thing wherever it is opened — a file whose
 * numbers changed meaning with the reader's settings would be unshareable. Conversion happens only at
 * the display/input boundary, which is this module.
 */

export const UNIT_SYSTEMS = ['metric', 'imperial'] as const;
export type UnitSystem = (typeof UNIT_SYSTEMS)[number];

/** Exact by definition (the international avoirdupois pound), not an approximation to tune. */
const KG_PER_LB = 0.45359237;

/**
 * Display precision differs per system, and its pairing with `STORED_DECIMALS` is what makes a
 * pound-authored value survive the trip to disk and back.
 *
 * Storing kg at two decimals gives a resolution of 0.01 kg ≈ 0.022 lb. Rounding the pound *display*
 * to 0.1 lb is coarser than that, so a value typed in pounds always redisplays as itself: 135 lb →
 * 61.23 kg → 135.0 lb. Showing pounds at two decimals instead would put that quantisation on screen
 * as "134.99", in front of the user who just typed 135.
 *
 * The reverse trip — a kg-authored value shown in pounds and written back — is lossy under any fixed
 * rounding, since 0.1 lb is coarser than 0.01 kg. That one isn't solved here: `buildExercise` keeps
 * the stored kilograms when the field wasn't edited, which is the honest fix rather than a rounding
 * rule that makes 100 kg into 100.02.
 */
const DISPLAY_DECIMALS: Record<UnitSystem, number> = { metric: 2, imperial: 1 };
const STORED_DECIMALS = 2;

function round(value: number, decimals: number): number {
  const factor = 10 ** decimals;
  return Math.round(value * factor) / factor;
}

/** Stored kilograms → the number to show the user, in their own unit. */
export function toDisplayWeight(kg: number, system: UnitSystem): number {
  return round(system === 'imperial' ? kg / KG_PER_LB : kg, DISPLAY_DECIMALS[system]);
}

/** A number the user entered in their own unit → kilograms to store. */
export function fromDisplayWeight(value: number, system: UnitSystem): number {
  // Metric passes straight through: the number typed *is* the stored value, so rounding it here would
  // silently edit input the user can see.
  return system === 'imperial' ? round(value * KG_PER_LB, STORED_DECIMALS) : value;
}

/**
 * The runner's −/+ load step, in display units.
 *
 * 2.5 kg is the smallest real jump on a bar or belt (a pair of 1.25 kg plates) and the spacing of
 * most metric dumbbell racks. Its imperial counterpart is a pair of 2.5 lb plates — 5 lb — which is
 * also how US racks step. Converting the metric step instead would give 5.5 lb, a number that matches
 * no plate anyone owns; the point of the stepper is to move in increments the equipment actually has.
 */
export function weightStep(system: UnitSystem): number {
  return system === 'imperial' ? 5 : 2.5;
}
