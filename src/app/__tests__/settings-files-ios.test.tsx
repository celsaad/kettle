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
jest.mock('expo-router', () => require('@/test-support/expo-router'));

// A getter, not a literal: the paragraph is only interesting if it can be shown to *not* render, and
// a hardcoded `true` would make the negative case assert this mock instead of the screen. The folder
// flag stays false throughout — that branch is `settings-backup.test.tsx`'s.
let mockDocumentsShared = true;
jest.mock('@/storage/backup', () => ({
  isBackupFolderSupported: false,
  get isDocumentsFolderShared() {
    return mockDocumentsShared;
  },
  backUpNow: jest.fn(),
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
import en from '@/i18n/locales/en.json';
import pt from '@/i18n/locales/pt.json';
import { useLibraryStore } from '@/state/library-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { aLibrary, anExercise } from '@/test-support/library';
import { renderScreen } from '@/test-support/render';

beforeEach(() => {
  mockDocumentsShared = true;
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

/**
 * Where the paragraph must *not* appear: Android has the folder rows instead, and the web build has
 * no filesystem at all. Telling either one to look in the Files app describes something that isn't
 * there — and since this is copy with no behaviour behind it, the wrong branch is invisible to
 * everything except a reader.
 */
it('says nothing about the Files app where the folder is not published', async () => {
  mockDocumentsShared = false;

  await renderScreen(<SettingsScreen />);

  expect(screen.queryByText(en.settings.filesInFilesApp)).toBeNull();
  // The generic sentences stay: this branch loses a paragraph, not the section.
  expect(screen.getByText(en.settings.filesLocal)).toBeTruthy();
  expect(screen.getByText(en.settings.filesSync)).toBeTruthy();
});
