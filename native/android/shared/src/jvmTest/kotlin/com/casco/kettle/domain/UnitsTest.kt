package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * What JSON can't carry, so the corpus can't pin it: JS `Math.round` passes NaN and ±∞ through,
 * where `roundToLong` throws on NaN and clamps infinities to `Long`'s range.
 */
class UnitsTest {
    @Test
    fun `a non-finite weight passes through rounding instead of throwing`() {
        assertEquals(Double.NaN, toDisplayWeight(Double.NaN, UnitSystem.METRIC))
        assertEquals(Double.POSITIVE_INFINITY, toDisplayWeight(Double.POSITIVE_INFINITY, UnitSystem.IMPERIAL))
        assertEquals(Double.NEGATIVE_INFINITY, fromDisplayWeight(Double.NEGATIVE_INFINITY, UnitSystem.IMPERIAL))
    }

    @Test
    fun `a negative exact half rounds toward positive infinity, as Math round does`() {
        // -0.125 * 100 is exactly -12.5 in binary. Math.round gives -12; rounding away from zero gives -13.
        assertEquals(-0.12, toDisplayWeight(-0.125, UnitSystem.METRIC))
    }
}
