/**
 * Settings' "Session sounds" row.
 *
 * The gate itself is pinned in `hooks/__tests__/use-session-sounds.test.ts`; what's left here is the
 * wiring — that the control writes the preference, that it shows the stored value rather than a
 * default, and that the row is translated. The last one is why this is a screen test at all: the
 * caption is the only place the app explains why the phone's silent switch doesn't cover these.
 */
jest.mock('expo-router', () => require('@/test-support/expo-router'));

jest.mock('@/storage/backup', () => ({
  isBackupFolderSupported: false,
  backUpNow: jest.fn(),
  pickBackupFolder: jest.fn(),
  backupFolderLabel: (uri: string) => uri,
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

import { fireEvent, screen } from '@testing-library/react-native';
import { changeLanguage } from 'i18next';

import SettingsScreen from '@/app/settings';
import { useLibraryStore } from '@/state/library-store';
import { usePreferencesStore } from '@/state/preferences-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { aLibrary, anExercise } from '@/test-support/library';
import { renderScreen } from '@/test-support/render';

function setSounds(sessionSounds: boolean) {
  usePreferencesStore.setState({
    status: 'ready',
    preferences: {
      unitSystem: 'metric',
      themePreference: 'system',
      // Deliberately the opposite of the default, and of whatever `sessionSounds` is: Reminder is the
      // other Off/On pair on this screen, so the two segmented controls carry the same two labels.
      // Setting it opposite means a query that grabs the wrong pair fails instead of quietly
      // asserting against the reminder.
      restDayReminder: true,
      sessionSounds,
      backupFolderUri: null,
    },
  });
}

/**
 * The sounds pair, by position: Settings renders it above Reminder, which is the only other control
 * using these labels. `getAllBy` rather than `getBy` because both pairs match — and each assertion
 * below is written so that picking the wrong one fails.
 */
const soundsButton = (name: string) => screen.getAllByRole('button', { name })[0];

beforeEach(() => {
  mockSavePreferences.mockClear().mockResolvedValue(true);
  useLibraryStore.setState({ library: aLibrary({ exercises: [anExercise()] }), status: 'ready' });
  useSessionHistoryStore.setState({ sessions: [], status: 'ready' });
  setSounds(true);
});

it('mutes the session cues on request', async () => {
  await renderScreen(<SettingsScreen />);

  await fireEvent.press(soundsButton('Off'));

  expect(usePreferencesStore.getState().preferences.sessionSounds).toBe(false);
});

it('turns them back on', async () => {
  setSounds(false);

  await renderScreen(<SettingsScreen />);
  await fireEvent.press(soundsButton('On'));

  expect(usePreferencesStore.getState().preferences.sessionSounds).toBe(true);
});

/**
 * The state, not just the label. `Segmented` carries `accessibilityState`, so this is also what a
 * screen reader is told — and a control that persisted the choice but always drew "On" would pass
 * the two tests above.
 */
it('shows the stored choice rather than the default', async () => {
  setSounds(false);

  await renderScreen(<SettingsScreen />);

  expect(soundsButton('Off').props.accessibilityState).toMatchObject({ selected: true });
  expect(soundsButton('On').props.accessibilityState).toMatchObject({ selected: false });
  // The reminder is on in this fixture, so the pair below reads the other way round — which is what
  // makes the two assertions above about *this* control rather than about either pair.
  expect(screen.getAllByRole('button', { name: 'On' })[1].props.accessibilityState).toMatchObject({ selected: true });
});

// One file, so a change here can't drop the folder, the units or the theme.
it('writes the whole preferences file, not just its own field', async () => {
  await renderScreen(<SettingsScreen />);
  await fireEvent.press(soundsButton('Off'));

  expect(mockSavePreferences).toHaveBeenCalledWith(
    expect.objectContaining({ sessionSounds: false, unitSystem: 'metric', themePreference: 'system' }),
  );
});

/**
 * Driven in `pt` because an English assertion cannot tell `t('settings.sounds')` from a hardcoded
 * literal — both render identically. It only catches a rendered key path.
 */
it('is translated', async () => {
  await changeLanguage('pt');

  await renderScreen(<SettingsScreen />);

  expect(screen.getByText('Sons da sessão')).toBeTruthy();
  expect(screen.getByText(/no silencioso/)).toBeTruthy();
  await fireEvent.press(soundsButton('Desligado'));
  expect(usePreferencesStore.getState().preferences.sessionSounds).toBe(false);
});
