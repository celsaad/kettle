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
import { REST_DAY_REMINDER_DAYS } from '@/domain/preferences';
import type { UnitSystem } from '@/domain/units';
import { useAppTheme } from '@/hooks/theme-context';
import { useTheme } from '@/hooks/use-theme';
import { useLibraryStore } from '@/state/library-store';
import { usePreferencesStore, useUnitSystem } from '@/state/preferences-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { isTipJarSupported, useTipStore } from '@/state/tip-store';
import type { BackupFailure } from '@/storage/backup';
import { backUpNow, backupFolderLabel, isBackupFolderSupported, pickBackupFolder } from '@/storage/backup';
import { exportLibrary, exportSessions } from '@/storage/export';
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

// Off first, so the default sits where the eye lands and the control reads as opt-in rather than as
// something being switched off. Reuses Segmented rather than introducing the app's first Switch —
// same a11y surface, already carrying accessibilityState.
const REMINDER: { labelKey: string; value: 'off' | 'on' }[] = [
  { labelKey: 'settings.reminderOff', value: 'off' },
  { labelKey: 'settings.reminderOn', value: 'on' },
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
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      style={({ pressed }) => pressed && styles.pressed}>
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
  const restDayReminder = usePreferencesStore((state) => state.preferences.restDayReminder);
  const setRestDayReminder = usePreferencesStore((state) => state.setRestDayReminder);
  const library = useLibraryStore((state) => state.library);
  const sessions = useSessionHistoryStore((state) => state.sessions);
  const supporter = useTipStore((state) => state.supporter);
  const hydrateTips = useTipStore((state) => state.hydrate);
  const backupFolderUri = usePreferencesStore((state) => state.preferences.backupFolderUri);
  const setBackupFolderUri = usePreferencesStore((state) => state.setBackupFolderUri);
  const [exportError, setExportError] = useState<string | null>(null);
  // One line for both outcomes rather than an error state and a separate success state: they are
  // mutually exclusive answers to the same question, and two pieces of state would let a stale
  // "backed up" sit under a fresh failure.
  const [backupMessage, setBackupMessage] = useState<{ ok: boolean; text: string } | null>(null);

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

  // The Library and History tabs swallow this failure, which is fine next to a one-word link; a row
  // that spells out what it's about to do owes the user a reason when nothing happens. The `async`
  // wrapper matters: on web both exporters throw *synchronously* (expo-file-system's File
  // constructor has no web implementation, and it runs before any promise exists), which a bare
  // `.catch()` would miss — that's the unhandled error the Library tab's Export hits today.
  //
  // Parameterised rather than one function per row: they fail the same two ways (no share sheet, no
  // filesystem) into the same error line below, so a second copy would only be a second place to
  // forget the try/catch.
  const runExport = async (exporter: () => Promise<void>) => {
    setExportError(null);
    try {
      await exporter();
    } catch (error) {
      setExportError((error as Error).message);
    }
  };

  /**
   * A failure turned into something to read. The only one carrying a platform string is
   * `writeFailed` — there is no better phrasing available for "the OS refused, and here is why" —
   * and it's interpolated rather than rendered bare so the sentence around it stays translated.
   */
  const describeBackupFailure = (failure: BackupFailure): string => {
    switch (failure.kind) {
      case 'unsupported':
        return t('settings.backupUnsupported');
      case 'noFolder':
        return t('settings.backupNowNoFolder');
      case 'unreachable':
        return t('settings.backupUnreachable');
      case 'writeFailed':
        return t('settings.backupWriteFailed', { detail: failure.detail });
    }
  };

  const runBackup = () => {
    const failure = backUpNow(backupFolderUri, sessions);
    setBackupMessage(
      failure ? { ok: false, text: describeBackupFailure(failure) } : { ok: true, text: t('settings.backupDone') },
    );
  };

  /**
   * Opens the system picker and keeps what comes back.
   *
   * Backing out of the picker leaves everything as it was — `pickBackupFolder` answers null for that
   * rather than treating it as an error, so a mis-tap can't clear a folder the user already chose.
   * A folder that was chosen but couldn't be written to `preferences.json` is called out, because the
   * grant itself survives the restart and the URI wouldn't: it would look set and stop working.
   */
  const chooseFolder = async () => {
    setBackupMessage(null);
    try {
      const uri = await pickBackupFolder();
      if (!uri) return;
      const saved = await setBackupFolderUri(uri);
      setBackupMessage(saved ? null : { ok: false, text: t('settings.backupFolderNotSaved') });
    } catch (error) {
      setBackupMessage({ ok: false, text: (error as Error).message });
    }
  };

  const forgetFolder = async () => {
    setBackupMessage(null);
    await setBackupFolderUri(null);
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

        <Section title={t('settings.reminder')}>
          <Segmented
            options={REMINDER}
            selected={restDayReminder ? 'on' : 'off'}
            onSelect={(value) => setRestDayReminder(value === 'on')}
          />
          <ThemedText type="small" themeColor="textSecondary" style={styles.caption}>
            {/* Spells out both halves of what makes this different from a daily alarm: it's a local
                notification that never leaves the phone, and it only lands if you've actually gone
                quiet. Someone training regularly will never see it, which is worth saying up front. */}
            {t('settings.reminderNote', { count: REST_DAY_REMINDER_DAYS })}
          </ThemedText>
        </Section>

        <Section title={t('settings.data')}>
          <View style={styles.rowList}>
            {/* First of the three, above the exports: it's the only row here that *changes* anything,
                and the one an assistant-written library arrives through. The exports below it are
                also the only rows that can be disabled, so leading with them put a greyed-out row at
                the top of the section on a device with no file storage and nothing logged yet. */}
            <ActionRow
              title={t('settings.importLibrary')}
              detail={t('settings.importLibraryDetail')}
              onPress={() => router.push('/import')}
            />
            <ActionRow
              title={t('settings.exportLibrary')}
              detail={isFileStorageSupported ? t('settings.exportLibraryDetail') : t('settings.exportUnavailable')}
              onPress={() => runExport(exportLibrary)}
              disabled={!isFileStorageSupported}
            />
            {/* Disabled rather than hidden at zero sessions, unlike History's header link: this row
                is part of a list describing what the app can do with your data, and a row that
                appears only once you've trained wouldn't answer "is my log exportable?" for the
                person asking before they start. */}
            <ActionRow
              title={t('settings.exportHistory')}
              detail={
                !isFileStorageSupported
                  ? t('settings.exportUnavailable')
                  : sessions.length === 0
                    ? t('settings.exportHistoryEmpty')
                    : t('settings.exportHistoryDetail', { count: sessions.length })
              }
              onPress={() => runExport(() => exportSessions(sessions))}
              disabled={!isFileStorageSupported || sessions.length === 0}
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

        {/*
          The sync story. It used to say export/import *was* the mechanism, which was true only
          because the app couldn't reach outside its own folder — now it can, into one the user
          nominates, so the copy describes that instead.

          Hidden entirely where the folder can't be made to stick (see `isBackupFolderSupported`),
          rather than shown greyed out: an iOS grant dies with the app session, so the row would be
          advertising a backup that silently stops happening. The export rows above still work there.
        */}
        <Section title={t('settings.files')}>
          {isBackupFolderSupported ? (
            <>
              <View style={styles.rowList}>
                <ActionRow
                  title={t('settings.backupFolder')}
                  // The folder is the user's own path — rendered verbatim, never translated.
                  detail={backupFolderUri ? backupFolderLabel(backupFolderUri) : t('settings.backupFolderNone')}
                  onPress={chooseFolder}
                />
                <ActionRow
                  title={t('settings.backupNow')}
                  detail={backupFolderUri ? t('settings.backupNowDetail') : t('settings.backupNowNoFolder')}
                  onPress={runBackup}
                  disabled={!backupFolderUri}
                />
                {/* Only once there's something to forget. A row offering to undo a choice nobody has
                    made is three rows where two would do. */}
                {backupFolderUri && (
                  <ActionRow
                    title={t('settings.backupForget')}
                    detail={t('settings.backupForgetDetail')}
                    onPress={forgetFolder}
                  />
                )}
              </View>
              {backupMessage && (
                <ThemedText
                  type="small"
                  style={[styles.caption, { color: backupMessage.ok ? theme.textSecondary : theme.accentText }]}>
                  {backupMessage.text}
                </ThemedText>
              )}
              <ThemedText type="small" themeColor="textSecondary" style={styles.caption}>
                {t('settings.backupWhat')}
              </ThemedText>
              {/* The honest half, and the reason no string above says "restore": the library can be
                  imported back, the log cannot — nothing in the app parses a session file. Promising
                  a restore that doesn't exist is the one way this feature could cost someone
                  everything it was built to protect. */}
              <ThemedText type="small" themeColor="textSecondary" style={styles.caption}>
                {t('settings.backupLimits')}
              </ThemedText>
            </>
          ) : (
            <>
              <ThemedText type="small" themeColor="textSecondary">
                {t('settings.filesLocal')}
              </ThemedText>
              <ThemedText type="small" themeColor="textSecondary" style={styles.caption}>
                {t('settings.filesSync')}
              </ThemedText>
            </>
          )}
        </Section>

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
