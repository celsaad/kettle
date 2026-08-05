/**
 * The backup choke point.
 *
 * Two things are worth pinning here, and neither is the YAML — `yaml-mapping.test.ts` owns that.
 *
 * The first is that **nothing here throws**. `backUpNow` is reached from `completeSession`, which the
 * runner calls from `finishSession` — an event handler no error boundary covers. A throw there ends
 * the session on the screen of someone who has just finished a workout, which is the failure the
 * whole non-throwing shape exists to prevent, so every way this can go wrong has a test.
 *
 * The second is that a repeated backup **replaces** what was there, which SAF makes wrong in two
 * independent ways and neither of them raises anything:
 *
 * - `createFile` goes through the platform's `createDocument`, which uniquifies a name that already
 *   exists — so creating unconditionally leaves `kettle-history (1).yaml` beside the original, one
 *   file per session, in the folder whose whole job is to hold one good copy.
 * - `File.write` opens the document `"w"`, which overwrites from offset zero **without truncating**,
 *   so a shorter backup keeps the tail of the longer one it replaced.
 *
 * The second is why `MockFile` below models bytes instead of spying on the call.
 */
/**
 * Models the bytes on disk rather than spying on the call, which is the only way the truncation
 * behaviour below is visible at all.
 *
 * The distinction it reproduces is the SAF one: `write()` opens the document `"w"`, which overwrites
 * from offset zero and leaves anything past the new content in place, while `open(Truncate)` is
 * `"wt"` and wipes it first. A plain `jest.fn()` for `write` can only assert *that* a write happened,
 * which is exactly what let a non-truncating overwrite through the first time.
 */
class MockFile {
  /** What is actually stored, so an assertion can read it back. */
  contents = '';

  constructor(public name: string) {}

  /** Overwrites in place without truncating — the trap. */
  write = jest.fn((content: string) => {
    this.contents = content + this.contents.slice(content.length);
  });

  open = jest.fn((mode: string) => {
    if (mode === MOCK_TRUNCATE) this.contents = '';
    return {
      writeBytes: (bytes: Uint8Array) => {
        const text = new TextDecoder().decode(bytes);
        this.contents = text + this.contents.slice(text.length);
      },
      close: jest.fn(),
    };
  });
}

/** Mirrors `FileMode.Truncate`'s `'wt'`; the enum itself is mocked away with the module. */
const MOCK_TRUNCATE = 'wt';

// Prefixed `mock` because `jest.mock`'s factory is hoisted above every `const` and may only reach
// out-of-scope names that start with it — see the note in AGENTS.md.
const mockFolder = {
  exists: true,
  list: jest.fn<(MockFile | { name: string })[], []>(),
  createFile: jest.fn((name: string) => new MockFile(name)),
};

let mockBackupSupported = true;
const mockLibraryFile = { exists: true, textSync: jest.fn(() => 'exercises: []\n') };

jest.mock('expo-file-system', () => ({
  // Every SAF `Directory` in a run is the same folder; the URI is only carried so the assertions can
  // see what was handed over.
  Directory: jest.fn(() => mockFolder),
  File: MockFile,
  FileMode: { Read: 'r', Write: 'w', Append: 'wa', Truncate: 'wt', ReadWrite: 'rw' },
}));

jest.mock('react-native', () => ({
  get Platform() {
    return { OS: mockBackupSupported ? 'android' : 'ios' };
  },
}));

jest.mock('@/storage/paths', () => ({
  isFileStorageSupported: true,
  get storagePaths() {
    return { libraryFile: mockLibraryFile };
  },
}));

import { load } from 'js-yaml';

import type { Session } from '@/domain/types';

const FOLDER = 'content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FKettle';

function sessionAt(id: string, startedAt: string): Session {
  return {
    version: 1,
    id,
    workout: 'push',
    program: null,
    programWeek: null,
    programDay: null,
    startedAt,
    endedAt: '2026-08-01T10:00:00.000Z',
    entries: [],
  };
}

const june = sessionAt('june', '2026-06-01T09:00:00.000Z');
const july = sessionAt('july', '2026-07-01T09:00:00.000Z');

/**
 * Re-required per test, after `jest.resetModules()`, so the `Platform.OS` mock above is read afresh.
 *
 * `isBackupFolderSupported` is a module-level const — matching `isFileStorageSupported` and
 * `isTipJarSupported` — so it freezes at import, and a top-level `import` here would pin every test
 * in the file to whichever platform the first one wanted. `require` rather than a dynamic `import()`,
 * which jest's CJS runtime refuses.
 */
function backup(): typeof import('@/storage/backup') {
  // oxlint-disable-next-line @typescript-eslint/no-require-imports
  return require('@/storage/backup');
}

beforeEach(() => {
  jest.resetModules();
  mockBackupSupported = true;
  mockFolder.exists = true;
  mockFolder.list.mockReturnValue([]);
  mockFolder.createFile.mockClear();
  mockLibraryFile.exists = true;
  mockLibraryFile.textSync.mockReturnValue('exercises: []\n');
});

describe('backUpNow', () => {
  it('writes both artefacts into the chosen folder', async () => {
    const { backUpNow, HISTORY_BACKUP_NAME, LIBRARY_BACKUP_NAME } = backup();

    expect(backUpNow(FOLDER, [july, june])).toBeNull();

    const written = mockFolder.createFile.mock.results.map((result) => result.value as MockFile);
    expect(written.map((file) => file.name)).toEqual([LIBRARY_BACKUP_NAME, HISTORY_BACKUP_NAME]);
    expect(written[0].contents).toBe('exercises: []\n');
  });

  // Oldest-first, matching `exportSessions` — this is an archive read top to bottom somewhere else,
  // so it runs forward in time, not in the newest-first order the store hands over.
  it('orders the archived log oldest-first regardless of the order it was given', async () => {
    const { backUpNow } = backup();

    backUpNow(FOLDER, [july, june]);

    const history = mockFolder.createFile.mock.results[1].value as MockFile;
    const archive = load(history.contents) as { sessions: { id: string }[] };
    expect(archive.sessions.map((session) => session.id)).toEqual(['june', 'july']);
  });

  /**
   * The duplicate-file regression. Reintroducing it — dropping the `list()` lookup and always calling
   * `createFile` — fails this test, which is how it was verified rather than assumed.
   */
  it('overwrites the files it wrote last time instead of creating a second copy', async () => {
    const { backUpNow, HISTORY_BACKUP_NAME, LIBRARY_BACKUP_NAME } = backup();
    const existingLibrary = new MockFile(LIBRARY_BACKUP_NAME);
    const existingHistory = new MockFile(HISTORY_BACKUP_NAME);
    mockFolder.list.mockReturnValue([existingLibrary, existingHistory]);

    expect(backUpNow(FOLDER, [june])).toBeNull();

    expect(mockFolder.createFile).not.toHaveBeenCalled();
    expect(existingLibrary.contents).toBe('exercises: []\n');
    expect(existingHistory.contents).not.toBe('');
  });

  /**
   * The truncation regression, and the reason the mock models bytes instead of spying on the call.
   *
   * `File.write` opens a SAF document `"w"`, which overwrites from offset zero and does *not*
   * truncate — only `open(FileMode.Truncate)`'s `"wt"` does. So a backup shorter than the one before
   * it (delete an exercise, delete a session, shorten a note) used to leave the tail of the previous
   * version welded onto the end, producing a `kettle-library.yaml` that either won't parse or parses
   * into something wrong. Reintroducing `file.write(content)` here fails this test and only this one,
   * which is how it was confirmed rather than assumed.
   */
  it('truncates, so a shorter backup leaves no tail of the previous one behind', () => {
    const { backUpNow, LIBRARY_BACKUP_NAME } = backup();
    const existing = new MockFile(LIBRARY_BACKUP_NAME);
    existing.contents = 'exercises:\n  - id: one\n  - id: two\n  - id: three\n';
    mockFolder.list.mockReturnValue([existing]);
    mockLibraryFile.textSync.mockReturnValue('exercises: []\n');

    backUpNow(FOLDER, []);

    expect(existing.contents).toBe('exercises: []\n');
  });

  // The folder is the user's own, so it holds their files too. Matching on the name rather than on
  // position is what keeps a backup out of the first file that happens to be in there.
  it('ignores the user’s own files in the same folder', async () => {
    const { backUpNow, LIBRARY_BACKUP_NAME } = backup();
    const strangerFile = new MockFile('tax-return.pdf');
    mockFolder.list.mockReturnValue([strangerFile, new MockFile(LIBRARY_BACKUP_NAME)]);

    backUpNow(FOLDER, [june]);

    expect(strangerFile.contents).toBe('');
  });

  it('skips the library when there is no library file to copy, and still archives the log', async () => {
    const { backUpNow, HISTORY_BACKUP_NAME } = backup();
    mockLibraryFile.exists = false;

    expect(backUpNow(FOLDER, [june])).toBeNull();

    expect(mockFolder.createFile).toHaveBeenCalledTimes(1);
    expect(mockFolder.createFile).toHaveBeenCalledWith(HISTORY_BACKUP_NAME, expect.anything());
  });
});

describe('when it cannot back up', () => {
  it('reports the missing folder rather than writing anywhere', async () => {
    const { backUpNow } = backup();

    expect(backUpNow(null, [june])).toEqual({ kind: 'noFolder' });
    expect(mockFolder.createFile).not.toHaveBeenCalled();
  });

  // Both halves of "the folder went away" — deleted, and the grant revoked in system settings —
  // arrive as `exists: false` rather than as a throw, and both want the same "choose it again".
  it('reports a folder that can no longer be reached', async () => {
    const { backUpNow } = backup();
    mockFolder.exists = false;

    expect(backUpNow(FOLDER, [june])).toEqual({ kind: 'unreachable' });
  });

  /**
   * The one that matters most: a refused write returns rather than throwing, because the caller is
   * the runner's finish path. A `toThrow`-shaped regression here would take the session down with it.
   */
  it('returns the reason instead of throwing when the write is refused', async () => {
    const { backUpNow } = backup();
    mockFolder.createFile.mockImplementation(() => {
      throw new Error('disk full');
    });

    expect(() => backUpNow(FOLDER, [june])).not.toThrow();
    expect(backUpNow(FOLDER, [june])).toEqual({ kind: 'writeFailed', detail: 'disk full' });
  });

  it('does not throw when the folder itself refuses to be listed', async () => {
    const { backUpNow } = backup();
    mockFolder.list.mockImplementation(() => {
      throw new Error('permission denied');
    });

    expect(backUpNow(FOLDER, [june])).toEqual({ kind: 'writeFailed', detail: 'permission denied' });
  });

  // iOS grants access for the app session only and stores no bookmark, so a folder chosen there stops
  // working at the next launch. Refusing up front beats a backup that silently stops happening.
  it('reports the platform as unsupported rather than writing on iOS', async () => {
    mockBackupSupported = false;
    const { backUpNow, isBackupFolderSupported } = backup();

    expect(isBackupFolderSupported).toBe(false);
    expect(backUpNow(FOLDER, [june])).toEqual({ kind: 'unsupported' });
    expect(mockFolder.createFile).not.toHaveBeenCalled();
  });
});

describe('backupFolderLabel', () => {
  // Purely so the user recognises what they picked. It is their own path, so it renders verbatim and
  // is never translated.
  it('shows the path under the volume rather than the whole tree URI', async () => {
    const { backupFolderLabel } = backup();

    expect(backupFolderLabel(FOLDER)).toBe('Documents/Kettle');
  });

  /**
   * A provider whose document id isn't `<volume>:<path>` has no path to show, so the whole URI is
   * what's left. Asserted exactly rather than with `toBeTruthy()`, which passed against the earlier
   * `lastIndexOf(':')` version too — that found the *scheme's* colon and answered
   * `//provider/tree/opaque-id`, mangled rather than recognisable, while still being truthy.
   */
  it('falls back to the whole URI, unmangled, when there is no volume marker to cut at', async () => {
    const { backupFolderLabel } = backup();

    expect(backupFolderLabel('content://provider/tree/opaque-id')).toBe('content://provider/tree/opaque-id');
  });
});
