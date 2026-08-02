import { Pressable, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { LIST_SORTS, type ListSort } from '@/domain/preferences';
import { useTheme } from '@/hooks/use-theme';

const LABEL_KEYS: Record<ListSort, string> = {
  custom: 'sort.custom',
  name: 'sort.name',
  recent: 'sort.recent',
};

/**
 * The order control shared by Build, Programs and Library. Extracted rather than copied three times:
 * these lists should read as one pattern, and a fourth list should get the control by importing it.
 *
 * Styled to match Library's type-filter pills exactly, since on that screen the two rows sit one
 * above the other and any difference between them would read as meaning something. The leading "Sort"
 * label is what tells the two rows apart there — without it they're two rows of identical pills doing
 * unrelated things — and it's why the row wraps rather than squeezing: four items plus a label is
 * already tight in Portuguese at default text size, and accessibility text sizes are wider still.
 */
export function SortPills({ sort, onSelect }: { sort: ListSort; onSelect: (sort: ListSort) => void }) {
  const theme = useTheme();
  const { t } = useTranslation();

  return (
    <View style={styles.row}>
      <ThemedText type="small" themeColor="textSecondary" style={styles.label}>
        {t('sort.label')}
      </ThemedText>
      {LIST_SORTS.map((option) => {
        const active = option === sort;
        return (
          <Pressable
            key={option}
            onPress={() => onSelect(option)}
            accessibilityRole="button"
            accessibilityState={{ selected: active }}
            // The pill draws at ~32px to match the filter row; the slop is what makes the target 44.
            // `minHeight` would have been the house default, but it can't be used here without either
            // making these taller than the row they sit under or making that row taller too.
            hitSlop={{ top: 6, bottom: 6 }}
            style={[
              styles.pill,
              active
                ? { backgroundColor: theme.text }
                : { backgroundColor: theme.backgroundElement, borderWidth: 1, borderColor: theme.border },
            ]}>
            <ThemedText type="small" style={{ color: active ? theme.onAccent : theme.textSecondary }}>
              {t(LABEL_KEYS[option])}
            </ThemedText>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: Spacing.two,
    marginTop: Spacing.two,
  },
  label: {
    marginRight: Spacing.half,
  },
  // Copied value-for-value from Library's filterPill, deliberately — see the note above.
  pill: {
    paddingHorizontal: Spacing.three - 3,
    paddingVertical: 7,
    borderRadius: 999,
  },
});
