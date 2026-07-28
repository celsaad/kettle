/**
 * App preferences: choices that belong to this install rather than to the library.
 *
 * JSON next to `supporter.json`, deliberately *outside* `exercises.yaml`. The YAML is the file the
 * user hand-edits and shares, and display units aren't part of what a workout means — carrying them
 * in there would hand a recipient someone else's settings along with their exercises, and would make
 * every preference change rewrite the library file.
 *
 * There is no default here on purpose: "no preference stored yet" means *follow the device*, which
 * only the store can resolve (see `state/preferences-store.ts`). Baking a static fallback into this
 * layer would quietly turn an unanswered question into an answer of "metric".
 */
import { z } from 'zod';

import { UNIT_SYSTEMS, type UnitSystem } from '@/domain/units';

export type Preferences = {
  /** Display only — stored weights are always kilograms, see `domain/units.ts`. */
  unitSystem: UnitSystem;
};

export const preferencesSchema = z.object({
  unitSystem: z.enum(UNIT_SYSTEMS),
});
