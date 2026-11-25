# Glossary

The domain's own words, as the code and the docs use them. One entry per term; the code names it in
the files each entry points at.

## Catch-up

The runner engine bringing a session up to a moment it didn't observe — a return after a gap, or a
Resume after process death — **by replay**: each step that ends by itself ends at its own moment and
the next starts there, through to the first step only the user ends, a pause, or the end of the
workout. A replayed rest logs its planned length, and the time between the last such end and the
moment of return belongs to the step then on screen. A replay plays no cues and posts no step-end
alert. `:shared`'s `RunnerTiming.kt` (`replayed`); `upcomingStepEnds` in `RunnerWake.kt` lists the
moments a catch-up would cross. Decided in #404.
