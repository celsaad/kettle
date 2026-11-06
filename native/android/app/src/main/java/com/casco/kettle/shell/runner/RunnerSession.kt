package com.casco.kettle.shell.runner

import android.util.Log
import com.casco.kettle.domain.CountIn
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerPlan
import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.RunnerState
import com.casco.kettle.domain.RunnerTransition
import com.casco.kettle.domain.nextTimeUpdateAt
import com.casco.kettle.domain.reduceRunner
import com.casco.kettle.domain.startRunner
import com.casco.kettle.domain.updateCountIn

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
 * Only [send], [leave] and [setRunnerVisible] are called from other threads; each posts to the loop.
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

    @Volatile private var left = false

    fun begin() =
        loop.post {
            countIn = CountIn(now())
            countInStep()
        }

    fun send(command: RunnerCommand) = loop.post { apply(command) }

    /**
     * Leaves during the count-in, which has written nothing, so nothing is left behind (#297). Once
     * the session runs it can only be finished; false then.
     */
    fun leave(): Boolean {
        if (state != null || left) return false
        left = true
        loop.post {
            timer?.cancel()
            countIn = null
            show(RunnerView.Idle)
        }
        return true
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
                    null -> FinishBackup.NONE
                    true -> FinishBackup.FAILED
                    false -> FinishBackup.OK
                }
            show(completed.copy(backup = outcome))
        }

    private fun countInStep() {
        val counting = countIn?.takeUnless { left } ?: return
        val at = now()
        val update = updateCountIn(counting, at)
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
    }

    private fun apply(command: RunnerCommand) {
        val current = state ?: return
        val transition =
            try {
                reduceRunner(current, command, now())
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                Log.w(TAG, "A runner command failed; the last good state stands", e)
                return
            }
        settle(transition)
    }

    private fun settle(transition: RunnerTransition) {
        val after = transition.state
        state = after
        effects.carryOut(transition.effects, after.snapshot)
        if (after.finished) {
            timer?.cancel()
            show(RunnerView.Completed(plan, after.session, FinishBackup.PENDING))
            return
        }
        val running = shown as? RunnerView.Running
        if (running == null || running.snapshot !== after.snapshot || running.session !== after.session) {
            show(RunnerView.Running(plan, after.snapshot, after.session, start))
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

/** Whole seconds into the count-in at [now]. */
private fun CountIn.elapsedSeconds(now: Long): Long = (now - startedAt) / MS_PER_SECOND

private const val MS_PER_SECOND = 1000L
private const val TAG = "RunnerSession"
