import { router, useLocalSearchParams } from 'expo-router';
import { useCallback, useMemo, useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { GestureDetector } from 'react-native-gesture-handler';
import { SafeAreaView } from 'react-native-safe-area-context';

import { circuitShape, ExerciseBadge, exerciseSummary } from '@/components/exercise-badge';
import { ModalHeader } from '@/components/modal-header';
import { NewExerciseForm } from '@/components/new-exercise-form';
import { ReorderableList } from '@/components/reorderable-list';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing, MaxContentWidth } from '@/constants/theme';
import { formatCircuitShape } from '@/domain/format';
import { slugify } from '@/domain/slug';
import type { Exercise, Workout, WorkoutBlock } from '@/domain/types';
import { useTheme } from '@/hooks/use-theme';
import { findExerciseInLibrary, useLibraryStore } from '@/state/library-store';
import { useUnitSystem } from '@/state/preferences-store';

export { ModalErrorBoundary as ErrorBoundary } from '@/components/error-fallback';

const EMPTY_WORKOUT: Workout = { id: '', name: '', blocks: [] };

export default function WorkoutEditorScreen() {
  const theme = useTheme();
  const { t } = useTranslation();
  const unitSystem = useUnitSystem();
  const { id } = useLocalSearchParams<{ id?: string }>();
  const library = useLibraryStore((state) => state.library);
  const saveWorkout = useLibraryStore((state) => state.saveWorkout);
  const deleteWorkout = useLibraryStore((state) => state.deleteWorkout);
  const saveExercise = useLibraryStore((state) => state.saveExercise);

  const editing = useMemo(() => library?.workouts.find((candidate) => candidate.id === id), [library, id]);

  const [draft, setDraft] = useState<Workout>(() => editing ?? EMPTY_WORKOUT);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [circuitPickerOpen, setCircuitPickerOpen] = useState(false);
  const [circuitSelection, setCircuitSelection] = useState<string[]>([]);
  const [openIdFields, setOpenIdFields] = useState<Set<number>>(new Set());
  const [newExerciseOpen, setNewExerciseOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!library) return null;

  const removeBlock = (index: number) => {
    setDraft((current) => ({ ...current, blocks: current.blocks.filter((_, i) => i !== index) }));
  };

  const handleReorder = useCallback((blocks: WorkoutBlock[]) => {
    setDraft((current) => ({ ...current, blocks }));
  }, []);

  const addBlock = (exerciseId: string) => {
    setDraft((current) => ({ ...current, blocks: [...current.blocks, { kind: 'exercise', exerciseId }] }));
    setPickerOpen(false);
  };

  const toggleCircuitMember = (exerciseId: string) => {
    setCircuitSelection((current) =>
      current.includes(exerciseId) ? current.filter((existingId) => existingId !== exerciseId) : [...current, exerciseId],
    );
  };

  // Persists a quick-added exercise to the library, then drops it into whichever picker triggered the
  // form: a plain block (addBlock also closes that picker) or a circuit-in-progress member selection
  // (kept open — building a circuit means picking several exercises, not just one).
  const handleCreateExercise = async (exercise: Exercise) => {
    try {
      await saveExercise(exercise);
    } catch (err) {
      setError(t('common.saveFailed', { detail: (err as Error).message }));
      return;
    }
    if (circuitPickerOpen) toggleCircuitMember(exercise.id);
    else addBlock(exercise.id);
    setNewExerciseOpen(false);
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
      setError(t('common.nameRequired'));
      return;
    }
    const workoutId = editing?.id || slugify(draft.name);
    if (!workoutId) {
      setError(t('common.couldNotDeriveId'));
      return;
    }
    try {
      await saveWorkout({ ...draft, id: workoutId, name: draft.name.trim() });
    } catch (err) {
      setError(t('common.saveFailed', { detail: (err as Error).message }));
      return;
    }
    close();
  };

  const confirmDelete = () => {
    if (!editing) return;
    const usedBy = (library.programs ?? []).filter((program) => program.weeks.some((week) => week.workoutId === editing.id));
    if (usedBy.length > 0) {
      Alert.alert(
        t('workoutEditor.inUseTitle'),
        t('workoutEditor.inUseBody', { name: editing.name, programs: usedBy.map((program) => program.name).join(', ') }),
      );
      return;
    }
    Alert.alert(t('workoutEditor.deleteConfirmTitle'), t('common.deleteConfirmBody', { name: editing.name }), [
      { text: t('common.cancel'), style: 'cancel' },
      {
        text: t('common.delete'),
        style: 'destructive',
        onPress: async () => {
          try {
            await deleteWorkout(editing.id);
          } catch (err) {
            setError(t('common.deleteFailed', { detail: (err as Error).message }));
            return;
          }
          close();
        },
      },
    ]);
  };

  return (
    <SafeAreaView
      style={[styles.safeArea, { backgroundColor: theme.background }]}
      edges={['top', 'bottom', 'left', 'right']}>
      <ModalHeader onClose={close} />
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <ThemedText type="subtitle">{editing ? t('workoutEditor.editTitle') : t('workoutEditor.newTitle')}</ThemedText>

        <ThemedText type="small" themeColor="textSecondary" style={styles.label}>
          {t('common.name')}
        </ThemedText>
        <TextInput
          value={draft.name}
          onChangeText={(name) => setDraft((current) => ({ ...current, name }))}
          placeholder={t('workoutEditor.namePlaceholder')}
          placeholderTextColor={theme.textSecondary}
          style={[styles.input, { borderColor: theme.border, backgroundColor: theme.backgroundElement, color: theme.text }]}
        />

        <ThemedText themeColor="textSecondary" style={styles.blockCount}>
          {t('workoutEditor.blockCount', { count: draft.blocks.length })}
        </ThemedText>

        <ReorderableList
          data={draft.blocks}
          keyExtractor={(block, index) => (block.kind === 'exercise' ? `exercise-${index}` : `circuit-${index}`)}
          onReorder={handleReorder}
          labelsFor={(block, index, total) => {
            const name =
              block.kind === 'exercise'
                ? (findExerciseInLibrary(library, block.exerciseId)?.name ?? block.exerciseId)
                : t('workoutEditor.circuit');
            return {
              handle: t('workoutEditor.reorderAccessibility', { name }),
              position: t('workoutEditor.positionOf', { index: index + 1, total }),
              moveUp: t('workoutEditor.moveUp'),
              moveDown: t('workoutEditor.moveDown'),
            };
          }}
          style={styles.list}
          renderItem={(block, index, dragHandle) => {
            if (block.kind === 'exercise') {
              const exercise = findExerciseInLibrary(library, block.exerciseId);
              if (!exercise) return null;
              const isRest = exercise.type === 'rest';
              const overrideSec = block.configOverride?.durationSec;
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
                  <GestureDetector gesture={dragHandle.gesture}>
                    <View {...dragHandle.a11yProps} style={styles.dragHandleTouchArea}>
                      <ThemedText themeColor="textSecondary" style={styles.dragHandle}>
                        ⣿
                      </ThemedText>
                    </View>
                  </GestureDetector>
                  <View style={styles.rowText}>
                    <ThemedText type={isRest ? 'default' : 'heading'}>{exercise.name}</ThemedText>
                    <ThemedText type="small" themeColor="textSecondary">
                      {summary}
                    </ThemedText>
                  </View>
                  <ExerciseBadge
                    type={exercise.type}
                    overrideLabel={overrideSec ? t('workoutEditor.overrideBadge') : undefined}
                  />
                  <Pressable
                    onPress={() => removeBlock(index)}
                    hitSlop={8}
                    accessibilityRole="button"
                    accessibilityLabel={t('workoutEditor.removeAccessibility', { name: exercise.name })}
                    style={styles.removeButton}>
                    <ThemedText themeColor="textSecondary">✕</ThemedText>
                  </Pressable>
                </View>
              );
            }

            return (
              <View style={[styles.circuitBlock, { borderColor: theme.border, backgroundColor: theme.backgroundElement }]}>
                <View style={styles.circuitHeader}>
                  <GestureDetector gesture={dragHandle.gesture}>
                    <View {...dragHandle.a11yProps} style={styles.dragHandleTouchArea}>
                      <ThemedText themeColor="textSecondary" style={styles.dragHandle}>
                        ⣿
                      </ThemedText>
                    </View>
                  </GestureDetector>
                  <ThemedText type="heading" style={styles.circuitTitle}>
                    {t('workoutEditor.circuit')}
                  </ThemedText>
                  <Pressable
                    onPress={() => removeBlock(index)}
                    hitSlop={8}
                    accessibilityRole="button"
                    accessibilityLabel={t('workoutEditor.removeCircuitAccessibility')}
                    style={styles.removeButton}>
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
                            {exerciseSummary(exercise, unitSystem)}
                          </ThemedText>
                        </View>
                        <ExerciseBadge type={exercise.type} />
                        <Pressable
                          onPress={() => removeCircuitMember(index, memberIndex)}
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
                  onPress={() => toggleIdField(index)}
                  accessibilityRole="button"
                  accessibilityState={{ expanded: openIdFields.has(index) }}
                  style={styles.circuitIdToggle}
                  hitSlop={4}>
                  <ThemedText type="small" themeColor="textSecondary" style={styles.circuitFieldLabel}>
                    {block.id
                      ? t('workoutEditor.blockIdWithValue', { id: block.id })
                      : t('workoutEditor.blockIdPlaceholder')}
                  </ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">
                    {openIdFields.has(index) ? '⌄' : '›'}
                  </ThemedText>
                </Pressable>
                {openIdFields.has(index) && (
                  <View style={styles.circuitIdField}>
                    <ThemedText type="small" themeColor="textSecondary" style={styles.circuitIdHint}>
                      {t('workoutEditor.blockIdHint')}
                    </ThemedText>
                    <TextInput
                      value={block.id ?? ''}
                      onChangeText={(text) => updateCircuit(index, { id: text.trim() || undefined })}
                      placeholder={t('workoutEditor.blockIdInputPlaceholder')}
                      placeholderTextColor={theme.textSecondary}
                      autoCapitalize="none"
                      autoCorrect={false}
                      autoFocus
                      style={[
                        styles.smallInput,
                        { borderColor: theme.border, backgroundColor: theme.background, color: theme.text },
                      ]}
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
                        onPress={() => updateCircuit(index, { rounds: Math.max(1, block.rounds - 1) })}
                        accessibilityRole="button"
                        accessibilityLabel={t('common.decrease', { label: t('workoutEditor.rounds') })}
                        style={[styles.stepperButton, { borderColor: theme.border }]}>
                        <ThemedText themeColor="textSecondary">−</ThemedText>
                      </Pressable>
                      <ThemedText type="smallMedium" style={styles.stepperValue}>
                        {block.rounds}
                      </ThemedText>
                      <Pressable
                        onPress={() => updateCircuit(index, { rounds: block.rounds + 1 })}
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
                      onChangeText={(text) => updateCircuit(index, { restBetweenExercisesSec: Number(text) || 0 })}
                      keyboardType="numeric"
                      style={[
                        styles.smallInput,
                        { borderColor: theme.border, backgroundColor: theme.background, color: theme.text },
                      ]}
                    />
                  </View>

                  <View style={styles.circuitNumberField}>
                    <ThemedText type="small" themeColor="textSecondary" style={styles.circuitFieldLabel}>
                      {t('workoutEditor.restPerRound')}
                    </ThemedText>
                    <TextInput
                      value={String(block.restBetweenRoundsSec ?? 0)}
                      onChangeText={(text) => updateCircuit(index, { restBetweenRoundsSec: Number(text) || 0 })}
                      keyboardType="numeric"
                      style={[
                        styles.smallInput,
                        { borderColor: theme.border, backgroundColor: theme.background, color: theme.text },
                      ]}
                    />
                  </View>
                </View>

                <ThemedText type="small" themeColor="textSecondary" style={styles.circuitSummaryText}>
                  {formatCircuitShape(circuitShape(block))}
                </ThemedText>
              </View>
            );
          }}
        />

        <View style={styles.addButtonsRow}>
          <Pressable
            onPress={() => {
              setPickerOpen((current) => !current);
              setCircuitPickerOpen(false);
              setNewExerciseOpen(false);
            }}
            accessibilityRole="button"
            accessibilityState={{ expanded: pickerOpen }}
            style={({ pressed }) => [
              styles.addBlock,
              styles.addBlockHalf,
              { borderColor: theme.border },
              pressed && styles.pressed,
            ]}>
            <ThemedText type="heading" themeColor="textSecondary">
              {pickerOpen ? t('common.close') : t('workoutEditor.addBlock')}
            </ThemedText>
          </Pressable>
          <Pressable
            onPress={() => {
              setCircuitPickerOpen((current) => !current);
              setPickerOpen(false);
              setCircuitSelection([]);
              setNewExerciseOpen(false);
            }}
            accessibilityRole="button"
            accessibilityState={{ expanded: circuitPickerOpen }}
            style={({ pressed }) => [
              styles.addBlock,
              styles.addBlockHalf,
              { borderColor: theme.border },
              pressed && styles.pressed,
            ]}>
            <ThemedText type="heading" themeColor="textSecondary">
              {circuitPickerOpen ? t('common.close') : t('workoutEditor.newCircuit')}
            </ThemedText>
          </Pressable>
        </View>

        {pickerOpen && (
          <View style={styles.picker}>
            {newExerciseOpen ? (
              <NewExerciseForm onCreate={handleCreateExercise} onCancel={() => setNewExerciseOpen(false)} />
            ) : (
              <>
                <Pressable
                  onPress={() => setNewExerciseOpen(true)}
                  accessibilityRole="button"
                  style={[styles.newExerciseButton, { borderColor: theme.border }]}>
                  <ThemedText type="smallMedium" themeColor="textSecondary">
                    {t('workoutEditor.newExercise')}
                  </ThemedText>
                </Pressable>
                {library.exercises.map((exercise) => (
                  <Pressable key={exercise.id} onPress={() => addBlock(exercise.id)} accessibilityRole="button">
                    <ThemedView type="backgroundElement" style={[styles.pickerRow, { borderColor: theme.border }]}>
                      <ThemedText type="smallMedium" style={styles.pickerRowText}>
                        {exercise.name}
                      </ThemedText>
                      <ExerciseBadge type={exercise.type} />
                    </ThemedView>
                  </Pressable>
                ))}
              </>
            )}
          </View>
        )}

        {circuitPickerOpen && (
          <View style={styles.picker}>
            {newExerciseOpen ? (
              <NewExerciseForm onCreate={handleCreateExercise} onCancel={() => setNewExerciseOpen(false)} />
            ) : (
              <>
                <ThemedText type="small" themeColor="textSecondary">
                  {t('workoutEditor.selectAtLeast2')}
                </ThemedText>
                <Pressable
                  onPress={() => setNewExerciseOpen(true)}
                  accessibilityRole="button"
                  style={[styles.newExerciseButton, { borderColor: theme.border }]}>
                  <ThemedText type="smallMedium" themeColor="textSecondary">
                    {t('workoutEditor.newExercise')}
                  </ThemedText>
                </Pressable>
                {library.exercises.map((exercise) => {
                  const selected = circuitSelection.includes(exercise.id);
                  return (
                    <Pressable
                      key={exercise.id}
                      onPress={() => toggleCircuitMember(exercise.id)}
                      accessibilityRole="checkbox"
                      accessibilityState={{ checked: selected }}>
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
                  accessibilityRole="button"
                  accessibilityState={{ disabled: circuitSelection.length < 2 }}
                  style={[
                    styles.confirmCircuit,
                    { backgroundColor: theme.accent },
                    circuitSelection.length < 2 && styles.disabled,
                  ]}>
                  <ThemedText type="heading" style={{ color: theme.onAccent }}>
                    {t('workoutEditor.addCircuitCount', { count: circuitSelection.length })}
                  </ThemedText>
                </Pressable>
              </>
            )}
          </View>
        )}

        {error && (
          <ThemedText type="small" style={[styles.error, { color: theme.accentText }]}>
            {error}
          </ThemedText>
        )}

        <View style={styles.buttonRow}>
          <Pressable onPress={close} accessibilityRole="button" style={[styles.cancelButton, { borderColor: theme.border }]}>
            <ThemedText type="heading" themeColor="textSecondary">
              {t('common.cancel')}
            </ThemedText>
          </Pressable>
          <Pressable
            onPress={save}
            accessibilityRole="button"
            style={[styles.saveButton, { backgroundColor: theme.accent }]}>
            <ThemedText type="heading" style={{ color: theme.onAccent }}>
              {t('common.save')}
            </ThemedText>
          </Pressable>
        </View>

        {editing && (
          <Pressable onPress={confirmDelete} accessibilityRole="button" style={styles.deleteButton} hitSlop={8}>
            <ThemedText type="smallMedium" themeColor="textSecondary">
              {t('workoutEditor.deleteWorkout')}
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
    paddingBottom: Spacing.four,
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
  dragHandleTouchArea: {
    paddingVertical: 4,
    paddingHorizontal: 2,
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
