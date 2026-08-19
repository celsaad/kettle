import { render, screen } from '@testing-library/react-native';
import { useWindowDimensions } from 'react-native';

import { SessionNextCard } from '@/components/session-next-card';
import { ThemeOverrideProvider } from '@/hooks/theme-context';
import en from '@/i18n/locales/en.json';

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

// 'pull-ups' is deliberately not a seeded id, so this fixture exercises the no-drawing path that
// most of a real library takes.
const next = { label: 'Pull-ups', detail: 'reps · set 2 of 4 · target 6–10', exerciseId: 'pull-ups' };
const seededNext = { label: 'Prancha', detail: 'hold · set 1 of 3 · 45s', exerciseId: 'plank' };

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

it('shows the drawing for an upcoming exercise that has one', async () => {
  setFontScale(1);
  await render(inTheme(<SessionNextCard next={seededNext} />));
  expect(screen.getByLabelText(en.exerciseArt.plank)).toBeTruthy();
});

it('shows no drawing for an exercise that has none, and still shows the card', async () => {
  setFontScale(1);
  await render(inTheme(<SessionNextCard next={next} />));
  expect(screen.getByText('Pull-ups')).toBeTruthy();
  expect(screen.queryByRole('image')).toBeNull();
});

it('drops the drawing before it drops the card', async () => {
  // The drawing is the fixed-size part: text reflows at large type, an illustration just keeps its
  // 58dp. Losing it first is what buys the card the room to stay useful up to 1.5.
  setFontScale(1.3);
  await render(inTheme(<SessionNextCard next={seededNext} />));
  expect(screen.getByText('Prancha')).toBeTruthy();
  expect(screen.queryByLabelText(en.exerciseArt.plank)).toBeNull();
});
