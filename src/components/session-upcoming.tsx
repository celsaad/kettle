import { memo, useEffect, type ReactNode } from 'react';
import { BackHandler, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import type { TFunction } from 'i18next';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { MaxContentWidth, RunnerColors, Spacing } from '@/constants/theme';
import { formatClock } from '@/domain/format';
import type { UpcomingItem, WorkStep, WorkUnit } from '@/hooks/session-outline';
import { formatHoldTarget, formatRepsTarget, type RunnerStep } from '@/hooks/session-steps';

/**
 * Where the sheet starts below the top inset: clear of the runner header with its circuit crumb
 * (8 padding + 18 + 4 + 16 = 46), plus a gap. Fixed rather than measured, since `onLayout` costs a
 * frame of visible jump; at large text sizes the dimmed header runs under the sheet, which hides
 * nothing anyone needs while it's open.
 */
const HEADER_CLEARANCE = 56;

type Props = {
  /** The step on screen, which the NOW row describes. */
  step: RunnerStep;
  items: UpcomingItem[];
  /** False for an ad-hoc session, which parks rather than ending when its queue runs out. */
  ends: boolean;
  holdElapsedSec: number;
  restRemainingSec: number;
  restTargetSec: number;
  onClose: () => void;
};

/**
 * The Coming up sheet: the rest of the workout as a timeline (docs/up-next-plan.md, layout B).
 *
 * A sheet inside the runner rather than a route, for the swap picker's reasons, plus one of its own:
 * the step list is runner hook state, and a route couldn't read it without moving that state into a
 * store. Read-only on purpose — the only controls close it.
 *
 * Written against `RunnerColors` throughout, since `ListRow` and `ModalHeader` read the shell theme
 * and the runner is always dark. The timeline breaks the hairline-rows house rule deliberately: it's a
 * different kind of list, not a subtly different copy of the normal one.
 */
export function SessionUpcoming({ step, items, ends, holdElapsedSec, restRemainingSec, restTargetSec, onClose }: Props) {
  const { t } = useTranslation();

  // Android's back button closes the sheet and nothing else. Unhandled, it falls through to the
  // session route and leaves the runner mid-workout — see "Found while planning" in the plan.
  useEffect(() => {
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      onClose();
      return true;
    });
    return () => subscription.remove();
  }, [onClose]);

  return (
    // Modal on the overlay rather than on the sheet: iOS hides only a modal view's *siblings*, and the
    // runner beneath is a sibling of this overlay, not of the sheet. session.tsx also hides the runner
    // with aria-hidden, which is what reaches Android and the web.
    <View style={styles.overlay} accessibilityViewIsModal onAccessibilityEscape={onClose}>
      {/* Hidden from assistive tech: the × is the named way out, and a second "Close" spanning the
          whole screen would only be one more thing to swipe past. */}
      <Pressable style={styles.backdrop} onPress={onClose} testID="upcoming-backdrop" aria-hidden />
      <SafeAreaView edges={['top', 'left', 'right']} style={styles.column} pointerEvents="box-none">
        {/* Taps here fall through to the backdrop, so the dimmed header is a way out too. */}
        <View style={styles.headerClearance} pointerEvents="none" />
        <SafeAreaView edges={['bottom']} style={styles.sheet}>
          {/* Outside the ScrollView, for ModalHeader's reason: a close control that scrolls away is one
              you have to go looking for. */}
          <View style={styles.strip}>
            <ThemedText type="code" style={styles.title} accessibilityRole="header">
              {t('session.upcoming.title')}
            </ThemedText>
            <Pressable
              onPress={onClose}
              hitSlop={6}
              accessibilityRole="button"
              accessibilityLabel={t('common.close')}
              style={({ pressed }) => [styles.closeButton, pressed && styles.pressed]}>
              <ThemedText type="heading" style={styles.closeGlyph}>
                ×
              </ThemedText>
            </Pressable>
          </View>
          <ScrollView contentContainerStyle={styles.scrollContent}>
            <NowRow
              step={step}
              holdElapsedSec={holdElapsedSec}
              restRemainingSec={restRemainingSec}
              restTargetSec={restTargetSec}
              last={items.length === 0 && !ends}
            />
            <UpcomingList items={items} ends={ends} />
          </ScrollView>
        </SafeAreaView>
      </SafeAreaView>
    </View>
  );
}

// --- The rail ---

type NodeKind = 'nowRest' | 'nowWork' | 'circuit' | 'step';

/**
 * One stop on the timeline. `slot` is the height of the item's first line, so the node sits level with
 * it whether that line is a heading or a kicker.
 */
function RailItem({
  node,
  slot,
  last,
  children,
}: {
  node: NodeKind;
  slot: 'heading' | 'code';
  last: boolean;
  children: ReactNode;
}) {
  return (
    <View style={styles.item}>
      <View style={styles.rail}>
        <View style={[styles.nodeSlot, slot === 'heading' ? styles.slotHeading : styles.slotCode]}>
          <Node kind={node} />
        </View>
        {!last && <View style={styles.line} />}
      </View>
      <View style={[styles.itemBody, last && styles.itemBodyLast]}>{children}</View>
    </View>
  );
}

function Node({ kind }: { kind: NodeKind }) {
  if (kind === 'nowRest' || kind === 'nowWork') {
    const resting = kind === 'nowRest';
    return (
      <View style={[styles.halo, resting ? styles.haloCalm : styles.haloWarm]}>
        <View style={[styles.nowDot, resting ? styles.fillCalm : styles.fillWarm]} />
      </View>
    );
  }
  return <View style={kind === 'circuit' ? styles.circuitNode : styles.stepNode} />;
}

// --- NOW ---

/**
 * What's on screen right now, with its live number — which is also what makes the sheet closing itself
 * legible: the countdown says it's about to go. Re-renders every second; the list below doesn't.
 *
 * Hue follows the runner's existing split, calm for rest and warm for work. Not a live region: it would
 * announce every second.
 */
function NowRow({
  step,
  holdElapsedSec,
  restRemainingSec,
  restTargetSec,
  last,
}: {
  step: RunnerStep;
  holdElapsedSec: number;
  restRemainingSec: number;
  restTargetSec: number;
  last: boolean;
}) {
  const { t } = useTranslation();
  const resting = step.kind === 'rest';

  // A reps set has no clock. A hold counts up, as the hold screen does; a count-up cardio too.
  let clock: number | null = null;
  if (step.kind === 'rest') clock = restRemainingSec;
  else if (step.kind === 'hold') clock = holdElapsedSec;
  else if (step.kind === 'interval') clock = step.countUp ? holdElapsedSec : restRemainingSec;

  const caption =
    step.kind === 'rest'
      ? t('session.rest.remaining', { total: formatClock(restTargetSec) })
      : step.kind === 'interval'
        ? t('preview.interval', { variant: step.variant, progress: intervalProgress(step, t) })
        : t('session.setOf', { index: step.setIndex, total: step.setTotal });

  return (
    <RailItem node={resting ? 'nowRest' : 'nowWork'} slot="code" last={last}>
      <View accessible style={styles.stack}>
        <ThemedText type="code" style={[styles.nowKicker, resting ? styles.onSoftCalm : styles.onSoftWarm]}>
          {t(resting ? 'session.upcoming.nowRest' : 'session.upcoming.now')}
        </ThemedText>
        {step.kind !== 'rest' && (
          <ThemedText type="heading" style={styles.name}>
            {step.exerciseName}
          </ThemedText>
        )}
        {clock !== null && (
          <ThemedText
            type="numeral"
            maxFontSizeMultiplier={1.3}
            style={[styles.clock, resting ? styles.textCalm : styles.textWarm]}>
            {formatClock(clock)}
          </ThemedText>
        )}
        <ThemedText type="small" style={styles.detail}>
          {caption}
        </ThemedText>
      </View>
    </RailItem>
  );
}

// --- What's left ---

/**
 * Memoised so the 1Hz tick that re-renders the NOW row stops here: `items` is the runner's memoised
 * outline, which keeps its identity until what's left changes.
 */
const UpcomingList = memo(function UpcomingList({ items, ends }: { items: UpcomingItem[]; ends: boolean }) {
  const { t } = useTranslation();
  return (
    <>
      {items.map((item, index) => (
        <Item key={`${item.kind}:${index}`} item={item} last={!ends && index === items.length - 1} t={t} />
      ))}
      {ends && (
        <RailItem node="step" slot="code" last>
          <ThemedText type="code" style={styles.end}>
            {t('session.upcoming.end')}
          </ThemedText>
        </RailItem>
      )}
    </>
  );
});

function Item({ item, last, t }: { item: UpcomingItem; last: boolean; t: TFunction }) {
  switch (item.kind) {
    case 'exercise':
      return (
        <RailItem node="step" slot="heading" last={last}>
          <Entry name={item.step.exerciseName} detail={exerciseDetail(item, t)} />
        </RailItem>
      );
    case 'rest':
      // Rest steps carry no name, so this is the translated word rather than the library's name for it.
      return (
        <RailItem node="step" slot="heading" last={last}>
          <Entry name={t('session.upcoming.rest')} detail={formatClock(item.seconds)} />
        </RailItem>
      );
    case 'round':
      return (
        <RailItem node="circuit" slot="code" last={last}>
          <Kicker>
            {item.rounds === 1
              ? t('session.upcoming.circuitLeft', { count: item.members.length })
              : t('session.circuit.crumb', { index: item.round, total: item.rounds })}
          </Kicker>
          <View style={styles.members}>
            {item.members.map((member) => (
              <Entry key={member.memberKey} name={member.step.exerciseName} detail={targetFor(member.step, t)} />
            ))}
          </View>
        </RailItem>
      );
    case 'moreRounds':
      return (
        <RailItem node="circuit" slot="code" last={last}>
          <Kicker>{t('session.upcoming.roundsAfter', { count: item.count })}</Kicker>
          <Names names={item.names} />
        </RailItem>
      );
    case 'circuit':
      return (
        <RailItem node="circuit" slot="code" last={last}>
          <Kicker>{t('session.upcoming.circuitRounds', { count: item.rounds })}</Kicker>
          <Names names={item.names} />
        </RailItem>
      );
  }
}

/** A name and its detail, announced as one element. The name is the user's own and renders verbatim. */
function Entry({ name, detail }: { name: string; detail: string }) {
  return (
    <View accessible style={styles.stack}>
      <ThemedText type="heading" style={styles.name}>
        {name}
      </ThemedText>
      <ThemedText type="small" style={styles.detail}>
        {detail}
      </ThemedText>
    </View>
  );
}

function Kicker({ children }: { children: string }) {
  return (
    <ThemedText type="code" style={styles.circuitKicker} accessibilityRole="header">
      {children}
    </ThemedText>
  );
}

/** One name per line: easier to scan at arm's length than a wrapped paragraph, and never clamped. */
function Names({ names }: { names: string[] }) {
  return (
    <View style={styles.names}>
      {names.map((name, index) => (
        <ThemedText key={`${name}:${index}`} style={styles.name}>
          {name}
        </ThemedText>
      ))}
    </View>
  );
}

// --- Strings ---

const COUNT_KEYS: Record<Exclude<WorkUnit, null>, { fresh: string; more: string }> = {
  set: { fresh: 'session.upcoming.sets', more: 'session.upcoming.moreSets' },
  round: { fresh: 'session.upcoming.rounds', more: 'session.upcoming.moreRounds' },
  minute: { fresh: 'session.upcoming.minutes', more: 'session.upcoming.moreMinutes' },
};

function exerciseDetail(item: Extract<UpcomingItem, { kind: 'exercise' }>, t: TFunction): string {
  const target = targetFor(item.step, t);
  // One-shot work (an AMRAP, a cardio) has no count to give.
  if (item.unit === null) return target;
  const keys = COUNT_KEYS[item.unit];
  const amount = t(item.started ? keys.more : keys.fresh, { count: item.left });
  return t('session.upcoming.detail', { amount, target });
}

/** The target as the Next card words it, minus the set position — the list already says how many. */
function targetFor(step: WorkStep, t: TFunction): string {
  switch (step.kind) {
    case 'reps':
      return t('session.upcoming.targetReps', { target: formatRepsTarget(step) });
    case 'hold': {
      const target = formatHoldTarget(step);
      return target === null ? t('session.upcoming.targetHoldOpen') : t('session.upcoming.targetHold', { target });
    }
    case 'interval':
      return t('preview.interval', {
        variant: step.variant,
        progress: step.countUp ? t('session.countingUp') : t('preview.seconds', { n: step.targetSec }),
      });
  }
}

/**
 * The Next card's interval wording (`previewFor` in the runner), except that a count-up cardio says so
 * rather than "0s" — it has no duration to show.
 */
function intervalProgress(step: Extract<RunnerStep, { kind: 'interval' }>, t: TFunction): string {
  if (step.countUp) return t('session.countingUp');
  return step.setTotal > 1
    ? t('preview.round', { index: step.setIndex, total: step.setTotal })
    : t('preview.seconds', { n: step.targetSec });
}

const styles = StyleSheet.create({
  // Absolute children ignore the SafeAreaView padding they sit in, so this covers the whole screen and
  // the two SafeAreaViews inside it apply the insets.
  overlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    zIndex: 10,
  },
  backdrop: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0,0,0,0.55)',
  },
  column: {
    flex: 1,
  },
  headerClearance: {
    height: HEADER_CLEARANCE,
  },
  sheet: {
    flex: 1,
    alignSelf: 'center',
    width: '100%',
    maxWidth: MaxContentWidth,
    backgroundColor: RunnerColors.backgroundElement,
    borderTopWidth: 1,
    borderColor: RunnerColors.border,
    borderTopLeftRadius: 22,
    borderTopRightRadius: 22,
    paddingTop: Spacing.three,
    paddingHorizontal: Spacing.three,
  },
  strip: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    minHeight: 32,
    marginBottom: Spacing.four - 4,
  },
  title: {
    color: RunnerColors.textSecondary,
    letterSpacing: 1.4,
  },
  // 32 visible, 44 with the hitSlop — the ModalHeader button's size, in runner colours.
  closeButton: {
    width: 32,
    height: 32,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: RunnerColors.border,
    backgroundColor: RunnerColors.background,
    alignItems: 'center',
    justifyContent: 'center',
  },
  closeGlyph: {
    color: RunnerColors.textSecondary,
  },
  pressed: {
    opacity: 0.6,
  },
  scrollContent: {
    paddingBottom: Spacing.three,
  },
  item: {
    flexDirection: 'row',
    gap: Spacing.three,
  },
  rail: {
    width: 20,
    alignItems: 'center',
  },
  nodeSlot: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  slotHeading: {
    height: 26,
  },
  slotCode: {
    height: 16,
  },
  // Decorative geometry, so a fixed size is right here (the 44px rule is about controls).
  line: {
    flex: 1,
    width: 2,
    marginTop: 6,
    backgroundColor: RunnerColors.border,
  },
  itemBody: {
    flex: 1,
    minWidth: 0,
    paddingBottom: 22,
  },
  itemBodyLast: {
    paddingBottom: 0,
  },
  stepNode: {
    width: 10,
    height: 10,
    borderRadius: 5,
    borderWidth: 1.5,
    borderColor: RunnerColors.textSecondary,
  },
  circuitNode: {
    width: 12,
    height: 12,
    borderRadius: 6,
    borderWidth: 2,
    borderColor: RunnerColors.accentCalm,
  },
  halo: {
    width: 20,
    height: 20,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
  },
  haloCalm: {
    backgroundColor: 'rgba(63,130,192,0.25)',
  },
  haloWarm: {
    backgroundColor: 'rgba(207,106,55,0.25)',
  },
  nowDot: {
    width: 12,
    height: 12,
    borderRadius: 6,
  },
  // Both measured against `backgroundElement` as graphics that carry meaning (3:1 needed): calm ≈ 4.2,
  // warm ≈ 4.7.
  fillCalm: {
    backgroundColor: RunnerColors.accentCalm,
  },
  fillWarm: {
    backgroundColor: RunnerColors.accent,
  },
  stack: {
    gap: 2,
  },
  nowKicker: {
    letterSpacing: 1.4,
  },
  // The "on soft" label tokens, which are the ones measured for small text on the runner's dark
  // surfaces (both ≈ 6.4:1 on `backgroundElement`).
  onSoftCalm: {
    color: RunnerColors.accentCalmOnSoft,
  },
  onSoftWarm: {
    color: RunnerColors.accentOnSoft,
  },
  circuitKicker: {
    color: RunnerColors.accentCalmOnSoft,
    letterSpacing: 1,
  },
  clock: {
    fontSize: 34,
    lineHeight: 40,
  },
  // Large text (34px), so the fills clear AA-large on `backgroundElement` as text too.
  textCalm: {
    color: RunnerColors.accentCalm,
  },
  textWarm: {
    color: RunnerColors.accent,
  },
  name: {
    color: RunnerColors.text,
  },
  detail: {
    color: RunnerColors.textSecondary,
  },
  members: {
    gap: Spacing.two + 4,
    marginTop: Spacing.two + 2,
  },
  names: {
    gap: 2,
    marginTop: 2,
  },
  end: {
    color: RunnerColors.textSecondary,
    letterSpacing: 1,
  },
});
