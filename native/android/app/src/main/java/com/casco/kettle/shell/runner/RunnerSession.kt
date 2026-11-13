package com.casco.kettle.shell.runner

import android.util.Log
import com.casco.kettle.domain.CountIn
import com.casco.kettle.domain.LoggedUnit
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerPlan
import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.RunnerState
import com.casco.kettle.domain.RunnerTransition
import com.casco.kettle.domain.lastLogged
import com.casco.kettle.domain.nextTimeUpdateAt
import com.casco.kettle.domain.reduceRunner
import com.casco.kettle.domain.startRunner
import com.casco.kettle.domain.updateCountIn
import java.util.concurrent.atomic.AtomicReference

/**
 * One session as the service hosts it (#305): the count-in it owns (#297), then #304's engine, fed
 * every command and timer firing on [loop]'s one thread, with its effects carried out there too.
 *
 * - [now] is wall-clock time read through the service's anchor on `elapsedRealtime()`, so a clock
 *   change mid-session moves nothing (`ClockAnchor`).
 * - The timer isn't a 1 Hz poll: it sleeps until the engine's next moment ([nextTimeUpdateAt]), which
 *   lands on the step's own second boundaries, so it can't drift from the floor RN ticked on.
 * - [publish] gets a new view only when the snapshot or the session changed, not for a cue alone.
 * - A command that throws is logged and the last good state kept (#297).
 *
 * Everything a caller calls posts to the loop, so state is only ever read and written there.
 */
class RunnerSession(
    private val plan: RunnerPlan.Runnable,
    private val start: RunnerStart?,
    private val loop: RunnerLoop,
    private val now: () -> Long,
    private val effects: RunnerEffects,
    private val publish: (RunnerView) -> Unit,
) {
    private var countIn: CountIn? = null
    private var state: RunnerState? = null
    private var timer: Cancellable? = null
    private var shown: RunnerView? = null

    /**
     * Whether it was left or started, decided once. Set off the loop by [leave], on the caller's thread,
     * so a leave sent as the count-in ends wins against the last tick even though its block is queued
     * behind it (#318); a late [begin] can't undo a leave either.
     */
    private val phase = AtomicReference(Phase.OPEN)

    /**
     * Counts in, or with [rebuilt], picks up a session the process died under (#307): `resumeRunner`'s,
     * already caught up and paused, so there's no count-in, and its effects (a rest that ran out, or the
     * whole session ending during the kill) are carried out as any transition's are.
     */
    fun begin(rebuilt: RunnerTransition? = null) =
        loop.post {
            if (phase.get() == Phase.LEFT) return@post
            if (rebuilt == null) {
                countIn = CountIn(now())
                countInStep()
                return@post
            }
            if (state != null || countIn != null || !phase.compareAndSet(Phase.OPEN, Phase.STARTED)) return@post
            try {
                settle(rebuilt)
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                Log.w(TAG, "Couldn't resume the session", e)
            }
        }

    fun send(command: RunnerCommand) = loop.post { apply(command) }

    /**
     * Leaves during the count-in, which has written nothing, so nothing is left behind (#297). Once
     * the session runs it can only be finished, and this does nothing. Whether it left, answered at
     * once: false when the count-in had already ended, and the session is running.
     */
    fun leave(): Boolean {
        if (phase.compareAndSet(Phase.OPEN, Phase.LEFT)) {
            loop.post {
                timer?.cancel()
                countIn = null
                show(RunnerView.Idle)
            }
        }
        return phase.get() == Phase.LEFT
    }

    /** What the runner's screen reports (#306): alerts only while it isn't showing, a flush as it goes. */
    fun setRunnerVisible(visible: Boolean) =
        loop.post {
            effects.alerts.runnerVisible = visible
            if (!visible) effects.flushState()
        }

    /** The finish backup's answer: null where none is set up, else whether it failed. */
    fun backedUp(failed: Boolean?) =
        loop.post {
            val completed = shown as? RunnerView.Completed ?: return@post
            val outcome =
                when (failed) {
                    null -> FinishBackupState.NONE
                    true -> FinishBackupState.FAILED
                    false -> FinishBackupState.OK
                }
            show(completed.copy(backup = outcome))
        }

    private fun countInStep() {
        // Not after a leave whose own block is still queued: no tick for a count-in already left.
        val counting = countIn?.takeIf { phase.get() != Phase.LEFT } ?: return
        try {
            val at = now()
            val update = updateCountIn(counting, at)
            // A leave already sent wins, start cue and all; its own block, queued behind this one, shows Idle.
            if (update.done && !phase.compareAndSet(Phase.OPEN, Phase.STARTED)) return
            update.cues.forEach(effects::cue)
            if (update.done) {
                countIn = null
                settle(startRunner(plan, start, at))
            } else {
                countIn = update.countIn
                show(RunnerView.CountingIn(plan, start, update.countIn))
                val next = counting.startedAt + (update.countIn.elapsedSeconds(at) + 1) * MS_PER_SECOND
                timer = loop.postDelayed(next - at, ::countInStep)
            }
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // Nothing has been written unless the session started, so leaving costs nothing; a session
            // that did start keeps the state settle() left it in.
            Log.w(TAG, "The count-in failed", e)
            if (state == null) {
                // Ended as a leave does, so a leave after this one still says it left.
                phase.set(Phase.LEFT)
                countIn = null
                show(RunnerView.Idle)
            }
        }
    }

    /**
     * A command or a timer firing, which never throws out (#297). A failed transition leaves the last
     * good state; one that fails after it has settled keeps the new state, perhaps with no timer, so a
     * step that ends by itself then waits for the user's tap instead.
     */
    private fun apply(command: RunnerCommand) {
        val current = state ?: return
        try {
            settle(reduceRunner(current, command, now()))
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Log.w(TAG, "A runner command failed; the last good state stands", e)
        }
    }

    private fun settle(transition: RunnerTransition) {
        val after = transition.state
        state = after
        effects.carryOut(transition.effects, after.snapshot)
        if (after.finished) {
            timer?.cancel()
            show(RunnerView.Completed(plan, after.session, FinishBackupState.PENDING))
            return
        }
        val logged = lastLogged(after)
        if ((shown as? RunnerView.Running)?.shows(after, logged) != true) {
            show(RunnerView.Running(plan, after.snapshot, after.session, start, logged))
        }
        reschedule(after)
    }

    private fun reschedule(after: RunnerState) {
        timer?.cancel()
        timer = null
        val at = now()
        val wake = nextTimeUpdateAt(after.snapshot, at) ?: return
        val visit = after.snapshot.visit
        timer = loop.postDelayed(maxOf(0, wake - at)) { apply(RunnerCommand.TimeUpdate(visit)) }
    }

    private fun show(view: RunnerView) {
        shown = view
        publish(view)
    }
}

private enum class Phase { OPEN, LEFT, STARTED }

/** Whole seconds into the count-in at [now]. */
private fun CountIn.elapsedSeconds(now: Long): Long = (now - startedAt) / MS_PER_SECOND

private const val TAG = "RunnerSession"

/**
 * Whether this view already shows [state]. The undo record is compared as well: it can clear with
 * neither the snapshot nor the session changing, on an edit that changes nothing.
 */
private fun RunnerView.Running.shows(
    state: RunnerState,
    logged: LoggedUnit?,
) = snapshot === state.snapshot && session === state.session && this.logged == logged
