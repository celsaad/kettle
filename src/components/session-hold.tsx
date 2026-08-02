import { useEffect } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
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
  // Guarded the same way session-interval.tsx's bar is: a 0 target makes this NaN before the clock
  // starts (0/0) and Infinity after it, and `width: "NaN%"` is an invalid style rather than a
  // no-op. validateConfig keeps the in-app editor from writing a 0-second hold, but a program
  // week's `hold_sec_min: 0` override reaches here unchecked from either direction — the override
  // schema types `config` as a free record of numbers, and the in-app override editor doesn't run
  // validateConfig either.
  //
  // The bar spans the *top* of the range, not the bottom (§12.2, settled): a range hold scaled to its
  // minimum pegged at 100% the moment the minimum was reached and stayed there for the whole span the
  // range exists to describe — so on a 15–25s hold the bar was uninformative across exactly the
  // seconds that decide the set. Scaled to the maximum, the fill keeps moving to the end, and the
  // minimum becomes a mark to cross rather than the finish line.
  const barSec = targetMaxSec && targetMaxSec > targetSec ? targetMaxSec : targetSec;
  const fillPct = barSec > 0 ? Math.min(100, (elapsedSec / barSec) * 100) : 0;
  // Where "enough" sits on that bar: the end of the track for a single target, part-way along it for a
  // range. Guarded with the fill, since a 0 target divides here too.
  const markerPct = barSec > 0 ? Math.min(100, (targetSec / barSec) * 100) : 100;

  return (
    <View style={styles.container}>
      <View style={styles.top}>
        <View style={styles.livePill}>
          <Animated.View style={[styles.liveDot, pulseStyle]} />
          <ThemedText type="code" style={styles.liveLabel}>
            {t('session.hold.label')}
          </ThemedText>
        </View>
        <ThemedText type="subtitle" style={styles.exerciseName}>
          {exerciseName}
        </ThemedText>
        <ThemedText type="small" style={styles.setLabel}>
          {t('session.setOf', { index: setIndex, total: setTotal })}
        </ThemedText>
        {notes && (
          <ThemedText type="small" style={styles.notes}>
            {notes}
          </ThemedText>
        )}
      </View>

      <View style={styles.middle}>
        <View style={styles.numeralRow}>
          <ThemedText type="numeral" maxFontSizeMultiplier={1.3} style={styles.numeral}>
            {elapsedSec}
          </ThemedText>
          {/* Capped to match the numeral it sits beside — letting the unit scale while the number
              doesn't would break their alignment rather than help anyone read it. */}
          <ThemedText type="numeral" maxFontSizeMultiplier={1.3} style={styles.numeralUnit}>
            s
          </ThemedText>
        </View>
        <View style={styles.progressTrack}>
          <View style={[styles.progressFill, { width: `${fillPct}%` }]} />
          <View style={[styles.progressMarker, { left: `${markerPct}%` }]} />
        </View>
        <ThemedText type="small" style={styles.captionLabel}>
          {t('session.hold.caption', { target: targetMaxSec ? `${targetSec}–${targetMaxSec}` : targetSec })}
        </ThemedText>
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
       * The primary action, styled like one. A hold counts up with its target as a marker and never
       * auto-advances (unlike rest and a countdown interval), so this is the *only* way out of the
       * step — and it was a bare text link with no minHeight, giving it the smallest touch target on
       * the screen where you're least able to aim. Pause, which is the interruption case, had the
       * filled treatment instead; it's outlined now so the hierarchy matches what each one does.
       */}
      <Pressable onPress={onDone} accessibilityRole="button" style={styles.doneButton}>
        <ThemedText type="heading" style={styles.doneLabel}>
          {t('session.hold.doneSet')}
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
  // `left` is set inline: it marks the range's minimum, which is only 100% when there's no range.
  progressMarker: {
    position: 'absolute',
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
});
