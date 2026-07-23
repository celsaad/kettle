import { StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { useTheme } from '@/hooks/use-theme';
import { Exercise, ExerciseType } from '@/domain/types';

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

export function exerciseSummary(exercise: Exercise): string {
  switch (exercise.type) {
    case 'hiit':
      return `${exercise.config.workSec}s work · ${exercise.config.restSec}s rest · ×${exercise.config.rounds}`;
    case 'reps': {
      const weight = exercise.config.targetWeightKg ? ` · ${exercise.config.targetWeightKg} kg` : '';
      return `${exercise.config.sets} × ${exercise.config.targetReps}${weight} · ${exercise.config.restSec}s rest`;
    }
    case 'timed_hold':
      return `${exercise.config.sets} × ${exercise.config.holdSec}s · ${exercise.config.restSec}s rest`;
    case 'emom':
      return `${exercise.config.intervalSec}s interval · ${exercise.config.totalMinutes} min`;
    case 'amrap':
      return `${exercise.config.timeCapSec}s cap`;
    case 'cardio':
      return exercise.config.distanceMeters ? `${exercise.config.distanceMeters} m` : `${exercise.config.durationSec ?? 0}s`;
    case 'rest':
      return `${exercise.config.durationSec} seconds`;
  }
}

const styles = StyleSheet.create({
  badge: {
    paddingHorizontal: 9,
    paddingVertical: 4,
    borderRadius: 7,
  },
});
