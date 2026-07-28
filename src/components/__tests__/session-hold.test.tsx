import { screen } from '@testing-library/react-native';

import { SessionHold } from '@/components/session-hold';
import { renderScreen } from '@/test-support/render';

/**
 * The progress bar's fill only, which is the one piece of arithmetic on this screen. A 0-second hold
 * target made it `width: "NaN%"` — an invalid style rather than a no-op, and the kind of thing that
 * can't be caught in the browser because `validateConfig` won't let the in-app editor create the
 * exercise that produces it. A program week's `hold_sec_min: 0` override still can.
 */
const props = {
  exerciseName: 'L-Sit',
  setIndex: 1,
  setTotal: 3,
  elapsedSec: 0,
  paused: false,
  next: null,
  onTogglePause: jest.fn(),
  onPrev: jest.fn(),
  onDone: jest.fn(),
};

/**
 * The fill is the only percentage width in the component — the track's own width is a number and the
 * end marker uses `left` — so this reads it off the rendered tree rather than adding a testID to
 * production markup for the test's benefit.
 */
function fillWidth(): string | undefined {
  return JSON.stringify(screen.toJSON()).match(/"width":"([^"]*)"/)?.[1];
}

it('fills in proportion to the elapsed hold', async () => {
  await renderScreen(<SessionHold {...props} targetSec={30} elapsedSec={15} />);

  expect(fillWidth()).toBe('50%');
});

it('stops at full once the target is passed', async () => {
  await renderScreen(<SessionHold {...props} targetSec={30} elapsedSec={45} />);

  expect(fillWidth()).toBe('100%');
});

// Regression: `elapsedSec / targetSec` is 0/0 before the clock starts — `width: "NaN%"`.
it('renders an empty bar rather than NaN for a 0-second target', async () => {
  await renderScreen(<SessionHold {...props} targetSec={0} elapsedSec={0} />);

  expect(fillWidth()).toBe('0%');
});

// And Infinity once it has, which `Math.min` swallowed into a full bar — a hold with no target
// reading as complete on the first tick.
it('renders an empty bar for a 0-second target once the clock is running', async () => {
  await renderScreen(<SessionHold {...props} targetSec={0} elapsedSec={12} />);

  expect(fillWidth()).toBe('0%');
});
