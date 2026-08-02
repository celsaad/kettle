import { StyleSheet, TextInput, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * The search box shared by all four list screens.
 *
 * Library and History each had their own copy of this markup, the second carrying a comment saying it
 * was taken from the first value for value so the two would read as one pattern. Adding Build and
 * Programs would have made four copies of a promise nothing enforced, so it's one component now.
 *
 * The placeholder doubles as the accessibility label. That's deliberate rather than lazy: a
 * `TextInput` with placeholder text has a visible name, and giving it a *different* label would break
 * Voice Control, which matches what's on screen.
 *
 * It owns its own top margin because all four screens place it the same distance below the block above
 * it, and a margin passed in from four call sites is a margin that drifts at three of them.
 */
export function SearchBar({
  value,
  onChangeText,
  placeholder,
}: {
  value: string;
  onChangeText: (value: string) => void;
  placeholder: string;
}) {
  const theme = useTheme();

  return (
    <View style={[styles.searchBar, { borderColor: theme.border, backgroundColor: theme.backgroundElement }]}>
      <ThemedText themeColor="textSecondary">⌕</ThemedText>
      <TextInput
        value={value}
        onChangeText={onChangeText}
        placeholder={placeholder}
        accessibilityLabel={placeholder}
        placeholderTextColor={theme.textSecondary}
        style={[styles.searchInput, { color: theme.text }]}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  searchBar: {
    marginTop: Spacing.three,
    minHeight: 44,
    borderRadius: 14,
    borderWidth: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    paddingHorizontal: Spacing.two + 6,
  },
  searchInput: {
    flex: 1,
    fontSize: 14,
  },
});
