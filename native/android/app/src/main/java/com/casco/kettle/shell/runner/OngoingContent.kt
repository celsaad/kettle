package com.casco.kettle.shell.runner

import android.content.res.Resources
import com.casco.kettle.domain.IntervalVariant
import com.casco.kettle.domain.RunnerSnapshot
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.WorkStep
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.pluralText

// What the runner's ongoing notification says, and the session controls on it (docs/watch-remote-plan.md):
// a function of the runner's state, which RunnerNotifications.kt builds into the notification.

/** A chronometer in the shade: counting down to [baseWallMs], or up from it. */
data class Chronometer(
    val baseWallMs: Long,
    val countDown: Boolean,
)

/**
 * What the ongoing notification says, and the session controls on it (docs/watch-remote-plan.md). The
 * step leads: [title] is the exercise's name verbatim, or "Rest" or "Get ready"; [text] is one detail line
 * or none; [subText] is the workout's name, kept off the title, where it cost a round watch face two lines.
 * [shortText] is the Android 16 status-bar chip's text where no [chronometer] stands in for it. [actions]
 * are aimed at [aim], and there are none without one.
 */
data class OngoingContent(
    val title: String,
    val text: String?,
    val subText: String?,
    val chronometer: Chronometer?,
    val shortText: String? = null,
    val actions: List<SessionAction> = emptyList(),
    val aim: SessionAim? = null,
)

/**
 * The ongoing notification for [view]: a chronometer for a timed step (down for a countdown, up for a
 * hold or count-up cardio, as the screen shows them), none for reps or while paused.
 */
fun ongoingContent(
    resources: Resources,
    view: RunnerView.Active,
): OngoingContent {
    val workout = resources.sessionName(view.plan.workout?.name)
    return when (view) {
        is RunnerView.CountingIn -> {
            val first = view.plan.steps.firstNotNullOfOrNull { (it as? WorkStep)?.exerciseName }
            val getReady = resources.getString(R.string.session_notification_getReady)
            OngoingContent(getReady, first, workout, Chronometer(view.countIn.endsAt, true))
        }

        is RunnerView.Running -> {
            resources.runningContent(view, workout)
        }
    }
}

private fun Resources.runningContent(
    view: RunnerView.Running,
    workout: String,
): OngoingContent {
    val snapshot = view.snapshot
    val step = snapshot.step ?: return OngoingContent(getString(R.string.session_adhoc_title), null, workout, null)
    val title = (step as? WorkStep)?.exerciseName ?: getString(R.string.session_notification_rest)
    val aim = SessionAim(snapshot.sessionId, snapshot.visit)
    return if (snapshot.clock.paused) {
        pausedContent(title, workout, aim)
    } else {
        stepContent(step, title, workout, aim, view)
    }
}

/** Paused: no clock, the chip says so, and Resume is the only button. */
private fun Resources.pausedContent(
    title: String,
    workout: String,
    aim: SessionAim,
): OngoingContent {
    val paused = getString(R.string.session_paused)
    val resume = SessionAction(getString(R.string.session_resume), SessionControl.Resume)
    return OngoingContent(title, paused, workout, null, paused, listOf(resume), aim)
}

private fun Resources.stepContent(
    step: RunnerStep,
    title: String,
    workout: String,
    aim: SessionAim,
    view: RunnerView.Running,
): OngoingContent {
    val snapshot = view.snapshot
    val clock = snapshot.clock
    val started = clock.phaseStartedAt + clock.pausedMs
    val chronometer =
        when {
            step is RunnerStep.Reps -> {
                null
            }

            step is RunnerStep.Rest || (step is RunnerStep.Interval && !step.countUp) -> {
                Chronometer(started + (clock.stepEndSec * MS_PER_SECOND).toLong(), true)
            }

            else -> {
                Chronometer(started, false)
            }
        }
    // A reps step has no clock to put in the chip, so it says the set.
    val chip =
        (step as? RunnerStep.Reps)?.let {
            getString(R.string.session_notification_chipSet, "${it.setIndex}", "${it.setTotal}")
        }
    val back = backAction(snapshot.stepIndex, view.logged != null)
    return OngoingContent(
        title,
        detail(step, snapshot, view.context.units),
        workout,
        chronometer,
        chip,
        actions(step, snapshot.inputs.roundsCompleted, back),
        aim,
    )
}

/**
 * The step's one detail line. Reps say what Log set will log, the live inputs and not a recomputed seed,
 * so a phone edit reaches the card; an AMRAP its rounds so far, which "Round N done" counts from the
 * wrist. Positions are the screen's own header line, which a circuit's round already replaces.
 */
private fun Resources.detail(
    step: RunnerStep,
    snapshot: RunnerSnapshot,
    units: UnitSystem,
): String? {
    val inputs = snapshot.inputs
    return when (step) {
        is RunnerStep.Rest -> {
            snapshot.steps
                .drop(snapshot.stepIndex + 1)
                .firstNotNullOfOrNull { (it as? WorkStep)?.exerciseName }
                ?.let { getString(R.string.session_header_next, it) }
        }

        is RunnerStep.Reps -> {
            listOfNotNull(
                stepLabel(step, inputs),
                inputs.weightKg.takeIf { it > 0 }?.let { weightText(it, units) },
                reps(inputs.reps),
            ).joinToString(" · ")
        }

        is RunnerStep.Hold -> {
            stepLabel(step, inputs)
        }

        is RunnerStep.Interval -> {
            when (step.variant) {
                IntervalVariant.HIIT -> {
                    stepLabel(step, inputs)
                }

                IntervalVariant.EMOM -> {
                    listOfNotNull(stepLabel(step, inputs), inputs.reps.takeIf { it > 0 }?.let { reps(it) })
                        .joinToString(" · ")
                }

                IntervalVariant.AMRAP -> {
                    val rounds = inputs.roundsCompleted.toInt()
                    pluralText(R.plurals.session_amrap_rounds, R.string.session_amrap_rounds_zero, rounds)
                }

                IntervalVariant.CARDIO -> {
                    null
                }
            }
        }
    }
}

/**
 * Back, or Undo where it takes back a set just logged, as the rest screen labels it; none on the first
 * step with nothing logged, where the engine has nowhere to go back to and a button can't be disabled.
 */
private fun Resources.backAction(
    stepIndex: Int,
    logged: Boolean,
): SessionAction? =
    when {
        logged -> SessionAction(getString(R.string.session_undo), SessionControl.Prev)
        stepIndex == 0 -> null
        else -> SessionAction(getString(R.string.session_prev), SessionControl.Prev)
    }

/**
 * The buttons while running, in the runner's own words. An EMOM's minute ends on its clock and logs what
 * the card shows, so it has no advance; an AMRAP counts rounds rather than ending, which its cap does.
 * +30s is a rest's alone, as on the screen. Finish and every edit stay on the phone.
 */
private fun Resources.actions(
    step: RunnerStep,
    roundsCompleted: Long,
    back: SessionAction?,
): List<SessionAction> {
    val done = SessionAction(getString(R.string.session_done), SessionControl.Advance)
    return when (step) {
        is RunnerStep.Reps -> {
            listOfNotNull(SessionAction(getString(R.string.session_reps_logSet), SessionControl.Advance), back)
        }

        is RunnerStep.Rest -> {
            listOfNotNull(
                SessionAction(getString(R.string.session_rest_skip), SessionControl.Advance),
                back,
                SessionAction(getString(R.string.session_rest_add30), SessionControl.AddRest),
            )
        }

        is RunnerStep.Hold -> {
            listOfNotNull(done, back)
        }

        is RunnerStep.Interval -> {
            when (step.variant) {
                IntervalVariant.EMOM -> {
                    listOfNotNull(back)
                }

                IntervalVariant.AMRAP -> {
                    val next = roundsCompleted + 1
                    val label = getString(R.string.session_amrap_roundDone, "$next")
                    listOfNotNull(SessionAction(label, SessionControl.Rounds(next)), back)
                }

                IntervalVariant.HIIT, IntervalVariant.CARDIO -> {
                    listOfNotNull(done, back)
                }
            }
        }
    }
}
