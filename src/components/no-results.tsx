import { StyleSheet } from 'react-native';
import { useTranslation } from 'react-i18next';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * What a list shows when a search or filter matched nothing.
 *
 * Worth having rather than rendering an empty space, which is what Library and History did: a screen
 * whose chrome is intact and whose body is blank reads as a bug, not as an answer. It also has to be
 * distinct from a *first-run* empty state — "No workouts yet, build one from your library" is
 * actively wrong in front of someone with forty workouts who mistyped one. The screens pick between
 * the two; this is only ever the no-match half.
 *
 * An empty `query` means the narrowing came from a filter rather than typing, which is reachable on
 * Library alone (its type pills) and needs its own sentence — quoting an empty string back at someone
 * explains nothing.
 */
export function NoResults({ query }: { query: string }) {
  const theme = useTheme();
  const { t } = useTranslation();
  const trimmed = query.trim();

  return (
    <ThemedView type="backgroundElement" style={[styles.card, { borderColor: theme.border }]}>
      <ThemedText type="heading">{t('search.noMatchTitle')}</ThemedText>
      <ThemedText type="small" themeColor="textSecondary" style={styles.body}>
        {trimmed ? t('search.noMatchBody', { query: trimmed }) : t('search.noMatchFilterBody')}
      </ThemedText>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: 16,
    borderWidth: 1,
    padding: Spacing.three,
    gap: 4,
  },
  body: {
    lineHeight: 18,
  },
});
