package com.casco.kettle.domain

import kotlinx.datetime.Instant

/**
 * How long [session] ran, in whole minutes: `sessionDurationMinutes` in
 * `src/state/selectors/session-summary.ts`, held to it by `conformance/records.yaml`. An unfinished
 * session is 0, and so is one that ended before it started, which a clock set back mid-session does.
 *
 * Rounded as JS's `Math.round`, through [roundHalfUp]. A time that isn't an instant counts as 0 where RN's would
 * be NaN; nothing RN writes is either.
 */
fun sessionDurationMinutes(session: Session): Int {
    val started = session.startedInstant()
    val ended = session.endedAt?.let(::instantOrNull)
    if (started == null || ended == null) return 0
    val minutes = (ended - started).inWholeMilliseconds / MS_PER_MINUTE
    return maxOf(0, roundHalfUp(minutes).toInt())
}

/** When [Session.startedAt] is, or null where it isn't an instant, as RN's invalid `Date` is left out. */
internal fun Session.startedInstant(): Instant? = instantOrNull(startedAt)

private fun instantOrNull(text: String): Instant? =
    try {
        Instant.parse(text)
    } catch (_: IllegalArgumentException) {
        null
    }

private const val MS_PER_MINUTE = 60_000.0
