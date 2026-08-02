import { screen } from '@testing-library/react-native';

import { SessionInterval } from '@/components/session-interval';
import { RunnerColors } from '@/constants/theme';
import { renderScreen } from '@/test-support/render';

/**
 * Which weight the step's own exit control carries, which is a behaviour question rather than a
 * styling one: a countdown interval auto-advances at zero, so its control is a genuine "skip ahead",
 * but a count-up one has no other way out and the tap *is* the step. Only open-ended cardio produces
 * `countUp`, and nothing in the seed library does — so this branch can't be reached by driving the
 * app, which is why it's pinned here.
 */
const props = {
  exerciseName: 'Row',
  variant: 'cardio' as const,
  setIndex: 1,
  setTotal: 1,
  elapsedSec: 30,
  remainingSec: 0,
  paused: false,
  next: null,
  onTogglePause: jest.fn(),
  reps: 0,
  onChangeReps: jest.fn(),
  roundsCompleted: 0,
  onChangeRoundsCompleted: jest.fn(),
  extraReps: 0,
  onChangeExtraReps: jest.fn(),
  onPrev: jest.fn(),
  onDone: jest.fn(),
};

/** The accent fill is what makes it read as the primary action; nothing else on the row carries it. */
function isFilledPrimary(name: string | RegExp): boolean {
  const flattened = [screen.getByRole('button', { name })].flatMap((node) =>
    [node.props.style].flat(Infinity).filter(Boolean),
  );
  return flattened.some((style) => (style as { backgroundColor?: string }).backgroundColor === RunnerColors.accent);
}

it('gives a count-up interval a filled button, since the tap is the only way out of the step', async () => {
  await renderScreen(<SessionInterval {...props} targetSec={0} countUp />);

  expect(isFilledPrimary('Done →')).toBe(true);
});

it('leaves a countdown interval a quiet link, since it advances on its own at zero', async () => {
  await renderScreen(<SessionInterval {...props} targetSec={600} countUp={false} />);

  expect(isFilledPrimary('Skip →')).toBe(false);
});

// 44px minimum applies to the link too; it has no minHeight to carry it, so hitSlop does.
it('keeps the countdown link at a full touch target via hitSlop', async () => {
  await renderScreen(<SessionInterval {...props} targetSec={600} countUp={false} />);

  expect(screen.getByRole('button', { name: 'Skip →' }).props.hitSlop).toEqual({
    top: 12,
    bottom: 12,
    left: 24,
    right: 24,
  });
});

// Pause is the interruption case on both, and was carrying the screen's heaviest treatment.
it('does not let pause outrank the step exit', async () => {
  await renderScreen(<SessionInterval {...props} targetSec={0} countUp />);

  expect(isFilledPrimary('Pause')).toBe(false);
});
