package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * `src/domain/__tests__/session-entry-form.test.ts`, case for case. The interesting failure modes live
 * here: a load that drifts on a round trip through pounds, a removal that staples the wrong rest onto
 * the wrong set, an entry that survives losing its last set.
 */
class SessionEntryFormTest {
    private val repsEntry =
        SessionEntry.Reps(
            "back-squat",
            listOf(
                RepsSetLog(reps = 8, weightKg = 60.0, rpe = 8.0, restTakenSec = 90.0),
                RepsSetLog(reps = 8, weightKg = 60.0, rpe = 9.0, restTakenSec = 120.0),
                RepsSetLog(reps = 6, weightKg = 60.0, restTakenSec = 75.0),
            ),
        )

    private val holdEntry =
        SessionEntry.TimedHold("plank", listOf(TimedHoldSetLog(45.0, 60.0), TimedHoldSetLog(40.0, 60.0)))

    @Nested
    inner class EntryFields {
        @Test
        fun `has fields for the five types the editor rewrites`() {
            assertNotNull(entryFields(repsEntry))
            assertNotNull(entryFields(holdEntry))
            assertNotNull(entryFields(SessionEntry.Hiit("burpees", 4)))
            assertNotNull(entryFields(SessionEntry.Amrap("cindy", 12, 3)))
            assertNotNull(entryFields(SessionEntry.Cardio("row", durationSec = 1200.0)))
        }

        // Both are deliberate scope cuts rather than oversights, so they get pinned.
        @Test
        fun `has none for emom and rest`() {
            assertNull(entryFields(SessionEntry.Emom("power-clean", listOf(EmomMinuteLog(3), EmomMinuteLog(3)))))
            assertNull(entryFields(SessionEntry.Rest("rest", 120.0)))
        }
    }

    @Nested
    inner class EntryToSetForms {
        @Test
        fun `gives a per-set type one row per logged set`() {
            val forms = entryToSetForms(repsEntry, UnitSystem.METRIC)

            assertEquals(3, forms.size)
            assertEquals(
                mapOf(EntryField.Reps to "8", EntryField.Load to "60", EntryField.Rpe to "8"),
                forms[0].values,
            )
        }

        // A bodyweight set has no weight, and an unrecorded RPE is not an RPE of 0.
        @Test
        fun `shows an absent load and an absent RPE as empty, not as zero`() {
            assertEquals("", entryToSetForms(repsEntry, UnitSystem.METRIC)[2].values[EntryField.Rpe])

            val bodyweight = SessionEntry.Reps("pull-ups", listOf(RepsSetLog(reps = 8, restTakenSec = 60.0)))
            assertEquals("", entryToSetForms(bodyweight, UnitSystem.METRIC)[0].values[EntryField.Load])
        }

        @Test
        fun `converts the load for display and carries the stored kilograms alongside it`() {
            val forms = entryToSetForms(repsEntry, UnitSystem.IMPERIAL)

            assertEquals("132.3", forms[0].values[EntryField.Load])
            assertEquals(60.0, forms[0].weightKg)
        }

        @Test
        fun `keeps each set's own rest, which has no field and still has to survive a save`() {
            assertEquals(
                listOf(90.0, 120.0, 75.0),
                entryToSetForms(repsEntry, UnitSystem.METRIC).map { it.restTakenSec },
            )
        }

        @Test
        fun `gives the one-result types a single row`() {
            assertEquals(
                listOf(SetForm(mapOf(EntryField.Rounds to "4"), 0.0)),
                entryToSetForms(SessionEntry.Hiit("burpees", 4), UnitSystem.METRIC),
            )
        }
    }

    @Nested
    inner class ValidateEntryForm {
        private fun formsFor(vararg values: Pair<EntryField, String>) = listOf(SetForm(values.toMap(), 0.0))

        @Test
        fun `accepts a valid entry`() {
            assertNull(validateEntryForm(repsEntry, entryToSetForms(repsEntry, UnitSystem.METRIC)))
        }

        @Test
        fun `rejects a required field left blank`() {
            assertEquals(
                EntryProblem.Required(EntryField.Reps),
                validateEntryForm(repsEntry, formsFor(EntryField.Reps to "", EntryField.Load to "60")),
            )
        }

        @Test
        fun `rejects a non-numeric value`() {
            assertEquals(
                EntryProblem.MustBeNumber(EntryField.Hold),
                validateEntryForm(holdEntry, formsFor(EntryField.Hold to "ages")),
            )
        }

        // The floor is 0 here where the exercise form's is 1: this describes what happened, and the
        // runner can and does log a set failed at zero reps.
        @Test
        fun `accepts a logged zero`() {
            assertNull(validateEntryForm(repsEntry, formsFor(EntryField.Reps to "0", EntryField.Load to "60")))
            assertNull(validateEntryForm(SessionEntry.Hiit("burpees", 4), formsFor(EntryField.Rounds to "0")))
        }

        @Test
        fun `rejects a negative`() {
            assertEquals(
                EntryProblem.MustBeAtLeast(EntryField.Reps, 0.0),
                validateEntryForm(repsEntry, formsFor(EntryField.Reps to "-2")),
            )
        }

        // A mis-tap that turns 8 into 88 is the mistake this feature exists to fix, not to fix *into*.
        @Test
        fun `rejects an RPE above the scale but accepts its ends`() {
            fun rpe(value: String) =
                validateEntryForm(
                    repsEntry,
                    formsFor(
                        EntryField.Reps to "8",
                        EntryField.Rpe to value,
                    ),
                )

            assertEquals(EntryProblem.MustBeAtMost(EntryField.Rpe, 10.0), rpe("88"))
            assertNull(rpe("10"))
            assertNull(rpe("1"))
            assertEquals(EntryProblem.MustBeAtLeast(EntryField.Rpe, 1.0), rpe("0"))
        }

        @Test
        fun `rejects a fractional count but allows a fractional load`() {
            assertEquals(
                EntryProblem.MustBeWhole(EntryField.Reps),
                validateEntryForm(repsEntry, formsFor(EntryField.Reps to "8.5")),
            )
            assertNull(validateEntryForm(repsEntry, formsFor(EntryField.Reps to "8", EntryField.Load to "62.5")))
        }

        // Either cardio field alone is allowed, so only a cross-field rule catches an entry with neither.
        @Test
        fun `rejects a cardio entry with neither duration nor distance`() {
            val cardio = SessionEntry.Cardio("row", durationSec = 1200.0)

            assertEquals(
                EntryProblem.CardioNeedsOne,
                validateEntryForm(cardio, formsFor(EntryField.Duration to "", EntryField.Distance to "")),
            )
            assertNull(validateEntryForm(cardio, formsFor(EntryField.Duration to "1200", EntryField.Distance to "")))
            assertNull(validateEntryForm(cardio, formsFor(EntryField.Duration to "", EntryField.Distance to "5000")))
        }

        @Test
        fun `checks every row, not just the first`() {
            val forms = entryToSetForms(repsEntry, UnitSystem.METRIC).toMutableList()
            forms[2] = forms[2].copy(values = forms[2].values + (EntryField.Reps to ""))

            assertEquals(EntryProblem.Required(EntryField.Reps), validateEntryForm(repsEntry, forms))
        }

        // JS's `Number` reads these, and so must the port, or a value RN saved would be refused here.
        @Test
        fun `reads numbers the way JavaScript's Number does`() {
            assertNull(validateEntryForm(holdEntry, formsFor(EntryField.Hold to " 45 ")))
            assertNull(validateEntryForm(holdEntry, formsFor(EntryField.Hold to ".5")))
            assertNull(validateEntryForm(holdEntry, formsFor(EntryField.Hold to "4.5e1")))
            assertNull(validateEntryForm(holdEntry, formsFor(EntryField.Hold to "0x2D")))
            assertEquals(
                EntryProblem.MustBeNumber(EntryField.Hold),
                validateEntryForm(
                    holdEntry,
                    formsFor(EntryField.Hold to "0x-1"),
                ),
            )
            // Kotlin's own parser takes a type suffix and the words; JS's refuses or overflows them.
            assertEquals(
                EntryProblem.MustBeNumber(EntryField.Hold),
                validateEntryForm(
                    holdEntry,
                    formsFor(EntryField.Hold to "45d"),
                ),
            )
            assertEquals(
                EntryProblem.MustBeNumber(EntryField.Hold),
                validateEntryForm(holdEntry, formsFor(EntryField.Hold to "Infinity")),
            )
            assertEquals(
                EntryProblem.MustBeNumber(EntryField.Hold),
                validateEntryForm(
                    holdEntry,
                    formsFor(EntryField.Hold to "62,5"),
                ),
            )
        }
    }

    @Nested
    inner class BuildEntry {
        private fun Pair<Int, List<SetForm>>.edit(vararg values: Pair<EntryField, String>): List<SetForm> =
            second.toMutableList().also { it[first] = it[first].copy(values = it[first].values + values) }

        @Test
        fun `round-trips an untouched entry unchanged`() {
            assertEquals(
                repsEntry,
                buildEntry(repsEntry, entryToSetForms(repsEntry, UnitSystem.METRIC), UnitSystem.METRIC),
            )
            assertEquals(
                holdEntry,
                buildEntry(holdEntry, entryToSetForms(holdEntry, UnitSystem.METRIC), UnitSystem.METRIC),
            )
        }

        @Test
        fun `applies an edited value and leaves the other sets alone`() {
            val forms = (1 to entryToSetForms(repsEntry, UnitSystem.METRIC)).edit(EntryField.Reps to "5")

            assertEquals(
                repsEntry.copy(sets = listOf(repsEntry.sets[0], repsEntry.sets[1].copy(reps = 5), repsEntry.sets[2])),
                buildEntry(repsEntry, forms, UnitSystem.METRIC),
            )
        }

        @Test
        fun `clears an optional field back to absent when it is emptied`() {
            val forms =
                (0 to entryToSetForms(repsEntry, UnitSystem.METRIC)).edit(EntryField.Rpe to "", EntryField.Load to "")
            val built = buildEntry(repsEntry, forms, UnitSystem.METRIC) as SessionEntry.Reps

            assertEquals(RepsSetLog(reps = 8, restTakenSec = 90.0), built.sets[0])
        }

        /**
         * The drift guard: 60 kg displays as 132.3 lb, which converts back to 60.01, so editing another
         * field of a set while in pounds used to rewrite its load. Drop `weightKg` from [buildEntry]'s
         * untouched case and this fails with 60.01.
         */
        @Test
        fun `keeps the stored load exactly when the load field was not touched, in either unit`() {
            for (system in UnitSystem.entries) {
                val forms = (0 to entryToSetForms(repsEntry, system)).edit(EntryField.Reps to "9")
                val built = buildEntry(repsEntry, forms, system) as SessionEntry.Reps

                assertEquals(60.0, built.sets[0].weightKg, system.name)
            }
        }

        @Test
        fun `converts a load the user did type back to kilograms`() {
            val forms = (0 to entryToSetForms(repsEntry, UnitSystem.IMPERIAL)).edit(EntryField.Load to "135")
            val built = buildEntry(repsEntry, forms, UnitSystem.IMPERIAL) as SessionEntry.Reps

            assertEquals(61.23, built.sets[0].weightKg)
        }

        // Removal is a splice, so a row's position stops matching the set it came from.
        @Test
        fun `keeps each surviving set's own rest after a removal`() {
            val forms = entryToSetForms(repsEntry, UnitSystem.METRIC).filterIndexed { index, _ -> index != 1 }
            val built = buildEntry(repsEntry, forms, UnitSystem.METRIC) as SessionEntry.Reps

            assertEquals(
                listOf(
                    RepsSetLog(reps = 8, weightKg = 60.0, rpe = 8.0, restTakenSec = 90.0),
                    RepsSetLog(reps = 6, weightKg = 60.0, restTakenSec = 75.0),
                ),
                built.sets,
            )
        }

        // The caller's signal to drop the entry rather than write an exercise that did nothing.
        @Test
        fun `returns null when the last row is removed`() {
            assertNull(buildEntry(repsEntry, emptyList(), UnitSystem.METRIC))
            assertNull(buildEntry(SessionEntry.Hiit("burpees", 4), emptyList(), UnitSystem.METRIC))
        }

        @Test
        fun `round-trips the one-result types`() {
            val cases =
                listOf(
                    SessionEntry.Hiit("burpees", 4),
                    SessionEntry.Amrap("cindy", 12, 3),
                    SessionEntry.Amrap("cindy", 12, null),
                    SessionEntry.Cardio("row", durationSec = 1200.0),
                    SessionEntry.Cardio("row", distanceMeters = 5000.0),
                )
            for (entry in cases) {
                assertEquals(entry, buildEntry(entry, entryToSetForms(entry, UnitSystem.METRIC), UnitSystem.METRIC))
            }
        }

        @Test
        fun `never changes which exercise an entry is for`() {
            val forms = (0 to entryToSetForms(repsEntry, UnitSystem.METRIC)).edit(EntryField.Reps to "1")

            assertEquals("back-squat", buildEntry(repsEntry, forms, UnitSystem.METRIC)?.exercise)
        }

        // Display precision differs per system, so the load's round trip is the unit-dependent one.
        @Test
        fun `survives an untouched save of a load in either unit`() {
            val bench = SessionEntry.Reps("bench", listOf(RepsSetLog(reps = 5, weightKg = 100.0, restTakenSec = 180.0)))
            for (system in UnitSystem.entries) {
                assertEquals(bench, buildEntry(bench, entryToSetForms(bench, system), system), system.name)
            }
        }
    }

    /** A zero means "not done": dropped on save, as the runner never writes one. */
    @Nested
    inner class ZeroRows {
        private fun withReps(reps: String) = SetForm(mapOf(EntryField.Reps to reps), 60.0)

        @Test
        fun `drops a zero row and keeps the rest of the entry`() {
            val built =
                buildEntry(
                    repsEntry,
                    listOf(withReps("8"), withReps("0")),
                    UnitSystem.METRIC,
                ) as SessionEntry.Reps

            assertEquals(listOf(8L), built.sets.map { it.reps })
        }

        @Test
        fun `returns null when every row is zero, which the editor saves as a removal`() {
            assertNull(buildEntry(repsEntry, listOf(withReps("0"), withReps("")), UnitSystem.METRIC))
            assertNull(buildEntry(holdEntry, listOf(SetForm(mapOf(EntryField.Hold to "0"), 0.0)), UnitSystem.METRIC))
        }

        @Test
        fun `flags only the rows that would be dropped`() {
            assertTrue(isZeroForm(repsEntry, withReps("0"), UnitSystem.METRIC))
            assertFalse(isZeroForm(repsEntry, withReps("1"), UnitSystem.METRIC))
        }
    }

    /** The editor's whole-session half: what it offers, and the one session it writes back. */
    @Nested
    inner class EditingASession {
        private val rest = SessionEntry.Rest("rest", 120.0)
        private val emom = SessionEntry.Emom("clean", listOf(EmomMinuteLog(3)))
        private val session =
            Session(
                1,
                "session-1",
                "leg-day",
                null,
                null,
                null,
                "2026-07-29T09:00:00.000Z",
                "2026-07-29T10:00:00.000Z",
                listOf(repsEntry, rest, holdEntry),
            )

        private fun editable(of: Session = session) =
            sessionEditing(listOf(of), "session-1", UnitSystem.METRIC) as SessionEditing.Editable

        /**
         * Into `session.entries`, where the plank sits at 2: History shows it at 1, having filtered the
         * rest out, and writing back by that position would rewrite the rest entry.
         */
        @Test
        fun `indexes the raw entries, not the rest-filtered ones History shows`() {
            assertEquals(listOf(0, 2), editable().drafts.map { it.index })
        }

        @Test
        fun `writes an edit back to the entry it came from and leaves the others as they were`() {
            val drafts = editable().drafts.toMutableList()
            val plank = drafts[1]
            drafts[1] =
                plank.copy(forms = listOf(plank.forms[0].copy(values = mapOf(EntryField.Hold to "60")), plank.forms[1]))

            val edited = editedSession(session, drafts, UnitSystem.METRIC)

            assertEquals(
                listOf(repsEntry, rest, holdEntry.copy(sets = listOf(TimedHoldSetLog(60.0, 60.0), holdEntry.sets[1]))),
                edited.entries,
            )
        }

        // RN removed back to front, since each removal shifted every later index. One rebuild can't.
        @Test
        fun `drops each entry left with nothing, and only those`() {
            val drafts = editable().drafts.map { it.copy(forms = emptyList()) }

            assertEquals(listOf<SessionEntry>(rest), editedSession(session, drafts, UnitSystem.METRIC).entries)
        }

        @Test
        fun `keeps an entry it can't rewrite, untouched, and lists it read-only`() {
            val withEmom = session.copy(entries = listOf(repsEntry, emom))
            val editing = editable(withEmom)

            assertEquals(listOf<SessionEntry>(emom), editing.readOnly)
            assertEquals(withEmom, editedSession(withEmom, editing.drafts, UnitSystem.METRIC))
        }

        @Test
        fun `finds the first problem across every entry`() {
            val drafts = editable().drafts.toMutableList()
            drafts[1] = drafts[1].copy(forms = listOf(SetForm(mapOf(EntryField.Hold to ""), 60.0)))

            assertEquals(EntryProblem.Required(EntryField.Hold), firstProblem(drafts))
            assertNull(firstProblem(editable().drafts))
        }

        // The runner owns an in-flight session's file and writes through its own copy of it, so an edit
        // made here would be overwritten by the next set logged.
        @Test
        fun `refuses a session that is still running`() {
            assertEquals(
                SessionEditing.Running,
                sessionEditing(listOf(session.copy(endedAt = null)), "session-1", UnitSystem.METRIC),
            )
        }

        @Test
        fun `says so when the session is gone`() {
            assertEquals(SessionEditing.NotFound, sessionEditing(emptyList(), "session-1", UnitSystem.METRIC))
            assertEquals(SessionEditing.NotFound, sessionEditing(listOf(session), null, UnitSystem.METRIC))
        }

        @Test
        fun `says so when nothing in it has fields`() {
            assertEquals(
                SessionEditing.NothingToEdit,
                sessionEditing(listOf(session.copy(entries = listOf(rest, emom))), "session-1", UnitSystem.METRIC),
            )
        }
    }
}
