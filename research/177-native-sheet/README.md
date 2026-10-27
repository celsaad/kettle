# Evidence: the RN surface inside an in-window native sheet (#177)

Throwaway branch, never merged. The prototype is the commit before this one
(`SheetPrototype.kt`, wired from `ShellLayout` and `MainActivity`, plus `hostedModalOptions` in
`safe-brownfield.ts`). Recorded on a Galaxy S25+, `com.casco.kettle.dev`, 2026-10-27. The verdict and
what it means for #155 are in the resolution comment on #177.

- `settings-sheet.png`: Settings presented as the sheet over the dimmed snapshot. One header, and
  RN's top inset is zero below the sheet's edge.
- `ime-sheet.png` / `ime-play-baseline.png`: Target reps focused in the exercise editor, in the sheet
  and in the Play build (`com.casco.kettle`, only screenshotted). It's half under the keyboard in
  both, so this is an existing editor bug, not something the sheet caused.
- `talkback-tree-exercise-editor.xml`: uiautomator dump with TalkBack on and the editor in the sheet.
  The whole RN tree is present (the #153 failure mode is absent), and nothing from the backdrop.
- `open-close.mp4`, and the frame tiles `open-1-tap-to-slide.png`, `open-2-slide.png`, `close.png`:
  no full-screen flash, but the native slide runs 1–3 frames ahead of RN's draw both ways. The
  opening sheet shows the tab for two frames, and the live surface behind the closing picture shows
  the editor for three. The missing scrim and the picture shrinking on close are prototype bugs
  (scrim alpha never reset; `FIT_START` rescales once the tab bar returns).
