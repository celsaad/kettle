import { Pressable, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { RunnerColors, Spacing } from '@/constants/theme';

const RPE_OPTIONS = [7, 8, 9];

type Props = {
  exerciseName: string;
  setIndex: number;
  setTotal: number;
  targetReps: number;
  reps: number;
  onChangeReps: (reps: number) => void;
  rpe: number;
  onChangeRpe: (rpe: number) => void;
  onPrev: () => void;
  onLogSet: () => void;
};

export function SessionReps({
  exerciseName,
  setIndex,
  setTotal,
  targetReps,
  reps,
  onChangeReps,
  rpe,
  onChangeRpe,
  onPrev,
  onLogSet,
}: Props) {
  return (
    <View style={styles.container}>
      <View style={styles.top}>
        <View style={styles.repsPill}>
          <ThemedText type="code" style={styles.repsPillLabel}>
            REPS
          </ThemedText>
        </View>
        <ThemedText type="subtitle" style={styles.exerciseName}>
          {exerciseName}
        </ThemedText>
        <ThemedText type="small" style={styles.setLabel}>
          Set {setIndex} of {setTotal} · target {targetReps}
        </ThemedText>
      </View>

      <View style={styles.middle}>
        <View style={styles.stepperRow}>
          <Pressable
            onPress={() => onChangeReps(Math.max(0, reps - 1))}
            style={styles.stepperButton}>
            <ThemedText type="title" style={styles.stepperGlyph}>
              −
            </ThemedText>
          </Pressable>
          <View style={styles.repsDisplay}>
            <ThemedText type="numeral" style={styles.numeral}>
              {reps}
            </ThemedText>
            <ThemedText type="code" style={styles.repsLabel}>
              REPS DONE
            </ThemedText>
          </View>
          <Pressable onPress={() => onChangeReps(reps + 1)} style={[styles.stepperButton, styles.stepperButtonAccent]}>
            <ThemedText type="title" style={[styles.stepperGlyph, styles.stepperGlyphAccent]}>
              +
            </ThemedText>
          </Pressable>
        </View>

        <View style={styles.statsRow}>
          <View style={styles.statCard}>
            <ThemedText type="code" style={styles.statCardLabel}>
              LOAD
            </ThemedText>
            <ThemedText type="heading" style={styles.loadValue}>
              BW <ThemedText style={styles.loadUnit}>+0 kg</ThemedText>
            </ThemedText>
          </View>
          <View style={styles.statCard}>
            <ThemedText type="code" style={styles.statCardLabel}>
              RPE
            </ThemedText>
            <View style={styles.rpeRow}>
              {RPE_OPTIONS.map((option) => {
                const active = option === rpe;
                return (
                  <Pressable
                    key={option}
                    onPress={() => onChangeRpe(option)}
                    style={[styles.rpePill, active && styles.rpePillActive]}>
                    <ThemedText type="smallMedium" style={active ? styles.rpeTextActive : styles.rpeText}>
                      {option}
                    </ThemedText>
                  </Pressable>
                );
              })}
            </View>
          </View>
        </View>
      </View>

      <View style={styles.controlsRow}>
        <Pressable onPress={onPrev} style={styles.prevButton}>
          <ThemedText type="code" style={styles.prevLabel}>
            Prev
          </ThemedText>
        </Pressable>
        <Pressable onPress={onLogSet} style={styles.logButton}>
          <ThemedText type="heading" style={styles.logButtonLabel}>
            Log set → Rest
          </ThemedText>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'space-between',
  },
  top: {},
  repsPill: {
    alignSelf: 'flex-start',
    backgroundColor: RunnerColors.accentSoft,
    borderWidth: 1,
    borderColor: 'rgba(207,106,55,0.4)',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 999,
  },
  repsPillLabel: {
    color: RunnerColors.accent,
    letterSpacing: 1.4,
  },
  exerciseName: {
    marginTop: Spacing.two,
    color: RunnerColors.text,
  },
  setLabel: {
    marginTop: 4,
    color: RunnerColors.textSecondary,
  },
  middle: {
    flex: 1,
    justifyContent: 'center',
    gap: Spacing.four,
  },
  stepperRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.four - 2,
  },
  stepperButton: {
    width: 60,
    height: 60,
    borderRadius: 30,
    borderWidth: 1.5,
    borderColor: RunnerColors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepperButtonAccent: {
    backgroundColor: RunnerColors.accentSoft,
    borderColor: 'rgba(207,106,55,0.4)',
  },
  stepperGlyph: {
    fontSize: 28,
    color: RunnerColors.textSecondary,
  },
  stepperGlyphAccent: {
    color: RunnerColors.accent,
  },
  repsDisplay: {
    alignItems: 'center',
  },
  numeral: {
    fontSize: 88,
    lineHeight: 88,
    color: RunnerColors.text,
  },
  repsLabel: {
    marginTop: 6,
    color: RunnerColors.textSecondary,
    letterSpacing: 1.4,
  },
  statsRow: {
    flexDirection: 'row',
    gap: Spacing.two,
  },
  statCard: {
    flex: 1,
    backgroundColor: RunnerColors.backgroundElement,
    borderWidth: 1,
    borderColor: RunnerColors.border,
    borderRadius: 16,
    padding: Spacing.two + 6,
  },
  statCardLabel: {
    color: RunnerColors.textSecondary,
    letterSpacing: 1,
  },
  loadValue: {
    marginTop: 2,
    color: RunnerColors.text,
  },
  loadUnit: {
    fontSize: 15,
    color: RunnerColors.textSecondary,
  },
  rpeRow: {
    flexDirection: 'row',
    gap: 6,
    marginTop: 6,
  },
  rpePill: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 4,
    borderRadius: 8,
    backgroundColor: 'rgba(243,239,228,0.08)',
  },
  rpePillActive: {
    backgroundColor: RunnerColors.accent,
  },
  rpeText: {
    color: RunnerColors.text,
  },
  rpeTextActive: {
    color: RunnerColors.background,
  },
  controlsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three - 2,
  },
  prevButton: {
    width: 56,
    height: 56,
    borderRadius: 28,
    borderWidth: 1.5,
    borderColor: RunnerColors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  prevLabel: {
    color: RunnerColors.textSecondary,
  },
  logButton: {
    flex: 1,
    height: 64,
    borderRadius: 20,
    backgroundColor: RunnerColors.accent,
    alignItems: 'center',
    justifyContent: 'center',
  },
  logButtonLabel: {
    color: RunnerColors.background,
  },
});
