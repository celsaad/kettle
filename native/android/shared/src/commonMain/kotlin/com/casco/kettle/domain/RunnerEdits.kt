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
    return canAddSet(state.snapshot) &&
        setStepsForMember(state.snapshot.steps, step.memberKey) > loggedSets(state, step.memberKey) + 1
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

/**
 * What the exercise on screen could be swapped for: anything in [exercises] but itself and the
 * built-in rest, its own type first, then the rest in library order. [exercises] is the plan's, so a
 * program week's overrides reach the candidates. Empty where no swap is offered.
 */
fun swapCandidates(
    snapshot: RunnerSnapshot,
    exercises: List<Exercise>,
): List<Exercise> {
    val step = snapshot.step?.takeIf { canAddSet(snapshot) }
    val current = exercises.firstOrNull { it.id == step?.exerciseId } ?: return emptyList()
    val others = exercises.filter { it.config !is ExerciseConfig.Rest && it.id != current.id }
    val (same, different) = others.partition { it.config::class == current.config::class }
    return same + different
}

/** How many blocks the progress dots count: the workout's, or for an ad-hoc session the exercises added. */
fun blockTotal(snapshot: RunnerSnapshot): Int = snapshot.workoutBlockCount ?: snapshot.adhocCount

internal fun editSteps(
    state: RunnerState,
    edit: RunnerCommand.StepEdit,
): RunnerTransition {
    val snapshot = state.snapshot
    val steps = snapshot.steps
    val key = snapshot.step?.takeIf { canAddSet(snapshot) }?.memberKey
    val droppable = key?.takeIf { canDropSet(state) }
    val edited =
        when (edit) {
            RunnerCommand.AddSet -> key?.let { snapshot.copy(steps = addSetForMember(steps, it)) }
            RunnerCommand.DropSet -> droppable?.let { snapshot.copy(steps = dropLastSetForMember(steps, it)) }
            is RunnerCommand.Swap -> key?.let { swapped(snapshot, it, edit.exercise) }
            is RunnerCommand.AddExercise -> snapshot.takeIf { it.adhoc }?.let { added(it, edit.exercise) }
        }
    return if (edited == null || edited.steps === steps) {
        unchanged(state)
    } else {
        RunnerTransition(state.copy(snapshot = edited, lastCommit = null), emptyList())
    }
}

/**
 * [exercise] for what's left of [memberKey], under a key nothing has used, `<key>~swap<n>`, which no
 * expansion issues. Every log is keyed by member, so reusing the key would grow the replaced
 * exercise's entry with the substitute's sets. Null for the exercise already on screen or the rest.
 */
private fun swapped(
    snapshot: RunnerSnapshot,
    memberKey: String,
    exercise: Exercise,
): RunnerSnapshot? {
    val refused = exercise.id == snapshot.step?.exerciseId || exercise.config is ExerciseConfig.Rest
    if (refused) return null
    val count = snapshot.swapCount + 1
    val steps = swapExerciseForMember(snapshot.steps, memberKey, snapshot.stepIndex, exercise, "$memberKey~swap$count")
    return snapshot.copy(steps = steps, swapCount = count)
}

/** Appended under `adhoc<n>`, its block index the count so far, so its sets start an entry of their own. */
private fun added(
    snapshot: RunnerSnapshot,
    exercise: Exercise,
): RunnerSnapshot? {
    val blockIndex = snapshot.adhocCount
    val built = buildStepsForExercise(exercise, blockIndex, "adhoc$blockIndex")
    return if (built.isEmpty()) null else snapshot.copy(steps = snapshot.steps + built, adhocCount = blockIndex + 1)
}
