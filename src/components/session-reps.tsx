import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { SessionNextCard } from '@/components/session-next-card';
import { SessionNumberPad } from '@/components/session-number-pad';
import { RunnerColors, Spacing } from '@/constants/theme';
import type { RestPreview } from '@/hooks/use-session-runner';

const RPE_OPTIONS = [7, 8, 9];
/**
 * 2.5kg because it's the one increment that serves both ends of a generalist app: it's the smallest
 * real jump on a bar or belt (a pair of 1.25kg plates), and metric dumbbell racks are commonly spaced
 * the same way (2.5, 5, 7.5, 10…). 1kg suits neither — you can't load it on a bar without uncommon
 * 0.5kg plates, and racks rarely step that finely. Going finer would also make the stepper's tap count
 * worse, which is the wrong direction: precision is meant to come from direct entry (see the
 * implementation plan), leaving this control for coarse adjustment.
 */
const WEIGHT_STEP_KG = 2.5;

type Props = {
  exerciseName: string;
  setIndex: number;
  setTotal: number;
  targetReps: number;
  targetRepsMax?: number;
  reps: number;
  onChangeReps: (reps: number) => void;
  rpe: number;
  onChangeRpe: (rpe: number) => void;
  weightKg: number;
  onChangeWeightKg: (weightKg: number) => void;
  notes?: string;
  next: RestPreview;
  onPrev: () => void;
  onLogSet: () => void;
};

export function SessionReps({
  exerciseName,
  setIndex,
  setTotal,
  targetReps,
  targetRepsMax,
  reps,
  onChangeReps,
  rpe,
  onChangeRpe,
  weightKg,
  onChangeWeightKg,
  notes,
  next,
  onPrev,
  onLogSet,
}: Props) {
  const [editing, setEditing] = useState<'reps' | 'load' | null>(null);

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
          Set {setIndex} of {setTotal} · target {targetRepsMax ? `${targetReps}–${targetRepsMax}` : targetReps}
        </ThemedText>
        {notes && (
          <ThemedText type="small" style={styles.notes}>
            {notes}
          </ThemedText>
        )}
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
          {/* The numeral doubles as the way in to exact entry — a 30-rep set is 30 taps on the
              stepper otherwise, mid-workout. The steppers stay for small adjustments. */}
          <Pressable
            onPress={() => setEditing('reps')}
            accessibilityRole="button"
            accessibilityLabel={`Reps done: ${reps}. Tap to enter an exact value.`}
            style={styles.repsDisplay}>
            <ThemedText type="numeral" style={styles.numeral}>
              {reps}
            </ThemedText>
            <ThemedText type="code" style={styles.repsLabel}>
              REPS DONE
            </ThemedText>
          </Pressable>
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
            {/*
              This was a hardcoded "BW +0 kg" literal with no input, so no weight was ever captured and
              every logged set looked like bodyweight. Bottoms out at 0, which reads and logs as
              bodyweight rather than a 0 kg load.
            */}
            <View style={styles.loadRow}>
              <Pressable
                onPress={() => onChangeWeightKg(Math.max(0, weightKg - WEIGHT_STEP_KG))}
                hitSlop={8}
                accessibilityRole="button"
                accessibilityLabel="Decrease load"
                style={styles.loadButton}>
                <ThemedText type="heading" style={styles.loadButtonGlyph}>
                  −
                </ThemedText>
              </Pressable>
              <Pressable
                onPress={() => setEditing('load')}
                accessibilityRole="button"
                accessibilityLabel={`Load: ${weightKg > 0 ? `${weightKg} kilograms` : 'bodyweight'}. Tap to enter an exact value.`}>
                <ThemedText type="heading" style={styles.loadValue}>
                  {weightKg > 0 ? (
                    <>
                      {weightKg}
                      <ThemedText style={styles.loadUnit}> kg</ThemedText>
                    </>
                  ) : (
                    'BW'
                  )}
                </ThemedText>
              </Pressable>
              <Pressable
                onPress={() => onChangeWeightKg(weightKg + WEIGHT_STEP_KG)}
                hitSlop={8}
                accessibilityRole="button"
                accessibilityLabel="Increase load"
                style={styles.loadButton}>
                <ThemedText type="heading" style={styles.loadButtonGlyph}>
                  +
                </ThemedText>
              </Pressable>
            </View>
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
                    accessibilityRole="button"
                    accessibilityLabel={`RPE ${option}`}
                    accessibilityState={{ selected: active }}
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

      <SessionNextCard next={next} />

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

      {editing === 'reps' && (
        <SessionNumberPad
          label="Reps done"
          initialValue={reps}
          onCancel={() => setEditing(null)}
          onConfirm={(value) => {
            // Reps are whole; the pad has no decimal key here, but a pasted or malformed value
            // shouldn't reach the session log as a fraction.
            onChangeReps(Math.round(value));
            setEditing(null);
          }}
        />
      )}

      {editing === 'load' && (
        <SessionNumberPad
          label="Load"
          initialValue={weightKg}
          unit="kg"
          allowDecimal
          onCancel={() => setEditing(null)}
          onConfirm={(value) => {
            onChangeWeightKg(value);
            setEditing(null);
          }}
        />
      )}
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
    color: RunnerColors.accentOnSoft,
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
  notes: {
    marginTop: 4,
    color: RunnerColors.textSecondary,
    fontStyle: 'italic',
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
  loadRow: {
    marginTop: 2,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.one,
  },
  loadValue: {
    color: RunnerColors.text,
  },
  loadUnit: {
    fontSize: 15,
    color: RunnerColors.textSecondary,
  },
  loadButton: {
    width: 30,
    height: 30,
    borderRadius: 15,
    borderWidth: 1,
    borderColor: RunnerColors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  loadButtonGlyph: {
    color: RunnerColors.textSecondary,
    lineHeight: 24,
  },
  rpeRow: {
    flexDirection: 'row',
    gap: 6,
    marginTop: 6,
  },
  // Was ~26px tall on 13px text — the smallest touch target in the app, and in the live runner where
  // you're least precise. minHeight rather than height so it still grows with accessibility text sizes.
  rpePill: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 44,
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
    marginTop: Spacing.three - 2,
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
