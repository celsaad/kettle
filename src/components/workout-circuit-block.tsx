import { useState } from 'react';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { DragHandleGrip } from '@/components/drag-handle-grip';
import { circuitShape, ExerciseBadge, exerciseSummary } from '@/components/exercise-badge';
import type { DragHandle } from '@/components/reorderable-list';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { formatCircuitShape } from '@/domain/format';
import { MaxRounds } from '@/domain/schema';
import type { Exercise, WorkoutBlock } from '@/domain/types';
import { useTheme } from '@/hooks/use-theme';
import { useUnitSystem } from '@/state/preferences-store';

type CircuitBlock = Extract<WorkoutBlock, { kind: 'circuit' }>;

type Props = {
  block: CircuitBlock;
  /** The library's exercises, for resolving each member's name and summary. */
  exercises: Exercise[];
  dragHandle: DragHandle;
  onChange: (patch: Partial<CircuitBlock>) => void;
  onRemove: () => void;
  onRemoveMember: (memberIndex: number) => void;
};

/** One circuit block in the workout editor: its members, its optional id, and its rounds/rest config. */
export function WorkoutCircuitBlock({ block, exercises, dragHandle, onChange, onRemove, onRemoveMember }: Props) {
  const theme = useTheme();
  const { t } = useTranslation();
  const unitSystem = useUnitSystem();
  // Local to the block rather than a set of open indices held by the screen: the field is a disclosure
  // on this card and nothing outside it reads whether it's open.
  const [idFieldOpen, setIdFieldOpen] = useState(false);

  return (
    <View style={[styles.circuitBlock, { borderColor: theme.border, backgroundColor: theme.backgroundElement }]}>
      <View style={styles.circuitHeader}>
        <DragHandleGrip handle={dragHandle} />
        <ThemedText type="heading" style={styles.circuitTitle}>
          {t('workoutEditor.circuit')}
        </ThemedText>
        <Pressable
          onPress={onRemove}
          hitSlop={8}
          accessibilityRole="button"
          accessibilityLabel={t('workoutEditor.removeCircuitAccessibility')}
          style={styles.removeButton}>
          <ThemedText themeColor="textSecondary">✕</ThemedText>
        </Pressable>
      </View>

      <View style={styles.circuitMembers}>
        {block.members.map((member, memberIndex) => {
          const exercise = exercises.find((candidate) => candidate.id === member.exerciseId);
          if (!exercise) return null;
          return (
            <View key={`${member.exerciseId}-${memberIndex}`} style={styles.circuitMemberRow}>
              <ThemedText type="small" themeColor="textSecondary" style={styles.circuitMemberIndex}>
                {memberIndex + 1}
              </ThemedText>
              <View style={styles.rowText}>
                <ThemedText type="smallMedium">{exercise.name}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {exerciseSummary(exercise, unitSystem)}
                </ThemedText>
              </View>
              <ExerciseBadge type={exercise.type} />
              <Pressable
                onPress={() => onRemoveMember(memberIndex)}
                hitSlop={8}
                accessibilityRole="button"
                accessibilityLabel={t('workoutEditor.removeFromCircuitAccessibility', { name: exercise.name })}
                style={styles.removeButton}>
                <ThemedText themeColor="textSecondary">✕</ThemedText>
              </Pressable>
            </View>
          );
        })}
      </View>

      <Pressable
        onPress={() => setIdFieldOpen((open) => !open)}
        accessibilityRole="button"
        accessibilityState={{ expanded: idFieldOpen }}
        style={styles.circuitIdToggle}
        hitSlop={4}>
        <ThemedText type="small" themeColor="textSecondary" style={styles.circuitFieldLabel}>
          {block.id ? t('workoutEditor.blockIdWithValue', { id: block.id }) : t('workoutEditor.blockIdPlaceholder')}
        </ThemedText>
        <ThemedText type="small" themeColor="textSecondary">
          {idFieldOpen ? '⌄' : '›'}
        </ThemedText>
      </Pressable>
      {idFieldOpen && (
        <View style={styles.circuitIdField}>
          <ThemedText type="small" themeColor="textSecondary" style={styles.circuitIdHint}>
            {t('workoutEditor.blockIdHint')}
          </ThemedText>
          <TextInput
            value={block.id ?? ''}
            onChangeText={(text) => onChange({ id: text.trim() || undefined })}
            placeholder={t('workoutEditor.blockIdInputPlaceholder')}
            placeholderTextColor={theme.textSecondary}
            autoCapitalize="none"
            autoCorrect={false}
            autoFocus
            style={[styles.smallInput, { borderColor: theme.border, backgroundColor: theme.background, color: theme.text }]}
          />
        </View>
      )}

      <View style={styles.circuitConfigRow}>
        <View style={styles.circuitNumberField}>
          <ThemedText type="small" themeColor="textSecondary" style={styles.circuitFieldLabel}>
            {t('workoutEditor.rounds')}
          </ThemedText>
          <View style={styles.stepperRow}>
            <Pressable
              onPress={() => onChange({ rounds: Math.max(1, block.rounds - 1) })}
              accessibilityRole="button"
              accessibilityLabel={t('common.decrease', { label: t('workoutEditor.rounds') })}
              style={[styles.stepperButton, { borderColor: theme.border }]}>
              <ThemedText themeColor="textSecondary">−</ThemedText>
            </Pressable>
            <ThemedText type="smallMedium" style={styles.stepperValue}>
              {block.rounds}
            </ThemedText>
            <Pressable
              // Clamped to the schema's ceiling. This stepper is the only way to set a circuit's rounds,
              // and workout-editor.tsx's save() validates the name and nothing else — so an unbounded one
              // wrote a library that failed to parse on the next launch and was silently reseeded.
              onPress={() => onChange({ rounds: Math.min(MaxRounds, block.rounds + 1) })}
              accessibilityRole="button"
              accessibilityLabel={t('common.increase', { label: t('workoutEditor.rounds') })}
              style={[styles.stepperButton, { borderColor: theme.border }]}>
              <ThemedText themeColor="textSecondary">+</ThemedText>
            </Pressable>
          </View>
        </View>

        <View style={styles.circuitNumberField}>
          <ThemedText type="small" themeColor="textSecondary" style={styles.circuitFieldLabel}>
            {t('workoutEditor.restPerExercise')}
          </ThemedText>
          <TextInput
            value={String(block.restBetweenExercisesSec ?? 0)}
            onChangeText={(text) => onChange({ restBetweenExercisesSec: Number(text) || 0 })}
            keyboardType="numeric"
            style={[styles.smallInput, { borderColor: theme.border, backgroundColor: theme.background, color: theme.text }]}
          />
        </View>

        <View style={styles.circuitNumberField}>
          <ThemedText type="small" themeColor="textSecondary" style={styles.circuitFieldLabel}>
            {t('workoutEditor.restPerRound')}
          </ThemedText>
          <TextInput
            value={String(block.restBetweenRoundsSec ?? 0)}
            onChangeText={(text) => onChange({ restBetweenRoundsSec: Number(text) || 0 })}
            keyboardType="numeric"
            style={[styles.smallInput, { borderColor: theme.border, backgroundColor: theme.background, color: theme.text }]}
          />
        </View>
      </View>

      <ThemedText type="small" themeColor="textSecondary" style={styles.circuitSummaryText}>
        {formatCircuitShape(circuitShape(block))}
      </ThemedText>
    </View>
  );
}

const styles = StyleSheet.create({
  circuitBlock: {
    borderRadius: 16,
    borderWidth: 1,
    padding: Spacing.two + 6,
    gap: Spacing.two,
  },
  circuitHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  circuitTitle: {
    flex: 1,
  },
  rowText: {
    flex: 1,
    gap: 2,
  },
  removeButton: {
    paddingLeft: Spacing.one,
  },
  circuitMembers: {
    gap: Spacing.one + 2,
    paddingLeft: Spacing.three,
  },
  circuitMemberRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two - 2,
  },
  circuitMemberIndex: {
    width: 16,
  },
  circuitIdToggle: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  circuitIdField: {
    gap: 4,
    marginTop: 2,
  },
  circuitIdHint: {
    marginBottom: 2,
  },
  circuitConfigRow: {
    flexDirection: 'row',
    gap: Spacing.two,
  },
  circuitNumberField: {
    flex: 1,
    gap: 4,
  },
  circuitFieldLabel: {},
  stepperRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.one,
  },
  stepperButton: {
    width: 28,
    height: 28,
    borderRadius: 14,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepperValue: {
    minWidth: 20,
    textAlign: 'center',
  },
  smallInput: {
    height: 36,
    borderRadius: 10,
    borderWidth: 1,
    paddingHorizontal: Spacing.one + 2,
    fontSize: 14,
  },
  circuitSummaryText: {
    marginTop: 2,
  },
});
