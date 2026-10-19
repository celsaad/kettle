package com.casco.kettle.domain

import kotlin.math.pow
import kotlin.math.roundToLong

/** Exact by definition (the international avoirdupois pound), not an approximation to tune. */
private const val KG_PER_LB = 0.45359237

/**
 * Pounds display coarser than the stored 0.01 kg, so a value typed in pounds always redisplays as
 * itself: 135 lb → 61.23 kg → 135.0 lb.
 */
private fun displayDecimals(system: UnitSystem): Int =
    when (system) {
        UnitSystem.METRIC -> 2
        UnitSystem.IMPERIAL -> 1
    }

private const val STORED_DECIMALS = 2

/**
 * JavaScript's `Math.round`: an exact half rounds toward +∞. `roundToLong` does the same, where
 * `kotlin.math.round` would round it to even and turn 2.125 kg into 2.12.
 */
private fun round(
    value: Double,
    decimals: Int,
): Double {
    val factor = 10.0.pow(decimals)
    return (value * factor).roundToLong() / factor
}

/** Stored kilograms → the number to show the user, in their own unit. */
fun toDisplayWeight(
    kg: Double,
    system: UnitSystem,
): Double = round(if (system == UnitSystem.IMPERIAL) kg / KG_PER_LB else kg, displayDecimals(system))

/** A number the user entered in their own unit → kilograms to store. */
fun fromDisplayWeight(
    value: Double,
    system: UnitSystem,
): Double =
    // Metric passes straight through: the number typed is the stored value, so rounding it here
    // would silently edit input the user can see.
    if (system == UnitSystem.IMPERIAL) round(value * KG_PER_LB, STORED_DECIMALS) else value

/**
 * The runner's −/+ load step, in display units: the smallest jump the equipment actually has in
 * each system (a pair of 1.25 kg plates, a pair of 2.5 lb plates), not one converted from the other.
 */
fun weightStep(system: UnitSystem): Double =
    when (system) {
        UnitSystem.METRIC -> METRIC_STEP_KG
        UnitSystem.IMPERIAL -> IMPERIAL_STEP_LB
    }

private const val METRIC_STEP_KG = 2.5
private const val IMPERIAL_STEP_LB = 5.0
