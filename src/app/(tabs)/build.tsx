import { useState } from 'react';
import { Platform, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ExerciseBadge, exerciseSummary } from '@/components/exercise-badge';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing, MaxContentWidth } from '@/constants/theme';
import type { Workout } from '@/domain/types';
import { useTheme } from '@/hooks/use-theme';
import { findExerciseInLibrary, useLibraryStore } from '@/state/library-store';

const EMPTY_WORKOUT: Workout = { id: '', name: '', blocks: [] };

export default function BuildScreen() {
  const theme = useTheme();
  const library = useLibraryStore((state) => state.library);
  const saveWorkout = useLibraryStore((state) => state.saveWorkout);
  const workout = library?.workouts[0];

  const [draft, setDraft] = useState<Workout>(() => workout ?? EMPTY_WORKOUT);
  const [renaming, setRenaming] = useState(false);
  const [pickerOpen, setPickerOpen] = useState(false);

  if (!workout || !library) return null;

  const dirty = draft !== workout;

  const removeBlock = (index: number) => {
    setDraft((current) => ({ ...current, blocks: current.blocks.filter((_, i) => i !== index) }));
  };

  const addBlock = (exerciseId: string) => {
    setDraft((current) => ({ ...current, blocks: [...current.blocks, { exerciseId }] }));
    setPickerOpen(false);
  };

  const cancel = () => {
    setDraft(workout);
    setRenaming(false);
    setPickerOpen(false);
  };

  const save = () => {
    saveWorkout(draft);
    setRenaming(false);
  };

  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: theme.background }]} edges={['top', 'left', 'right']}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.header}>
          <Pressable onPress={cancel} disabled={!dirty}>
            <ThemedText themeColor="textSecondary" style={!dirty && styles.disabled}>
              ‹ Cancel
            </ThemedText>
          </Pressable>
          <Pressable onPress={save} disabled={!dirty}>
            <ThemedText type="heading" themeColor="accentText" style={!dirty && styles.disabled}>
              Save
            </ThemedText>
          </Pressable>
        </View>

        <View style={styles.titleRow}>
          {renaming ? (
            <TextInput
              value={draft.name}
              onChangeText={(name) => setDraft((current) => ({ ...current, name }))}
              onSubmitEditing={() => setRenaming(false)}
              autoFocus
              style={[styles.nameInput, { color: theme.text, borderColor: theme.border }]}
            />
          ) : (
            <ThemedText type="subtitle">{draft.name}</ThemedText>
          )}
          <Pressable onPress={() => setRenaming((current) => !current)} hitSlop={8}>
            <ThemedText themeColor="textSecondary">✎</ThemedText>
          </Pressable>
        </View>
        <ThemedText themeColor="textSecondary" style={styles.blockCount}>
          {draft.blocks.length} blocks
        </ThemedText>

        <View style={styles.list}>
          {draft.blocks.map((block, index) => {
            const exercise = findExerciseInLibrary(library, block.exerciseId);
            if (!exercise) return null;
            const isRest = exercise.type === 'rest';
            const overrideSec = block.configOverride?.durationSec;
            const summary = isRest && overrideSec ? `${overrideSec} seconds` : exerciseSummary(exercise);

            return (
              <View
                key={`${block.exerciseId}-${index}`}
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
          })}
        </View>

        <Pressable
          onPress={() => setPickerOpen((current) => !current)}
          style={({ pressed }) => [styles.addBlock, { borderColor: theme.border }, pressed && styles.pressed]}>
          <ThemedText type="heading" themeColor="textSecondary">
            {pickerOpen ? 'Close' : '+ Add block'}
          </ThemedText>
        </Pressable>

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
    paddingTop: Platform.select({ web: Spacing.six, default: Spacing.two }),
    paddingBottom: Spacing.six,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  disabled: {
    opacity: 0.4,
  },
  pressed: {
    opacity: 0.7,
  },
  titleRow: {
    marginTop: Spacing.three - 2,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  nameInput: {
    flex: 1,
    fontSize: 20,
    fontWeight: '600',
    borderBottomWidth: 1,
    paddingVertical: 2,
  },
  blockCount: {
    marginTop: 4,
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
  addBlock: {
    marginTop: Spacing.three,
    height: 48,
    borderRadius: 14,
    borderWidth: 1.5,
    borderStyle: 'dashed',
    alignItems: 'center',
    justifyContent: 'center',
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
});
