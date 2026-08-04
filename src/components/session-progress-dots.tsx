import { StyleSheet, View } from 'react-native';

import { RunnerColors } from '@/constants/theme';

/**
 * Fixed-width track, flexible segments — the header can't afford to grow with the workout.
 *
 * The segments used to be 9px each with a 6px gap and no cap, so a fourteen-block workout claimed
 * ~200px of a row that also holds the name and "Finish"; the name pushed from the left, the dots
 * from the right, and "Finish" went off the edge of the screen entirely. Everything below exists to
 * make the row's width independent of `total`.
 */
const TrackWidth = 116;
const DotWidth = 9;
const ActiveDotWidth = 22;

/**
 * Gaps can't flex — RN's `gap` is a fixed length — so at high counts they'd eat the whole track and
 * leave the segments hairlines (thirteen 6px gaps is 78 of 116px). Stepping the gap down by count
 * keeps the shrinking on the segments, where it degrades gracefully, and preserves the original 6px
 * rhythm for the block counts most workouts actually have.
 */
function gapFor(total: number) {
  if (total > 12) return 2;
  if (total > 8) return 4;
  return 6;
}

/**
 * `calm` is for the circuit crumb's track, which sits directly under the block track: two identical
 * rows of dots a few pixels apart read as one control with a rendering fault, and the runner has an
 * established second accent (the PR pill) to separate them with. The fill measures 4.47:1 on
 * `background`, against the 3:1 WCAG asks of a meaningful graphic.
 */
type Tone = 'accent' | 'calm';

export function SessionProgressDots({
  total,
  activeIndex,
  tone = 'accent',
}: {
  total: number;
  activeIndex: number;
  tone?: Tone;
}) {
  return (
    <View
      style={[styles.row, { gap: gapFor(total) }]}
      accessibilityRole="progressbar"
      accessibilityValue={{ min: 1, max: Math.max(total, 1), now: activeIndex + 1 }}>
      {Array.from({ length: total }).map((_, index) => (
        <View
          key={index}
          style={[
            styles.dot,
            index === activeIndex && styles.dotActive,
            index === activeIndex && tone === 'calm' && styles.dotActiveCalm,
          ]}
        />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    // Reserves the track whatever the count, and right-aligns so that few-block workouts sit against
    // "Finish" and read exactly as they did before, with the slack falling on the name's side.
    width: TrackWidth,
    justifyContent: 'flex-end',
    flexShrink: 0,
  },
  dot: {
    // flex shares the track; maxWidth stops a three-block workout from stretching into three fat
    // bars, and minWidth keeps a twenty-block one from thinning into something invisible (it may
    // overflow the track slightly at that point, which beats rendering nothing legible).
    flex: 1,
    minWidth: 3,
    maxWidth: DotWidth,
    height: 4,
    borderRadius: 2,
    backgroundColor: 'rgba(243,239,228,0.22)',
  },
  dotActive: {
    // Double weight, so the current block stays the widest segment at every count.
    flex: 2,
    maxWidth: ActiveDotWidth,
    backgroundColor: RunnerColors.accent,
  },
  dotActiveCalm: {
    backgroundColor: RunnerColors.accentCalm,
  },
});
