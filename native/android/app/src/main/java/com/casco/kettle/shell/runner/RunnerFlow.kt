package com.casco.kettle.shell.runner

import com.casco.kettle.domain.RunnerPlan

/** What the runner's route shows, decided before anything is drawn (#297, #306). */
sealed interface RunnerEntry {
    /** The library isn't read yet: nothing, as RN's runner draws while it loads. */
    data object Loading : RunnerEntry

    /** A session the service is counting into or running, whatever the route's own start was. */
    data class Attach(
        val view: RunnerView.Active,
    ) : RunnerEntry

    /** A session that is over, the one just run or one that ended by itself while the runner was away. */
    data class Complete(
        val view: RunnerView.Completed,
    ) : RunnerEntry

    /** A plan that doesn't start by itself: Rest week, Nothing to run, Too long, or Missing. */
    data class Before(
        val plan: RunnerPlan,
    ) : RunnerEntry

    /** Ask the service to start the route's plan. */
    data object Start : RunnerEntry

    /** Asked, and the service hasn't answered with its count-in yet. */
    data object Waiting : RunnerEntry
}

/**
 * The route's entry. [view] is read before [plan], so a different workout pushed while one runs
 * attaches rather than drawing its own Rest week or Missing over the session. [opened] is whether this
 * route has asked the service already: `RunnerHost.active` is only set once the service publishes its
 * count-in, so without it a recomposition or a second tap in between would start a second session,
 * and the Idle a leave publishes before RN pops the route would start a new one.
 *
 * [answered] is whether this route has ever shown the service's session, asked for or attached to.
 * One that attached, by a notification tap or a relaunch, asked nothing, so after its Done the Idle
 * it sees must not read as a fresh start: that began a session nobody saw (#317's review).
 */
fun runnerEntry(
    view: RunnerView,
    plan: RunnerPlan?,
    opened: Boolean,
    answered: Boolean,
): RunnerEntry =
    when {
        view is RunnerView.Active -> RunnerEntry.Attach(view)
        view is RunnerView.Completed -> RunnerEntry.Complete(view)
        opened || answered -> RunnerEntry.Waiting
        plan == null -> RunnerEntry.Loading
        plan is RunnerPlan.Runnable -> RunnerEntry.Start
        else -> RunnerEntry.Before(plan)
    }

/** What can open over the runner's body. Declared in the order back closes them. */
enum class Overlay { PAD, UPCOMING, ADD_PICKER, SWAP_PICKER }

sealed interface BackAction {
    data class Close(
        val overlay: Overlay,
    ) : BackAction

    /** "Finish session?" */
    data object ConfirmFinish : BackAction

    /** Leaves before the session runs, which stops the service with nothing written. */
    data object Leave : BackAction

    data object Done : BackAction
}

/**
 * Back on the runner (#297): one chain, so the pad, which RN's guard didn't know about, closes before
 * "Finish session?" is asked. Anything before the session runs leaves silently, and completion is Done.
 */
fun backAction(
    entry: RunnerEntry,
    open: Set<Overlay>,
): BackAction =
    when {
        entry is RunnerEntry.Complete -> BackAction.Done
        entry !is RunnerEntry.Attach || entry.view !is RunnerView.Running -> BackAction.Leave
        else -> Overlay.entries.firstOrNull { it in open }?.let(BackAction::Close) ?: BackAction.ConfirmFinish
    }

/**
 * The overlays open, each bound to the step it opened on by that step's visit: once the step is left,
 * by the user or by its own clock, the overlay is gone and doesn't come back when Prev returns to it.
 */
data class RunnerOverlays(
    private val opened: Map<Overlay, Long> = emptyMap(),
) {
    val open: Set<Overlay> get() = opened.keys

    fun open(
        overlay: Overlay,
        visit: Long,
    ) = RunnerOverlays(opened + (overlay to visit))

    fun close(overlay: Overlay) = RunnerOverlays(opened - overlay)

    /** What's still open on [visit]: only what was opened on it. */
    fun on(visit: Long) = RunnerOverlays(opened.filterValues { it == visit })
}
