package com.casco.kettle.shell.runner

import android.content.res.Resources
import com.casco.kettle.domain.CircuitPosition
import com.casco.kettle.domain.IntervalProgress
import com.casco.kettle.domain.LoggedUnit
import com.casco.kettle.domain.PreviousSet
import com.casco.kettle.domain.StepPreview
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.jsNumber
import com.casco.kettle.domain.toDisplayWeight
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.i18n.timeText
import com.casco.kettle.shell.i18n.weightUnit

// The runner's words, as RN's formatter and the session components assembled them. In one
// place because `:strings` numbers a key's placeholders in the alphabetical order of their names, not
// in the order they read: `preview.interval` is (progress, variant). Every call here is in that
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

/**
 * What Undo would take back, after "Logged": "set 1 · 10 × 48.5 lb", "round 3", or the entry's name. A
 * circuit's member logs a round, which "set 2" read as a second set of the same move (#473), and says
 * whose round, "Pull-ups · round 2 · 6 × 20 kg", since the rest it shows on is between two moves.
 */
fun Resources.loggedText(
    logged: LoggedUnit,
    units: UnitSystem,
): String =
    when (logged) {
        is LoggedUnit.Set -> {
            setText(logged.number, logged.circuitExercise, logged.set, units)
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

/**
 * One logged set after its number, "set 2 · 8 × 60 kg", or a circuit member's, which is its round, after
 * its [circuitExercise] name verbatim: "Pull-ups · round 2 · 6 × 20 kg". The name sorts between the
 * index and the rest of the placeholders, so it slots in second whichever key is said.
 */
private fun Resources.setText(
    number: Int,
    circuitExercise: String?,
    set: PreviousSet,
    units: UnitSystem,
): String {
    val lead = listOfNotNull("$number", circuitExercise)

    // getString only takes varargs, and the copy the spread makes is of two to four strings, once a rest.
    @Suppress("SpreadOperator")
    fun say(
        setKey: Int,
        roundKey: Int,
        vararg rest: String,
    ) = getString(if (circuitExercise == null) setKey else roundKey, *(lead + rest).toTypedArray())

    return when (set) {
        is PreviousSet.Hold -> {
            say(R.string.session_logged_setHold, R.string.session_logged_roundHold, timeText(set.holdSec))
        }

        is PreviousSet.Reps -> {
            val kg = set.weightKg?.takeIf { it > 0 }
            if (kg == null) {
                say(R.string.session_logged_setBodyweight, R.string.session_logged_roundBodyweight, reps(set.reps))
            } else {
                say(
                    R.string.session_logged_setLoaded,
                    R.string.session_logged_roundLoaded,
                    "${set.reps}",
                    weightText(kg, units),
                )
            }
        }
    }
}

/**
 * A rest's line under "Next: Squat" (#473), as the board writes it: "Set 2 of 4 · 48.5 lb · 8–12 reps".
 * In a circuit a set or hold's [circuit] round stands in for the set, since a member's set there is the
 * round; the load is what the set will open on ([openingLoadKg]), so the rest and the set can't disagree.
 */
fun Resources.previewDetail(
    preview: StepPreview,
    circuit: CircuitPosition?,
    loadKg: Double?,
    units: UnitSystem,
): String {
    val round = circuit?.let { getString(R.string.preview_roundOf, "${it.round}", "${it.rounds}") }
    val parts =
        when (preview) {
            is StepPreview.Hold -> {
                listOf(
                    round ?: getString(R.string.preview_set, "${preview.setIndex}", "${preview.setTotal}"),
                    preview.target?.let { holdTargetText(it) } ?: getString(R.string.preview_noTarget),
                )
            }

            is StepPreview.Reps -> {
                listOfNotNull(
                    round ?: getString(R.string.preview_set, "${preview.setIndex}", "${preview.setTotal}"),
                    loadKg?.takeIf { it > 0 }?.let { weightText(it, units) },
                    // A single target is a count, so it takes the plural forms: "1 rep", not "1 reps".
                    preview.target.max?.let { getString(R.string.preview_repsTarget, repsTargetText(preview.target)) }
                        ?: reps(preview.target.min),
                )
            }

            is StepPreview.Interval -> {
                // Unchanged (#473 named the reps line): an interval's own round, which in a circuit a HIIT
                // member runs all of on each visit, so the circuit's round would misname it.
                val progress =
                    when (val at = preview.progress) {
                        is IntervalProgress.Round -> getString(R.string.preview_round, "${at.index}", "${at.total}")
                        is IntervalProgress.Seconds -> timeText(at.seconds)
                    }
                listOf(getString(R.string.preview_interval, progress, variantName(preview.variant)))
            }
        }
    return parts.joinToString(" · ")
}

/** "Last time · 60 kg × 8 reps", or the bodyweight and hold forms. */
fun Resources.previousSetText(
    previous: PreviousSet,
    units: UnitSystem,
): String =
    when (previous) {
        is PreviousSet.Hold -> {
            getString(R.string.format_previous_hold, timeText(previous.holdSec))
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

fun Resources.oneRepMaxText(
    kg: Double,
    units: UnitSystem,
): String = getString(R.string.format_oneRepMax, weightText(kg, units))
