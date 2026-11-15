package com.casco.kettle.shell.runner

import android.content.res.Resources
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.TimelineStop
import com.casco.kettle.domain.WorkStep
import com.casco.kettle.domain.holdTarget
import com.casco.kettle.domain.lastRoundReps
import com.casco.kettle.domain.repsTarget
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.pluralText

// Coming up's targets (#479): "Target" only on the stop on screen, the number alone on the stops ahead.
// Their own lines rather than Home's preview's, which keeps "target 8–12".

/** A stop ahead: "3 sets · 8–12 reps", the target alone for one-shot work. */
internal fun Resources.nextDetail(stop: TimelineStop.Exercise): String {
    val target = aheadTarget(stop.step)
    val unit = stop.unit ?: return target
    val (plural, zero) = COUNT_KEYS.getValue(unit).first
    return getString(R.string.session_upcoming_detail, pluralText(plural, zero, stop.count), target)
}

/**
 * The stop on screen: "Set 2 of 4 · Target 8–12", or an interval's own line. Only the stop on screen says
 * "Target" (#479); the stops ahead give the number alone, which reads as what's planned.
 */
internal fun Resources.nowDetail(step: WorkStep): String =
    when (step) {
        is RunnerStep.Interval -> {
            intervalLine(step, rounds = true)
        }

        else -> {
            getString(
                R.string.session_comingUp_nowLine,
                getString(R.string.session_setOf, "${step.setIndex}", "${step.setTotal}"),
                nowTarget(step),
            )
        }
    }

/**
 * A circuit member on screen: "Target 12–20", and from round 2 "· round 1: 16", the step screen's own
 * reference and held to the same check (`lastRoundReps`), so the two never disagree.
 */
internal fun Resources.loopNowDetail(
    step: WorkStep,
    entry: SessionEntry?,
): String {
    val target = if (step is RunnerStep.Interval) intervalLine(step, rounds = false) else nowTarget(step)
    val reps = (step as? RunnerStep.Reps)?.let { lastRoundReps(it, entry) } ?: return target
    val round = (step.circuit?.round ?: 1) - 1
    return getString(
        R.string.session_comingUp_nowLine,
        target,
        getString(R.string.session_comingUp_lastRound, "$reps", "$round"),
    )
}

/** "Target 8–12", "Target 20–45 s", or "Max hold" for an open hold, as it reads ahead. */
private fun Resources.nowTarget(step: WorkStep): String =
    when (step) {
        is RunnerStep.Reps -> {
            getString(R.string.session_comingUp_nowTarget, repsTargetText(repsTarget(step)))
        }

        is RunnerStep.Hold -> {
            holdTarget(step)?.let { getString(R.string.session_comingUp_nowTarget, holdTargetText(it)) }
                ?: getString(R.string.session_comingUp_nowHoldOpen)
        }

        is RunnerStep.Interval -> {
            intervalLine(step, rounds = false)
        }
    }

/** A stop ahead's target, no "target" before it: "8–12 reps", "12 reps", "hold 20–45 s", "max hold". */
internal fun Resources.aheadTarget(step: WorkStep): String =
    when (step) {
        is RunnerStep.Reps -> {
            val target = repsTarget(step)
            if (target.max == null) {
                pluralText(R.plurals.session_comingUp_reps, R.string.session_comingUp_reps_zero, target.min.toInt())
            } else {
                getString(R.string.session_comingUp_repsRange, repsTargetText(target))
            }
        }

        is RunnerStep.Hold -> {
            holdTarget(step)?.let { getString(R.string.session_comingUp_hold, holdTargetText(it)) }
                ?: getString(R.string.session_comingUp_holdOpen)
        }

        is RunnerStep.Interval -> {
            intervalLine(step, rounds = false)
        }
    }
