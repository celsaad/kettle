package com.casco.kettle.shell.history

import android.content.res.Resources
import com.casco.kettle.domain.EntryResult
import com.casco.kettle.domain.jsNumber
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.pluralText

/**
 * What one logged entry put up, in the reader's words: `formatEntryResult` in `src/domain/format.ts`,
 * which RN's session editor still reads. Numbers print as JS prints them, so a 52.5s hold reads the
 * same on both sides and a whole one has no ".0".
 */
fun Resources.entryResultText(result: EntryResult): String =
    when (result) {
        is EntryResult.Holds -> {
            result.holdSecs.joinToString(" · ") { seconds(it) }
        }

        is EntryResult.Reps -> {
            getString(R.string.format_repsList, result.reps.joinToString(" · "))
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

private fun Resources.seconds(seconds: Double) = getString(R.string.format_seconds, jsNumber(seconds))

private fun Resources.reps(count: Long) = pluralText(R.plurals.format_rep, R.string.format_rep_zero, count.toInt())
