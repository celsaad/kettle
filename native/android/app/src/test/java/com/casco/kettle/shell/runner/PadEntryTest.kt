package com.casco.kettle.shell.runner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The number pad's entry rules (§3.4 of #294's inventory), `session-number-pad.tsx`'s buffer. */
class PadEntryTest {
    private fun PadEntry.typed(keys: String) = keys.fold(this) { entry, key -> entry.type(key) }

    @Test
    fun `shows the seeded value and confirms it until a key is typed`() {
        val entry = PadEntry(seed = 12.5, allowDecimal = true)
        assertEquals("12.5", entry.display)
        assertEquals(12.5, entry.value)
    }

    @Test
    fun `a whole seed shows without a decimal point`() {
        assertEquals("8", PadEntry(seed = 8.0, allowDecimal = false).display)
    }

    @Test
    fun `the first key replaces the value rather than appending to it`() {
        val entry = PadEntry(seed = 8.0, allowDecimal = false).typed("3")
        assertEquals("3", entry.display)
        assertEquals(3.0, entry.value)
    }

    @Test
    fun `stops at six characters`() {
        assertEquals("123456", PadEntry(0.0, allowDecimal = false).typed("1234567").display)
    }

    @Test
    fun `takes one decimal point, and only where allowed`() {
        assertEquals("1.25", PadEntry(0.0, allowDecimal = true).typed("1..25").display)
        assertEquals("125", PadEntry(0.0, allowDecimal = false).typed("1.25").display)
    }

    @Test
    fun `a lone point can't be confirmed, and a trailing one reads as the whole number`() {
        assertNull(PadEntry(0.0, allowDecimal = true).typed(".").value)
        assertEquals(5.0, PadEntry(0.0, allowDecimal = true).typed("5.").value)
    }

    @Test
    fun `backspace shortens what was typed, and emptied shows the seed again`() {
        val entry = PadEntry(seed = 40.0, allowDecimal = true).typed("12")
        assertEquals("1", entry.backspace().display)
        assertEquals("40", entry.backspace().backspace().display)
        assertEquals(40.0, entry.backspace().backspace().value)
    }

    @Test
    fun `a negative seed can't be confirmed`() {
        assertNull(PadEntry(seed = -1.0, allowDecimal = false).value)
    }
}
