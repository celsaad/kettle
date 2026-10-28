package com.casco.kettle.domain

import kotlin.math.floor
import kotlin.math.pow

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
 * JavaScript's `Math.round`: an exact half rounds toward +∞, where `kotlin.math.round` would round
 * it to even and turn 2.125 kg into 2.12. Built on `floor` rather than `roundToLong`, which also
 * rounds halves up but throws on NaN and clamps infinities, where `Math.round` passes both through.
 * `scaled - down` can lose a bit between −1 and 0, but never enough to carry a value across 0.5, so
 * the half comparison is never wrong.
 *
 * One difference from `Math.round` is kept on purpose: a value in (−0.5, 0) rounds to +0.0, not −0.
 * The corpus treats the two as equal, and Kotlin would print −0 as "-0.0" where JS prints "0".
 */
fun roundHalfUp(
    value: Double,
    decimals: Int = 0,
): Double {
    val factor = 10.0.pow(decimals)
    val scaled = value * factor
    val down = floor(scaled)
    return (if (scaled - down >= HALF) down + 1 else down) / factor
}

private const val HALF = 0.5

/** Stored kilograms → the number to show the user, in their own unit. */
fun toDisplayWeight(
    kg: Double,
    system: UnitSystem,
): Double = roundHalfUp(if (system == UnitSystem.IMPERIAL) kg / KG_PER_LB else kg, displayDecimals(system))

/** A number the user entered in their own unit → kilograms to store. */
fun fromDisplayWeight(
    value: Double,
    system: UnitSystem,
): Double =
    // Metric passes straight through: the number typed is the stored value, so rounding it here
    // would silently edit input the user can see.
    if (system == UnitSystem.IMPERIAL) roundHalfUp(value * KG_PER_LB, STORED_DECIMALS) else value

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
