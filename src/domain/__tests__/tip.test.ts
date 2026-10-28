import { EMPTY_SUPPORTER_STATE, supporterStateSchema } from '@/domain/tip';

/**
 * The tier, purchase and recording cases moved with the tip jar to `:shared`'s `TipTest.kt` (#198).
 * What RN still does is read the file, and how it reads a whole file is `conformance/supporter.yaml`.
 */
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
