package com.casco.kettle.shell.runner

import android.content.res.Resources
import com.casco.kettle.domain.HoldTarget
import com.casco.kettle.domain.RepsTarget
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.UpcomingItem
import com.casco.kettle.domain.WorkStep
import com.casco.kettle.domain.WorkUnit
import com.casco.kettle.domain.holdTarget
import com.casco.kettle.domain.jsNumber
import com.casco.kettle.domain.repsTarget
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.pluralText

// The step's own words: its clock and targets, what a screen reader hears as it comes up, and the
// Coming up sheet's lines. Placeholders go in the alphabetical order of their names, as in RunnerText.

/** `m:ss`, as RN's `formatClock`. */
fun formatClock(totalSeconds: Double): String {
    val whole = totalSeconds.toLong()
    return "${whole / SECONDS_PER_MINUTE}:${(whole % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
}

/** "15s" or "15–25s", as RN's `formatHoldTarget`. */
fun holdTargetText(target: HoldTarget): String =
    target.maxSec?.let { "${jsNumber(target.minSec)}–${jsNumber(it)}s" } ?: "${jsNumber(target.minSec)}s"

fun repsTargetText(target: RepsTarget): String = target.max?.let { "${target.min}–$it" } ?: "${target.min}"

/**
 * What a screen reader hears as a step comes up (§3.10): one sentence per step. A hold announces its
 * end, which is when it will actually stop, and a rest its configured seconds, not the +30s-moved end.
 */
fun Resources.announcement(step: RunnerStep): String =
    when (step) {
        is RunnerStep.Hold -> {
            step.holdEndSec?.let {
                getString(
                    R.string.session_announce_hold,
                    "${step.setIndex}",
                    step.exerciseName,
                    jsNumber(it),
                    "${step.setTotal}",
                )
            }
                ?: getString(
                    R.string.session_announce_holdOpen,
                    "${step.setIndex}",
                    step.exerciseName,
                    "${step.setTotal}",
                )
        }

        is RunnerStep.Reps -> {
            getString(
                R.string.session_announce_reps,
                "${step.setIndex}",
                step.exerciseName,
                "${step.targetReps}",
                "${step.setTotal}",
            )
        }

        is RunnerStep.Interval -> {
            getString(
                R.string.session_announce_interval,
                "${step.setIndex}",
                step.exerciseName,
                jsNumber(step.targetSec),
                "${step.setTotal}",
            )
        }

        is RunnerStep.Rest -> {
            getString(R.string.session_announce_rest, jsNumber(step.seconds))
        }
    }

/** A work step's target, as the Coming up sheet words it. */
fun Resources.upcomingTarget(step: WorkStep): String =
    when (step) {
        is RunnerStep.Reps -> {
            getString(R.string.session_upcoming_targetReps, repsTargetText(repsTarget(step)))
        }

        is RunnerStep.Hold -> {
            holdTarget(step)?.let { getString(R.string.session_upcoming_targetHold, holdTargetText(it)) }
                ?: getString(R.string.session_upcoming_targetHoldOpen)
        }

        is RunnerStep.Interval -> {
            val progress =
                if (step.countUp) {
                    getString(R.string.session_countingUp)
                } else {
                    getString(R.string.preview_seconds, jsNumber(step.targetSec))
                }
            getString(R.string.preview_interval, progress, step.variant.json)
        }
    }

/** "2 more sets · target 8", or the target alone for one-shot work. */
fun Resources.upcomingDetail(item: UpcomingItem.Exercise): String {
    val target = upcomingTarget(item.step)
    val unit = item.unit ?: return target
    val (fresh, more) = COUNT_KEYS.getValue(unit)
    val (plural, zero) = if (item.started) more else fresh
    return getString(R.string.session_upcoming_detail, pluralText(plural, zero, item.left), target)
}

/** The NOW row's caption: set n of N, the interval's progress, or the rest against its total. */
fun Resources.nowCaption(
    step: RunnerStep,
    restTotalSec: Double,
): String =
    when (step) {
        is RunnerStep.Rest -> {
            getString(R.string.session_rest_remaining, formatClock(restTotalSec))
        }

        is RunnerStep.Interval -> {
            val progress =
                when {
                    step.countUp -> getString(R.string.session_countingUp)
                    step.setTotal > 1 -> getString(R.string.preview_round, "${step.setIndex}", "${step.setTotal}")
                    else -> getString(R.string.preview_seconds, jsNumber(step.targetSec))
                }
            getString(R.string.preview_interval, progress, step.variant.json)
        }

        is WorkStep -> {
            getString(R.string.session_setOf, "${step.setIndex}", "${step.setTotal}")
        }
    }

private val COUNT_KEYS: Map<WorkUnit, Pair<Pair<Int, Int>, Pair<Int, Int>>> =
    mapOf(
        WorkUnit.SET to
            (
                (R.plurals.session_upcoming_sets to R.string.session_upcoming_sets_zero) to
                    (R.plurals.session_upcoming_moreSets to R.string.session_upcoming_moreSets_zero)
            ),
        WorkUnit.ROUND to
            (
                (R.plurals.session_upcoming_rounds to R.string.session_upcoming_rounds_zero) to
                    (R.plurals.session_upcoming_moreRounds to R.string.session_upcoming_moreRounds_zero)
            ),
        WorkUnit.MINUTE to
            (
                (R.plurals.session_upcoming_minutes to R.string.session_upcoming_minutes_zero) to
                    (R.plurals.session_upcoming_moreMinutes to R.string.session_upcoming_moreMinutes_zero)
            ),
        WorkUnit.INTERVAL to
            (
                (R.plurals.session_upcoming_intervals to R.string.session_upcoming_intervals_zero) to
                    (R.plurals.session_upcoming_moreIntervals to R.string.session_upcoming_moreIntervals_zero)
            ),
    )

private const val SECONDS_PER_MINUTE = 60L
