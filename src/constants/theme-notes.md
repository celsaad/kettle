# `theme.json`'s notes

The notes `theme.json` can't hold, since JSON takes no comments: the contrast ratios each color was
measured at, and what the tokens whose names don't say it all are for. Native's `:theme` generates
its Compose theme from `theme.json` on every build, and `GeneratedThemeTest` re-checks the ratios
below against the generated colors. Edit a color there, re-measure it here.

Contrast is measured against WCAG 2.1 AA: 4.5:1 for body text, 3:1 for large text and for the
graphics WCAG 1.4.11 covers.

## `Colors`

One warm token set, remapped for light and dark; the app shell follows the OS color scheme, with a
manual override. The palette is the canvas's since #487, light and dark, which is what moved every
number here.

- `textSecondary`: 6.35 on `background`, 7.03 on `backgroundElement`, 5.82 on `backgroundSelected`
  light; 6.83, 6.27 and 5.58 dark. `backgroundSelected` is opaque in both schemes now, so a filled
  field takes it in both (`fieldFill`): dark's used to be translucent and composited to 4.46.
- `onAccent` on `accent`, the filled button's label: 4.99 light (white on the deeper `#b94f24`; the
  old `#cf6a37` took it at 3.64, AA-large only), 6.36 dark.
- `lineStrong` is the 3:1 edge of a control that has no fill of its own, or only the surface's
  (Settings' segmented controls, Back up now): 3.30 on `background` and 3.65 on
  `backgroundElement` light, 3.41 and 3.13 dark. Never on `backgroundSelected`, where it falls short
  (3.02 light, 2.78 dark). `border` stays the decorative hairline, rows' and cards'.
- The training calendar sits on the page since #414, and its marks are held to **3:1 against
  `background`** — WCAG 1.4.11's bar for a graphic you need to read the chart, which here is whether
  a day was trained at all. The rest day's outline (`calendarEmpty`) and the three shades, light:
  3.53, 3.13, 7.03, 14.41; dark: 4.06, 3.05, 6.36, 13.46. Neighbouring levels are about 2:1 apart
  (2.25 and 2.05 light, 2.08 and 2.12 dark), where the old scale's 1.36–1.71 barely told them apart;
  they still needn't clear 3:1, since every week's row label carries its count and time and the
  legend names the buckets. An early draft had a lowest step at 1.6:1 against the empty cell, which
  is why these are measured rather than eyeballed.
- `accentCalmText` is the recover blue for words, `#2a64a0` in light: a shade under the canvas's
  `#2f6fae`, which stays `accentCalm`, the blue for fills and marks. On its own soft tint, the HOLD
  badge's 12sp label, the canvas blue measured 3.95 on the page and 4.34 on a card; this one is 4.62
  and 5.07 (dark, `#6aa6de`, is 5.28 and 4.75).
- The YAML view colours a key in `accentText` and a number in `accentCalmText`, on `background`:
  5.56 and 5.53 light, 8.52 and 7.28 dark (#444).
- The session editor's Undo bar turns the page over, `background` on a `text` pill: 15.47 light,
  16.13 dark (#442).

Tokens whose name doesn't say it all:

- `onAccent` is the text or icon color to place on top of an accent-colored surface (buttons, active
  pills).
- `calendarEmpty` is a rest day's outline on the training calendar, and `calendarLevel1`–`3` trained
  days by duration. More is darker on a light page and brighter on a dark one, so the two ramps run
  in opposite directions.

## `RunnerColors`

The live session runner ignores the shell's scheme. Its background says what the step's clock is
doing (#418), and each field is a whole token set, so a screen reads the same names on any of them:

- `pace`, black: you set the pace (a reps set, a hold or cardio with no end of its own, anything
  paused), and every screen around a session.
- `getReady`: `pace` with an orange `numeral`, for the count-in and a rest's last `TICK_SECONDS`
  (`RunnerTiming.kt`), the seconds that tick. Every other token is pace's, which `GeneratedThemeTest`
  holds, so an edit to `pace` has to be made in `getReady` too.
- `work`, orange: the clock runs the work (HIIT, EMOM, AMRAP, cardio with a duration, a hold that ends
  itself).
- `rest`, blue.

`accent` is each field's ink, so the one filled button is the field's inverse and orange is never a
button (`onAccent` is its label). `numeral` is the big clock's colour, which only `getReady` sets
apart. `outline` is a control's edge, held to WCAG 1.4.11's 3:1 against its field. `track` is a
progress bar's unfilled part, and the fill (`accent`) is held to 3:1 against it, since progress is
what the bar says. On `work` and `rest` the track can't also clear 3:1 against the field: ink on those
fields is only 6.68 and 6.85, short of the 9 two 3:1 steps need. The canvas's tracks there (`#6A3013`,
`#93B8DA`) took the field's side and left the fill at 1.93 against them, on the one field a bar draws
on mid-set. `border` stays a decorative hairline, a card's or a separator's.

Measured at the byte alpha both platforms render, translucent tokens composited onto their field:

- `pace`: `text` 17.87, `textSecondary` 8.69 (7.58 on `backgroundElement`), `outline`/`track` 3.58
  against the field, the fill 4.99 against the track. `getReady`'s orange `numeral` 6.68.
- `field`, a sheet's search fill (#479), with no edge of its own: `text` 13.87 and `textSecondary`
  6.74 on it. Against the sheet (`backgroundElement`) the fill is only 1.12, so it isn't the field's
  boundary: the search icon and the placeholder name it, as the keypad face's 1.40 edge never did
  either. Only the sheets draw it, and they are always `pace`'s, so `work` and `rest` repeat their
  `backgroundElement` to keep each field whole.
- `work`: `text` 6.68, `textSecondary` 5.25 (4.55 on `backgroundElement`, the board's dark panel over
  orange (#473) as ink at 8%, the darkest tint the secondary text still clears AA on: the board's own
  10% gave 4.36. `text` is 5.80 there, and a pill's `outline` 3.01, which 9% would take under 3:1, so
  that's the second limit on darkening it), `outline` 3.47, the fill 3.02 against `track`, which is
  2.21 against the field.
- `rest`: `text` 6.85, `textSecondary` 5.09, `outline` 3.49, the fill 3.03 against `track`, which is
  2.26 against the field.
- `onAccent` on `accent`, the main button's label: 17.87 on `pace` and `work`, 6.85 on `rest`.
- On `work` and `rest` the header's two bars, the session's and a circuit's, are both ink: there is
  one ink per field, so they are told apart there by height and by the circuit's label beside it.
  Only `pace` keeps the circuit's blue.
- A step pill's label on its soft fill, composited onto the field: `accentOnSoft` 13.59 and
  `accentCalmOnSoft` 6.15 on `pace`, both 7.98 on `work`, both 4.95 on `rest`.

Over `work` the system bars' icons go dark (light ones measure 2.96 on the orange): a field's icons
are whichever of black and white contrasts more with it, so this follows from the colour.

## `Fonts` and `Type`

`Fonts` names each face by the role `Type` uses it in, as its Google Fonts name; `:theme` copies each
named face out of `native/android/theme/fonts` into the app's resources. `Type` is the type scale
`KettleType` is generated from, and a `font` role `Fonts` doesn't list fails the native build.
