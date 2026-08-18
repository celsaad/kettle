import { render, screen } from '@testing-library/react-native';

import { SessionProgressBar } from '@/components/session-progress';

/**
 * The runner's progress bar, which broke in opposite ways at opposite ends of its range. Both failures
 * were found on a real library rather than reasoned about, and both are pinned here.
 *
 * These assert *flex weights*, not appearance: the weights are the whole mechanism by which the bar
 * stops depending on how many things it is counting. Whether it reads well from across a room is a
 * device question and belongs to a device pass.
 */

/** The flex weight of each segment, in render order. */
function flexWeights(): (number | undefined)[] {
  const track = screen.getByRole('progressbar');
  return track.children.map((child) =>
    typeof child === 'string' ? undefined : (child.props.style?.flex ?? child.props.style?.at(-1)?.flex),
  );
}

it('fills through the current step, not up to it', async () => {
  await render(<SessionProgressBar total={5} activeIndex={0} />);

  // 1 of 5 done, 4 remaining — an empty bar on the first step would say the work had not started,
  // when in fact you are standing in it.
  expect(flexWeights()).toEqual([1, 4]);
  expect(screen.getByRole('progressbar').props.accessibilityValue).toEqual({ min: 1, max: 5, now: 1 });
});

/**
 * The regression that motivated the rewrite. As a segmented track this needed
 * `27x3px + 26x2px = 133px` inside a 116px container: it overflowed, every segment sat at its 3px
 * minimum, and the active one was 6px among them — nothing a reader could pick out at arm's length.
 * 27 is not hypothetical; it is the member count of "03 - Stretch and mobility" in the library this
 * was found on.
 *
 * Two flex children can neither overflow nor shrink below legibility, so what this pins is that the
 * shape is still two proportional children at a count that used to break it.
 */
it('stays two proportional segments at a member count that overflowed the old track', async () => {
  await render(<SessionProgressBar total={27} activeIndex={12} />);

  expect(flexWeights()).toEqual([13, 14]);
});

it('survives a two-step circuit at the same shape', async () => {
  await render(<SessionProgressBar total={2} activeIndex={0} />);

  expect(flexWeights()).toEqual([1, 1]);
});

/**
 * The other regression. A one-block workout drew a single permanently-full indicator, and since it is
 * always complete it reads as a finished session from the first second. Four of the twenty workouts in
 * the library this was found on are single-block, so it was the *common* case there, not an edge one.
 *
 * Reintroducing the bug (dropping the `total < 2` guard) makes this fail with a bar found.
 */
it('draws nothing at all for a single-step workout, rather than one permanently full bar', async () => {
  await render(<SessionProgressBar total={1} activeIndex={0} />);

  expect(screen.queryByRole('progressbar')).toBeNull();
});

it('draws nothing for a workout with no blocks', async () => {
  await render(<SessionProgressBar total={0} activeIndex={0} />);

  expect(screen.queryByRole('progressbar')).toBeNull();
});

// The remaining track is dropped rather than rendered at flex 0, so a finished bar is one solid fill
// instead of a fill plus a zero-width sibling that some layout passes still round up to a hairline.
it('drops the remainder once the last step is reached', async () => {
  await render(<SessionProgressBar total={4} activeIndex={3} />);

  expect(flexWeights()).toEqual([4]);
});

// activeIndex can momentarily exceed the count while the runner advances off the end of a circuit;
// clamping means the bar reads full rather than inverting into a negative remainder.
it('clamps rather than inverting when the index runs past the end', async () => {
  await render(<SessionProgressBar total={3} activeIndex={9} />);

  expect(flexWeights()).toEqual([3]);
});

/**
 * The two levels are one component now, so what separates them is this prop plus where each is drawn.
 * They used to be drawn as different *kinds* of thing — dashes above, a bar below — which read as a
 * rendering fault rather than as a distinction.
 */
it('is the same shape at both levels, differing only in tone', async () => {
  const session = await render(<SessionProgressBar total={4} activeIndex={1} />);
  const sessionWeights = flexWeights();
  session.unmount();

  await render(<SessionProgressBar total={4} activeIndex={1} tone="calm" />);

  expect(flexWeights()).toEqual(sessionWeights);
});
