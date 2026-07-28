const mockLoad = jest.fn();
const mockSave = jest.fn();
jest.mock('@/storage/tip-file', () => ({
  loadSupporterState: () => mockLoad(),
  saveSupporterState: (state: unknown) => mockSave(state),
}));

import { EMPTY_SUPPORTER_STATE } from '@/domain/tip';
import { useTipStore } from '@/state/tip-store';

beforeEach(() => {
  useTipStore.setState({ status: 'idle', supporter: EMPTY_SUPPORTER_STATE });
  mockLoad.mockReset().mockResolvedValue(EMPTY_SUPPORTER_STATE);
  mockSave.mockReset().mockResolvedValue(true);
});

describe('hydrate', () => {
  it('loads the persisted state and marks itself ready', async () => {
    const stored = { tipCount: 1, lastTipAt: '2026-07-28T09:00:00.000Z', lastTier: 'small' as const };
    mockLoad.mockResolvedValue(stored);

    await useTipStore.getState().hydrate();

    expect(useTipStore.getState().supporter).toEqual(stored);
    expect(useTipStore.getState().status).toBe('ready');
  });

  // Settings and the Support screen both hydrate on mount, and opening Support from Settings mounts
  // them back to back — so overlapping calls are the normal path, not an edge case.
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

describe('recordTip', () => {
  it('accumulates repeat tips rather than flipping a flag', async () => {
    await useTipStore.getState().recordTip('small');
    await useTipStore.getState().recordTip('large');

    const { supporter } = useTipStore.getState();
    expect(supporter.tipCount).toBe(2);
    expect(supporter.lastTier).toBe('large');
    expect(supporter.lastTipAt).not.toBeNull();
  });

  it('reports success when the write lands', async () => {
    expect(await useTipStore.getState().recordTip('medium')).toBe(true);
  });

  /**
   * The deliberate part: a failed *write* must not roll back the in-memory tip. The user really did
   * pay — Play took the money before this code ran — so showing "no tip recorded" because a JSON
   * write failed would read as the payment being lost. The return value is how the screen says
   * "this won't survive a restart" without contradicting what actually happened.
   */
  it('still reflects the tip when persistence fails, and says so', async () => {
    mockSave.mockResolvedValue(false);

    expect(await useTipStore.getState().recordTip('small')).toBe(false);
    expect(useTipStore.getState().supporter.tipCount).toBe(1);
  });

  it('persists the accumulated state, not just the latest tier', async () => {
    await useTipStore.getState().recordTip('small');
    await useTipStore.getState().recordTip('medium');

    expect(mockSave).toHaveBeenLastCalledWith(expect.objectContaining({ tipCount: 2, lastTier: 'medium' }));
  });
});
