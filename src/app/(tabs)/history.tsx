import { useState } from 'react';
import { Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useLibraryStore } from '@/state/library-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { historySessionsView, historyStats as historyStatsFor } from '@/state/selectors';
import { exportSession } from '@/storage/export';

export default function HistoryScreen() {
  const theme = useTheme();
  const library = useLibraryStore((state) => state.library);
  const sessions = useSessionHistoryStore((state) => state.sessions);
  const historySessions = library ? historySessionsView(sessions, library) : [];
  const historyStats = historyStatsFor(sessions);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: theme.background }]} edges={['top', 'left', 'right']}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.header}>
          <ThemedText type="subtitle">History</ThemedText>
          <ThemedText themeColor="textSecondary">July 2026</ThemedText>
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

        <View style={styles.list}>
          {historySessions.map((session) => {
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
                      <ThemedText type="small" themeColor="textSecondary" style={styles.expandedNote}>
                        self-describing · stays valid if the definition later changes
                      </ThemedText>
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
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: Spacing.two,
  },
  expandedNote: {
    flex: 1,
  },
});
