package com.casco.kettle.shell.runner

import android.content.res.Resources
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.TimelineStop
import com.casco.kettle.domain.UpcomingItem
import com.casco.kettle.domain.WorkStep
import com.casco.kettle.domain.WorkUnit
import com.casco.kettle.domain.jsNumber
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.pluralText

// The words on Coming up's stops (#415). Logged values come from the session file as written; targets
// and counts from the steps, through the same lines Home's preview uses.

/** A stop ahead: "3 sets · target 8–12", the target alone for one-shot work. */
internal fun Resources.nextDetail(stop: TimelineStop.Exercise): String =
    upcomingDetail(UpcomingItem.Exercise(stop.memberKey, stop.step, stop.count, stop.unit, started = false))

/** The stop on screen: "set 2 of 4 · target 8–12", or an interval's own line. */
internal fun Resources.nowDetail(step: WorkStep): String =
    when (step) {
        is RunnerStep.Interval -> {
            intervalLine(step, rounds = true)
        }

        else -> {
            getString(
                R.string.session_upcoming_detail,
                getString(R.string.session_setOf, "${step.setIndex}", "${step.setTotal}"),
                upcomingTarget(step),
            )
        }
    }

/**
 * A stop done, as it was logged: "3 sets · 10, 9, 8", "2 sets · 30, 28 s", "8 rounds", "7 rounds".
 * Without an entry (work that logged nothing) it counts the steps that ran.
 */
internal fun Resources.doneDetail(
    stop: TimelineStop.Exercise,
    entry: SessionEntry?,
): String =
    when (entry) {
        is SessionEntry.Reps -> {
            withCount(stop, entry.sets.size, entry.sets.joinToString(", ") { "${it.reps}" })
        }

        is SessionEntry.TimedHold -> {
            withCount(stop, entry.sets.size, holdValues(entry.sets.map { it.holdSec }))
        }

        is SessionEntry.Amrap -> {
            pluralText(
                R.plurals.session_amrap_rounds,
                R.string.session_amrap_rounds_zero,
                entry.roundsCompleted.toInt(),
            )
        }

        is SessionEntry.Cardio -> {
            entry.distanceMeters?.takeIf { it > 0 }?.let { getString(R.string.summary_cardioDistance, jsNumber(it)) }
                ?: entry.durationSec?.let(::formatClock)
                ?: getString(R.string.session_end_done)
        }

        else -> {
            stepCount(stop.unit, stop.count) ?: getString(R.string.session_end_done)
        }
    }

/** A circuit member done this round, its round's own value: "12 reps", "30 s". */
internal fun Resources.roundValue(
    entry: SessionEntry?,
    round: Long,
): String? {
    val index = (round - 1).toInt()
    return when (entry) {
        is SessionEntry.Reps -> {
            entry.sets.getOrNull(index)?.let {
                pluralText(R.plurals.session_comingUp_reps, R.string.session_comingUp_reps_zero, it.reps.toInt())
            }
        }

        is SessionEntry.TimedHold -> {
            entry.sets.getOrNull(index)?.let { holdValues(listOf(it.holdSec)) }
        }

        else -> {
            null
        }
    }
}

/** "No rest between moves · 1:00 after each round", or nothing to say for a circuit with no rest at all. */
internal fun Resources.loopRests(stop: TimelineStop.Loop): String? {
    val moves =
        stop.betweenMovesSec?.let { getString(R.string.session_comingUp_restBetween, formatClock(it)) }
            ?: getString(R.string.session_comingUp_noRestBetween)
    val rounds = stop.afterRoundSec?.let { getString(R.string.session_comingUp_afterRound, formatClock(it)) }
    return if (stop.betweenMovesSec == null &&
        rounds == null
    ) {
        moves
    } else {
        listOfNotNull(moves, rounds).joinToString(" · ")
    }
}

/** "Then round 3", "Then rounds 3–5", or null on the last. */
internal fun Resources.thenRounds(stop: TimelineStop.Loop): String? {
    val next = stop.round + 1
    return when {
        next > stop.lastRound -> null
        next == stop.lastRound -> getString(R.string.session_comingUp_thenRound, "$next")
        else -> getString(R.string.session_comingUp_thenRounds, "$next", "${stop.lastRound}")
    }
}

private fun Resources.withCount(
    stop: TimelineStop.Exercise,
    count: Int,
    values: String,
): String = getString(R.string.session_upcoming_detail, stepCount(stop.unit, count) ?: "$count", values)

private fun Resources.stepCount(
    unit: WorkUnit?,
    count: Int,
): String? {
    val (plural, zero) =
        when (unit ?: return null) {
            WorkUnit.SET -> R.plurals.session_upcoming_sets to R.string.session_upcoming_sets_zero
            WorkUnit.ROUND -> R.plurals.session_upcoming_rounds to R.string.session_upcoming_rounds_zero
            WorkUnit.MINUTE -> R.plurals.session_upcoming_minutes to R.string.session_upcoming_minutes_zero
            WorkUnit.INTERVAL -> R.plurals.session_upcoming_intervals to R.string.session_upcoming_intervals_zero
        }
    return pluralText(plural, zero, count)
}

private fun Resources.holdValues(seconds: List<Double>): String =
    getString(R.string.session_comingUp_holdValues, seconds.joinToString(", ") { jsNumber(it) })
