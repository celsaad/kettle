import { router, useLocalSearchParams } from 'expo-router';
import { useMemo, useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { circuitSummary, ExerciseBadge, exerciseSummary } from '@/components/exercise-badge';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing, MaxContentWidth } from '@/constants/theme';
import type { Workout, WorkoutBlock } from '@/domain/types';
import { useTheme } from '@/hooks/use-theme';
import { findExerciseInLibrary, useLibraryStore } from '@/state/library-store';

const EMPTY_WORKOUT: Workout = { id: '', name: '', blocks: [] };

function slugify(name: string): string {
  return name
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-+|-+$)/g, '');
}

export default function WorkoutEditorScreen() {
  const theme = useTheme();
  const { id } = useLocalSearchParams<{ id?: string }>();
  const library = useLibraryStore((state) => state.library);
  const saveWorkout = useLibraryStore((state) => state.saveWorkout);
  const deleteWorkout = useLibraryStore((state) => state.deleteWorkout);

  const editing = useMemo(() => library?.workouts.find((candidate) => candidate.id === id), [library, id]);

  const [draft, setDraft] = useState<Workout>(() => editing ?? EMPTY_WORKOUT);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [circuitPickerOpen, setCircuitPickerOpen] = useState(false);
  const [circuitSelection, setCircuitSelection] = useState<string[]>([]);
  const [openIdFields, setOpenIdFields] = useState<Set<number>>(new Set());
  const [error, setError] = useState<string | null>(null);

  if (!library) return null;

  const removeBlock = (index: number) => {
    setDraft((current) => ({ ...current, blocks: current.blocks.filter((_, i) => i !== index) }));
  };

  const addBlock = (exerciseId: string) => {
    setDraft((current) => ({ ...current, blocks: [...current.blocks, { kind: 'exercise', exerciseId }] }));
    setPickerOpen(false);
  };

  const toggleCircuitMember = (exerciseId: string) => {
    setCircuitSelection((current) =>
      current.includes(exerciseId) ? current.filter((existingId) => existingId !== exerciseId) : [...current, exerciseId],
    );
  };

  const addCircuit = () => {
    if (circuitSelection.length < 2) return;
    setDraft((current) => ({
      ...current,
      blocks: [
        ...current.blocks,
        {
          kind: 'circuit',
          rounds: 3,
          restBetweenExercisesSec: 15,
          restBetweenRoundsSec: 60,
          members: circuitSelection.map((exerciseId) => ({ exerciseId })),
        },
      ],
    }));
    setCircuitSelection([]);
    setCircuitPickerOpen(false);
  };

  const updateCircuit = (index: number, patch: Partial<Extract<WorkoutBlock, { kind: 'circuit' }>>) => {
    setDraft((current) => ({
      ...current,
      blocks: current.blocks.map((block, i) => (i === index && block.kind === 'circuit' ? { ...block, ...patch } : block)),
    }));
  };

  const toggleIdField = (index: number) => {
    setOpenIdFields((current) => {
      const next = new Set(current);
      if (next.has(index)) next.delete(index);
      else next.add(index);
      return next;
    });
  };

  const removeCircuitMember = (index: number, memberIndex: number) => {
    setDraft((current) => ({
      ...current,
      blocks: current.blocks.map((block, i) =>
        i === index && block.kind === 'circuit'
          ? { ...block, members: block.members.filter((_, mi) => mi !== memberIndex) }
          : block,
      ),
    }));
  };

  const close = () => router.back();

  const save = async () => {
    if (!draft.name.trim()) {
      setError('Name is required.');
      return;
    }
    const workoutId = editing?.id || slugify(draft.name);
    if (!workoutId) {
      setError('Could not derive an id from that name.');
      return;
    }
    await saveWorkout({ ...draft, id: workoutId, name: draft.name.trim() });
    close();
  };

  const confirmDelete = () => {
    if (!editing) return;
    const usedBy = (library.programs ?? []).filter((program) => program.weeks.some((week) => week.workoutId === editing.id));
    if (usedBy.length > 0) {
      Alert.alert(
        'Workout in use',
        `"${editing.name}" is used by ${usedBy.map((program) => program.name).join(', ')}. Remove it from those programs first.`,
      );
      return;
    }
    Alert.alert('Delete workout?', `"${editing.name}" will be permanently removed.`, [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: async () => {
          await deleteWorkout(editing.id);
          close();
        },
      },
    ]);
  };

  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: theme.background }]} edges={['bottom', 'left', 'right']}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={[styles.grabber, { backgroundColor: theme.border }]} />
        <ThemedText type="subtitle">{editing ? 'Edit workout' : 'New workout'}</ThemedText>

        <ThemedText type="small" themeColor="textSecondary" style={styles.label}>
          Name
        </ThemedText>
        <TextInput
          value={draft.name}
          onChangeText={(name) => setDraft((current) => ({ ...current, name }))}
          placeholder="e.g. Push day"
          placeholderTextColor={theme.textSecondary}
          style={[styles.input, { borderColor: theme.border, backgroundColor: theme.backgroundElement, color: theme.text }]}
        />

        <ThemedText themeColor="textSecondary" style={styles.blockCount}>
          {draft.blocks.length} blocks
        </ThemedText>

        <View style={styles.list}>
          {draft.blocks.map((block, index) => {
            if (block.kind === 'exercise') {
              const exercise = findExerciseInLibrary(library, block.exerciseId);
              if (!exercise) return null;
              const isRest = exercise.type === 'rest';
              const overrideSec = block.configOverride?.durationSec;
              const summary = isRest && overrideSec ? `${overrideSec} seconds` : exerciseSummary(exercise);

              return (
                <View
                  key={`exercise-${index}`}
                  style={[
                    styles.row,
                    isRest
                      ? { borderWidth: 1, borderStyle: 'dashed', borderColor: theme.border }
                      : { backgroundColor: theme.backgroundElement, borderWidth: 1, borderColor: theme.border },
                  ]}>
                  <ThemedText themeColor="textSecondary" style={styles.dragHandle}>
                    ⣿
                  </ThemedText>
                  <View style={styles.rowText}>
                    <ThemedText type={isRest ? 'default' : 'heading'}>{exercise.name}</ThemedText>
                    <ThemedText type="small" themeColor="textSecondary">
                      {summary}
                    </ThemedText>
                  </View>
                  <ExerciseBadge type={exercise.type} overrideLabel={overrideSec ? 'OVERRIDE' : undefined} />
                  <Pressable onPress={() => removeBlock(index)} hitSlop={8} style={styles.removeButton}>
                    <ThemedText themeColor="textSecondary">✕</ThemedText>
                  </Pressable>
                </View>
              );
            }

            return (
              <View key={`circuit-${index}`} style={[styles.circuitBlock, { borderColor: theme.border, backgroundColor: theme.backgroundElement }]}>
                <View style={styles.circuitHeader}>
                  <ThemedText themeColor="textSecondary" style={styles.dragHandle}>
                    ⣿
                  </ThemedText>
                  <ThemedText type="heading" style={styles.circuitTitle}>
                    Circuit
                  </ThemedText>
                  <Pressable onPress={() => removeBlock(index)} hitSlop={8} style={styles.removeButton}>
                    <ThemedText themeColor="textSecondary">✕</ThemedText>
                  </Pressable>
                </View>

                <View style={styles.circuitMembers}>
                  {block.members.map((member, memberIndex) => {
                    const exercise = findExerciseInLibrary(library, member.exerciseId);
                    if (!exercise) return null;
                    return (
                      <View key={`${member.exerciseId}-${memberIndex}`} style={styles.circuitMemberRow}>
                        <ThemedText type="small" themeColor="textSecondary" style={styles.circuitMemberIndex}>
                          {memberIndex + 1}
                        </ThemedText>
                        <View style={styles.rowText}>
                          <ThemedText type="smallMedium">{exercise.name}</ThemedText>
                          <ThemedText type="small" themeColor="textSecondary">
                            {exerciseSummary(exercise)}
                          </ThemedText>
                        </View>
                        <ExerciseBadge type={exercise.type} />
                        <Pressable onPress={() => removeCircuitMember(index, memberIndex)} hitSlop={8} style={styles.removeButton}>
                          <ThemedText themeColor="textSecondary">✕</ThemedText>
                        </Pressable>
                      </View>
                    );
                  })}
                </View>

                <Pressable onPress={() => toggleIdField(index)} style={styles.circuitIdToggle} hitSlop={4}>
                  <ThemedText type="small" themeColor="textSecondary" style={styles.circuitFieldLabel}>
                    {block.id ? `Block ID: ${block.id}` : 'Block ID (optional)'}
                  </ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">
                    {openIdFields.has(index) ? '⌄' : '›'}
                  </ThemedText>
                </Pressable>
                {openIdFields.has(index) && (
                  <View style={styles.circuitIdField}>
                    <ThemedText type="small" themeColor="textSecondary" style={styles.circuitIdHint}>
                      Lets a program override this circuit's rounds/rest for a specific week.
                    </ThemedText>
                    <TextInput
                      value={block.id ?? ''}
                      onChangeText={(text) => updateCircuit(index, { id: text.trim() || undefined })}
                      placeholder="e.g. finisher-rounds"
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
                      Rounds
                    </ThemedText>
                    <View style={styles.stepperRow}>
                      <Pressable
                        onPress={() => updateCircuit(index, { rounds: Math.max(1, block.rounds - 1) })}
                        style={[styles.stepperButton, { borderColor: theme.border }]}>
                        <ThemedText themeColor="textSecondary">−</ThemedText>
                      </Pressable>
                      <ThemedText type="smallMedium" style={styles.stepperValue}>
                        {block.rounds}
                      </ThemedText>
                      <Pressable
                        onPress={() => updateCircuit(index, { rounds: block.rounds + 1 })}
                        style={[styles.stepperButton, { borderColor: theme.border }]}>
                        <ThemedText themeColor="textSecondary">+</ThemedText>
                      </Pressable>
                    </View>
                  </View>

                  <View style={styles.circuitNumberField}>
                    <ThemedText type="small" themeColor="textSecondary" style={styles.circuitFieldLabel}>
                      Rest/exercise (s)
                    </ThemedText>
                    <TextInput
                      value={String(block.restBetweenExercisesSec ?? 0)}
                      onChangeText={(text) => updateCircuit(index, { restBetweenExercisesSec: Number(text) || 0 })}
                      keyboardType="numeric"
                      style={[styles.smallInput, { borderColor: theme.border, backgroundColor: theme.background, color: theme.text }]}
                    />
                  </View>

                  <View style={styles.circuitNumberField}>
                    <ThemedText type="small" themeColor="textSecondary" style={styles.circuitFieldLabel}>
                      Rest/round (s)
                    </ThemedText>
                    <TextInput
                      value={String(block.restBetweenRoundsSec ?? 0)}
                      onChangeText={(text) => updateCircuit(index, { restBetweenRoundsSec: Number(text) || 0 })}
                      keyboardType="numeric"
                      style={[styles.smallInput, { borderColor: theme.border, backgroundColor: theme.background, color: theme.text }]}
                    />
                  </View>
                </View>

                <ThemedText type="small" themeColor="textSecondary" style={styles.circuitSummaryText}>
                  {circuitSummary(block)}
                </ThemedText>
              </View>
            );
          })}
        </View>

        <View style={styles.addButtonsRow}>
          <Pressable
            onPress={() => {
              setPickerOpen((current) => !current);
              setCircuitPickerOpen(false);
            }}
            style={({ pressed }) => [styles.addBlock, styles.addBlockHalf, { borderColor: theme.border }, pressed && styles.pressed]}>
            <ThemedText type="heading" themeColor="textSecondary">
              {pickerOpen ? 'Close' : '+ Add block'}
            </ThemedText>
          </Pressable>
          <Pressable
            onPress={() => {
              setCircuitPickerOpen((current) => !current);
              setPickerOpen(false);
              setCircuitSelection([]);
            }}
            style={({ pressed }) => [styles.addBlock, styles.addBlockHalf, { borderColor: theme.border }, pressed && styles.pressed]}>
            <ThemedText type="heading" themeColor="textSecondary">
              {circuitPickerOpen ? 'Close' : '+ New circuit'}
            </ThemedText>
          </Pressable>
        </View>

        {pickerOpen && (
          <View style={styles.picker}>
            {library.exercises.map((exercise) => (
              <Pressable key={exercise.id} onPress={() => addBlock(exercise.id)}>
                <ThemedView type="backgroundElement" style={[styles.pickerRow, { borderColor: theme.border }]}>
                  <ThemedText type="smallMedium" style={styles.pickerRowText}>
                    {exercise.name}
                  </ThemedText>
                  <ExerciseBadge type={exercise.type} />
                </ThemedView>
              </Pressable>
            ))}
          </View>
        )}

        {circuitPickerOpen && (
          <View style={styles.picker}>
            <ThemedText type="small" themeColor="textSecondary">
              Select at least 2 exercises
            </ThemedText>
            {library.exercises.map((exercise) => {
              const selected = circuitSelection.includes(exercise.id);
              return (
                <Pressable key={exercise.id} onPress={() => toggleCircuitMember(exercise.id)}>
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
            })}
            <Pressable
              onPress={addCircuit}
              disabled={circuitSelection.length < 2}
              style={[styles.confirmCircuit, { backgroundColor: theme.accent }, circuitSelection.length < 2 && styles.disabled]}>
              <ThemedText type="heading" style={{ color: theme.onAccent }}>
                Add circuit ({circuitSelection.length})
              </ThemedText>
            </Pressable>
          </View>
        )}

        {error && (
          <ThemedText type="small" style={[styles.error, { color: theme.accentText }]}>
            {error}
          </ThemedText>
        )}

        <View style={styles.buttonRow}>
          <Pressable onPress={close} style={[styles.cancelButton, { borderColor: theme.border }]}>
            <ThemedText type="heading" themeColor="textSecondary">
              Cancel
            </ThemedText>
          </Pressable>
          <Pressable onPress={save} style={[styles.saveButton, { backgroundColor: theme.accent }]}>
            <ThemedText type="heading" style={{ color: theme.onAccent }}>
              Save
            </ThemedText>
          </Pressable>
        </View>

        {editing && (
          <Pressable onPress={confirmDelete} style={styles.deleteButton} hitSlop={8}>
            <ThemedText type="smallMedium" themeColor="textSecondary">
              Delete workout
            </ThemedText>
          </Pressable>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
  },
  scrollContent: {
    alignSelf: 'center',
    width: '100%',
    maxWidth: MaxContentWidth,
    paddingHorizontal: Spacing.three,
    paddingTop: Spacing.two,
    paddingBottom: Spacing.four,
  },
  grabber: {
    alignSelf: 'center',
    width: 40,
    height: 5,
    borderRadius: 3,
    marginBottom: Spacing.three - 2,
  },
  disabled: {
    opacity: 0.4,
  },
  pressed: {
    opacity: 0.7,
  },
  label: {
    marginTop: Spacing.three - 2,
    marginBottom: Spacing.one,
  },
  input: {
    height: 46,
    borderRadius: 12,
    borderWidth: 1,
    paddingHorizontal: Spacing.two + 4,
    fontSize: 15,
  },
  blockCount: {
    marginTop: Spacing.two,
  },
  list: {
    marginTop: Spacing.three,
    gap: Spacing.two + 2,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderRadius: 16,
    padding: Spacing.two + 6,
  },
  dragHandle: {
    letterSpacing: -2,
  },
  rowText: {
    flex: 1,
    gap: 2,
  },
  removeButton: {
    paddingLeft: Spacing.one,
  },
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
  addButtonsRow: {
    flexDirection: 'row',
    gap: Spacing.two - 2,
    marginTop: Spacing.three,
  },
  addBlock: {
    height: 48,
    borderRadius: 14,
    borderWidth: 1.5,
    borderStyle: 'dashed',
    alignItems: 'center',
    justifyContent: 'center',
  },
  addBlockHalf: {
    flex: 1,
  },
  picker: {
    marginTop: Spacing.two,
    gap: Spacing.one + 2,
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
  error: {
    marginTop: Spacing.two,
  },
  buttonRow: {
    flexDirection: 'row',
    gap: Spacing.three - 4,
    marginTop: Spacing.four,
  },
  cancelButton: {
    flex: 1,
    height: 52,
    borderRadius: 15,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  saveButton: {
    flex: 1.4,
    height: 52,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
  },
  deleteButton: {
    alignItems: 'center',
    marginTop: Spacing.three - 2,
  },
});
