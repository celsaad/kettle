import { router } from 'expo-router';
import { memo, useCallback, useDeferredValue, useMemo, useState } from 'react';
import { Alert, FlatList, Platform, Pressable, StyleSheet, View, type ListRenderItemInfo } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ListHeaderRule, ListRowMinHeight, ListRowSeparator, ListRowVerticalPadding } from '@/components/list-row';
import { NoResults } from '@/components/no-results';
import { SearchBar } from '@/components/search-bar';
import { ThemedText } from '@/components/themed-text';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { formatSessionCount, formatSetCount } from '@/domain/format';
import { useTheme } from '@/hooks/use-theme';
import { useLibraryStore } from '@/state/library-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { historyStats as historyStatsFor } from '@/state/selectors/history-stats';
import { historySessionsView, type HistorySessionView } from '@/state/selectors/history-views';
import { exportSession, exportSessions } from '@/storage/export';

export { RouteErrorBoundary as ErrorBoundary } from '@/components/error-fallback';

/**
 * One session card, memoised and module-level for the reasons in `(tabs)/index.tsx`'s note — and this is the
 * list where it pays: History is the only one that grows every time the app is used, and every card
 * carries an expandable body of per-exercise rows.
 *
 * `onToggle` and `onDelete` take the session rather than being bound per row, so the screen can hand
 * down two `useCallback`s that never change. Only the card whose `expanded` actually flipped
 * re-renders; the rest compare equal and bail out.
 */
const SessionCard = memo(function SessionCard({
  session,
  expanded,
  onToggle,
  onDelete,
}: {
  session: HistorySessionView;
  expanded: boolean;
  onToggle: (id: string) => void;
  onDelete: (session: HistorySessionView) => void;
}) {
  const { t } = useTranslation();

  return (
    <View style={styles.card}>
      <Pressable
        onPress={() => onToggle(session.id)}
        accessibilityRole="button"
        // The row's own text supplies the name; `expanded` is the part a screen reader
        // can't infer from the chevron glyph.
        accessibilityState={{ expanded }}
        style={styles.cardHeader}>
        <View style={styles.dateBadge}>
          <ThemedText type="heading">{session.day}</ThemedText>
          <ThemedText type="code" themeColor="textSecondary">
            {session.month}
          </ThemedText>
        </View>
        <View style={styles.cardHeaderText}>
          <ThemedText type="heading">{session.workoutName}</ThemedText>
          <ThemedText type="small" themeColor="textSecondary">
            {session.durationLabel} · {session.setsLabel}
            {session.mixed ? ` · ${t('history.mixed')}` : ''}
          </ThemedText>
        </View>
        <ThemedText themeColor="textSecondary">{expanded ? '⌄' : '›'}</ThemedText>
      </Pressable>

      {expanded && (
        <View style={styles.expandedContent}>
          {session.entries.map((entry, index) => (
            <View key={`${entry.exerciseName}-${index}`} style={styles.entryRow}>
              <ThemedText type="small" themeColor="textSecondary" numberOfLines={1} style={styles.entryLabel}>
                {entry.exerciseName}
              </ThemedText>
              <ThemedText type="smallMedium" style={styles.entrySummary}>
                {entry.summary}
              </ThemedText>
            </View>
          ))}
          <View style={styles.expandedFooter}>
            <Pressable onPress={() => onDelete(session)} accessibilityRole="button" hitSlop={8}>
              <ThemedText type="small" themeColor="textSecondary">
                {t('common.delete')}
              </ThemedText>
            </Pressable>
            {/* Hidden rather than disabled while a session is still running: the runner owns that file
                and writes through its own copy, so an edit made here would be overwritten by the next
                set logged. The store's `editEntry` refuses the same case. */}
            {session.editable && (
              <Pressable
                onPress={() => router.push({ pathname: '/session-editor', params: { id: session.id } })}
                accessibilityRole="button"
                hitSlop={8}>
                <ThemedText type="small" themeColor="textSecondary">
                  {t('history.edit')}
                </ThemedText>
              </Pressable>
            )}
            <Pressable onPress={() => exportSession(session.id).catch(() => {})} accessibilityRole="button" hitSlop={8}>
              <ThemedText type="small" themeColor="accentText">
                {t('common.export')}
              </ThemedText>
            </Pressable>
          </View>
        </View>
      )}
    </View>
  );
});

const keyExtractor = (session: HistorySessionView) => session.id;

export default function HistoryScreen() {
  const theme = useTheme();
  const { t } = useTranslation();
  const library = useLibraryStore((state) => state.library);
  const sessions = useSessionHistoryStore((state) => state.sessions);
  const deleteSession = useSessionHistoryStore((state) => state.deleteSession);
  // Collected since hydration existed and rendered nowhere: a session file that wouldn't parse, or one
  // the disk wouldn't take mid-workout, was known about and never mentioned. History is where a
  // session that's missing or short would be noticed, so it's where the reason belongs.
  const sessionErrors = useSessionHistoryStore((state) => state.errors);
  const [query, setQuery] = useState('');
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const historySessions = useMemo(() => (library ? historySessionsView(sessions, library) : []), [sessions, library]);

  // Immediate value in the input, deferred value in the filter — see the note in (tabs)/index.tsx. This is
  // the screen it was added for: the tiles below aggregate over every entry of every matching session,
  // so a keystroke here does real work on a long log rather than a name comparison.
  const deferredQuery = useDeferredValue(query);
  // Derived from the deferred value, not the typed one, so the label, the tiles and the list can never
  // describe three different sets mid-keystroke.
  const searching = deferredQuery.trim().length > 0;

  // Matching happens on the view rather than on the raw sessions because the workout *name* the user
  // types is only resolved from the library by historySessionsView; a Session only carries the id.
  // No filter pills alongside the search box (unlike Library): a session has no single type to pill on
  // — it's whatever mix of exercises got logged — and a date-range pill would fight the stat tiles,
  // which already declare their own scope in the header. Name search is the one axis where the user
  // knows in advance what they're looking for.
  const visibleSessions = useMemo(() => {
    if (!searching) return historySessions;
    const needle = deferredQuery.trim().toLowerCase();
    return historySessions.filter((session) => session.workoutName.toLowerCase().includes(needle));
  }, [historySessions, deferredQuery, searching]);

  // The tiles aggregate what's actually listed below them, not the whole archive — three all-time
  // numbers sitting on top of a filtered list would be describing sessions that aren't on screen. The
  // header label switches from "All time" to a match count so the narrowed scope is stated, not implied.
  const historyStats = useMemo(() => {
    if (!searching) return historyStatsFor(sessions);
    const visibleIds = new Set(visibleSessions.map((session) => session.id));
    return historyStatsFor(sessions.filter((session) => visibleIds.has(session.id)));
  }, [sessions, visibleSessions, searching]);

  // The whole header's numbers, as one sentence. Prefixed with the match count while searching, so a
  // narrowed set says both what it is ("3 of 41") and what it adds up to, rather than quietly
  // relabelling a subset.
  // Each count is formatted before it is interpolated, never written into the sentence as a bare
  // number: `{{sessions}} sessions` renders "1 sessions" the first week you use the app, which is the
  // exact bug `formatSetCount`'s own note records History shipping once already. The separator and the
  // ordering stay in the locale string so a translator can move them.
  const totals = t('history.summaryLine', {
    sessions: formatSessionCount(historyStats.sessions),
    time: `${historyStats.hours}h ${historyStats.minutes}m`,
    sets: formatSetCount(historyStats.sets),
  });
  const summaryLine = searching
    ? `${t('history.matchCount', { shown: visibleSessions.length, total: sessions.length })} · ${totals}`
    : totals;

  // Both wrapped so every card can hold the same two function identities and stay memo-equal; without
  // that, one expand re-renders every card on screen.
  const confirmDelete = useCallback(
    (session: HistorySessionView) => {
      Alert.alert(
        t('history.deleteConfirmTitle'),
        t('history.deleteConfirmBody', { name: session.workoutName, day: session.day, month: session.month }),
        [
          { text: t('common.cancel'), style: 'cancel' },
          {
            text: t('common.delete'),
            style: 'destructive',
            onPress: () => deleteSession(session.id),
          },
        ],
      );
    },
    [t, deleteSession],
  );

  const toggleExpanded = useCallback((id: string) => {
    setExpandedId((current) => (current === id ? null : id));
  }, []);

  const renderItem = useCallback(
    ({ item }: ListRenderItemInfo<HistorySessionView>) => (
      <SessionCard session={item} expanded={expandedId === item.id} onToggle={toggleExpanded} onDelete={confirmDelete} />
    ),
    [expandedId, toggleExpanded, confirmDelete],
  );

  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: theme.background }]} edges={['top', 'left', 'right']}>
      {/* The header is an element, not an inline `() => <Header/>` — see the note in (tabs)/index.tsx. That
          matters here and not only in principle: the search box below lives in it, and a remounted
          header loses focus on every keystroke. */}
      <FlatList
        data={visibleSessions}
        keyExtractor={keyExtractor}
        renderItem={renderItem}
        ItemSeparatorComponent={ListRowSeparator}
        contentContainerStyle={styles.scrollContent}
        ListHeaderComponent={
          <View style={styles.listHeader}>
            <View style={styles.header}>
              <ThemedText type="subtitle">{t('history.title')}</ThemedText>
              {/*
            Hidden rather than disabled at zero, since the empty list below already says there's
            nothing to export. "Export all" rather than the bare "Export" the Library header uses:
            each expanded card carries its own Export for one session, and two identical labels a
            scroll apart meaning different amounts of data is the kind of thing you only find out
            about after sharing the wrong one.
          */}
              <View style={styles.headerActions}>
                {/* The way to the numbers that used to be six tiles at the top of this screen. Always
                    offered, even with an empty log — arriving at zeros explains what the screen is,
                    whereas a control that appears only once you have history is one you never learn
                    about on the run that would have taught you. */}
                <Pressable onPress={() => router.push('/analytics')} accessibilityRole="button" hitSlop={8}>
                  <ThemedText type="smallMedium" themeColor="accentText">
                    {t('history.stats')}
                  </ThemedText>
                </Pressable>
                {sessions.length > 0 && (
                  <Pressable onPress={() => exportSessions(sessions).catch(() => {})} accessibilityRole="button" hitSlop={8}>
                    <ThemedText type="smallMedium" themeColor="accentText">
                      {t('history.exportAll')}
                    </ThemedText>
                  </Pressable>
                )}
              </View>
            </View>
            {/*
              One line where six stat cards used to be.

              The cards were right about *what* to show and wrong about how much room it was worth: two
              labelled rows of three filled the entire first screen of a phone, so the log this tab
              exists for started below the fold. The same three all-time numbers set as one line of
              text cost a single row and still answer "is my history all here", which is the only
              question this line has to answer at a glance. The breakdown moved behind `Stats` above,
              where it has room to be more than three numbers.

              It keeps narrowing with the search, exactly as the cards did, and says so — the count is
              the honest label for a filtered subset, and calling one "all time" was the specific
              dishonesty the hardcoded "July 2026" it replaced was guilty of.
            */}
            <ThemedText themeColor="textSecondary" style={styles.countLabel}>
              {summaryLine}
            </ThemedText>

            {sessionErrors.length > 0 && (
              <View style={[styles.problemCard, { borderColor: theme.accentText }]}>
                <ThemedText type="smallMedium" style={{ color: theme.accentText }}>
                  {t('history.problemsTitle', { count: sessionErrors.length })}
                </ThemedText>
                {sessionErrors.map((problem) => (
                  <ThemedText key={problem} type="small" themeColor="textSecondary">
                    {problem}
                  </ThemedText>
                ))}
              </View>
            )}

            <SearchBar value={query} onChangeText={setQuery} placeholder={t('history.searchPlaceholder')} />

            <ListHeaderRule />
          </View>
        }
        /* No first-run half, same as Library: an empty log is what a new install *is*, and the stat
           tiles above already read 0 · 0h 0m · 0. This covers only the blank body a search creates. */
        ListEmptyComponent={sessions.length > 0 ? <NoResults query={deferredQuery} /> : null}
      />
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
    alignItems: 'baseline',
    // Holds its height with no action button, so the summary line below doesn't jump when the first
    // session lands and "Export all" appears.
    minHeight: 28,
  },
  headerActions: {
    flexDirection: 'row',
    alignItems: 'baseline',
    gap: Spacing.three,
  },
  // Matches Library and Build, which both carry their count on its own line under the title.
  countLabel: {
    marginTop: 2,
  },
  problemCard: {
    marginTop: Spacing.two,
    gap: 2,
    borderWidth: 1,
    borderRadius: 12,
    padding: Spacing.two,
  },
  // What `styles.list`'s `marginTop` and `gap` became once the list stopped being one `View`.
  // The gap that used to sit here belongs to `ListHeaderRule`, above its line rather than below it.
  listHeader: {},
  /**
   * A row, not a card — matching the three library lists, which stopped being cards in the same pass.
   *
   * This one stays a vertical container rather than using `ListRow` because it expands in place: the
   * pressable header is a row *inside* it and the per-exercise detail unfolds underneath. It shares
   * the metrics, which is the whole reason those are exported — History being the one tab whose list
   * still looked boxed is exactly the mismatch this pass exists to remove.
   */
  card: {
    paddingVertical: ListRowVerticalPadding,
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: ListRowMinHeight,
    gap: Spacing.two + 4,
  },
  dateBadge: {
    width: 38,
    alignItems: 'center',
  },
  cardHeaderText: {
    flex: 1,
    gap: 2,
  },
  /**
   * No rule above the detail. It had one while this was a card, where the line separated two parts of
   * one boxed object — but now that rows are divided by hairlines of exactly that weight, a second
   * one *inside* an expanded row reads as the boundary between two rows and splits the session in
   * half. The indent and the gap say "this belongs to the row above" without drawing anything.
   */
  expandedContent: {
    marginTop: Spacing.two + 4,
    gap: Spacing.one + 4,
  },
  entryRow: {
    flexDirection: 'row',
    // Both sides are 13px/18px text, so their first lines align exactly even when the summary wraps.
    alignItems: 'flex-start',
    gap: Spacing.two,
    justifyContent: 'space-between',
  },
  // The name is an identifier, so it takes whatever width is left over and ellipsizes rather than
  // wrapping — but it can't squeeze the summary out, since that's the actual logged data.
  entryLabel: {
    flex: 1,
  },
  // A long summary (an emom's "20 min · 240 reps", or a hold with many sets) wraps onto a second line
  // instead of running off the card. flexShrink lets it give ground before it has to wrap.
  entrySummary: {
    flexShrink: 1,
    textAlign: 'right',
  },
  expandedFooter: {
    marginTop: Spacing.one,
    flexDirection: 'row',
    // Both actions anchor right, destructive one first: Export keeps the outermost, easiest-to-hit
    // spot, and Delete stays muted rather than accented so it never reads as the intended action.
    justifyContent: 'flex-end',
    alignItems: 'center',
    gap: Spacing.two + 4,
  },
});
