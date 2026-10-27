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

Added after the review (tiles now carry each frame's timestamp in seconds):

- `replace-guide-to-import.png`: Program guide → Import (`router.replace`), fade through in place.
- `steered-open-fade-in.png`, `steered-open-frames-8-12.png`: the shell's navigate to `/settings`
  from Workouts. The sheet fades in over the tab's picture, bar included.
- `grow-into-runner-270ms.png`: the grow with `RUNNER_MS` at 270. It reaches full height at 1.328s,
  and RN's runner is fully in at 1.345s.
