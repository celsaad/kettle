package com.casco.kettle.domain

import kotlin.math.max

// The session runner's engine: RN's `use-session-runner.ts` as a headless state machine (#304). The
// service (#305) owns one, feeds it commands with the time, and carries out the effects it returns.
// The UI (#306) observes the state and sends commands. No Android, no I/O and no clock of its own, so
// the tests drive the time. The reasoning behind each rule is in the RN file's comments, which stay
// the fuller record until the flip release deletes it.

/** The three sounds a session plays, as `use-session-sounds.ts` loads them. */
enum class RunnerCue { TICK, EXERCISE_CHANGE, MILESTONE }

/**
 * What's being entered on the step on screen. [weightKg] 0 is bodyweight, logged as an absent weight.
 * [reps] is an EMOM minute's reps too. [rpe] is never re-seeded, so it carries for the session.
 */
data class StepInputs(
    val reps: Long,
    val weightKg: Double,
    val rpe: Double,
    val roundsCompleted: Long,
    val extraReps: Long,
)

/**
 * Everything a rebuild needs that the session file can't say (#295), and what the state file holds.
 * The work itself is in the session file: [entryIndex] says which entry each member's sets grow.
 *
 * - [workoutBlockCount] is null for an ad-hoc session, which parks past its last step.
 * - [visit] counts re-seeds: it moves whenever the step on screen becomes a different step, which is
 *   also how a command says which step it was aimed at.
 * - [droppedLastSet] holds the members whose last set was zero and so never written, which is how a
 *   rest after it knows not to write onto the set before. The file can't tell.
 * - [updatedAt] is the last transition's time, which a Finish from the recovery prompt stamps as the
 *   session's end.
 *
 * The circuit position is on the step. Undo and the prior sessions are left out: a rebuild starts with
 * nothing to undo and re-reads the log.
 */
data class RunnerSnapshot(
    val sessionId: String,
    val workoutBlockCount: Int?,
    val steps: List<RunnerStep>,
    val stepIndex: Int,
    val visit: Long,
    val clock: StepClock,
    val inputs: StepInputs,
    val entryIndex: Map<String, Int>,
    val droppedLastSet: Set<String>,
    val swapCount: Int,
    val adhocCount: Int,
    val updatedAt: Long,
) {
    val step: RunnerStep? get() = steps.getOrNull(stepIndex)
    val adhoc: Boolean get() = workoutBlockCount == null
}

/** How [LastCommit] takes a commit back: shrink the member's entry, or remove a one-shot entry. */
enum class UndoKind { MEMBER, ONE_SHOT }

/** What the last advance wrote, for Prev to undo straight after it and no further. */
data class LastCommit(
    val resultingIndex: Int,
    val memberKey: String,
    val kind: UndoKind,
)

/** The state after a commit, and whether it wrote anything. Zero work writes nothing. */
internal data class Committed(
    val state: RunnerState,
    val wrote: Boolean,
)

/** Which cues the step on screen has already had: the last whole second ticked, and the milestone. */
data class CueMarks(
    val second: Long = -1,
    val milestone: Boolean = false,
)

/**
 * The engine's whole state. [snapshot] and [session] change only on transitions, so the UI can
 * observe them; [lastCommit] and [cues] are kept in memory only.
 */
data class RunnerState(
    val snapshot: RunnerSnapshot,
    val session: Session,
    val finished: Boolean = false,
    val lastCommit: LastCommit? = null,
    val cues: CueMarks = CueMarks(),
)

/**
 * What the service carries out after a transition, in order.
 *
 * - [WriteSession] is the whole file, every commit: write-through, one file per session.
 * - [StepEnded] is a step that ended by itself, with the step it hands over to (null at the end),
 *   for the alert posted while the runner isn't visible (#296).
 * - [SaveSnapshot] writes the state file, at once or, for an input edit, debounced.
 * - [Completed] ends the session: the state file goes and the finish backup runs.
 */
sealed interface RunnerEffect {
    data class WriteSession(
        val session: Session,
    ) : RunnerEffect

    data class PlayCue(
        val cue: RunnerCue,
    ) : RunnerEffect

    data class StepEnded(
        val step: RunnerStep,
        val next: RunnerStep?,
    ) : RunnerEffect

    data class SaveSnapshot(
        val debounce: Boolean,
    ) : RunnerEffect

    data class Completed(
        val session: Session,
    ) : RunnerEffect
}

data class RunnerTransition(
    val state: RunnerState,
    val effects: List<RunnerEffect>,
)

/**
 * What the UI and the service ask of the engine. [Advance] and [TimeUpdate] name the [visit] they
 * were aimed at, and one aimed at a step already left does nothing. A ticker and a resume can both
 * land on a step that has just ended, and a tap can land just after a step ended by itself; without
 * that, the second of either commits a step nobody performed.
 */
sealed interface RunnerCommand {
    /** Log the set, Done, Skip, or log the interval: commit the step on screen and move on. */
    data class Advance(
        val visit: Long,
    ) : RunnerCommand

    /** The clock moved: auto-advance and cue, or catch up after a gap. */
    data class TimeUpdate(
        val visit: Long,
    ) : RunnerCommand

    data object Prev : RunnerCommand

    data object TogglePause : RunnerCommand

    data class AddRestSeconds(
        val seconds: Double,
    ) : RunnerCommand

    /** Commit the step on screen, then end the session. */
    data object Finish : RunnerCommand

    sealed interface InputEdit : RunnerCommand

    data class SetReps(
        val reps: Long,
    ) : InputEdit

    data class SetWeight(
        val weightKg: Double,
    ) : InputEdit

    data class SetRpe(
        val rpe: Double,
    ) : InputEdit

    data class SetRounds(
        val rounds: Long,
    ) : InputEdit

    data class SetExtraReps(
        val extraReps: Long,
    ) : InputEdit

    sealed interface StepEdit : RunnerCommand

    data object AddSet : StepEdit

    data object DropSet : StepEdit

    /** [exercise] in place of what's left of the exercise on screen, under a fresh member key. */
    data class Swap(
        val exercise: Exercise,
    ) : StepEdit

    /** Appends [exercise] to an ad-hoc session, the only way one gets steps. */
    data class AddExercise(
        val exercise: Exercise,
    ) : StepEdit
}

private const val SESSION_VERSION = 1L
private const val DEFAULT_RPE = 8.0

/**
 * A session's first state, once the count-in is over: the session file is created now, not when the
 * runner opened, so leaving during the count-in writes nothing. Only a [RunnerPlan.Runnable] starts
 * one. [start] supplies the program week the session records; null is the no-params fallback.
 */
fun startRunner(
    plan: RunnerPlan.Runnable,
    start: RunnerStart?,
    now: Long,
): RunnerTransition {
    val slot = (start as? RunnerStart.Planned)?.params as? SessionParams.ProgramSlot
    val startedAt = isoTimestamp(now)
    val session =
        Session(
            SESSION_VERSION,
            startedAt.replace(':', '-').replace('.', '-'),
            plan.workout?.id,
            slot?.programId,
            slot?.week,
            slot?.day,
            startedAt,
            endedAt = null,
            entries = emptyList(),
        )
    val snapshot =
        RunnerSnapshot(
            sessionId = session.id,
            workoutBlockCount = plan.workout?.blocks?.size,
            steps = plan.steps,
            stepIndex = 0,
            visit = 0,
            clock = StepClock(now, null, 0, 0.0),
            inputs = StepInputs(0, 0.0, DEFAULT_RPE, 0, 0),
            entryIndex = emptyMap(),
            droppedLastSet = emptySet(),
            swapCount = 0,
            adhocCount = 0,
            updatedAt = now,
        )
    val effects = listOf(RunnerEffect.WriteSession(session), RunnerEffect.SaveSnapshot(debounce = false))
    return RunnerTransition(seeded(RunnerState(snapshot, session), now), effects)
}

/**
 * [command] applied to [state] at [now]. A finished session refuses everything, which is what makes
 * a second Finish, or one landing with the last auto-advance, a no-op.
 */
fun reduceRunner(
    state: RunnerState,
    command: RunnerCommand,
    now: Long,
): RunnerTransition {
    if (state.finished) return unchanged(state)
    val moved =
        when (command) {
            is RunnerCommand.Advance -> {
                val aimed = command.visit == state.snapshot.visit
                if (aimed) advance(state, now) else unchanged(state)
            }

            is RunnerCommand.TimeUpdate -> {
                timeUpdate(state, command.visit, now)
            }

            RunnerCommand.Prev -> {
                goPrev(state)
            }

            RunnerCommand.TogglePause -> {
                togglePause(state, now)
            }

            is RunnerCommand.AddRestSeconds -> {
                addRestSeconds(state, command.seconds)
            }

            RunnerCommand.Finish -> {
                finish(state, now)
            }

            is RunnerCommand.InputEdit -> {
                editInputs(state, command)
            }

            is RunnerCommand.StepEdit -> {
                editSteps(state, command)
            }
        }
    return settled(state, moved, now, debounce = command is RunnerCommand.InputEdit)
}

internal fun unchanged(state: RunnerState) = RunnerTransition(state, emptyList())

/**
 * Re-seeds the step on screen when it became a different step, then stamps and saves. Keyed on the
 * step's identity rather than its index, so a swap or an ad-hoc add re-seeds without the index moving,
 * and adding or dropping a set doesn't snap the reps just dialled in back to the target. A transition
 * that only moved [RunnerState.cues] saves nothing.
 */
private fun settled(
    before: RunnerState,
    moved: RunnerTransition,
    now: Long,
    debounce: Boolean,
): RunnerTransition {
    val after = moved.state
    val changed = after.snapshot !== before.snapshot || after.session !== before.session
    if (!changed || after.finished) return moved
    val reseeded =
        if (stepIdentity(before.snapshot) != stepIdentity(after.snapshot)) seeded(after, now) else after
    return RunnerTransition(
        reseeded.copy(snapshot = reseeded.snapshot.copy(updatedAt = now)),
        moved.effects + RunnerEffect.SaveSnapshot(debounce),
    )
}

/**
 * Commits the step on screen and moves on, or completes. An exercise change sounds when the next step
 * is a different member, so on every circuit hand-off but not between sets. An ad-hoc session parks
 * one past its end, where an added exercise's first step lands.
 */
internal fun advance(
    state: RunnerState,
    now: Long,
): RunnerTransition {
    val snapshot = state.snapshot
    val current = snapshot.step ?: return unchanged(state)
    val nextIndex = snapshot.stepIndex + 1
    val next = snapshot.steps.getOrNull(nextIndex)
    val committed = commitStep(state, current, now)
    val undo = undoKind(current)?.takeIf { committed.wrote }
    val after =
        committed.state.copy(
            lastCommit = undo?.let { LastCommit(nextIndex, current.memberKey, it) },
        )
    val effects =
        listOfNotNull(
            RunnerEffect.WriteSession(after.session).takeIf { committed.wrote },
            RunnerEffect
                .PlayCue(
                    RunnerCue.EXERCISE_CHANGE,
                ).takeIf { next != null && next.memberKey != current.memberKey },
        )
    return if (next == null && !snapshot.adhoc) {
        complete(after, effects, now)
    } else {
        RunnerTransition(after.copy(snapshot = after.snapshot.copy(stepIndex = nextIndex)), effects)
    }
}

/** Ends the session at [now]. Earlier writes in the same transition fold into the final one. */
private fun complete(
    state: RunnerState,
    effects: List<RunnerEffect>,
    now: Long,
): RunnerTransition {
    val session = state.session.copy(endedAt = isoTimestamp(now))
    return RunnerTransition(
        state.copy(session = session, finished = true, lastCommit = null),
        effects.filterNot { it is RunnerEffect.WriteSession } +
            RunnerEffect.WriteSession(session) +
            RunnerEffect.Completed(session),
    )
}

/** Commits the step on screen, read from the state rather than from what any caller last saw, then ends. */
private fun finish(
    state: RunnerState,
    now: Long,
): RunnerTransition {
    val current = state.snapshot.step
    val committed = current?.let { commitStep(state, it, now).state } ?: state
    return complete(committed, emptyList(), now)
}

/**
 * Back one step, undoing what the last advance wrote if Prev comes straight after it. A second Prev
 * only moves. Prev on the first step keeps its identity, so its clock is not restarted.
 */
private fun goPrev(state: RunnerState): RunnerTransition {
    val snapshot = state.snapshot
    val commit = state.lastCommit?.takeIf { it.resultingIndex == snapshot.stepIndex }
    if (commit == null && snapshot.stepIndex == 0) return unchanged(state)
    val undone = commit?.let { undoCommit(state, it).copy(lastCommit = null) } ?: state
    val moved = undone.copy(snapshot = undone.snapshot.copy(stepIndex = max(0, snapshot.stepIndex - 1)))
    return RunnerTransition(moved, listOfNotNull(commit?.let { RunnerEffect.WriteSession(moved.session) }))
}

private fun editInputs(
    state: RunnerState,
    edit: RunnerCommand.InputEdit,
): RunnerTransition {
    val inputs = state.snapshot.inputs
    val edited =
        when (edit) {
            is RunnerCommand.SetReps -> inputs.copy(reps = max(0, edit.reps))
            is RunnerCommand.SetWeight -> inputs.copy(weightKg = max(0.0, edit.weightKg))
            is RunnerCommand.SetRpe -> inputs.copy(rpe = edit.rpe)
            is RunnerCommand.SetRounds -> inputs.copy(roundsCompleted = max(0, edit.rounds))
            is RunnerCommand.SetExtraReps -> inputs.copy(extraReps = max(0, edit.extraReps))
        }
    return if (edited == inputs) {
        unchanged(state)
    } else {
        RunnerTransition(state.copy(snapshot = state.snapshot.copy(inputs = edited)), emptyList())
    }
}
