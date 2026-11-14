package com.casco.kettle.domain

// The duration picker's two steppers (#440), shared by the workout and exercise editors: whole seconds,
// split into minutes and seconds, each stepped on its own.

/** [seconds] as whole minutes and the seconds left over; a fraction rounds to the nearest second. */
fun durationParts(seconds: Double): Pair<Int, Int> {
    val whole = wholeSeconds(seconds)
    return whole / SECONDS_PER_MINUTE to whole % SECONDS_PER_MINUTE
}

/**
 * [seconds] with its minutes moved [by], held between 0 and [MAX_PICKER_MINUTES]; the seconds stay. A
 * file's longer rest is its own ceiling, so + leaves 90:00 where it is rather than cutting it to 59:00.
 */
fun steppingMinutes(
    seconds: Double,
    by: Int,
): Double {
    val (minutes, rest) = durationParts(seconds)
    val stepped = (minutes + by).coerceIn(0, maxOf(MAX_PICKER_MINUTES, minutes))
    return (stepped * SECONDS_PER_MINUTE + rest).toDouble()
}

/** Whether + can move [seconds]'s minutes: not at the ceiling, or past it in a file's longer rest. */
fun canAddMinute(seconds: Double): Boolean = durationParts(seconds).first < MAX_PICKER_MINUTES

/**
 * [seconds] with its seconds moved [by] steps of [SECONDS_STEP], wrapping within the minute rather than
 * carrying into it, as a clock's seconds do: the minutes have their own stepper. A value off the step,
 * a file's 0:20 say, lands on the step from there, 0:25.
 */
fun steppingSeconds(
    seconds: Double,
    by: Int,
): Double {
    val (minutes, rest) = durationParts(seconds)
    // Up from the step below, down from the step above: an off-grid value lands on the next step its way.
    val from = if (by > 0) rest / SECONDS_STEP else (rest + SECONDS_STEP - 1) / SECONDS_STEP
    val snapped = (from + by) * SECONDS_STEP
    val wrapped = (snapped % SECONDS_PER_MINUTE + SECONDS_PER_MINUTE) % SECONDS_PER_MINUTE
    return (minutes * SECONDS_PER_MINUTE + wrapped).toDouble()
}

/** [seconds] to the nearest whole second, a half up, as the picker shows, steps and says it. */
fun wholeSeconds(seconds: Double): Int = kotlin.math.floor(seconds.coerceAtLeast(0.0) + HALF).toInt()

const val SECONDS_STEP = 5
private const val HALF = 0.5
const val MAX_PICKER_MINUTES = 59
