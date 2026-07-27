import Constants from 'expo-constants';
import { router } from 'expo-router';
import type { ReactNode } from 'react';
import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ModalHeader } from '@/components/modal-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import type { ThemePreference } from '@/hooks/theme-context';
import { useAppTheme } from '@/hooks/theme-context';
import { useTheme } from '@/hooks/use-theme';
import { useLibraryStore } from '@/state/library-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { exportLibrary } from '@/storage/export';
import { isFileStorageSupported } from '@/storage/paths';

const APPEARANCE: { label: string; value: ThemePreference }[] = [
  { label: 'Light', value: 'light' },
  { label: 'Dark', value: 'dark' },
  { label: 'System', value: 'system' },
];

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <View style={styles.section}>
      <ThemedText type="label" themeColor="textSecondary" style={styles.sectionTitle}>
        {title}
      </ThemedText>
      {children}
    </View>
  );
}

function ActionRow({
  title,
  detail,
  onPress,
  disabled,
}: {
  title: string;
  detail: string;
  onPress: () => void;
  disabled?: boolean;
}) {
  const theme = useTheme();

  return (
    <Pressable onPress={onPress} disabled={disabled} style={({ pressed }) => pressed && styles.pressed}>
      <ThemedView
        type="backgroundElement"
        style={[styles.row, { borderColor: theme.border }, disabled && styles.disabled]}>
        <View style={styles.rowText}>
          <ThemedText type="heading">{title}</ThemedText>
          <ThemedText type="small" themeColor="textSecondary">
            {detail}
          </ThemedText>
        </View>
        {!disabled && <ThemedText themeColor="textSecondary">{'›'}</ThemedText>}
      </ThemedView>
    </Pressable>
  );
}

export default function SettingsScreen() {
  const theme = useTheme();
  const { preference, setPreference, scheme } = useAppTheme();
  const library = useLibraryStore((state) => state.library);
  const sessions = useSessionHistoryStore((state) => state.sessions);
  const [exportError, setExportError] = useState<string | null>(null);

  const close = () => router.back();

  // The Library tab swallows this failure, which is fine next to a one-word "Export" link; a row
  // that spells out what it's about to do owes the user a reason when nothing happens. The `async`
  // wrapper matters: on web `exportLibrary` throws *synchronously* (expo-file-system's File
  // constructor has no web implementation, and it runs before any promise exists), which a bare
  // `.catch()` would miss — that's the unhandled error the Library tab's Export hits today.
  const runExport = async () => {
    setExportError(null);
    try {
      await exportLibrary();
    } catch (error) {
      setExportError((error as Error).message);
    }
  };

  const counts = [
    // Matches the Library tab's count: `rest` is a built-in pseudo-exercise, not something the user
    // wrote, so counting it would make the two screens disagree.
    { label: 'Exercises', value: library?.exercises.filter((exercise) => exercise.type !== 'rest').length ?? 0 },
    { label: 'Workouts', value: library?.workouts.length ?? 0 },
    { label: 'Programs', value: library?.programs.length ?? 0 },
    { label: 'Sessions', value: sessions.length },
  ];

  const version = Constants.expoConfig?.version;

  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: theme.background }]} edges={['top', 'bottom', 'left', 'right']}>
      <ModalHeader onClose={close} />
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <ThemedText type="subtitle">Settings</ThemedText>

        <Section title="Appearance">
          <View style={[styles.segmented, { borderColor: theme.border, backgroundColor: theme.backgroundElement }]}>
            {APPEARANCE.map((option) => {
              const active = option.value === preference;
              return (
                <Pressable
                  key={option.value}
                  onPress={() => setPreference(option.value)}
                  accessibilityRole="button"
                  accessibilityState={{ selected: active }}
                  style={[styles.segment, active && { backgroundColor: theme.text }]}>
                  <ThemedText type="smallMedium" style={{ color: active ? theme.onAccent : theme.textSecondary }}>
                    {option.label}
                  </ThemedText>
                </Pressable>
              );
            })}
          </View>
          <ThemedText type="small" themeColor="textSecondary" style={styles.caption}>
            {preference === 'system'
              ? `Following your device, which is set to ${scheme}.`
              : `Pinned to ${preference}, ignoring your device.`}
          </ThemedText>
        </Section>

        <Section title="Data">
          <View style={styles.rowList}>
            <ActionRow
              title="Export library"
              detail={
                isFileStorageSupported
                  ? 'Share exercises.yaml — every exercise, workout and program.'
                  : 'Not available in the browser build.'
              }
              onPress={runExport}
              disabled={!isFileStorageSupported}
            />
            <ActionRow
              title="Import library"
              detail="Merge a .yaml file into what you already have."
              onPress={() => router.push('/import')}
            />
          </View>
          {exportError && (
            <ThemedText type="small" style={[styles.caption, { color: theme.accentText }]}>
              {exportError}
            </ThemedText>
          )}
          <ThemedText type="small" themeColor="textSecondary" style={styles.caption}>
            Sessions stay on this device — export covers the library only.
          </ThemedText>
        </Section>

        <Section title="In your library">
          <ThemedView type="backgroundElement" style={[styles.countsCard, { borderColor: theme.border }]}>
            {counts.map((count) => (
              <View key={count.label} style={styles.countRow}>
                <ThemedText themeColor="textSecondary">{count.label}</ThemedText>
                <ThemedText type="heading">{count.value}</ThemedText>
              </View>
            ))}
          </ThemedView>
        </Section>

        {version && (
          <ThemedText type="small" themeColor="textSecondary" style={styles.version}>
            Kettle {version}
          </ThemedText>
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
  section: {
    marginTop: Spacing.four,
  },
  sectionTitle: {
    marginBottom: Spacing.two,
  },
  segmented: {
    flexDirection: 'row',
    gap: Spacing.half + 1,
    borderRadius: 14,
    borderWidth: 1,
    padding: Spacing.half + 1,
  },
  segment: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: Spacing.two + 1,
    borderRadius: 11,
  },
  caption: {
    marginTop: Spacing.two,
  },
  rowList: {
    gap: Spacing.two - 3,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderRadius: 16,
    borderWidth: 1,
    padding: Spacing.two + 6,
  },
  rowText: {
    flex: 1,
    gap: 2,
  },
  pressed: {
    opacity: 0.7,
  },
  disabled: {
    opacity: 0.5,
  },
  countsCard: {
    borderRadius: 16,
    borderWidth: 1,
    paddingHorizontal: Spacing.two + 6,
    paddingVertical: Spacing.two,
  },
  countRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: Spacing.two - 1,
  },
  version: {
    marginTop: Spacing.four,
    textAlign: 'center',
  },
});
