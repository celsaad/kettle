package com.casco.kettle.shell.runner

import android.content.res.Resources
import com.casco.kettle.domain.IntervalProgress
import com.casco.kettle.domain.LoggedUnit
import com.casco.kettle.domain.PreviousSet
import com.casco.kettle.domain.SessionRecord
import com.casco.kettle.domain.StepPreview
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.jsNumber
import com.casco.kettle.domain.toDisplayWeight
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.i18n.weightUnit

// The runner's words, as RN's `domain/format.ts` and the session components assemble them. In one
// place because `:strings` numbers a key's placeholders in the alphabetical order of their names, not
// in the order they read: `preview.reps` is (index, target, total). Every call here is in that
// order, and the Compose tests in `pt` catch one that isn't. User data (names, notes) goes in verbatim.

/** A stored weight in the user's own unit with its unit, "60 kg". */
fun Resources.weightText(
    kg: Double,
    units: UnitSystem,
): String = "${jsNumber(toDisplayWeight(kg, units))} ${getString(weightUnit(units))}"

/** The workout's name verbatim, or the ad-hoc stand-in. */
fun Resources.sessionName(workoutName: String?): String = workoutName ?: getString(R.string.format_adHocSession)

internal fun Resources.reps(count: Long): String =
    pluralText(R.plurals.format_rep, R.string.format_rep_zero, count.toInt())

private fun Resources.seconds(value: Double): String = getString(R.string.format_seconds, jsNumber(value))

/** What Undo would take back, after "Logged": "set 1 · 10 × 48.5 lb", "round 3", or the entry's name. */
fun Resources.loggedText(
    logged: LoggedUnit,
    units: UnitSystem,
): String =
    when (logged) {
        is LoggedUnit.Set -> {
            val index = "${logged.number}"
            when (val set = logged.set) {
                is PreviousSet.Hold -> {
                    getString(R.string.session_logged_setHold, index, seconds(set.holdSec))
                }

                is PreviousSet.Reps -> {
                    val kg = set.weightKg?.takeIf { it > 0 }
                    if (kg == null) {
                        getString(R.string.session_logged_setBodyweight, index, reps(set.reps))
                    } else {
                        getString(R.string.session_logged_setLoaded, index, "${set.reps}", weightText(kg, units))
                    }
                }
            }
        }

        is LoggedUnit.Round -> {
            getString(R.string.session_logged_round, "${logged.number}")
        }

        is LoggedUnit.Minute -> {
            getString(R.string.session_logged_minute, "${logged.number}")
        }

        is LoggedUnit.Entry -> {
            logged.exerciseName ?: getString(R.string.session_logged_rest)
        }
    }

/** The Next card's second line, as RN's `previewFor`. */
fun Resources.previewDetail(preview: StepPreview): String =
    when (preview) {
        is StepPreview.Hold -> {
            preview.target?.let {
                getString(R.string.preview_hold, "${preview.setIndex}", holdTargetText(it), "${preview.setTotal}")
            } ?: getString(R.string.preview_holdOpen, "${preview.setIndex}", "${preview.setTotal}")
        }

        is StepPreview.Reps -> {
            getString(
                R.string.preview_reps,
                "${preview.setIndex}",
                repsTargetText(preview.target),
                "${preview.setTotal}",
            )
        }

        is StepPreview.Interval -> {
            val progress =
                when (val at = preview.progress) {
                    is IntervalProgress.Round -> getString(R.string.preview_round, "${at.index}", "${at.total}")
                    is IntervalProgress.Seconds -> getString(R.string.preview_seconds, jsNumber(at.seconds))
                }
            // The variant goes in as its YAML id, as RN's does.
            getString(R.string.preview_interval, progress, preview.variant.json)
        }
    }

/** "Last time · 60 kg × 8 reps", or the bodyweight and hold forms. */
fun Resources.previousSetText(
    previous: PreviousSet,
    units: UnitSystem,
): String =
    when (previous) {
        is PreviousSet.Hold -> {
            getString(R.string.format_previous_hold, seconds(previous.holdSec))
        }

        is PreviousSet.Reps -> {
            val kg = previous.weightKg?.takeIf { it > 0 }
            if (kg == null) {
                getString(R.string.format_previous_bodyweight, reps(previous.reps))
            } else {
                getString(R.string.format_previous_loaded, reps(previous.reps), weightText(kg, units))
            }
        }
    }

fun Resources.recordText(
    record: SessionRecord,
    units: UnitSystem,
): String =
    when (record) {
        is SessionRecord.HeaviestSet -> {
            getString(R.string.format_record_heaviestSet, reps(record.reps), weightText(record.weightKg, units))
        }

        is SessionRecord.MostReps -> {
            getString(R.string.format_record_mostReps, reps(record.reps.toLong()))
        }

        is SessionRecord.LongestHold -> {
            getString(R.string.format_record_longestHold, seconds(record.holdSec))
        }

        is SessionRecord.MostRounds -> {
            getString(
                R.string.format_record_mostRounds,
                pluralText(R.plurals.format_round, R.string.format_round_zero, record.rounds.toInt()),
            )
        }
    }

fun Resources.oneRepMaxText(
    kg: Double,
    units: UnitSystem,
): String = getString(R.string.format_oneRepMax, weightText(kg, units))
