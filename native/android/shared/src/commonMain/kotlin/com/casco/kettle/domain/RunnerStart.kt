package com.casco.kettle.domain

/**
 * What a session is started from: a planned start, as Home and Program detail resolve one, or an
 * empty session the user fills as they go (#297). A wrapper rather than a third [SessionParams] case,
 * which every `when` over what Home queues up would then have to answer for a start it never makes.
 */
sealed interface RunnerStart {
    data class Planned(
        val params: SessionParams,
    ) : RunnerStart

    data object Adhoc : RunnerStart
}
