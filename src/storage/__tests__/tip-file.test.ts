/**
 * Mocked at our own boundary (`@/storage/paths`) rather than at `expo-file-system`, matching how the
 * runner tests mock the history store: the surface is small, stable and ours.
 *
 * What's worth pinning here is the *self-healing*. `supporter.json` is app-owned, so a parse failure
 * has no user who could fix it by hand — the file degrades to "no tips recorded" instead of surfacing
 * an error nobody can act on. The cost of being wrong is forgetting a past tip, never losing workout
 * data, and that trade only holds if these paths genuinely can't throw.
 */
const mockFile = {
  exists: true,
  text: jest.fn<Promise<string>, []>(),
  write: jest.fn(),
  create: jest.fn(),
};
let mockStorageSupported = true;

jest.mock('@/storage/paths', () => ({
  get isFileStorageSupported() {
    return mockStorageSupported;
  },
  ensureStorageReady: jest.fn(),
  storagePaths: {
    get supporterFile() {
      return mockFile;
    },
  },
}));

import { EMPTY_SUPPORTER_STATE } from '@/domain/tip';
import { loadSupporterState, saveSupporterState } from '@/storage/tip-file';

const recorded = { tipCount: 2, lastTipAt: '2026-07-28T09:00:00.000Z', lastTier: 'medium' as const };

beforeEach(() => {
  mockStorageSupported = true;
  mockFile.exists = true;
  mockFile.text.mockReset();
  mockFile.write.mockReset();
  mockFile.create.mockReset();
});

describe('loadSupporterState', () => {
  it('reads back a state the app previously wrote', async () => {
    mockFile.text.mockResolvedValue(JSON.stringify(recorded));
    expect(await loadSupporterState()).toEqual(recorded);
  });

  it('returns the empty state when no file exists yet', async () => {
    mockFile.exists = false;
    expect(await loadSupporterState()).toEqual(EMPTY_SUPPORTER_STATE);
    expect(mockFile.text).not.toHaveBeenCalled();
  });

  it('self-heals rather than throwing on malformed JSON', async () => {
    mockFile.text.mockResolvedValue('{ not json');
    expect(await loadSupporterState()).toEqual(EMPTY_SUPPORTER_STATE);
  });

  it('self-heals on JSON that parses but fails the schema', async () => {
    mockFile.text.mockResolvedValue(JSON.stringify({ tipCount: 'lots' }));
    expect(await loadSupporterState()).toEqual(EMPTY_SUPPORTER_STATE);
  });

  it('self-heals when the read itself throws', async () => {
    mockFile.text.mockRejectedValue(new Error('device storage unavailable'));
    expect(await loadSupporterState()).toEqual(EMPTY_SUPPORTER_STATE);
  });

  it('returns the empty state on web, where there is no file storage', async () => {
    mockStorageSupported = false;
    expect(await loadSupporterState()).toEqual(EMPTY_SUPPORTER_STATE);
  });
});

describe('saveSupporterState', () => {
  it('reports success once the write lands', async () => {
    expect(await saveSupporterState(recorded)).toBe(true);
    expect(mockFile.write).toHaveBeenCalledWith(JSON.stringify(recorded));
  });

  it('creates the file the first time a tip is recorded', async () => {
    mockFile.exists = false;
    expect(await saveSupporterState(recorded)).toBe(true);
    expect(mockFile.create).toHaveBeenCalled();
  });

  // The boolean is the whole point: the caller shows "couldn't save this" rather than silently
  // claiming a tip was recorded that won't survive a restart.
  it('reports failure rather than throwing when the write fails', async () => {
    mockFile.write.mockImplementation(() => {
      throw new Error('disk full');
    });
    expect(await saveSupporterState(recorded)).toBe(false);
  });

  it('reports failure on web instead of pretending to persist', async () => {
    mockStorageSupported = false;
    expect(await saveSupporterState(recorded)).toBe(false);
    expect(mockFile.write).not.toHaveBeenCalled();
  });
});
