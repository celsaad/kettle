package com.casco.kettle.domain

/**
 * The engine driven the way RN's `use-session-runner.test.tsx` drives the hook: a virtual clock, a
 * 1 Hz ticker ([tick]), a gap away from the app ([away]), and the session as the effects wrote it.
 * The fixtures are that suite's, plus the built-in rest, so the swap picker has one to leave out.
 */
internal class Runner(
    workout: Workout?,
    val exercises: List<Exercise> = RUNNER_EXERCISES,
    start: Long = T0,
) {
    var now = start
    var state: RunnerState
    val effects = mutableListOf<RunnerEffect>()

    init {
        val plan = RunnerPlan.Runnable(workout, exercises, workout?.let { buildSteps(it, exercises) }.orEmpty())
        val started = startRunner(plan, null, now)
        state = started.state
        effects += started.effects
    }

    /** The session as the service would have written it: the last [RunnerEffect.WriteSession]. */
    val written: Session get() = effects.filterIsInstance<RunnerEffect.WriteSession>().last().session
    val entries: List<SessionEntry> get() = written.entries
    val snapshot: RunnerSnapshot get() = state.snapshot
    val step: RunnerStep? get() = snapshot.step
    val index: Int get() = snapshot.stepIndex
    val inputs: StepInputs get() = snapshot.inputs
    val completions: Int get() = effects.count { it is RunnerEffect.Completed }

    fun send(command: RunnerCommand): RunnerTransition {
        val transition = reduceRunner(state, command, now)
        state = transition.state
        effects += transition.effects
        return transition
    }

    /** Log the set, Done, Skip or log the interval, aimed at the step on screen. */
    fun advance() = send(RunnerCommand.Advance(snapshot.visit))

    fun prev() = send(RunnerCommand.Prev)

    fun finish() = send(RunnerCommand.Finish)

    /** [seconds] of a 1 Hz ticker, each aimed at whatever step is on screen when it fires. */
    fun tick(seconds: Int) =
        repeat(seconds) {
            now += MS
            send(RunnerCommand.TimeUpdate(snapshot.visit))
        }

    /** [seconds] with nothing ticking, then one catch-up on return. */
    fun away(seconds: Int) {
        now += seconds * MS
        send(RunnerCommand.TimeUpdate(snapshot.visit))
    }

    fun cues(cue: RunnerCue): Int = effects.count { it == RunnerEffect.PlayCue(cue) }

    fun clearEffects() {
        val last = effects.filterIsInstance<RunnerEffect.WriteSession>().lastOrNull()
        effects.clear()
        last?.let(effects::add)
    }

    fun elapsed(): Long = elapsedSec(snapshot.clock, now)

    fun remaining(): Double = remainingSec(snapshot.clock, now)

    inline fun <reified E : SessionEntry> entry(exerciseId: String): E? =
        entries.filterIsInstance<E>().firstOrNull { it.exercise == exerciseId }

    fun exercise(id: String): Exercise = exercises.single { it.id == id }
}

internal const val T0 = 1_785_142_800_000L // 2026-07-27T09:00:00Z
internal const val MS = 1000L

internal val RUNNER_EXERCISES =
    listOf(
        Exercise("burpees", "Burpees", ExerciseConfig.Hiit(workSec = 40.0, restSec = 20.0, rounds = 3)),
        Exercise("pullups", "Pull-ups", ExerciseConfig.Reps(3, 6, targetWeightKg = 20.0, restSec = 90.0)),
        Exercise("lsit", "L-Sit", ExerciseConfig.TimedHold(3, holdSecMin = 15.0, restSec = 60.0)),
        Exercise("plank", "Plank", ExerciseConfig.TimedHold(2, holdSecMin = 15.0, holdSecMax = 25.0, restSec = 30.0)),
        Exercise("deadhang", "Dead Hang", ExerciseConfig.TimedHold(2, restSec = 30.0)),
        Exercise("grinder", "Grinder", ExerciseConfig.Amrap(timeCapSec = 300.0)),
        Exercise("swings", "KB Swings", ExerciseConfig.Emom(intervalSec = 60.0, totalMinutes = 3.0, targetReps = 10)),
        Exercise("climb", "Rope Climb", ExerciseConfig.Emom(intervalSec = 60.0, totalMinutes = 2.0)),
        Exercise("row", "Row", ExerciseConfig.Cardio(durationSec = 60.0)),
        Exercise("walk", "Walk", ExerciseConfig.Cardio()),
        Exercise("run", "Run", ExerciseConfig.Cardio(distanceMeters = 5000.0)),
        Exercise("dips", "Dips", ExerciseConfig.Reps(3, 8, restSec = 0.0)),
        Exercise("rest", "Rest", ExerciseConfig.Rest(durationSec = 60.0)),
    )

internal fun workoutOf(vararg blocks: WorkoutBlock) = Workout("w", "W", blocks.toList())

internal fun single(exerciseId: String) = WorkoutBlock.ExerciseBlock(exerciseId)

internal fun circuitOf(
    vararg members: String,
    rounds: Long = 2,
    between: Double? = null,
    betweenRounds: Double? = null,
) = WorkoutBlock.Circuit(
    rounds = rounds,
    restBetweenExercisesSec = between,
    restBetweenRoundsSec = betweenRounds,
    members = members.map { CircuitMember(it) },
)

/** A finished session from before this one, for "last time" and the PR marker. */
internal fun finishedSession(
    id: String,
    vararg entries: SessionEntry,
) = Session(1, id, "w", null, null, null, "2026-07-20T09:00:00.000Z", "2026-07-20T10:00:00.000Z", entries.toList())
