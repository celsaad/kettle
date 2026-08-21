import { router, useLocalSearchParams } from 'expo-router';
import { useCallback, useMemo, useRef, useState } from 'react';
import { Alert, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useTranslation } from 'react-i18next';
// The gesture-handler `ScrollView`, deliberately, not `react-native`'s: it is the only one RNGH can
// make defer to the block-drag, and without that Android's scroller eats the long-press. See
// `ScrollableRef` in `reorderable-list.tsx`.
import { ScrollView } from 'react-native-gesture-handler';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ExercisePicker } from '@/components/exercise-picker';
import { ModalHeader } from '@/components/modal-header';
import { ReorderableList } from '@/components/reorderable-list';
import { ThemedText } from '@/components/themed-text';
import { WorkoutBlockRow } from '@/components/workout-block-row';
import { WorkoutCircuitBlock } from '@/components/workout-circuit-block';
import { Spacing, MaxContentWidth } from '@/constants/theme';
import { slugify } from '@/domain/slug';
import type { Exercise, Workout, WorkoutBlock } from '@/domain/types';
import { useTheme } from '@/hooks/use-theme';
import { findExerciseInLibrary, useLibraryStore } from '@/state/library-store';

export { ModalErrorBoundary as ErrorBoundary } from '@/components/error-fallback';

const EMPTY_WORKOUT: Workout = { id: '', name: '', blocks: [] };

export default function WorkoutEditorScreen() {
  const theme = useTheme();
  const { t } = useTranslation();
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
  const [error, setError] = useState<string | null>(null);

  if (!library) return null;

  const removeBlock = (index: number) => {
    setDraft((current) => ({ ...current, blocks: current.blocks.filter((_, i) => i !== index) }));
  };

  const handleReorder = useCallback((blocks: WorkoutBlock[]) => {
    setDraft((current) => ({ ...current, blocks }));
  }, []);

  // Handed to `ReorderableList` so a block-drag outranks the scroll it lives inside.
  const scrollRef = useRef(null);

  const addBlock = (exerciseId: string) => {
    setDraft((current) => ({ ...current, blocks: [...current.blocks, { kind: 'exercise', exerciseId }] }));
    setPickerOpen(false);
  };

  const toggleCircuitMember = (exerciseId: string) => {
    setCircuitSelection((current) =>
      current.includes(exerciseId) ? current.filter((existingId) => existingId !== exerciseId) : [...current, exerciseId],
    );
  };

  // Persists a quick-added exercise to the library, then drops it into whichever picker asked for it:
  // a plain block (addBlock also closes that picker) or a circuit-in-progress member selection (kept
  // open — building a circuit means picking several exercises, not just one). Reports back whether it
  // saved, since a picker keeps its form open on failure so the entered values aren't lost.
  const createExercise = async (exercise: Exercise, add: (exerciseId: string) => void): Promise<boolean> => {
    try {
      await saveExercise(exercise);
    } catch (err) {
      setError(t('common.saveFailed', { detail: (err as Error).message }));
      return false;
    }
    add(exercise.id);
    return true;
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

  /**
   * Removing the second-to-last member would leave a one-member circuit, which the schema refuses
   * (`exercises` is `.min(2)`) — so the editor wrote a library that failed to parse on the next
   * launch. A circuit of one is also not a circuit; the way to get there is to delete the block.
   */
  const removeCircuitMember = (index: number, memberIndex: number) => {
    setDraft((current) => ({
      ...current,
      blocks: current.blocks.map((block, i) =>
        i === index && block.kind === 'circuit' && block.members.length > 2
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
    const usedBy = (library.programs ?? []).filter((program) =>
      program.weeks.some((week) => !week.restDay && week.workoutId === editing.id),
    );
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
      <ScrollView ref={scrollRef} contentContainerStyle={styles.scrollContent}>
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
          scrollRef={scrollRef}
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
            if (block.kind === 'circuit') {
              return (
                <WorkoutCircuitBlock
                  block={block}
                  exercises={library.exercises}
                  dragHandle={dragHandle}
                  onChange={(patch) => updateCircuit(index, patch)}
                  onRemove={() => removeBlock(index)}
                  onRemoveMember={(memberIndex) => removeCircuitMember(index, memberIndex)}
                />
              );
            }

            const exercise = findExerciseInLibrary(library, block.exerciseId);
            if (!exercise) return null;
            return (
              <WorkoutBlockRow
                exercise={exercise}
                overrideSec={block.configOverride?.durationSec}
                dragHandle={dragHandle}
                onRemove={() => removeBlock(index)}
              />
            );
          }}
        />

        <View style={styles.addButtonsRow}>
          <Pressable
            onPress={() => {
              setPickerOpen((current) => !current);
              setCircuitPickerOpen(false);
            }}
            accessibilityRole="button"
            accessibilityState={{ expanded: pickerOpen }}
            style={({ pressed }) => [
              styles.addBlock,
              styles.addBlockHalf,
              { borderColor: theme.border },
              pressed && styles.pressed,
            ]}>
            <ThemedText type="heading" themeColor="textSecondary" style={styles.addBlockLabel}>
              {pickerOpen ? t('common.close') : t('workoutEditor.addBlock')}
            </ThemedText>
          </Pressable>
          <Pressable
            onPress={() => {
              setCircuitPickerOpen((current) => !current);
              setPickerOpen(false);
              setCircuitSelection([]);
            }}
            accessibilityRole="button"
            accessibilityState={{ expanded: circuitPickerOpen }}
            style={({ pressed }) => [
              styles.addBlock,
              styles.addBlockHalf,
              { borderColor: theme.border },
              pressed && styles.pressed,
            ]}>
            <ThemedText type="heading" themeColor="textSecondary" style={styles.addBlockLabel}>
              {circuitPickerOpen ? t('common.close') : t('workoutEditor.newCircuit')}
            </ThemedText>
          </Pressable>
        </View>

        {pickerOpen && (
          <ExercisePicker
            mode="single"
            exercises={library.exercises}
            onPick={addBlock}
            onCreate={(exercise) => createExercise(exercise, addBlock)}
          />
        )}

        {circuitPickerOpen && (
          <ExercisePicker
            mode="multi"
            exercises={library.exercises}
            selected={circuitSelection}
            onToggle={toggleCircuitMember}
            onConfirm={addCircuit}
            onCreate={(exercise) => createExercise(exercise, toggleCircuitMember)}
          />
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
  pressed: {
    opacity: 0.7,
  },
  label: {
    marginTop: Spacing.three - 2,
    marginBottom: Spacing.one,
  },
  input: {
    minHeight: 46,
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
  addButtonsRow: {
    flexDirection: 'row',
    gap: Spacing.two - 2,
    marginTop: Spacing.three,
  },
  addBlock: {
    // `minHeight`, never `height`: at half width this label wraps in any language wordier than
    // English — `+ Adicionar bloco` took two lines and a fixed 48 clipped the second one on device.
    // The row has no `alignItems`, so the sibling stretches to match rather than the two disagreeing.
    minHeight: 48,
    paddingVertical: Spacing.two,
    paddingHorizontal: Spacing.two,
    borderRadius: 14,
    borderWidth: 1.5,
    borderStyle: 'dashed',
    alignItems: 'center',
    justifyContent: 'center',
  },
  // Only matters once the label wraps: without it the second line sits left while the block is centred.
  addBlockLabel: {
    textAlign: 'center',
  },
  addBlockHalf: {
    flex: 1,
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
    minHeight: 52,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1.5,
  },
  saveButton: {
    flex: 1.4,
    minHeight: 52,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
  },
  deleteButton: {
    alignItems: 'center',
    marginTop: Spacing.three - 2,
  },
});
