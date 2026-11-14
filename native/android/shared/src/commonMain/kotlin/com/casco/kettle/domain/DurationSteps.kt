package com.casco.kettle.domain

// The duration picker's two steppers (#440), shared by the workout and exercise editors: whole seconds,
// split into minutes and seconds, each stepped on its own.

/** [seconds] as whole minutes and the seconds left over; a fraction rounds to the nearest second. */
fun durationParts(seconds: Double): Pair<Int, Int> {
    val whole = kotlin.math.round(seconds.coerceAtLeast(0.0)).toInt()
    return whole / SECONDS_PER_MINUTE to whole % SECONDS_PER_MINUTE
}

/** [seconds] with its minutes moved [by], held between 0 and [MAX_PICKER_MINUTES]; the seconds stay. */
fun steppingMinutes(
    seconds: Double,
    by: Int,
): Double {
    val (minutes, rest) = durationParts(seconds)
    return ((minutes + by).coerceIn(0, MAX_PICKER_MINUTES) * SECONDS_PER_MINUTE + rest).toDouble()
}

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
    val snapped =
        if (by >
            0
        ) {
            (rest / SECONDS_STEP + by) * SECONDS_STEP
        } else {
            ((rest + SECONDS_STEP - 1) / SECONDS_STEP + by) * SECONDS_STEP
        }
    return (minutes * SECONDS_PER_MINUTE + (snapped % SECONDS_PER_MINUTE + SECONDS_PER_MINUTE) % SECONDS_PER_MINUTE)
        .toDouble()
}

const val SECONDS_STEP = 5
const val MAX_PICKER_MINUTES = 59
