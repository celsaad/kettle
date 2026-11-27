package com.casco.kettle.shell.runner

/**
 * When the runner buzzes: once on each advance of the same session, tapped or automatic, and on the
 * advance that ends the workout, but not on a Finish, as RN's didn't, nor on an advance made while
 * nobody watches, which would buzz nobody. Never with sounds off (#301): one switch makes a session
 * quiet, with no haptics setting of its own. Fed every view the service publishes, in order.
 */
internal class AdvanceHaptics {
    private var last: Pair<String, Int>? = null

    /**
     * Whether [shown], arriving now, buzzes. [watching]: the runner is on screen; [finishing]: it sent
     * Finish; [soundsOn]: the session's sounds preference, as it is now.
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
