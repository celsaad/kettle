import { StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { useTheme } from '@/hooks/use-theme';

import { EXERCISE_ART } from './map';

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

  const Art = EXERCISE_ART[exerciseId];
  if (!Art) return null;

  return (
    <View
      style={styles.frame}
      // The art is app content, not user data, so unlike a user-supplied photo it has a real
      // description to give — and one that gets translated. It describes the *movement*, not the
      // picture, and it neither coaches nor gives injury advice (the seed's own content bar).
      accessible
      accessibilityRole="image"
      accessibilityLabel={t(`exerciseArt.${exerciseId}`)}>
      <Art size={size} color={color ?? theme.textSecondary} />
    </View>
  );
}

const styles = StyleSheet.create({
  frame: {
    alignItems: 'center',
  },
});
