import { StyleSheet, Text, type TextProps } from 'react-native';

import { Fonts, ThemeColor, Type } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export type ThemedTextProps = TextProps & {
  type?: 'default' | 'label' | 'title' | 'subtitle' | 'heading' | 'small' | 'smallMedium' | 'numeral' | 'code';
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

/** A `Type` style from theme.json, which native generates its `KettleType` from too. */
function fromTheme({ font, ...sizes }: (typeof Type)[keyof typeof Type]) {
  return { fontFamily: Fonts[font], ...sizes };
}

// The sizes are theme.json's, shared with native. What only RN draws (uppercase, tabular figures)
// stays here.
const styles = StyleSheet.create({
  default: fromTheme(Type.default),
  label: { ...fromTheme(Type.label), textTransform: 'uppercase' },
  title: fromTheme(Type.title),
  subtitle: fromTheme(Type.subtitle),
  heading: fromTheme(Type.heading),
  small: fromTheme(Type.small),
  smallMedium: fromTheme(Type.smallMedium),
  numeral: { ...fromTheme(Type.numeral), fontVariant: ['tabular-nums'] },
  code: fromTheme(Type.code),
});
