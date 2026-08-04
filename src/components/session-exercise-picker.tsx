import { useMemo, useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { ThemedText } from '@/components/themed-text';
import { RunnerColors, Spacing } from '@/constants/theme';
import type { Exercise } from '@/domain/types';

type Props = {
  /**
   * The exercise being replaced, when substituting. Absent in an ad-hoc session, which is *adding*
   * rather than replacing — the two modes differ only in what the sheet calls itself, so one component
   * serves both rather than a near-copy for each.
   */
  replacing?: string;
  /** Candidates, already filtered by the caller: same-type when swapping, all non-rest when adding. */
  candidates: Exercise[];
  onCancel: () => void;
  onSelect: (exerciseId: string) => void;
};

/**
 * Above this many candidates the sheet grows a search field. Swapping filters to one exercise type
 * and lands well under it; adding, which offers the whole library, does not.
 */
const SEARCH_THRESHOLD = 8;

/**
 * Picks a substitute for the rest of the current exercise, or an exercise to add to an ad-hoc session.
 *
 * A sheet inside the runner rather than a route, for the same reasons `SessionNumberPad` is one: the
 * runner screens are `flex: 1` with no room to push anything aside, and a route would need registering
 * in `_layout.tsx` and the router types regenerating for a control that only exists mid-session.
 *
 * Searchable, because the ad-hoc case made the plain list untenable: swapping filters to one exercise
 * type and leaves a handful, but *adding* offers the whole library, and scrolling that mid-workout is
 * worse than a keyboard. The field is hidden below a threshold, so the swap case keeps the plain list
 * it wants.
 *
 * The input is written here against `RunnerColors` rather than reusing `SearchBar`: that component
 * reads the light/dark shell theme through `useTheme`, and the runner is always dark regardless of the
 * scheme (constants/theme.ts), so it would render a light box inside this sheet.
 */
export function SessionExercisePicker({ replacing, candidates, onCancel, onSelect }: Props) {
  const { t } = useTranslation();
  const [query, setQuery] = useState('');

  const showsSearch = candidates.length > SEARCH_THRESHOLD;
  // Case- and accent-insensitive: exercise names come from the user's own YAML, and "Agachamento"
  // should be reachable by typing "agach". Matches anywhere in the name rather than only at the start,
  // since "press" should find "Dumbbell Floor Press".
  const normalise = (value: string) =>
    value
      .normalize('NFD')
      .replace(/\p{Diacritic}/gu, '')
      .toLowerCase();
  const shown = useMemo(() => {
    const needle = normalise(query.trim());
    if (!needle) return candidates;
    return candidates.filter((exercise) => normalise(exercise.name).includes(needle));
  }, [candidates, query]);

  return (
    <View style={styles.overlay}>
      <Pressable
        style={styles.backdrop}
        onPress={onCancel}
        accessibilityRole="button"
        accessibilityLabel={t('session.swap.dismiss')}
      />

      {/* The sheet is bottom-anchored, so on iOS the keyboard would sit on top of it. Android's
          windowSoftInputMode resizes the window instead, which the absolute overlay follows for free. */}
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <View style={styles.sheet}>
          <ThemedText type="code" style={styles.label}>
            {t(replacing === undefined ? 'session.adhoc.pickerTitle' : 'session.swap.title')}
          </ThemedText>
          {/* The user's own exercise name, interpolated rather than translated. */}
          <ThemedText type="small" style={styles.replacing}>
            {replacing === undefined ? t('session.adhoc.pickerBody') : t('session.swap.replacing', { name: replacing })}
          </ThemedText>

          {showsSearch && (
            <View style={styles.search}>
              <ThemedText style={styles.searchGlyph}>⌕</ThemedText>
              <TextInput
                value={query}
                onChangeText={setQuery}
                placeholder={t('session.adhoc.search')}
                // The placeholder doubles as the label, matching SearchBar: a TextInput with placeholder
                // text already has a visible name, and a different label would break Voice Control.
                accessibilityLabel={t('session.adhoc.search')}
                placeholderTextColor={RunnerColors.textSecondary}
                autoCorrect={false}
                style={styles.searchInput}
              />
            </View>
          )}

          <ScrollView style={styles.list} contentContainerStyle={styles.listContent} keyboardShouldPersistTaps="handled">
            {shown.length === 0 && (
              <ThemedText type="small" style={styles.noResults}>
                {t('session.adhoc.noMatches', { query: query.trim() })}
              </ThemedText>
            )}
            {shown.map((exercise) => (
              <Pressable
                key={exercise.id}
                onPress={() => onSelect(exercise.id)}
                accessibilityRole="button"
                style={({ pressed }) => [styles.option, pressed && styles.optionPressed]}>
                {/* Takes its name from this text, so it needs no accessibilityLabel of its own. */}
                <ThemedText type="heading" style={styles.optionLabel}>
                  {exercise.name}
                </ThemedText>
              </Pressable>
            ))}
          </ScrollView>

          <Pressable onPress={onCancel} accessibilityRole="button" style={styles.cancelButton}>
            <ThemedText type="code" style={styles.cancelLabel}>
              {t('common.cancel')}
            </ThemedText>
          </Pressable>
        </View>
      </KeyboardAvoidingView>
    </View>
  );
}

const styles = StyleSheet.create({
  overlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    justifyContent: 'flex-end',
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
  sheet: {
    backgroundColor: RunnerColors.backgroundElement,
    borderTopWidth: 1,
    borderColor: RunnerColors.border,
    borderTopLeftRadius: 22,
    borderTopRightRadius: 22,
    padding: Spacing.three,
    gap: Spacing.two,
  },
  label: {
    color: RunnerColors.textSecondary,
    letterSpacing: 1.4,
    textAlign: 'center',
  },
  replacing: {
    color: RunnerColors.textSecondary,
    textAlign: 'center',
  },
  search: {
    minHeight: 44,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: RunnerColors.border,
    backgroundColor: RunnerColors.background,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    paddingHorizontal: Spacing.two + 6,
  },
  searchGlyph: {
    color: RunnerColors.textSecondary,
  },
  searchInput: {
    flex: 1,
    fontSize: 14,
    color: RunnerColors.text,
  },
  noResults: {
    color: RunnerColors.textSecondary,
    textAlign: 'center',
    paddingVertical: Spacing.three,
  },
  // Capped so a long library scrolls inside the sheet rather than pushing Cancel off the screen.
  list: {
    maxHeight: 320,
  },
  listContent: {
    gap: Spacing.two - 2,
    paddingVertical: Spacing.one,
  },
  option: {
    minHeight: 56,
    justifyContent: 'center',
    paddingHorizontal: Spacing.three,
    borderRadius: 14,
    backgroundColor: RunnerColors.background,
    borderWidth: 1,
    borderColor: RunnerColors.border,
  },
  optionPressed: {
    opacity: 0.6,
  },
  optionLabel: {
    color: RunnerColors.text,
  },
  cancelButton: {
    minHeight: 52,
    paddingVertical: Spacing.one,
    paddingHorizontal: Spacing.two,
    borderRadius: 15,
    borderWidth: 1,
    borderColor: RunnerColors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cancelLabel: {
    textAlign: 'center',
    color: RunnerColors.textSecondary,
    letterSpacing: 1,
  },
});
