import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { ThemedText } from '@/components/themed-text';
import { SessionNextCard } from '@/components/session-next-card';
import { SessionNumberPad } from '@/components/session-number-pad';
import { SessionSetCount } from '@/components/session-set-count';
import { RunnerColors, Spacing } from '@/constants/theme';
import { formatPreviousSet } from '@/domain/format';
import { fromDisplayWeight, toDisplayWeight, weightStep } from '@/domain/units';
import type { RestPreview } from '@/hooks/use-session-runner';
import { useUnitSystem } from '@/state/preferences-store';
import type { PreviousSet } from '@/state/selectors/records';

const RPE_OPTIONS = [7, 8, 9];

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
  /** Names the action button honestly: back-to-back sets (rest_sec: 0) have no rest step to promise. */
  restFollows: boolean;
  /** This set number, last time this exercise was trained. Null on a first-ever session. */
  previousSet?: PreviousSet | null;
  /** True while what's on screen would beat everything ever logged for this exercise. */
  beatsPersonalBest?: boolean;
  onAdoptPrevious?: () => void;
  /** False inside a circuit — see SessionSetCount. */
  canAddSet?: boolean;
  canDropSet?: boolean;
  canSwapExercise?: boolean;
  onAddSet?: () => void;
  onDropSet?: () => void;
  onSwapExercise?: () => void;
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
  restFollows,
  previousSet,
  beatsPersonalBest,
  onAdoptPrevious,
  canAddSet,
  canDropSet,
  canSwapExercise,
  onAddSet,
  onDropSet,
  onSwapExercise,
  onPrev,
  onLogSet,
}: Props) {
  const [editing, setEditing] = useState<'reps' | 'load' | null>(null);
  const { t } = useTranslation();
  const unitSystem = useUnitSystem();

  // The runner works in kilograms end to end — that's what gets logged — so the conversion lives here,
  // at the one place a load is shown or typed. Stepping in *display* units is the point: +5 lb has to
  // land on 5 lb, not on the 5.51 lb that converting a 2.5 kg step would produce.
  const displayWeight = toDisplayWeight(weightKg, unitSystem);
  const step = weightStep(unitSystem);
  const setDisplayWeight = (value: number) => onChangeWeightKg(fromDisplayWeight(Math.max(0, value), unitSystem));
  const unitLabel = t(unitSystem === 'imperial' ? 'units.lb' : 'units.kg');
  const spokenUnit = t(unitSystem === 'imperial' ? 'units.lbSpoken' : 'units.kgSpoken');

  // Last time's load, in kilograms as logged — converted for display and for the spoken label below,
  // never converted on the way back into the log. Absent means that set was bodyweight.
  const previousLoadKg = previousSet?.kind === 'reps' ? previousSet.weightKg : undefined;
  const previousLabel =
    previousSet?.kind === 'reps'
      ? formatPreviousSet({
          kind: 'reps',
          reps: previousSet.reps,
          weight: previousLoadKg === undefined ? undefined : `${toDisplayWeight(previousLoadKg, unitSystem)} ${unitLabel}`,
        })
      : null;

  return (
    <View style={styles.container}>
      <View style={styles.top}>
        <View style={styles.repsPill}>
          <ThemedText type="code" style={styles.repsPillLabel}>
            {t('session.reps.pillLabel')}
          </ThemedText>
        </View>
        <ThemedText type="subtitle" style={styles.exerciseName}>
          {exerciseName}
        </ThemedText>
        <SessionSetCount
          label={t('session.reps.setLabel', {
            index: setIndex,
            total: setTotal,
            target: targetRepsMax ? `${targetReps}–${targetRepsMax}` : targetReps,
          })}
          canAdd={canAddSet}
          canDrop={canDropSet}
          canSwap={canSwapExercise}
          onAdd={onAddSet}
          onDrop={onDropSet}
          onSwap={onSwapExercise}
        />
        {/*
          Capped at two lines. The note is the author's own and stays exactly as written — it is not
          summarised or moved off the screen — but it sits in the band directly under the exercise
          name, where the eye lands, and it is re-read on every set and every minute of an EMOM. An
          unbounded one pushes the thing you are counting down the screen for the whole block. Two
          lines is the reminder; the full text lives in the exercise, one tap away.
        */}
        {notes && (
          <ThemedText type="small" style={styles.notes} numberOfLines={2}>
            {notes}
          </ThemedText>
        )}
        {/*
          The line the whole feature exists for: load used to be seeded from the library target and
          carried only within a session, so progressive overload meant hand-editing exercises.yaml
          between workouts. Absent entirely on a first-ever session rather than showing an empty
          placeholder — there is nothing to say yet.

          Pressable only when there is a load to adopt: a bodyweight set has no weight to take, and a
          control that does nothing is worse than no control.
        */}
        {previousSet && (
          <View style={styles.previousRow}>
            {previousLoadKg !== undefined ? (
              <Pressable
                onPress={onAdoptPrevious}
                accessibilityRole="button"
                // The visible text is a value, not an action, so it can't name this control itself.
                accessibilityLabel={t('session.reps.adoptPrevious', {
                  weight: toDisplayWeight(previousLoadKg, unitSystem),
                  unit: spokenUnit,
                })}
                style={styles.previousButton}>
                <ThemedText type="small" style={styles.previousLabel}>
                  {previousLabel}
                </ThemedText>
              </Pressable>
            ) : (
              <ThemedText type="small" style={[styles.previousLabel, styles.previousStatic]}>
                {previousLabel}
              </ThemedText>
            )}
            {beatsPersonalBest && (
              <View style={styles.bestPill}>
                <ThemedText type="code" style={styles.bestPillLabel}>
                  {t('session.complete.recordBadge')}
                </ThemedText>
              </View>
            )}
          </View>
        )}
      </View>

      <View style={styles.middle}>
        <View style={styles.stepperRow}>
          <Pressable
            onPress={() => onChangeReps(Math.max(0, reps - 1))}
            accessibilityRole="button"
            accessibilityLabel={t('common.decrease', { label: t('session.reps.repsLabel') })}
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
            accessibilityLabel={t('session.reps.repsDoneAccessibility', { count: reps })}
            style={styles.repsDisplay}>
            <ThemedText type="numeral" maxFontSizeMultiplier={1.3} style={styles.numeral}>
              {reps}
            </ThemedText>
            <ThemedText type="code" style={styles.repsLabel}>
              {t('session.reps.repsDoneCaps')}
            </ThemedText>
          </Pressable>
          <Pressable
            onPress={() => onChangeReps(reps + 1)}
            accessibilityRole="button"
            accessibilityLabel={t('common.increase', { label: t('session.reps.repsLabel') })}
            style={styles.stepperButton}>
            {/*
              Same treatment as the minus opposite it. It used to be accent-filled while that one was
              a plain outline, which made a matched pair of nudges look like a primary action and its
              afterthought — and put a second accent fill on the screen, competing with the log-set
              button that actually ends the set. They do the same size of thing in opposite
              directions, so they look the same.
            */}
            <ThemedText type="title" style={styles.stepperGlyph}>
              +
            </ThemedText>
          </Pressable>
        </View>

        <View style={styles.statsRow}>
          <View style={styles.statCard}>
            <ThemedText type="code" style={styles.statCardLabel}>
              {t('session.reps.loadCaps')}
            </ThemedText>
            {/*
              This was a hardcoded "BW +0 kg" literal with no input, so no weight was ever captured and
              every logged set looked like bodyweight. Bottoms out at 0, which reads and logs as
              bodyweight rather than a 0 kg load.
            */}
            <View style={styles.loadRow}>
              <Pressable
                onPress={() => setDisplayWeight(displayWeight - step)}
                hitSlop={8}
                accessibilityRole="button"
                accessibilityLabel={t('session.reps.decreaseLoad')}
                style={styles.loadButton}>
                <ThemedText type="heading" style={styles.loadButtonGlyph}>
                  −
                </ThemedText>
              </Pressable>
              <Pressable
                onPress={() => setEditing('load')}
                accessibilityRole="button"
                accessibilityLabel={
                  weightKg > 0
                    ? t('session.reps.loadAccessibilityWeighted', { weight: displayWeight, unit: spokenUnit })
                    : t('session.reps.loadAccessibilityBodyweight')
                }>
                <ThemedText type="heading" style={styles.loadValue}>
                  {weightKg > 0 ? (
                    <>
                      {displayWeight}
                      <ThemedText style={styles.loadUnit}> {unitLabel}</ThemedText>
                    </>
                  ) : (
                    t('session.reps.bodyweightAbbr')
                  )}
                </ThemedText>
              </Pressable>
              <Pressable
                onPress={() => setDisplayWeight(displayWeight + step)}
                hitSlop={8}
                accessibilityRole="button"
                accessibilityLabel={t('session.reps.increaseLoad')}
                style={styles.loadButton}>
                <ThemedText type="heading" style={styles.loadButtonGlyph}>
                  +
                </ThemedText>
              </Pressable>
            </View>
          </View>
          <View style={styles.statCard}>
            <ThemedText type="code" style={styles.statCardLabel}>
              {t('session.reps.rpe')}
            </ThemedText>
            <View style={styles.rpeRow}>
              {RPE_OPTIONS.map((option) => {
                const active = option === rpe;
                return (
                  <Pressable
                    key={option}
                    onPress={() => onChangeRpe(option)}
                    accessibilityRole="button"
                    accessibilityLabel={t('session.reps.rpeOption', { value: option })}
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
        <Pressable onPress={onPrev} accessibilityRole="button" style={styles.prevButton}>
          <ThemedText type="code" style={styles.prevLabel}>
            {t('session.prev')}
          </ThemedText>
        </Pressable>
        <Pressable onPress={onLogSet} accessibilityRole="button" style={styles.logButton}>
          <ThemedText type="heading" style={styles.logButtonLabel}>
            {t(restFollows ? 'session.reps.logSet' : 'session.reps.logSetNoRest')}
          </ThemedText>
        </Pressable>
      </View>

      {editing === 'reps' && (
        <SessionNumberPad
          label={t('session.reps.repsDone')}
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
          label={t('session.reps.loadLabel')}
          initialValue={displayWeight}
          unit={unitLabel}
          allowDecimal
          onCancel={() => setEditing(null)}
          onConfirm={(value) => {
            setDisplayWeight(value);
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
  previousRow: {
    marginTop: Spacing.two,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  // minHeight rather than height, so the target survives a raised accessibility text size — and
  // justifyContent so the label stays centred in the taller box it makes.
  previousButton: {
    minHeight: 44,
    justifyContent: 'center',
    paddingRight: Spacing.two,
  },
  previousLabel: {
    color: RunnerColors.textSecondary,
  },
  // The non-pressable variant carries the row's height instead, so a bodyweight set doesn't make the
  // header jump relative to a loaded one.
  previousStatic: {
    minHeight: 44,
    lineHeight: 44,
  },
  // The calm accent, matching the record pill on session-complete.tsx — both tokens carry measured
  // ratios against RunnerColors.background (constants/theme.ts), and the warm accent is already spoken
  // for by the log button. The "PR" text is what actually carries the meaning; colour only supports it.
  bestPill: {
    backgroundColor: RunnerColors.accentCalmSoft,
    borderWidth: 1,
    borderColor: 'rgba(63,130,192,0.4)',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 999,
  },
  bestPillLabel: {
    color: RunnerColors.accentCalmOnSoft,
    letterSpacing: 1.4,
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
  stepperGlyph: {
    fontSize: 28,
    color: RunnerColors.textSecondary,
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
    textAlign: 'center',
    color: RunnerColors.textSecondary,
  },
  logButton: {
    flex: 1,
    minHeight: 64,
    paddingVertical: Spacing.one,
    paddingHorizontal: Spacing.two,
    borderRadius: 20,
    backgroundColor: RunnerColors.accent,
    alignItems: 'center',
    justifyContent: 'center',
  },
  // `textAlign` matters only once the label wraps, which is exactly when it's noticed: the parent's
  // `alignItems: 'center'` centers the *text block*, so a wrapped label centers its longest line and
  // left-aligns every other one against it. pt's "Registrar série → Descanso" wraps on any device
  // with a raised font scale, and it read as a misaligned button rather than a long label. Every
  // runner button label carries this for the same reason — a translation only has to get one word
  // longer to hit it.
  logButtonLabel: {
    textAlign: 'center',
    color: RunnerColors.background,
  },
});
