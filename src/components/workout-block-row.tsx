import { Pressable, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { DragHandleGrip } from '@/components/drag-handle-grip';
import { ExerciseBadge, exerciseSummary } from '@/components/exercise-badge';
import type { DragHandle } from '@/components/reorderable-list';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import type { Exercise } from '@/domain/types';
import { useTheme } from '@/hooks/use-theme';
import { useUnitSystem } from '@/state/preferences-store';

type Props = {
  exercise: Exercise;
  /** A rest block's per-workout duration override, which replaces the exercise's own summary. */
  overrideSec?: number;
  dragHandle: DragHandle;
  onRemove: () => void;
};

/** One plain (non-circuit) block in the workout editor's list. */
export function WorkoutBlockRow({ exercise, overrideSec, dragHandle, onRemove }: Props) {
  const theme = useTheme();
  const { t } = useTranslation();
  const unitSystem = useUnitSystem();

  const isRest = exercise.type === 'rest';
  const summary =
    isRest && overrideSec
      ? t('workoutEditor.overrideSeconds', { count: overrideSec })
      : exerciseSummary(exercise, unitSystem);

  return (
    <View
      style={[
        styles.row,
        isRest
          ? { borderWidth: 1, borderStyle: 'dashed', borderColor: theme.border }
          : { backgroundColor: theme.backgroundElement, borderWidth: 1, borderColor: theme.border },
      ]}>
      <DragHandleGrip handle={dragHandle} />
      <View style={styles.rowText}>
        <ThemedText type={isRest ? 'default' : 'heading'}>{exercise.name}</ThemedText>
        <ThemedText type="small" themeColor="textSecondary">
          {summary}
        </ThemedText>
      </View>
      <ExerciseBadge type={exercise.type} overrideLabel={overrideSec ? t('workoutEditor.overrideBadge') : undefined} />
      <Pressable
        onPress={onRemove}
        hitSlop={8}
        accessibilityRole="button"
        accessibilityLabel={t('workoutEditor.removeAccessibility', { name: exercise.name })}
        style={styles.removeButton}>
        <ThemedText themeColor="textSecondary">✕</ThemedText>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderRadius: 16,
    padding: Spacing.two + 6,
  },
  rowText: {
    flex: 1,
    gap: 2,
  },
  removeButton: {
    paddingLeft: Spacing.one,
  },
});
