package com.casco.kettle.domain

// Edits to the running step list: add or drop a set, swap the exercise on screen, add one to an
// ad-hoc session. The list surgery is `StepMutations.kt`'s; what is here is what only the runner
// knows: what has reached the file, and the keys already used.
//
// **Every edit clears the undo record**, which RN's file calls the single most dangerous line in it.
// `LastCommit.resultingIndex` points into the list, so after an edit a stale one sends Prev to undo a
// commit that now belongs to a different step, retracting a set the user kept.

/**
 * Whether the set count of the exercise on screen can change: on a reps or hold step outside a
 * circuit, where a member's sets are its rounds and "one more set" would mean "one more round".
 */
fun canAddSet(snapshot: RunnerSnapshot): Boolean {
    val step = snapshot.step
    return (step is RunnerStep.Reps || step is RunnerStep.Hold) && step.circuit == null
}

/**
 * Whether a set can go: only down to the sets already logged plus the one in progress, since below
 * that it would remove work already in the session file.
 */
fun canDropSet(state: RunnerState): Boolean {
    val step = state.snapshot.step ?: return false
    return canAddSet(state.snapshot) && dropped(state, step.memberKey) != null
}

private fun loggedSets(
    state: RunnerState,
    memberKey: String,
): Int =
    when (val entry = state.memberEntry(memberKey)) {
        is SessionEntry.Reps -> entry.sets.size
        is SessionEntry.TimedHold -> entry.sets.size
        else -> 0
    }

/** The swap sheet's two groups (#417): [current]'s own type, "Also reps", then every other type. */
data class SwapChoices(
    val current: Exercise,
    val same: List<Exercise>,
    val other: List<Exercise>,
)

/**
 * What the exercise on screen could be swapped for, grouped as the sheet shows them: anything in
 * [exercises] but itself and the built-in rest, its own type first, then the rest, each in library
 * order. [exercises] is the plan's, so a program week's overrides reach the choices. Null where no swap
 * is offered.
 */
fun swapChoices(
    snapshot: RunnerSnapshot,
    exercises: List<Exercise>,
): SwapChoices? {
    val step = snapshot.step?.takeIf { canAddSet(snapshot) }
    val current = exercises.firstOrNull { it.id == step?.exerciseId } ?: return null
    val others = exercises.filter { it.config !is ExerciseConfig.Rest && it.id != current.id }
    val (same, other) = others.partition { it.config::class == current.config::class }
    return SwapChoices(current, same, other).takeIf { others.isNotEmpty() }
}

/** How many blocks the progress dots count: the workout's, or for an ad-hoc session the exercises added. */
fun blockTotal(snapshot: RunnerSnapshot): Int = snapshot.workoutBlockCount ?: snapshot.adhocCount

/**
 * The edit, as RN's hook makes it: on whatever step is on screen, with the buttons' rules
 * ([canAddSet], [canDropSet], [swapChoices]) left to the UI that shows them, and the undo record
 * cleared by every edit, even one that changes nothing. Two refusals RN's hook leaves to its screen
 * are kept here, because without them an edit corrupts the session: a circuit member's sets
 * (`StepMutations.kt` refuses them), and a drop reaching work already done or the step on screen,
 * which leaves the index past the list ([dropped]).
 */
internal fun editSteps(
    state: RunnerState,
    edit: RunnerCommand.StepEdit,
): RunnerTransition {
    val snapshot = state.snapshot
    val steps = snapshot.steps
    val key = snapshot.step?.memberKey
    val edited =
        when (edit) {
            RunnerCommand.AddSet -> {
                key?.let { snapshot.copy(steps = addSetForMember(steps, it)) }
            }

            RunnerCommand.DropSet -> {
                key?.let { dropped(state, it) }?.let { snapshot.copy(steps = it) }
            }

            is RunnerCommand.Swap -> {
                key?.let { swapped(snapshot, it, edit.exercise) }
            }

            is RunnerCommand.AddExercise -> {
                added(snapshot, edit.exercise)
            }
        }
    // RN returns before its undo clear only when nothing is on screen; a parked ad-hoc session still adds.
    return when {
        key == null && edit !is RunnerCommand.AddExercise -> unchanged(state)
        edited == null || edited.steps === steps -> RunnerTransition(state.copy(lastCommit = null), emptyList())
        else -> RunnerTransition(state.copy(snapshot = edited, lastCommit = null), emptyList())
    }
}

/**
 * The list without [memberKey]'s last set, or null where that drop would reach work already done:
 * RN's floor, more sets than are logged plus the one in progress, and a positional one, since a zero
 * set is never logged and the log alone would then let the drop remove the step on screen.
 */
private fun dropped(
    state: RunnerState,
    memberKey: String,
): List<RunnerStep>? {
    val steps = state.snapshot.steps
    val shorter = dropLastSetForMember(steps, memberKey)
    val lastSet = steps.indexOfLast { it.memberKey == memberKey && (it is RunnerStep.Reps || it is RunnerStep.Hold) }
    val removedFrom = lastSet - (steps.size - shorter.size - 1)
    val aboveFloor = setStepsForMember(steps, memberKey) > loggedSets(state, memberKey) + 1
    return shorter.takeIf { it !== steps && aboveFloor && removedFrom > state.snapshot.stepIndex }
}

/**
 * [exercise] for what's left of [memberKey], under a key nothing has used, `<key>~swap<n>`, which no
 * expansion issues. Every log is keyed by member, so reusing the key would grow the replaced
 * exercise's entry with the substitute's sets. The count moves on every swap asked for, as RN's does.
 */
private fun swapped(
    snapshot: RunnerSnapshot,
    memberKey: String,
    exercise: Exercise,
): RunnerSnapshot {
    val count = snapshot.swapCount + 1
    val steps = swapExerciseForMember(snapshot.steps, memberKey, snapshot.stepIndex, exercise, "$memberKey~swap$count")
    return snapshot.copy(steps = steps, swapCount = count)
}

/**
 * Appended under `adhoc<n>`, its block index the count so far, so its sets start an entry of their
 * own. Only an ad-hoc session's screen offers it; like RN's hook, a workout's accepts it too.
 */
private fun added(
    snapshot: RunnerSnapshot,
    exercise: Exercise,
): RunnerSnapshot {
    val blockIndex = snapshot.adhocCount
    val built = buildStepsForExercise(exercise, blockIndex, "adhoc$blockIndex")
    return snapshot.copy(steps = snapshot.steps + built, adhocCount = blockIndex + 1)
}
