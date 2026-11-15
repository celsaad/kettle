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
import com.casco.kettle.shell.i18n.weightUnit

// The runner's words, as RN's `domain/format.ts` and the session components assemble them. In one
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

internal fun Resources.seconds(value: Double): String = getString(R.string.format_seconds, jsNumber(value))

/**
 * What Undo would take back, after "Logged": "set 1 · 10 × 48.5 lb", "round 3", or the entry's name. A
 * circuit's member logs a round, which "set 2" read as a second set of the same move (#473).
 */
fun Resources.loggedText(
    logged: LoggedUnit,
    units: UnitSystem,
): String =
    when (logged) {
        is LoggedUnit.Set -> {
            setText(logged.number, logged.set, units, if (logged.inCircuit) ROUND_KEYS else SET_KEYS)
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

/** One logged set after its number, with [keys] saying "set" or, in a circuit, "round". */
private fun Resources.setText(
    number: Int,
    set: PreviousSet,
    units: UnitSystem,
    keys: SetKeys,
): String {
    val index = "$number"
    return when (set) {
        is PreviousSet.Hold -> {
            getString(keys.hold, index, seconds(set.holdSec))
        }

        is PreviousSet.Reps -> {
            val kg = set.weightKg?.takeIf { it > 0 }
            if (kg == null) {
                getString(keys.bodyweight, index, reps(set.reps))
            } else {
                getString(keys.loaded, index, "${set.reps}", weightText(kg, units))
            }
        }
    }
}

/** The three ways a logged set is said: held, at bodyweight, loaded. */
private class SetKeys(
    val hold: Int,
    val bodyweight: Int,
    val loaded: Int,
)

private val SET_KEYS =
    SetKeys(R.string.session_logged_setHold, R.string.session_logged_setBodyweight, R.string.session_logged_setLoaded)
private val ROUND_KEYS =
    SetKeys(
        R.string.session_logged_roundHold,
        R.string.session_logged_roundBodyweight,
        R.string.session_logged_roundLoaded,
    )

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
                        is IntervalProgress.Seconds -> getString(R.string.preview_seconds, jsNumber(at.seconds))
                    }
                listOf(getString(R.string.preview_interval, progress, preview.variant.json))
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

fun Resources.oneRepMaxText(
    kg: Double,
    units: UnitSystem,
): String = getString(R.string.format_oneRepMax, weightText(kg, units))
