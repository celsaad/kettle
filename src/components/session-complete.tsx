import { Pressable, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { ThemedText } from '@/components/themed-text';
import { RunnerColors, Spacing } from '@/constants/theme';
import type { RecordResult } from '@/domain/format';
import { formatOneRepMax, formatRecord } from '@/domain/format';
import { toDisplayWeight } from '@/domain/units';
import type { SessionRecord } from '@/state/selectors';
import { useUnitSystem } from '@/state/preferences-store';

type Props = {
  workoutName: string;
  /** Empty on most sessions, which is the point — this screen stays as it was when nothing was beaten. */
  records?: SessionRecord[];
  onDone: () => void;
};

/**
 * The bookend to SessionCountdown at the other end of a session — same dark full-screen, centered
 * layout family, deliberately a different scale (a checkmark + a large title, not a giant numeral)
 * since this is a different kind of moment, not another countdown.
 */
export function SessionComplete({ workoutName, records = [], onDone }: Props) {
  const { t } = useTranslation();
  const unitSystem = useUnitSystem();
  const unitLabel = t(unitSystem === 'imperial' ? 'units.lb' : 'units.kg');

  // Weights reach this screen in kilograms, like everywhere else, and become a string only here —
  // `domain/format.ts` has no access to the unit preference. Same boundary as `session-reps.tsx`.
  const weightLabel = (kg: number) => `${toDisplayWeight(kg, unitSystem)} ${unitLabel}`;

  // Only when a 1RM is actually on screen — a bodyweight-only session has no estimate for the note
  // to be explaining.
  const showsOneRepMax = records.some((record) => record.kind === 'heaviestSet' && record.oneRepMaxKg !== null);

  const describe = (record: SessionRecord): RecordResult => {
    switch (record.kind) {
      case 'heaviestSet':
        return { kind: 'heaviestSet', weight: weightLabel(record.weightKg), reps: record.reps };
      case 'mostReps':
        return { kind: 'mostReps', reps: record.reps };
      case 'longestHold':
        return { kind: 'longestHold', holdSec: record.holdSec };
      case 'mostRounds':
        return { kind: 'mostRounds', rounds: record.rounds };
    }
  };

  return (
    <View style={styles.container}>
      <View style={styles.badge}>
        <ThemedText style={styles.checkmark}>✓</ThemedText>
      </View>
      <ThemedText type="title" style={styles.title}>
        {t('session.complete.title')}
      </ThemedText>
      <ThemedText type="small" style={styles.workoutName}>
        {workoutName}
      </ThemedText>

      {records.length > 0 && (
        <View style={styles.records}>
          {records.map((record) => (
            <View key={`${record.exerciseId}-${record.kind}`} style={styles.recordCard}>
              <View style={styles.recordHeader}>
                {/* A text badge, not a color: the accent alone would carry the whole "this is a PR"
                    signal, which fails for anyone who can't separate it from the warm accent the
                    checkmark and Done button already use. */}
                <View style={styles.recordPill}>
                  <ThemedText type="code" style={styles.recordPillLabel}>
                    {t('session.complete.recordBadge')}
                  </ThemedText>
                </View>
                {/* The user's own exercise name — rendered verbatim, never translated. */}
                <ThemedText type="smallMedium" style={styles.recordExercise} numberOfLines={1}>
                  {record.exerciseName}
                </ThemedText>
              </View>
              <ThemedText type="heading" style={styles.recordValue}>
                {formatRecord(describe(record))}
              </ThemedText>
              {record.kind === 'heaviestSet' && record.oneRepMaxKg !== null && (
                <ThemedText type="small" style={styles.recordEstimate}>
                  {formatOneRepMax(weightLabel(record.oneRepMaxKg))}
                </ThemedText>
              )}
            </View>
          ))}
          {/* Once, under the list, rather than in parentheses after every value: naming the formula
              is for the person who has decided to question the number, and that is not the moment
              they have just finished a workout in. It still says so on screen, which is the point —
              an unattributed 1RM is a number people argue with. */}
          {showsOneRepMax && (
            <ThemedText type="small" style={styles.recordsNote}>
              {t('session.complete.oneRepMaxNote')}
            </ThemedText>
          )}
        </View>
      )}

      <Pressable onPress={onDone} accessibilityRole="button" style={styles.doneButton}>
        <ThemedText type="heading" style={styles.doneButtonLabel}>
          {t('session.complete.done')}
        </ThemedText>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.two,
  },
  badge: {
    width: 88,
    height: 88,
    borderRadius: 44,
    backgroundColor: RunnerColors.accentSoft,
    borderWidth: 1,
    borderColor: 'rgba(207,106,55,0.4)',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: Spacing.one,
  },
  checkmark: {
    fontSize: 44,
    lineHeight: 44,
    color: RunnerColors.accent,
  },
  title: {
    color: RunnerColors.text,
    textAlign: 'center',
  },
  workoutName: {
    color: RunnerColors.textSecondary,
  },
  records: {
    marginTop: Spacing.three,
    width: '100%',
    gap: Spacing.two,
  },
  recordCard: {
    backgroundColor: RunnerColors.backgroundElement,
    borderWidth: 1,
    borderColor: RunnerColors.border,
    borderRadius: 16,
    padding: Spacing.two + 6,
    gap: Spacing.half,
  },
  recordHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    marginBottom: Spacing.half,
  },
  // The calm accent rather than the warm one, so a record reads as a different kind of thing from the
  // checkmark and the Done button. Both tokens carry measured ratios against RunnerColors.background
  // (constants/theme.ts), which is the surface this card's translucent fill composites onto.
  recordPill: {
    backgroundColor: RunnerColors.accentCalmSoft,
    borderWidth: 1,
    borderColor: 'rgba(63,130,192,0.4)',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 999,
  },
  recordPillLabel: {
    color: RunnerColors.accentCalmOnSoft,
    letterSpacing: 1.4,
  },
  recordExercise: {
    flex: 1,
    minWidth: 0,
    color: RunnerColors.textSecondary,
  },
  recordValue: {
    color: RunnerColors.text,
  },
  recordEstimate: {
    color: RunnerColors.textSecondary,
  },
  recordsNote: {
    marginTop: Spacing.half,
    color: RunnerColors.textSecondary,
  },
  doneButton: {
    marginTop: Spacing.four,
    minHeight: 52,
    paddingVertical: Spacing.one,
    paddingHorizontal: Spacing.three,
    minWidth: 160,
    borderRadius: 15,
    backgroundColor: RunnerColors.accent,
    alignItems: 'center',
    justifyContent: 'center',
  },
  doneButtonLabel: {
    textAlign: 'center',
    color: RunnerColors.background,
  },
});
