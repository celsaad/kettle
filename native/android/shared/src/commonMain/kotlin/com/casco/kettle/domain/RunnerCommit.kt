package com.casco.kettle.domain

import kotlin.math.min

// The runner's write path (§2.5 of #294's inventory): every commit reaches the session file at once,
// and Prev straight after one takes exactly that one back.
//
// RN kept each member's sets in a working copy beside the file. Here the session's own entry is the
// member's log, found through `entryIndex`, so a rebuild needs nothing the file doesn't already hold.
// Entries are only appended at the end or removed from the end, so a recorded index stays valid.

/** The entry [memberKey]'s work has grown so far, or null before its first commit. */
internal fun RunnerState.memberEntry(memberKey: String): SessionEntry? =
    snapshot.entryIndex[memberKey]?.let(session.entries::getOrNull)

/** [entry] as [memberKey]'s: appended the first time and its index recorded, rewritten in place after. */
internal fun RunnerState.withMemberEntry(
    memberKey: String,
    entry: SessionEntry,
): RunnerState {
    val index = snapshot.entryIndex[memberKey]
    return if (index == null) {
        copy(
            session = session.copy(entries = session.entries + entry),
            snapshot = snapshot.copy(entryIndex = snapshot.entryIndex + (memberKey to session.entries.size)),
        )
    } else {
        val entries = session.entries.toMutableList().apply { set(index, entry) }
        copy(session = session.copy(entries = entries))
    }
}

/** A one-shot entry, AMRAP, cardio or a standalone rest, which no later commit grows. */
private fun RunnerState.withOneShot(entry: SessionEntry): Committed {
    val entries = session.entries + entry
    return Committed(copy(session = session.copy(entries = entries)), true)
}

/** The step on screen's own contribution, written through: the set, round or minute just done, or the rest taken. */
internal fun commitStep(
    state: RunnerState,
    step: RunnerStep,
    now: Long,
): Committed =
    when (step) {
        is RunnerStep.Hold -> commitSet(state, step, setEntry(state, step, now))
        is RunnerStep.Reps -> commitSet(state, step, setEntry(state, step, now))
        is RunnerStep.Interval -> commitInterval(state, step, now)
        is RunnerStep.Rest -> commitRest(state, step, now)
    }

/**
 * The member's entry with this set added, or null for a zero set, which is how an exercise is
 * skipped and is never written. A hold logs its elapsed time clamped to its end, so one noticed late
 * after a gap logs the end rather than the time away; ending one early by hand logs the honest number.
 * Bodyweight is an absent weight, not a load of 0.
 */
private fun setEntry(
    state: RunnerState,
    step: WorkStep,
    now: Long,
): SessionEntry? {
    val inputs = state.snapshot.inputs
    val entry = state.memberEntry(step.memberKey)
    return when (step) {
        is RunnerStep.Hold -> {
            val elapsed = elapsedSec(state.snapshot.clock, now).toDouble()
            val holdSec = step.holdEndSec?.let { min(elapsed, it) } ?: elapsed
            val sets = (entry as? SessionEntry.TimedHold)?.sets.orEmpty()
            SessionEntry.TimedHold(step.exerciseId, sets + TimedHoldSetLog(holdSec, 0.0)).takeIf { holdSec != 0.0 }
        }

        is RunnerStep.Reps -> {
            val set = RepsSetLog(inputs.reps, inputs.weightKg.takeIf { it != 0.0 }, inputs.rpe, 0.0)
            val sets = (entry as? SessionEntry.Reps)?.sets.orEmpty()
            SessionEntry.Reps(step.exerciseId, sets + set).takeIf { inputs.reps != 0L }
        }

        else -> {
            null
        }
    }
}

/** Records whether the set was dropped, so the rest after it knows not to write onto the set before. */
private fun commitSet(
    state: RunnerState,
    step: WorkStep,
    entry: SessionEntry?,
): Committed {
    val dropped = state.snapshot.droppedLastSet.let { if (entry == null) it + step.memberKey else it - step.memberKey }
    val marked = state.copy(snapshot = state.snapshot.copy(droppedLastSet = dropped))
    return entry?.let { Committed(marked.withMemberEntry(step.memberKey, it), true) } ?: Committed(marked, false)
}

/**
 * HIIT counts its round in place and EMOM adds its minute, both exempt from zero trimming. AMRAP and
 * cardio are one-shot, and dropped at zero. Cardio at 0 s is a skip whatever its distance says, since
 * that distance is the plan's, copied in. A cardio with a duration is clamped to it, as a hold is.
 */
private fun commitInterval(
    state: RunnerState,
    step: RunnerStep.Interval,
    now: Long,
): Committed {
    val inputs = state.snapshot.inputs
    val entry = state.memberEntry(step.memberKey)
    val elapsed = elapsedSec(state.snapshot.clock, now).toDouble()
    return when (step.variant) {
        IntervalVariant.HIIT -> {
            val rounds = (entry as? SessionEntry.Hiit)?.roundsCompleted ?: 0
            Committed(state.withMemberEntry(step.memberKey, SessionEntry.Hiit(step.exerciseId, rounds + 1)), true)
        }

        IntervalVariant.EMOM -> {
            val minutes =
                (entry as? SessionEntry.Emom)?.minutes.orEmpty() + EmomMinuteLog(inputs.reps.takeIf { it != 0L })
            Committed(state.withMemberEntry(step.memberKey, SessionEntry.Emom(step.exerciseId, minutes)), true)
        }

        IntervalVariant.AMRAP -> {
            val amrap =
                SessionEntry.Amrap(
                    step.exerciseId,
                    inputs.roundsCompleted,
                    inputs.extraReps.takeIf { it != 0L },
                )
            entryWithoutZeroWork(amrap)?.let(state::withOneShot) ?: Committed(state, false)
        }

        IntervalVariant.CARDIO -> {
            val durationSec = if (step.countUp) elapsed else min(elapsed, step.targetSec)
            val cardio = SessionEntry.Cardio(step.exerciseId, durationSec, step.cardioDistanceMeters)
            if (durationSec == 0.0) Committed(state, false) else state.withOneShot(cardio)
        }
    }
}

/**
 * A standalone rest is its own entry. A rest between sets writes the time taken onto the member's
 * last logged set and rewrites the entry, and writes nothing after a dropped set. Rest is never
 * clamped: the time that passed is how long you rested.
 */
private fun commitRest(
    state: RunnerState,
    step: RunnerStep.Rest,
    now: Long,
): Committed {
    val taken = elapsedSec(state.snapshot.clock, now).toDouble()
    val rested = state.memberEntry(step.memberKey)?.let { withRestTaken(it, taken) }
    return when {
        step.standalone -> state.withOneShot(SessionEntry.Rest(step.exerciseId, taken))
        step.memberKey in state.snapshot.droppedLastSet || rested == null -> Committed(state, false)
        else -> Committed(state.withMemberEntry(step.memberKey, rested), true)
    }
}

private fun withRestTaken(
    entry: SessionEntry,
    taken: Double,
): SessionEntry? =
    when (entry) {
        is SessionEntry.Reps -> {
            entry.sets.lastOrNull()?.let { entry.copy(sets = entry.sets.dropLast(1) + it.copy(restTakenSec = taken)) }
        }

        is SessionEntry.TimedHold -> {
            entry.sets.lastOrNull()?.let { entry.copy(sets = entry.sets.dropLast(1) + it.copy(restTakenSec = taken)) }
        }

        else -> {
            null
        }
    }
