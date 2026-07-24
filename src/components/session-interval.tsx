import { useEffect } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import Animated, { Easing, useAnimatedStyle, useSharedValue, withRepeat, withTiming } from 'react-native-reanimated';

import { ThemedText } from '@/components/themed-text';
import type { IntervalVariant } from '@/hooks/use-session-runner';
import { RunnerColors, Spacing } from '@/constants/theme';

function formatClock(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

const VARIANT_LABEL: Record<IntervalVariant, string> = {
  hiit: 'HIIT',
  emom: 'EMOM',
  amrap: 'AMRAP',
  cardio: 'CARDIO',
};

type StepperProps = {
  label: string;
  value: number;
  onChange: (value: number) => void;
};

function Stepper({ label, value, onChange }: StepperProps) {
  return (
    <View style={styles.stepperBlock}>
      <ThemedText type="code" style={styles.stepperLabel}>
        {label}
      </ThemedText>
      <View style={styles.stepperRow}>
        <Pressable onPress={() => onChange(Math.max(0, value - 1))} style={styles.stepperButton}>
          <ThemedText type="title" style={styles.stepperGlyph}>
            −
          </ThemedText>
        </Pressable>
        <ThemedText type="heading" style={styles.stepperValue}>
          {value}
        </ThemedText>
        <Pressable onPress={() => onChange(value + 1)} style={[styles.stepperButton, styles.stepperButtonAccent]}>
          <ThemedText type="title" style={[styles.stepperGlyph, styles.stepperGlyphAccent]}>
            +
          </ThemedText>
        </Pressable>
      </View>
    </View>
  );
}

type Props = {
  exerciseName: string;
  variant: IntervalVariant;
  setIndex: number;
  setTotal: number;
  targetSec: number;
  countUp: boolean;
  elapsedSec: number;
  remainingSec: number;
  targetReps?: number;
  cardioDistanceMeters?: number;
  notes?: string;
  paused: boolean;
  onTogglePause: () => void;
  reps: number;
  onChangeReps: (reps: number) => void;
  roundsCompleted: number;
  onChangeRoundsCompleted: (rounds: number) => void;
  extraReps: number;
  onChangeExtraReps: (reps: number) => void;
  onPrev: () => void;
  onDone: () => void;
};

export function SessionInterval({
  exerciseName,
  variant,
  setIndex,
  setTotal,
  targetSec,
  countUp,
  elapsedSec,
  remainingSec,
  targetReps,
  cardioDistanceMeters,
  notes,
  paused,
  onTogglePause,
  reps,
  onChangeReps,
  roundsCompleted,
  onChangeRoundsCompleted,
  extraReps,
  onChangeExtraReps,
  onPrev,
  onDone,
}: Props) {
  const pulse = useSharedValue(1);

  useEffect(() => {
    pulse.value = withRepeat(withTiming(0.35, { duration: 650, easing: Easing.inOut(Easing.ease) }), -1, true);
  }, [pulse]);

  const pulseStyle = useAnimatedStyle(() => ({ opacity: pulse.value }));

  const setLabel =
    variant === 'hiit' ? `Round ${setIndex} of ${setTotal}` : variant === 'emom' ? `Minute ${setIndex} of ${setTotal}` : null;

  const captionParts: string[] = [];
  if (variant === 'emom' && targetReps) captionParts.push(`target ${targetReps} reps`);
  if (variant === 'cardio' && cardioDistanceMeters) captionParts.push(`target ${cardioDistanceMeters}m`);
  captionParts.push(countUp ? 'counting up' : `of ${formatClock(targetSec)} total`);

  const fillPct = countUp || targetSec <= 0 ? 0 : Math.min(100, ((targetSec - remainingSec) / targetSec) * 100);

  return (
    <View style={styles.container}>
      <View style={styles.top}>
        <View style={styles.livePill}>
          <Animated.View style={[styles.liveDot, pulseStyle]} />
          <ThemedText type="code" style={styles.liveLabel}>
            {VARIANT_LABEL[variant]}
          </ThemedText>
        </View>
        <ThemedText type="subtitle" style={styles.exerciseName}>
          {exerciseName}
        </ThemedText>
        {setLabel && (
          <ThemedText type="small" style={styles.setLabel}>
            {setLabel}
          </ThemedText>
        )}
        {notes && (
          <ThemedText type="small" style={styles.notes}>
            {notes}
          </ThemedText>
        )}
      </View>

      <View style={styles.middle}>
        <ThemedText type="numeral" style={styles.numeral}>
          {countUp ? formatClock(elapsedSec) : formatClock(remainingSec)}
        </ThemedText>
        {!countUp && (
          <View style={styles.progressTrack}>
            <View style={[styles.progressFill, { width: `${fillPct}%` }]} />
          </View>
        )}
        <ThemedText type="small" style={styles.captionLabel}>
          {captionParts.join(' · ')}
        </ThemedText>

        {variant === 'emom' && <Stepper label="REPS THIS MINUTE" value={reps} onChange={onChangeReps} />}

        {variant === 'amrap' && (
          <View style={styles.amrapRow}>
            <Stepper label="ROUNDS" value={roundsCompleted} onChange={onChangeRoundsCompleted} />
            <Stepper label="+REPS" value={extraReps} onChange={onChangeExtraReps} />
          </View>
        )}
      </View>

      <View style={styles.controlsRow}>
        <Pressable onPress={onPrev} style={styles.circleButton}>
          <View style={styles.iconPrev} />
        </Pressable>
        <Pressable onPress={onTogglePause} style={styles.pauseButton}>
          <ThemedText type="heading" style={styles.pauseButtonLabel}>
            {paused ? 'Resume' : 'Pause'}
          </ThemedText>
        </Pressable>
        <Pressable onPress={onDone} style={styles.circleButton}>
          <View style={styles.iconNext} />
        </Pressable>
      </View>
      <Pressable onPress={onDone}>
        <ThemedText type="heading" style={styles.doneLabel}>
          {countUp ? 'Done ↑' : 'Skip →'}
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
    color: RunnerColors.accent,
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
  numeral: {
    fontSize: 88,
    lineHeight: 88,
    color: RunnerColors.text,
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
  captionLabel: {
    color: RunnerColors.textSecondary,
  },
  stepperBlock: {
    marginTop: Spacing.three,
    alignItems: 'center',
    gap: Spacing.one,
  },
  stepperRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.three - 2,
  },
  stepperButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    borderWidth: 1.5,
    borderColor: RunnerColors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepperButtonAccent: {
    backgroundColor: RunnerColors.accentSoft,
    borderColor: 'rgba(207,106,55,0.4)',
  },
  stepperGlyph: {
    fontSize: 22,
    color: RunnerColors.textSecondary,
  },
  stepperGlyphAccent: {
    color: RunnerColors.accent,
  },
  stepperValue: {
    minWidth: 36,
    textAlign: 'center',
    color: RunnerColors.text,
  },
  stepperLabel: {
    color: RunnerColors.textSecondary,
    letterSpacing: 1.2,
  },
  amrapRow: {
    flexDirection: 'row',
    gap: Spacing.four,
  },
  controlsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three - 2,
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
