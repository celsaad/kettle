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

export function SessionProgressDots({ total, activeIndex }: { total: number; activeIndex: number }) {
  return (
    <View
      style={[styles.row, { gap: gapFor(total) }]}
      accessibilityRole="progressbar"
      accessibilityValue={{ min: 1, max: Math.max(total, 1), now: activeIndex + 1 }}>
      {Array.from({ length: total }).map((_, index) => (
        <View key={index} style={[styles.dot, index === activeIndex && styles.dotActive]} />
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
});
