import { router, useLocalSearchParams } from 'expo-router';
import { useMemo, useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ExerciseBadge } from '@/components/exercise-badge';
import { ModalHeader } from '@/components/modal-header';
import { ThemedText } from '@/components/themed-text';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { fieldUnitLabel } from '@/domain/exercise-form';
import { formatEntryResult, formatSessionName } from '@/domain/format';
import {
  buildEntry,
  ENTRY_FIELDS,
  entryToSetForms,
  isEditableEntry,
  validateEntryForm,
  type EditableEntry,
  type SetForm,
} from '@/domain/session-entry-form';
import { formatMonthBadge } from '@/i18n/format';
import { useTheme } from '@/hooks/use-theme';
import { useLibraryStore } from '@/state/library-store';
import { useUnitSystem } from '@/state/preferences-store';
import { exerciseName, sessionEntryResult } from '@/state/selectors';
import { useSessionHistoryStore } from '@/state/session-history-store';

export { ModalErrorBoundary as ErrorBoundary } from '@/components/error-fallback';

/**
 * One entry's working state.
 *
 * `index` is its position in `session.entries` — **the raw array, not History's view of it**.
 * `historySessionsView` filters `rest` entries out, so a card's position on the History screen is not
 * this number, and writing back with the wrong one would rewrite whichever entry happened to sit at
 * that offset. This screen builds from the raw session for that reason and never from the view.
 *
 * An empty `forms` means the user removed the entry's last set, so the whole entry comes out on save.
 */
type EntryDraft = { index: number; entry: EditableEntry; name: string; forms: SetForm[] };

/** Entry types whose log is one result rather than a list of sets — no "Set 1" label to put on a lone row. */
function isPerSet(entry: EditableEntry): boolean {
  return entry.type === 'reps' || entry.type === 'timed_hold';
}

/**
 * Corrects what a finished session logged: a mis-typed rep count, a load entered before the plates
 * changed, an RPE tapped in the wrong column.
 *
 * Save-at-the-end rather than write-through, matching the other editors. That is what makes Remove
 * safe to offer — it edits this screen's copy, so closing without saving puts a mistaken removal back,
 * and the only write is the one the Save button asks for.
 */
export default function SessionEditorScreen() {
  const theme = useTheme();
  const { t } = useTranslation();
  const { id } = useLocalSearchParams<{ id?: string }>();
  const unitSystem = useUnitSystem();

  const library = useLibraryStore((state) => state.library);
  const sessions = useSessionHistoryStore((state) => state.sessions);
  const editEntry = useSessionHistoryStore((state) => state.editEntry);
  const removeEntry = useSessionHistoryStore((state) => state.removeEntry);

  const session = useMemo(() => sessions.find((existing) => existing.id === id), [sessions, id]);

  const [drafts, setDrafts] = useState<EntryDraft[]>(() => {
    if (!session) return [];
    const exercises = library?.exercises ?? [];
    return session.entries.flatMap((entry, index) =>
      isEditableEntry(entry)
        ? [{ index, entry, name: exerciseName(exercises, entry.exercise), forms: entryToSetForms(entry, unitSystem) }]
        : [],
    );
  });
  const [error, setError] = useState<string | null>(null);

  const close = () => router.back();

  const setField = (draftIndex: number, formIndex: number, key: string, text: string) => {
    setDrafts((current) =>
      current.map((draft, position) =>
        position === draftIndex
          ? {
              ...draft,
              forms: draft.forms.map((form, formPosition) =>
                formPosition === formIndex ? { ...form, values: { ...form.values, [key]: text } } : form,
              ),
            }
          : draft,
      ),
    );
  };

  const dropForm = (draftIndex: number, formIndex: number) => {
    setDrafts((current) =>
      current.map((draft, position) =>
        position === draftIndex ? { ...draft, forms: draft.forms.filter((_, i) => i !== formIndex) } : draft,
      ),
    );
  };

  const removeForm = (draftIndex: number, formIndex: number) => {
    const draft = drafts[draftIndex];
    if (draft.forms.length > 1) {
      dropForm(draftIndex, formIndex);
      return;
    }
    // The last one takes the exercise with it, which is a bigger thing than dropping a set and is the
    // one step here the user can't infer from the button they pressed.
    Alert.alert(
      t('sessionEditor.removeEntryConfirmTitle'),
      t('sessionEditor.removeEntryConfirmBody', { name: draft.name }),
      [
        { text: t('common.cancel'), style: 'cancel' },
        { text: t('common.delete'), style: 'destructive', onPress: () => dropForm(draftIndex, formIndex) },
      ],
    );
  };

  const save = () => {
    if (!session) return;

    // Validate everything before writing anything: a failure halfway through would leave the session
    // half-corrected on disk with the screen still open, which is worse than either outcome.
    for (const draft of drafts) {
      const problem = validateEntryForm(draft.entry.type, draft.forms);
      if (problem) {
        setError(problem);
        return;
      }
    }

    const built = drafts.map((draft) => ({ index: draft.index, entry: buildEntry(draft.entry, draft.forms, unitSystem) }));

    // Edits first: replacing an entry leaves every index alone, so these are safe in any order.
    for (const result of built) {
      if (result.entry) editEntry(session.id, result.index, result.entry);
    }
    // Removals last, and **back to front**, because each one shifts every later index down by one.
    // Front-to-back, removing entries 1 and 3 would delete 1, then delete whatever slid into 3.
    for (let position = built.length - 1; position >= 0; position -= 1) {
      if (!built[position].entry) removeEntry(session.id, built[position].index);
    }

    close();
  };

  const body = () => {
    if (!session) return notice(t('sessionEditor.notFoundTitle'), t('sessionEditor.notFoundBody'));
    // The runner owns an in-flight session's file and writes through its own copy of it, so an edit
    // made here would be overwritten by the next set logged. The store refuses this case too — this
    // is only what stops the screen from taking input it would then silently drop.
    if (!session.endedAt) return notice(t('sessionEditor.runningTitle'), t('sessionEditor.runningBody'));
    if (drafts.length === 0 && session.entries.every((entry) => !isEditableEntry(entry))) {
      return notice(t('sessionEditor.emptyTitle'), t('sessionEditor.emptyBody'));
    }
    return null;
  };

  const notice = (title: string, message: string) => (
    <View style={styles.notice}>
      <ThemedText type="heading">{title}</ThemedText>
      <ThemedText type="small" themeColor="textSecondary">
        {message}
      </ThemedText>
    </View>
  );

  const blocked = body();
  const startedAt = session ? new Date(session.startedAt) : null;
  // Falls back to the raw id for a workout since deleted from the library, and to the ad-hoc label for
  // a session that never had one — the same resolution `historySessionsView` does, so the editor's
  // header and the card it was opened from agree.
  const workoutName = formatSessionName(
    session?.workout ? (library?.workouts.find((workout) => workout.id === session.workout)?.name ?? session.workout) : null,
  );
  // Entries the editor can't rewrite still get listed, read-only: a session that quietly dropped its
  // EMOM on the way into the editor would read as if the log had lost it.
  const readOnly = session?.entries.filter((entry) => entry.type === 'emom') ?? [];

  return (
    <SafeAreaView
      style={[styles.safeArea, { backgroundColor: theme.background }]}
      edges={['top', 'bottom', 'left', 'right']}>
      <ModalHeader onClose={close} />
      <ScrollView contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">
        <ThemedText type="subtitle">{t('sessionEditor.title')}</ThemedText>
        {startedAt && (
          <ThemedText type="small" themeColor="textSecondary" style={styles.subtitle}>
            {/* The workout's own name, interpolated rather than translated. */}
            {t('sessionEditor.subtitle', {
              name: workoutName,
              day: startedAt.getDate(),
              month: formatMonthBadge(startedAt),
            })}
          </ThemedText>
        )}

        {blocked ?? (
          <>
            <ThemedText type="small" themeColor="textSecondary" style={styles.intro}>
              {t('sessionEditor.intro')}
            </ThemedText>

            {drafts.map((draft, draftIndex) =>
              draft.forms.length === 0 ? null : (
                <View
                  key={`${draft.index}-${draft.entry.exercise}`}
                  style={[styles.card, { borderColor: theme.border, backgroundColor: theme.backgroundElement }]}>
                  <View style={styles.cardHeader}>
                    <ThemedText type="heading" numberOfLines={1} style={styles.cardName}>
                      {draft.name}
                    </ThemedText>
                    <ExerciseBadge type={draft.entry.type} />
                  </View>

                  {draft.forms.map((form, formIndex) => {
                    const perSet = isPerSet(draft.entry);
                    const setLabel = t('sessionEditor.setLabel', { n: formIndex + 1 });
                    return (
                      <View key={formIndex} style={[styles.setRow, { borderTopColor: theme.border }]}>
                        {perSet && (
                          <ThemedText type="code" themeColor="textSecondary">
                            {setLabel.toUpperCase()}
                          </ThemedText>
                        )}
                        <View style={styles.fields}>
                          {ENTRY_FIELDS[draft.entry.type].map((field) => {
                            const unit = fieldUnitLabel(field, unitSystem);
                            const fieldLabel = unit ? `${t(field.label)} (${unit})` : t(field.label);
                            return (
                              <View key={field.key} style={styles.field}>
                                <ThemedText type="small" themeColor="textSecondary" style={styles.fieldLabel}>
                                  {fieldLabel}
                                </ThemedText>
                                <TextInput
                                  value={form.values[field.key] ?? ''}
                                  onChangeText={(text) => setField(draftIndex, formIndex, field.key, text)}
                                  // The visible label above isn't programmatically tied to the input in
                                  // RN, and on a three-set entry every field would otherwise announce
                                  // identically — the set number is the only thing telling them apart.
                                  accessibilityLabel={perSet ? `${setLabel} · ${fieldLabel}` : fieldLabel}
                                  keyboardType="numeric"
                                  placeholder={field.optional ? '—' : '0'}
                                  placeholderTextColor={theme.textSecondary}
                                  style={[
                                    styles.input,
                                    { borderColor: theme.border, backgroundColor: theme.background, color: theme.text },
                                  ]}
                                />
                              </View>
                            );
                          })}
                        </View>
                        <Pressable
                          onPress={() => removeForm(draftIndex, formIndex)}
                          accessibilityRole="button"
                          // Same reason as the inputs: "Remove set" reads the same on every row.
                          accessibilityLabel={
                            draft.forms.length > 1
                              ? `${t('sessionEditor.removeSet')} · ${setLabel}`
                              : t('sessionEditor.removeEntry')
                          }
                          style={styles.removeButton}
                          hitSlop={8}>
                          <ThemedText type="small" themeColor="textSecondary">
                            {draft.forms.length > 1 ? t('sessionEditor.removeSet') : t('sessionEditor.removeEntry')}
                          </ThemedText>
                        </Pressable>
                      </View>
                    );
                  })}
                </View>
              ),
            )}

            {readOnly.map((entry, position) => (
              <View
                key={`readonly-${position}-${entry.exercise}`}
                style={[styles.card, { borderColor: theme.border, backgroundColor: theme.backgroundElement }]}>
                <View style={styles.cardHeader}>
                  <ThemedText type="heading" numberOfLines={1} style={styles.cardName}>
                    {exerciseName(library?.exercises ?? [], entry.exercise)}
                  </ThemedText>
                  <ExerciseBadge type={entry.type} />
                </View>
                <ThemedText type="smallMedium">{formatEntryResult(sessionEntryResult(entry))}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {t('sessionEditor.readOnlyNote')}
                </ThemedText>
              </View>
            ))}

            {error && (
              <ThemedText type="small" style={[styles.error, { color: theme.accentText }]}>
                {error}
              </ThemedText>
            )}

            <View style={styles.buttonRow}>
              <Pressable
                onPress={close}
                accessibilityRole="button"
                style={[styles.cancelButton, { borderColor: theme.border }]}>
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
          </>
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
  subtitle: {
    marginTop: 2,
  },
  intro: {
    marginTop: Spacing.two,
  },
  notice: {
    marginTop: Spacing.three,
    gap: Spacing.one,
  },
  card: {
    marginTop: Spacing.three - 2,
    borderRadius: 16,
    borderWidth: 1,
    padding: Spacing.two + 4,
    gap: Spacing.one,
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
  // The name is an identifier: it takes the leftover width and ellipsizes rather than pushing the
  // type badge off the card.
  cardName: {
    flex: 1,
  },
  setRow: {
    marginTop: Spacing.one,
    paddingTop: Spacing.one + 2,
    borderTopWidth: 1,
    gap: Spacing.one,
  },
  fields: {
    flexDirection: 'row',
    gap: Spacing.two - 2,
  },
  field: {
    flex: 1,
  },
  fieldLabel: {
    marginBottom: 4,
  },
  input: {
    // minHeight, not height: a fixed one collapses the input at large accessibility text sizes.
    minHeight: 46,
    borderRadius: 12,
    borderWidth: 1,
    paddingHorizontal: Spacing.two,
    fontSize: 15,
  },
  removeButton: {
    alignSelf: 'flex-end',
    minHeight: 44,
    justifyContent: 'center',
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
});
