package com.casco.kettle.domain

/**
 * The 3-2-1 before the first step, which the service owns (#297) so that its ticks come from the one
 * cue path (#296). Nothing is written during it: the session starts when it ends ([startRunner]).
 * [ticked] is how many cues have sounded, the closing exercise change included.
 */
data class CountIn(
    val startedAt: Long,
    val ticked: Int = 0,
) {
    /** The numeral on screen at [now]: 3, then 2, then 1, a second each. */
    fun numeral(now: Long): Int = (COUNT_IN_SECONDS - elapsed(now)).coerceIn(1, COUNT_IN_SECONDS)

    /** When it ends, which is when the session starts: what the shade's chronometer counts down to. */
    val endsAt: Long get() = startedAt + COUNT_IN_SECONDS * MS_PER_SECOND

    internal fun elapsed(now: Long): Int = elapsedSec(StepClock(startedAt, null, 0, 0.0), now).toInt()
}

/** The count-in at [now], what it sounds, and whether it is over, which is when the session starts. */
data class CountInUpdate(
    val countIn: CountIn,
    val cues: List<RunnerCue>,
    val done: Boolean,
)

private const val COUNT_IN_SECONDS = 3

/**
 * A tick as each numeral appears, the first at the start, and the exercise change once as it ends,
 * however often it is updated after. A gap that skips numerals doesn't play them late.
 */
fun updateCountIn(
    countIn: CountIn,
    now: Long,
): CountInUpdate {
    val elapsed = countIn.elapsed(now)
    val over = elapsed >= COUNT_IN_SECONDS
    val cue =
        when {
            over -> RunnerCue.EXERCISE_CHANGE.takeIf { countIn.ticked <= COUNT_IN_SECONDS }
            elapsed < countIn.ticked -> null
            else -> RunnerCue.TICK
        }
    val ticked = if (cue == null) countIn.ticked else maxOf(countIn.ticked, elapsed) + 1
    return CountInUpdate(countIn.copy(ticked = ticked), listOfNotNull(cue), done = over)
}
