package com.casco.kettle.shell.progress

import android.content.res.Resources
import com.casco.kettle.domain.RecordKind
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.jsNumber
import com.casco.kettle.domain.roundHalfUp
import com.casco.kettle.domain.toDisplayWeight
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.pluralText
import kotlin.math.abs
import kotlin.math.sign

/**
 * A progress value as text: kilograms through the reader's units, seconds and reps rounded. RN's
 * `useProgressReading` and `formatProgressReading`. Stats' Getting stronger rows and the exercise
 * progress screen they open both print through this, so the row and the screen show one number.
 */
class Readings(
    private val resources: Resources,
    private val kind: RecordKind,
    private val units: UnitSystem,
) {
    fun reading(value: Double): String =
        when (kind) {
            RecordKind.HeaviestSet -> {
                val unit =
                    resources.getString(
                        if (units ==
                            UnitSystem.IMPERIAL
                        ) {
                            R.string.units_lb
                        } else {
                            R.string.units_kg
                        },
                    )
                "${jsNumber(toDisplayWeight(value, units))} $unit"
            }

            RecordKind.LongestHold -> {
                resources.getString(R.string.format_seconds, jsNumber(roundHalfUp(value)))
            }

            RecordKind.MostReps, RecordKind.MostRounds -> {
                val reps = roundHalfUp(value).toInt()
                resources.pluralText(R.plurals.format_rep, R.string.format_rep_zero, reps)
            }
        }

    /**
     * The change, signed. The magnitude is read and the sign added, so "−1 rep" pluralises on 1; and a
     * flat window has its own words rather than a "+0" that reads as a rounding artefact.
     */
    fun delta(delta: Double): String =
        when (delta.sign) {
            0.0 -> resources.getString(R.string.analytics_progressFlat)
            1.0 -> resources.getString(R.string.analytics_progressUp, reading(abs(delta)))
            else -> resources.getString(R.string.analytics_progressDown, reading(abs(delta)))
        }
}
