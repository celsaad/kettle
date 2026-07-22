import { StyleSheet, View } from 'react-native';

import { RunnerColors } from '@/constants/theme';

export function SessionProgressDots({ total, activeIndex }: { total: number; activeIndex: number }) {
  return (
    <View style={styles.row}>
      {Array.from({ length: total }).map((_, index) => (
        <View key={index} style={[styles.dot, index === activeIndex && styles.dotActive]} />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    gap: 6,
  },
  dot: {
    width: 9,
    height: 4,
    borderRadius: 2,
    backgroundColor: 'rgba(243,239,228,0.22)',
  },
  dotActive: {
    width: 22,
    backgroundColor: RunnerColors.accent,
  },
});
