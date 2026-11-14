package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The duration picker's steppers (#440). */
class DurationStepsTest {
    @Test
    fun `splits seconds into minutes and seconds`() {
        assertEquals(1 to 15, durationParts(75.0))
        assertEquals(0 to 0, durationParts(-3.0))
    }

    @Test
    fun `steps minutes, keeping the seconds, never below zero or past the ceiling`() {
        assertEquals(135.0, steppingMinutes(75.0, +1))
        assertEquals(15.0, steppingMinutes(15.0, -1))
        assertEquals(MAX_PICKER_MINUTES * 60.0, steppingMinutes(MAX_PICKER_MINUTES * 60.0, +1))
    }

    // A clock's seconds: 0:55 up is 0:00, not 1:00, since minutes have their own stepper.
    @Test
    fun `steps seconds by five, wrapping within the minute`() {
        assertEquals(80.0, steppingSeconds(75.0, +1))
        assertEquals(60.0, steppingSeconds(115.0, +1))
        assertEquals(115.0, steppingSeconds(60.0, -1))
    }

    // A file's 0:22 steps to the grid from where it is: up to 0:25, down to 0:20.
    @Test
    fun `lands a value off the step on the step`() {
        assertEquals(25.0, steppingSeconds(22.0, +1))
        assertEquals(20.0, steppingSeconds(22.0, -1))
    }
}
