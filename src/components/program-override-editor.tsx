import { useState } from 'react';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';

import { buildExercise, CONFIG_FIELDS, configToStrings, type FieldDef } from '@/app/exercise-editor';
import { overrideLines } from '@/app/program-detail';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { applyBlockOverride, applyExerciseOverride, diffBlockOverride, diffExerciseOverride } from '@/domain/yaml-mapping';
import type { Exercise, Library, ProgramOverride, Workout, WorkoutBlock } from '@/domain/types';
import { useTheme } from '@/hooks/use-theme';

type CircuitBlock = Extract<WorkoutBlock, { kind: 'circuit' }>;
type Target = { kind: 'exercise'; exercise: Exercise } | { kind: 'block'; block: CircuitBlock };
type Step = 'closed' | 'choose-target' | 'edit-fields';

type Props = {
  library: Library;
  /** The week's currently-selected workout — determines which exercises/circuits are eligible targets. */
  workout: Workout | undefined;
  overrides: ProgramOverride[];
  onChange: (overrides: ProgramOverride[]) => void;
};

function workoutExerciseIds(workout: Workout): string[] {
  const ids = new Set<string>();
  for (const block of workout.blocks) {
    if (block.kind === 'exercise') ids.add(block.exerciseId);
    else for (const member of block.members) ids.add(member.exerciseId);
  }
  return [...ids];
}

function workoutIdTaggedCircuits(workout: Workout): CircuitBlock[] {
  return workout.blocks.filter((block): block is CircuitBlock => block.kind === 'circuit' && !!block.id);
}

/**
 * Editor for a program week's overrides — the per-exercise/per-circuit config patches. Kept as its own
 * component (rather than inline in program-editor.tsx) since it owns a real chunk of transient
 * add/edit-flow state. The tricky part it hides: an override's `config` is a partial, snake_case patch
 * (see applyExerciseOverride/applyBlockOverride in domain/yaml-mapping.ts), but the field form below
 * works in ordinary camelCase domain values like any other exercise form — diffExerciseOverride/
 * diffBlockOverride convert an edited value back into just the changed raw keys on confirm.
 */
export function ProgramOverrideEditor({ library, workout, overrides, onChange }: Props) {
  const theme = useTheme();
  const [step, setStep] = useState<Step>('closed');
  const [editingIndex, setEditingIndex] = useState<number | null>(null);
  const [target, setTarget] = useState<Target | null>(null);
  const [fieldValues, setFieldValues] = useState<Record<string, string>>({});

  const close = () => {
    setStep('closed');
    setEditingIndex(null);
    setTarget(null);
    setFieldValues({});
  };

  const startAdd = () => {
    setEditingIndex(null);
    setTarget(null);
    setFieldValues({});
    setStep('choose-target');
  };

  const chooseTarget = (next: Target) => {
    setTarget(next);
    setFieldValues(
      next.kind === 'exercise'
        ? configToStrings(next.exercise)
        : {
            rounds: String(next.block.rounds),
            restBetweenExercisesSec: String(next.block.restBetweenExercisesSec ?? 0),
            restBetweenRoundsSec: String(next.block.restBetweenRoundsSec ?? 0),
          },
    );
    setStep('edit-fields');
  };

  const startEdit = (index: number) => {
    const override = overrides[index];
    if (override.kind === 'exercise') {
      const base = library.exercises.find((candidate) => candidate.id === override.exerciseId);
      if (!base) return;
      const effective = applyExerciseOverride(base, override.config);
      setTarget({ kind: 'exercise', exercise: base });
      setFieldValues(configToStrings(effective));
    } else {
      const block = workout?.blocks.find(
        (candidate): candidate is CircuitBlock => candidate.kind === 'circuit' && candidate.id === override.blockId,
      );
      if (!block) return;
      const effective = applyBlockOverride(block, override.config) as CircuitBlock;
      setTarget({ kind: 'block', block });
      setFieldValues({
        rounds: String(effective.rounds),
        restBetweenExercisesSec: String(effective.restBetweenExercisesSec ?? 0),
        restBetweenRoundsSec: String(effective.restBetweenRoundsSec ?? 0),
      });
    }
    setEditingIndex(index);
    setStep('edit-fields');
  };

  const removeOverride = (index: number) => {
    onChange(overrides.filter((_, i) => i !== index));
  };

  const setField = (key: string, text: string) => setFieldValues((prev) => ({ ...prev, [key]: text }));

  const confirm = () => {
    if (!target) return;
    let nextOverride: ProgramOverride;
    if (target.kind === 'exercise') {
      const edited = buildExercise(target.exercise.id, target.exercise.name, target.exercise.type, fieldValues, target.exercise.notes ?? '');
      nextOverride = { kind: 'exercise', exerciseId: target.exercise.id, config: diffExerciseOverride(target.exercise, edited) };
    } else {
      const blockId = target.block.id;
      if (!blockId) return;
      const edited: CircuitBlock = {
        ...target.block,
        rounds: Number(fieldValues.rounds) || target.block.rounds,
        restBetweenExercisesSec: Number(fieldValues.restBetweenExercisesSec) || 0,
        restBetweenRoundsSec: Number(fieldValues.restBetweenRoundsSec) || 0,
      };
      nextOverride = { kind: 'block', blockId, config: diffBlockOverride(target.block, edited) };
    }
    onChange(editingIndex !== null ? overrides.map((existing, i) => (i === editingIndex ? nextOverride : existing)) : [...overrides, nextOverride]);
    close();
  };

  const eligibleExercises = (workout ? workoutExerciseIds(workout) : [])
    .map((id) => library.exercises.find((exercise) => exercise.id === id))
    .filter((exercise): exercise is Exercise => !!exercise);
  const eligibleCircuits = workout ? workoutIdTaggedCircuits(workout) : [];
  const noEligibleTargets = eligibleExercises.length === 0 && eligibleCircuits.length === 0;

  const exerciseFields: FieldDef[] = target?.kind === 'exercise' ? CONFIG_FIELDS[target.exercise.type] : [];

  return (
    <View style={styles.container}>
      {overrides.length > 0 && (
        <View style={styles.list}>
          {overrides.map((override, index) => {
            const lines = overrideLines(override, library, workout);
            const resolvable =
              override.kind === 'exercise'
                ? library.exercises.some((exercise) => exercise.id === override.exerciseId)
                : !!workout?.blocks.some((block) => block.kind === 'circuit' && block.id === override.blockId);
            return (
              <View key={index} style={[styles.overrideRow, { borderColor: theme.border }]}>
                <Pressable onPress={() => resolvable && startEdit(index)} disabled={!resolvable} style={styles.overrideRowText}>
                  {lines.map((line, lineIndex) => (
                    <ThemedText key={lineIndex} type="small" themeColor="textSecondary">
                      {line}
                    </ThemedText>
                  ))}
                </Pressable>
                <Pressable onPress={() => removeOverride(index)} hitSlop={8}>
                  <ThemedText themeColor="textSecondary">✕</ThemedText>
                </Pressable>
              </View>
            );
          })}
        </View>
      )}

      {step === 'closed' && (
        <>
          <Pressable
            onPress={startAdd}
            disabled={!workout || noEligibleTargets}
            style={[styles.addButton, { borderColor: theme.border }, (!workout || noEligibleTargets) && styles.disabled]}>
            <ThemedText type="small" themeColor="textSecondary">
              + Add override
            </ThemedText>
          </Pressable>
          {workout && noEligibleTargets && (
            <ThemedText type="small" themeColor="textSecondary" style={styles.hint}>
              This workout has nothing to override yet.
            </ThemedText>
          )}
        </>
      )}

      {step === 'choose-target' && (
        <View style={styles.picker}>
          {eligibleExercises.map((exercise) => (
            <Pressable key={exercise.id} onPress={() => chooseTarget({ kind: 'exercise', exercise })}>
              <ThemedView type="backgroundElement" style={[styles.pickerRow, { borderColor: theme.border }]}>
                <ThemedText type="smallMedium" style={styles.pickerRowText}>
                  {exercise.name}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  Exercise
                </ThemedText>
              </ThemedView>
            </Pressable>
          ))}
          {eligibleCircuits.map((block) => (
            <Pressable key={block.id} onPress={() => chooseTarget({ kind: 'block', block })}>
              <ThemedView type="backgroundElement" style={[styles.pickerRow, { borderColor: theme.border }]}>
                <ThemedText type="smallMedium" style={styles.pickerRowText}>
                  {block.id}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  Circuit
                </ThemedText>
              </ThemedView>
            </Pressable>
          ))}
          <Pressable onPress={close} style={styles.cancelPickerButton}>
            <ThemedText type="small" themeColor="textSecondary">
              Cancel
            </ThemedText>
          </Pressable>
        </View>
      )}

      {step === 'edit-fields' && target && (
        <View style={[styles.fieldsPanel, { borderColor: theme.border }]}>
          <ThemedText type="smallMedium">
            {target.kind === 'exercise' ? target.exercise.name : `Circuit (${target.block.id})`}
          </ThemedText>

          {target.kind === 'block' ? (
            <>
              <View style={styles.fieldRow}>
                <ThemedText type="small" themeColor="textSecondary" style={styles.fieldLabel}>
                  Rounds
                </ThemedText>
                <View style={styles.stepperRow}>
                  <Pressable
                    onPress={() => setField('rounds', String(Math.max(1, (Number(fieldValues.rounds) || 1) - 1)))}
                    style={[styles.stepperButton, { borderColor: theme.border }]}>
                    <ThemedText themeColor="textSecondary">−</ThemedText>
                  </Pressable>
                  <ThemedText type="smallMedium" style={styles.stepperValue}>
                    {fieldValues.rounds ?? '1'}
                  </ThemedText>
                  <Pressable
                    onPress={() => setField('rounds', String((Number(fieldValues.rounds) || 1) + 1))}
                    style={[styles.stepperButton, { borderColor: theme.border }]}>
                    <ThemedText themeColor="textSecondary">+</ThemedText>
                  </Pressable>
                </View>
              </View>
              <View style={styles.fieldRow}>
                <ThemedText type="small" themeColor="textSecondary" style={styles.fieldLabel}>
                  Rest/exercise (sec)
                </ThemedText>
                <TextInput
                  value={fieldValues.restBetweenExercisesSec ?? ''}
                  onChangeText={(text) => setField('restBetweenExercisesSec', text)}
                  keyboardType="numeric"
                  style={[styles.fieldInput, { borderColor: theme.border, backgroundColor: theme.background, color: theme.text }]}
                />
              </View>
              <View style={styles.fieldRow}>
                <ThemedText type="small" themeColor="textSecondary" style={styles.fieldLabel}>
                  Rest/round (sec)
                </ThemedText>
                <TextInput
                  value={fieldValues.restBetweenRoundsSec ?? ''}
                  onChangeText={(text) => setField('restBetweenRoundsSec', text)}
                  keyboardType="numeric"
                  style={[styles.fieldInput, { borderColor: theme.border, backgroundColor: theme.background, color: theme.text }]}
                />
              </View>
            </>
          ) : (
            exerciseFields.map((field) => (
              <View key={field.key} style={styles.fieldRow}>
                <ThemedText type="small" themeColor="textSecondary" style={styles.fieldLabel}>
                  {field.label} {field.unit ? `(${field.unit})` : ''}
                  {field.optional ? ' · optional' : ''}
                </ThemedText>
                <TextInput
                  value={fieldValues[field.key] ?? ''}
                  onChangeText={(text) => setField(field.key, text)}
                  keyboardType="numeric"
                  style={[styles.fieldInput, { borderColor: theme.border, backgroundColor: theme.background, color: theme.text }]}
                />
              </View>
            ))
          )}

          <View style={styles.fieldsButtonRow}>
            <Pressable onPress={close} style={[styles.smallCancelButton, { borderColor: theme.border }]}>
              <ThemedText type="small" themeColor="textSecondary">
                Cancel
              </ThemedText>
            </Pressable>
            <Pressable onPress={confirm} style={[styles.smallConfirmButton, { backgroundColor: theme.accent }]}>
              <ThemedText type="small" style={{ color: theme.onAccent }}>
                {editingIndex !== null ? 'Save override' : 'Add override'}
              </ThemedText>
            </Pressable>
          </View>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    marginTop: Spacing.one,
  },
  list: {
    gap: Spacing.one,
  },
  overrideRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    gap: Spacing.two,
    borderRadius: 10,
    borderWidth: 1,
    padding: Spacing.two - 2,
  },
  overrideRowText: {
    flex: 1,
    gap: 2,
  },
  addButton: {
    height: 36,
    borderRadius: 10,
    borderWidth: 1,
    borderStyle: 'dashed',
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: Spacing.one,
  },
  disabled: {
    opacity: 0.4,
  },
  hint: {
    marginTop: 4,
  },
  picker: {
    marginTop: Spacing.one,
    gap: Spacing.one,
  },
  pickerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.two,
    borderRadius: 10,
    borderWidth: 1,
    padding: Spacing.two - 2,
  },
  pickerRowText: {
    flex: 1,
  },
  cancelPickerButton: {
    alignItems: 'center',
    paddingVertical: Spacing.one,
  },
  fieldsPanel: {
    marginTop: Spacing.one,
    borderRadius: 10,
    borderWidth: 1,
    padding: Spacing.two - 2,
    gap: Spacing.one,
  },
  fieldRow: {
    gap: 4,
  },
  fieldLabel: {},
  fieldInput: {
    height: 36,
    borderRadius: 8,
    borderWidth: 1,
    paddingHorizontal: Spacing.one + 2,
    fontSize: 13,
  },
  stepperRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.one,
  },
  stepperButton: {
    width: 26,
    height: 26,
    borderRadius: 13,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepperValue: {
    minWidth: 20,
    textAlign: 'center',
  },
  fieldsButtonRow: {
    flexDirection: 'row',
    gap: Spacing.one,
    marginTop: 2,
  },
  smallCancelButton: {
    flex: 1,
    height: 36,
    borderRadius: 10,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  smallConfirmButton: {
    flex: 1,
    height: 36,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
