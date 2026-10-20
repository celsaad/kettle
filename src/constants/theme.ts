/**
 * Kettle's brand palette: one warm token set, remapped for light/dark (the app shell follows the
 * OS color scheme, with a manual override). `RunnerColors` is separate and fixed — the live
 * session runner is always dark regardless of the shell's scheme, per the design.
 *
 * The values live in `theme.json`, not here, because the native app reads the same file: its
 * Compose theme is generated from it on every build (`native/android/theme`). Edit a color there and
 * both UIs change. This file keeps the names RN code imports and the notes below, which JSON can't
 * hold; the contrast ratios they record are re-checked against the generated colors by
 * `GeneratedThemeTest` on the native side.
 */

import '@/global.css';

import theme from '@/constants/theme.json';

/**
 * Contrast notes, measured against WCAG 2.1 AA (4.5:1 for body text, 3:1 for large):
 *
 * - `light.textSecondary` was `#777166`, which measured 4.41 on `background` and 4.01 on
 *   `backgroundSelected` — failing on two of its three surfaces, and it's the app's most-used color
 *   (every caption, count and summary line). Darkened to `#6b6558`: 5.28 / 5.79 / 4.79.
 * - `light.onAccent` (white) on `light.accent` is **3.64** — AA-large only. That's fine where it's
 *   used, the 20px semibold "Start session" label, which clears the 18.66px-bold threshold, but it
 *   has no headroom: don't reuse this pairing for body-sized text without darkening `accent` first.
 * - The runner's soft pill labels had the same problem and get their own tokens; see `RunnerColors`.
 * - The training calendar's shades (`calendarLevel1`–`3`) are held to **3:1 against both the card and
 *   the empty-day cell** — WCAG 1.4.11's bar for a graphic you need to read the chart, which here is
 *   whether a day was trained at all. Card / empty, dark: 3.73 / 3.20, 5.61 / 4.81, 7.61 / 6.53;
 *   light: 3.64 / 3.23, 5.68 / 5.04, 9.69 / 8.59. The steps *between* levels are 1.4–1.7 and don't
 *   have to clear it: every week's row label carries its count and time, and the legend names the
 *   buckets. The design's first draft had a lowest step at 1.6:1 against the empty cell, which is why
 *   these are measured rather than eyeballed.
 *
 * Tokens whose name doesn't say it all:
 *
 * - `onAccent` is the text/icon color to place on top of an accent-colored surface (buttons, active
 *   pills).
 * - `calendarEmpty` is a rest day on the training calendar, and `calendarLevel1`–`3` trained days by
 *   duration. More is darker on a light card and brighter on a dark one, so the two ramps run in
 *   opposite directions.
 */
export const Colors = theme.Colors;

export type ThemeColor = keyof typeof Colors.light & keyof typeof Colors.dark;

/**
 * `accentOnSoft` and `accentCalmOnSoft` are label colors for text sitting *on* the soft pill
 * backgrounds, which are translucent and so composite against `background`. The fill colors fail AA
 * there as text — `accent` measures 4.17 and `accentCalm` 3.79 on their own soft backgrounds, and the
 * pill labels are 12px. These lightened variants measure 5.68 and 5.75. Separate tokens rather than
 * lightening the fills, so the shapes keep their intended weight.
 */
export const RunnerColors = theme.RunnerColors;

/** Not in `theme.json`: Compose needs the font files bundled as resources before it can use these. */
export const Fonts = {
  display: 'SpaceGrotesk_600SemiBold',
  displayMedium: 'SpaceGrotesk_500Medium',
  displayBold: 'SpaceGrotesk_700Bold',
  body: 'HankenGrotesk_400Regular',
  bodyMedium: 'HankenGrotesk_500Medium',
  bodySemiBold: 'HankenGrotesk_600SemiBold',
} as const;

export const Spacing = theme.Spacing;

export const MaxContentWidth = theme.MaxContentWidth;
