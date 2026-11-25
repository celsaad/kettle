package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The estimated one-rep max a records row shows under a loaded set (`estimatedOneRepMaxKg`, Epley). */
class OneRepMaxTest {
    @Test
    fun `applies Epley to a multi-rep set`() {
        // 100 × (1 + 8/30)
        assertEquals(126.667, estimatedOneRepMaxKg(100.0, 8)!!, 0.001)
    }

    @Test
    fun `returns a single as itself rather than inflating it`() {
        // A single already is a one-rep max: the formula would report a 3% lift nobody made, on the one
        // input where the honest answer is known exactly.
        assertEquals(140.0, estimatedOneRepMaxKg(140.0, 1))
    }

    @Test
    fun `has no answer for a bodyweight set, absent or zero`() {
        // Either shape reaches here, and answering 0 would put "Est. 1RM 0 kg" under a bodyweight PR.
        assertNull(estimatedOneRepMaxKg(null, 10))
        assertNull(estimatedOneRepMaxKg(0.0, 10))
    }

    @Test
    fun `has no answer for a set with no reps`() {
        assertNull(estimatedOneRepMaxKg(100.0, 0))
    }

    @Test
    fun `declines to estimate past twelve reps`() {
        // Epley diverges past about 12: a 20-rep set projects to nearly 1.7× the load, which is not a
        // number to show next to the word "estimated".
        assertNotNull(estimatedOneRepMaxKg(100.0, 12))
        assertNull(estimatedOneRepMaxKg(100.0, 13))
    }
}
