package com.casco.kettle.shell.i18n

import com.casco.kettle.domain.wholeSeconds
import com.casco.kettle.shell.runner.formatClock

// Times as every visible line writes them (#462): m:ss, as the runner's clocks and the duration picker
// show them. Spoken sentences keep their seconds, which a screen reader says better than "0:30".

/** [seconds] as m:ss, to the whole second it rounds to: a file's 74.6 s reads 1:15 everywhere. */
fun timeText(seconds: Double): String = formatClock(wholeSeconds(seconds).toDouble())

/**
 * "0:20–0:45", or one time for a fixed value: a top that is missing, 0, or the same once both ends are
 * rounded to the second, where "0:30–0:30" would read as a range that isn't one.
 */
fun timeRange(
    min: Double,
    max: Double?,
): String {
    val top = max?.takeIf { it != 0.0 && wholeSeconds(it) != wholeSeconds(min) }
    return if (top == null) timeText(min) else "${timeText(min)}–${timeText(top)}"
}
