package com.casco.kettle.shell.i18n

import com.casco.kettle.domain.wholeSeconds

// Times as every visible line writes them (#462): m:ss, as the runner's clocks and the duration picker
// show them. The runner's spoken step announcements keep their seconds.

/** `m:ss`, minutes uncapped: an hour reads "60:00", as the runner's clock counts it. */
fun formatClock(totalSeconds: Double): String {
    val whole = totalSeconds.toLong()
    return "${whole / SECONDS_PER_MINUTE}:${(whole % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
}

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

private const val SECONDS_PER_MINUTE = 60L
