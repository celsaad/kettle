import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { ExerciseBadge } from '@/components/exercise-badge';
import { NewExerciseForm } from '@/components/new-exercise-form';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import type { Exercise } from '@/domain/types';
import { useTheme } from '@/hooks/use-theme';

type CommonProps = {
  exercises: Exercise[];
  /**
   * Persists a quick-added exercise and drops it into the picker. Returns whether it was saved: a
   * failed save leaves the form open so the entered values survive to be corrected, with the caller's
   * own error line explaining why.
   */
  onCreate: (exercise: Exercise) => Promise<boolean>;
};

type SingleProps = CommonProps & { mode: 'single'; onPick: (exerciseId: string) => void };

type MultiProps = CommonProps & {
  mode: 'multi';
  selected: string[];
  onToggle: (exerciseId: string) => void;
  onConfirm: () => void;
};

/**
 * The workout editor's exercise picker, in the two modes it opens in: `single` picks one exercise to
 * add as a block, `multi` selects the members of a new circuit. One component because the two panels
 * were adjacent near-copies of each other — same list, same quick-add form, differing only in what a
 * row press means and in the confirm button the multi case needs.
 *
 * The quick-add form's open/closed state lives here rather than in the screen. Each mounted picker owns
 * its own, so closing one panel resets it for free, and `onCreate` no longer has to work out which
 * panel it was called from.
 */
export function ExercisePicker(props: SingleProps | MultiProps) {
  const theme = useTheme();
  const { t } = useTranslation();
  const [newExerciseOpen, setNewExerciseOpen] = useState(false);

  const create = async (exercise: Exercise) => {
    if (await props.onCreate(exercise)) setNewExerciseOpen(false);
  };

  if (newExerciseOpen) {
    return (
      <View style={styles.picker}>
        <NewExerciseForm onCreate={create} onCancel={() => setNewExerciseOpen(false)} />
      </View>
    );
  }

  return (
    <View style={styles.picker}>
      {props.mode === 'multi' && (
        <ThemedText type="small" themeColor="textSecondary">
          {t('workoutEditor.selectAtLeast2')}
        </ThemedText>
      )}

      <Pressable
        onPress={() => setNewExerciseOpen(true)}
        accessibilityRole="button"
        style={[styles.newExerciseButton, { borderColor: theme.border }]}>
        <ThemedText type="smallMedium" themeColor="textSecondary">
          {t('workoutEditor.newExercise')}
        </ThemedText>
      </Pressable>

      {props.exercises.map((exercise) =>
        props.mode === 'single' ? (
          <Pressable key={exercise.id} onPress={() => props.onPick(exercise.id)} accessibilityRole="button">
            <ThemedView type="backgroundElement" style={[styles.pickerRow, { borderColor: theme.border }]}>
              <ThemedText type="smallMedium" style={styles.pickerRowText}>
                {exercise.name}
              </ThemedText>
              <ExerciseBadge type={exercise.type} />
            </ThemedView>
          </Pressable>
        ) : (
          <SelectableRow
            key={exercise.id}
            exercise={exercise}
            selected={props.selected.includes(exercise.id)}
            onToggle={() => props.onToggle(exercise.id)}
          />
        ),
      )}

      {props.mode === 'multi' && (
        <Pressable
          onPress={props.onConfirm}
          disabled={props.selected.length < 2}
          accessibilityRole="button"
          accessibilityState={{ disabled: props.selected.length < 2 }}
          style={[styles.confirmCircuit, { backgroundColor: theme.accent }, props.selected.length < 2 && styles.disabled]}>
          <ThemedText type="heading" style={{ color: theme.onAccent }}>
            {t('workoutEditor.addCircuitCount', { count: props.selected.length })}
          </ThemedText>
        </Pressable>
      )}
    </View>
  );
}

/** A `checkbox` rather than a `button`: in multi mode a press toggles membership rather than acting. */
function SelectableRow({ exercise, selected, onToggle }: { exercise: Exercise; selected: boolean; onToggle: () => void }) {
  const theme = useTheme();

  return (
    <Pressable onPress={onToggle} accessibilityRole="checkbox" accessibilityState={{ checked: selected }}>
      <ThemedView
        type="backgroundElement"
        style={[styles.pickerRow, { borderColor: selected ? theme.accent : theme.border }]}>
        <View
          style={[
            styles.checkbox,
            { borderColor: theme.border },
            selected && { backgroundColor: theme.accent, borderColor: theme.accent },
          ]}>
          {selected && (
            <ThemedText type="small" style={{ color: theme.onAccent }}>
              ✓
            </ThemedText>
          )}
        </View>
        <ThemedText type="smallMedium" style={styles.pickerRowText}>
          {exercise.name}
        </ThemedText>
        <ExerciseBadge type={exercise.type} />
      </ThemedView>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  picker: {
    marginTop: Spacing.two,
    gap: Spacing.one + 2,
  },
  newExerciseButton: {
    height: 40,
    borderRadius: 10,
    borderWidth: 1,
    borderStyle: 'dashed',
    alignItems: 'center',
    justifyContent: 'center',
  },
  pickerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.two,
    borderRadius: 12,
    borderWidth: 1,
    padding: Spacing.two,
  },
  pickerRowText: {
    flex: 1,
  },
  checkbox: {
    width: 20,
    height: 20,
    borderRadius: 5,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  confirmCircuit: {
    height: 48,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: Spacing.one,
  },
  disabled: {
    opacity: 0.4,
  },
});
