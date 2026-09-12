import { router, useLocalSearchParams } from 'expo-router';
import type { ErrorBoundaryProps } from 'expo-router';
import { usePreventRemove } from 'expo-router/react-navigation';
import { useKeepAwake } from 'expo-keep-awake';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Platform, Pressable, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';
import Svg, { Path } from 'react-native-svg';

import { ErrorFallback } from '@/components/error-fallback';
import { SessionComplete } from '@/components/session-complete';
import { SessionCountdown } from '@/components/session-countdown';
import { SessionExercisePicker } from '@/components/session-exercise-picker';
import { SessionHold } from '@/components/session-hold';
import { SessionInterval } from '@/components/session-interval';
import { SessionProgressBar } from '@/components/session-progress';
import { SessionReps } from '@/components/session-reps';
import { SessionRest } from '@/components/session-rest';
import { SessionUpcoming } from '@/components/session-upcoming';
import { ThemedText } from '@/components/themed-text';
import { RunnerColors, MaxContentWidth, Spacing } from '@/constants/theme';
import { formatSessionName } from '@/domain/format';
import { findProgramWeek, resolveWorkoutForWeek } from '@/domain/program';
import type { Exercise, Session, Workout } from '@/domain/types';
import { useSessionAnnouncements } from '@/hooks/use-session-announcements';
import { buildStepsWithLimits, useSessionRunner } from '@/hooks/use-session-runner';
import { useLibraryStore } from '@/state/library-store';
import { sessionRecords } from '@/state/selectors/records';
import { useSessionHistoryStore } from '@/state/session-history-store';

/**
 * The one screen where a render throw costs data, so its boundary does more than apologise.
 *
 * Every logged set reaches the session file as it happens (§7.2), so all of them survive a throw — but
 * the runner writes `ended_at` only when the whole workout does, and a session without it counts as
 * zero minutes in every stat tile and is skipped outright by `exerciseHistory`. Those entries would sit
 * on disk and appear nowhere in the app. React has already unmounted the runner and the ref holding
 * that `Session` by the time this renders, which is why the id is tracked in the store rather than
 * passed down here.
 *
 * What this *can't* salvage, and the copy is careful not to claim: the set being performed right now,
 * which has no logged value until the user advances off it.
 *
 * Deliberately no retry, unlike the shared boundaries: re-rendering this route restarts it from the
 * countdown, and mounting the runner again calls `startSession` — a second session file for one
 * workout, with the first one's sets stranded in it. "Go to History" instead, where the salvaged
 * session is the top row.
 */
export function ErrorBoundary({ error }: ErrorBoundaryProps) {
  const { t } = useTranslation();
  const abandonActiveSession = useSessionHistoryStore((state) => state.abandonActiveSession);

  useEffect(() => {
    abandonActiveSession();
  }, [abandonActiveSession]);

  return (
    <ErrorFallback
      title={t('errorBoundary.session.title')}
      body={t('errorBoundary.session.body')}
      error={error}
      primary={{ label: t('errorBoundary.session.toHistory'), onPress: () => router.dismissTo('/history') }}
    />
  );
}

export default function SessionScreen() {
  // suppressDeactivateWarnings is the library's own escape hatch for this exact race, and without it
  // leaving this screen always threw. useKeepAwake's cleanup calls deactivateKeepAwake() with no
  // .catch() unless the flag is set; on web, activate() only records the tag *after* awaiting
  // navigator.wakeLock.request(), so any unmount that beats that promise — which is every unmount
  // here, since finishing/closing navigates immediately — leaves deactivate with no tag to release
  // and it throws ERR_KEEP_AWAKE_TAG_INVALID ("The wake lock with tag _r_N_ has not activated yet")
  // as an unhandled rejection. The flag makes that path .catch(() => {}) instead. Nothing is leaked:
  // the browser releases a screen wake lock on its own when the page/tab goes away.
  useKeepAwake(undefined, { suppressDeactivateWarnings: true });
  const { t } = useTranslation();
  // The finished session doubles as the "are we done" flag: the runner hands it over on the same call
  // that used to carry nothing, and it is what the completion screen reads to say what was beaten.
  // `null` is still a completion — a workout with no runnable steps never created a session file.
  const [completed, setCompleted] = useState<{ session: Session | null } | null>(null);
  // Doesn't navigate back directly — advance()/finishSession() in use-session-runner.ts already call
  // this the moment the workout is done (naturally or via "Finish"), and by then the session has
  // already saved. Showing a completion screen first (rather than snapping straight back to wherever
  // the session was started from) gives that moment somewhere to land instead of just vanishing.
  const onComplete = useCallback((session: Session | null) => setCompleted({ session }), []);
  const library = useLibraryStore((state) => state.library);
  const { workoutId, programId, week, day, adhoc } = useLocalSearchParams<{
    workoutId?: string;
    programId?: string;
    week?: string;
    day?: string;
    adhoc?: string;
  }>();
  // An ad-hoc session: no workout, and a step list built as the user goes. Checked before every
  // workout lookup below, since there is nothing to look up.
  const isAdHoc = adhoc === '1';
  const [started, setStarted] = useState(false);
  /** Whether the "this workout will stop early" screen has been dismissed — see the guard below. */
  const [acknowledgedLength, setAcknowledgedLength] = useState(false);

  const resolved = useMemo(() => {
    if (!library) return null;
    if (isAdHoc) {
      return { workout: null, exercises: library.exercises, programId: null, week: null, day: null };
    }
    if (workoutId) {
      const found = library.workouts.find((candidate) => candidate.id === workoutId);
      return found ? { workout: found, exercises: library.exercises, programId: null, week: null, day: null } : null;
    }
    if (programId && week) {
      const program = library.programs.find((candidate) => candidate.id === programId);
      const weekNumber = Number(week);
      if (!program || Number.isNaN(weekNumber)) return null;
      const resolvedWeek = resolveWorkoutForWeek(program, weekNumber, library, day);
      return resolvedWeek ? { ...resolvedWeek, programId, week: weekNumber, day: day ?? null } : null;
    }
    return { workout: library.workouts[0], exercises: library.exercises, programId: null, week: null, day: null };
  }, [library, isAdHoc, workoutId, programId, week, day]);

  // Asked separately from `resolved`, which collapses "rest day" and "no such week" into the same
  // null — see the note on resolveWorkoutForWeek.
  const isRestWeek = useMemo(() => {
    if (!library || isAdHoc || workoutId || !programId || !week) return false;
    const program = library.programs.find((candidate) => candidate.id === programId);
    const weekNumber = Number(week);
    if (!program || Number.isNaN(weekNumber)) return false;
    return findProgramWeek(program, weekNumber, day)?.restDay === true;
  }, [library, isAdHoc, workoutId, programId, week, day]);

  const workout = resolved?.workout ?? null;
  // resolved is already memoized (stable reference unless its own deps change), so memoizing off it
  // rather than `resolved?.exercises ?? []` directly keeps `exercises` from getting a fresh array
  // identity every render while resolved is null (before library loads).
  const exercises = useMemo(() => resolved?.exercises ?? [], [resolved]);
  // Computed unconditionally (hooks can't follow the early returns below) so a workout that resolves
  // to zero runnable steps — no blocks, or every block's exercise has sets/rounds/minutes at 0 — can be
  // caught before ever showing the pre-session countdown, instead of leaving the user on a blank screen
  // with nothing to tap once the countdown finishes.
  const built = useMemo(
    () => (workout ? buildStepsWithLimits(workout, exercises) : { steps: [], truncated: false }),
    [workout, exercises],
  );
  const steps = built.steps;

  // A rest week resolves to null exactly like a broken reference does, but it isn't broken — it's the
  // program saying today runs nothing. Reachable by deep link, or by navigating back to a week that
  // has since been edited into a rest day; without this it renders as a blank screen.
  if (isRestWeek) {
    return (
      <SafeAreaView style={styles.safeArea} edges={['top', 'bottom', 'left', 'right']}>
        <View style={styles.content}>
          <View style={styles.emptyState}>
            <ThemedText type="subtitle" style={styles.emptyStateTitle}>
              {t('session.restDay.title')}
            </ThemedText>
            <ThemedText type="small" style={styles.emptyStateBody}>
              {t('session.restDay.body')}
            </ThemedText>
            <Pressable
              onPress={() => router.back()}
              style={styles.emptyStateButton}
              accessibilityRole="button"
              accessibilityLabel={t('common.close')}>
              <ThemedText type="heading" style={styles.emptyStateButtonLabel}>
                {t('common.close')}
              </ThemedText>
            </Pressable>
          </View>
        </View>
      </SafeAreaView>
    );
  }

  // Still hydrating, or a workout id that resolved to nothing. An ad-hoc session legitimately has no
  // workout, so it is exempt from both this and the zero-step guard below — an empty step list is its
  // *starting* state rather than an error.
  if (!resolved) return null;

  if (!isAdHoc && workout && steps.length === 0) {
    return (
      <SafeAreaView style={styles.safeArea} edges={['top', 'bottom', 'left', 'right']}>
        <View style={styles.content}>
          <View style={styles.emptyState}>
            <ThemedText type="subtitle" style={styles.emptyStateTitle}>
              {t('session.nothingToRun.title')}
            </ThemedText>
            <ThemedText type="small" style={styles.emptyStateBody}>
              {t('session.nothingToRun.body', { name: workout.name })}
            </ThemedText>
            <Pressable
              onPress={() => router.back()}
              style={styles.emptyStateButton}
              accessibilityRole="button"
              accessibilityLabel={t('common.close')}>
              <ThemedText type="heading" style={styles.emptyStateButtonLabel}>
                {t('common.close')}
              </ThemedText>
            </Pressable>
          </View>
        </View>
      </SafeAreaView>
    );
  }

  /**
   * The step list is capped (see MaxStepsPerWorkout), and a workout over the cap stops partway and is
   * written to the log as a finished session. Said *before* the countdown, and with a control, rather
   * than as a banner beside it: the countdown auto-advances in three seconds, so a banner there is
   * gone before it is read and invisible to a screen reader, and this is the last moment where the
   * answer is still "run something shorter" instead of "your log is wrong".
   *
   * Not a hard stop — running most of a workout is a legitimate thing to want, and refusing would take
   * away the only option left. It just cannot be the silent default.
   */
  if (!started && !acknowledgedLength && built.truncated) {
    return (
      <SafeAreaView style={styles.safeArea} edges={['top', 'bottom', 'left', 'right']}>
        <View style={styles.content}>
          <View style={styles.emptyState}>
            <ThemedText type="subtitle" style={styles.emptyStateTitle} accessibilityRole="header">
              {t('session.tooLong.title')}
            </ThemedText>
            <ThemedText type="small" style={styles.emptyStateBody}>
              {t('session.tooLong.body', { name: workout?.name ?? '' })}
            </ThemedText>
            <Pressable
              onPress={() => setAcknowledgedLength(true)}
              style={styles.emptyStateButton}
              accessibilityRole="button">
              <ThemedText type="heading" style={styles.emptyStateButtonLabel}>
                {t('session.tooLong.startAnyway')}
              </ThemedText>
            </Pressable>
            <Pressable onPress={() => router.back()} style={styles.tooLongBack} accessibilityRole="button">
              <ThemedText type="small" style={styles.emptyStateBody}>
                {t('common.close')}
              </ThemedText>
            </Pressable>
          </View>
        </View>
      </SafeAreaView>
    );
  }

  if (!started) {
    return (
      <SafeAreaView style={styles.safeArea} edges={['top', 'bottom', 'left', 'right']}>
        <View style={styles.content}>
          <SessionCountdown workoutName={formatSessionName(workout?.name ?? null)} onDone={() => setStarted(true)} />
        </View>
      </SafeAreaView>
    );
  }

  if (completed) {
    return (
      <SafeAreaView style={styles.safeArea} edges={['top', 'bottom', 'left', 'right']}>
        <View style={styles.content}>
          <CompletedSession
            workoutName={formatSessionName(workout?.name ?? null)}
            session={completed.session}
            onDone={() => router.back()}
          />
        </View>
      </SafeAreaView>
    );
  }

  return (
    <ActiveSession
      workout={workout}
      exercises={exercises}
      programId={resolved?.programId ?? null}
      programWeek={resolved?.week ?? null}
      programDay={resolved?.day ?? null}
      onComplete={onComplete}
    />
  );
}

/**
 * Its own component so the session log is subscribed to *after* the workout, not during it. Reading
 * `sessions` up in SessionScreen would re-render the whole runner on every logged set, since each one
 * writes through the store (`persistMember`) — the cost the runner's refs exist to avoid.
 *
 * The just-finished session is excluded from its own comparison inside `sessionRecords`; it's passed
 * separately rather than filtered here so that rule lives with the rule about ties and first entries.
 */
function CompletedSession({
  workoutName,
  session,
  onDone,
}: {
  workoutName: string;
  session: Session | null;
  onDone: () => void;
}) {
  const library = useLibraryStore((state) => state.library);
  const sessions = useSessionHistoryStore((state) => state.sessions);
  // Set by `completeSession`, so it describes the backup this very session triggered. Here rather
  // than mid-workout for the same reason a full disk isn't announced between two sets: the person was
  // holding a plank, and this is the first moment where telling them costs nothing.
  const backupFailed = useSessionHistoryStore((state) => state.backupFailure !== null);
  const records = useMemo(
    () => (session ? sessionRecords(session, sessions, library?.exercises ?? []) : []),
    [session, sessions, library],
  );

  return <SessionComplete workoutName={workoutName} records={records} backupFailed={backupFailed} onDone={onDone} />;
}

function ActiveSession({
  workout,
  exercises,
  programId,
  programWeek,
  programDay,
  onComplete,
}: {
  workout: Workout | null;
  exercises: Exercise[];
  programId: string | null;
  programWeek: number | null;
  programDay: string | null;
  onComplete: (session: Session | null) => void;
}) {
  const runner = useSessionRunner(workout, exercises, programId, programWeek, programDay, onComplete);
  const { t } = useTranslation();
  // Here rather than inside the two set screens: both offer the control, and which exercise is being
  // substituted is a fact about the session, not about the row that happens to be on screen.
  //
  // Held as the index the picker was opened on, and cleared when the step moves, the same way as the
  // Coming up sheet below. A plain boolean outlived its step: a hold keeps timing under the picker, and
  // when it ended by itself the picker vanished over the rest (which offers no swap) and then reopened
  // by itself on the next set, over a hold that was already counting.
  const [swappingAt, setSwappingAt] = useState<number | null>(null);
  if (swappingAt !== null && swappingAt !== runner.stepIndex) setSwappingAt(null);
  const swapping = swappingAt === runner.stepIndex;
  const [adding, setAdding] = useState(false);
  /**
   * The Coming up sheet is open while this equals the step on screen, so it closes itself the moment
   * the step changes. The index alone is enough here, unlike the runner's per-step reset: the two
   * mutations that keep it still (swap, ad-hoc add) start from controls the sheet covers.
   *
   * Cleared in the render that sees the index move (adjusting state during render, as the runner's
   * reset does), not merely left to stop matching: a stale index would reopen the sheet the moment
   * Prev stepped back onto it.
   */
  const [upcomingOpenAt, setUpcomingOpenAt] = useState<number | null>(null);
  if (upcomingOpenAt !== null && upcomingOpenAt !== runner.stepIndex) setUpcomingOpenAt(null);
  const upcomingOpen = upcomingOpenAt === runner.stepIndex;
  // Stable, because the sheet re-subscribes to the back button whenever this changes.
  const closeUpcoming = useCallback(() => setUpcomingOpenAt(null), []);
  // `rest` is a built-in pseudo-exercise rather than something the user wrote — the Library tab
  // excludes it from its own count for the same reason.
  const addCandidates = useMemo(() => exercises.filter((exercise) => exercise.type !== 'rest'), [exercises]);

  /**
   * One sentence per step, rebuilt only when the step itself changes — the hook dedupes, but keeping
   * this out of the tick path means the string isn't reassembled sixty times a minute either.
   * Names come from the user's library, so they're interpolated rather than translated.
   */
  const announcement = useMemo(() => {
    const current = runner.step;
    if (!current) return null;
    switch (current.kind) {
      case 'hold': {
        // The end, not the minimum: it's when the set will actually stop, which is the thing you
        // can't see coming without sight. A max-effort hold has no end to announce.
        const spoken = { name: current.exerciseName, index: current.setIndex, total: current.setTotal };
        return current.holdEndSec === undefined
          ? t('session.announce.holdOpen', spoken)
          : t('session.announce.hold', { ...spoken, target: current.holdEndSec });
      }
      case 'reps':
        return t('session.announce.reps', {
          name: current.exerciseName,
          index: current.setIndex,
          total: current.setTotal,
          target: current.targetReps,
        });
      case 'interval':
        return t('session.announce.interval', {
          name: current.exerciseName,
          index: current.setIndex,
          total: current.setTotal,
          seconds: current.targetSec,
        });
      case 'rest':
        return t('session.announce.rest', { seconds: current.seconds });
    }
  }, [runner.step, t]);

  useSessionAnnouncements(announcement);
  const { step } = runner;

  const confirmFinish = useCallback(() => {
    Alert.alert(t('session.finish.confirmTitle'), t('session.finish.confirmBody'), [
      { text: t('common.cancel'), style: 'cancel' },
      { text: t('session.finish.confirmAction'), style: 'destructive', onPress: runner.finishSession },
    ]);
  }, [runner.finishSession, t]);

  /**
   * Back and swipe-down ask before leaving. Unguarded, both dismissed the route with the session still
   * open, so the session never got an `endedAt`. They get the Finish control's own dialog, which asks the
   * same question and has the same outcome. Anything open on top closes first, since that is what back
   * means there.
   *
   * One hook covers both gestures: native-stack turns a prevented route into `preventNativeDismiss` on
   * iOS, and Android's back already runs through JS. On Android the Coming up sheet's `BackHandler`
   * still takes back first, having subscribed after the navigator did; the check below is for iOS's
   * swipe, which never reaches that handler.
   *
   * Off on web, where `Alert` is a no-op: a guard there would swallow the browser's back with no dialog
   * to answer. The runner's unmount stamps the session ended instead.
   */
  usePreventRemove(Platform.OS !== 'web', () => {
    if (upcomingOpen) closeUpcoming();
    else if (adding) setAdding(false);
    else if (swapping) setSwappingAt(null);
    else confirmFinish();
  });

  /**
   * An ad-hoc session parks here whenever it runs out of steps — at the start, and again after each
   * exercise finishes. A pre-built workout never reaches this: it completes instead.
   */
  if (!step) {
    if (!runner.isAdHoc) return null;
    return (
      <SafeAreaView style={styles.safeArea} edges={['top', 'bottom', 'left', 'right']}>
        {/* Hidden while the picker is open, so a screen reader stays inside it. The picker renders
            beside this view rather than in it, so hiding this leaves the picker reachable. */}
        <View style={styles.content} aria-hidden={adding}>
          <View style={styles.emptyState}>
            <ThemedText type="subtitle" style={styles.emptyStateTitle}>
              {t('session.adhoc.title')}
            </ThemedText>
            <ThemedText type="small" style={styles.emptyStateBody}>
              {t('session.adhoc.body')}
            </ThemedText>
            <Pressable onPress={() => setAdding(true)} style={styles.emptyStateButton} accessibilityRole="button">
              <ThemedText type="heading" style={styles.emptyStateButtonLabel}>
                {t('session.adhoc.addExercise')}
              </ThemedText>
            </Pressable>
            <Pressable onPress={runner.finishSession} accessibilityRole="button" style={styles.adhocFinish}>
              <ThemedText type="code" style={styles.finishLabel}>
                {t('session.finish.label')}
              </ThemedText>
            </Pressable>
          </View>
        </View>
        {adding && (
          <SessionExercisePicker
            candidates={addCandidates}
            onCancel={() => setAdding(false)}
            onSelect={(exerciseId) => {
              runner.addExercise(exerciseId);
              setAdding(false);
            }}
          />
        )}
      </SafeAreaView>
    );
  }

  /**
   * Whether the swap picker is actually on screen. It's the render condition and the aria-hidden
   * condition both, deliberately, so the two can't disagree: hiding the runner behind a picker that
   * isn't there would leave a screen reader with nothing to reach.
   */
  const swapPickerOpen = swapping && runner.canSwapExercise && (step.kind === 'reps' || step.kind === 'hold');

  return (
    <SafeAreaView style={styles.safeArea} edges={['top', 'bottom', 'left', 'right']}>
      {/* Hidden while the sheet is open, so a screen reader stays inside it. aria-hidden rather than
          the native props, which react-native-web drops; RN maps it onto both of them. */}
      <View style={styles.content} aria-hidden={upcomingOpen}>
        {/* The runner itself, hidden while a picker is open for the same reason. The pickers render
            inside `content`, so this is the sheet's aria-hidden one level down, with the pickers as
            its siblings rather than its children. */}
        <View style={styles.runnerBody} aria-hidden={adding || swapPickerOpen}>
          <View style={styles.header}>
            <View style={styles.headerRow}>
              {/*
              Opens the Coming up sheet. A chevron and nothing louder, since this header is read
              mid-set. Named by its own text, so it takes a hint rather than a label: Voice Control
              matches the visible name. hitSlop takes it to 44px without making the row taller.
            */}
              <Pressable
                onPress={() => setUpcomingOpenAt(runner.stepIndex)}
                hitSlop={{ top: 13, bottom: 13 }}
                accessibilityRole="button"
                accessibilityHint={t('session.upcoming.hint')}
                accessibilityState={{ expanded: upcomingOpen }}
                style={styles.workoutNameButton}>
                <ThemedText type="small" style={styles.workoutName} numberOfLines={1}>
                  {formatSessionName(runner.workoutName)}
                </ThemedText>
                <Svg width={12} height={12} viewBox="0 0 12 12" fill="none">
                  <Path
                    d="M3 4.5L6 7.5L9 4.5"
                    stroke={RunnerColors.textSecondary}
                    strokeWidth={1.5}
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </Svg>
              </Pressable>
              <View style={styles.headerRight}>
                <SessionProgressBar total={runner.blockTotal} activeIndex={runner.blockIndex} />
                {/* Ad-hoc only: a pre-built workout already knows what it contains. Here it is the only
                  way to queue anything, so it has to be reachable mid-step and not just at the end. */}
                {runner.isAdHoc && (
                  <Pressable
                    onPress={() => setAdding(true)}
                    hitSlop={8}
                    accessibilityRole="button"
                    accessibilityLabel={t('session.adhoc.addExercise')}>
                    <ThemedText type="code" style={styles.finishLabel}>
                      {t('session.adhoc.addShort')}
                    </ThemedText>
                  </Pressable>
                )}
                <Pressable
                  onPress={confirmFinish}
                  hitSlop={8}
                  accessibilityRole="button"
                  accessibilityLabel={t('session.finish.confirmTitle')}>
                  <ThemedText type="code" style={styles.finishLabel}>
                    {t('session.finish.label')}
                  </ThemedText>
                </Pressable>
              </View>
            </View>
            {/*
            The circuit crumb: a second breadcrumb line, present only inside a circuit. It repeats the
            row above's shape — label left, dot track right — because a circuit's steps are
            interleaved, so the block dots say which block and the exercise name says what you're
            doing, but neither said that two more rounds of this were coming.

            One accessibility node, since split it would announce a bare "circuit, round 2 of 3"
            followed by an unnamed progress bar duplicating the header's.
          */}
            {runner.circuit && (
              <View
                style={styles.headerRow}
                accessible
                accessibilityRole="text"
                accessibilityLabel={t('session.circuit.accessibility', {
                  index: runner.circuit.round,
                  total: runner.circuit.rounds,
                  member: runner.circuit.member,
                  memberTotal: runner.circuit.memberTotal,
                })}>
                <ThemedText type="code" style={styles.circuitLabel} numberOfLines={1}>
                  {t('session.circuit.crumb', { index: runner.circuit.round, total: runner.circuit.rounds })}
                </ThemedText>
                <View style={styles.headerRight}>
                  <SessionProgressBar
                    total={runner.circuit.memberTotal}
                    activeIndex={runner.circuit.member - 1}
                    tone="calm"
                  />
                  {/*
                  An invisible copy of the "Finish" label, purely to reserve its width so the two dot
                  tracks line up in a column instead of the lower one sliding under the control. Same
                  component and style as the real one, which is the point: a hardcoded width is wrong
                  in the next locale ("Encerrar") and at the next font scale, and measuring it with
                  onLayout costs a frame of visible misalignment on entry.

                  Never coexists with the ad-hoc ADD control beside it: an ad-hoc session has no
                  workout, so it has no blocks and therefore no circuit.

                  `aria-hidden` rather than `accessibilityElementsHidden`/`importantForAccessibility`,
                  which react-native-web drops (docs/verifying-in-the-browser.md) — a browser check
                  found the spacer still reachable, so the web build announced "Encerrar" twice. RN
                  maps `aria-hidden` onto both native equivalents.
                */}
                  <ThemedText type="code" style={[styles.finishLabel, styles.finishSpacer]} aria-hidden>
                    {t('session.finish.label')}
                  </ThemedText>
                </View>
              </View>
            )}
          </View>

          {step.kind === 'hold' && (
            <SessionHold
              exerciseName={step.exerciseName}
              setIndex={step.setIndex}
              setTotal={step.setTotal}
              targetSec={step.holdTargetSec}
              targetMaxSec={step.holdTargetMaxSec}
              elapsedSec={runner.holdElapsedSec}
              paused={runner.paused}
              notes={step.notes}
              next={runner.nextPreview}
              previousSet={runner.previousSet}
              beatsPersonalBest={runner.beatsPersonalBest}
              canAddSet={runner.canAddSet}
              canDropSet={runner.canDropSet}
              canSwapExercise={runner.canSwapExercise}
              onAddSet={runner.addSet}
              onDropSet={runner.dropSet}
              onSwapExercise={() => setSwappingAt(runner.stepIndex)}
              onTogglePause={runner.setPaused}
              onPrev={runner.goPrev}
              onDone={runner.doneSet}
            />
          )}

          {step.kind === 'reps' && (
            <SessionReps
              exerciseName={step.exerciseName}
              setIndex={step.setIndex}
              setTotal={step.setTotal}
              targetReps={step.targetReps}
              targetRepsMax={step.targetRepsMax}
              reps={runner.reps}
              onChangeReps={runner.setReps}
              rpe={runner.rpe}
              onChangeRpe={runner.setRpe}
              weightKg={runner.weightKg}
              onChangeWeightKg={runner.setWeightKg}
              notes={step.notes}
              next={runner.nextPreview}
              restFollows={runner.restFollows}
              previousSet={runner.previousSet}
              beatsPersonalBest={runner.beatsPersonalBest}
              onAdoptPrevious={runner.adoptPreviousLoad}
              canAddSet={runner.canAddSet}
              canDropSet={runner.canDropSet}
              canSwapExercise={runner.canSwapExercise}
              onAddSet={runner.addSet}
              onDropSet={runner.dropSet}
              onSwapExercise={() => setSwappingAt(runner.stepIndex)}
              onPrev={runner.goPrev}
              onLogSet={runner.logSet}
            />
          )}

          {step.kind === 'interval' && (
            <SessionInterval
              exerciseName={step.exerciseName}
              variant={step.variant}
              setIndex={step.setIndex}
              setTotal={step.setTotal}
              targetSec={step.targetSec}
              countUp={step.countUp}
              elapsedSec={runner.holdElapsedSec}
              remainingSec={runner.restRemainingSec}
              targetReps={step.targetReps}
              cardioDistanceMeters={step.cardioDistanceMeters}
              notes={step.notes}
              paused={runner.paused}
              onTogglePause={runner.setPaused}
              reps={runner.reps}
              onChangeReps={runner.setReps}
              roundsCompleted={runner.roundsCompleted}
              onChangeRoundsCompleted={runner.setRoundsCompleted}
              extraReps={runner.extraReps}
              onChangeExtraReps={runner.setExtraReps}
              next={runner.nextPreview}
              onPrev={runner.goPrev}
              onDone={runner.logInterval}
            />
          )}

          {step.kind === 'rest' && (
            <SessionRest
              secondsRemaining={runner.restRemainingSec}
              totalSeconds={runner.restTargetSec}
              next={runner.nextPreview}
              onAddSeconds={runner.addRestSeconds}
              onSkip={runner.skipRest}
              onPrev={runner.goPrev}
            />
          )}
        </View>

        {adding && (
          <SessionExercisePicker
            candidates={addCandidates}
            onCancel={() => setAdding(false)}
            onSelect={(exerciseId) => {
              runner.addExercise(exerciseId);
              setAdding(false);
            }}
          />
        )}

        {swapPickerOpen && (
          <SessionExercisePicker
            replacing={step.exerciseName}
            candidates={runner.swapCandidates}
            onCancel={() => setSwappingAt(null)}
            onSelect={(exerciseId) => {
              runner.swapExercise(exerciseId);
              setSwappingAt(null);
            }}
          />
        )}
      </View>

      {/* Outside `content`, so the dim covers the whole screen and the sheet reaches the bottom edge. */}
      {upcomingOpen && (
        <SessionUpcoming
          step={step}
          items={runner.upcoming}
          ends={!runner.isAdHoc}
          holdElapsedSec={runner.holdElapsedSec}
          restRemainingSec={runner.restRemainingSec}
          restTargetSec={runner.restTargetSec}
          onClose={closeUpcoming}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: RunnerColors.background,
  },
  content: {
    flex: 1,
    alignSelf: 'center',
    width: '100%',
    maxWidth: MaxContentWidth,
    paddingHorizontal: Spacing.three,
    paddingTop: Spacing.two,
    paddingBottom: Spacing.three,
  },
  // Takes content's place in its column, so wrapping the runner in it changes no layout.
  runnerBody: {
    flex: 1,
  },
  // A stack rather than the single row it used to be, so the circuit crumb can sit under the name
  // and share its bottom margin instead of adding one of its own.
  header: {
    gap: Spacing.one,
    marginBottom: Spacing.four,
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    // gap, not space-between: the name now flexes into whatever is left, so there is no free space
    // for space-between to distribute, and a long name would otherwise sit flush against the dots.
    gap: Spacing.two,
  },
  emptyState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.two,
  },
  emptyStateTitle: {
    color: RunnerColors.text,
  },
  emptyStateBody: {
    color: RunnerColors.textSecondary,
    textAlign: 'center',
  },
  tooLongBack: {
    marginTop: Spacing.two,
    minHeight: 44,
    justifyContent: 'center',
  },
  emptyStateButton: {
    marginTop: Spacing.two,
    // minHeight, not height: a fixed one clips the label at large accessibility text sizes.
    minHeight: 52,
    paddingVertical: Spacing.one,
    paddingHorizontal: Spacing.three,
    minWidth: 160,
    borderRadius: 15,
    backgroundColor: RunnerColors.accent,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyStateButtonLabel: {
    textAlign: 'center',
    color: RunnerColors.background,
  },
  // Takes the leftover width, so the name can ellipsize inside it (with numberOfLines) instead of
  // growing to its intrinsic width and shoving the dots and "Finish" off the right edge. minWidth: 0 is
  // what actually lets it shrink below that intrinsic width on web, where the flex default is auto.
  workoutNameButton: {
    flex: 1,
    minWidth: 0,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.one,
  },
  // Shrinks rather than flexes, so the chevron sits right after the name instead of at the far edge.
  workoutName: {
    color: RunnerColors.textSecondary,
    flexShrink: 1,
    minWidth: 0,
  },
  headerRight: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
    // The name is the only thing here that may lose width; "Finish" is a control and stays whole.
    flexShrink: 0,
  },
  finishLabel: {
    color: RunnerColors.textSecondary,
    letterSpacing: 1,
  },
  // opacity, not display:none or a width — the element has to lay out at its natural size to reserve
  // the column. Pointer events fall through to nothing here, so it needs no further disabling.
  finishSpacer: {
    opacity: 0,
  },
  // The calm accent, matching this line's dot track and the record pill: color is what separates the
  // circuit crumb from the block row above it at a glance. Measured 6.78:1 on the runner background.
  circuitLabel: {
    color: RunnerColors.accentCalmOnSoft,
    letterSpacing: 1,
    // Shrinks and ellipsizes like the workout name above rather than pushing the dots out of the
    // track they share with it.
    flex: 1,
    minWidth: 0,
  },
  adhocFinish: {
    marginTop: Spacing.two,
    minHeight: 44,
    justifyContent: 'center',
    paddingHorizontal: Spacing.three,
  },
});
