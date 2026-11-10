package com.casco.kettle.shell.runner

import android.util.Log
import com.casco.kettle.domain.RunnerCue
import com.casco.kettle.domain.RunnerEffect
import com.casco.kettle.domain.RunnerSnapshot
import com.casco.kettle.domain.Session
import com.casco.kettle.shell.files.SessionWrites
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock

/** The session's sounds, and whether the user has them on (`sessionSounds`), asked at each cue. */
interface CueSounds {
    fun on(): Boolean

    fun play(cue: RunnerCue)
}

/**
 * Carries out the engine's effects (#305), in the order it returns them, on the runner's thread. The
 * service is the running session's only writer, of its session file and its state file both (#295).
 *
 * - [writeSession] is `:shared`'s atomic write, taken under [SessionWrites] so a screen's session write
 *   can't interleave, then [onSessionWritten] has the screens read the log again.
 * - [sounds]' preference is read at each cue, so a toggle mid-session applies on the next one (#296).
 * - [backUp] starts the finish backup, which the caller runs off this thread.
 *
 * Each effect is tried on its own: one that throws is logged and the rest still run, so a disk that
 * refuses the session file still gets the state file, and the clock keeps going (#297).
 */
class RunnerEffects(
    private val writeSession: (Session) -> Unit,
    private val onSessionWritten: () -> Unit,
    private val stateFile: RunnerStateFile,
    private val sounds: CueSounds,
    val alerts: StepAlerts,
    private val backUp: () -> Unit,
) {
    /** [effects] from one transition, with [after], the snapshot it left. */
    fun carryOut(
        effects: List<RunnerEffect>,
        after: RunnerSnapshot,
    ) {
        val alerted = effects.any { it is RunnerEffect.StepEnded }
        for (effect in effects) guarded { carryOut(effect, after, alerted) }
    }

    /** A cue from outside a transition: the count-in's, which share the one sound path (#297). */
    fun cue(cue: RunnerCue) = guarded { if (sounds.on()) sounds.play(cue) }

    /** A debounced state-file write, now: the runner has left the screen. */
    fun flushState() = guarded { stateFile.flush() }

    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // Any failure, not just the disk's: the session outlives a bug in one effect (#297).
            Log.w(TAG, "An effect failed; the session runs on", e)
        }
    }

    private fun carryOut(
        effect: RunnerEffect,
        after: RunnerSnapshot,
        alerted: Boolean,
    ) {
        when (effect) {
            is RunnerEffect.WriteSession -> {
                runBlocking { SessionWrites.lock.withLock { writeSession(effect.session) } }
                onSessionWritten()
            }

            is RunnerEffect.PlayCue -> {
                if (sounds.on()) sounds.play(effect.cue)
            }

            is RunnerEffect.StepEnded -> {
                alerts.stepEnded(stepEndAlert(effect.step, effect.next, after), sounds.on())
            }

            is RunnerEffect.SaveSnapshot -> {
                stateFile.save(after, effect.debounce)
            }

            is RunnerEffect.Completed -> {
                stateFile.delete(after.sessionId)
                // A last step that ended by itself has just posted "Workout complete", which is the
                // news; a Finish was tapped on the screen, where there's nothing to tell.
                if (!alerted) alerts.clear()
                backUp()
            }
        }
    }
}

private const val TAG = "RunnerEffects"
