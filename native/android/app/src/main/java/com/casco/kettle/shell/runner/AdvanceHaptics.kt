package com.casco.kettle.shell.runner

/**
 * When the runner buzzes: once on each advance of the same session, tapped or automatic, and on the
 * advance that ends the workout. Not on a Finish, which is a decision made on screen rather than a step
 * the session moved on to; not on an advance made while nobody watches, which would buzz nobody; and
 * never with sounds off (#301), so one switch quiets the runner, with no haptics setting of its own.
 */
internal class AdvanceHaptics {
    private var last: Pair<String, Int>? = null

    /**
     * Whether [shown], arriving now, buzzes. [watching]: the runner is on screen; [finishing]: it sent
     * Finish; [soundsOn]: the session's sounds preference, as it is now. Not a query: it records [shown]
     * as the step the next view is compared with, so it's called once per view the service publishes.
     */
    fun buzzes(
        shown: RunnerView,
        watching: Boolean,
        finishing: Boolean,
        soundsOn: Boolean,
    ): Boolean {
        val before = last
        val position = (shown as? RunnerView.Running)?.snapshot?.let { it.sessionId to it.stepIndex }
        // Completed ends the session, so the next Running starts from nothing; other views change nothing.
        if (shown is RunnerView.Completed || position != null) last = position
        val advanced =
            when {
                before == null -> false
                shown is RunnerView.Completed -> !finishing
                position == null -> false
                else -> before.first == position.first && position.second > before.second
            }
        return advanced && watching && soundsOn
    }
}
