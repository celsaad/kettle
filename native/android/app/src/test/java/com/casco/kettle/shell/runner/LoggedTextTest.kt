package com.casco.kettle.shell.runner

import androidx.test.core.app.ApplicationProvider
import com.casco.kettle.domain.LoggedUnit
import com.casco.kettle.domain.PreviousSet
import com.casco.kettle.domain.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The rest's Logged line, every wording of it, in `pt`: an English assertion couldn't catch one left
 * hardcoded, and the screen tests only ever log a loaded set.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class LoggedTextTest {
    private val resources = ApplicationProvider.getApplicationContext<android.app.Application>().resources

    private fun text(logged: LoggedUnit) = resources.loggedText(logged, UnitSystem.METRIC)

    @Test
    fun `a set says its number and what was logged in it`() {
        assertEquals("série 2 · 8 × 60 kg", text(LoggedUnit.Set(2, PreviousSet.Reps(8, 60.0))))
        assertEquals("série 1 · 8 repetições", text(LoggedUnit.Set(1, PreviousSet.Reps(8, null))))
        assertEquals("série 1 · 1 repetição", text(LoggedUnit.Set(1, PreviousSet.Reps(1, 0.0))))
        assertEquals("série 3 · 45s", text(LoggedUnit.Set(3, PreviousSet.Hold(45.0))))
    }

    @Test
    fun `a round and a minute say their number`() {
        assertEquals("rodada 4", text(LoggedUnit.Round(4)))
        assertEquals("minuto 7", text(LoggedUnit.Minute(7)))
    }

    @Test
    fun `a whole entry is its exercise's name as written, or the word for a rest`() {
        assertEquals("AMRAP 12 Min", text(LoggedUnit.Entry("AMRAP 12 Min")))
        assertEquals("descanso", text(LoggedUnit.Entry(null)))
    }
}
