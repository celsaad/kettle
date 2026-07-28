/**
 * Tip jar domain: product identity and the locally-persisted supporter state.
 *
 * Kettle has no backend, so there is nothing to verify a purchase receipt against — a tip is
 * recorded on the strength of Play's own success callback. The worst case is someone contriving a
 * free tip, which costs nothing that was ever owed.
 */
import { z } from 'zod';

/**
 * Ordered smallest to largest. This array *is* the display order — deliberately not derived from
 * the fetched price, which Android reports as an optional field (`price?: number | null`) and which
 * would leave the list unordered exactly when the store returns partial data.
 */
export const TIP_TIERS = ['small', 'medium', 'large'] as const;

export type TipTier = (typeof TIP_TIERS)[number];

/**
 * Play Console product IDs. These are permanent once created — a product ID cannot be renamed or
 * reused after deletion — so treat them as fixed identifiers rather than something to tidy up later.
 */
export const TIP_SKUS: Record<TipTier, string> = {
  small: 'tip_small',
  medium: 'tip_medium',
  large: 'tip_large',
};

export const TIP_SKU_LIST: string[] = TIP_TIERS.map((tier) => TIP_SKUS[tier]);

const TIER_BY_SKU: Record<string, TipTier> = Object.fromEntries(
  TIP_TIERS.map((tier) => [TIP_SKUS[tier], tier]),
);

export function tierForSku(sku: string): TipTier | undefined {
  return TIER_BY_SKU[sku];
}

/** A tier ready to render: the store's own localized price string, never formatted by hand. */
export type TipTierOffer = { tier: TipTier; sku: string; displayPrice: string };

/** The subset of a store product this screen needs, so the domain doesn't depend on expo-iap types. */
type FetchedProduct = { id: string; displayPrice: string };

/**
 * Maps fetched store products onto the tiers, in TIP_TIERS order. Unknown SKUs are dropped and
 * missing ones simply don't appear, so a partially-configured Play Console degrades to fewer
 * buttons rather than a broken screen.
 */
export function toTipTierOffers(products: readonly FetchedProduct[]): TipTierOffer[] {
  const offers: TipTierOffer[] = [];
  for (const tier of TIP_TIERS) {
    const sku = TIP_SKUS[tier];
    const product = products.find((candidate) => candidate.id === sku);
    if (product) offers.push({ tier, sku, displayPrice: product.displayPrice });
  }
  return offers;
}

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

/** Pure state transition, so the store stays a thin persistence wrapper around it. */
export function withTipRecorded(current: SupporterState, tier: TipTier, at: Date): SupporterState {
  return { tipCount: current.tipCount + 1, lastTipAt: at.toISOString(), lastTier: tier };
}

export function hasTipped(state: SupporterState): boolean {
  return state.tipCount > 0;
}
