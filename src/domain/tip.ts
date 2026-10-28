/**
 * Tip jar domain: the supporter file RN Settings reads.
 *
 * The tip jar itself is the native shell's from M4 (#198): the tiers, the store and the writes live in
 * `native/android/shared/.../Tip.kt` and the Support screen beside the shell. What stays here is the
 * read, which has to agree with native's, its writer (`conformance/supporter.yaml`).
 */
import { z } from 'zod';

/** Smallest to largest, the order native's Support lists them in. */
export const TIP_TIERS = ['small', 'medium', 'large'] as const;

export type TipTier = (typeof TIP_TIERS)[number];

/**
 * App-owned, written only by the app. Kept as JSON next to the library rather than inside
 * exercises.yaml: the YAML file is the user's to hand-edit, and purchase state isn't theirs to edit.
 */
export type SupporterState = {
  /** Tips are consumable, so this counts repeat tips rather than being a boolean. */
  tipCount: number;
  /** ISO-8601. Null only in the empty state. */
  lastTipAt: string | null;
  lastTier: TipTier | null;
};

export const supporterStateSchema = z.object({
  tipCount: z.number().int().nonnegative(),
  lastTipAt: z.string().min(1).nullable(),
  lastTier: z.enum(TIP_TIERS).nullable(),
});

export const EMPTY_SUPPORTER_STATE: SupporterState = { tipCount: 0, lastTipAt: null, lastTier: null };

/**
 * What a supporter.json says. Anything the schema refuses, malformed JSON included, reads as the empty
 * state: the file is app-written, so there is no user to report a parse error to. Held to the native
 * shell's reader, its writer from M4 on, by `conformance/supporter.yaml`.
 */
export function parseSupporterState(text: string): SupporterState {
  try {
    const parsed = supporterStateSchema.safeParse(JSON.parse(text));
    return parsed.success ? parsed.data : EMPTY_SUPPORTER_STATE;
  } catch {
    return EMPTY_SUPPORTER_STATE;
  }
}
