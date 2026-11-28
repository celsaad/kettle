# Glossary

The domain's own words, as the code and the docs use them. One entry per term; the code names it in
the files each entry points at.

## Catch-up

The runner engine bringing a session up to a moment it didn't observe — a return after a gap, or a
Resume after process death — **by replay**: each step that ends by itself ends at its own moment and
the next starts there, through to the first step only the user ends, a pause, or the end of the
workout. A replayed rest logs its planned length, and the time between the last such end and the
moment of return belongs to the step then on screen. The steps a replay passes play no cues and post
no step-end alert; only the last end it crosses is announced, as a live end is. `:shared`'s
`RunnerTiming.kt` (`replayed`); `upcomingStepEnds` in `RunnerWake.kt` lists the moments a catch-up
would cross. Decided in #404, the last-end alert in #546's review.

## Equipment

The items an exercise needs at hand, such as a dumbbell or a bench. It belongs to the exercise alone:
a workout's equipment is everything its exercises need, combined, and is never stated separately.
_Avoid_: gear, kit, props

## Known equipment

An item of equipment from Kettle's own vocabulary, which every reader sees in their own language. Any
other item is the user's own words and reads exactly as written, as an exercise name does.
_Avoid_: closed list, catalogue
