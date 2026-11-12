package com.casco.kettle.domain

/**
 * What's left in a running workout, for the Coming up sheet, ported from `upcomingOutline` in RN's
 * `src/hooks/session-outline.ts` and held by `conformance/steps.yaml`, whose goldens it writes since
 * the TS twin went (#308).
 *
 * Built from the **live step list, never from the workout's blocks**: the list is what will run, so a
 * swap, an added set, an override, an ad-hoc queue and a truncated workout all come out right without
 * being special-cased. Descriptors only: names are the user's and render verbatim, the rest is the
 * sheet's to translate.
 */
enum class WorkUnit(
    val json: String,
) {
    SET("set"),
    ROUND("round"),
    MINUTE("minute"),
    INTERVAL("interval"),
}

/** One circuit member's visit, carried by its first step so the sheet can show the target. */
data class UpcomingMember(
    val memberKey: String,
    val step: WorkStep,
)

sealed interface UpcomingItem {
    /**
     * A non-circuit exercise. [started] is whether the step on screen belongs to it, which is the
     * difference between "2 more sets" and "2 sets". [unit] is null for one-shot work.
     */
    data class Exercise(
        val memberKey: String,
        val step: WorkStep,
        val left: Int,
        val unit: WorkUnit?,
        val started: Boolean,
    ) : UpcomingItem

    /** A standalone Rest block. Inter-set and circuit rest are never listed. */
    data class Rest(
        val seconds: Double,
    ) : UpcomingItem

    /** The first round with work left in the circuit you're in, each remaining member spelled out. */
    data class Round(
        val blockIndex: Int,
        val round: Long,
        val rounds: Long,
        val members: List<UpcomingMember>,
    ) : UpcomingItem

    /** The rounds after that one, collapsed. */
    data class MoreRounds(
        val blockIndex: Int,
        val count: Int,
        val names: List<String>,
    ) : UpcomingItem

    /** A circuit you haven't reached, collapsed to one item. */
    data class Circuit(
        val blockIndex: Int,
        val rounds: Int,
        val names: List<String>,
    ) : UpcomingItem
}

private const val SECONDS_PER_EMOM_MINUTE = 60.0

/**
 * Sets for reps and holds, rounds for HIIT, and for an EMOM minutes only when its interval is one:
 * a 30s interval over ten minutes would otherwise read "20 minutes".
 */
private fun unitOf(step: WorkStep): WorkUnit? =
    when (step) {
        is RunnerStep.Reps, is RunnerStep.Hold -> {
            WorkUnit.SET
        }

        is RunnerStep.Interval -> {
            when (step.variant) {
                IntervalVariant.HIIT -> {
                    WorkUnit.ROUND
                }

                IntervalVariant.EMOM -> {
                    if (step.targetSec ==
                        SECONDS_PER_EMOM_MINUTE
                    ) {
                        WorkUnit.MINUTE
                    } else {
                        WorkUnit.INTERVAL
                    }
                }

                IntervalVariant.AMRAP, IntervalVariant.CARDIO -> {
                    null
                }
            }
        }
    }

/**
 * One entry per member, in first-appearance order. HIIT and EMOM members run their interval count
 * on every visit, so without the folding an 8-round HIIT member is listed eight times in a round.
 */
private fun distinctMembers(steps: List<WorkStep>): List<UpcomingMember> =
    steps.distinctBy { it.memberKey }.map { UpcomingMember(it.memberKey, it) }

/** The round a circuit step is in. Only called on steps the outline already knows are in one. */
private val WorkStep.round: Long get() = checkNotNull(circuit).round

/**
 * One circuit's remaining steps. Rounds are read off the steps' positions, never counted from a
 * member's steps, and they are the rounds *present in the list*, so a truncated workout lists exactly
 * what will run.
 */
private fun circuitItems(
    group: List<RunnerStep>,
    current: RunnerStep?,
): List<UpcomingItem> {
    val blockIndex = group.first().blockIndex
    val currentCircuit = current?.circuit
    val visit =
        if (currentCircuit != null &&
            current.blockIndex == blockIndex
        ) {
            current.memberKey to currentCircuit.round
        } else {
            null
        }

    // The rest of the visit in progress, a HIIT member's remaining intervals, is the NOW row's.
    val work =
        group.filterIsInstance<WorkStep>().filter { step ->
            step.circuit != null && !(visit != null && step.memberKey == visit.first && step.round == visit.second)
        }
    val rounds = work.map { it.round }.distinct()
    val inRound = { round: Long -> work.filter { it.round == round } }
    val namesIn = { round: Long -> distinctMembers(inRound(round)).map { it.step.exerciseName } }

    return when {
        work.isEmpty() -> {
            emptyList()
        }

        visit == null -> {
            listOf(UpcomingItem.Circuit(blockIndex, rounds.size, namesIn(rounds.first())))
        }

        // "First round with work left", not "the current round": on the last member of a round, or
        // the rest after it, the current round has nothing left.
        else -> {
            buildList {
                add(
                    UpcomingItem.Round(
                        blockIndex,
                        rounds.first(),
                        checkNotNull(work.first().circuit).rounds,
                        distinctMembers(inRound(rounds.first())),
                    ),
                )
                if (rounds.size > 1) add(UpcomingItem.MoreRounds(blockIndex, rounds.size - 1, namesIn(rounds[1])))
            }
        }
    }
}

/**
 * Everything after the step at [stepIndex], grouped the way the workout was written.
 *
 * Never throws: a throw here is a throw in the runner's frame. An index past the end, which is how a
 * parked ad-hoc session looks, or an empty list is simply nothing left.
 */
fun upcomingOutline(
    steps: List<RunnerStep>,
    stepIndex: Int,
): List<UpcomingItem> {
    val current = steps.getOrNull(stepIndex)
    val items = mutableListOf<UpcomingItem>()
    var i = maxOf(stepIndex + 1, 0)

    while (i < steps.size) {
        val step = steps[i]
        if (step.circuit != null) {
            // A circuit's steps are contiguous as a block, rests included, so it's taken whole.
            var end = i
            while (end < steps.size && steps[end].circuit != null && steps[end].blockIndex == step.blockIndex) end++
            items += circuitItems(steps.subList(i, end), current)
            i = end
        } else {
            i++
            items.addStep(step, current)
        }
    }
    return items
}

/**
 * The whole session before it starts, for Home's preview (#280): the first step included, since no NOW
 * row is showing it. Each circuit is spelled out as its first round, every member with its target,
 * where Coming up collapses one it hasn't reached to names: the preview's question is what to pick up,
 * which names alone half answer, and every round of a circuit runs the same members.
 */
fun sessionOutline(steps: List<RunnerStep>): List<UpcomingItem> =
    upcomingOutline(steps, stepIndex = -1).map { item ->
        (item as? UpcomingItem.Circuit)?.let { firstRound(steps, it.blockIndex) } ?: item
    }

/**
 * Null when the block has no circuit work, which a Circuit item can't come from, but this is drawn in a
 * frame, so the item it would replace stands instead of a throw.
 */
private fun firstRound(
    steps: List<RunnerStep>,
    blockIndex: Int,
): UpcomingItem.Round? {
    val work = steps.filterIsInstance<WorkStep>().filter { it.blockIndex == blockIndex && it.circuit != null }
    val circuit = work.firstOrNull()?.circuit ?: return null
    return UpcomingItem.Round(
        blockIndex,
        circuit.round,
        circuit.rounds,
        distinctMembers(work.filter { it.round == circuit.round }),
    )
}

/**
 * Grouped by member key, not block: a swap gives the substitute a new key inside the same block, and
 * it is listed under its own name. Inter-set rest adds nothing, so a member's sets stay adjacent.
 */
private fun MutableList<UpcomingItem>.addStep(
    step: RunnerStep,
    current: RunnerStep?,
) {
    when (step) {
        is RunnerStep.Rest -> {
            if (step.standalone) add(UpcomingItem.Rest(step.seconds))
        }

        is WorkStep -> {
            val last = lastOrNull()
            if (last is UpcomingItem.Exercise && last.memberKey == step.memberKey) {
                set(lastIndex, last.copy(left = last.left + 1))
            } else {
                // Also started while resting between its sets: an inter-set rest carries its key.
                val started = current != null && current.circuit == null && current.memberKey == step.memberKey
                add(UpcomingItem.Exercise(step.memberKey, step, 1, unitOf(step), started))
            }
        }
    }
}
