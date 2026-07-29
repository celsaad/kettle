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

/**
 * What Settings → Appearance offers: pin a scheme, or defer to the OS.
 *
 * `system` is the intent "follow the device", not an outcome — which is the whole reason this is a
 * three-value preference rather than the `Scheme | null` override it replaced. See `theme-context.tsx`.
 */
export const THEME_PREFERENCES = ['light', 'dark', 'system'] as const;
export type ThemePreference = (typeof THEME_PREFERENCES)[number];

export type Preferences = {
  /** Display only — stored weights are always kilograms, see `domain/units.ts`. */
  unitSystem: UnitSystem;
  themePreference: ThemePreference;
};

export const preferencesSchema = z.object({
  unitSystem: z.enum(UNIT_SYSTEMS),
  // Defaulted, not required, because this field arrived after `preferences.json` was already being
  // written in the field. A required key would fail `safeParse` on every file that predates it, and
  // `loadPreferences` answers a failed parse with `null` — so an unrelated missing key would silently
  // reset the user's *unit* choice too. Any preference added later needs the same treatment.
  themePreference: z.enum(THEME_PREFERENCES).default('system'),
});
