package com.casco.kettle.shell.timeline

import com.casco.kettle.domain.UnitSystem

/** Which outline an item is drawn in, since the two answer different questions about the same rows. */
sealed interface OutlineMode {
    /** The runner's Coming up: what's left of a session in progress, the set on screen. */
    data object Running : OutlineMode

    /**
     * Workout preview (#280, #439): the whole session before it starts. Adds a reps step's target weight
     * in [units], the preview's question being what to pick up, each exercise's rest between sets from
     * [rests] by member key, and its note under it, which Coming up leaves out because the set on screen
     * says them. Counts a circuit's rounds rather than what's left of one.
     */
    data class Preview(
        val units: UnitSystem,
        val rests: Map<String, Double>,
    ) : OutlineMode
}
