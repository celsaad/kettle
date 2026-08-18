import { StyleSheet, View } from 'react-native';

import { RunnerColors } from '@/constants/theme';

/**
 * Fixed width, whatever it is counting.
 *
 * The header also holds the workout name and "Finish", and an indicator that grew with the workout
 * pushed "Finish" off the edge of the screen at fourteen blocks. Everything here is sized so the row's
 * width cannot depend on `total`.
 */
const TrackWidth = 116;

/** Which level a bar belongs to: the session as a whole, or the circuit inside it. */
type Tone = 'accent' | 'calm';

/**
 * How far through something you are, as one proportional bar. Used twice — once for blocks, once for
 * the members of a circuit — and it is the same component both times *on purpose*.
 *
 * **The two levels used to be drawn differently, and it was the worse mistake of the two.** Blocks
 * were discrete dashes (one per block) and the circuit was a continuous bar, stacked twenty pixels
 * apart in the same header: two visual languages saying the same kind of thing, which reads as a
 * rendering fault rather than as a distinction. They are now one shape, told apart by the three
 * signals that survive a glance from across a room — position (session on top, circuit below), weight
 * (4px against 3px, descending with scope, since a circuit is part of the session and not the other
 * way round), and the kicker that captions the lower one ("CIRCUIT · ROUND 1 OF 1"). Hue is the
 * fourth and is reinforcement, never the only signal.
 *
 * **Two flex children, never fixed segments.** The dashes broke on real data: circuit members are not
 * blocks, and a mobility routine written as one circuit runs 27 of them — 27 dashes need
 * `27x3px + 26x2px = 133px` inside this 116px track, so it overflowed, every dash sat at its 3px
 * floor, and the active one was 6px among them. Nothing a reader could pick out at arm's length,
 * which is the only distance the runner is ever read from. A proportional bar cannot overflow and
 * cannot shrink below legibility, so the same geometry reads correctly at 2 and at 50.
 *
 * It fills *through* the current step rather than up to it: you are in member 1 of 5, so a fifth of
 * the work is under way, and an empty bar on the first one would say the circuit hadn't started.
 *
 * **Nothing renders below two steps.** A single-block workout drew one permanently-full indicator for
 * the entire session, which reads as finished from the first second. One step is not progress.
 */
export function SessionProgressBar({
  total,
  activeIndex,
  tone = 'accent',
}: {
  total: number;
  activeIndex: number;
  tone?: Tone;
}) {
  if (total < 2) return null;

  const done = Math.min(activeIndex + 1, total);
  const remaining = Math.max(total - done, 0);

  return (
    <View
      // `accessible`, or the role and value are announced by nobody: a View is not an accessibility
      // element by default, so this was exposed as a couple of unnamed boxes and the progressbar
      // semantics it was given never reached a screen reader at all.
      accessible
      accessibilityRole="progressbar"
      accessibilityValue={{ min: 1, max: total, now: done }}
      style={[styles.track, tone === 'calm' && styles.trackCalm]}>
      <View style={[styles.fill, tone === 'calm' && styles.fillCalm, { flex: done }]} />
      {/* Rendered rather than left as empty space so the unfilled part is visibly *track* — without it
          a nearly-complete bar and a finished one look the same against the background. */}
      {remaining > 0 && <View style={{ flex: remaining }} />}
    </View>
  );
}

const styles = StyleSheet.create({
  track: {
    flexDirection: 'row',
    width: TrackWidth,
    height: 4,
    borderRadius: 2,
    overflow: 'hidden',
    backgroundColor: 'rgba(243,239,228,0.22)',
    // Never squeezed by the name to its left; the name truncates instead.
    flexShrink: 0,
  },
  // Thinner than the session bar above it, not thicker. A circuit is a subdivision of the session, so
  // an inner bar that outweighs the outer one states the hierarchy backwards — and on device that is
  // exactly how it read. Weight now descends with scope; hue and the kicker beside it do the rest.
  trackCalm: {
    height: 3,
    borderRadius: 1.5,
  },
  fill: {
    backgroundColor: RunnerColors.accent,
  },
  // Measures 4.47:1 on `background`, against the 3:1 WCAG asks of a meaningful graphic.
  fillCalm: {
    backgroundColor: RunnerColors.accentCalm,
  },
});
