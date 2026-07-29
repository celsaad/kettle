import { router, useLocalSearchParams } from 'expo-router';
import { useMemo, useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ModalHeader } from '@/components/modal-header';
import { ThemedText } from '@/components/themed-text';
import { VolumeChart } from '@/components/volume-chart';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import {
  buildExercise,
  CONFIG_FIELDS,
  configToStrings,
  fieldUnitLabel,
  TYPE_OPTIONS,
  validateConfig,
} from '@/domain/exercise-form';
import { slugify } from '@/domain/slug';
import type { ExerciseType } from '@/domain/types';
import { useTheme } from '@/hooks/use-theme';
import { useLibraryStore } from '@/state/library-store';
import { useUnitSystem } from '@/state/preferences-store';
import { exerciseHistory } from '@/state/selectors';
import { useSessionHistoryStore } from '@/state/session-history-store';

export { ModalErrorBoundary as ErrorBoundary } from '@/components/error-fallback';

export default function ExerciseEditorScreen() {
  const theme = useTheme();
  const { t } = useTranslation();
  const { id } = useLocalSearchParams<{ id?: string }>();
  const library = useLibraryStore((state) => state.library);
  const saveExercise = useLibraryStore((state) => state.saveExercise);
  const deleteExercise = useLibraryStore((state) => state.deleteExercise);

  const sessions = useSessionHistoryStore((state) => state.sessions);

  const unitSystem = useUnitSystem();

  const editing = useMemo(() => library?.exercises.find((exercise) => exercise.id === id), [library, id]);
  const recentHistory = useMemo(() => (editing ? exerciseHistory(sessions, editing.id) : []), [sessions, editing]);

  const [name, setName] = useState(editing?.name ?? '');
  const [type, setType] = useState<ExerciseType>(editing?.type ?? 'reps');
  const [values, setValues] = useState<Record<string, string>>(editing ? configToStrings(editing, unitSystem) : {});
  const [notes, setNotes] = useState(editing?.notes ?? '');
  const [error, setError] = useState<string | null>(null);

  const close = () => router.back();

  const setField = (key: string, text: string) => setValues((prev) => ({ ...prev, [key]: text }));

  const save = async () => {
    if (!name.trim()) {
      setError(t('common.nameRequired'));
      return;
    }
    const exerciseId = editing?.id ?? slugify(name);
    if (!exerciseId) {
      setError(t('common.couldNotDeriveId'));
      return;
    }
    const configError = validateConfig(type, values);
    if (configError) {
      setError(configError);
      return;
    }
    const exercise = buildExercise(exerciseId, name.trim(), type, values, notes, {
      unitSystem,
      previousWeightKg: editing?.type === 'reps' ? editing.config.targetWeightKg : undefined,
    });
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
        t('exerciseEditor.inUseTitle'),
        t('exerciseEditor.inUseBody', { name: editing.name, workouts: usedBy.map((workout) => workout.name).join(', ') }),
      );
      return;
    }
    Alert.alert(t('exerciseEditor.deleteConfirmTitle'), t('common.deleteConfirmBody', { name: editing.name }), [
      { text: t('common.cancel'), style: 'cancel' },
      {
        text: t('common.delete'),
        style: 'destructive',
        onPress: async () => {
          await deleteExercise(editing.id);
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
        <ThemedText type="subtitle">{editing ? t('exerciseEditor.editTitle') : t('exerciseEditor.newTitle')}</ThemedText>

        <ThemedText type="small" themeColor="textSecondary" style={styles.label}>
          {t('common.name')}
        </ThemedText>
        <TextInput
          value={name}
          onChangeText={setName}
          placeholder={t('exerciseEditor.namePlaceholder')}
          placeholderTextColor={theme.textSecondary}
          style={[styles.input, { borderColor: theme.border, backgroundColor: theme.backgroundElement, color: theme.text }]}
        />

        <ThemedText type="small" themeColor="textSecondary" style={styles.label}>
          {t('exerciseEditor.type')}
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
                  {t(option.label)}
                </ThemedText>
              </Pressable>
            );
          })}
        </View>

        <View style={styles.configGrid}>
          {CONFIG_FIELDS[type].map((field) => {
            const unit = fieldUnitLabel(field, unitSystem);
            return (
              <View key={field.key} style={styles.configField}>
                <ThemedText type="small" themeColor="textSecondary" style={styles.label}>
                  {t(field.label)} {unit ? `(${unit})` : ''}
                  {field.optional ? ` · ${t('exerciseEditor.optional')}` : ''}
                </ThemedText>
                <TextInput
                  value={values[field.key] ?? ''}
                  onChangeText={(text) => setField(field.key, text)}
                  keyboardType="numeric"
                  placeholder="0"
                  placeholderTextColor={theme.textSecondary}
                  style={[
                    styles.input,
                    { borderColor: theme.border, backgroundColor: theme.backgroundElement, color: theme.text },
                  ]}
                />
              </View>
            );
          })}
        </View>

        <ThemedText type="small" themeColor="textSecondary" style={styles.label}>
          {t('exerciseEditor.notesOptional')}
        </ThemedText>
        <TextInput
          value={notes}
          onChangeText={setNotes}
          placeholder={t('exerciseEditor.notesPlaceholder')}
          placeholderTextColor={theme.textSecondary}
          multiline
          style={[
            styles.input,
            styles.notesInput,
            { borderColor: theme.border, backgroundColor: theme.backgroundElement, color: theme.text },
          ]}
        />

        {recentHistory.length > 0 && (
          <View style={styles.historySection}>
            <ThemedText type="small" themeColor="textSecondary" style={styles.label}>
              {t('exerciseEditor.recent')}
            </ThemedText>
            <VolumeChart
              // Reverses a copy — the spread is the copy oxlint can't see through (decision log: no `toReversed`).
              // oxlint-disable-next-line unicorn/no-array-reverse
              data={[...recentHistory].reverse().map((entry) => ({ label: entry.dateLabel, value: entry.volume }))}
            />
            <View style={styles.historyList}>
              {recentHistory.map((entry) => (
                <View key={entry.sessionId} style={styles.historyRow}>
                  <ThemedText type="small" themeColor="textSecondary">
                    {entry.dateLabel}
                  </ThemedText>
                  <ThemedText type="smallMedium">{entry.summary}</ThemedText>
                </View>
              ))}
            </View>
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
              {t('common.cancel')}
            </ThemedText>
          </Pressable>
          <Pressable onPress={save} style={[styles.saveButton, { backgroundColor: theme.accent }]}>
            <ThemedText type="heading" style={{ color: theme.onAccent }}>
              {t('common.save')}
            </ThemedText>
          </Pressable>
        </View>

        {editing && (
          <Pressable onPress={confirmDelete} style={styles.deleteButton} hitSlop={8}>
            <ThemedText type="smallMedium" themeColor="textSecondary">
              {t('exerciseEditor.deleteExercise')}
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
  historySection: {
    marginTop: Spacing.three - 2,
  },
  historyList: {
    marginTop: Spacing.one,
    gap: Spacing.one + 2,
  },
  historyRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
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
