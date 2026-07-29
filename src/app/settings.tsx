import Constants from 'expo-constants';
import { router } from 'expo-router';
import type { ReactNode } from 'react';
import { useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ModalHeader } from '@/components/modal-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import type { ThemePreference } from '@/domain/preferences';
import type { UnitSystem } from '@/domain/units';
import { useAppTheme } from '@/hooks/theme-context';
import { useTheme } from '@/hooks/use-theme';
import { useLibraryStore } from '@/state/library-store';
import { usePreferencesStore, useUnitSystem } from '@/state/preferences-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { isTipJarSupported, useTipStore } from '@/state/tip-store';
import { exportLibrary } from '@/storage/export';
import { isFileStorageSupported } from '@/storage/paths';

export { ModalErrorBoundary as ErrorBoundary } from '@/components/error-fallback';

const APPEARANCE: { labelKey: string; value: ThemePreference }[] = [
  { labelKey: 'settings.light', value: 'light' },
  { labelKey: 'settings.dark', value: 'dark' },
  { labelKey: 'settings.system', value: 'system' },
];

// No "system" option to match Appearance's: the device measurement system only seeds the initial
// value (see deviceUnitSystem), and a running "follow the device" mode would have nothing to follow —
// unlike the color scheme, the OS has no unit setting that changes while the app is open.
const UNITS: { labelKey: string; value: UnitSystem }[] = [
  { labelKey: 'settings.metric', value: 'metric' },
  { labelKey: 'settings.imperial', value: 'imperial' },
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

/** Extracted when Units needed the same control as Appearance — two copies of this markup would have drifted. */
function Segmented<T extends string>({
  options,
  selected,
  onSelect,
}: {
  options: { labelKey: string; value: T }[];
  selected: T;
  onSelect: (value: T) => void;
}) {
  const theme = useTheme();
  const { t } = useTranslation();

  return (
    <View style={[styles.segmented, { borderColor: theme.border, backgroundColor: theme.backgroundElement }]}>
      {options.map((option) => {
        const active = option.value === selected;
        return (
          <Pressable
            key={option.value}
            onPress={() => onSelect(option.value)}
            accessibilityRole="button"
            accessibilityState={{ selected: active }}
            style={[styles.segment, active && { backgroundColor: theme.text }]}>
            <ThemedText type="smallMedium" style={{ color: active ? theme.onAccent : theme.textSecondary }}>
              {t(option.labelKey)}
            </ThemedText>
          </Pressable>
        );
      })}
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
      <ThemedView type="backgroundElement" style={[styles.row, { borderColor: theme.border }, disabled && styles.disabled]}>
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
  const { t } = useTranslation();
  const { preference, setPreference, scheme } = useAppTheme();
  const unitSystem = useUnitSystem();
  const setUnitSystem = usePreferencesStore((state) => state.setUnitSystem);
  const library = useLibraryStore((state) => state.library);
  const sessions = useSessionHistoryStore((state) => state.sessions);
  const supporter = useTipStore((state) => state.supporter);
  const hydrateTips = useTipStore((state) => state.hydrate);
  const [exportError, setExportError] = useState<string | null>(null);

  // Cheap enough to do on open, and it's what lets the Support row acknowledge a past tip. The store
  // is deliberately absent from the root layout's startup gate — see the note in tip-store.ts.
  useEffect(() => {
    if (isTipJarSupported) hydrateTips();
  }, [hydrateTips]);
  // Reuses the segmented control's own labels, lowercased, so the sentence below always names the
  // same word the user just saw — no separate translation to keep in sync with the button text.
  const schemeWord = (value: ThemePreference) =>
    t(APPEARANCE.find((option) => option.value === value)!.labelKey).toLowerCase();

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
    { label: t('settings.exercises'), value: library?.exercises.filter((exercise) => exercise.type !== 'rest').length ?? 0 },
    { label: t('build.title'), value: library?.workouts.length ?? 0 },
    { label: t('programs.title'), value: library?.programs.length ?? 0 },
    { label: t('settings.sessions'), value: sessions.length },
  ];

  const version = Constants.expoConfig?.version;

  return (
    <SafeAreaView
      style={[styles.safeArea, { backgroundColor: theme.background }]}
      edges={['top', 'bottom', 'left', 'right']}>
      <ModalHeader onClose={close} />
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <ThemedText type="subtitle">{t('settings.title')}</ThemedText>

        <Section title={t('settings.appearance')}>
          <Segmented options={APPEARANCE} selected={preference} onSelect={setPreference} />
          <ThemedText type="small" themeColor="textSecondary" style={styles.caption}>
            {preference === 'system'
              ? t('settings.followingDevice', { scheme: schemeWord(scheme) })
              : t('settings.pinnedTo', { preference: schemeWord(preference) })}
          </ThemedText>
        </Section>

        <Section title={t('settings.units')}>
          <Segmented options={UNITS} selected={unitSystem} onSelect={setUnitSystem} />
          <ThemedText type="small" themeColor="textSecondary" style={styles.caption}>
            {/* The imperial copy spells out that storage doesn't change, because that's the surprising
                half: switching units here does not rewrite a single number in exercises.yaml. */}
            {unitSystem === 'imperial' ? t('settings.unitsImperialNote') : t('settings.unitsMetricNote')}
          </ThemedText>
        </Section>

        <Section title={t('settings.data')}>
          <View style={styles.rowList}>
            <ActionRow
              title={t('settings.exportLibrary')}
              detail={isFileStorageSupported ? t('settings.exportLibraryDetail') : t('settings.exportUnavailable')}
              onPress={runExport}
              disabled={!isFileStorageSupported}
            />
            <ActionRow
              title={t('settings.importLibrary')}
              detail={t('settings.importLibraryDetail')}
              onPress={() => router.push('/import')}
            />
          </View>
          {exportError && (
            <ThemedText type="small" style={[styles.caption, { color: theme.accentText }]}>
              {exportError}
            </ThemedText>
          )}
          <ThemedText type="small" themeColor="textSecondary" style={styles.caption}>
            {t('settings.sessionsStayNote')}
          </ThemedText>
        </Section>

        {/* Hidden rather than disabled on web: there's no Play Billing in a browser, so a greyed row
            would advertise something that can never work there. */}
        {isTipJarSupported && (
          <Section title={t('settings.support')}>
            <ActionRow
              title={t('support.row')}
              detail={supporter.tipCount > 0 ? t('support.rowDetailTipped') : t('support.rowDetail')}
              onPress={() => router.push('/support')}
            />
          </Section>
        )}

        <Section title={t('settings.inYourLibrary')}>
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
            {t('settings.version', { version })}
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
