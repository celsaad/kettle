import { useEffect } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import Animated, { Easing, useAnimatedStyle, useSharedValue, withRepeat, withTiming } from 'react-native-reanimated';

import { ThemedText } from '@/components/themed-text';
import { SessionNextCard } from '@/components/session-next-card';
import { RestPreview } from '@/hooks/use-session-runner';
import { RunnerColors, Spacing } from '@/constants/theme';

function formatClock(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

type Props = {
  secondsRemaining: number;
  totalSeconds: number;
  next: RestPreview;
  onAddSeconds: (amount: number) => void;
  onSkip: () => void;
  onPrev: () => void;
};

export function SessionRest({ secondsRemaining, totalSeconds, next, onAddSeconds, onSkip, onPrev }: Props) {
  const pulse = useSharedValue(1);

  useEffect(() => {
    pulse.value = withRepeat(withTiming(0.35, { duration: 800, easing: Easing.inOut(Easing.ease) }), -1, true);
  }, [pulse]);

  const pulseStyle = useAnimatedStyle(() => ({ opacity: pulse.value }));

  return (
    <View style={styles.container}>
      <View style={styles.top}>
        <View style={styles.restPill}>
          <Animated.View style={[styles.restDot, pulseStyle]} />
          <ThemedText type="code" style={styles.restLabel}>
            REST
          </ThemedText>
        </View>
      </View>

      <View style={styles.middle}>
        <ThemedText type="numeral" style={styles.numeral}>
          {formatClock(secondsRemaining)}
        </ThemedText>
        <ThemedText type="small" style={styles.caption}>
          of {formatClock(totalSeconds)} remaining
        </ThemedText>
      </View>

      <SessionNextCard next={next} />

      <View style={styles.controlsRow}>
        <Pressable onPress={onPrev} style={styles.prevButton}>
          <ThemedText type="code" style={styles.prevLabel}>
            Prev
          </ThemedText>
        </Pressable>
        <Pressable onPress={() => onAddSeconds(30)} style={styles.addButton}>
          <ThemedText type="heading" style={styles.addButtonLabel}>
            +30s
          </ThemedText>
        </Pressable>
        <Pressable onPress={onSkip} style={styles.skipButton}>
          <ThemedText type="heading" style={styles.skipButtonLabel}>
            Skip rest →
          </ThemedText>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'space-between',
  },
  top: {},
  restPill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
    alignSelf: 'flex-start',
    backgroundColor: RunnerColors.accentCalmSoft,
    borderWidth: 1,
    borderColor: 'rgba(63,130,192,0.4)',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 999,
  },
  restDot: {
    width: 7,
    height: 7,
    borderRadius: 4,
    backgroundColor: RunnerColors.accentCalm,
  },
  restLabel: {
    color: RunnerColors.accentCalmOnSoft,
    letterSpacing: 1.4,
  },
  middle: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.two,
  },
  numeral: {
    fontSize: 100,
    lineHeight: 100,
    color: RunnerColors.accentCalm,
  },
  caption: {
    color: RunnerColors.textSecondary,
  },
  controlsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three - 2,
    marginTop: Spacing.three - 2,
  },
  prevButton: {
    width: 60,
    height: 60,
    borderRadius: 30,
    borderWidth: 1.5,
    borderColor: RunnerColors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  prevLabel: {
    color: RunnerColors.textSecondary,
  },
  addButton: {
    flex: 1,
    height: 60,
    borderRadius: 18,
    borderWidth: 1.5,
    borderColor: RunnerColors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  addButtonLabel: {
    color: RunnerColors.text,
  },
  skipButton: {
    flex: 1,
    height: 60,
    borderRadius: 18,
    backgroundColor: RunnerColors.text,
    alignItems: 'center',
    justifyContent: 'center',
  },
  skipButtonLabel: {
    color: RunnerColors.background,
  },
});
