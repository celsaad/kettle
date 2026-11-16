package com.casco.kettle.shell.runner

import android.content.res.Resources
import com.casco.kettle.domain.ExerciseAgainstLast
import com.casco.kettle.domain.RecordKind
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.shell.R
import com.casco.kettle.shell.progress.Readings
import kotlin.math.abs

// The receipt's change since last time (#416), shown and spoken.

/**
 * The change as shown, "▲ 2.5 kg", "▲ 3 reps" at a weight that held, "Same" or "First time", and as
 * spoken, where the drawn arrow is the signed change instead ("+2.5 kg"). Both null for interval work or
 * a last time on another measure.
 */
internal fun changeTexts(
    row: ExerciseAgainstLast,
    readings: Readings?,
    resources: Resources,
): Pair<String?, String?> {
    val change = row.change
    val reps = row.repsChange?.takeIf { it != 0L }?.toDouble()
    return when {
        row.firstTime -> resources.getString(R.string.session_complete_firstTime).let { it to it }

        change == null || readings == null -> null to null

        // Decided on what's printed, so 61.2 kg against 135 lb can't read "▲ 0 lb".
        readings.reading(abs(change)) != readings.reading(0.0) -> signed(change, readings)

        // Reps take no units; the argument is only the constructor's.
        reps != null -> signed(reps, Readings(resources, RecordKind.MostReps, UnitSystem.METRIC))

        else -> resources.getString(R.string.session_complete_same).let { it to it }
    }
}

/** "▲ 2.5 kg" drawn, "+2.5 kg" said, as Getting stronger and Exercise detail write them. */
private fun signed(
    change: Double,
    readings: Readings,
): Pair<String, String> = readings.change(change) to readings.delta(change)
