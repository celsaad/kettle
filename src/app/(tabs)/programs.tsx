import { router } from 'expo-router';
import { memo, useDeferredValue, useMemo, useState } from 'react';
import { FlatList, Platform, Pressable, StyleSheet, View, type ListRenderItemInfo } from 'react-native';
import type { TFunction } from 'i18next';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { NoResults } from '@/components/no-results';
import { SearchBar } from '@/components/search-bar';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { programWeekNumbers } from '@/domain/program';
import type { Program } from '@/domain/types';
import { useAppTheme } from '@/hooks/theme-context';
import { useTheme } from '@/hooks/use-theme';
import { useLibraryStore } from '@/state/library-store';

export { RouteErrorBoundary as ErrorBoundary } from '@/components/error-fallback';

function weekRangeLabel(program: Program, t: TFunction): string {
  const weeks = programWeekNumbers(program);
  if (weeks.length === 0) return t('programs.noWeeks');
  const first = weeks[0];
  const last = weeks[weeks.length - 1];
  return first === last ? t('programs.week', { n: first }) : t('programs.weeksRange', { first, last });
}

function detailLabel(program: Program, t: TFunction): string | null {
  const count = program.weeks.filter(
    (week) => week.notes || (!week.restDay && week.overrides && week.overrides.length > 0),
  ).length;
  if (count === 0) return null;
  return t('programs.weeksWithNotes', { count });
}

/** Memoised and module-level for the same reasons as Build's `WorkoutCard`; see the note there. */
const ProgramCard = memo(function ProgramCard({ program }: { program: Program }) {
  const theme = useTheme();
  const { t } = useTranslation();
  const detail = detailLabel(program, t);

  return (
    <Pressable
      onPress={() => router.push({ pathname: '/program-detail', params: { programId: program.id } })}
      accessibilityRole="button">
      <ThemedView type="backgroundElement" style={[styles.card, { borderColor: theme.border }]}>
        <View style={styles.cardText}>
          <ThemedText type="heading">{program.name}</ThemedText>
          <ThemedText type="small" themeColor="textSecondary">
            {weekRangeLabel(program, t)}
          </ThemedText>
          {detail && (
            <ThemedText type="small" themeColor="textSecondary">
              {detail}
            </ThemedText>
          )}
        </View>
        <ThemedText themeColor="textSecondary">{'›'}</ThemedText>
      </ThemedView>
    </Pressable>
  );
});

/** Reproduces the `gap` the old `styles.list` had, which a FlatList's cells don't inherit. */
function Separator() {
  return <View style={styles.separator} />;
}

const keyExtractor = (program: Program) => program.id;

const renderItem = ({ item }: ListRenderItemInfo<Program>) => <ProgramCard program={item} />;

export default function ProgramsScreen() {
  const theme = useTheme();
  const { scheme } = useAppTheme();
  const { t } = useTranslation();
  const library = useLibraryStore((state) => state.library);
  const [query, setQuery] = useState('');
  // Immediate value in the input, deferred value in the filter — see the note in (tabs)/index.tsx.
  const deferredQuery = useDeferredValue(query);

  const all = library?.programs ?? [];
  const matching = useMemo(() => {
    const needle = deferredQuery.trim().toLowerCase();
    if (!needle) return all;
    return all.filter((program) => program.name.toLowerCase().includes(needle));
    // The library, not `all`, is the honest dependency — see the note in (tabs)/index.tsx.
    // oxlint-disable-next-line react-hooks/exhaustive-deps
  }, [library, deferredQuery]);

  const fabColor = scheme === 'dark' ? theme.accent : theme.text;

  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: theme.background }]} edges={['top', 'left', 'right']}>
      {/* The header is an element, not an inline `() => <Header/>` — see the note in (tabs)/index.tsx. */}
      <FlatList
        data={matching}
        keyExtractor={keyExtractor}
        renderItem={renderItem}
        ItemSeparatorComponent={Separator}
        contentContainerStyle={styles.scrollContent}
        ListHeaderComponent={
          <View style={styles.listHeader}>
            <View style={styles.header}>
              <ThemedText type="subtitle">{t('programs.title')}</ThemedText>
              <View style={styles.headerActions}>
                <Pressable
                  onPress={() => router.push('/program-guide')}
                  hitSlop={8}
                  accessibilityRole="button"
                  accessibilityLabel={t('programs.helpLabel')}
                  style={styles.helpButton}>
                  <ThemedText type="heading" themeColor="textSecondary">
                    ?
                  </ThemedText>
                </Pressable>
              </View>
            </View>

            {/* Keyed off the whole library rather than what's visible, and carrying the count that
                used to be a line of its own — see Build. */}
            {all.length > 0 && (
              <SearchBar
                value={query}
                onChangeText={setQuery}
                placeholder={t('programs.searchPlaceholder', { count: all.length })}
              />
            )}
          </View>
        }
        /* Two different empty states, told apart — see the note in (tabs)/index.tsx. This one matters more if
           anything: the first-run card invites you to go read the YAML guide, which is a strange
           answer to a mistyped program name. */
        ListEmptyComponent={
          all.length > 0 ? (
            <NoResults query={deferredQuery} />
          ) : (
            <ThemedView type="backgroundElement" style={[styles.emptyState, { borderColor: theme.border }]}>
              <ThemedText type="heading">{t('programs.emptyTitle')}</ThemedText>
              <ThemedText type="small" themeColor="textSecondary" style={styles.emptyStateBody}>
                {t('programs.emptyBody')}
              </ThemedText>
              <Pressable
                onPress={() => router.push('/program-guide')}
                accessibilityRole="button"
                style={[styles.emptyStateButton, { borderColor: theme.border }]}>
                <ThemedText type="smallMedium">{t('programs.emptyYamlLink')}</ThemedText>
              </Pressable>
            </ThemedView>
          )
        }
      />

      <Pressable
        onPress={() => router.push('/program-editor')}
        accessibilityRole="button"
        accessibilityLabel={t('programs.newProgram')}
        style={({ pressed }) => [styles.fab, { backgroundColor: fabColor }, pressed && styles.pressed]}>
        <ThemedText type="title" style={[styles.fabPlus, { color: theme.onAccent }]}>
          +
        </ThemedText>
      </Pressable>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
  },
  scrollContent: {
    alignSelf: 'center',
    width: '100%',
    maxWidth: MaxContentWidth,
    paddingHorizontal: Spacing.three,
    paddingTop: Platform.select({ web: Spacing.six, default: Spacing.two }),
    paddingBottom: Spacing.six,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  helpButton: {
    width: 28,
    height: 28,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
  },
  headerActions: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  // What `styles.list`'s `marginTop` and `gap` became once the list stopped being one `View`.
  listHeader: {
    marginBottom: Spacing.three,
  },
  separator: {
    height: Spacing.two - 3,
  },
  emptyState: {
    borderRadius: 16,
    borderWidth: 1,
    padding: Spacing.three,
    gap: Spacing.one + 2,
  },
  emptyStateBody: {
    lineHeight: 18,
  },
  emptyStateButton: {
    alignSelf: 'flex-start',
    borderRadius: 10,
    borderWidth: 1,
    paddingVertical: Spacing.one + 2,
    paddingHorizontal: Spacing.two + 2,
    marginTop: 2,
  },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderRadius: 16,
    borderWidth: 1,
    padding: Spacing.two + 6,
  },
  cardText: {
    flex: 1,
    gap: 2,
  },
  fab: {
    position: 'absolute',
    right: Spacing.three,
    bottom: Spacing.four,
    width: 52,
    height: 52,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  fabPlus: {
    fontSize: 26,
    lineHeight: 28,
  },
  pressed: {
    opacity: 0.7,
  },
});
