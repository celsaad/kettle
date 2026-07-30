import * as Clipboard from 'expo-clipboard';
import { File } from 'expo-file-system';
import { router } from 'expo-router';
import type { TFunction } from 'i18next';
import { useEffect, useRef, useState } from 'react';
import { AccessibilityInfo, ActivityIndicator, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ModalHeader } from '@/components/modal-header';
import { ThemedText } from '@/components/themed-text';
import { buildAssistantBrief } from '@/domain/assistant-brief';
import type { FieldChange } from '@/domain/library-diff';
import { diffExercise, diffProgram, diffWorkout } from '@/domain/library-diff';
import type { MergeError, MergeSummary } from '@/domain/merge';
import { mergeLibraries } from '@/domain/merge';
import type { Library } from '@/domain/types';
import type { ParseError } from '@/domain/yaml-mapping';
import { parseLibraryYaml } from '@/domain/yaml-mapping';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useLibraryStore } from '@/state/library-store';
import { useUnitSystem } from '@/state/preferences-store';

export { ModalErrorBoundary as ErrorBoundary } from '@/components/error-fallback';

/** What the preview says it's about to merge. A filename for a pick, "Pasted YAML" for a paste. */
type Source = { title: string; detail: string };
type ReadyMerge = { picked: Source; library: Library; summary: MergeSummary };

/**
 * A refusal on screen, plus — when there is one — the same refusal worded for whoever wrote the YAML.
 *
 * `report` is deliberately null for the failures that aren't about the *content*: a picker that
 * couldn't read the file, a disk that wouldn't take the write, a library that hasn't hydrated. Handing
 * "Couldn't save your library: no space left on device" to an assistant asks it to fix something it
 * has no access to, and offering the button at all would suggest it could.
 */
type ImportError = { message: string; report: string | null };

/**
 * The two things worth putting on the clipboard, and the two directions of the same loop: `brief` goes
 * out to an assistant before anything is written, `report` goes back to it after the importer refuses
 * what came of that.
 */
type CopyTarget = 'brief' | 'report';

/**
 * Where an import failure becomes a sentence. The parser and the merge return descriptors — they're
 * logic-layer code and hold no prose — so every reason a file is refused is worded here, in both
 * locales, rather than in whichever module happened to detect it.
 */
function errorMessage(t: TFunction, error: ParseError | MergeError): string {
  switch (error.kind) {
    case 'invalidYaml':
      return t('import.error.invalidYaml', { detail: error.detail });
    case 'schemaMismatch':
      return t('import.error.schemaMismatch', { detail: error.detail });
    case 'unknownExercise':
      return t('import.error.unknownExercise', { workout: error.workoutId, exercise: error.exerciseId });
    case 'unknownWorkout':
      return t('import.error.unknownWorkout', {
        program: error.programId,
        // The day is the user's own label ("Monday", "Push A"), so it renders verbatim inside the frame.
        week: error.day ? t('import.error.weekWithDay', { week: error.week, day: error.day }) : error.week,
        workout: error.workoutId,
      });
  }
}

export default function ImportScreen() {
  const theme = useTheme();
  const { t } = useTranslation();
  const currentLibrary = useLibraryStore((state) => state.library);
  const replaceLibrary = useLibraryStore((state) => state.replaceLibrary);
  // The diff prints a target weight, and a weight is never formatted without going through the
  // preference — a kilogram figure shown to somebody working in pounds describes a change they didn't
  // make.
  const unitSystem = useUnitSystem();

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ImportError | null>(null);
  const [ready, setReady] = useState<ReadyMerge | null>(null);
  const [pasting, setPasting] = useState(false);
  const [pasted, setPasted] = useState('');
  // Keyed by which button copied, since there are two of them: a "Copied" that isn't tied to a target
  // would confirm the wrong one the moment the screen has both on it.
  const [copied, setCopied] = useState<{ target: CopyTarget; status: 'copied' | 'failed' } | null>(null);
  const copyTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(
    () => () => {
      if (copyTimerRef.current) clearTimeout(copyTimerRef.current);
    },
    [],
  );

  const close = () => router.back();

  /** Every refusal goes through here, so none can leave a stale "Copied" over a different error. */
  const refuse = (message: string, report: string | null = null) => {
    setError({ message, report });
    setCopied(null);
  };

  /** A refusal the YAML's author can act on — the only kind that gets a report to hand back. */
  const refuseContent = (reason: ParseError | MergeError) => {
    const message = errorMessage(t, reason);
    refuse(message, `${t('import.repairPrompt')}\n\n${message}`);
  };

  /**
   * The one path to the clipboard, shared by both directions of the loop.
   *
   * The `report` it carries is the refusal plus a line of framing, and deliberately *not* the rejected
   * YAML: an assistant that just wrote it still has it, and a hand-edited file is on disk where the
   * user can point at it, so appending it would mostly mean putting a whole library on the clipboard
   * to say something both ends already know. The `brief` is built in `domain/assistant-brief.ts`.
   */
  const copy = async (target: CopyTarget, text: string) => {
    try {
      // The boolean is the API's own way of saying it didn't take, distinct from throwing — so a
      // "Copied" claimed over a false here would be the one thing these buttons must never do.
      if (!(await Clipboard.setStringAsync(text))) {
        setCopied({ target, status: 'failed' });
        return;
      }
      setCopied({ target, status: 'copied' });
      // Announced rather than left to the label change: the button's own text switches to "Copied",
      // which a screen reader doesn't re-read while focus stays put.
      AccessibilityInfo.announceForAccessibility(t('import.copiedAnnouncement'));
      if (copyTimerRef.current) clearTimeout(copyTimerRef.current);
      copyTimerRef.current = setTimeout(() => setCopied(null), 2000);
    } catch {
      // Reachable on web, where the clipboard is permission-gated and throws when denied. For the
      // report that means the error it would replace is the one still worth reading, so both degrade
      // to a note beside the button rather than taking the screen over.
      setCopied({ target, status: 'failed' });
    }
  };

  /**
   * The only path from raw YAML to a confirmed preview. Both input sources land here, so a paste is
   * refused for the same reasons and in the same words as a file — the two differ in where the text
   * came from and in nothing else.
   */
  const review = (text: string, source: Source) => {
    const parsed = parseLibraryYaml(text);
    if (!parsed.ok) {
      refuseContent(parsed.error);
      return;
    }
    if (!currentLibrary) {
      refuse(t('import.libraryNotLoaded'));
      return;
    }
    const merge = mergeLibraries(currentLibrary, parsed.data);
    if (!merge.ok) {
      refuseContent(merge.error);
      return;
    }
    setReady({ picked: source, library: merge.library, summary: merge.summary });
  };

  const pickFile = async () => {
    setError(null);
    setReady(null);
    setBusy(true);
    try {
      const result = await File.pickFileAsync();
      if (result.canceled) return;

      const file = result.result;
      const text = await file.text();
      const sizeBytes = file.size;
      const sizeLabel = sizeBytes < 1024 ? `${sizeBytes} B` : `${(sizeBytes / 1024).toFixed(1)} KB`;
      review(text, { title: file.name, detail: t('import.pickedFrom', { size: sizeLabel }) });
    } catch (err) {
      // The picker's and the filesystem's own message, which is the platform's and stays untranslated
      // — but the sentence around it says which step failed, and that part is ours.
      refuse(t('import.error.readFailed', { detail: (err as Error).message }));
    } finally {
      setBusy(false);
    }
  };

  /**
   * No I/O, so no `busy` and no failure mode of its own — a paste can only be refused by the parser
   * or the merge, which is the whole reason this path is worth having: an assistant's YAML is text in
   * a chat window, and saving it to a file on a phone just to hand it back to the picker is several
   * awkward steps that buy nothing.
   */
  const reviewPaste = () => {
    setError(null);
    setReady(null);
    const text = pasted.trim();
    if (!text) return;
    review(text, {
      title: t('import.pastedTitle'),
      detail: t('import.pastedLines', { count: text.split('\n').length }),
    });
  };

  const confirmMerge = async () => {
    if (!ready) return;
    setBusy(true);
    try {
      await replaceLibrary(ready.library);
      close();
    } catch (err) {
      refuse(t('import.error.saveFailed', { detail: (err as Error).message }));
      setBusy(false);
    }
  };

  // Programs are listed alongside exercises and workouts, and counted in the tiles below, because the
  // merge writes all three (§6: surface updates clearly so local tweaks aren't lost silently). They
  // were missing from both, so a program-only file previewed as "0 new, 0 updated" and then merged
  // its programs anyway — the one case where the preview contradicted what the button was about to do.
  /**
   * What an updated id actually loses, per §6 and open question §12.5. Computed here rather than in
   * `merge.ts` because both sides are already on hand — the store's library and the merged result —
   * so the merge doesn't have to carry a diff nobody but this preview wants.
   */
  const changesFor = (kind: 'exercise' | 'workout' | 'program', id: string): FieldChange[] => {
    if (!ready || !currentLibrary) return [];
    switch (kind) {
      case 'exercise': {
        const before = currentLibrary.exercises.find((exercise) => exercise.id === id);
        const after = ready.library.exercises.find((exercise) => exercise.id === id);
        return before && after ? diffExercise(before, after, unitSystem) : [];
      }
      case 'workout': {
        const before = currentLibrary.workouts.find((workout) => workout.id === id);
        const after = ready.library.workouts.find((workout) => workout.id === id);
        return before && after ? diffWorkout(before, after) : [];
      }
      case 'program': {
        const before = currentLibrary.programs.find((program) => program.id === id);
        const after = ready.library.programs.find((program) => program.id === id);
        return before && after ? diffProgram(before, after) : [];
      }
    }
  };

  const changedItems = ready
    ? [
        ...ready.summary.newExercises.map((id) => ({
          id,
          kind: 'new' as const,
          detail: t('import.newExercise'),
          changes: [],
        })),
        ...ready.summary.updatedExercises.map((id) => ({
          id,
          kind: 'updated' as const,
          detail: t('import.updatedExercise'),
          changes: changesFor('exercise', id),
        })),
        ...ready.summary.newWorkouts.map((id) => ({
          id,
          kind: 'new' as const,
          detail: t('import.newWorkout'),
          changes: [],
        })),
        ...ready.summary.updatedWorkouts.map((id) => ({
          id,
          kind: 'updated' as const,
          detail: t('import.updatedWorkout'),
          changes: changesFor('workout', id),
        })),
        ...ready.summary.newPrograms.map((id) => ({
          id,
          kind: 'new' as const,
          detail: t('import.newProgram'),
          changes: [],
        })),
        ...ready.summary.updatedPrograms.map((id) => ({
          id,
          kind: 'updated' as const,
          detail: t('import.updatedProgram'),
          changes: changesFor('program', id),
        })),
      ]
    : [];

  const newCount = changedItems.filter((item) => item.kind === 'new').length;
  const updatedCount = changedItems.length - newCount;
  // Hoisted out of the JSX: narrowing `error.report` inside the press handler's closure doesn't hold,
  // since TypeScript has to assume a property can change between render and tap.
  const errorReport = error?.report ?? null;
  const copyLabel = (target: CopyTarget, idle: string) =>
    copied?.target === target && copied.status === 'copied' ? t('import.copied') : idle;
  const copyFailed = (target: CopyTarget) => copied?.target === target && copied.status === 'failed';

  return (
    <SafeAreaView
      style={[styles.safeArea, { backgroundColor: theme.background }]}
      edges={['top', 'bottom', 'left', 'right']}>
      <ModalHeader onClose={close} />
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <ThemedText type="subtitle">{t('import.title')}</ThemedText>

        {!ready && (
          <>
            <Pressable
              onPress={pickFile}
              disabled={busy}
              accessibilityRole="button"
              accessibilityLabel={t('import.chooseFile')}
              style={[
                styles.fileRow,
                styles.pickRow,
                { backgroundColor: theme.backgroundElement, borderColor: theme.border },
              ]}>
              <View style={[styles.fileIcon, { borderColor: theme.textSecondary }]} />
              <View style={styles.fileText}>
                <ThemedText type="heading">{t('import.chooseFile')}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {t('import.chooseFileDetail')}
                </ThemedText>
              </View>
              {busy && <ActivityIndicator color={theme.accentText} />}
            </Pressable>

            <Pressable
              onPress={() => setPasting((open) => !open)}
              accessibilityRole="button"
              accessibilityLabel={t('import.pasteToggle')}
              accessibilityState={{ expanded: pasting }}
              style={[
                styles.fileRow,
                styles.pickRow,
                { backgroundColor: theme.backgroundElement, borderColor: theme.border },
              ]}>
              <View style={[styles.fileIcon, styles.pasteIcon, { borderColor: theme.textSecondary }]} />
              <View style={styles.fileText}>
                <ThemedText type="heading">{t('import.pasteToggle')}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {t('import.pasteToggleDetail')}
                </ThemedText>
              </View>
            </Pressable>

            {pasting && (
              <>
                <TextInput
                  value={pasted}
                  onChangeText={setPasted}
                  multiline
                  autoCapitalize="none"
                  autoCorrect={false}
                  // YAML is whitespace-significant, so the keyboard must not be allowed to "helpfully"
                  // capitalize an id or swap a quote for a smart one on the way in.
                  placeholder={t('import.pastePlaceholder')}
                  placeholderTextColor={theme.textSecondary}
                  accessibilityLabel={t('import.pastePlaceholder')}
                  style={[
                    styles.pasteInput,
                    { borderColor: theme.border, backgroundColor: theme.backgroundElement, color: theme.text },
                  ]}
                />
                <Pressable
                  onPress={reviewPaste}
                  disabled={!pasted.trim()}
                  accessibilityRole="button"
                  accessibilityLabel={t('import.reviewPaste')}
                  style={[styles.reviewButton, { backgroundColor: theme.accentSoft, opacity: pasted.trim() ? 1 : 0.5 }]}>
                  <ThemedText type="heading" themeColor="accentText">
                    {t('import.reviewPaste')}
                  </ThemedText>
                </Pressable>
              </>
            )}

            {/*
              Sits under both input sources rather than beside them, because it isn't one: it's what
              you send *before* there's any YAML to bring in. Only rendered with a library loaded —
              the brief's whole point is the ids in it, and offering it empty would hand an assistant
              a confident list of nothing.
            */}
            {currentLibrary && (
              <View style={styles.briefRow}>
                <Pressable
                  onPress={() => copy('brief', buildAssistantBrief(currentLibrary))}
                  accessibilityRole="button"
                  style={[styles.copyButton, { borderColor: theme.border }]}>
                  <ThemedText type="smallMedium" themeColor="textSecondary">
                    {copyLabel('brief', t('import.copyBrief'))}
                  </ThemedText>
                </Pressable>
                <ThemedText type="small" themeColor="textSecondary">
                  {copyFailed('brief') ? t('import.copyFailed') : t('import.copyBriefDetail')}
                </ThemedText>
              </View>
            )}
          </>
        )}

        {ready && (
          <View style={[styles.fileRow, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
            <View style={[styles.fileIcon, { borderColor: theme.textSecondary }]} />
            <View style={styles.fileText}>
              <ThemedText type="heading" style={styles.fileName}>
                {ready.picked.title}
              </ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                {ready.picked.detail}
              </ThemedText>
            </View>
          </View>
        )}

        {error && (
          <View style={styles.error}>
            <ThemedText type="small" style={{ color: theme.accentText }}>
              {error.message}
            </ThemedText>
            {errorReport && (
              <View style={styles.copyRow}>
                <Pressable
                  onPress={() => copy('report', errorReport)}
                  accessibilityRole="button"
                  style={[styles.copyButton, { borderColor: theme.border }]}>
                  <ThemedText type="smallMedium" themeColor="textSecondary">
                    {copyLabel('report', t('import.copyError'))}
                  </ThemedText>
                </Pressable>
                {copyFailed('report') && (
                  <ThemedText type="small" themeColor="textSecondary" style={styles.copyFailed}>
                    {t('import.copyFailed')}
                  </ThemedText>
                )}
              </View>
            )}
          </View>
        )}

        {ready && (
          <>
            <View style={styles.countsRow}>
              <View style={[styles.countCard, { backgroundColor: theme.accentSoft }]}>
                <ThemedText type="subtitle" themeColor="accentText">
                  {newCount}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {t('import.new')}
                </ThemedText>
              </View>
              <View style={[styles.countCard, { backgroundColor: theme.accentCalmSoft }]}>
                <ThemedText type="subtitle" themeColor="accentCalmText">
                  {updatedCount}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {t('import.updated')}
                </ThemedText>
              </View>
              <View
                style={[
                  styles.countCard,
                  { backgroundColor: theme.backgroundElement, borderWidth: 1, borderColor: theme.border },
                ]}>
                <ThemedText type="subtitle">
                  {ready.summary.newWorkouts.length + ready.summary.updatedWorkouts.length}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {t('import.workoutNoun', {
                    count: ready.summary.newWorkouts.length + ready.summary.updatedWorkouts.length,
                  })}
                </ThemedText>
              </View>
            </View>

            <View style={styles.changedList}>
              {changedItems.map((item) => (
                <View key={`${item.detail}-${item.id}`}>
                  <View style={styles.changedRow}>
                    <ThemedText
                      style={[
                        styles.changedGlyph,
                        { color: item.kind === 'new' ? theme.accentText : theme.accentCalmText },
                      ]}>
                      {item.kind === 'new' ? '+' : '↻'}
                    </ThemedText>
                    <ThemedText type="smallMedium" style={styles.changedName}>
                      {item.id}
                    </ThemedText>
                    <ThemedText type="small" themeColor="textSecondary">
                      {item.detail}
                    </ThemedText>
                  </View>
                  {/*
                    Only for updates, and only what moved. An updated id whose definition is
                    byte-identical says so instead of showing an empty indent — `mergeById` classifies
                    by id, not by value, so re-importing your own export lands here for every item and
                    would otherwise look like a wall of unexplained overwrites.
                  */}
                  {item.kind === 'updated' && (
                    <View style={styles.diffList}>
                      {item.changes.map((field) => (
                        <ThemedText key={field.label} type="small" themeColor="textSecondary">
                          {field.label}: {field.from} → {field.to}
                        </ThemedText>
                      ))}
                      {item.changes.length === 0 && (
                        <ThemedText type="small" themeColor="textSecondary">
                          {t('import.diff.sameValues')}
                        </ThemedText>
                      )}
                    </View>
                  )}
                </View>
              ))}
              {changedItems.length === 0 && (
                <ThemedText type="small" themeColor="textSecondary">
                  {t('import.noChanges')}
                </ThemedText>
              )}
            </View>

            <ThemedText type="small" themeColor="textSecondary" style={styles.note}>
              {t('import.updateNote')}
            </ThemedText>
          </>
        )}

        <View style={styles.buttonRow}>
          <Pressable
            onPress={close}
            accessibilityRole="button"
            accessibilityLabel={t('common.cancel')}
            style={[styles.cancelButton, { borderColor: theme.border }]}>
            <ThemedText type="heading" themeColor="textSecondary">
              {t('common.cancel')}
            </ThemedText>
          </Pressable>
          <Pressable
            onPress={confirmMerge}
            disabled={!ready || busy}
            accessibilityRole="button"
            accessibilityLabel={t('import.mergeButton')}
            style={[styles.mergeButton, { backgroundColor: theme.accent, opacity: !ready || busy ? 0.5 : 1 }]}>
            <ThemedText type="heading" style={{ color: theme.onAccent }}>
              {t('import.mergeButton')}
            </ThemedText>
          </Pressable>
        </View>
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
  fileRow: {
    marginTop: Spacing.three - 2,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: 14,
    padding: Spacing.two + 4,
  },
  pickRow: {
    borderStyle: 'dashed',
  },
  fileIcon: {
    width: 26,
    height: 32,
    borderRadius: 5,
    borderWidth: 1.5,
  },
  // Squarer than the file glyph, so the two rows read as different sources at a glance.
  pasteIcon: {
    height: 26,
    borderRadius: 7,
  },
  pasteInput: {
    marginTop: Spacing.two,
    minHeight: 132,
    borderRadius: 12,
    borderWidth: 1,
    paddingHorizontal: Spacing.two + 4,
    paddingTop: Spacing.one + 4,
    paddingBottom: Spacing.one + 4,
    fontSize: 14,
    textAlignVertical: 'top',
  },
  reviewButton: {
    marginTop: Spacing.two,
    minHeight: 48,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
  },
  fileText: {
    flex: 1,
    gap: 2,
  },
  fileName: {},
  error: {
    marginTop: Spacing.two,
    gap: Spacing.two - 2,
    alignItems: 'flex-start',
  },
  copyRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    flexWrap: 'wrap',
  },
  briefRow: {
    marginTop: Spacing.three - 2,
    gap: Spacing.one + 2,
    alignItems: 'flex-start',
  },
  copyButton: {
    minHeight: 44,
    justifyContent: 'center',
    paddingHorizontal: Spacing.two + 2,
    borderWidth: 1,
    borderRadius: 12,
  },
  copyFailed: {
    flexShrink: 1,
  },
  countsRow: {
    flexDirection: 'row',
    gap: Spacing.two,
    marginTop: Spacing.three - 2,
  },
  countCard: {
    flex: 1,
    alignItems: 'center',
    borderRadius: 12,
    paddingVertical: Spacing.two + 2,
  },
  changedList: {
    marginTop: Spacing.three - 2,
    gap: Spacing.two - 1,
  },
  changedRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two + 2,
  },
  changedGlyph: {
    width: 18,
    textAlign: 'center',
    fontWeight: '700',
  },
  changedName: {
    flex: 1,
  },
  // Indented under the id it belongs to, past the glyph column so the two read as one entry.
  diffList: {
    marginTop: 2,
    marginLeft: 18 + Spacing.two + 2,
    gap: 1,
  },
  note: {
    marginTop: Spacing.two + 4,
  },
  buttonRow: {
    flexDirection: 'row',
    gap: Spacing.three - 4,
    marginTop: Spacing.three,
  },
  cancelButton: {
    flex: 1,
    minHeight: 52,
    borderRadius: 15,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  mergeButton: {
    flex: 1.4,
    minHeight: 52,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
