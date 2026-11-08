package com.casco.kettle.domain

import kotlin.math.ceil

// Surviving process death (#295): a state file left behind means the service died with a session
// running. The next launch reads it and the session file it names, and either offers Resume or Finish,
// offers Finish only, or deletes the state file with nobody told. Launch never crashes on either file.

/** What a state file read as. */
sealed interface SnapshotRead {
    data class Read(
        val snapshot: RunnerSnapshot,
    ) : SnapshotRead

    /** Not JSON, or a field missing or of the wrong type. */
    data object Corrupt : SnapshotRead

    data class UnknownVersion(
        val version: Long,
    ) : SnapshotRead
}

/** What storage found where the state file's session should be. */
sealed interface SessionFile {
    /** The session, and when its file was last written. */
    data class Read(
        val session: Session,
        val modifiedAt: Long,
    ) : SessionFile

    /** There, but reading it failed: an I/O error, which may not happen next time. */
    data object Unreadable : SessionFile

    /** Read, but not a session: it won't parse next time either. */
    data object Corrupt : SessionFile

    data object Absent : SessionFile
}

sealed interface RunnerRecovery {
    /** What a Finish from the prompt writes: the session file as it stands, stamped ended at [finished]'s time. */
    sealed interface Finishable : RunnerRecovery {
        /** The session file as the service last wrote it. */
        val session: Session
        val finished: Session
    }

    /**
     * Resume ([resumeRunner]) or Finish. Finish stamps the snapshot's last transition as the end
     * rather than now, so the hours the app was dead don't count. It commits nothing more: nobody
     * said the step on screen was done. The session then counts as finished, sets and all.
     */
    data class Prompt(
        val snapshot: RunnerSnapshot,
        override val session: Session,
    ) : Finishable {
        override val finished: Session get() = session.copy(endedAt = isoInstant(snapshot.updatedAt))
    }

    /** The state file can't be resumed, but the session can still be finished, as of its file's last write. */
    data class FinishOnly(
        override val session: Session,
        val modifiedAt: Long,
    ) : Finishable {
        override val finished: Session get() = session.copy(endedAt = isoInstant(modifiedAt))
    }

    /**
     * Nothing left to finish: the session file is gone, already finished, or doesn't parse, which
     * can't be stamped and stays among History's errors, as any unreadable session file does.
     */
    data object DeleteSilently : RunnerRecovery

    /**
     * Reading the session file failed. Keep the state file and offer nothing, so a read that failed
     * once is tried again at the next launch rather than costing the session.
     */
    data object Leave : RunnerRecovery
}

/**
 * What to offer for a state file holding [stateText], given the session file it is named for.
 *
 * Finish only when the snapshot is corrupt, of a version this build doesn't know, or doesn't fit the
 * session file: names another session, or points past its entries. The session id comes from the
 * state file's name rather than its contents, which is what lets a corrupt one still be finished.
 * An absent session file can't be finished, having no mtime to stamp, so its state file goes.
 */
fun recoverRunner(
    stateText: String,
    file: SessionFile,
): RunnerRecovery =
    when (file) {
        SessionFile.Absent, SessionFile.Corrupt -> RunnerRecovery.DeleteSilently
        SessionFile.Unreadable -> RunnerRecovery.Leave
        is SessionFile.Read -> recoverFrom(stateText, file)
    }

private fun recoverFrom(
    stateText: String,
    file: SessionFile.Read,
): RunnerRecovery {
    val session = file.session.takeIf { it.endedAt == null } ?: return RunnerRecovery.DeleteSilently
    val snapshot = (decodeRunnerSnapshot(stateText) as? SnapshotRead.Read)?.snapshot?.takeIf { fits(it, session) }
    return snapshot?.let { RunnerRecovery.Prompt(it, session) } ?: RunnerRecovery.FinishOnly(session, file.modifiedAt)
}

private fun fits(
    snapshot: RunnerSnapshot,
    session: Session,
): Boolean = snapshot.sessionId == session.id && snapshot.entryIndex.values.all { it < session.entries.size }

/**
 * Resume: the session rebuilt from [prompt], with the same catch-up a return from the background gets
 * at [now], so a countdown that ran out during the kill commits and advances one step. It then lands
 * paused, whatever it was, so the user picks it up rather than finding a clock already running.
 *
 * One difference from a live return: a rest that ran out during the kill logs its planned length, not
 * the hours the app was dead, which nobody spent resting. A live return logs the real time, since
 * there the user was away from the screen, not gone.
 *
 * That includes a rest or a reps step, whose RN screens have no Resume control: every step kind
 * pauses and resumes through [RunnerCommand.TogglePause], so the screen that lands paused offers it.
 *
 * It starts with nothing to undo, and the caller re-reads the log for the prior sessions. Cues and
 * step-end alerts are dropped: the user is looking at the prompt, not waiting on a cue.
 */
fun resumeRunner(
    prompt: RunnerRecovery.Prompt,
    now: Long,
): RunnerTransition {
    val caught = caughtUp(RunnerState(prompt.snapshot, prompt.session), now)
    val paused =
        if (caught.state.finished || caught.state.snapshot.clock.paused) {
            caught
        } else {
            val landed = reduceRunner(caught.state, RunnerCommand.TogglePause, now)
            RunnerTransition(landed.state, caught.effects + landed.effects)
        }
    val quiet = paused.effects.filterNot { it is RunnerEffect.PlayCue || it is RunnerEffect.StepEnded }
    return RunnerTransition(paused.state, quiet)
}

/**
 * The catch-up, with a rest that ended during the kill committed at its own end, so it logs its
 * length, and the step after it started fresh at [now], as a live catch-up starts it.
 */
private fun caughtUp(
    state: RunnerState,
    now: Long,
): RunnerTransition {
    val snapshot = state.snapshot
    val clock = snapshot.clock
    val restEnd = clock.phaseStartedAt + clock.pausedMs + ceil(clock.stepEndSec).toLong() * MS_PER_SECOND
    val update = RunnerCommand.TimeUpdate(snapshot.visit)
    if (snapshot.step !is RunnerStep.Rest || clock.paused || restEnd >= now) return reduceRunner(state, update, now)
    val ended = reduceRunner(state, update, restEnd)
    return if (ended.state.finished) ended else RunnerTransition(seeded(ended.state, now), ended.effects)
}

/**
 * The start [session] was written from, read back: what [startRunner] stamps on the file, inverted. A
 * program week where the file names one, the workout where it names only that, and ad hoc for neither.
 */
fun recoveredStart(session: Session): RunnerStart {
    val program = session.program
    val week = session.programWeek
    val workout = session.workout
    return when {
        program != null && week != null -> {
            RunnerStart.Planned(
                SessionParams.ProgramSlot(program, week, session.programDay),
            )
        }

        workout != null -> {
            RunnerStart.Planned(SessionParams.Workout(workout))
        }

        else -> {
            RunnerStart.Adhoc
        }
    }
}

/**
 * What a Resume runs: [snapshot]'s steps, which are the truth once a session has started (#297), with
 * the workout's name and the exercises its pickers offer resolved from [library] as it is now. A
 * workout that no longer resolves, deleted or edited down to nothing runnable, keeps the library's
 * name, or its id for one, as History names it (`workoutNameFor`), rather than turning into an ad-hoc
 * session; its pickers then offer the whole library.
 */
fun resumedPlan(
    session: Session,
    snapshot: RunnerSnapshot,
    library: Library,
): RunnerPlan.Runnable {
    val resolved =
        when (val plan = resolveRunnerPlan(recoveredStart(session), library)) {
            is RunnerPlan.Runnable -> plan
            is RunnerPlan.TooLong -> plan.plan
            else -> null
        }
    val workout =
        resolved?.workout
            ?: session.workout?.let { id ->
                library.workouts.firstOrNull { it.id == id } ?: Workout(id, id, emptyList())
            }
    return RunnerPlan.Runnable(workout, resolved?.exercises ?: library.exercises, snapshot.steps)
}
