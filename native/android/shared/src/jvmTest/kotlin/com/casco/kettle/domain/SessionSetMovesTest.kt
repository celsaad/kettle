package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The session editor's set moves (#442): Undo restores exactly, and an added set copies the last. */
class SessionSetMovesTest {
    private val squats =
        SessionEntry.Reps(
            "squat",
            listOf(
                RepsSetLog(reps = 8, weightKg = 100.0, rpe = 7.0, restTakenSec = 90.0),
                RepsSetLog(reps = 8, weightKg = 100.0, rpe = 8.0, restTakenSec = 95.0),
                RepsSetLog(reps = 6, weightKg = 100.0, rpe = 9.0, restTakenSec = 0.0),
            ),
        )
    private val drafts = listOf(EntryDraft(0, squats, entryToSetForms(squats, UnitSystem.IMPERIAL)))

    // Its rest and its stored kilograms come back too: rebuilt from text, 100 kg would return as 100.02.
    @Test
    fun `puts a removed set back exactly where it was`() {
        val (removed, taken) = drafts.removingSet(0, 1)!!

        assertEquals(2, removed[0].forms.size)
        assertEquals(drafts, removed.restoring(taken))
    }

    @Test
    fun `puts back an exercise's last set`() {
        val one = listOf(EntryDraft(0, squats, entryToSetForms(squats, UnitSystem.METRIC).take(1)))
        val (removed, taken) = one.removingSet(0, 0)!!

        assertEquals(emptyList<SetForm>(), removed[0].forms)
        assertEquals(one, removed.restoring(taken))
    }

    @Test
    fun `takes nothing for a set that isn't there`() {
        assertEquals(null, drafts.removingSet(0, 5))
    }

    @Test
    fun `adds a copy of the last set, with the effort blank and no rest`() {
        val added = drafts.addingSet(0)[0].forms.last()

        assertEquals("6", added.values[EntryField.Reps])
        assertEquals(drafts[0].forms.last().values[EntryField.Load], added.values[EntryField.Load])
        assertEquals("", added.values[EntryField.Rpe])
        assertEquals(0.0, added.restTakenSec)
    }

    // The copy keeps the stored kilograms, so saving it writes 100, not the 100.02 220.5 lb converts to.
    @Test
    fun `saves an added set's load as the kilograms it copied`() {
        val session =
            Session(
                1,
                "s",
                null,
                null,
                null,
                null,
                "2026-11-01T09:00:00.000Z",
                "2026-11-01T10:00:00.000Z",
                listOf(squats),
            )
        val saved = editedSession(session, drafts.addingSet(0), UnitSystem.IMPERIAL).entries[0] as SessionEntry.Reps

        assertEquals(100.0, saved.sets.last().weightKg)
        assertEquals(4, saved.sets.size)
    }

    @Test
    fun `adds no set to an entry that logs one result`() {
        val hiit = SessionEntry.Hiit("burpees", 8)
        val one = listOf(EntryDraft(0, hiit, entryToSetForms(hiit, UnitSystem.METRIC)))

        assertEquals(one, one.addingSet(0))
    }
}
