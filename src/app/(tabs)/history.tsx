import { useMemo, useState } from 'react';
import { Alert, Platform, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useLibraryStore } from '@/state/library-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { historySessionsView, historyStats as historyStatsFor, type HistorySessionView } from '@/state/selectors';
import { exportSession } from '@/storage/export';

export default function HistoryScreen() {
  const theme = useTheme();
  const library = useLibraryStore((state) => state.library);
  const sessions = useSessionHistoryStore((state) => state.sessions);
  const deleteSession = useSessionHistoryStore((state) => state.deleteSession);
  const [query, setQuery] = useState('');
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const historySessions = useMemo(() => (library ? historySessionsView(sessions, library) : []), [sessions, library]);

  const searching = query.trim().length > 0;

  // Matching happens on the view rather than on the raw sessions because the workout *name* the user
  // types is only resolved from the library by historySessionsView; a Session only carries the id.
  // No filter pills alongside the search box (unlike Library): a session has no single type to pill on
  // — it's whatever mix of exercises got logged — and a date-range pill would fight the stat tiles,
  // which already declare their own scope in the header. Name search is the one axis where the user
  // knows in advance what they're looking for.
  const visibleSessions = useMemo(() => {
    if (!searching) return historySessions;
    const needle = query.trim().toLowerCase();
    return historySessions.filter((session) => session.workoutName.toLowerCase().includes(needle));
  }, [historySessions, query, searching]);

  // The tiles aggregate what's actually listed below them, not the whole archive — three all-time
  // numbers sitting on top of a filtered list would be describing sessions that aren't on screen. The
  // header label switches from "All time" to a match count so the narrowed scope is stated, not implied.
  const historyStats = useMemo(() => {
    if (!searching) return historyStatsFor(sessions);
    const visibleIds = new Set(visibleSessions.map((session) => session.id));
    return historyStatsFor(sessions.filter((session) => visibleIds.has(session.id)));
  }, [sessions, visibleSessions, searching]);

  const confirmDelete = (session: HistorySessionView) => {
    Alert.alert('Delete session?', `"${session.workoutName}" on ${session.day} ${session.month} will be permanently removed.`, [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: () => deleteSession(session.id),
      },
    ]);
  };

  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: theme.background }]} edges={['top', 'left', 'right']}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.header}>
          <ThemedText type="subtitle">History</ThemedText>
          {/*
            Says "All time" rather than a month: the list below is every session ever, and the three
            tiles are historyStats(sessions) over that same unfiltered set. This used to be a
            hardcoded "July 2026", which was wrong twice over — frozen to one month, and labelling
            all-time numbers as if they were that month's. Searching narrows both the list and the
            tiles, so the label has to stop claiming "all time" and say what the subset is instead.
          */}
          <ThemedText themeColor="textSecondary">
            {searching ? `${visibleSessions.length} of ${sessions.length}` : 'All time'}
          </ThemedText>
        </View>

        <View style={styles.statsRow}>
          <ThemedView type="backgroundElement" style={[styles.statCard, { borderColor: theme.border }]}>
            <ThemedText type="heading">{historyStats.sessions}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              sessions
            </ThemedText>
          </ThemedView>
          <ThemedView type="backgroundElement" style={[styles.statCard, { borderColor: theme.border }]}>
            <ThemedText type="heading">{historyStats.hours}h {historyStats.minutes}m</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              time
            </ThemedText>
          </ThemedView>
          <ThemedView type="backgroundElement" style={[styles.statCard, { borderColor: theme.border }]}>
            <ThemedText type="heading">{historyStats.sets}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              sets
            </ThemedText>
          </ThemedView>
        </View>

        <View style={[styles.searchBar, { borderColor: theme.border, backgroundColor: theme.backgroundElement }]}>
          <ThemedText themeColor="textSecondary">⌕</ThemedText>
          <TextInput
            value={query}
            onChangeText={setQuery}
            placeholder="Search workouts"
            placeholderTextColor={theme.textSecondary}
            style={[styles.searchInput, { color: theme.text }]}
          />
        </View>

        <View style={styles.list}>
          {visibleSessions.map((session) => {
            const expanded = expandedId === session.id;
            return (
              <ThemedView key={session.id} type="backgroundElement" style={[styles.card, { borderColor: theme.border }]}>
                <Pressable
                  onPress={() => setExpandedId(expanded ? null : session.id)}
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
                      {session.mixed ? ' · mixed' : ''}
                    </ThemedText>
                  </View>
                  <ThemedText themeColor="textSecondary">{expanded ? '⌄' : '›'}</ThemedText>
                </Pressable>

                {expanded && (
                  <View style={[styles.expandedContent, { borderTopColor: theme.border }]}>
                    {session.entries.map((entry, index) => (
                      <View key={`${entry.exerciseName}-${index}`} style={styles.entryRow}>
                        <ThemedText
                          type="small"
                          themeColor="textSecondary"
                          numberOfLines={1}
                          style={styles.entryLabel}>
                          {entry.exerciseName}
                        </ThemedText>
                        <ThemedText type="smallMedium" style={styles.entrySummary}>
                          {entry.summary}
                        </ThemedText>
                      </View>
                    ))}
                    <View style={styles.expandedFooter}>
                      <Pressable onPress={() => confirmDelete(session)} hitSlop={8}>
                        <ThemedText type="small" themeColor="textSecondary">
                          Delete
                        </ThemedText>
                      </Pressable>
                      <Pressable onPress={() => exportSession(session.id).catch(() => { })} hitSlop={8}>
                        <ThemedText type="small" themeColor="accentText">
                          Export
                        </ThemedText>
                      </Pressable>
                    </View>
                  </View>
                )}
              </ThemedView>
            );
          })}
        </View>
      </ScrollView>
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
  },
  statsRow: {
    flexDirection: 'row',
    gap: Spacing.two,
    marginTop: Spacing.three,
  },
  statCard: {
    flex: 1,
    borderRadius: 14,
    borderWidth: 1,
    padding: Spacing.two + 4,
  },
  // Copied value-for-value from Library's search bar, deliberately: the two list screens should read
  // as one pattern, not two takes on it.
  searchBar: {
    marginTop: Spacing.three,
    height: 44,
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
  list: {
    marginTop: Spacing.three,
    gap: Spacing.two,
  },
  card: {
    borderRadius: 16,
    borderWidth: 1,
    padding: Spacing.two + 7,
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
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
  expandedContent: {
    marginTop: Spacing.two + 4,
    paddingTop: Spacing.two + 4,
    borderTopWidth: 1,
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
