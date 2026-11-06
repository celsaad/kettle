package com.casco.kettle.shell.runner

import com.casco.kettle.domain.IntervalVariant
import com.casco.kettle.domain.RunnerSnapshot
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.WorkStep
import com.casco.kettle.domain.upcomingStep

/** The step-end alert's title, by the kind of step that ended (#296). */
enum class StepEndTitle { REST, HOLD, INTERVAL, TIME_UP }

/** What the step-end alert says: the title, and the next exercise's name, or null for "Workout complete". */
data class StepEndAlert(
    val title: StepEndTitle,
    val nextName: String?,
)

/**
 * The alert for [ended], a step that ended by itself, read against [after], the snapshot it handed
 * over to. The next exercise skips a rest in between, as the Next card does, since "Rest" says nothing
 * about what to get ready for. RN said "Rest complete" for every step that wasn't a hold.
 */
fun stepEndAlert(
    ended: RunnerStep,
    next: RunnerStep?,
    after: RunnerSnapshot,
): StepEndAlert {
    val title =
        when (ended) {
            is RunnerStep.Rest -> {
                StepEndTitle.REST
            }

            is RunnerStep.Hold -> {
                StepEndTitle.HOLD
            }

            is RunnerStep.Interval -> {
                when (ended.variant) {
                    IntervalVariant.HIIT, IntervalVariant.EMOM -> StepEndTitle.INTERVAL
                    IntervalVariant.AMRAP, IntervalVariant.CARDIO -> StepEndTitle.TIME_UP
                }
            }

            // Never ends by itself; named for completeness.
            is RunnerStep.Reps -> {
                StepEndTitle.INTERVAL
            }
        }
    val upcoming = next?.let { it as? WorkStep ?: upcomingStep(after.steps, after.stepIndex - 1) as? WorkStep }
    return StepEndAlert(title, upcoming?.exerciseName)
}

/**
 * The step-end alert's gate (#296): posted only while the runner isn't on screen, where the user is
 * already looking, and cleared as soon as it is again. [post]'s `silent` is true when the service
 * plays the cue itself, so the alert doesn't sound a second time.
 */
class StepAlerts(
    private val post: (alert: StepEndAlert, silent: Boolean) -> Unit,
    private val cancel: () -> Unit,
) {
    /** What the runner's screen reports (#306). Off until it says it's showing. */
    @Volatile var runnerVisible: Boolean = false
        set(value) {
            field = value
            if (value) cancel()
        }

    fun stepEnded(
        alert: StepEndAlert,
        soundsOn: Boolean,
    ) {
        if (!runnerVisible) post(alert, soundsOn)
    }

    fun clear() = cancel()
}
