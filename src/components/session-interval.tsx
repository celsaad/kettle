import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import type { TFunction } from 'i18next';
import { useTranslation } from 'react-i18next';
import Animated, {
  Easing,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';

import { ThemedText } from '@/components/themed-text';
import { SessionNextCard } from '@/components/session-next-card';
import { SessionNumberPad } from '@/components/session-number-pad';
import type { IntervalVariant, RestPreview } from '@/hooks/use-session-runner';
import { RunnerColors, Spacing } from '@/constants/theme';

function formatClock(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

// These acronyms (HIIT/EMOM/AMRAP) and "CARDIO" don't change between en and pt — Brazilian fitness
// usage keeps the English training-style names — but they're still routed through t() so the seam
// exists if that ever stops being true for a future locale.
const VARIANT_LABEL_KEY: Record<IntervalVariant, string> = {
  hiit: 'session.interval.variantHiit',
  emom: 'session.interval.variantEmom',
  amrap: 'session.interval.variantAmrap',
  cardio: 'session.interval.variantCardio',
};

type StepperProps = {
  label: string;
  value: number;
  onChange: (value: number) => void;
  onRequestEdit: () => void;
  t: TFunction;
};

/**
 * The keypad is *not* rendered here. Its overlay fills its parent, and in React Native that's the
 * nearest view rather than the screen — inside a stepper block it would be clipped to a small strip.
 * The parent owns which field is being edited and renders one pad at the screen root.
 */
function Stepper({ label, value, onChange, onRequestEdit, t }: StepperProps) {
  return (
    <View style={styles.stepperBlock}>
      <ThemedText type="code" style={styles.stepperLabel}>
        {label}
      </ThemedText>
      <View style={styles.stepperRow}>
        <Pressable
          onPress={() => onChange(Math.max(0, value - 1))}
          accessibilityRole="button"
          accessibilityLabel={t('common.decrease', { label })}
          style={styles.stepperButton}>
          <ThemedText type="title" style={styles.stepperGlyph}>
            −
          </ThemedText>
        </Pressable>
        {/* Same escape hatch as the reps screen: an AMRAP round count in the twenties is a lot of
            taps, and these steppers sit in the same tight runner layout. */}
        <Pressable
          onPress={onRequestEdit}
          accessibilityRole="button"
          accessibilityLabel={t('session.stepperAccessibility', { label, value })}>
          <ThemedText type="heading" style={styles.stepperValue}>
            {value}
          </ThemedText>
        </Pressable>
        <Pressable
          onPress={() => onChange(value + 1)}
          accessibilityRole="button"
          accessibilityLabel={t('common.increase', { label })}
          style={[styles.stepperButton, styles.stepperButtonAccent]}>
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
  next: RestPreview;
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
  next,
  onPrev,
  onDone,
}: Props) {
  const [editing, setEditing] = useState<'reps' | 'rounds' | 'extra' | null>(null);
  const { t } = useTranslation();
  const pulse = useSharedValue(1);
  const reduceMotion = useReducedMotion();

  useEffect(() => {
    if (reduceMotion) {
      pulse.value = 1;
      return;
    }
    pulse.value = withRepeat(withTiming(0.35, { duration: 650, easing: Easing.inOut(Easing.ease) }), -1, true);
  }, [pulse, reduceMotion]);

  const pulseStyle = useAnimatedStyle(() => ({ opacity: pulse.value }));

  // "Minute" only when the interval actually is one — an EMOM here is any fixed-interval block, so a
  // 30-second interval counting "Minute 3 of 20" would be plainly wrong.
  const setLabel =
    variant === 'hiit'
      ? t('session.interval.roundLabel', { index: setIndex, total: setTotal })
      : variant === 'emom'
        ? targetSec === 60
          ? t('session.interval.minuteLabel', { index: setIndex, total: setTotal })
          : t('session.interval.intervalLabel', { index: setIndex, total: setTotal })
        : null;

  const captionParts: string[] = [];
  if (variant === 'emom' && targetReps) captionParts.push(t('session.interval.targetReps', { reps: targetReps }));
  if (variant === 'cardio' && cardioDistanceMeters)
    captionParts.push(t('session.interval.targetDistance', { meters: cardioDistanceMeters }));
  captionParts.push(countUp ? t('session.countingUp') : t('session.interval.ofTotal', { clock: formatClock(targetSec) }));

  const fillPct = countUp || targetSec <= 0 ? 0 : Math.min(100, ((targetSec - remainingSec) / targetSec) * 100);

  return (
    <View style={styles.container}>
      <View style={styles.top}>
        <View style={styles.livePill}>
          <Animated.View style={[styles.liveDot, pulseStyle]} />
          <ThemedText type="code" style={styles.liveLabel}>
            {t(VARIANT_LABEL_KEY[variant])}
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
        <ThemedText type="numeral" maxFontSizeMultiplier={1.3} style={styles.numeral}>
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

        {variant === 'emom' && (
          <Stepper
            label={t('session.interval.repsThisMinuteCaps')}
            value={reps}
            onChange={onChangeReps}
            onRequestEdit={() => setEditing('reps')}
            t={t}
          />
        )}

        {variant === 'amrap' && (
          <View style={styles.amrapRow}>
            <Stepper
              label={t('session.interval.roundsCaps')}
              value={roundsCompleted}
              onChange={onChangeRoundsCompleted}
              onRequestEdit={() => setEditing('rounds')}
              t={t}
            />
            <Stepper
              label={t('session.interval.extraRepsCaps')}
              value={extraReps}
              onChange={onChangeExtraReps}
              onRequestEdit={() => setEditing('extra')}
              t={t}
            />
          </View>
        )}
      </View>

      <SessionNextCard next={next} />

      <View style={styles.controlsRow}>
        <Pressable
          onPress={onPrev}
          accessibilityRole="button"
          accessibilityLabel={t('session.previousStep')}
          style={styles.circleButton}>
          <View style={styles.iconPrev} />
        </Pressable>
        <Pressable onPress={onTogglePause} accessibilityRole="button" style={styles.pauseButton}>
          <ThemedText type="heading" style={styles.pauseButtonLabel}>
            {paused ? t('session.resume') : t('session.pause')}
          </ThemedText>
        </Pressable>
        <Pressable
          onPress={onDone}
          accessibilityRole="button"
          accessibilityLabel={t('session.doneNextStep')}
          style={styles.circleButton}>
          <View style={styles.iconNext} />
        </Pressable>
      </View>
      {/*
       * Promoted to a filled button only when the clock counts up. That's the AMRAP-shaped case, where
       * nothing ends the step but this tap — the same reason the hold screen's is always primary. A
       * countdown interval auto-advances at zero, so there this is a genuine "skip ahead" and stays a
       * quiet link; hitSlop rather than minHeight gets it to a 44px target without giving a secondary
       * action a button's weight.
       */}
      <Pressable
        onPress={onDone}
        accessibilityRole="button"
        hitSlop={countUp ? undefined : { top: 12, bottom: 12, left: 24, right: 24 }}
        style={countUp ? styles.doneButton : styles.skipLink}>
        <ThemedText type="heading" style={countUp ? styles.doneLabel : styles.skipLabel}>
          {countUp ? t('session.interval.doneUp') : t('session.interval.skipArrow')}
        </ThemedText>
      </Pressable>

      {editing && (
        <SessionNumberPad
          label={
            editing === 'reps'
              ? t('session.interval.repsThisMinute')
              : editing === 'rounds'
                ? t('session.interval.roundsLabel')
                : t('session.interval.extraRepsLabel')
          }
          initialValue={editing === 'reps' ? reps : editing === 'rounds' ? roundsCompleted : extraReps}
          onCancel={() => setEditing(null)}
          onConfirm={(value) => {
            const whole = Math.round(value);
            if (editing === 'reps') onChangeReps(whole);
            else if (editing === 'rounds') onChangeRoundsCompleted(whole);
            else onChangeExtraReps(whole);
            setEditing(null);
          }}
        />
      )}
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
    textAlign: 'center',
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
    minHeight: 64,
    paddingVertical: Spacing.one,
    paddingHorizontal: Spacing.two + 4,
    borderRadius: 20,
    borderWidth: 1.5,
    borderColor: RunnerColors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pauseButtonLabel: {
    textAlign: 'center',
    color: RunnerColors.text,
  },
  // accent + background is the reps screen's log-set pairing (4.9:1), so no new color decision here.
  doneButton: {
    marginTop: Spacing.two,
    minHeight: 64,
    paddingVertical: Spacing.one,
    paddingHorizontal: Spacing.two,
    borderRadius: 20,
    backgroundColor: RunnerColors.accent,
    alignItems: 'center',
    justifyContent: 'center',
  },
  doneLabel: {
    textAlign: 'center',
    color: RunnerColors.background,
  },
  skipLink: {
    marginTop: Spacing.two,
  },
  skipLabel: {
    textAlign: 'center',
    color: RunnerColors.accent,
  },
});
