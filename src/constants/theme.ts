/**
 * Kettle's brand palette: one warm token set, remapped for light/dark (the app shell follows the
 * OS color scheme, with a manual override). `RunnerColors` is separate and fixed — the live
 * session runner is always dark regardless of the shell's scheme, per the design.
 */

import '@/global.css';

/**
 * Contrast notes, measured against WCAG 2.1 AA (4.5:1 for body text, 3:1 for large):
 *
 * - `light.textSecondary` was `#777166`, which measured 4.41 on `background` and 4.01 on
 *   `backgroundSelected` — failing on two of its three surfaces, and it's the app's most-used color
 *   (every caption, count and summary line). Darkened to `#6b6558`: 5.28 / 5.79 / 4.79.
 * - `light.onAccent` (white) on `light.accent` is **3.64** — AA-large only. That's fine where it's
 *   used, the 20px semibold "Start session" label, which clears the 18.66px-bold threshold, but it
 *   has no headroom: don't reuse this pairing for body-sized text without darkening `accent` first.
 * - The runner's soft pill labels had the same problem and get their own tokens below.
 */
export const Colors = {
  light: {
    text: '#26221c',
    textSecondary: '#6b6558',
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
  /**
   * Label colors for text sitting *on* the soft pill backgrounds, which are translucent and so
   * composite against `background`. The fill colors above fail AA there as text — `accent` measures
   * 4.17 and `accentCalm` 3.79 on their own soft backgrounds, and the pill labels are 12px. These
   * lightened variants measure 5.68 and 5.75. Separate tokens rather than lightening the fills, so
   * the shapes keep their intended weight.
   */
  accentOnSoft: '#dd8a5c',
  accentCalmOnSoft: '#6ba3d6',
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
