package com.casco.kettle.shell.history

import android.content.res.Resources
import com.casco.kettle.domain.EntryResult
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.jsNumber
import com.casco.kettle.domain.toDisplayWeight
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.runner.weightText

/**
 * What one logged entry put up, in the reader's words,.
 * Numbers print as JS prints them, so a 52.5s hold reads the same on both sides and a whole one has no ".0".
 * A reps entry's load is native's alone: the TS formatter renders the reps only, since no screen reads it
 * after #309. A load is in [units], as every weight the reader sees is.
 */
fun Resources.entryResultText(
    result: EntryResult,
    units: UnitSystem,
): String =
    when (result) {
        is EntryResult.Holds -> {
            result.holdSecs.joinToString(" · ") { seconds(it) }
        }

        is EntryResult.Reps -> {
            repsText(result, units)
        }

        is EntryResult.Rounds -> {
            val rounds = pluralText(R.plurals.format_round, R.string.format_round_zero, result.rounds.toInt())
            // Truthy, as RN's: no extra reps and 0 extra reps both read as the rounds alone.
            val extra = result.extraReps?.takeIf { it != 0L }
            if (extra == null) {
                rounds
            } else {
                getString(R.string.format_roundsPlusReps, reps(extra), rounds)
            }
        }

        is EntryResult.Intervals -> {
            val intervals = pluralText(R.plurals.format_interval, R.string.format_interval_zero, result.intervals)
            result.totalReps?.let { getString(R.string.format_intervalsWithReps, intervals, reps(it)) } ?: intervals
        }

        is EntryResult.Cardio -> {
            listOfNotNull(
                result.durationSec?.let { seconds(it) },
                result.distanceMeters?.let { getString(R.string.format_metres, jsNumber(it)) },
            ).joinToString(" · ")
        }

        is EntryResult.Rest -> {
            seconds(result.restTakenSec)
        }
    }

/**
 * Unloaded sets as RN's "12 · 12 · 10 reps"; one load across every set as the board's "48.5 lb · 12, 12, 10".
 * Loads that differ, or a set without one among loaded sets, go set by set, "8 × 60 kg · 6 reps", since a
 * single load in front would claim a weight some sets didn't carry.
 *
 * A load of 0 is no load, as records count it (`Records.kt`): the session editor saves a typed "0" as one.
 * Loads are compared as the reader sees them, so 22.7 kg from the runner and "50" lb typed in the editor
 * both read "50 lb" and share one load in front rather than going set by set.
 */
private fun Resources.repsText(
    result: EntryResult.Reps,
    units: UnitSystem,
): String {
    val loads = result.weightsKg.map { kg -> kg?.takeIf { it > 0 } }
    val shown = loads.map { kg -> kg?.let { toDisplayWeight(it, units) } }.distinct()
    val load = loads.firstOrNull().takeIf { shown.singleOrNull() != null }
    return when {
        shown.all { it == null } -> {
            getString(R.string.format_repsList, result.reps.joinToString(" · "))
        }

        load != null -> {
            val list = result.reps.joinToString(getString(R.string.format_listSeparator))
            getString(R.string.format_loadedReps, list, weightText(load, units))
        }

        else -> {
            result.reps.zip(loads).joinToString(" · ") { (count, kg) ->
                kg?.let { getString(R.string.format_loadedSet, "$count", weightText(it, units)) } ?: reps(count)
            }
        }
    }
}

private fun Resources.seconds(seconds: Double) = getString(R.string.format_seconds, jsNumber(seconds))

private fun Resources.reps(count: Long) = pluralText(R.plurals.format_rep, R.string.format_rep_zero, count.toInt())
