/**
 * The iOS half of "Backups and sync".
 *
 * iOS can't hold on to a folder the user picks, so the Android rows are absent there by design —
 * which used to leave the section saying only that the files are local and can be exported. The
 * Info.plist keys in `app.json` make the app's own folder visible in the Files app, and this is the
 * copy that tells the user so.
 *
 * Worth a test of its own because the failure is silent in both directions: the paragraph is only
 * ever *rendered* under a flag no Android build sets, and it makes a promise about the OS that no
 * assertion here can check. What this pins is the wiring — the right branch, from the bundles, and
 * not on top of the Android rows.
 */
const mockBackUpNow = jest.fn();

jest.mock('expo-router', () => require('@/test-support/expo-router'));

// The two flags are mutually exclusive by construction (`Platform.OS === 'android'` against
// `=== 'ios'`), and the screen has to keep them that way: two answers to "where are my files" is
// worse than either one.
jest.mock('@/storage/backup', () => ({
  isBackupFolderSupported: false,
  isDocumentsFolderShared: true,
  backUpNow: (...args: unknown[]) => mockBackUpNow(...args),
  pickBackupFolder: jest.fn(),
  backupFolderLabel: (uri: string) => uri,
}));

jest.mock('@/storage/export', () => ({
  exportLibrary: jest.fn().mockResolvedValue(undefined),
  exportSessions: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('@/storage/preferences-file', () => ({
  loadPreferences: jest.fn().mockResolvedValue(null),
  savePreferences: jest.fn().mockResolvedValue(true),
}));

import { screen } from '@testing-library/react-native';
import { changeLanguage } from 'i18next';

import SettingsScreen from '@/app/settings';
import pt from '@/i18n/locales/pt.json';
import { useLibraryStore } from '@/state/library-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { aLibrary, anExercise } from '@/test-support/library';
import { renderScreen } from '@/test-support/render';

beforeEach(() => {
  useLibraryStore.setState({ library: aLibrary({ exercises: [anExercise()] }), status: 'ready' });
  useSessionHistoryStore.setState({ sessions: [], status: 'ready' });
});

it('says where the files are, in the language the app is in', async () => {
  await changeLanguage('pt');

  await renderScreen(<SettingsScreen />);

  expect(screen.getByText(pt.settings.filesInFilesApp)).toBeTruthy();
});

/**
 * The same sentence the Android branch ends on. It matters more here: files sitting in the Files app
 * look like a backup already, and the session log is the half that cannot be read back in.
 */
it('carries the same warning about what can be restored', async () => {
  await changeLanguage('pt');

  await renderScreen(<SettingsScreen />);

  expect(screen.getByText(pt.settings.backupLimits)).toBeTruthy();
});

// Not an alternative to the folder picker — the replacement for it. A build showing both would be
// offering two mechanisms where one of them cannot work.
it('offers none of the folder rows', async () => {
  await renderScreen(<SettingsScreen />);

  expect(screen.queryByText('Backup folder')).toBeNull();
  expect(screen.queryByText('Back up now')).toBeNull();
  expect(mockBackUpNow).not.toHaveBeenCalled();
});
