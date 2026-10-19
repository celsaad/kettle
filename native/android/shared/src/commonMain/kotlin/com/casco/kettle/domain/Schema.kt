package com.casco.kettle.domain

/**
 * How many intervals an EMOM runs, as `src/domain/schema.ts` defines it. The schema compares this
 * after the floor, never the raw quotient, so `total_minutes: 8.333333333333334` isn't refused over
 * a rounding error. Callers guard `intervalSec > 0`.
 *
 * A `Double`, as the JS number is, rather than an `Int`: `0 / 0` is NaN, which compares false against
 * any ceiling, and `toInt()` would quietly turn it into an in-range 0.
 */
fun emomIntervalCount(
    intervalSec: Double,
    totalMinutes: Double,
): Double = kotlin.math.floor(totalMinutes * SECONDS_PER_MINUTE / intervalSec)

private const val SECONDS_PER_MINUTE = 60

/**
 * The ceilings on what the session runner builds one step per unit of, as `MaxSets`, `MaxRounds` and
 * `MaxTotalMinutes` in `src/domain/schema.ts`, whose header says why they exist. The minutes bound an
 * EMOM's length, and so, with [emomIntervalCount], its intervals.
 */
const val MAX_SETS = 500
const val MAX_ROUNDS = 500
const val MAX_TOTAL_MINUTES = 1440
