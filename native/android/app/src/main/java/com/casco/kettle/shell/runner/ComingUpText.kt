package com.casco.kettle.shell.runner

import android.content.res.Resources
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.TimelineStop
import com.casco.kettle.domain.WorkUnit
import com.casco.kettle.domain.jsNumber
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.formatClock
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.i18n.timeText

// The words on Coming up's stops (#415). Logged values come from the session file as written; counts
// from the steps, and targets from `ComingUpTargets.kt`'s own lines (#479).

/**
 * A stop done, as it was logged: "3 sets · 10, 9, 8", "2 sets · 30, 28 s", "8 rounds", "5 minutes".
 * Null without an entry (work that logged nothing), which the tick alone then says: its planned count
 * would read as work done.
 */
internal fun Resources.doneDetail(
    stop: TimelineStop.Exercise,
    entry: SessionEntry?,
): String? =
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

        is SessionEntry.Hiit -> {
            stepCount(WorkUnit.ROUND, entry.roundsCompleted.toInt())
        }

        is SessionEntry.Emom -> {
            stepCount(stop.unit, entry.minutes.size)
        }

        is SessionEntry.Rest, null -> {
            null
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

/**
 * "No rest between moves · 1:00 after each round": the rest between moves always said, none included,
 * and the rest after each round only when there is one.
 */
internal fun Resources.loopRests(stop: TimelineStop.Loop): String {
    val moves =
        stop.betweenMovesSec?.let { getString(R.string.session_comingUp_restBetween, formatClock(it)) }
            ?: getString(R.string.session_comingUp_noRestBetween)
    val rounds =
        stop.afterRoundSec?.let { getString(R.string.session_comingUp_afterRound, formatClock(it)) }
            ?: return moves
    return getString(R.string.session_comingUp_rests, moves, rounds)
}

/** "Then round 3: all 3 again", "Then rounds 3–5: all 3 again", or null on the last. */
internal fun Resources.thenRounds(stop: TimelineStop.Loop): String? {
    val next = stop.round + 1
    val members = stop.members.size
    return when {
        next > stop.lastRound -> {
            null
        }

        next == stop.lastRound -> {
            pluralText(R.plurals.session_comingUp_thenRound, R.string.session_comingUp_thenRound_zero, members, "$next")
        }

        else -> {
            pluralText(
                R.plurals.session_comingUp_thenRounds,
                R.string.session_comingUp_thenRounds_zero,
                members,
                "$next",
                "${stop.lastRound}",
            )
        }
    }
}

/** "After round 3" under End, when the circuit's last round is what ends the workout (#479). */
internal fun Resources.afterLastRound(last: TimelineStop?): String? {
    val round =
        when (last) {
            is TimelineStop.Loop -> last.lastRound
            is TimelineStop.Circuit -> last.rounds
            else -> return null
        }
    return getString(R.string.session_comingUp_afterLastRound, "$round")
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
    val (plural, zero) = COUNT_KEYS.getValue(unit ?: return null).first
    return pluralText(plural, zero, count)
}

private fun holdValues(seconds: List<Double>): String = seconds.joinToString(", ") { timeText(it) }
