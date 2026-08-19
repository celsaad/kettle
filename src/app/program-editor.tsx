import { router, useLocalSearchParams } from 'expo-router';
import { useMemo, useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ModalHeader } from '@/components/modal-header';
import { ProgramOverrideEditor } from '@/components/program-override-editor';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { programWeekNumbers } from '@/domain/program';
import { slugify } from '@/domain/slug';
import type { Program, ProgramOverride, ProgramWeek } from '@/domain/types';
import { useTheme } from '@/hooks/use-theme';
import { useLibraryStore } from '@/state/library-store';

export { ModalErrorBoundary as ErrorBoundary } from '@/components/error-fallback';

const EMPTY_PROGRAM: Program = { id: '', name: '', weeks: [] };

function weekKey(week: ProgramWeek): string {
  return `${week.week}::${week.day ?? ''}`;
}

export default function ProgramEditorScreen() {
  const theme = useTheme();
  const { t } = useTranslation();
  const { id } = useLocalSearchParams<{ id?: string }>();
  const library = useLibraryStore((state) => state.library);
  const saveProgram = useLibraryStore((state) => state.saveProgram);
  const deleteProgram = useLibraryStore((state) => state.deleteProgram);

  const editing = useMemo(() => library?.programs.find((candidate) => candidate.id === id), [library, id]);

  const [draft, setDraft] = useState<Program>(() => editing ?? EMPTY_PROGRAM);
  const [openWorkoutPicker, setOpenWorkoutPicker] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  if (!library) return null;

  // Only the three fields both kinds of week share. Anything kind-specific — the workout, the
  // overrides, rest itself — goes through its own setter below, so a patch can never leave a week
  // half-rest and half-workout.
  const updateWeek = (index: number, patch: Partial<Pick<ProgramWeek, 'week' | 'day' | 'notes'>>) => {
    setDraft((current) => ({
      ...current,
      weeks: current.weeks.map((week, i) => (i === index ? { ...week, ...patch } : week)),
    }));
  };

  /**
   * Rest is a change of kind, not a flag: switching a week to rest drops its workout and any
   * overrides, because a rest week has neither and the schema refuses a file that carries both.
   * Switching back seeds the workout the same way `addWeek` does rather than restoring the old one —
   * remembering it would mean keeping a shadow copy of a field the week no longer has.
   */
  const toggleRestDay = (index: number) => {
    setDraft((current) => ({
      ...current,
      weeks: current.weeks.map((week, i) => {
        if (i !== index) return week;
        if (week.restDay) {
          return { week: week.week, day: week.day, workoutId: library.workouts[0]?.id ?? '', notes: week.notes };
        }
        return { week: week.week, day: week.day, restDay: true, notes: week.notes };
      }),
    }));
    setOpenWorkoutPicker(null);
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
    setDraft((current) => ({
      ...current,
      // The picker is only rendered for a non-rest week, so the guard is belt-and-braces against a
      // stale index rather than a reachable state.
      weeks: current.weeks.map((week, i) => (i === index && !week.restDay ? { ...week, workoutId } : week)),
    }));
    setOpenWorkoutPicker(null);
  };

  const setOverrides = (index: number, overrides: ProgramOverride[]) => {
    setDraft((current) => ({
      ...current,
      weeks: current.weeks.map((week, i) =>
        i === index && !week.restDay ? { ...week, overrides: overrides.length ? overrides : undefined } : week,
      ),
    }));
  };

  const close = () => router.back();

  const save = async () => {
    if (!draft.name.trim()) {
      setError(t('common.nameRequired'));
      return;
    }
    if (draft.weeks.length === 0) {
      setError(t('programEditor.addAtLeastOneWeek'));
      return;
    }
    if (draft.weeks.some((week) => !week.restDay && !week.workoutId)) {
      setError(t('programEditor.everyWeekNeedsWorkout'));
      return;
    }
    // Mirrors the schema's own refusal: a program of nothing but rest days can never queue anything,
    // so the home screen would show a rest card with no way out of it.
    if (!draft.weeks.some((week) => !week.restDay)) {
      setError(t('programEditor.needsOneWorkoutWeek'));
      return;
    }
    const seen = new Set<string>();
    for (const week of draft.weeks) {
      const key = weekKey(week);
      if (seen.has(key)) {
        setError(t('programEditor.duplicateWeek', { label: `${week.week}${week.day ? ` (${week.day})` : ''}` }));
        return;
      }
      seen.add(key);
    }
    const programId = editing?.id || slugify(draft.name);
    if (!programId) {
      setError(t('common.couldNotDeriveId'));
      return;
    }
    try {
      await saveProgram({ ...draft, id: programId, name: draft.name.trim() });
    } catch (err) {
      setError(t('common.saveFailed', { detail: (err as Error).message }));
      return;
    }
    close();
  };

  const confirmDelete = () => {
    if (!editing) return;
    Alert.alert(t('programEditor.deleteConfirmTitle'), t('common.deleteConfirmBody', { name: editing.name }), [
      { text: t('common.cancel'), style: 'cancel' },
      {
        text: t('common.delete'),
        style: 'destructive',
        onPress: async () => {
          try {
            await deleteProgram(editing.id);
          } catch (err) {
            setError(t('common.deleteFailed', { detail: (err as Error).message }));
            return;
          }
          // dismissTo, not back: this editor is reached from the program *detail* modal, which is
          // still on the stack and looks the program up by id. A plain back() landed on it with
          // nothing to find, so deleting left the user staring at "Program not found". Dismissing
          // the whole modal stack down to the tab is what the delete actually meant.
          router.dismissTo('/programs');
        },
      },
    ]);
  };

  const noWorkouts = library.workouts.length === 0;

  return (
    <SafeAreaView
      style={[styles.safeArea, { backgroundColor: theme.background }]}
      edges={['top', 'bottom', 'left', 'right']}>
      <ModalHeader onClose={close} />
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <ThemedText type="subtitle">{editing ? t('programEditor.editTitle') : t('programEditor.newTitle')}</ThemedText>

        <ThemedText type="small" themeColor="textSecondary" style={styles.label}>
          {t('common.name')}
        </ThemedText>
        <TextInput
          value={draft.name}
          onChangeText={(name) => setDraft((current) => ({ ...current, name }))}
          placeholder={t('programEditor.namePlaceholder')}
          placeholderTextColor={theme.textSecondary}
          style={[styles.input, { borderColor: theme.border, backgroundColor: theme.backgroundElement, color: theme.text }]}
        />

        <ThemedText themeColor="textSecondary" style={styles.weekCount}>
          {/* Distinct week *numbers*, not entries. A four-week program with seven slots a week read
              "28 weeks", which is neither what the author has nor what the Programs tab says about
              the same program ("Weeks 1–4"). It was already off for any multi-day program and rest
              days made it three times worse. The cards below are still one per slot. */}
          {t('programEditor.weekCount', { count: programWeekNumbers(draft).length })}
        </ThemedText>

        {noWorkouts && (
          <ThemedText type="small" themeColor="textSecondary" style={styles.noWorkoutsHint}>
            {t('programEditor.noWorkoutsHint')}
          </ThemedText>
        )}

        <View style={styles.list}>
          {draft.weeks.map((week, index) => {
            const workout = week.restDay ? undefined : library.workouts.find((candidate) => candidate.id === week.workoutId);

            return (
              <View
                key={index}
                style={[styles.weekCard, { borderColor: theme.border, backgroundColor: theme.backgroundElement }]}>
                <View style={styles.weekHeader}>
                  <ThemedText type="small" themeColor="textSecondary" style={styles.weekFieldLabel}>
                    {t('programEditor.week')}
                  </ThemedText>
                  <View style={styles.stepperRow}>
                    <Pressable
                      onPress={() => updateWeek(index, { week: Math.max(1, week.week - 1) })}
                      accessibilityRole="button"
                      accessibilityLabel={t('common.decrease', { label: t('programEditor.week') })}
                      style={[styles.stepperButton, { borderColor: theme.border }]}>
                      <ThemedText themeColor="textSecondary">−</ThemedText>
                    </Pressable>
                    <ThemedText type="smallMedium" style={styles.stepperValue}>
                      {week.week}
                    </ThemedText>
                    <Pressable
                      onPress={() => updateWeek(index, { week: week.week + 1 })}
                      accessibilityRole="button"
                      accessibilityLabel={t('common.increase', { label: t('programEditor.week') })}
                      style={[styles.stepperButton, { borderColor: theme.border }]}>
                      <ThemedText themeColor="textSecondary">+</ThemedText>
                    </Pressable>
                  </View>
                  <Pressable
                    onPress={() => removeWeek(index)}
                    hitSlop={8}
                    accessibilityRole="button"
                    accessibilityLabel={t('programEditor.removeWeek', { n: week.week })}
                    style={styles.removeButton}>
                    <ThemedText themeColor="textSecondary">✕</ThemedText>
                  </Pressable>
                </View>

                <ThemedText type="small" themeColor="textSecondary" style={styles.weekFieldLabel}>
                  {t('programEditor.dayOptional')}
                </ThemedText>
                <TextInput
                  value={week.day ?? ''}
                  onChangeText={(text) => updateWeek(index, { day: text.trim() || undefined })}
                  placeholder={t('programEditor.dayPlaceholder')}
                  placeholderTextColor={theme.textSecondary}
                  style={[
                    styles.smallInput,
                    { borderColor: theme.border, backgroundColor: theme.background, color: theme.text },
                  ]}
                />

                {/* A switch rather than a checkbox-ish button: it flips one week between two kinds,
                    which is what `switch` announces, and `checked` is what a screen reader reads back. */}
                <Pressable
                  onPress={() => toggleRestDay(index)}
                  accessibilityRole="switch"
                  accessibilityState={{ checked: week.restDay === true }}
                  style={[
                    styles.restToggle,
                    { borderColor: week.restDay ? theme.accent : theme.border },
                    week.restDay && { backgroundColor: theme.accentSoft },
                  ]}>
                  <ThemedText type="smallMedium" themeColor={week.restDay ? 'accentText' : 'textSecondary'}>
                    {t('programEditor.restDay')}
                  </ThemedText>
                </Pressable>

                {/* Everything below is a property of a week that runs something. A rest week keeps its
                    number, day and notes and nothing else. */}
                {!week.restDay && (
                  <>
                    <ThemedText type="small" themeColor="textSecondary" style={styles.weekFieldLabel}>
                      {t('programEditor.workout')}
                    </ThemedText>
                    <Pressable
                      onPress={() => setOpenWorkoutPicker((current) => (current === index ? null : index))}
                      accessibilityRole="button"
                      accessibilityState={{ expanded: openWorkoutPicker === index }}
                      style={[styles.workoutPickerButton, { borderColor: theme.border }]}>
                      <ThemedText type="smallMedium">{workout?.name ?? t('programEditor.selectWorkout')}</ThemedText>
                      <ThemedText themeColor="textSecondary">{openWorkoutPicker === index ? '⌄' : '›'}</ThemedText>
                    </Pressable>
                    {openWorkoutPicker === index && (
                      <View style={styles.picker}>
                        {library.workouts.map((candidate) => (
                          <Pressable
                            key={candidate.id}
                            onPress={() => selectWorkout(index, candidate.id)}
                            accessibilityRole="button"
                            accessibilityState={{ selected: candidate.id === week.workoutId }}>
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
                  </>
                )}

                <ThemedText type="small" themeColor="textSecondary" style={styles.weekFieldLabel}>
                  {t('programEditor.notesOptional')}
                </ThemedText>
                <TextInput
                  value={week.notes ?? ''}
                  onChangeText={(text) => updateWeek(index, { notes: text.trim() || undefined })}
                  placeholder={t('programEditor.notesPlaceholder')}
                  placeholderTextColor={theme.textSecondary}
                  style={[
                    styles.smallInput,
                    { borderColor: theme.border, backgroundColor: theme.background, color: theme.text },
                  ]}
                />

                {!week.restDay && (
                  <>
                    <ThemedText type="small" themeColor="textSecondary" style={styles.weekFieldLabel}>
                      {t('programEditor.overridesOptional')}
                    </ThemedText>
                    <ProgramOverrideEditor
                      library={library}
                      workout={workout}
                      overrides={week.overrides ?? []}
                      onChange={(overrides) => setOverrides(index, overrides)}
                    />
                  </>
                )}
              </View>
            );
          })}
        </View>

        <Pressable
          onPress={addWeek}
          disabled={noWorkouts}
          accessibilityRole="button"
          accessibilityState={{ disabled: noWorkouts }}
          style={[styles.addWeek, { borderColor: theme.border }, noWorkouts && styles.disabled]}>
          <ThemedText type="heading" themeColor="textSecondary">
            {t('programEditor.addWeek')}
          </ThemedText>
        </Pressable>

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
              {t('programEditor.deleteProgram')}
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
    minHeight: 40,
    borderRadius: 10,
    borderWidth: 1,
    paddingHorizontal: Spacing.one + 4,
    fontSize: 14,
  },
  // 44px minimum via minHeight, not height — a fixed one clips the label at large text sizes.
  restToggle: {
    alignSelf: 'flex-start',
    minHeight: 44,
    justifyContent: 'center',
    borderRadius: 10,
    borderWidth: 1,
    paddingHorizontal: Spacing.two,
    marginTop: Spacing.two - 2,
  },
  workoutPickerButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    minHeight: 44,
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
    minHeight: 48,
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
    minHeight: 52,
    borderRadius: 15,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
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
