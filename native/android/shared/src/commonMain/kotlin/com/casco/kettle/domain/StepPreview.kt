package com.casco.kettle.domain

// The runner's Next card and its log button's label, ported from the pure helpers in RN's
// `use-session-runner.ts` and `session-steps.ts`. Those were private to the RN runner, so
// `conformance/steps.yaml` never held them, and `RunnerStepsTest` does.
//
// Descriptors rather than strings, so `:shared` holds no user-facing text: the app formats a target
// the way it formats every other one (`ExerciseSummary.kt`), and translates the rest.

/**
 * The step the Next card previews from [index]: the next one, or the one after it when the next is a
 * rest, so it shows real work rather than "Rest". An exercise screen's next step is usually its own
 * trailing rest, which is why the skip exists. Only one rest is skipped.
 */
fun upcomingStep(
    steps: List<RunnerStep>,
    index: Int,
): RunnerStep? {
    val next = steps.getOrNull(index + 1)
    return if (next is RunnerStep.Rest) steps.getOrNull(index + 2) else next
}

/**
 * Whether a rest follows [index], which the reps button names ("Log set → Rest"). It can't be
 * assumed: back-to-back sets (`rest_sec: 0`) emit no rest step at all.
 */
fun restFollows(
    steps: List<RunnerStep>,
    index: Int,
): Boolean = steps.getOrNull(index + 1) is RunnerStep.Rest

/** A hold's target: [minSec] alone, or a range. A 0 maximum is no maximum, as JS's falsy test has it. */
data class HoldTarget(
    val minSec: Double,
    val maxSec: Double?,
)

/** A reps target: [min] alone, or a range. A 0 maximum is no maximum, as for [HoldTarget]. */
data class RepsTarget(
    val min: Long,
    val max: Long?,
)

/** Null on a max-effort hold, which has no target to preview: the caller says so instead. */
fun holdTarget(step: RunnerStep.Hold): HoldTarget? =
    step.holdTargetSec?.let { min -> HoldTarget(min, step.holdTargetMaxSec?.takeIf { max -> max.isConfigured() }) }

fun repsTarget(step: RunnerStep.Reps): RepsTarget = RepsTarget(step.targetReps, step.targetRepsMax?.takeIf { it != 0L })

/**
 * What the Next card shows. [label] is the user's own exercise name, never translated, and
 * [exerciseId] picks the bundled drawing, keyed by id so it is right in every language.
 */
sealed interface StepPreview {
    val label: String
    val exerciseId: String

    data class Hold(
        override val label: String,
        override val exerciseId: String,
        val setIndex: Long,
        val setTotal: Long,
        val target: HoldTarget?,
    ) : StepPreview

    data class Reps(
        override val label: String,
        override val exerciseId: String,
        val setIndex: Long,
        val setTotal: Long,
        val target: RepsTarget,
    ) : StepPreview

    data class Interval(
        override val label: String,
        override val exerciseId: String,
        val variant: IntervalVariant,
        val progress: IntervalProgress,
    ) : StepPreview
}

/** Which round of several, or, for a single interval, how long it runs. */
sealed interface IntervalProgress {
    data class Round(
        val index: Long,
        val total: Long,
    ) : IntervalProgress

    data class Seconds(
        val seconds: Double,
    ) : IntervalProgress
}

/** The preview of [step], or null for a rest or no step: rest previews nothing. */
fun previewFor(step: RunnerStep?): StepPreview? =
    when (step) {
        null, is RunnerStep.Rest -> {
            null
        }

        is RunnerStep.Hold -> {
            StepPreview.Hold(step.exerciseName, step.exerciseId, step.setIndex, step.setTotal, holdTarget(step))
        }

        is RunnerStep.Reps -> {
            StepPreview.Reps(step.exerciseName, step.exerciseId, step.setIndex, step.setTotal, repsTarget(step))
        }

        is RunnerStep.Interval -> {
            val progress =
                if (step.setTotal > 1) {
                    IntervalProgress.Round(step.setIndex, step.setTotal)
                } else {
                    IntervalProgress.Seconds(step.targetSec)
                }
            StepPreview.Interval(step.exerciseName, step.exerciseId, step.variant, progress)
        }
    }

/** The Next card's preview from [index]: [previewFor] of [upcomingStep]. */
fun upcomingPreview(
    steps: List<RunnerStep>,
    index: Int,
): StepPreview? = previewFor(upcomingStep(steps, index))
