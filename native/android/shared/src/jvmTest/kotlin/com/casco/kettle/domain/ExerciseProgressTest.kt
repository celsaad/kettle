package com.casco.kettle.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * `src/state/selectors/__tests__/exercise-progress.test.ts`, case for case. The measure itself is
 * `entryBest`'s, held by `conformance/records.yaml`; these pin the windowing, the two-session floor,
 * the one-kind rule, the fixed-hold exclusion, the split, the order and the best-ever mark.
 *
 * In UTC, which is what jest's run in too: the zone only moves where the window's calendar days fall.
 */
class ExerciseProgressTest {
    private val now = Instant.parse("2026-08-18T12:00:00.000Z")

    private fun progress(
        sessions: List<Session>,
        exercises: List<Exercise> = emptyList(),
    ) = exerciseProgress(sessions, exercises, weeks = 4, now = now, zone = TimeZone.UTC)

    @Test
    fun `reports the change from the first session in the window to the last`() {
        val view =
            progress(
                listOf(
                    session("2026-08-04T10:00:00.000Z", loaded("rdl", 10.0)),
                    session("2026-08-11T10:00:00.000Z", loaded("rdl", 11.0)),
                    session("2026-08-17T10:00:00.000Z", loaded("rdl", 12.0)),
                ),
            )

        assertEquals(
            ProgressView(
                movers =
                    listOf(
                        ExerciseProgress(
                            exerciseId = "rdl",
                            kind = RecordKind.HeaviestSet,
                            points = listOf(10.0, 11.0, 12.0),
                            latest = 12.0,
                            delta = 2.0,
                            lastTrainedAt = "2026-08-17T10:00:00.000Z",
                            bestEver = true,
                            sessions =
                                listOf(
                                    ProgressSession("2026-08-04T10:00:00.000Z", 10.0, newBest = false),
                                    ProgressSession("2026-08-11T10:00:00.000Z", 11.0, newBest = true),
                                    ProgressSession("2026-08-17T10:00:00.000Z", 12.0, newBest = true),
                                ),
                        ),
                    ),
                steady = emptyList(),
                fixedHoldsLeftOut = 0,
            ),
            view,
        )
    }

    /** Last-minus-first, not best-minus-worst: a good day inside a plateau is not movement. */
    @Test
    fun `measures the ends of the window, not its high point`() {
        val view =
            progress(
                listOf(
                    session("2026-08-04T10:00:00.000Z", loaded("rdl", 10.0)),
                    session("2026-08-11T10:00:00.000Z", loaded("rdl", 20.0)),
                    session("2026-08-17T10:00:00.000Z", loaded("rdl", 10.0)),
                ),
            )

        assertEquals(emptyList<ExerciseProgress>(), view.movers)
        assertEquals(0.0, view.steady[0].delta)
        assertEquals(10.0, view.steady[0].latest)
    }

    @Test
    fun `leaves out sessions older than the window`() {
        val view =
            progress(
                listOf(
                    session("2026-05-01T10:00:00.000Z", loaded("rdl", 5.0)),
                    session("2026-08-11T10:00:00.000Z", loaded("rdl", 11.0)),
                    session("2026-08-17T10:00:00.000Z", loaded("rdl", 12.0)),
                ),
            )

        assertEquals(listOf(11.0, 12.0), view.rows()[0].points)
    }

    /** One session is not a trend. */
    @Test
    fun `drops an exercise trained only once in the window`() {
        val view =
            progress(
                listOf(
                    session("2026-08-11T10:00:00.000Z", loaded("rdl", 11.0), hold("plank", 40.0)),
                    session("2026-08-17T10:00:00.000Z", loaded("rdl", 12.0)),
                ),
            )

        assertEquals(listOf("rdl"), view.rows().map { it.exerciseId })
    }

    @Test
    fun `skips a session that was never finished`() {
        val view =
            progress(
                listOf(
                    session("2026-08-11T10:00:00.000Z", loaded("rdl", 11.0)),
                    session("2026-08-14T10:00:00.000Z", loaded("rdl", 99.0), endedAt = null),
                    session("2026-08-17T10:00:00.000Z", loaded("rdl", 12.0)),
                ),
            )

        assertEquals(listOf(11.0, 12.0), view.rows()[0].points)
    }

    /** Two blocks of one exercise are one session's work: one point, at the better of them. */
    @Test
    fun `takes one point per session when an exercise is logged twice in it`() {
        val view =
            progress(
                listOf(
                    session("2026-08-11T10:00:00.000Z", loaded("rdl", 10.0), loaded("rdl", 12.0)),
                    session("2026-08-17T10:00:00.000Z", loaded("rdl", 14.0)),
                ),
            )

        assertEquals(listOf(12.0, 14.0), view.rows()[0].points)
    }

    /** Reps and kilograms can't be subtracted, so the older kind's points are dropped. */
    @Test
    fun `reports only the most recent measure when an exercise changed from bodyweight to loaded`() {
        val view =
            progress(
                listOf(
                    session("2026-08-01T10:00:00.000Z", bodyweight("squat", 20)),
                    session("2026-08-08T10:00:00.000Z", bodyweight("squat", 22)),
                    session("2026-08-12T10:00:00.000Z", loaded("squat", 10.0)),
                    session("2026-08-17T10:00:00.000Z", loaded("squat", 14.0)),
                ),
            )

        val row = view.movers[0]
        assertEquals(RecordKind.HeaviestSet, row.kind)
        assertEquals(listOf(10.0, 14.0), row.points)
        assertEquals(4.0, row.delta)
    }

    @Test
    fun `holds compete on seconds`() {
        val view =
            progress(
                listOf(
                    session("2026-08-11T10:00:00.000Z", hold("plank", 40.0)),
                    session("2026-08-17T10:00:00.000Z", hold("plank", 55.0)),
                ),
            )

        val row = view.movers[0]
        assertEquals(RecordKind.LongestHold, row.kind)
        assertEquals(listOf(40.0, 55.0), row.points)
        assertEquals(15.0, row.delta)
    }

    @Test
    fun `leaves out interval work, whose numbers are set by the workout`() {
        val view =
            progress(
                listOf(
                    session(
                        "2026-08-11T10:00:00.000Z",
                        SessionEntry.Hiit("burpees", 3),
                        SessionEntry.Cardio("row", durationSec = 600.0),
                    ),
                    session(
                        "2026-08-17T10:00:00.000Z",
                        SessionEntry.Hiit("burpees", 5),
                        SessionEntry.Cardio("row", durationSec = 900.0),
                    ),
                ),
            )

        assertEquals(ProgressView(emptyList(), emptyList(), 0), view)
    }

    @Test
    fun `has nothing to say about an empty log`() {
        assertEquals(ProgressView(emptyList(), emptyList(), 0), progress(emptyList()))
    }

    @Nested
    inner class FixedTargetHolds {
        private fun twoHolds(
            id: String,
            first: Double,
            second: Double,
        ) = listOf(
            session("2026-08-11T10:00:00.000Z", hold(id, first)),
            session("2026-08-17T10:00:00.000Z", hold(id, second)),
        )

        /** A fixed hold logs its target every time, so it could only ever read "no change". */
        @Test
        fun `are left out, and counted`() {
            val view = progress(twoHolds("hurdler", 45.0, 45.0), listOf(holdExercise("hurdler", min = 45.0)))

            assertEquals(ProgressView(emptyList(), emptyList(), 1), view)
        }

        /** A range whose top equals its bottom is a fixed hold spelled differently. */
        @Test
        fun `are left out when the range has no width`() {
            val view =
                progress(twoHolds("hurdler", 45.0, 45.0), listOf(holdExercise("hurdler", min = 45.0, max = 45.0)))

            assertEquals(ProgressView(emptyList(), emptyList(), 1), view)
        }

        @Test
        fun `are left out when cut short, too`() {
            val view = progress(twoHolds("hurdler", 45.0, 30.0), listOf(holdExercise("hurdler", min = 45.0)))

            assertEquals(emptyList<ExerciseProgress>(), view.movers)
        }

        @Test
        fun `are counted only once they would have been a row`() {
            val view =
                progress(
                    listOf(session("2026-08-17T10:00:00.000Z", hold("hurdler", 45.0))),
                    listOf(holdExercise("hurdler", min = 45.0)),
                )

            assertEquals(0, view.fixedHoldsLeftOut)
        }

        @Test
        fun `keep a range hold in`() {
            val view = progress(twoHolds("lsit", 20.0, 30.0), listOf(holdExercise("lsit", min = 20.0, max = 40.0)))

            assertEquals(listOf("lsit"), view.movers.map { it.exerciseId })
        }

        @Test
        fun `keep a max-effort hold in`() {
            val view = progress(twoHolds("plank", 40.0, 55.0), listOf(holdExercise("plank")))

            assertEquals(listOf("plank"), view.movers.map { it.exerciseId })
        }

        /** Hiding a row needs a reason, and "no config" isn't one. */
        @Test
        fun `keep an exercise that is no longer in the library`() {
            val view = progress(twoHolds("gone", 45.0, 45.0))

            assertEquals(listOf("gone"), view.steady.map { it.exerciseId })
            assertEquals(0, view.fixedHoldsLeftOut)
        }
    }

    @Nested
    inner class MoversAndSteady {
        @Test
        fun `splits on whether the exercise changed`() {
            val view =
                progress(
                    listOf(
                        session("2026-08-11T10:00:00.000Z", loaded("rdl", 10.0), hold("plank", 30.0)),
                        session("2026-08-17T10:00:00.000Z", loaded("rdl", 12.0), hold("plank", 30.0)),
                    ),
                )

            assertEquals(listOf("rdl"), view.movers.map { it.exerciseId })
            assertEquals(listOf("plank"), view.steady.map { it.exerciseId })
        }

        /** Relative change, so +3 reps on 5 outranks +2 kg on 10, and every gain comes before any dip. */
        @Test
        fun `ranks gains by relative change, then dips`() {
            val view =
                progress(
                    listOf(
                        session(
                            "2026-08-11T10:00:00.000Z",
                            loaded("rdl", 10.0),
                            bodyweight("chin-up", 5),
                            loaded("squat", 20.0),
                            hold("plank", 40.0),
                        ),
                        session(
                            "2026-08-17T10:00:00.000Z",
                            loaded("rdl", 12.0),
                            bodyweight("chin-up", 8),
                            loaded("squat", 15.0),
                            hold("plank", 38.0),
                        ),
                    ),
                )

            assertEquals(listOf("chin-up", "rdl", "squat", "plank"), view.movers.map { it.exerciseId })
        }

        @Test
        fun `orders steady rows most recently trained first`() {
            val view =
                progress(
                    listOf(
                        session("2026-08-05T10:00:00.000Z", hold("plank", 30.0), loaded("rdl", 10.0)),
                        session("2026-08-10T10:00:00.000Z", hold("plank", 30.0)),
                        session("2026-08-17T10:00:00.000Z", loaded("rdl", 10.0)),
                    ),
                )

            assertEquals(listOf("rdl", "plank"), view.steady.map { it.exerciseId })
        }
    }

    @Nested
    inner class BestEver {
        private fun chinUps(vararg sessions: Pair<String, Long>) =
            sessions.map { (startedAt, reps) -> session(startedAt, bodyweight("chin-up", reps)) }

        @Test
        fun `marks a best set inside the window by beating an earlier session`() {
            val view = progress(chinUps("2026-08-04T10:00:00.000Z" to 5, "2026-08-17T10:00:00.000Z" to 8))

            assertTrue(view.movers[0].bestEver)
        }

        @Test
        fun `still marks it when a later session only matches it`() {
            val view =
                progress(
                    chinUps(
                        "2026-08-04T10:00:00.000Z" to 5,
                        "2026-08-11T10:00:00.000Z" to 8,
                        "2026-08-17T10:00:00.000Z" to 8,
                    ),
                )

            assertTrue(view.movers[0].bestEver)
        }

        @Test
        fun `does not mark a row that has fallen back from its best`() {
            val view =
                progress(
                    chinUps(
                        "2026-08-04T10:00:00.000Z" to 5,
                        "2026-08-11T10:00:00.000Z" to 9,
                        "2026-08-17T10:00:00.000Z" to 8,
                    ),
                )

            assertFalse(view.movers[0].bestEver)
        }

        @Test
        fun `does not mark a best nobody beat`() {
            val view = progress(chinUps("2026-08-04T10:00:00.000Z" to 5, "2026-08-17T10:00:00.000Z" to 5))

            assertFalse(view.steady[0].bestEver)
        }

        @Test
        fun `does not mark a best set before the window and only matched inside it`() {
            val view =
                progress(
                    chinUps(
                        "2026-05-01T10:00:00.000Z" to 8,
                        "2026-05-08T10:00:00.000Z" to 12,
                        "2026-08-11T10:00:00.000Z" to 10,
                        "2026-08-17T10:00:00.000Z" to 12,
                    ),
                )

            assertEquals(2.0, view.movers[0].delta)
            assertFalse(view.movers[0].bestEver)
        }

        @Test
        fun `judges each measure against its own history`() {
            val view =
                progress(
                    listOf(
                        session("2026-08-01T10:00:00.000Z", bodyweight("squat", 20)),
                        session("2026-08-12T10:00:00.000Z", loaded("squat", 10.0)),
                        session("2026-08-17T10:00:00.000Z", loaded("squat", 14.0)),
                    ),
                )

            assertEquals(RecordKind.HeaviestSet, view.movers[0].kind)
            assertTrue(view.movers[0].bestEver)
        }
    }

    /** What the progress screen charts: a session sets a new best by the rule `bestEver` uses. */
    @Nested
    inner class Sessions {
        private fun chinUps(vararg sessions: Pair<String, Long>) =
            sessions.map { (startedAt, reps) -> session(startedAt, bodyweight("chin-up", reps)) }

        @Test
        fun `marks each session that beat everything before it, and no other`() {
            val view =
                progress(
                    chinUps(
                        "2026-07-28T10:00:00.000Z" to 5,
                        "2026-08-01T10:00:00.000Z" to 5,
                        "2026-08-05T10:00:00.000Z" to 6,
                        "2026-08-10T10:00:00.000Z" to 6,
                        "2026-08-15T10:00:00.000Z" to 7,
                    ),
                )

            assertEquals(
                listOf(5.0 to false, 5.0 to false, 6.0 to true, 6.0 to false, 7.0 to true),
                view.movers[0].sessions.map { it.value to it.newBest },
            )
        }

        /** The window trims what is listed, not what counts as beaten. */
        @Test
        fun `judges a session against the whole log, not just the window`() {
            val view =
                progress(
                    chinUps(
                        "2026-05-01T10:00:00.000Z" to 12,
                        "2026-08-11T10:00:00.000Z" to 10,
                        "2026-08-17T10:00:00.000Z" to 13,
                    ),
                )

            assertEquals(
                listOf(
                    ProgressSession("2026-08-11T10:00:00.000Z", 10.0, newBest = false),
                    ProgressSession("2026-08-17T10:00:00.000Z", 13.0, newBest = true),
                ),
                view.movers[0].sessions,
            )
        }
    }
}

/** Every row in render order, for tests about the measure rather than the split. */
private fun ProgressView.rows() = movers + steady

private fun session(
    startedAt: String,
    vararg entries: SessionEntry,
    endedAt: String? = startedAt,
) = Session(
    version = 1,
    id = startedAt,
    workout = null,
    program = null,
    programWeek = null,
    programDay = null,
    startedAt = startedAt,
    endedAt = endedAt,
    entries = entries.toList(),
)

private fun loaded(
    exercise: String,
    weightKg: Double,
) = SessionEntry.Reps(exercise, listOf(RepsSetLog(reps = 8, weightKg = weightKg, restTakenSec = 60.0)))

private fun bodyweight(
    exercise: String,
    reps: Long,
) = SessionEntry.Reps(exercise, listOf(RepsSetLog(reps = reps, restTakenSec = 60.0)))

private fun hold(
    exercise: String,
    holdSec: Double,
) = SessionEntry.TimedHold(exercise, listOf(TimedHoldSetLog(holdSec = holdSec, restTakenSec = 30.0)))

private fun holdExercise(
    id: String,
    min: Double? = null,
    max: Double? = null,
) = Exercise(id, id, ExerciseConfig.TimedHold(sets = 1, holdSecMin = min, holdSecMax = max, restSec = 30.0))
