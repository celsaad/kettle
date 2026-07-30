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

/** Same trick for the target mark, which is the only percentage `left` in the component. */
function markerLeft(): string | undefined {
  return JSON.stringify(screen.toJSON()).match(/"left":"([^"]*)"/)?.[1];
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

it('puts the target mark at the end of the bar when there is a single target', async () => {
  await renderScreen(<SessionHold {...props} targetSec={30} elapsedSec={15} />);

  expect(markerLeft()).toBe('100%');
});

/**
 * §12.2, settled: count up, with the range as the marker. The bar is scaled to the *top* of the range
 * — scaled to the bottom it hit 100% at 15s and sat there through 25s, going flat across exactly the
 * seconds a range hold is about.
 */
describe('a hold with a target range', () => {
  it('scales the bar to the top of the range', async () => {
    await renderScreen(<SessionHold {...props} targetSec={15} targetMaxSec={25} elapsedSec={15} />);

    expect(fillWidth()).toBe('60%');
  });

  it('marks the bottom of the range part-way along, as something to cross', async () => {
    await renderScreen(<SessionHold {...props} targetSec={15} targetMaxSec={25} elapsedSec={20} />);

    expect(markerLeft()).toBe('60%');
    expect(fillWidth()).toBe('80%');
  });

  it('still tops out at the range maximum', async () => {
    await renderScreen(<SessionHold {...props} targetSec={15} targetMaxSec={25} elapsedSec={40} />);

    expect(fillWidth()).toBe('100%');
  });

  // A max at or below the min is a range in name only — authored by a program override, which no
  // schema refinement guards on that path. Falling back to the min keeps the bar meaningful instead
  // of dividing by the smaller number and reading past full immediately.
  it('ignores a maximum that is not above the minimum', async () => {
    await renderScreen(<SessionHold {...props} targetSec={30} targetMaxSec={20} elapsedSec={15} />);

    expect(fillWidth()).toBe('50%');
    expect(markerLeft()).toBe('100%');
  });
});
