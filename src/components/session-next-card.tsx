import { StyleSheet, useWindowDimensions, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { ExerciseArt } from '@/components/exercise-art';
import { ThemedText } from '@/components/themed-text';
import { RunnerColors, Spacing } from '@/constants/theme';
import type { RestPreview } from '@/hooks/use-session-runner';

/**
 * Above this text scale the runner screens stop fitting. They're `flex: 1` with `space-between` and
 * no ScrollView, so overflow *clips* rather than scrolls — and what gets clipped is the bottom, which
 * is where the primary action lives. Dropping this card is the cheapest way to buy that room back:
 * it's the only genuinely supplementary element on those screens, and the information is a preview of
 * a step the user is about to reach anyway.
 */
const MAX_FONT_SCALE_FOR_PREVIEW = 1.5;

/**
 * The drawing goes before the card does, and at a lower scale, because it is the one part of this
 * card that does *not* shrink: the text reflows, a fixed-size illustration just keeps taking its
 * 45dp while everything around it grows. Dropping it first buys back the room the card needs to stay
 * useful a bit longer, and costs only the decoration.
 */
const MAX_FONT_SCALE_FOR_ART = 1.2;

/**
 * Sized against the text beside it rather than for its own sake: at 56dp the drawing is barely
 * taller than the two lines it sits next to, so the card keeps very close to the height it already
 * had — which is what matters on screens that clip instead of scrolling. It was 72dp first, and that
 * pushed the detail line into a second wrap on a 420dp-wide phone, growing the card by more than the
 * drawing was worth.
 *
 * The drawings are iconographic — uniform stroke, no faces, no background — which is what lets them
 * survive being this small at all.
 */
const ART_SIZE = 56;

/**
 * The "what's coming up next" card — originally only shown on the rest screen, now shared across
 * every session step screen (hold/reps/interval too) so you can see what's next without waiting for
 * rest. Renders nothing when there's nothing upcoming (end of workout).
 */
export function SessionNextCard({ next }: { next: RestPreview }) {
  const { t } = useTranslation();
  const { fontScale } = useWindowDimensions();
  if (!next || fontScale > MAX_FONT_SCALE_FOR_PREVIEW) return null;
  return (
    <View style={styles.nextCard}>
      <ThemedText type="code" style={styles.nextLabel}>
        {t('session.next')}
      </ThemedText>
      {fontScale <= MAX_FONT_SCALE_FOR_ART && (
        // Drawn in `text` rather than `textSecondary`: the stroke scales down with the drawing, so
        // at this size it renders under 2dp and needs the contrast (14.6:1 here against the card)
        // more than it needs to look subordinate.
        <ExerciseArt exerciseId={next.exerciseId} size={ART_SIZE} color={RunnerColors.text} />
      )}
      <View style={styles.nextText}>
        <ThemedText type="heading" style={styles.nextName}>
          {next.label}
        </ThemedText>
        <ThemedText type="small" style={styles.nextDetail}>
          {next.detail}
        </ThemedText>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  nextCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three - 2,
    backgroundColor: RunnerColors.backgroundElement,
    borderWidth: 1,
    borderColor: RunnerColors.border,
    borderRadius: 18,
    padding: Spacing.three - 2,
  },
  nextLabel: {
    color: RunnerColors.textSecondary,
    letterSpacing: 1.4,
  },
  nextText: {
    flex: 1,
    gap: 2,
  },
  nextName: {
    color: RunnerColors.text,
  },
  nextDetail: {
    color: RunnerColors.textSecondary,
  },
});
