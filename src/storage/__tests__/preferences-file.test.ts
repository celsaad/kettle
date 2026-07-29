/**
 * Mocked at our own boundary (`@/storage/paths`) rather than at `expo-file-system`, matching
 * `tip-file.test.ts`.
 *
 * What's worth pinning here is that every failure lands on `null` rather than on a default. `null`
 * means "never chosen", which the store turns into "follow the device" — so a corrupt file falls back
 * to the user's locale instead of to a hardcoded metric that would be wrong for half the people it
 * hits.
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
    get preferencesFile() {
      return mockFile;
    },
  },
}));

import { loadPreferences, savePreferences } from '@/storage/preferences-file';

const stored = { unitSystem: 'imperial' as const, themePreference: 'dark' as const };

beforeEach(() => {
  mockStorageSupported = true;
  mockFile.exists = true;
  mockFile.text.mockReset();
  mockFile.write.mockReset();
  mockFile.create.mockReset();
});

describe('loadPreferences', () => {
  it('reads back preferences the app previously wrote', async () => {
    mockFile.text.mockResolvedValue(JSON.stringify(stored));
    expect(await loadPreferences()).toEqual(stored);
  });

  /**
   * The regression this pins: `themePreference` was added to a file already being written in the
   * field. Had it gone in as a required key, every pre-existing `preferences.json` would have failed
   * the schema, and a failed parse answers `null` — so shipping the appearance setting would have
   * quietly reset each existing user's *unit* choice back to their device default.
   */
  it('reads a file written before themePreference existed, keeping its unit choice', async () => {
    mockFile.text.mockResolvedValue(JSON.stringify({ unitSystem: 'imperial' }));
    expect(await loadPreferences()).toEqual({ unitSystem: 'imperial', themePreference: 'system' });
  });

  it('returns null when nothing has been chosen yet', async () => {
    mockFile.exists = false;
    expect(await loadPreferences()).toBeNull();
    expect(mockFile.text).not.toHaveBeenCalled();
  });

  it('returns null rather than throwing on malformed JSON', async () => {
    mockFile.text.mockResolvedValue('{ not json');
    expect(await loadPreferences()).toBeNull();
  });

  // A unit system this build doesn't know — a downgrade, or a hand-edited file — is not something to
  // carry into the UI as a valid setting.
  it('returns null on JSON that parses but fails the schema', async () => {
    mockFile.text.mockResolvedValue(JSON.stringify({ unitSystem: 'stone' }));
    expect(await loadPreferences()).toBeNull();
  });

  // Same reasoning one field over: a *present* but unknown value is a downgrade or a hand-edit, not a
  // missing key, so it gets the file's usual all-or-nothing answer rather than the default above.
  it('returns null on a theme preference this build does not know', async () => {
    mockFile.text.mockResolvedValue(JSON.stringify({ unitSystem: 'metric', themePreference: 'sepia' }));
    expect(await loadPreferences()).toBeNull();
  });

  it('returns null when the read itself throws', async () => {
    mockFile.text.mockRejectedValue(new Error('device storage unavailable'));
    expect(await loadPreferences()).toBeNull();
  });

  it('returns null on web, where there is no file storage', async () => {
    mockStorageSupported = false;
    expect(await loadPreferences()).toBeNull();
  });
});

describe('savePreferences', () => {
  it('reports success once the write lands', async () => {
    expect(await savePreferences(stored)).toBe(true);
    expect(mockFile.write).toHaveBeenCalledWith(JSON.stringify(stored));
  });

  it('creates the file the first time a preference is set', async () => {
    mockFile.exists = false;
    expect(await savePreferences(stored)).toBe(true);
    expect(mockFile.create).toHaveBeenCalled();
  });

  it('reports failure rather than throwing when the write fails', async () => {
    mockFile.write.mockImplementation(() => {
      throw new Error('disk full');
    });
    expect(await savePreferences(stored)).toBe(false);
  });

  it('reports failure on web instead of pretending to persist', async () => {
    mockStorageSupported = false;
    expect(await savePreferences(stored)).toBe(false);
    expect(mockFile.write).not.toHaveBeenCalled();
  });
});
