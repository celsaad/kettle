# Evidence: the native modal sheet on a device (#155)

Throwaway branch, never merged. Recorded on a Galaxy S25+, `com.casco.kettle.dev`, 2026-10-27, against
`feat/native-modal-sheets`. Frame tiles are `adb shell screenrecord` cut with ffmpeg's `mpdecimate`, so
each tile is a frame where something changed, numbered in order.

- `open-before-fix.png`: Workouts → Settings. Frame 4, the sheet's first, still has Workouts inside
  it: the drawn signal ran a frame early for a route RN adds.
- `open-after-fix.png`: the same after 56f64d1. Frame 5, the sheet's first, already shows Settings.
- `close-x.png`: × from Settings. The sheet's picture slides down over the live tab from frame 5.
- `push-settings-to-import.png`: Settings → Import, shared axis inside the sheet.
- `grow-into-runner.png`, `shrink-out-of-runner.png`: Program detail → session, and Done back to it.
- `settings-sheet.png`: the sheet at rest. One header, RN's top inset zero below the sheet's edge.
- `ime-sheet.png`: Target reps focused in the exercise editor: half under the keyboard, as in the
  Play build (#177's `ime-play-baseline.png`), which is #180.
- `settings-uiautomator.xml`: the open sheet's tree. RN's content, "Drag handle" and "Close", and
  nothing from the tab behind.
