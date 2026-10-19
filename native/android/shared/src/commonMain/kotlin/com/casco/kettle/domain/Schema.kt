package com.casco.kettle.domain

/**
 * How many intervals an EMOM runs, as `src/domain/schema.ts` defines it. The schema compares this
 * after the floor, never the raw quotient, so `total_minutes: 8.333333333333334` isn't refused over
 * a rounding error. Callers guard `intervalSec > 0`.
 */
fun emomIntervalCount(
    intervalSec: Double,
    totalMinutes: Double,
): Int = kotlin.math.floor(totalMinutes * SECONDS_PER_MINUTE / intervalSec).toInt()

private const val SECONDS_PER_MINUTE = 60
