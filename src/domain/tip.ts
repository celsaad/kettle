/**
 * Tip jar domain: product identity and the locally-persisted supporter state.
 *
 * Kettle has no backend, so there is nothing to verify a purchase receipt against — a tip is
 * recorded on the strength of the store's own success callback. The worst case is someone contriving
 * a free tip, which costs nothing that was ever owed.
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
 * Store product IDs, the same three in Play Console and App Store Connect. These are permanent once
 * created — a product ID cannot be renamed or reused after deletion — so treat them as fixed
 * identifiers rather than something to tidy up later.
 */
export const TIP_SKUS: Record<TipTier, string> = {
  small: 'tip_small',
  medium: 'tip_medium',
  large: 'tip_large',
};

export const TIP_SKU_LIST: string[] = TIP_TIERS.map((tier) => TIP_SKUS[tier]);

const TIER_BY_SKU: Record<string, TipTier> = Object.fromEntries(TIP_TIERS.map((tier) => [TIP_SKUS[tier], tier]));

export function tierForSku(sku: string): TipTier | undefined {
  return TIER_BY_SKU[sku];
}

/**
 * The purchase request for a tier, shaped for both stores at once.
 *
 * Both keys go in every time rather than being selected by `Platform.OS`: the request object is
 * per-SDK ("apple", "google") and the native side reads only its own, so one shape is correct
 * everywhere and there is no platform branch to get wrong. The asymmetry is the stores' own —
 * StoreKit 2 takes a single `sku`, Play Billing takes a `skus` array — and a request carrying only
 * `google` buys nothing on iOS while failing silently on the one screen that asks for money.
 */
export function tipPurchaseRequest(sku: string): {
  request: { apple: { sku: string }; google: { skus: string[] } };
  type: 'in-app';
} {
  return { request: { apple: { sku }, google: { skus: [sku] } }, type: 'in-app' };
}

/**
 * Which store the tip copy names. Two of the screen's strings say it out loud and neither can say
 * both, so the platform picks a *key path* and the locale bundles keep the sentences — the house
 * rule is that no user-facing string lives outside them, which a `Platform.select` over two
 * translated paragraphs would break in all three languages at once.
 *
 * Takes the platform rather than reading it, so it stays pure and both branches are testable without
 * a React tree. Web reaches no store at all and takes the Play wording: the browser build is a
 * preview of the app a reader would install from Play.
 */
export function tipStoreCopyKeys(os: string): { why: string; unavailable: string } {
  return os === 'ios'
    ? { why: 'support.whyAppStore', unavailable: 'support.storeUnavailableAppStore' }
    : { why: 'support.whyPlay', unavailable: 'support.storeUnavailablePlay' };
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
