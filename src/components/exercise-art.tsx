import { useTranslation } from 'react-i18next';
import { StyleSheet, View } from 'react-native';
import { SvgXml } from 'react-native-svg';

import art from '@/content/art.json';
import amrapBodyweight from '@/content/art/amrap-bodyweight.svg';
import backSquat from '@/content/art/back-squat.svg';
import bandRow from '@/content/art/band-row.svg';
import barbellOverheadPress from '@/content/art/barbell-overhead-press.svg';
import barbellRow from '@/content/art/barbell-row.svg';
import benchPress from '@/content/art/bench-press.svg';
import bodyweightSquats from '@/content/art/bodyweight-squats.svg';
import burpees from '@/content/art/burpees.svg';
import cleanAndPress from '@/content/art/clean-and-press.svg';
import deadlift from '@/content/art/deadlift.svg';
import dumbbellGobletSquat from '@/content/art/dumbbell-goblet-squat.svg';
import dumbbellOverheadPress from '@/content/art/dumbbell-overhead-press.svg';
import dumbbellRow from '@/content/art/dumbbell-row.svg';
import easyCardio from '@/content/art/easy-cardio.svg';
import emomPushUps from '@/content/art/emom-push-ups.svg';
import farmersCarry from '@/content/art/farmers-carry.svg';
import floorPress from '@/content/art/floor-press.svg';
import gluteBridge from '@/content/art/glute-bridge.svg';
import halo from '@/content/art/halo.svg';
import heelRaises from '@/content/art/heel-raises.svg';
import hipHinge from '@/content/art/hip-hinge.svg';
import invertedRows from '@/content/art/inverted-rows.svg';
import kettlebellGobletSquat from '@/content/art/kettlebell-goblet-squat.svg';
import kettlebellRow from '@/content/art/kettlebell-row.svg';
import kettlebellSwing from '@/content/art/kettlebell-swing.svg';
import mountainClimbers from '@/content/art/mountain-climbers.svg';
import plank from '@/content/art/plank.svg';
import pullUps from '@/content/art/pull-ups.svg';
import pushUps from '@/content/art/push-ups.svg';
import rackCarry from '@/content/art/rack-carry.svg';
import romanianDeadlift from '@/content/art/romanian-deadlift.svg';
import seatedMarch from '@/content/art/seated-march.svg';
import singleLegDeadlift from '@/content/art/single-leg-deadlift.svg';
import sitToStand from '@/content/art/sit-to-stand.svg';
import splitSquats from '@/content/art/split-squats.svg';
import standingBalance from '@/content/art/standing-balance.svg';
import walk from '@/content/art/walk.svg';
import wallPushUps from '@/content/art/wall-push-ups.svg';
import { useTheme } from '@/hooks/use-theme';

/**
 * Every drawing's markup, by the file name `art.json` points at. Spelled out rather than globbed
 * because Metro bundles only what a static import names; a drawing missing here is a test failure,
 * not a silent gap.
 */
export const ARTWORK: Record<string, string> = {
  'amrap-bodyweight': amrapBodyweight,
  'back-squat': backSquat,
  'band-row': bandRow,
  'barbell-overhead-press': barbellOverheadPress,
  'barbell-row': barbellRow,
  'bench-press': benchPress,
  'bodyweight-squats': bodyweightSquats,
  burpees: burpees,
  'clean-and-press': cleanAndPress,
  deadlift: deadlift,
  'dumbbell-goblet-squat': dumbbellGobletSquat,
  'dumbbell-overhead-press': dumbbellOverheadPress,
  'dumbbell-row': dumbbellRow,
  'easy-cardio': easyCardio,
  'emom-push-ups': emomPushUps,
  'farmers-carry': farmersCarry,
  'floor-press': floorPress,
  'glute-bridge': gluteBridge,
  halo: halo,
  'heel-raises': heelRaises,
  'hip-hinge': hipHinge,
  'inverted-rows': invertedRows,
  'kettlebell-goblet-squat': kettlebellGobletSquat,
  'kettlebell-row': kettlebellRow,
  'kettlebell-swing': kettlebellSwing,
  'mountain-climbers': mountainClimbers,
  plank: plank,
  'pull-ups': pullUps,
  'push-ups': pushUps,
  'rack-carry': rackCarry,
  'romanian-deadlift': romanianDeadlift,
  'seated-march': seatedMarch,
  'single-leg-deadlift': singleLegDeadlift,
  'sit-to-stand': sitToStand,
  'split-squats': splitSquats,
  'standing-balance': standingBalance,
  walk: walk,
  'wall-push-ups': wallPushUps,
};

/** Height as a fraction of width: every drawing shares the `0 0 120 96` canvas. */
const ART_ASPECT = 96 / 120;

const drawings: Record<string, string> = art.drawings;

type Props = {
  exerciseId: string;
  /** Width in dp; height follows the shared aspect. */
  size?: number;
  /** Overrides the theme colour — the runner is dark whatever the scheme, and would pass its own. */
  color?: string;
};

/**
 * Renders the bundled drawing for an exercise, or nothing at all.
 *
 * Nothing at all is the important half: most exercises in a real library are the user's own, and an
 * empty frame or a placeholder glyph on every one of them would be worse than the silence. A missing
 * drawing is a map lookup returning `undefined`, and that is the entire failure mode.
 */
export function ExerciseArt({ exerciseId, size = 152, color }: Props) {
  const theme = useTheme();
  const { t } = useTranslation();

  const markup = ARTWORK[drawings[exerciseId]];
  if (!markup) return null;

  return (
    <View
      style={styles.frame}
      // The art is app content, not user data, so unlike a user-supplied photo it has a real
      // description to give — and one that gets translated. It describes the *movement*, not the
      // picture, and it neither coaches nor gives injury advice (the seed's own content bar).
      accessible
      accessibilityRole="image"
      accessibilityLabel={t(`exerciseArt.${exerciseId}`)}>
      {/* Every file strokes in `currentColor`, so this one prop recolours the drawing for the light
          theme, the dark theme and the always-dark runner alike. `color` is required in spirit: a
          drawing that fell back to a fixed colour would vanish on one of the three. */}
      <SvgXml xml={markup} width={size} height={size * ART_ASPECT} color={color ?? theme.textSecondary} />
    </View>
  );
}

const styles = StyleSheet.create({
  frame: {
    alignItems: 'center',
  },
});
