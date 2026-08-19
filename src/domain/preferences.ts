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

/**
 * How long after the last session the opt-in nudge lands, and the local hour it lands at.
 *
 * Two days, not one: a rest day is training, not a lapse, and a reminder that fires the morning after
 * a heavy session is nagging someone who is doing the right thing. 18:00 because the notification is
 * useful when there is still an evening left to train in.
 */
export const REST_DAY_REMINDER_DAYS = 2;
export const REST_DAY_REMINDER_HOUR = 18;

export type Preferences = {
  /** Display only — stored weights are always kilograms, see `domain/units.ts`. */
  unitSystem: UnitSystem;
  themePreference: ThemePreference;
  /** Per-list, not one shared setting: ordering exercises by name says nothing about programs. */
  /**
   * Off unless the user asks for it, and never on by default. A local notification is the one thing
   * this app does that reaches outside itself, and its whole pitch is that it doesn't bother anyone.
   */
  restDayReminder: boolean;
  /**
   * The runner's audio cues — the countdown tick, the exercise-change ding and the milestone chime.
   *
   * On by default, unlike the reminder above: a reminder reaches outside the app and has to be asked
   * for, while these are the timer working as designed, and someone who has been training with them
   * expects the tick to keep ticking after an update. The setting exists at all because
   * `setAudioModeAsync({ playsInSilentMode: true })` opts the runner out of the phone's own mute —
   * having taken the platform's switch away, the app owes the user one of its own.
   *
   * Sound only. Haptics stay on the OS's switch, since the runner never overrode that one.
   */
  sessionSounds: boolean;
  /**
   * The folder the user nominated for backups — a SAF `content://` tree URI on Android, `null` until
   * they choose one. App-owned state, which is why it lives here rather than in the YAML library: the
   * library is a file people export and share, and a recipient has no business inheriting a path into
   * someone else's phone.
   */
  backupFolderUri: string | null;
};

export const preferencesSchema = z.object({
  unitSystem: z.enum(UNIT_SYSTEMS),
  // Defaulted, not required, because this field arrived after `preferences.json` was already being
  // written in the field. A required key would fail `safeParse` on every file that predates it, and
  // `loadPreferences` answers a failed parse with `null` — so an unrelated missing key would silently
  // reset the user's *unit* choice too. Any preference added later needs the same treatment.
  themePreference: z.enum(THEME_PREFERENCES).default('system'),
  // Defaulted for the same reason as the two above — it arrived after `preferences.json` was already
  // being written in the field — and `false` is also the value the product wants: an opt-in that
  // defaulted to true for existing installs would start notifying people who never asked.
  restDayReminder: z.boolean().default(false),
  // Defaulted for the same reason as every field above it, and the reason is worth separating from
  // the value: `true` is what the product wants (see the note on the type), while the `.default()`
  // is what stops a `preferences.json` written before this shipped from failing `safeParse` and
  // taking units, appearance and the backup folder down with it.
  sessionSounds: z.boolean().default(true),
  // Same treatment again, and the stakes are higher than for the others: this arrived last, so *every*
  // `preferences.json` in the field predates it, and a required key here would fail `safeParse` for
  // every existing install at once — resetting appearance, units and list order along with it.
  backupFolderUri: z.string().nullable().default(null),
});
