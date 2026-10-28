const mockLoad = jest.fn();
jest.mock('@/storage/tip-file', () => ({
  loadSupporterState: () => mockLoad(),
}));

import { EMPTY_SUPPORTER_STATE } from '@/domain/tip';
import { useTipStore } from '@/state/tip-store';

beforeEach(() => {
  useTipStore.setState({ status: 'idle', supporter: EMPTY_SUPPORTER_STATE });
  mockLoad.mockReset().mockResolvedValue(EMPTY_SUPPORTER_STATE);
});

describe('hydrate', () => {
  it('loads the persisted state and marks itself ready', async () => {
    const stored = { tipCount: 1, lastTipAt: '2026-07-28T09:00:00.000Z', lastTier: 'small' as const };
    mockLoad.mockResolvedValue(stored);

    await useTipStore.getState().hydrate();

    expect(useTipStore.getState().supporter).toEqual(stored);
    expect(useTipStore.getState().status).toBe('ready');
  });

  // Settings hydrates on mount, and a `filesChanged('supporter')` from the shell can land while that
  // read is still going — so overlapping calls are a real path, not an edge case.
  it('ignores a second call while the first is still in flight', async () => {
    let release: (value: typeof EMPTY_SUPPORTER_STATE) => void = () => {};
    mockLoad.mockReturnValue(new Promise((resolve) => (release = resolve)));

    const first = useTipStore.getState().hydrate();
    const second = useTipStore.getState().hydrate();
    release(EMPTY_SUPPORTER_STATE);
    await Promise.all([first, second]);

    expect(mockLoad).toHaveBeenCalledTimes(1);
  });
});
