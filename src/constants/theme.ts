/**
 * Kettle's brand palette: one warm token set, remapped for light/dark (the app shell follows the
 * OS color scheme, with a manual override). `theme.json`'s `RunnerColors` is separate and ignores the
 * shell's scheme: the live session runner's fields, which the step's clock picks between. Its notes are
 * below, without an export.
 *
 * The values live in `theme.json`, not here, because the native app reads the same file: its
 * Compose theme is generated from it on every build (`native/android/theme`). Edit a color there and
 * both UIs change. This file keeps the names RN code imports and the notes below, which JSON can't
 * hold; the contrast ratios they record are re-checked against the generated colors by
 * `GeneratedThemeTest` on the native side.
 */

import theme from '@/constants/theme.json';

/**
 * Contrast notes, measured against WCAG 2.1 AA (4.5:1 for body text, 3:1 for large):
 *
 * - `light.textSecondary` was `#777166`, which measured 4.41 on `background` and 4.01 on
 *   `backgroundSelected` — failing on two of its three surfaces, and it's the app's most-used color
 *   (every caption, count and summary line). Darkened to `#6b6558`: 5.28 on `background`, 5.79 on
 *   `backgroundElement`, 4.79 on `backgroundSelected`.
 * - `dark.backgroundSelected` is translucent and composites over `background` to #2b2823, where
 *   `textSecondary` measures **4.46**, short of 4.5. So a filled field (search, Library's Type) takes
 *   `backgroundElement` in dark (4.97) and `backgroundSelected` only in light (4.79); see `fieldFill`.
 * - `light.onAccent` (white) on `light.accent` is **3.64** — AA-large only. That's fine where it's
 *   used, the 20px semibold "Start session" label, which clears the 18.66px-bold threshold, but it
 *   has no headroom: don't reuse this pairing for body-sized text without darkening `accent` first.
 * - The training calendar sits on the page since #414, and its marks are held to **3:1 against
 *   `background`** — WCAG 1.4.11's bar for a graphic you need to read the chart, which here is
 *   whether a day was trained at all. The rest day's outline (`calendarEmpty`) and the three shades,
 *   dark: 3.85, 4.07, 6.12, 8.32; light: 3.56, 3.32, 5.18, 8.83. The steps *between* levels are
 *   1.4–1.7 and don't have to clear it: every week's row label carries its count and time, and the
 *   legend names the buckets. An early draft had a lowest step at 1.6:1 against the empty cell, which
 *   is why these are measured rather than eyeballed.
 *
 * Tokens whose name doesn't say it all:
 *
 * - `onAccent` is the text/icon color to place on top of an accent-colored surface (buttons, active
 *   pills).
 * - `calendarEmpty` is a rest day's outline on the training calendar, and `calendarLevel1`–`3`
 *   trained days by duration. More is darker on a light card and brighter on a dark one, so the two ramps run in
 *   opposite directions.
 */
export const Colors = theme.Colors;

export type ThemeColor = keyof typeof Colors.light & keyof typeof Colors.dark;

/*
 * `RunnerColors`' notes. The tokens live in `theme.json`, which only native's runner reads since the
 * flip release (#308), so there is no export to hang these on.
 *
 * The runner's background says what the step's clock is doing (#418), and each field is a whole token
 * set, so a screen reads the same names on any of them:
 *
 * - `pace`, black: you set the pace (a reps set, a hold or cardio with no end of its own, anything
 *   paused), and every screen around a session.
 * - `getReady`: `pace` with an orange `numeral`, for the count-in and a rest's last `TICK_SECONDS`
 *   (`RunnerTiming.kt`), the seconds that tick. Every other token is pace's, which `GeneratedThemeTest`
 *   holds, so an edit to `pace` has to be made here too.
 * - `work`, orange: the clock runs the work (HIIT, EMOM, AMRAP, cardio with a duration, a hold that ends
 *   itself).
 * - `rest`, blue.
 *
 * `accent` is each field's ink, so the one filled button is the field's inverse and orange is never a
 * button (`onAccent` is its label). `numeral` is the big clock's colour, which only `getReady` sets apart.
 * `outline` is a control's edge, held to WCAG 1.4.11's 3:1 against its field. `track` is a progress
 * bar's unfilled part, and the fill (`accent`) is held to 3:1 against it, since progress is what the
 * bar says. On `work` and `rest` the track can't also clear 3:1 against the field: ink on those fields
 * is only 6.68 and 6.85, short of the 9 two 3:1 steps need. The canvas's tracks there (`#6A3013`,
 * `#93B8DA`) took the field's side and left the fill at 1.93 against them, on the one field a bar
 * draws on mid-set. `border` stays a decorative hairline, a card's or a separator's.
 *
 * Measured at the byte alpha both platforms render, translucent tokens composited onto their field:
 *
 * - `pace`: `text` 17.87, `textSecondary` 8.69 (7.58 on `backgroundElement`), `outline`/`track` 3.58
 *   against the field, the fill 4.99 against the track. `getReady`'s orange `numeral` 6.68.
 * - `work`: `text` 6.68, `textSecondary` 5.25 (6.26 on `backgroundElement`, which is a lighter tint for
 *   that reason: a darker one took the secondary text to 4.20), `outline` 3.47, the fill 3.02 against
 *   `track`, which is 2.21 against the field.
 * - `rest`: `text` 6.85, `textSecondary` 5.09, `outline` 3.49, the fill 3.03 against `track`, which is
 *   2.26 against the field.
 * - `onAccent` on `accent`, the main button's label: 17.87 on `pace` and `work`, 6.85 on `rest`.
 * - On `work` and `rest` the header's two bars, the session's and a circuit's, are both ink: there is
 *   one ink per field, so they are told apart there by height and by the circuit's label beside it.
 *   Only `pace` keeps the circuit's blue.
 * - A step pill's label on its soft fill, composited onto the field: `accentOnSoft` 13.59 and
 *   `accentCalmOnSoft` 6.15 on `pace`, both 7.98 on `work`, both 4.95 on `rest`.
 *
 * Over `work` the system bars' icons go dark (light ones measure 2.96 on the orange): a field's icons
 * are whichever of black and white contrasts more with it, so this follows from the colour.
 */

/**
 * The faces, by the role `Type` names them by, as their Google Fonts names. Native's `:theme` copies
 * each named face out of `native/android/theme/fonts` into the app's resources.
 */
export const Fonts = theme.Fonts;

export const Spacing = theme.Spacing;

export const MaxContentWidth = theme.MaxContentWidth;

/** One of `Type`'s styles: what `:theme` accepts, and refuses anything else in. */
type TypeStyle = { font: keyof typeof Fonts; fontSize?: number; lineHeight?: number; letterSpacing?: number };

/**
 * `ThemedText`'s type scale, which native generates its `KettleType` from. JSON reads `font` as any
 * string; a role `Fonts` doesn't list fails the native build rather than rendering in RN's default.
 */
export const Type: Record<keyof typeof theme.Type, TypeStyle> = theme.Type as Record<keyof typeof theme.Type, TypeStyle>;
