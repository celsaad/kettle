import {
  EMPTY_SUPPORTER_STATE,
  TIP_SKUS,
  TIP_SKU_LIST,
  hasTipped,
  supporterStateSchema,
  tierForSku,
  tipPurchaseRequest,
  tipStoreCopyKeys,
  toTipTierOffers,
  withTipRecorded,
} from '@/domain/tip';
import en from '@/i18n/locales/en.json';

describe('toTipTierOffers', () => {
  const product = (id: string, displayPrice: string) => ({ id, displayPrice });

  /**
   * The store returns products in whatever order it likes, and Android reports `price` as an
   * optional field — so ordering is taken from TIP_TIERS, not from the response or the amount.
   */
  it('orders tiers smallest to largest regardless of the order products arrive in', () => {
    const offers = toTipTierOffers([
      product(TIP_SKUS.large, 'R$ 29,90'),
      product(TIP_SKUS.small, 'R$ 4,90'),
      product(TIP_SKUS.medium, 'R$ 14,90'),
    ]);

    expect(offers.map((offer) => offer.tier)).toEqual(['small', 'medium', 'large']);
  });

  it("carries the store's own localized price string through untouched", () => {
    const offers = toTipTierOffers([product(TIP_SKUS.small, 'R$ 4,90')]);

    expect(offers).toEqual([{ tier: 'small', sku: TIP_SKUS.small, displayPrice: 'R$ 4,90' }]);
  });

  // A half-configured Play Console should cost the user a button, not the whole screen.
  it('drops unknown SKUs and omits tiers the store did not return', () => {
    const offers = toTipTierOffers([product('some_other_product', '$1'), product(TIP_SKUS.medium, 'R$ 14,90')]);

    expect(offers.map((offer) => offer.tier)).toEqual(['medium']);
  });

  it('returns nothing when the store returned nothing', () => {
    expect(toTipTierOffers([])).toEqual([]);
  });
});

describe('tierForSku', () => {
  it('maps every published SKU back to its tier', () => {
    expect(TIP_SKU_LIST.map((sku) => tierForSku(sku))).toEqual(['small', 'medium', 'large']);
  });

  it('returns undefined for a SKU the app does not sell', () => {
    expect(tierForSku('tip_enormous')).toBeUndefined();
  });
});

describe('withTipRecorded', () => {
  it('counts repeat tips rather than flipping a boolean', () => {
    const first = withTipRecorded(EMPTY_SUPPORTER_STATE, 'small', new Date('2026-07-28T10:00:00Z'));
    const second = withTipRecorded(first, 'large', new Date('2026-08-01T10:00:00Z'));

    expect(second.tipCount).toBe(2);
    expect(second.lastTier).toBe('large');
    expect(second.lastTipAt).toBe('2026-08-01T10:00:00.000Z');
  });

  it('does not mutate the state it was given', () => {
    withTipRecorded(EMPTY_SUPPORTER_STATE, 'small', new Date());

    expect(EMPTY_SUPPORTER_STATE).toEqual({ tipCount: 0, lastTipAt: null, lastTier: null });
  });
});

describe('tipPurchaseRequest', () => {
  /**
   * The regression this pins: the request used to carry `google` alone, which on iOS asks StoreKit
   * for nothing and fails on the one screen that asks the user for money. Nothing else in the app
   * would have noticed — every other line of the purchase path is platform-neutral.
   */
  it('names the SKU to both stores, each in its own shape', () => {
    expect(tipPurchaseRequest(TIP_SKUS.medium)).toEqual({
      request: { apple: { sku: 'tip_medium' }, google: { skus: ['tip_medium'] } },
      type: 'in-app',
    });
  });

  // 'in-app' rather than 'subs' is what keeps a tip a one-off: nothing here is a subscription.
  it('requests a one-off purchase for every published tier', () => {
    for (const sku of TIP_SKU_LIST) {
      const request = tipPurchaseRequest(sku);
      expect(request.type).toBe('in-app');
      expect(request.request.apple.sku).toBe(sku);
      expect(request.request.google.skus).toEqual([sku]);
    }
  });
});

describe('tipStoreCopyKeys', () => {
  it('names the App Store on iOS and Play everywhere else', () => {
    expect(tipStoreCopyKeys('ios')).toEqual({
      why: 'support.whyAppStore',
      unavailable: 'support.storeUnavailableAppStore',
    });
    expect(tipStoreCopyKeys('android')).toEqual({ why: 'support.whyPlay', unavailable: 'support.storeUnavailablePlay' });
    expect(tipStoreCopyKeys('web')).toEqual({ why: 'support.whyPlay', unavailable: 'support.storeUnavailablePlay' });
  });

  /**
   * A key path nothing holds renders as the path itself, silently — the same failure mode
   * `locale-bundles.test.ts` guards between bundles, which it can't see here because the producer is
   * the thing that could be wrong. Against `en` only: parity with `pt` and `ja` is that test's job.
   */
  it('returns key paths that exist', () => {
    const support: Record<string, string> = en.support;
    for (const os of ['ios', 'android', 'web']) {
      const { why, unavailable } = tipStoreCopyKeys(os);
      expect(Object.keys(support)).toEqual(
        expect.arrayContaining([why, unavailable].map((path) => path.replace('support.', ''))),
      );
    }
  });
});

describe('hasTipped', () => {
  it('is false for the empty state and true once a tip lands', () => {
    expect(hasTipped(EMPTY_SUPPORTER_STATE)).toBe(false);
    expect(hasTipped(withTipRecorded(EMPTY_SUPPORTER_STATE, 'medium', new Date()))).toBe(true);
  });
});

describe('supporterStateSchema', () => {
  it('accepts the empty state and a recorded tip', () => {
    expect(supporterStateSchema.safeParse(EMPTY_SUPPORTER_STATE).success).toBe(true);
    expect(
      supporterStateSchema.safeParse({ tipCount: 3, lastTipAt: '2026-07-28T10:00:00.000Z', lastTier: 'large' }).success,
    ).toBe(true);
  });

  /**
   * supporter.json is app-written, so a rejection means the file was corrupted or predates a shape
   * change. `loadSupporterState` self-heals to the empty state on any of these rather than throwing.
   */
  it('rejects corrupt state', () => {
    expect(supporterStateSchema.safeParse({}).success).toBe(false);
    expect(supporterStateSchema.safeParse({ tipCount: -1, lastTipAt: null, lastTier: null }).success).toBe(false);
    expect(supporterStateSchema.safeParse({ tipCount: 1.5, lastTipAt: null, lastTier: null }).success).toBe(false);
    expect(supporterStateSchema.safeParse({ tipCount: 1, lastTipAt: null, lastTier: 'huge' }).success).toBe(false);
    expect(supporterStateSchema.safeParse('not an object').success).toBe(false);
  });
});
