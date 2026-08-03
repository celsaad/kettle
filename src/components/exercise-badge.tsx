import { t } from 'i18next';
import { StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { useTheme } from '@/hooks/use-theme';
import type { CircuitShape } from '@/domain/format';
import { Exercise, ExerciseType, WorkoutBlock } from '@/domain/types';
import { toDisplayWeight, type UnitSystem } from '@/domain/units';

export function ExerciseBadge({ type, overrideLabel }: { type: ExerciseType; overrideLabel?: string }) {
  const theme = useTheme();

  const BADGE: Record<ExerciseType, { label: string; bg: string; text: string }> = {
    hiit: { label: 'HIIT', bg: theme.accentSoft, text: theme.accentText },
    reps: { label: 'REPS', bg: theme.backgroundSelected, text: theme.textSecondary },
    timed_hold: { label: 'HOLD', bg: theme.accentCalmSoft, text: theme.accentCalmText },
    cardio: { label: 'CARDIO', bg: theme.backgroundSelected, text: theme.textSecondary },
    emom: { label: 'EMOM', bg: theme.backgroundSelected, text: theme.textSecondary },
    amrap: { label: 'AMRAP', bg: theme.backgroundSelected, text: theme.textSecondary },
    rest: { label: 'REST', bg: theme.backgroundSelected, text: theme.textSecondary },
  };

  const badge = BADGE[type];

  return (
    <View style={[styles.badge, { backgroundColor: overrideLabel ? theme.accentSoft : badge.bg }]}>
      <ThemedText type="code" style={{ color: overrideLabel ? theme.accentText : badge.text }}>
        {overrideLabel ?? badge.label}
      </ThemedText>
    </View>
  );
}

export function CircuitBadge() {
  const theme = useTheme();
  return (
    <View style={[styles.badge, { backgroundColor: theme.backgroundSelected }]}>
      <ThemedText type="code" style={{ color: theme.textSecondary }}>
        CIRCUIT
      </ThemedText>
    </View>
  );
}

function rangeLabel(min: number, max: number | undefined): string {
  return max ? `${min}–${max}` : `${min}`;
}

/**
 * `unitSystem` is passed in rather than read from the store because this is a plain function, not a
 * component — its two call sites are screens that already hold `useUnitSystem()`.
 */
export function exerciseSummary(exercise: Exercise, unitSystem: UnitSystem): string {
  switch (exercise.type) {
    case 'hiit':
      return t('summary.hiit', {
        work: exercise.config.workSec,
        rest: exercise.config.restSec,
        rounds: exercise.config.rounds,
      });
    case 'reps':
      return t('summary.reps', {
        sets: exercise.config.sets,
        reps: rangeLabel(exercise.config.targetRepsMin, exercise.config.targetRepsMax),
        weight: exercise.config.targetWeightKg
          ? t('summary.repsWeight', {
              weight: toDisplayWeight(exercise.config.targetWeightKg, unitSystem),
              unit: t(unitSystem === 'imperial' ? 'units.lb' : 'units.kg'),
            })
          : '',
        rest: exercise.config.restSec,
      });
    case 'timed_hold':
      // A max-effort hold has no duration to put in the badge, and "3 × s" is what the shared string
      // renders without one.
      return exercise.config.holdSecMin === undefined
        ? t('summary.holdOpen', { sets: exercise.config.sets, rest: exercise.config.restSec })
        : t('summary.hold', {
            sets: exercise.config.sets,
            hold: rangeLabel(exercise.config.holdSecMin, exercise.config.holdSecMax),
            rest: exercise.config.restSec,
          });
    case 'emom':
      return t('summary.emom', { interval: exercise.config.intervalSec, minutes: exercise.config.totalMinutes });
    case 'amrap':
      return t('summary.amrap', { cap: exercise.config.timeCapSec });
    case 'cardio':
      return exercise.config.distanceMeters
        ? t('summary.cardioDistance', { n: exercise.config.distanceMeters })
        : t('summary.cardioDuration', { n: exercise.config.durationSec ?? 0 });
    case 'rest':
      return t('summary.restSeconds', { n: exercise.config.durationSec });
  }
}

/** Structured, not a sentence — `formatCircuitShape` in domain/format.ts renders it. */
export function circuitShape(block: Extract<WorkoutBlock, { kind: 'circuit' }>): CircuitShape {
  return {
    rounds: block.rounds,
    restBetweenExercisesSec: block.restBetweenExercisesSec ?? 0,
    restBetweenRoundsSec: block.restBetweenRoundsSec ?? 0,
  };
}

const styles = StyleSheet.create({
  badge: {
    paddingHorizontal: 9,
    paddingVertical: 4,
    borderRadius: 7,
  },
});
