import { File } from 'expo-file-system';
import { router } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ModalHeader } from '@/components/modal-header';
import { ThemedText } from '@/components/themed-text';
import type { MergeSummary } from '@/domain/merge';
import { mergeLibraries } from '@/domain/merge';
import type { Library } from '@/domain/types';
import { parseLibraryYaml } from '@/domain/yaml-mapping';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useLibraryStore } from '@/state/library-store';

type PickedFile = { name: string; sizeLabel: string };
type ReadyMerge = { picked: PickedFile; library: Library; summary: MergeSummary };

export default function ImportScreen() {
  const theme = useTheme();
  const { t } = useTranslation();
  const currentLibrary = useLibraryStore((state) => state.library);
  const replaceLibrary = useLibraryStore((state) => state.replaceLibrary);

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [ready, setReady] = useState<ReadyMerge | null>(null);

  const close = () => router.back();

  const pickFile = async () => {
    setError(null);
    setReady(null);
    setBusy(true);
    try {
      const result = await File.pickFileAsync();
      if (result.canceled) return;

      const file = result.result;
      const text = await file.text();
      const parsed = parseLibraryYaml(text);
      if (!parsed.ok) {
        setError(parsed.error);
        return;
      }
      if (!currentLibrary) {
        setError(t('import.libraryNotLoaded'));
        return;
      }
      const merge = mergeLibraries(currentLibrary, parsed.data);
      if (!merge.ok) {
        setError(merge.error);
        return;
      }

      const sizeBytes = file.size;
      const sizeLabel = sizeBytes < 1024 ? `${sizeBytes} B` : `${(sizeBytes / 1024).toFixed(1)} KB`;
      setReady({ picked: { name: file.name, sizeLabel }, library: merge.library, summary: merge.summary });
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const confirmMerge = async () => {
    if (!ready) return;
    setBusy(true);
    try {
      await replaceLibrary(ready.library);
      close();
    } catch (err) {
      setError((err as Error).message);
      setBusy(false);
    }
  };

  const changedItems = ready
    ? [
        ...ready.summary.newExercises.map((id) => ({ id, kind: 'new' as const, detail: t('import.newExercise') })),
        ...ready.summary.updatedExercises.map((id) => ({
          id,
          kind: 'updated' as const,
          detail: t('import.updatedExercise'),
        })),
        ...ready.summary.newWorkouts.map((id) => ({ id, kind: 'new' as const, detail: t('import.newWorkout') })),
        ...ready.summary.updatedWorkouts.map((id) => ({ id, kind: 'updated' as const, detail: t('import.updatedWorkout') })),
      ]
    : [];

  return (
    <SafeAreaView
      style={[styles.safeArea, { backgroundColor: theme.background }]}
      edges={['top', 'bottom', 'left', 'right']}>
      <ModalHeader onClose={close} />
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <ThemedText type="subtitle">{t('import.title')}</ThemedText>

        {!ready && (
          <Pressable
            onPress={pickFile}
            disabled={busy}
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
        )}

        {ready && (
          <View style={[styles.fileRow, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
            <View style={[styles.fileIcon, { borderColor: theme.textSecondary }]} />
            <View style={styles.fileText}>
              <ThemedText type="heading" style={styles.fileName}>
                {ready.picked.name}
              </ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                {t('import.pickedFrom', { size: ready.picked.sizeLabel })}
              </ThemedText>
            </View>
          </View>
        )}

        {error && (
          <ThemedText type="small" style={[styles.error, { color: theme.accentText }]}>
            {error}
          </ThemedText>
        )}

        {ready && (
          <>
            <View style={styles.countsRow}>
              <View style={[styles.countCard, { backgroundColor: theme.accentSoft }]}>
                <ThemedText type="subtitle" themeColor="accentText">
                  {ready.summary.newExercises.length + ready.summary.newWorkouts.length}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {t('import.new')}
                </ThemedText>
              </View>
              <View style={[styles.countCard, { backgroundColor: theme.accentCalmSoft }]}>
                <ThemedText type="subtitle" themeColor="accentCalmText">
                  {ready.summary.updatedExercises.length + ready.summary.updatedWorkouts.length}
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
                <View key={`${item.detail}-${item.id}`} style={styles.changedRow}>
                  <ThemedText
                    style={[styles.changedGlyph, { color: item.kind === 'new' ? theme.accentText : theme.accentCalmText }]}>
                    {item.kind === 'new' ? '+' : '↻'}
                  </ThemedText>
                  <ThemedText type="smallMedium" style={styles.changedName}>
                    {item.id}
                  </ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">
                    {item.detail}
                  </ThemedText>
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
          <Pressable onPress={close} style={[styles.cancelButton, { borderColor: theme.border }]}>
            <ThemedText type="heading" themeColor="textSecondary">
              {t('common.cancel')}
            </ThemedText>
          </Pressable>
          <Pressable
            onPress={confirmMerge}
            disabled={!ready || busy}
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
  fileText: {
    flex: 1,
    gap: 2,
  },
  fileName: {},
  error: {
    marginTop: Spacing.two,
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
    height: 52,
    borderRadius: 15,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  mergeButton: {
    flex: 1.4,
    height: 52,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
