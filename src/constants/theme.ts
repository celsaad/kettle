/**
 * Kettle's brand palette: one warm token set, remapped for light/dark (the app shell follows the
 * OS color scheme, with a manual override). `RunnerColors` is separate and fixed — the live
 * session runner is always dark regardless of the shell's scheme, per the design.
 */

import '@/global.css';

export const Colors = {
  light: {
    text: '#26221c',
    textSecondary: '#777166',
    background: '#f7f4ef',
    backgroundElement: '#ffffff',
    backgroundSelected: '#efe9df',
    border: 'rgba(30,25,15,0.10)',
    accent: '#cf6a37',
    accentText: '#a1502a',
    accentSoft: 'rgba(207,106,55,0.12)',
    accentCalm: '#3f82c0',
    accentCalmText: '#2f6493',
    accentCalmSoft: 'rgba(63,130,192,0.14)',
    /** Text/icon color to place on top of an accent-colored surface (buttons, active pills). */
    onAccent: '#ffffff',
  },
  dark: {
    text: '#f0ece2',
    textSecondary: '#948d80',
    background: '#1a1712',
    backgroundElement: '#241f17',
    backgroundSelected: 'rgba(240,236,226,0.08)',
    border: 'rgba(240,236,226,0.10)',
    accent: '#e07d47',
    accentText: '#e8a172',
    accentSoft: 'rgba(224,125,71,0.16)',
    accentCalm: '#3f82c0',
    accentCalmText: '#7fb0dd',
    accentCalmSoft: 'rgba(63,130,192,0.2)',
    onAccent: '#1a1712',
  },
} as const;

export type ThemeColor = keyof typeof Colors.light & keyof typeof Colors.dark;

export const RunnerColors = {
  text: '#f3efe4',
  textSecondary: '#9a9384',
  background: '#17140d',
  backgroundElement: '#221d12',
  border: 'rgba(243,239,228,0.12)',
  accent: '#cf6a37',
  accentSoft: 'rgba(207,106,55,0.16)',
  accentCalm: '#3f82c0',
  accentCalmSoft: 'rgba(63,130,192,0.16)',
} as const;

export const Fonts = {
  display: 'SpaceGrotesk_600SemiBold',
  displayMedium: 'SpaceGrotesk_500Medium',
  displayBold: 'SpaceGrotesk_700Bold',
  body: 'HankenGrotesk_400Regular',
  bodyMedium: 'HankenGrotesk_500Medium',
  bodySemiBold: 'HankenGrotesk_600SemiBold',
} as const;

export const Spacing = {
  half: 2,
  one: 4,
  two: 8,
  three: 16,
  four: 24,
  five: 32,
  six: 64,
} as const;

export const MaxContentWidth = 480;
