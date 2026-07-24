import { router, useLocalSearchParams } from 'expo-router';
import { useMemo, useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import type { Exercise, ExerciseType } from '@/domain/types';
import { useTheme } from '@/hooks/use-theme';
import { useLibraryStore } from '@/state/library-store';

export type FieldDef = { key: string; label: string; unit?: string; optional?: boolean };

const TYPE_OPTIONS: { type: ExerciseType; label: string }[] = [
  { type: 'reps', label: 'Reps' },
  { type: 'timed_hold', label: 'Hold' },
  { type: 'hiit', label: 'HIIT' },
  { type: 'emom', label: 'EMOM' },
  { type: 'amrap', label: 'AMRAP' },
  { type: 'cardio', label: 'Cardio' },
  { type: 'rest', label: 'Rest' },
];

export const CONFIG_FIELDS: Record<ExerciseType, FieldDef[]> = {
  hiit: [
    { key: 'workSec', label: 'Work', unit: 'sec' },
    { key: 'restSec', label: 'Rest', unit: 'sec' },
    { key: 'rounds', label: 'Rounds' },
  ],
  emom: [
    { key: 'intervalSec', label: 'Interval', unit: 'sec' },
    { key: 'totalMinutes', label: 'Total', unit: 'min' },
    { key: 'targetReps', label: 'Target reps', optional: true },
  ],
  amrap: [{ key: 'timeCapSec', label: 'Time cap', unit: 'sec' }],
  reps: [
    { key: 'sets', label: 'Sets' },
    { key: 'targetRepsMin', label: 'Target reps' },
    { key: 'targetRepsMax', label: 'Target reps (max)', optional: true },
    { key: 'targetWeightKg', label: 'Weight', unit: 'kg', optional: true },
    { key: 'restSec', label: 'Rest', unit: 'sec' },
  ],
  timed_hold: [
    { key: 'sets', label: 'Sets' },
    { key: 'holdSecMin', label: 'Hold', unit: 'sec' },
    { key: 'holdSecMax', label: 'Hold (max)', unit: 'sec', optional: true },
    { key: 'restSec', label: 'Rest', unit: 'sec' },
  ],
  cardio: [
    { key: 'durationSec', label: 'Duration', unit: 'sec', optional: true },
    { key: 'distanceMeters', label: 'Distance', unit: 'm', optional: true },
  ],
  rest: [{ key: 'durationSec', label: 'Duration', unit: 'sec' }],
};

function slugify(name: string): string {
  return name
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-+|-+$)/g, '');
}

export function configToStrings(exercise: Exercise): Record<string, string> {
  const values: Record<string, string> = {};
  for (const [key, value] of Object.entries(exercise.config)) {
    if (value !== undefined) values[key] = String(value);
  }
  return values;
}

export function buildExercise(
  id: string,
  name: string,
  type: ExerciseType,
  values: Record<string, string>,
  notes: string,
): Exercise {
  const num = (key: string) => Number(values[key] ?? 0) || 0;
  const optionalNum = (key: string) => (values[key]?.trim() ? Number(values[key]) : undefined);
  const trimmedNotes = notes.trim() || undefined;

  switch (type) {
    case 'hiit':
      return {
        id,
        name,
        type,
        config: { workSec: num('workSec'), restSec: num('restSec'), rounds: num('rounds') },
        notes: trimmedNotes,
      };
    case 'emom':
      return {
        id,
        name,
        type,
        config: { intervalSec: num('intervalSec'), totalMinutes: num('totalMinutes'), targetReps: optionalNum('targetReps') },
        notes: trimmedNotes,
      };
    case 'amrap':
      return { id, name, type, config: { timeCapSec: num('timeCapSec') }, notes: trimmedNotes };
    case 'reps':
      return {
        id,
        name,
        type,
        config: {
          sets: num('sets'),
          targetRepsMin: num('targetRepsMin'),
          targetRepsMax: optionalNum('targetRepsMax'),
          targetWeightKg: optionalNum('targetWeightKg'),
          restSec: num('restSec'),
        },
        notes: trimmedNotes,
      };
    case 'timed_hold':
      return {
        id,
        name,
        type,
        config: { sets: num('sets'), holdSecMin: num('holdSecMin'), holdSecMax: optionalNum('holdSecMax'), restSec: num('restSec') },
        notes: trimmedNotes,
      };
    case 'cardio':
      return {
        id,
        name,
        type,
        config: { durationSec: optionalNum('durationSec'), distanceMeters: optionalNum('distanceMeters') },
        notes: trimmedNotes,
      };
    case 'rest':
      return { id, name, type, config: { durationSec: num('durationSec') }, notes: trimmedNotes };
  }
}

export default function ExerciseEditorScreen() {
  const theme = useTheme();
  const { id } = useLocalSearchParams<{ id?: string }>();
  const library = useLibraryStore((state) => state.library);
  const saveExercise = useLibraryStore((state) => state.saveExercise);
  const deleteExercise = useLibraryStore((state) => state.deleteExercise);

  const editing = useMemo(() => library?.exercises.find((exercise) => exercise.id === id), [library, id]);

  const [name, setName] = useState(editing?.name ?? '');
  const [type, setType] = useState<ExerciseType>(editing?.type ?? 'reps');
  const [values, setValues] = useState<Record<string, string>>(editing ? configToStrings(editing) : {});
  const [notes, setNotes] = useState(editing?.notes ?? '');
  const [error, setError] = useState<string | null>(null);

  const close = () => router.back();

  const setField = (key: string, text: string) => setValues((prev) => ({ ...prev, [key]: text }));

  const save = async () => {
    if (!name.trim()) {
      setError('Name is required.');
      return;
    }
    const exerciseId = editing?.id ?? slugify(name);
    if (!exerciseId) {
      setError('Could not derive an id from that name.');
      return;
    }
    const exercise = buildExercise(exerciseId, name.trim(), type, values, notes);
    await saveExercise(exercise);
    close();
  };

  const confirmDelete = () => {
    if (!editing || !library) return;
    const usedBy = library.workouts.filter((workout) =>
      workout.blocks.some((block) =>
        block.kind === 'exercise'
          ? block.exerciseId === editing.id
          : block.members.some((member) => member.exerciseId === editing.id),
      ),
    );
    if (usedBy.length > 0) {
      Alert.alert(
        'Exercise in use',
        `"${editing.name}" is used by ${usedBy.map((workout) => workout.name).join(', ')}. Remove it from those workouts first.`,
      );
      return;
    }
    Alert.alert('Delete exercise?', `"${editing.name}" will be permanently removed.`, [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: async () => {
          await deleteExercise(editing.id);
          close();
        },
      },
    ]);
  };

  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: theme.background }]} edges={['bottom', 'left', 'right']}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={[styles.grabber, { backgroundColor: theme.border }]} />
        <ThemedText type="subtitle">{editing ? 'Edit exercise' : 'New exercise'}</ThemedText>

        <ThemedText type="small" themeColor="textSecondary" style={styles.label}>
          Name
        </ThemedText>
        <TextInput
          value={name}
          onChangeText={setName}
          placeholder="e.g. Front Lever"
          placeholderTextColor={theme.textSecondary}
          style={[styles.input, { borderColor: theme.border, backgroundColor: theme.backgroundElement, color: theme.text }]}
        />

        <ThemedText type="small" themeColor="textSecondary" style={styles.label}>
          Type
        </ThemedText>
        <View style={styles.typeRow}>
          {TYPE_OPTIONS.map((option) => {
            const active = option.type === type;
            return (
              <Pressable
                key={option.type}
                onPress={() => setType(option.type)}
                disabled={!!editing}
                style={[
                  styles.typePill,
                  active
                    ? { backgroundColor: theme.text }
                    : { backgroundColor: theme.backgroundElement, borderWidth: 1, borderColor: theme.border },
                  !!editing && styles.pillDisabled,
                ]}>
                <ThemedText type="small" style={{ color: active ? theme.onAccent : theme.textSecondary }}>
                  {option.label}
                </ThemedText>
              </Pressable>
            );
          })}
        </View>

        <View style={styles.configGrid}>
          {CONFIG_FIELDS[type].map((field) => (
            <View key={field.key} style={styles.configField}>
              <ThemedText type="small" themeColor="textSecondary" style={styles.label}>
                {field.label} {field.unit ? `(${field.unit})` : ''}
                {field.optional ? ' · optional' : ''}
              </ThemedText>
              <TextInput
                value={values[field.key] ?? ''}
                onChangeText={(text) => setField(field.key, text)}
                keyboardType="numeric"
                placeholder="0"
                placeholderTextColor={theme.textSecondary}
                style={[styles.input, { borderColor: theme.border, backgroundColor: theme.backgroundElement, color: theme.text }]}
              />
            </View>
          ))}
        </View>

        <ThemedText type="small" themeColor="textSecondary" style={styles.label}>
          Notes · optional
        </ThemedText>
        <TextInput
          value={notes}
          onChangeText={setNotes}
          placeholder="Coaching cue…"
          placeholderTextColor={theme.textSecondary}
          multiline
          style={[styles.input, styles.notesInput, { borderColor: theme.border, backgroundColor: theme.backgroundElement, color: theme.text }]}
        />

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
              Delete exercise
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
  typeRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two - 2,
  },
  typePill: {
    paddingHorizontal: Spacing.three - 3,
    paddingVertical: 7,
    borderRadius: 999,
  },
  pillDisabled: {
    opacity: 0.5,
  },
  configGrid: {
    marginTop: Spacing.two,
  },
  configField: {},
  notesInput: {
    height: 90,
    paddingTop: Spacing.one + 4,
    textAlignVertical: 'top',
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
