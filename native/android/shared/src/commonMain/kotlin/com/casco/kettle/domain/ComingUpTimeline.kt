package com.casco.kettle.domain

/**
 * Coming up as a subway map (#415): the whole session as one line, what's done, the stop you're at and
 * what's ahead, where [upcomingOutline] holds only what's ahead (and stays, for Home's preview). Built
 * from the live step list, as the outline is, so swaps, added sets and an ad-hoc queue come out right
 * unasked; held by `conformance/steps.yaml`'s `timeline` op. Descriptors only.
 */
enum class StopState(
    val json: String,
) {
    DONE("done"),
    NOW("now"),
    NEXT("next"),
}

/** One member of the circuit you're in, in the round on screen. */
data class LoopMember(
    val memberKey: String,
    val step: WorkStep,
    val state: StopState,
)

sealed interface TimelineStop {
    val state: StopState

    /**
     * An exercise outside a circuit: its [count] work steps in [unit] (null for one-shot work). [step]
     * is the one on screen when it's [StopState.NOW] and working, its next set while resting between
     * sets, and its first otherwise, which is what the stop names and targets.
     */
    data class Exercise(
        val memberKey: String,
        val step: WorkStep,
        val count: Int,
        val unit: WorkUnit?,
        override val state: StopState,
    ) : TimelineStop

    /** A standalone Rest block: a tick on the line. Rest between sets and members isn't drawn. */
    data class Rest(
        val seconds: Double,
        override val state: StopState,
    ) : TimelineStop

    /**
     * The circuit you're in, as a loop off the line: the [round] with work left, of [rounds], its
     * [members] each done, now or next, the rest between moves and after each round (null for none), and
     * the [lastRound] the list runs to, for "Then round 3".
     */
    data class Loop(
        val blockIndex: Int,
        val round: Long,
        val rounds: Long,
        val lastRound: Long,
        val betweenMovesSec: Double?,
        val afterRoundSec: Double?,
        val members: List<LoopMember>,
    ) : TimelineStop {
        override val state: StopState get() = StopState.NOW
    }

    /** A circuit done or still ahead, collapsed to its rounds and the names one round runs. */
    data class Circuit(
        val blockIndex: Int,
        val rounds: Int,
        val names: List<String>,
        override val state: StopState,
    ) : TimelineStop
}

/** The sheet's header, "7 of 12 steps done", counting work steps only, and its stops in order. */
data class ComingUpTimeline(
    val done: Int,
    val total: Int,
    val stops: List<TimelineStop>,
)

/**
 * The session at [stepIndex] as the sheet draws it. An index past the end, a parked ad-hoc session or a
 * finished one, is everything done. Never throws: it's drawn in the runner's frame.
 */
fun comingUpTimeline(
    steps: List<RunnerStep>,
    stepIndex: Int,
): ComingUpTimeline {
    val stops = mutableListOf<TimelineStop>()
    var i = 0
    while (i < steps.size) {
        val step = steps[i]
        val end = runEnd(steps, i)
        val range = i until end
        val state =
            when {
                range.last < stepIndex -> StopState.DONE
                stepIndex in range -> StopState.NOW
                else -> StopState.NEXT
            }
        val run = steps.subList(i, end)
        when {
            step.circuit != null -> stops += circuitStop(run, i, stepIndex, state)
            step is RunnerStep.Rest && step.standalone -> stops += TimelineStop.Rest(step.seconds, state)
            else -> exerciseStop(run, i, stepIndex)?.let(stops::add)
        }
        i = end
    }
    val work = steps.withIndex().filter { it.value is WorkStep }
    return ComingUpTimeline(work.count { it.index < stepIndex }, work.size, stops)
}

/**
 * Where the run starting at [start] ends: a circuit block whole, rests included; a standalone rest
 * alone; otherwise one member's steps and the rests between them, which carry its key, so a swap's
 * substitute, under a key of its own, is a run of its own.
 */
private fun runEnd(
    steps: List<RunnerStep>,
    start: Int,
): Int {
    val first = steps[start]
    var end = start + 1
    val belongs: (RunnerStep) -> Boolean =
        when {
            first.circuit != null -> { step -> step.circuit != null && step.blockIndex == first.blockIndex }

            first is RunnerStep.Rest && first.standalone -> { _ -> false }

            else -> { step ->
                step.circuit == null && !(step is RunnerStep.Rest && step.standalone) &&
                    step.memberKey == first.memberKey
            }
        }
    while (end < steps.size && belongs(steps[end])) end++
    return end
}

private fun exerciseStop(
    run: List<RunnerStep>,
    start: Int,
    stepIndex: Int,
): TimelineStop.Exercise? {
    val work =
        run.withIndex().filter { it.value is WorkStep }.map { (index, step) ->
            start + index to step as WorkStep
        }
    val first = work.firstOrNull()?.second ?: return null
    val state = workState(work.map { it.first }, stepIndex)
    // Now: the step on screen when it's work, or the set the rest on screen leads to.
    val shown = if (state == StopState.NOW) work.firstOrNull { it.first >= stepIndex }?.second ?: first else first
    return TimelineStop.Exercise(first.memberKey, shown, work.size, unitOf(first), state)
}

/**
 * Where work at [indices] stands against the step on screen, judged by the work alone: done once all of
 * it is behind, so the rest after an exercise's last set leads on to the next rather than holding it;
 * now once any of it has started, a rest between its own sets or rounds included; otherwise next.
 */
private fun workState(
    indices: List<Int>,
    stepIndex: Int,
): StopState =
    when {
        indices.all { it < stepIndex } -> StopState.DONE
        indices.any { it <= stepIndex } -> StopState.NOW
        else -> StopState.NEXT
    }

private fun circuitStop(
    run: List<RunnerStep>,
    start: Int,
    stepIndex: Int,
    state: StopState,
): TimelineStop {
    val work =
        run.withIndex().filter { it.value is WorkStep }.map { (index, step) ->
            start + index to step as WorkStep
        }
    val rounds = work.map { it.second.circuitRound }.distinct()
    val blockIndex = run.first().blockIndex
    // The round with work left at or after the step on screen: on the rest after a round, the next one.
    val round = work.firstOrNull { it.first >= stepIndex }?.second?.circuitRound
    if (state != StopState.NOW || round == null) {
        val names =
            work
                .filter { it.second.circuitRound == rounds.firstOrNull() }
                .map { it.second.exerciseName }
                .distinct()
        return TimelineStop.Circuit(blockIndex, rounds.size, names, if (round == null) StopState.DONE else state)
    }
    val inRound = work.filter { it.second.circuitRound == round }
    val members =
        inRound.map { it.second.memberKey }.distinct().map { key ->
            val visit = inRound.filter { it.second.memberKey == key }
            LoopMember(key, visit.first().second, workState(visit.map { it.first }, stepIndex))
        }
    val (betweenMoves, afterRound) = circuitRests(run)
    return TimelineStop.Loop(
        blockIndex,
        round,
        checkNotNull(inRound.first().second.circuit).rounds,
        rounds.last(),
        betweenMoves,
        afterRound,
        members,
    )
}

/**
 * A circuit's rest between moves and after each round, read off its rest steps by what they sit
 * between: two members in one round, or two rounds. A HIIT member's rest between its own rounds is
 * neither. Null where the block has none.
 */
private fun circuitRests(run: List<RunnerStep>): Pair<Double?, Double?> {
    var betweenMoves: Double? = null
    var afterRound: Double? = null
    run.forEachIndexed { index, step ->
        if (step !is RunnerStep.Rest) return@forEachIndexed
        val before = run.subList(0, index).lastOrNull { it is WorkStep } as? WorkStep
        val after = run.subList(index + 1, run.size).firstOrNull { it is WorkStep } as? WorkStep
        if (before == null || after == null) return@forEachIndexed
        when {
            before.circuitRound != after.circuitRound -> afterRound = afterRound ?: step.seconds
            before.memberKey != after.memberKey -> betweenMoves = betweenMoves ?: step.seconds
        }
    }
    return betweenMoves to afterRound
}

private val WorkStep.circuitRound: Long get() = circuit?.round ?: 0
