import { useEffect } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import Animated, { Easing, useAnimatedStyle, useSharedValue, withRepeat, withTiming } from 'react-native-reanimated';

import { ThemedText } from '@/components/themed-text';
import { SessionNextCard } from '@/components/session-next-card';
import { RunnerColors, Spacing } from '@/constants/theme';
import type { RestPreview } from '@/hooks/use-session-runner';

type Props = {
  exerciseName: string;
  setIndex: number;
  setTotal: number;
  targetSec: number;
  targetMaxSec?: number;
  elapsedSec: number;
  paused: boolean;
  notes?: string;
  next: RestPreview;
  onTogglePause: () => void;
  onPrev: () => void;
  onDone: () => void;
};

export function SessionHold({
  exerciseName,
  setIndex,
  setTotal,
  targetSec,
  targetMaxSec,
  elapsedSec,
  paused,
  notes,
  next,
  onTogglePause,
  onPrev,
  onDone,
}: Props) {
  const pulse = useSharedValue(1);

  useEffect(() => {
    pulse.value = withRepeat(withTiming(0.35, { duration: 650, easing: Easing.inOut(Easing.ease) }), -1, true);
  }, [pulse]);

  const pulseStyle = useAnimatedStyle(() => ({ opacity: pulse.value }));
  const fillPct = Math.min(100, (elapsedSec / targetSec) * 100);

  return (
    <View style={styles.container}>
      <View style={styles.top}>
        <View style={styles.livePill}>
          <Animated.View style={[styles.liveDot, pulseStyle]} />
          <ThemedText type="code" style={styles.liveLabel}>
            HOLD
          </ThemedText>
        </View>
        <ThemedText type="subtitle" style={styles.exerciseName}>
          {exerciseName}
        </ThemedText>
        <ThemedText type="small" style={styles.setLabel}>
          Set {setIndex} of {setTotal}
        </ThemedText>
        {notes && (
          <ThemedText type="small" style={styles.notes}>
            {notes}
          </ThemedText>
        )}
      </View>

      <View style={styles.middle}>
        <View style={styles.numeralRow}>
          <ThemedText type="numeral" style={styles.numeral}>
            {elapsedSec}
          </ThemedText>
          <ThemedText type="numeral" style={styles.numeralUnit}>
            s
          </ThemedText>
        </View>
        <View style={styles.progressTrack}>
          <View style={[styles.progressFill, { width: `${fillPct}%` }]} />
          <View style={styles.progressMarker} />
        </View>
        <ThemedText type="small" style={styles.captionLabel}>
          target {targetMaxSec ? `${targetSec}–${targetMaxSec}` : targetSec}s · counting up
        </ThemedText>
      </View>

      <SessionNextCard next={next} />

      <View style={styles.controlsRow}>
        <Pressable
          onPress={onPrev}
          accessibilityRole="button"
          accessibilityLabel="Previous step"
          style={styles.circleButton}>
          <View style={styles.iconPrev} />
        </Pressable>
        <Pressable onPress={onTogglePause} style={styles.pauseButton}>
          <ThemedText type="heading" style={styles.pauseButtonLabel}>
            {paused ? 'Resume' : 'Pause'}
          </ThemedText>
        </Pressable>
        <Pressable
          onPress={onDone}
          accessibilityRole="button"
          accessibilityLabel="Done, next step"
          style={styles.circleButton}>
          <View style={styles.iconNext} />
        </Pressable>
      </View>
      <Pressable onPress={onDone}>
        <ThemedText type="heading" style={styles.doneLabel}>
          Done set ↑
        </ThemedText>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'space-between',
  },
  top: {},
  livePill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
    alignSelf: 'flex-start',
    backgroundColor: RunnerColors.accentSoft,
    borderWidth: 1,
    borderColor: 'rgba(207,106,55,0.4)',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 999,
  },
  liveDot: {
    width: 7,
    height: 7,
    borderRadius: 4,
    backgroundColor: RunnerColors.accent,
  },
  liveLabel: {
    color: RunnerColors.accentOnSoft,
    letterSpacing: 1.4,
  },
  exerciseName: {
    marginTop: Spacing.two,
    color: RunnerColors.text,
  },
  setLabel: {
    marginTop: 4,
    color: RunnerColors.textSecondary,
  },
  notes: {
    marginTop: 4,
    color: RunnerColors.textSecondary,
    fontStyle: 'italic',
  },
  middle: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.two,
  },
  numeralRow: {
    flexDirection: 'row',
    alignItems: 'flex-end',
  },
  numeral: {
    fontSize: 96,
    lineHeight: 96,
    color: RunnerColors.text,
  },
  numeralUnit: {
    fontSize: 38,
    color: RunnerColors.textSecondary,
  },
  progressTrack: {
    width: 220,
    height: 6,
    borderRadius: 3,
    backgroundColor: 'rgba(243,239,228,0.14)',
  },
  progressFill: {
    position: 'absolute',
    left: 0,
    top: 0,
    bottom: 0,
    backgroundColor: RunnerColors.accent,
    borderRadius: 3,
  },
  progressMarker: {
    position: 'absolute',
    left: '100%',
    top: -5,
    width: 2,
    height: 16,
    marginLeft: -1,
    backgroundColor: RunnerColors.textSecondary,
  },
  captionLabel: {
    color: RunnerColors.textSecondary,
  },
  controlsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three - 2,
    marginTop: Spacing.three - 2,
  },
  circleButton: {
    width: 56,
    height: 56,
    borderRadius: 28,
    borderWidth: 1.5,
    borderColor: RunnerColors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  iconPrev: {
    width: 0,
    height: 0,
    borderTopWidth: 6,
    borderBottomWidth: 6,
    borderRightWidth: 9,
    borderTopColor: 'transparent',
    borderBottomColor: 'transparent',
    borderRightColor: RunnerColors.textSecondary,
  },
  iconNext: {
    width: 0,
    height: 0,
    borderTopWidth: 6,
    borderBottomWidth: 6,
    borderLeftWidth: 9,
    borderTopColor: 'transparent',
    borderBottomColor: 'transparent',
    borderLeftColor: RunnerColors.textSecondary,
  },
  pauseButton: {
    flex: 1,
    height: 64,
    borderRadius: 20,
    backgroundColor: RunnerColors.text,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pauseButtonLabel: {
    color: RunnerColors.background,
  },
  doneLabel: {
    textAlign: 'center',
    marginTop: Spacing.two,
    color: RunnerColors.accent,
  },
});
