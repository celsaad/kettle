import { router, useLocalSearchParams } from 'expo-router';
import { useMemo, useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { overrideLines } from '@/app/program-detail';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import type { Program, ProgramWeek } from '@/domain/types';
import { useTheme } from '@/hooks/use-theme';
import { useLibraryStore } from '@/state/library-store';

const EMPTY_PROGRAM: Program = { id: '', name: '', weeks: [] };

function slugify(name: string): string {
  return name
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-+|-+$)/g, '');
}

function weekKey(week: ProgramWeek): string {
  return `${week.week}::${week.day ?? ''}`;
}

export default function ProgramEditorScreen() {
  const theme = useTheme();
  const { id } = useLocalSearchParams<{ id?: string }>();
  const library = useLibraryStore((state) => state.library);
  const saveProgram = useLibraryStore((state) => state.saveProgram);
  const deleteProgram = useLibraryStore((state) => state.deleteProgram);

  const editing = useMemo(() => library?.programs.find((candidate) => candidate.id === id), [library, id]);

  const [draft, setDraft] = useState<Program>(() => editing ?? EMPTY_PROGRAM);
  const [openWorkoutPicker, setOpenWorkoutPicker] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  if (!library) return null;

  const updateWeek = (index: number, patch: Partial<ProgramWeek>) => {
    setDraft((current) => ({ ...current, weeks: current.weeks.map((week, i) => (i === index ? { ...week, ...patch } : week)) }));
  };

  const removeWeek = (index: number) => {
    setDraft((current) => ({ ...current, weeks: current.weeks.filter((_, i) => i !== index) }));
    setOpenWorkoutPicker(null);
  };

  const addWeek = () => {
    const maxWeek = draft.weeks.reduce((max, week) => Math.max(max, week.week), 0);
    const defaultWorkoutId = library.workouts[0]?.id ?? '';
    setDraft((current) => ({ ...current, weeks: [...current.weeks, { week: maxWeek + 1, workoutId: defaultWorkoutId }] }));
  };

  const selectWorkout = (index: number, workoutId: string) => {
    updateWeek(index, { workoutId });
    setOpenWorkoutPicker(null);
  };

  const close = () => router.back();

  const save = async () => {
    if (!draft.name.trim()) {
      setError('Name is required.');
      return;
    }
    if (draft.weeks.length === 0) {
      setError('Add at least one week.');
      return;
    }
    if (draft.weeks.some((week) => !week.workoutId)) {
      setError('Every week needs a workout.');
      return;
    }
    const seen = new Set<string>();
    for (const week of draft.weeks) {
      const key = weekKey(week);
      if (seen.has(key)) {
        setError(`Week ${week.week}${week.day ? ` (${week.day})` : ''} is used twice — give one a different day, or remove the duplicate.`);
        return;
      }
      seen.add(key);
    }
    const programId = editing?.id || slugify(draft.name);
    if (!programId) {
      setError('Could not derive an id from that name.');
      return;
    }
    await saveProgram({ ...draft, id: programId, name: draft.name.trim() });
    close();
  };

  const confirmDelete = () => {
    if (!editing) return;
    Alert.alert('Delete program?', `"${editing.name}" will be permanently removed.`, [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: async () => {
          await deleteProgram(editing.id);
          close();
        },
      },
    ]);
  };

  const noWorkouts = library.workouts.length === 0;

  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: theme.background }]} edges={['bottom', 'left', 'right']}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={[styles.grabber, { backgroundColor: theme.border }]} />
        <ThemedText type="subtitle">{editing ? 'Edit program' : 'New program'}</ThemedText>

        <ThemedText type="small" themeColor="textSecondary" style={styles.label}>
          Name
        </ThemedText>
        <TextInput
          value={draft.name}
          onChangeText={(name) => setDraft((current) => ({ ...current, name }))}
          placeholder="e.g. 6-Week Pull Progression"
          placeholderTextColor={theme.textSecondary}
          style={[styles.input, { borderColor: theme.border, backgroundColor: theme.backgroundElement, color: theme.text }]}
        />

        <ThemedText themeColor="textSecondary" style={styles.weekCount}>
          {draft.weeks.length} week{draft.weeks.length === 1 ? '' : 's'}
        </ThemedText>

        {noWorkouts && (
          <ThemedText type="small" themeColor="textSecondary" style={styles.noWorkoutsHint}>
            No workouts yet — create one in Build first, then come back to add weeks here.
          </ThemedText>
        )}

        <View style={styles.list}>
          {draft.weeks.map((week, index) => {
            const workout = library.workouts.find((candidate) => candidate.id === week.workoutId);
            const existingOverrideLines = (week.overrides ?? []).flatMap((override) => overrideLines(override, library, workout));

            return (
              <View key={index} style={[styles.weekCard, { borderColor: theme.border, backgroundColor: theme.backgroundElement }]}>
                <View style={styles.weekHeader}>
                  <ThemedText type="small" themeColor="textSecondary" style={styles.weekFieldLabel}>
                    Week
                  </ThemedText>
                  <View style={styles.stepperRow}>
                    <Pressable
                      onPress={() => updateWeek(index, { week: Math.max(1, week.week - 1) })}
                      style={[styles.stepperButton, { borderColor: theme.border }]}>
                      <ThemedText themeColor="textSecondary">−</ThemedText>
                    </Pressable>
                    <ThemedText type="smallMedium" style={styles.stepperValue}>
                      {week.week}
                    </ThemedText>
                    <Pressable
                      onPress={() => updateWeek(index, { week: week.week + 1 })}
                      style={[styles.stepperButton, { borderColor: theme.border }]}>
                      <ThemedText themeColor="textSecondary">+</ThemedText>
                    </Pressable>
                  </View>
                  <Pressable onPress={() => removeWeek(index)} hitSlop={8} style={styles.removeButton}>
                    <ThemedText themeColor="textSecondary">✕</ThemedText>
                  </Pressable>
                </View>

                <ThemedText type="small" themeColor="textSecondary" style={styles.weekFieldLabel}>
                  Day · optional
                </ThemedText>
                <TextInput
                  value={week.day ?? ''}
                  onChangeText={(text) => updateWeek(index, { day: text.trim() || undefined })}
                  placeholder="e.g. Monday — only needed for 2+ sessions in one week"
                  placeholderTextColor={theme.textSecondary}
                  style={[styles.smallInput, { borderColor: theme.border, backgroundColor: theme.background, color: theme.text }]}
                />

                <ThemedText type="small" themeColor="textSecondary" style={styles.weekFieldLabel}>
                  Workout
                </ThemedText>
                <Pressable
                  onPress={() => setOpenWorkoutPicker((current) => (current === index ? null : index))}
                  style={[styles.workoutPickerButton, { borderColor: theme.border }]}>
                  <ThemedText type="smallMedium">{workout?.name ?? 'Select a workout'}</ThemedText>
                  <ThemedText themeColor="textSecondary">{openWorkoutPicker === index ? '⌄' : '›'}</ThemedText>
                </Pressable>
                {openWorkoutPicker === index && (
                  <View style={styles.picker}>
                    {library.workouts.map((candidate) => (
                      <Pressable key={candidate.id} onPress={() => selectWorkout(index, candidate.id)}>
                        <ThemedView
                          type="backgroundElement"
                          style={[
                            styles.pickerRow,
                            { borderColor: candidate.id === week.workoutId ? theme.accent : theme.border },
                          ]}>
                          <ThemedText type="smallMedium">{candidate.name}</ThemedText>
                        </ThemedView>
                      </Pressable>
                    ))}
                  </View>
                )}

                <ThemedText type="small" themeColor="textSecondary" style={styles.weekFieldLabel}>
                  Notes · optional
                </ThemedText>
                <TextInput
                  value={week.notes ?? ''}
                  onChangeText={(text) => updateWeek(index, { notes: text.trim() || undefined })}
                  placeholder="Baseline — see where you land."
                  placeholderTextColor={theme.textSecondary}
                  style={[styles.smallInput, { borderColor: theme.border, backgroundColor: theme.background, color: theme.text }]}
                />

                {existingOverrideLines.length > 0 && (
                  <View style={styles.overrides}>
                    <ThemedText type="small" themeColor="textSecondary" style={styles.weekFieldLabel}>
                      Overrides (not editable here yet)
                    </ThemedText>
                    {existingOverrideLines.map((line, lineIndex) => (
                      <ThemedText key={lineIndex} type="small" themeColor="textSecondary">
                        {line}
                      </ThemedText>
                    ))}
                  </View>
                )}
              </View>
            );
          })}
        </View>

        <Pressable
          onPress={addWeek}
          disabled={noWorkouts}
          style={[styles.addWeek, { borderColor: theme.border }, noWorkouts && styles.disabled]}>
          <ThemedText type="heading" themeColor="textSecondary">
            + Add week
          </ThemedText>
        </Pressable>

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
              Delete program
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
  weekCount: {
    marginTop: Spacing.two,
  },
  noWorkoutsHint: {
    marginTop: Spacing.one,
  },
  list: {
    marginTop: Spacing.three,
    gap: Spacing.two + 2,
  },
  weekCard: {
    borderRadius: 16,
    borderWidth: 1,
    padding: Spacing.two + 6,
    gap: 2,
  },
  weekHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    marginBottom: Spacing.one,
  },
  weekFieldLabel: {
    marginTop: Spacing.two - 2,
    marginBottom: Spacing.half + 1,
  },
  stepperRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.one,
    flex: 1,
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
    minWidth: 24,
    textAlign: 'center',
  },
  removeButton: {
    paddingLeft: Spacing.one,
  },
  smallInput: {
    height: 40,
    borderRadius: 10,
    borderWidth: 1,
    paddingHorizontal: Spacing.one + 4,
    fontSize: 14,
  },
  workoutPickerButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    height: 44,
    borderRadius: 10,
    borderWidth: 1,
    paddingHorizontal: Spacing.two,
  },
  picker: {
    marginTop: Spacing.one,
    gap: Spacing.one,
  },
  pickerRow: {
    borderRadius: 10,
    borderWidth: 1,
    padding: Spacing.two - 2,
  },
  overrides: {
    marginTop: Spacing.one,
    gap: 2,
  },
  addWeek: {
    height: 48,
    borderRadius: 14,
    borderWidth: 1.5,
    borderStyle: 'dashed',
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: Spacing.three,
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
