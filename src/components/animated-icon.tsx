import * as SplashScreen from 'expo-splash-screen';
import { useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  Keyframe,
  useAnimatedStyle,
  useSharedValue,
  withDelay,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { KettleMark } from '@/components/kettle-mark';
import { Fonts } from '@/constants/theme';

const DURATION = 600;

function Dot({ delay }: { delay: number }) {
  const pulse = useSharedValue(1);

  useEffect(() => {
    pulse.value = withDelay(
      delay,
      withRepeat(withTiming(0.25, { duration: 600, easing: Easing.inOut(Easing.ease) }), -1, true),
    );
  }, [delay, pulse]);

  const style = useAnimatedStyle(() => ({ opacity: pulse.value }));

  return <Animated.View style={[styles.dot, style]} />;
}

function SplashContent() {
  return (
    <View style={styles.content}>
      <KettleMark size={140} color="#f3efe4" steamColor="#cf6a37" />
      <View style={styles.textBlock}>
        <Text style={styles.wordmark}>Kettle</Text>
        <Text style={styles.subtitle}>local-first workout tracker</Text>
      </View>
      <View style={styles.dotsRow}>
        <Dot delay={0} />
        <Dot delay={200} />
        <Dot delay={400} />
      </View>
    </View>
  );
}

export function AnimatedSplashOverlay() {
  const [animate, setAnimate] = useState(false);
  const [visible, setVisible] = useState(true);

  if (!visible) return null;

  const splashKeyframe = new Keyframe({
    0: {
      transform: [{ scale: 1 }],
      opacity: 1,
    },
    20: {
      opacity: 1,
    },
    70: {
      opacity: 0,
      easing: Easing.elastic(0.7),
    },
    100: {
      opacity: 0,
      transform: [{ scale: 1 }],
      easing: Easing.elastic(0.7),
    },
  });

  return animate ? (
    <Animated.View
      entering={splashKeyframe.duration(DURATION).withCallback((finished) => {
        'worklet';
        if (finished) {
          scheduleOnRN(setVisible, false);
        }
      })}
      style={styles.splashOverlay}>
      <SplashContent />
    </Animated.View>
  ) : (
    <View
      onLayout={() => {
        SplashScreen.hideAsync().finally(() => {
          setAnimate(true);
        });
      }}
      style={styles.splashOverlay}>
      <SplashContent />
    </View>
  );
}

const styles = StyleSheet.create({
  splashOverlay: {
    ...StyleSheet.absoluteFill,
    backgroundColor: '#17140d',
    experimental_backgroundImage: 'radial-gradient(120% 80% at 50% 34%, #221c12 0%, #17140d 62%)',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 1000,
  },
  content: {
    alignItems: 'center',
    gap: 30,
  },
  textBlock: {
    alignItems: 'center',
  },
  wordmark: {
    fontFamily: Fonts.displayBold,
    fontSize: 38,
    letterSpacing: -0.8,
    color: '#f3efe4',
  },
  subtitle: {
    fontFamily: Fonts.body,
    fontSize: 13.5,
    color: '#9a9384',
    marginTop: 6,
    letterSpacing: 0.2,
  },
  dotsRow: {
    flexDirection: 'row',
    gap: 7,
  },
  dot: {
    width: 7,
    height: 7,
    borderRadius: 4,
    backgroundColor: '#cf6a37',
  },
});
