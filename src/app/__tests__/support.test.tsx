/**
 * The tip screen names the store that takes the money, and on iOS that store is not Play.
 *
 * Which key each platform reads is `tipStoreCopyKeys`' job and is tested there; what this pins is
 * that the key reaches the paragraph — a screen can hold a perfectly good key and still render a
 * literal beside it. Driven in `pt`, per the house rule: an English-locale assertion cannot tell
 * `t('support.whyAppStore')` apart from the sentence hardcoded in its place.
 *
 * Jest runs these screens as iOS (`@react-native/jest-preset` sets `defaultPlatform: 'ios'`), which
 * is the branch that did not exist before and the only one worth a render.
 */
jest.mock('expo-router', () => require('@/test-support/expo-router'));

// The store side is out of scope here: without the native module the screen renders its
// store-unavailable state, which carries the second of the two strings this is about.
jest.mock('@/hooks/safe-iap', () => ({ isIapAvailable: false }));

jest.mock('@/storage/tip-file', () => ({
  loadSupporterState: jest.fn().mockResolvedValue({ tipCount: 0, lastTipAt: null, lastTier: null }),
  saveSupporterState: jest.fn().mockResolvedValue(true),
}));

import { screen } from '@testing-library/react-native';
import { changeLanguage } from 'i18next';

import SupportScreen from '@/app/support';
import pt from '@/i18n/locales/pt.json';
import { renderScreen } from '@/test-support/render';

it('names the App Store, not Play, in the language the device is in', async () => {
  await changeLanguage('pt');

  await renderScreen(<SupportScreen />);

  expect(screen.getByText(pt.support.whyAppStore)).toBeTruthy();
  expect(screen.getByText(pt.support.storeUnavailableAppStore)).toBeTruthy();
  expect(screen.queryByText(pt.support.whyPlay)).toBeNull();
});
