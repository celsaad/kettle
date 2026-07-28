import { render, screen } from '@testing-library/react-native';
import { useWindowDimensions } from 'react-native';

import { SessionNextCard } from '@/components/session-next-card';
import { ThemeOverrideProvider } from '@/hooks/theme-context';

/**
 * Pins the dynamic-type gate, which can't be verified in the browser: react-native-web always
 * reports `fontScale: 1`, so the large-text branch is unreachable there.
 *
 * It matters because the runner screens are `flex: 1` with no ScrollView — overflow clips instead of
 * scrolling, and what clips is the bottom of the screen, where the primary action lives. Dropping
 * this card is what keeps that action reachable at large accessibility text sizes.
 */
// RNTL 14's `render` returns a Promise (React 19 made rendering async-aware), so every case awaits
// it — without that, `screen` reports "render function has not been called".
//
// Mocks the specific module rather than spreading `requireActual('react-native')`, which eagerly
// evaluates every native module and dies on an unregistered DevMenu.
jest.mock('react-native/Libraries/Utilities/useWindowDimensions', () => ({
  __esModule: true,
  default: jest.fn(),
}));

const mockDimensions = useWindowDimensions as unknown as jest.Mock;

function setFontScale(fontScale: number) {
  mockDimensions.mockReturnValue({ width: 390, height: 844, scale: 3, fontScale });
}

// ThemedText reads the theme through context, so anything rendering it needs the provider.
const inTheme = (ui: React.ReactElement) => <ThemeOverrideProvider>{ui}</ThemeOverrideProvider>;

const next = { label: 'Pull-ups', detail: 'reps · set 2 of 4 · target 6–10' };

it('shows the upcoming step at normal text size', async () => {
  setFontScale(1);
  await render(inTheme(<SessionNextCard next={next} />));
  expect(screen.getByText('Pull-ups')).toBeTruthy();
});

it('still shows it at a moderate text size', async () => {
  setFontScale(1.5);
  await render(inTheme(<SessionNextCard next={next} />));
  expect(screen.getByText('Pull-ups')).toBeTruthy();
});

it('drops itself past the threshold, to keep the primary action on screen', async () => {
  setFontScale(1.6);
  await render(inTheme(<SessionNextCard next={next} />));
  expect(screen.queryByText('Pull-ups')).toBeNull();
});

it('renders nothing when there is no upcoming step', async () => {
  setFontScale(1);
  await render(inTheme(<SessionNextCard next={null} />));
  expect(screen.queryByText('Pull-ups')).toBeNull();
});
