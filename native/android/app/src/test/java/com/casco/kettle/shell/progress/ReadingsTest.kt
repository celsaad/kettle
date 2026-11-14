package com.casco.kettle.shell.progress

import com.casco.kettle.domain.RecordKind
import com.casco.kettle.domain.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The progress chart's axis labels (#413): the top line carries the unit, the rest are bare numbers,
 * and a weight's axis is in the reader's units. In `pt`, so the unit words are the bundle's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class ReadingsTest {
    private fun readings(
        kind: RecordKind,
        units: UnitSystem = UnitSystem.METRIC,
    ) = Readings(RuntimeEnvironment.getApplication().resources, kind, units)

    @Test
    fun `the top line carries the unit, and the lines under it are bare numbers`() {
        val weight = readings(RecordKind.HeaviestSet, UnitSystem.IMPERIAL)

        assertEquals("60 lb", weight.axis(60.0, top = true))
        assertEquals("40", weight.axis(40.0, top = false))
        assertEquals("0", weight.axis(0.0, top = false))
    }

    @Test
    fun `a hold's top line is in seconds, and reps' in reps`() {
        assertEquals("60s", readings(RecordKind.LongestHold).axis(60.0, top = true))
        assertEquals("15 repetições", readings(RecordKind.MostReps).axis(15.0, top = true))
    }

    /** Stored in kilograms always: the axis is round in what the reader reads, so a weight is converted first. */
    @Test
    fun `puts a weight in the reader's units for the axis, and nothing else`() {
        assertEquals(48.5, readings(RecordKind.HeaviestSet, UnitSystem.IMPERIAL).display(22.0), 0.01)
        assertEquals(22.0, readings(RecordKind.HeaviestSet).display(22.0), 0.0)
        assertEquals(22.0, readings(RecordKind.MostReps, UnitSystem.IMPERIAL).display(22.0), 0.0)
    }

    /** A rep, a round and a second are whole, so the axis never steps under one; a weight can. */
    @Test
    fun `steps a counted measure's axis in ones at least, and a weight's however finely`() {
        assertEquals(1.0, readings(RecordKind.MostReps).minStep, 0.0)
        assertEquals(1.0, readings(RecordKind.LongestHold).minStep, 0.0)
        assertEquals(0.0, readings(RecordKind.HeaviestSet).minStep, 0.0)
    }
}
