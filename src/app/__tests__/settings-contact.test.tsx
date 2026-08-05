/**
 * The two ways to reach the developer from inside the app.
 *
 * There were none before this: a tester's only channel was a public Play review, and with no
 * analytics there is nothing that notices when something goes wrong quietly. That makes the
 * *degrade* the part worth testing rather than the happy path — `Linking.openURL` rejects on a device
 * with no mail client, and a row that silently does nothing on the one screen whose job is to be the
 * way out is the failure this is guarding.
 */
const mockOpenURL = jest.fn();
const mockSetStringAsync = jest.fn();

jest.mock('expo-router', () => require('@/test-support/expo-router'));

jest.mock('expo-clipboard', () => ({
  setStringAsync: (...args: unknown[]) => mockSetStringAsync(...args),
}));

// `expoConfig` is null under jest, and the version is the one thing the subject line carries — so
// without this the assertion below would pass against an empty interpolation.
jest.mock('expo-constants', () => ({ expoConfig: { version: '9.9.9' } }));

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

import { fireEvent, screen } from '@testing-library/react-native';
import { changeLanguage } from 'i18next';
import { Linking } from 'react-native';

import SettingsScreen from '@/app/settings';
import { useLibraryStore } from '@/state/library-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { aLibrary, anExercise } from '@/test-support/library';
import { renderScreen } from '@/test-support/render';

const EMAIL = 'kettleapp.feedback@gmail.com';
const ISSUES = 'https://github.com/celsaad/kettle/issues';

beforeEach(() => {
  jest.spyOn(Linking, 'openURL').mockImplementation((...args) => mockOpenURL(...args));
  mockOpenURL.mockResolvedValue(true);
  mockSetStringAsync.mockResolvedValue(true);
  useLibraryStore.setState({ library: aLibrary({ exercises: [anExercise()] }), status: 'ready' });
  useSessionHistoryStore.setState({ sessions: [], status: 'ready' });
});

it('shows the address rather than making the user tap to find out what it is', async () => {
  await renderScreen(<SettingsScreen />);

  expect(screen.getByText(EMAIL)).toBeTruthy();
});

/**
 * Subject only. The version is prefilled because it is the first thing every report needs and the
 * last thing anyone remembers — and it is already printed at the bottom of this screen, so nothing
 * is disclosed that the user can't see. A body, a device model or a log here would turn a mail
 * client handoff into data collection, which is what the Data Safety declaration rests on.
 */
it('prefills the subject with the version, and nothing else', async () => {
  await renderScreen(<SettingsScreen />);

  await fireEvent.press(screen.getByText('Email the developer'));

  const url = mockOpenURL.mock.calls[0][0] as string;
  expect(url.startsWith(`mailto:${EMAIL}?subject=`)).toBe(true);
  expect(decodeURIComponent(url)).toContain('Kettle 9.9.9 feedback');
  // No body parameter at all — not an empty one.
  expect(url).not.toContain('body=');
});

it('opens the issue tracker in the browser', async () => {
  await renderScreen(<SettingsScreen />);

  await fireEvent.press(screen.getByText('Report an issue on GitHub'));

  expect(mockOpenURL).toHaveBeenCalledWith(ISSUES);
});

describe('when nothing can open the link', () => {
  // `Linking.openURL` rejects rather than resolving false, and a device with no mail client is not
  // rare on Android. Uncaught this is an unhandled rejection and a row that appears broken.
  it('copies the address instead of failing silently', async () => {
    mockOpenURL.mockRejectedValue(new Error('No Activity found to handle Intent'));

    await renderScreen(<SettingsScreen />);
    await fireEvent.press(screen.getByText('Email the developer'));

    expect(mockSetStringAsync).toHaveBeenCalledWith(EMAIL);
    // The message names the address, so the user knows what landed on their clipboard rather than
    // having to paste somewhere to find out.
    expect(screen.getByText(`No app could open that, so ${EMAIL} was copied to your clipboard instead.`)).toBeTruthy();
  });

  // `setStringAsync` reports a refusal with `false` rather than by throwing, matching the import
  // screen's copy buttons. Claiming "copied" over that would be worse than the original failure.
  it('does not claim to have copied when the clipboard refused', async () => {
    mockOpenURL.mockRejectedValue(new Error('No Activity found to handle Intent'));
    mockSetStringAsync.mockResolvedValue(false);

    await renderScreen(<SettingsScreen />);
    await fireEvent.press(screen.getByText('Email the developer'));

    expect(screen.getByText(/couldn't be copied either/)).toBeTruthy();
  });

  it('degrades the same way for the issue tracker', async () => {
    mockOpenURL.mockRejectedValue(new Error('No Activity found to handle Intent'));

    await renderScreen(<SettingsScreen />);
    await fireEvent.press(screen.getByText('Report an issue on GitHub'));

    expect(mockSetStringAsync).toHaveBeenCalledWith(ISSUES);
  });
});

/**
 * Driven in `pt`, because an English assertion cannot tell `t('settings.contactEmail')` from a
 * hardcoded literal. The address and the URL are deliberately *not* translated — they are values,
 * not prose, and a translated one would break the only feedback channel the app has.
 */
it('is translated, except for the address itself', async () => {
  await changeLanguage('pt');

  await renderScreen(<SettingsScreen />);

  expect(screen.getByText('Fale com a gente')).toBeTruthy();
  expect(screen.getByText('Escrever para o desenvolvedor')).toBeTruthy();
  expect(screen.getByText(EMAIL)).toBeTruthy();
});
