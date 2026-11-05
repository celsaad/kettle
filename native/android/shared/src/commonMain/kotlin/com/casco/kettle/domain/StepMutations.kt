package com.casco.kettle.domain

// Mid-session edits to a running step list, from `session-steps.ts`.
//
// **Circuit members are not a legal target, and the caller enforces it.** A member's setIndex there
// is its round, and its steps aren't contiguous, so "one more set" would mean "one more round": a
// different operation. Nothing here can tell from a step alone.

/** A member's own set steps, in list order: the ones "Set 3 of 4" counts. */
private fun setStepIndices(
    steps: List<RunnerStep>,
    memberKey: String,
): List<Int> = steps.indices.filter { steps[it].memberKey == memberKey && steps[it].isSet() }

private fun RunnerStep.isSet(): Boolean = this is RunnerStep.Reps || this is RunnerStep.Hold

/** How many sets of [memberKey] the list holds. */
fun setStepsForMember(
    steps: List<RunnerStep>,
    memberKey: String,
): Int = setStepIndices(steps, memberKey).size

/** Renumbers one member's sets to 1..n, which both the display and the previews read. */
private fun renumber(
    steps: List<RunnerStep>,
    memberKey: String,
): List<RunnerStep> {
    val indices = setStepIndices(steps, memberKey)
    val total = indices.size.toLong()
    val position = indices.withIndex().associate { (i, stepIndex) -> stepIndex to (i + 1).toLong() }
    return steps.mapIndexed { index, step ->
        val setIndex = position[index]
        when {
            setIndex == null -> step
            step is RunnerStep.Reps -> step.copy(setIndex = setIndex, setTotal = total)
            step is RunnerStep.Hold -> step.copy(setIndex = setIndex, setTotal = total)
            else -> step
        }
    }
}

/**
 * One more set of [memberKey], after its last one. Its leading rest is a copy of one of the member's
 * own rests rather than rebuilt from config, so a `rest_sec: 0` exercise, which has none, gets none.
 */
fun addSetForMember(
    steps: List<RunnerStep>,
    memberKey: String,
): List<RunnerStep> {
    val lastIndex = setStepIndices(steps, memberKey).lastOrNull() ?: return steps
    val rest = steps.firstOrNull { it is RunnerStep.Rest && it.memberKey == memberKey && !it.standalone }
    val inserted = listOfNotNull(rest, steps[lastIndex])
    return renumber(steps.subList(0, lastIndex + 1) + inserted + steps.subList(lastIndex + 1, steps.size), memberKey)
}

/**
 * One fewer set of [memberKey]: its last one, and the rest that led into it. Never the only set.
 * Whether it may drop a set already logged is the runner's call, which knows what reached the file.
 */
fun dropLastSetForMember(
    steps: List<RunnerStep>,
    memberKey: String,
): List<RunnerStep> {
    val indices = setStepIndices(steps, memberKey)
    if (indices.size <= 1) return steps
    val lastIndex = indices.last()
    val before = steps[lastIndex - 1]
    val dropsRest = before is RunnerStep.Rest && before.memberKey == memberKey && !before.standalone
    val from = if (dropsRest) lastIndex - 1 else lastIndex
    return renumber(steps.subList(0, from) + steps.subList(lastIndex + 1, steps.size), memberKey)
}

/**
 * [exercise] in place of whatever is left of [memberKey], from [fromIndex] on, under [newMemberKey].
 *
 * A reps or hold substitute takes the *remaining set count*, with its own targets and rest. Any other
 * type runs its own shape in full: HIIT rounds aren't sets, and resizing one would loop forever, with
 * no set to clone. The caller must never reuse a key: every log in the runner is keyed by it.
 */
fun swapExerciseForMember(
    steps: List<RunnerStep>,
    memberKey: String,
    fromIndex: Int,
    exercise: Exercise,
    newMemberKey: String,
): List<RunnerStep> {
    val remaining = setStepIndices(steps, memberKey).count { it >= fromIndex }
    val lastIndex = steps.indexOfLast { it.memberKey == memberKey }
    // Non-circuit members are contiguous, which is what makes "the rest of this member" a slice.
    val start = steps.getOrNull(fromIndex)?.takeIf { remaining > 0 && lastIndex >= fromIndex }
    val replacement =
        start?.let {
            resized(buildStepsForExercise(exercise, it.blockIndex, newMemberKey), newMemberKey, remaining)
        }
    // An unvalidated `sets: 0` expands to nothing, and swapping to nothing would delete the member.
    return if (replacement.isNullOrEmpty()) {
        steps
    } else {
        steps.subList(0, fromIndex) + replacement + steps.subList(lastIndex + 1, steps.size)
    }
}

/** A set-based [steps] grown or trimmed to [sets] sets of [memberKey]; any other shape as it is. */
private fun resized(
    steps: List<RunnerStep>,
    memberKey: String,
    sets: Int,
): List<RunnerStep> {
    if (setStepsForMember(steps, memberKey) == 0) return steps
    var next = steps
    while (setStepsForMember(next, memberKey) > sets) next = dropLastSetForMember(next, memberKey)
    while (setStepsForMember(next, memberKey) < sets) next = addSetForMember(next, memberKey)
    return next
}
