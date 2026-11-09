package com.casco.kettle.domain

/**
 * The session runner's step model and the workout → step-list expansion, ported from RN's
 * `src/hooks/session-steps.ts` and held by `conformance/steps.yaml` (#303), whose goldens it writes
 * now that the TS twin is gone (#308). The reasoning behind each rule is in that file's comments, which
 * the flip release deleted: `git log -S` on a rule's name finds them. What is repeated here is what a
 * reader of this file can't do without.
 *
 * Pure: every function returns a new list and leaves its input alone.
 */
enum class IntervalVariant(
    val json: String,
) {
    HIIT("hiit"),
    EMOM("emom"),
    AMRAP("amrap"),
    CARDIO("cardio"),
}

/**
 * Where a step sits inside its circuit: which round, and which member of the round-robin. Null on
 * every step outside a circuit. [member] is 1-based over the members that *resolved* to an exercise,
 * which is why it is stamped during expansion: a dropped member leaves no trace afterwards.
 *
 * A rest takes the position of the work it *follows*.
 */
data class CircuitPosition(
    val round: Long,
    val rounds: Long,
    val member: Int,
    val memberTotal: Int,
)

/**
 * A circuit member's single visit: the circuit's rounds repeat the member, not its own `sets`, so a
 * visit is one set numbered by its round, with no inter-set rest. Only reps and holds read it: a HIIT
 * or EMOM member runs its full interval count on *every* visit, which is why the ceilings exist.
 */
internal data class CircuitVisit(
    val index: Long,
    val total: Long,
)

sealed interface RunnerStep {
    val blockIndex: Int
    val memberKey: String
    val circuit: CircuitPosition?
    val exerciseId: String

    /**
     * [holdTargetSec] is the mark to cross, not the finish line. [holdEndSec] is where the hold ends
     * by itself, the top of the range, and null for a max-effort hold that the Done button ends.
     */
    data class Hold(
        override val blockIndex: Int,
        override val memberKey: String,
        override val exerciseId: String,
        override val exerciseName: String,
        val holdTargetSec: Double?,
        val holdTargetMaxSec: Double?,
        val holdEndSec: Double?,
        override val setIndex: Long,
        override val setTotal: Long,
        override val notes: String?,
        override val circuit: CircuitPosition? = null,
    ) : WorkStep

    data class Reps(
        override val blockIndex: Int,
        override val memberKey: String,
        override val exerciseId: String,
        override val exerciseName: String,
        val targetReps: Long,
        val targetRepsMax: Long?,
        val targetWeightKg: Double?,
        override val setIndex: Long,
        override val setTotal: Long,
        override val notes: String?,
        override val circuit: CircuitPosition? = null,
    ) : WorkStep

    /** [countUp] is true only for cardio with no duration: it counts up and never auto-advances. */
    data class Interval(
        override val blockIndex: Int,
        override val memberKey: String,
        override val exerciseId: String,
        override val exerciseName: String,
        val variant: IntervalVariant,
        val targetSec: Double,
        val countUp: Boolean,
        override val setIndex: Long,
        override val setTotal: Long,
        val targetReps: Long? = null,
        val cardioDistanceMeters: Double? = null,
        override val notes: String?,
        override val circuit: CircuitPosition? = null,
    ) : WorkStep

    /**
     * [standalone] is a Rest block, its own logged entry. Any other rest is between sets, rounds or
     * members, and is only emitted when longer than zero: a zero-second rest step flashed the rest
     * screen and its chime between back-to-back sets. A standalone one is kept at 0, being an entry.
     */
    data class Rest(
        override val blockIndex: Int,
        override val memberKey: String,
        override val exerciseId: String,
        val standalone: Boolean,
        val seconds: Double,
        override val circuit: CircuitPosition? = null,
    ) : RunnerStep
}

/** A step that is work rather than rest: what the Next card and the Coming up sheet describe. */
sealed interface WorkStep : RunnerStep {
    val exerciseName: String
    val setIndex: Long
    val setTotal: Long
    val notes: String?
}

/**
 * The ceilings on one expansion. [MAX_STEPS_PER_EXERCISE] sits above anything the schema admits for
 * one exercise, so it only fires on a path that skipped validation. [MAX_STEPS_PER_WORKOUT] is the one
 * that can truncate something importable, since neither a workout's blocks nor a circuit's members
 * have an upper bound: a schema-legal 500-round circuit of two 500-round HIIT members is a million
 * steps, an OOM on a phone. Truncating is a poor degrade, and still better than that.
 */
internal const val MAX_STEPS_PER_WORKOUT = 20_000
internal const val MAX_STEPS_PER_EXERCISE = 2000

/** The step list, and whether a ceiling cut it short, which the runner says before the count-in. */
data class StepBuildResult(
    val steps: List<RunnerStep>,
    val truncated: Boolean,
)

/** The step with its circuit position stamped on, whatever kind it is. */
private fun RunnerStep.withCircuit(position: CircuitPosition): RunnerStep =
    when (this) {
        is RunnerStep.Hold -> copy(circuit = position)
        is RunnerStep.Reps -> copy(circuit = position)
        is RunnerStep.Interval -> copy(circuit = position)
        is RunnerStep.Rest -> copy(circuit = position)
    }

/** JS truthiness for an optional number: absent, 0 and NaN are all "not configured". */
internal fun Double?.isConfigured(): Boolean = this != null && this != 0.0 && !isNaN()

/**
 * The whole workout, expanded, and whether a ceiling cut it short. The workout ceiling is checked per
 * block, per round and per member visit, so a truncated list is made of whole blocks or whole visits.
 */
fun buildStepsWithLimits(
    workout: Workout,
    exercises: List<Exercise>,
): StepBuildResult {
    val steps = mutableListOf<RunnerStep>()
    var truncated = false
    workout.blocks.forEachIndexed { blockIndex, block ->
        if (steps.size >= MAX_STEPS_PER_WORKOUT) {
            truncated = true
            return@forEachIndexed
        }
        val built =
            when (block) {
                is WorkoutBlock.ExerciseBlock -> {
                    exercises.firstOrNull { it.id == block.exerciseId }?.let {
                        expandExercise(it, blockIndex, "$blockIndex", block.configOverride)
                    }
                }

                is WorkoutBlock.Circuit -> {
                    expandCircuit(block, blockIndex, exercises, steps.size)
                }
            }
        if (built != null) {
            steps += built.steps
            if (built.truncated) truncated = true
        }
    }
    return StepBuildResult(steps, truncated)
}

private data class ResolvedMember(
    val member: CircuitMember,
    val memberIndex: Int,
    val exercise: Exercise,
)

/**
 * A circuit, round-robin: A, B, C, A, B, C. Each member's key, `block:memberIndex`, is stable across
 * rounds, so its sets log as one entry. Circuit rest carries the key `block:circuit-rest`. [before]
 * is how many steps the workout already holds, which the ceiling counts against.
 */
private fun expandCircuit(
    block: WorkoutBlock.Circuit,
    blockIndex: Int,
    exercises: List<Exercise>,
    before: Int,
): StepBuildResult? {
    val members =
        block.members.mapIndexedNotNull { memberIndex, member ->
            exercises.firstOrNull { it.id == member.exerciseId }?.let { ResolvedMember(member, memberIndex, it) }
        }
    if (members.isEmpty()) return null

    val steps = mutableListOf<RunnerStep>()
    var truncated = false
    val full = { before + steps.size >= MAX_STEPS_PER_WORKOUT }
    val positionAt = { round: Long, slot: Int -> CircuitPosition(round + 1, block.rounds, slot + 1, members.size) }
    val circuitRest = { seconds: Double, exerciseId: String, position: CircuitPosition ->
        RunnerStep.Rest(blockIndex, "$blockIndex:circuit-rest", exerciseId, standalone = false, seconds, position)
    }

    for (round in 0 until block.rounds) {
        if (full()) {
            truncated = true
            break
        }
        for ((slot, resolved) in members.withIndex()) {
            if (full()) {
                truncated = true
                break
            }
            val at = positionAt(round, slot)
            val key = "$blockIndex:${resolved.memberIndex}"
            val visit =
                expandExercise(
                    resolved.exercise,
                    blockIndex,
                    key,
                    resolved.member.configOverride,
                    CircuitVisit(round + 1, block.rounds),
                )
            if (visit.truncated) truncated = true
            visit.steps.mapTo(steps) { it.withCircuit(at) }
            val between = block.restBetweenExercisesSec
            if (slot < members.size - 1 &&
                between.isConfigured()
            ) {
                steps += circuitRest(between!!, resolved.exercise.id, at)
            }
        }
        val betweenRounds = block.restBetweenRoundsSec
        if (round < block.rounds - 1 && betweenRounds.isConfigured()) {
            steps += circuitRest(betweenRounds!!, members.first().exercise.id, positionAt(round, members.size - 1))
        }
    }
    return StepBuildResult(steps, truncated)
}

/** The steps alone, which is all a caller wants unless it has to say the workout will stop early. */
fun buildSteps(
    workout: Workout,
    exercises: List<Exercise>,
): List<RunnerStep> = buildStepsWithLimits(workout, exercises).steps

/**
 * One exercise's steps outside any workout: a mid-session substitute, or an ad-hoc session's next
 * exercise. The clamp flag is dropped: a set-based substitute is resized to the sets it replaces.
 */
fun buildStepsForExercise(
    exercise: Exercise,
    blockIndex: Int,
    memberKey: String,
): List<RunnerStep> = expandExercise(exercise, blockIndex, memberKey).steps
