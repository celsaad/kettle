/**
 * Settings' "Backups and sync" section.
 *
 * The rows are thin — the reasoning they wrap is tested in `storage/__tests__/backup.test.ts`. What
 * this file pins is the wiring nobody would notice breaking: that the section only appears where a
 * chosen folder actually survives a relaunch, that backing up hands over the folder the user picked,
 * and that cancelling the picker leaves an existing choice alone rather than clearing it.
 *
 * It also carries this screen's translation check. Settings has more prose than any other screen and
 * had no test at all before this one.
 */
const mockBackUpNow = jest.fn();
const mockPickBackupFolder = jest.fn();
let mockBackupSupported = true;

jest.mock('expo-router', () => require('@/test-support/expo-router'));

jest.mock('@/storage/backup', () => ({
  get isBackupFolderSupported() {
    return mockBackupSupported;
  },
  backUpNow: (...args: unknown[]) => mockBackUpNow(...args),
  pickBackupFolder: () => mockPickBackupFolder(),
  backupFolderLabel: (uri: string) => `label:${uri}`,
}));

jest.mock('@/storage/export', () => ({
  exportLibrary: jest.fn().mockResolvedValue(undefined),
  exportSessions: jest.fn().mockResolvedValue(undefined),
}));

const mockSavePreferences = jest.fn().mockResolvedValue(true);
jest.mock('@/storage/preferences-file', () => ({
  loadPreferences: jest.fn().mockResolvedValue(null),
  savePreferences: (...args: unknown[]) => mockSavePreferences(...args),
}));

import { act, fireEvent, screen } from '@testing-library/react-native';
// Named import rather than the default, matching the other screen tests.
import { changeLanguage } from 'i18next';

import SettingsScreen from '@/app/settings';
import { DEFAULT_LIST_SORTS } from '@/domain/preferences';
import { useLibraryStore } from '@/state/library-store';
import { usePreferencesStore } from '@/state/preferences-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { aLibrary, anExercise } from '@/test-support/library';
import { renderScreen } from '@/test-support/render';

const FOLDER = 'content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FKettle';

function setFolder(backupFolderUri: string | null) {
  usePreferencesStore.setState({
    status: 'ready',
    preferences: {
      unitSystem: 'metric',
      themePreference: 'system',
      listSort: DEFAULT_LIST_SORTS,
      restDayReminder: false,
      backupFolderUri,
    },
  });
}

beforeEach(() => {
  mockBackupSupported = true;
  mockBackUpNow.mockReturnValue(null);
  mockPickBackupFolder.mockResolvedValue(FOLDER);
  mockSavePreferences.mockResolvedValue(true);
  useLibraryStore.setState({ library: aLibrary({ exercises: [anExercise()] }), status: 'ready' });
  useSessionHistoryStore.setState({ sessions: [], status: 'ready' });
  setFolder(null);
});

describe('choosing a folder', () => {
  it('invites the choice when none has been made', async () => {
    await renderScreen(<SettingsScreen />);

    expect(screen.getByText('Backup folder')).toBeTruthy();
    expect(screen.getByText('Not chosen yet. Pick a folder on this device.')).toBeTruthy();
    // Nothing to forget yet, so the row that undoes the choice isn't there to be tapped.
    expect(screen.queryByText('Forget this folder')).toBeNull();
  });

  it('shows the chosen folder rather than the raw tree URI', async () => {
    setFolder(FOLDER);

    await renderScreen(<SettingsScreen />);

    expect(screen.getByText(`label:${FOLDER}`)).toBeTruthy();
    expect(screen.getByText('Forget this folder')).toBeTruthy();
  });

  it('keeps the chosen folder', async () => {
    await renderScreen(<SettingsScreen />);

    await fireEvent.press(screen.getByText('Backup folder'));

    expect(usePreferencesStore.getState().preferences.backupFolderUri).toBe(FOLDER);
  });

  /**
   * A mis-tap on "Backup folder" opens the picker; backing out of it must not be read as "clear my
   * folder". Reintroducing that — treating a null pick as a value to store — fails this test.
   */
  it('leaves an existing folder alone when the picker is cancelled', async () => {
    setFolder(FOLDER);
    mockPickBackupFolder.mockResolvedValue(null);

    await renderScreen(<SettingsScreen />);
    await fireEvent.press(screen.getByText('Backup folder'));

    expect(usePreferencesStore.getState().preferences.backupFolderUri).toBe(FOLDER);
  });

  // The grant survives the restart on its own; the URI is what wouldn't. A row left looking set while
  // the next launch has nothing to write to is the one failure here that costs data silently.
  it('says so when the folder was chosen but could not be saved', async () => {
    mockSavePreferences.mockResolvedValue(false);

    await renderScreen(<SettingsScreen />);
    await fireEvent.press(screen.getByText('Backup folder'));

    expect(screen.getByText(/couldn't be saved/)).toBeTruthy();
  });

  it('forgets the folder on request', async () => {
    setFolder(FOLDER);

    await renderScreen(<SettingsScreen />);
    await fireEvent.press(screen.getByText('Forget this folder'));

    expect(usePreferencesStore.getState().preferences.backupFolderUri).toBeNull();
  });
});

describe('backing up now', () => {
  /**
   * `runBackup` defers the write off the press handler, so every assertion below has to drive the
   * clock. Fake timers rather than an `await` that happens to work: with real ones these passed
   * whether or not the deferral was there, which makes them worth nothing against a regression in it.
   * The flush needs its own `act` because the callback sets state.
   */
  beforeEach(() => jest.useFakeTimers());
  afterEach(() => jest.useRealTimers());

  const flush = () => act(async () => void jest.runAllTimers());

  it('is disabled until a folder is chosen', async () => {
    await renderScreen(<SettingsScreen />);

    expect(screen.getByText('Choose a folder first.')).toBeTruthy();
  });

  it('backs up into the folder the user chose', async () => {
    setFolder(FOLDER);

    await renderScreen(<SettingsScreen />);
    await fireEvent.press(screen.getByText('Back up now'));
    await flush();

    expect(mockBackUpNow).toHaveBeenCalledWith(FOLDER, []);
    expect(screen.getByText('Backed up.')).toBeTruthy();
  });

  /**
   * The row can't just go quiet. `backUpNow` is synchronous SAF IO against a folder that may be
   * cloud-backed, so it can take seconds — long enough that a row which neither responds nor changes
   * reads as broken, and gets tapped again.
   */
  it('says it is working before the write comes back', async () => {
    setFolder(FOLDER);

    await renderScreen(<SettingsScreen />);
    await fireEvent.press(screen.getByText('Back up now'));

    expect(screen.getByText('Writing…')).toBeTruthy();

    await flush();
    expect(screen.queryByText('Writing…')).toBeNull();
  });

  // The one failure carrying a platform string. The sentence around it stays translated; only the
  // reason the OS gave is passed through, because nothing else knows it.
  it('renders why a backup failed, reason included', async () => {
    setFolder(FOLDER);
    mockBackUpNow.mockReturnValue({ kind: 'writeFailed', detail: 'disk full' });

    await renderScreen(<SettingsScreen />);
    await fireEvent.press(screen.getByText('Back up now'));
    await flush();

    expect(screen.getByText("Couldn't write the backup: disk full")).toBeTruthy();
  });

  it('points at re-choosing the folder when the grant has gone', async () => {
    setFolder(FOLDER);
    mockBackUpNow.mockReturnValue({ kind: 'unreachable' });

    await renderScreen(<SettingsScreen />);
    await fireEvent.press(screen.getByText('Back up now'));
    await flush();

    expect(screen.getByText(/Choose it again/)).toBeTruthy();
  });
});

/**
 * Hidden rather than greyed out where a folder can't be made to stick — an iOS grant dies with the
 * app session. A disabled row would still be advertising a backup that silently stops happening; the
 * old export/import copy is the honest thing to show there instead.
 */
it('falls back to the export copy where a chosen folder would not survive a relaunch', async () => {
  mockBackupSupported = false;

  await renderScreen(<SettingsScreen />);

  expect(screen.queryByText('Backup folder')).toBeNull();
  expect(screen.queryByText('Back up now')).toBeNull();
  expect(screen.getByText(/Export sends exercises.yaml/)).toBeTruthy();
});

/**
 * Driven in `pt` because an English assertion cannot tell `t('settings.backupFolder')` from a
 * hardcoded literal — both render identically. It only catches a rendered key path.
 */
it('is translated', async () => {
  await changeLanguage('pt');
  setFolder(FOLDER);

  await renderScreen(<SettingsScreen />);

  expect(screen.getByText('Pasta de backup')).toBeTruthy();
  expect(screen.getByText('Fazer backup agora')).toBeTruthy();
  expect(screen.getByText('Esquecer esta pasta')).toBeTruthy();
});
