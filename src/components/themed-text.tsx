import { StyleSheet, Text, type TextProps } from 'react-native';

import { Fonts, ThemeColor } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export type ThemedTextProps = TextProps & {
  type?:
    | 'default'
    | 'label'
    | 'title'
    | 'subtitle'
    | 'heading'
    | 'small'
    | 'smallMedium'
    | 'numeral'
    | 'code';
  themeColor?: ThemeColor;
};

export function ThemedText({ style, type = 'default', themeColor, ...rest }: ThemedTextProps) {
  const theme = useTheme();

  return (
    <Text
      style={[
        { color: theme[themeColor ?? 'text'] },
        type === 'default' && styles.default,
        type === 'label' && styles.label,
        type === 'title' && styles.title,
        type === 'subtitle' && styles.subtitle,
        type === 'heading' && styles.heading,
        type === 'small' && styles.small,
        type === 'smallMedium' && styles.smallMedium,
        type === 'numeral' && styles.numeral,
        type === 'code' && styles.code,
        style,
      ]}
      {...rest}
    />
  );
}

const styles = StyleSheet.create({
  default: {
    fontFamily: Fonts.body,
    fontSize: 16,
    lineHeight: 22,
  },
  label: {
    fontFamily: Fonts.display,
    fontSize: 11,
    letterSpacing: 1.4,
    textTransform: 'uppercase',
  },
  title: {
    fontFamily: Fonts.display,
    fontSize: 40,
    letterSpacing: -0.8,
  },
  subtitle: {
    fontFamily: Fonts.display,
    fontSize: 26,
    letterSpacing: -0.4,
  },
  heading: {
    fontFamily: Fonts.display,
    fontSize: 20,
  },
  small: {
    fontFamily: Fonts.body,
    fontSize: 13,
    lineHeight: 18,
  },
  smallMedium: {
    fontFamily: Fonts.bodyMedium,
    fontSize: 13,
    lineHeight: 18,
  },
  numeral: {
    fontFamily: Fonts.display,
    fontVariant: ['tabular-nums'],
  },
  code: {
    fontFamily: Fonts.displayMedium,
    fontSize: 12,
  },
});
