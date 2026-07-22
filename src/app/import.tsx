import { router } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

const changedItems = [
  { name: 'Front Lever', kind: 'new' as const, detail: 'new' },
  { name: 'Muscle-up', kind: 'new' as const, detail: 'new' },
  { name: 'Pull-ups', kind: 'updated' as const, detail: '4×8 → 4×10' },
  { name: 'L-Sit Hold', kind: 'updated' as const, detail: '20s → 25s' },
];

export default function ImportScreen() {
  const theme = useTheme();
  const close = () => router.back();

  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: theme.background }]} edges={['bottom', 'left', 'right']}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={[styles.grabber, { backgroundColor: theme.border }]} />
        <ThemedText type="subtitle">Import library</ThemedText>

        <View style={[styles.fileRow, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
          <View style={[styles.fileIcon, { borderColor: theme.textSecondary }]} />
          <View style={styles.fileText}>
            <ThemedText type="heading" style={styles.fileName}>
              exercises.yaml
            </ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              4.2 KB · picked from Files
            </ThemedText>
          </View>
        </View>

        <View style={styles.countsRow}>
          <View style={[styles.countCard, { backgroundColor: theme.accentSoft }]}>
            <ThemedText type="subtitle" themeColor="accentText">
              3
            </ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              new
            </ThemedText>
          </View>
          <View style={[styles.countCard, { backgroundColor: theme.accentCalmSoft }]}>
            <ThemedText type="subtitle" themeColor="accentCalmText">
              2
            </ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              updated
            </ThemedText>
          </View>
          <View
            style={[
              styles.countCard,
              { backgroundColor: theme.backgroundElement, borderWidth: 1, borderColor: theme.border },
            ]}>
            <ThemedText type="subtitle">1</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              workout
            </ThemedText>
          </View>
        </View>

        <View style={styles.changedList}>
          {changedItems.map((item) => (
            <View key={item.name} style={styles.changedRow}>
              <ThemedText
                style={[styles.changedGlyph, { color: item.kind === 'new' ? theme.accentText : theme.accentCalmText }]}>
                {item.kind === 'new' ? '+' : '↻'}
              </ThemedText>
              <ThemedText type="smallMedium" style={styles.changedName}>
                {item.name}
              </ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                {item.detail}
              </ThemedText>
            </View>
          ))}
        </View>

        <ThemedText type="small" themeColor="textSecondary" style={styles.note}>
          Updated items replace local tweaks. Sessions are never touched.
        </ThemedText>

        <View style={styles.buttonRow}>
          <Pressable onPress={close} style={[styles.cancelButton, { borderColor: theme.border }]}>
            <ThemedText type="heading" themeColor="textSecondary">
              Cancel
            </ThemedText>
          </Pressable>
          <Pressable onPress={close} style={[styles.mergeButton, { backgroundColor: theme.accent }]}>
            <ThemedText type="heading" style={{ color: theme.onAccent }}>
              Merge & import
            </ThemedText>
          </Pressable>
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
    paddingTop: Spacing.two,
    paddingBottom: Spacing.four,
  },
  grabber: {
    alignSelf: 'center',
    width: 40,
    height: 5,
    borderRadius: 3,
    marginBottom: Spacing.three - 2,
  },
  fileRow: {
    marginTop: Spacing.three - 2,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: 14,
    padding: Spacing.two + 4,
  },
  fileIcon: {
    width: 26,
    height: 32,
    borderRadius: 5,
    borderWidth: 1.5,
  },
  fileText: {
    flex: 1,
    gap: 2,
  },
  fileName: {},
  countsRow: {
    flexDirection: 'row',
    gap: Spacing.two,
    marginTop: Spacing.three - 2,
  },
  countCard: {
    flex: 1,
    alignItems: 'center',
    borderRadius: 12,
    paddingVertical: Spacing.two + 2,
  },
  changedList: {
    marginTop: Spacing.three - 2,
    gap: Spacing.two - 1,
  },
  changedRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two + 2,
  },
  changedGlyph: {
    width: 18,
    textAlign: 'center',
    fontWeight: '700',
  },
  changedName: {
    flex: 1,
  },
  note: {
    marginTop: Spacing.two + 4,
  },
  buttonRow: {
    flexDirection: 'row',
    gap: Spacing.three - 4,
    marginTop: Spacing.three,
  },
  cancelButton: {
    flex: 1,
    height: 52,
    borderRadius: 15,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  mergeButton: {
    flex: 1.4,
    height: 52,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
