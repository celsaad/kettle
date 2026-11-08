package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The engine's clock and cues, from RN `use-session-runner.test.tsx`'s timing, catch-up, hold,
 * milestone and +30s sections (§2.2–2.4 and §2.6 of #294's inventory), plus #295's pause rule and
 * the count-in #297 moved into the service.
 */
class RunnerTimingTest {
    @Nested
    inner class Countdown {
        @Test
        fun `seeds the countdown target on the first step`() {
            val runner = Runner(workoutOf(single("burpees")))

            assertEquals(40.0, runner.snapshot.clock.stepEndSec)
            assertEquals(40.0, runner.remaining())
            assertEquals(0, runner.index)
        }

        @Test
        fun `does not auto-advance off the first step immediately`() {
            val runner = Runner(workoutOf(single("burpees")))
            runner.tick(1)

            assertEquals(0, runner.index)
            assertEquals(39.0, runner.remaining())
        }

        @Test
        fun `advances only once the full target has elapsed`() {
            val runner = Runner(workoutOf(single("burpees")))
            runner.tick(39)
            assertEquals(0, runner.index)

            runner.tick(1)
            assertEquals(1, runner.index)
        }

        @Test
        fun `floors part-seconds, as both the display and the log do`() {
            val runner = Runner(workoutOf(single("burpees")))
            runner.now += 39_999
            runner.send(RunnerCommand.TimeUpdate(runner.snapshot.visit))

            assertEquals(0, runner.index)
            assertEquals(1.0, runner.remaining())
        }

        @Test
        fun `ticks the last three seconds of a rest and of a countdown interval`() {
            val runner = Runner(workoutOf(single("burpees")))
            runner.tick(36)
            assertEquals(0, runner.cues(RunnerCue.TICK))

            runner.tick(4) // 3, 2, 1, then the end
            assertEquals(3, runner.cues(RunnerCue.TICK))

            runner.tick(20) // the 20 s rest, all the way out
            assertEquals(6, runner.cues(RunnerCue.TICK))
        }

        @Test
        fun `ticks once a second however often the service updates`() {
            val runner = Runner(workoutOf(single("burpees")))
            runner.tick(37)
            repeat(4) {
                runner.now += 250
                runner.send(RunnerCommand.TimeUpdate(runner.snapshot.visit))
            }

            assertEquals(2, runner.cues(RunnerCue.TICK))
        }

        @Test
        fun `reports a step that ended by itself, with what comes next`() {
            val runner = Runner(workoutOf(single("burpees")))
            val work = runner.step!!
            runner.tick(40)

            assertTrue(RunnerEffect.StepEnded(work, runner.step) in runner.effects)
        }

        @Test
        fun `reports no step end for a step the user ended`() {
            val runner = Runner(workoutOf(single("burpees")))
            runner.advance()

            assertFalse(runner.effects.any { it is RunnerEffect.StepEnded })
        }

        @Test
        fun `logs the configured duration of a countdown cardio when it ends`() {
            val runner = Runner(workoutOf(single("row"), single("pullups")))
            runner.tick(60)

            assertEquals(1, runner.index)
            assertEquals(60.0, runner.entry<SessionEntry.Cardio>("row")?.durationSec)
        }

        @Test
        fun `never ends a count-up cardio by itself`() {
            val runner = Runner(workoutOf(single("walk")))
            runner.tick(600)

            assertEquals(0, runner.index)
            assertEquals(600L, runner.elapsed())
        }
    }

    @Nested
    inner class Pause {
        @Test
        fun `excludes paused time from the elapsed count`() {
            val runner = Runner(workoutOf(single("burpees")))
            runner.tick(10)
            runner.send(RunnerCommand.TogglePause)
            runner.tick(30)
            assertEquals(30.0, runner.remaining())

            runner.send(RunnerCommand.TogglePause)
            runner.tick(5)
            assertEquals(25.0, runner.remaining())
            assertEquals(0, runner.index)
        }

        @Test
        fun `neither ends nor cues a paused step`() {
            val runner = Runner(workoutOf(single("burpees")))
            runner.tick(30)
            runner.send(RunnerCommand.TogglePause)
            runner.clearEffects()
            runner.tick(120)

            assertEquals(0, runner.index)
            assertEquals(0, runner.cues(RunnerCue.TICK))
        }

        /**
         * #295's deliberate difference from RN, which kept one session-wide flag and started the next
         * step paused (`use-session-runner.ts:407`): the rest after a paused interval sat at full time
         * with no Resume on its screen.
         */
        @Test
        fun `clears on a step change, so the next step runs`() {
            val runner = Runner(workoutOf(single("burpees")))
            runner.tick(10)
            runner.send(RunnerCommand.TogglePause)
            runner.advance()

            assertFalse(runner.snapshot.clock.paused)
            assertEquals(1, runner.index)
            runner.tick(20)
            assertEquals(2, runner.index)
        }

        @Test
        fun `stays with the step when Prev on the first step keeps its identity`() {
            val runner = Runner(workoutOf(single("burpees")))
            runner.tick(10)
            runner.send(RunnerCommand.TogglePause)
            runner.prev()

            assertTrue(runner.snapshot.clock.paused)
            assertEquals(30.0, runner.remaining())
        }
    }

    @Nested
    inner class CatchUp {
        @Test
        fun `advances exactly once when the countdown elapsed while away`() {
            val runner = Runner(workoutOf(single("burpees")))
            runner.away(120)

            assertEquals(1, runner.index)
        }

        /** The next step starts at the moment of return, so the time away isn't carried into it. */
        @Test
        fun `starts the next step fresh at the moment of return`() {
            val runner = Runner(workoutOf(single("burpees")))
            runner.away(120)

            assertEquals(20.0, runner.remaining())
            assertEquals(runner.now, runner.snapshot.clock.phaseStartedAt)
        }

        @Test
        fun `advances a hold whose end passed while away, logging the end rather than the overrun`() {
            val runner = Runner(workoutOf(single("plank")))
            runner.away(60)

            assertEquals(1, runner.index)
            assertEquals(
                25.0,
                runner
                    .entry<SessionEntry.TimedHold>("plank")
                    ?.sets
                    ?.single()
                    ?.holdSec,
            )
        }

        @Test
        fun `leaves a targetless hold running however long it was away`() {
            val runner = Runner(workoutOf(single("deadhang")))
            runner.away(300)

            assertEquals(0, runner.index)
            assertEquals(300L, runner.elapsed())
        }

        @Test
        fun `logs the configured duration of a cardio step, not the time away`() {
            val runner = Runner(workoutOf(single("row"), single("pullups")))
            runner.away(600)

            assertEquals(1, runner.index)
            assertEquals(60.0, runner.entry<SessionEntry.Cardio>("row")?.durationSec)
        }

        @Test
        fun `still measures a count-up cardio by the clock, having no duration to clamp to`() {
            val runner = Runner(workoutOf(single("walk")))
            runner.away(300)
            runner.advance()

            assertEquals(300.0, runner.entry<SessionEntry.Cardio>("walk")?.durationSec)
        }

        @Test
        fun `does not catch up a paused step`() {
            val runner = Runner(workoutOf(single("burpees")))
            runner.send(RunnerCommand.TogglePause)
            runner.away(300)

            assertEquals(0, runner.index)
        }
    }

    /**
     * RN's resume race (`runner.test:332-395`), where a queued tick and the foreground catch-up landed
     * in one batch, both on the old step's clock. Here each update names the visit it was aimed at, and
     * one aimed at a step already left does nothing.
     */
    @Nested
    inner class StaleStep {
        @Test
        fun `does not advance twice when a queued tick lands after the catch-up`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.advance()
            val rest = runner.snapshot.visit
            runner.now += 300 * MS
            runner.send(RunnerCommand.TimeUpdate(rest)) // the catch-up
            runner.send(RunnerCommand.TimeUpdate(rest)) // the tick that came due while away

            assertEquals(2, runner.index)
            assertEquals(1, runner.entry<SessionEntry.Reps>("pullups")?.sets?.size)
        }

        /**
         * The other order: the tick that came due while away runs first, stamped when it came due,
         * just past the rest's end, and the catch-up follows at the moment of return. As in RN, this
         * holds the other half: that the guard doesn't swallow the update that should advance.
         */
        @Test
        fun `does not advance twice when the catch-up lands after a queued tick`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.advance()
            val rest = runner.snapshot.visit
            runner.now += 91 * MS
            runner.send(RunnerCommand.TimeUpdate(rest)) // the queued tick, as it came due
            runner.now += 209 * MS
            runner.send(RunnerCommand.TimeUpdate(rest)) // then the catch-up, aimed at the same rest

            assertEquals(2, runner.index)
            assertEquals(1, runner.entry<SessionEntry.Reps>("pullups")?.sets?.size)
        }

        /**
         * The guard's own proof: the second update would expire the rest the first one started, a
         * second step from one return. Remove the visit check in `timeUpdate` and this fails.
         */
        @Test
        fun `ignores a time update aimed at a step already left`() {
            val runner = Runner(workoutOf(single("burpees")))
            val work = runner.snapshot.visit
            runner.now += 300 * MS
            runner.send(RunnerCommand.TimeUpdate(work))
            runner.now += 30 * MS
            runner.send(RunnerCommand.TimeUpdate(work))

            assertEquals(1, runner.index)
            assertEquals(1L, runner.entry<SessionEntry.Hiit>("burpees")?.roundsCompleted)
        }

        /**
         * A tap landing just after the step ended by itself. Remove the visit check in `reduceRunner`
         * and this fails.
         */
        @Test
        fun `ignores an advance aimed at a step already left`() {
            val runner = Runner(workoutOf(single("burpees")))
            val work = runner.snapshot.visit
            runner.tick(40)
            runner.send(RunnerCommand.Advance(work))

            assertEquals(1, runner.index)
            assertEquals(1L, runner.entry<SessionEntry.Hiit>("burpees")?.roundsCompleted)
        }

        @Test
        fun `completes once when the last step ends in that same batch`() {
            val runner = Runner(workoutOf(single("burpees")))
            repeat(4) { runner.advance() }
            assertEquals(4, runner.index)
            val last = runner.snapshot.visit
            runner.now += 300 * MS
            runner.send(RunnerCommand.TimeUpdate(last))
            runner.send(RunnerCommand.TimeUpdate(last))
            runner.send(RunnerCommand.Advance(runner.snapshot.visit))

            assertEquals(3L, runner.entry<SessionEntry.Hiit>("burpees")?.roundsCompleted)
            assertEquals(1, runner.completions)
        }
    }

    @Nested
    inner class Holds {
        @Test
        fun `advances at the top of the range, not at the minimum`() {
            val runner = Runner(workoutOf(single("plank")))
            runner.tick(20)
            assertEquals(0, runner.index)

            runner.tick(5)
            assertTrue(runner.step is RunnerStep.Rest)
        }

        @Test
        fun `advances at a fixed target`() {
            val runner = Runner(workoutOf(single("lsit")))
            runner.tick(14)
            assertEquals(0, runner.index)

            runner.tick(1)
            assertTrue(runner.step is RunnerStep.Rest)
        }

        @Test
        fun `never advances a hold with no target`() {
            val runner = Runner(workoutOf(single("deadhang")))
            runner.tick(600)

            assertEquals(0, runner.index)
            assertEquals(600L, runner.elapsed())
        }

        @Test
        fun `logs the same set an auto-end and a tapped Done both produce`() {
            val runner = Runner(workoutOf(single("lsit")))
            runner.tick(15)
            runner.advance()
            runner.tick(10)
            runner.advance()

            assertEquals(listOf(15.0, 10.0), runner.entry<SessionEntry.TimedHold>("lsit")?.sets?.map { it.holdSec })
        }

        @Test
        fun `ticks the last three seconds before it ends`() {
            val runner = Runner(workoutOf(single("plank")))
            runner.tick(21)
            assertEquals(0, runner.cues(RunnerCue.TICK))

            runner.tick(3)
            assertEquals(3, runner.cues(RunnerCue.TICK))
        }

        @Test
        fun `does not tick through a hold with no end to count into`() {
            val runner = Runner(workoutOf(single("deadhang")))
            runner.tick(120)

            assertEquals(0, runner.cues(RunnerCue.TICK))
        }

        @Test
        fun `keeps its own end when +30s is pressed`() {
            val runner = Runner(workoutOf(single("lsit")))
            runner.send(RunnerCommand.AddRestSeconds(30.0))
            runner.tick(15)

            assertTrue(runner.step is RunnerStep.Rest)
        }
    }

    @Nested
    inner class Cues {
        @Test
        fun `sounds an exercise change between members, not between sets`() {
            val runner = Runner(workoutOf(single("pullups"), single("dips")))
            repeat(4) { runner.advance() }
            assertEquals(0, runner.cues(RunnerCue.EXERCISE_CHANGE))

            runner.advance()
            assertEquals(1, runner.cues(RunnerCue.EXERCISE_CHANGE))
        }

        @Test
        fun `sounds one on every circuit hand-off`() {
            val runner = Runner(workoutOf(circuitOf("pullups", "dips")))
            repeat(3) { runner.advance() }

            assertEquals(3, runner.cues(RunnerCue.EXERCISE_CHANGE))
        }

        @Test
        fun `sounds nothing at completion`() {
            val runner = Runner(workoutOf(single("dips")))
            repeat(3) { runner.advance() }

            assertEquals(1, runner.completions)
            assertEquals(0, runner.cues(RunnerCue.EXERCISE_CHANGE))
        }

        @Test
        fun `sounds the milestone once at the halfway point of a hiit work interval`() {
            val runner = Runner(workoutOf(single("burpees")))
            runner.tick(19)
            assertEquals(0, runner.cues(RunnerCue.MILESTONE))

            runner.tick(1)
            assertEquals(1, runner.cues(RunnerCue.MILESTONE))

            runner.tick(10)
            assertEquals(1, runner.cues(RunnerCue.MILESTONE))
            assertEquals(0, runner.index)
        }

        @Test
        fun `sounds it again on the next interval, having reset per step`() {
            val runner = Runner(workoutOf(single("burpees")))
            runner.tick(40)
            runner.advance()
            runner.clearEffects()
            runner.tick(20)

            assertEquals(1, runner.cues(RunnerCue.MILESTONE))
        }

        @Test
        fun `sounds once when a range hold crosses its minimum`() {
            val runner = Runner(workoutOf(single("plank")))
            runner.tick(14)
            assertEquals(0, runner.cues(RunnerCue.MILESTONE))

            runner.tick(1)
            assertEquals(1, runner.cues(RunnerCue.MILESTONE))

            runner.tick(5)
            assertEquals(1, runner.cues(RunnerCue.MILESTONE))
            assertEquals(20L, runner.elapsed())
        }

        @Test
        fun `stays silent on a fixed-target hold, whose minimum is its end`() {
            val runner = Runner(workoutOf(single("lsit")))
            runner.tick(15)

            assertEquals(0, runner.cues(RunnerCue.MILESTONE))
        }

        @Test
        fun `stays silent through a hold with no target at all`() {
            val runner = Runner(workoutOf(single("deadhang")))
            runner.tick(120)

            assertEquals(0, runner.cues(RunnerCue.MILESTONE))
        }

        @Test
        fun `sounds again on a hold redone with Prev`() {
            val runner = Runner(workoutOf(single("plank")))
            runner.tick(15)
            assertEquals(1, runner.cues(RunnerCue.MILESTONE))

            runner.tick(10)
            assertTrue(runner.step is RunnerStep.Rest)
            runner.prev()
            runner.clearEffects()
            runner.tick(15)

            assertEquals(1, runner.cues(RunnerCue.MILESTONE))
        }

        @Test
        fun `stays silent through a rest countdown`() {
            val runner = Runner(workoutOf(single("burpees")))
            runner.tick(40)
            runner.clearEffects()
            runner.tick(19)

            assertEquals(0, runner.cues(RunnerCue.MILESTONE))
        }

        @Test
        fun `cues only the exercise change when a step ends after a gap`() {
            val runner = Runner(workoutOf(single("plank"), single("pullups")))
            runner.clearEffects()
            runner.away(60)

            assertEquals(0, runner.cues(RunnerCue.MILESTONE) + runner.cues(RunnerCue.TICK))
        }
    }

    /** RN `runner.test:1718-1760`, for the clock: identity, not the index, decides a re-seed. */
    @Nested
    inner class Reseed {
        @Test
        fun `restarts the clock on a swap, which keeps the index`() {
            val runner = Runner(workoutOf(single("deadhang")))
            runner.tick(10)
            runner.send(RunnerCommand.Swap(runner.exercise("plank")))

            assertEquals(0, runner.index)
            assertEquals(0L, runner.elapsed())
            assertEquals(25.0, runner.snapshot.clock.stepEndSec)
        }

        @Test
        fun `keeps the clock running when a set is added or dropped`() {
            val runner = Runner(workoutOf(single("deadhang")))
            runner.tick(10)
            runner.send(RunnerCommand.AddSet)
            runner.send(RunnerCommand.DropSet)

            assertEquals(10L, runner.elapsed())
        }

        @Test
        fun `starts an exercise added to a parked ad-hoc session on a fresh clock`() {
            val runner = Runner(null)
            runner.tick(30)
            runner.send(RunnerCommand.AddExercise(runner.exercise("row")))

            assertEquals(60.0, runner.remaining())
        }
    }

    @Nested
    inner class AddRestSeconds {
        @Test
        fun `moves only a countdown's end, never a hold's or a reps step's`() {
            val runner = Runner(workoutOf(single("lsit"), single("pullups")))
            val hold = runner.state
            runner.send(RunnerCommand.AddRestSeconds(30.0))
            assertSame(hold, runner.state)
            assertEquals(15.0, runner.snapshot.clock.stepEndSec)
        }

        @Test
        fun `extends the countdown`() {
            val runner = Runner(workoutOf(single("burpees")))
            runner.tick(10)
            runner.send(RunnerCommand.AddRestSeconds(30.0))

            assertEquals(70.0, runner.snapshot.clock.stepEndSec)
            assertEquals(60.0, runner.remaining())
            runner.tick(59)
            assertEquals(0, runner.index)
            runner.tick(1)
            assertEquals(1, runner.index)
        }
    }

    @Nested
    inner class CountIn {
        @Test
        fun `ticks 3, 2, 1 a second apart, then changes exercise and is done`() {
            var countIn = CountIn(T0)
            val heard = mutableListOf<List<RunnerCue>>()
            for (second in 0..3) {
                val update = updateCountIn(countIn, T0 + second * MS)
                countIn = update.countIn
                heard += update.cues
                assertEquals(second == 3, update.done)
            }

            val tick = listOf(RunnerCue.TICK)
            assertEquals(listOf(tick, tick, tick, listOf(RunnerCue.EXERCISE_CHANGE)), heard)
        }

        @Test
        fun `shows the numeral for the second it is in`() {
            assertEquals(listOf(3, 3, 2, 1), listOf(0L, 999L, 1000L, 2500L).map { CountIn(T0).numeral(T0 + it) })
        }

        @Test
        fun `ticks a numeral once however often it is updated`() {
            val once = updateCountIn(CountIn(T0), T0 + 100).countIn

            assertEquals(emptyList<RunnerCue>(), updateCountIn(once, T0 + 600).cues)
        }

        @Test
        fun `changes exercise once, however often it is updated after the end`() {
            val ended = updateCountIn(CountIn(T0, ticked = 3), T0 + 3 * MS)
            val again = updateCountIn(ended.countIn, T0 + 4 * MS)

            assertEquals(listOf(RunnerCue.EXERCISE_CHANGE), ended.cues)
            assertEquals(emptyList<RunnerCue>(), again.cues)
            assertTrue(again.done)
        }

        @Test
        fun `does not play numerals a gap skipped`() {
            val update = updateCountIn(CountIn(T0), T0 + 2 * MS)

            assertEquals(listOf(RunnerCue.TICK), update.cues)
            assertEquals(3, update.countIn.ticked)
        }
    }

    /**
     * The service sleeps until [nextTimeUpdateAt] rather than polling at 1 Hz (#305), so sleeping from
     * one to the next has to hear everything the ticker would: the same cues, ends and writes, at the
     * same moments.
     */
    @Nested
    inner class NextWake {
        private fun Runner.sleepUntil(end: Long) {
            // Stops at the finish as the service does (`RunnerSession.settle`): a finished session's
            // snapshot still holds its last step, which reads as overdue and would wake at once for ever.
            while (!state.finished) {
                val wake = nextTimeUpdateAt(snapshot, now) ?: return
                if (wake > end) return
                now = wake
                send(RunnerCommand.TimeUpdate(snapshot.visit))
            }
        }

        private fun assertSleepMatchesTicker(
            workout: Workout,
            seconds: Int,
        ) {
            val ticker = Runner(workout).apply { tick(seconds) }
            val sleeper = Runner(workout).apply { sleepUntil(T0 + seconds * MS) }

            assertEquals(ticker.effects, sleeper.effects)
            assertEquals(ticker.index, sleeper.index)
        }

        @Test
        fun `hears a hiit's milestones, ticks and rests as the ticker does`() =
            assertSleepMatchesTicker(workoutOf(single("burpees")), 200)

        @Test
        fun `hears a range hold's minimum, its last seconds and its end`() =
            assertSleepMatchesTicker(workoutOf(single("plank")), 120)

        @Test
        fun `hears fixed holds, countdown cardio, emom, amrap and a rest block`() =
            assertSleepMatchesTicker(
                workoutOf(single("lsit"), single("row"), single("swings"), single("rest"), single("grinder")),
                900,
            )

        @Test
        fun `wakes first at a hiit work interval's halfway mark`() {
            val runner = Runner(workoutOf(single("burpees")))

            assertEquals(T0 + 20 * MS, nextTimeUpdateAt(runner.snapshot, runner.now))
        }

        @Test
        fun `wakes three seconds before a rest ends, then each second to its end`() {
            val runner = Runner(workoutOf(single("rest")))

            assertEquals(T0 + 57 * MS, nextTimeUpdateAt(runner.snapshot, T0))
            assertEquals(T0 + 58 * MS, nextTimeUpdateAt(runner.snapshot, T0 + 57 * MS))
            assertEquals(T0 + 60 * MS, nextTimeUpdateAt(runner.snapshot, T0 + 59 * MS + 999))
        }

        @Test
        fun `moves with +30s`() {
            val runner = Runner(workoutOf(single("rest")))
            runner.send(RunnerCommand.AddRestSeconds(30.0))

            assertEquals(T0 + 87 * MS, nextTimeUpdateAt(runner.snapshot, T0))
        }

        @Test
        fun `counts paused time out of the wake`() {
            val runner = Runner(workoutOf(single("rest")))
            runner.send(RunnerCommand.TogglePause)
            runner.now += 10 * MS
            runner.send(RunnerCommand.TogglePause)

            assertEquals(T0 + 67 * MS, nextTimeUpdateAt(runner.snapshot, runner.now))
        }

        @Test
        fun `wakes at once for a step already past its end, so a missed wake isn't the end of it`() {
            // Seen on the S25+ (#306): a last HIIT round's end wake never ran, and once the step was past
            // its end nothing scheduled another, Resume included, so the session sat at 0:00 for good.
            val runner = Runner(workoutOf(single("rest")))
            val late = T0 + 75 * MS

            assertEquals(late, nextTimeUpdateAt(runner.snapshot, late))
        }

        @Test
        fun `doesn't wake at once for a hold past its minimum, which only its end or the user ends`() {
            val runner = Runner(workoutOf(single("deadhang")))

            assertEquals(null, nextTimeUpdateAt(runner.snapshot, T0 + 75 * MS))
        }

        @Test
        fun `never wakes for a paused step`() {
            val runner = Runner(workoutOf(single("rest")))
            runner.send(RunnerCommand.TogglePause)

            assertEquals(null, nextTimeUpdateAt(runner.snapshot, runner.now))
        }

        @Test
        fun `never wakes for a step only the user ends, or past a hold's last cue`() {
            assertEquals(null, Runner(workoutOf(single("pullups"))).let { nextTimeUpdateAt(it.snapshot, it.now) })
            assertEquals(null, Runner(workoutOf(single("walk"))).let { nextTimeUpdateAt(it.snapshot, it.now) })
            assertEquals(null, Runner(workoutOf(single("deadhang"))).let { nextTimeUpdateAt(it.snapshot, it.now) })
        }
    }

    @Nested
    inner class Clock {
        @Test
        fun `maps the monotonic clock onto the wall clock`() {
            val anchor = ClockAnchor(wallMs = T0, monotonicMs = 50_000)

            assertEquals(T0 + 30_000, anchor.wallAt(80_000))
        }

        /**
         * A reboot restarts the monotonic clock near 0. Read through the old anchor that would put
         * "now" before the step began; through a fresh one, the rest that ran out during the reboot
         * is caught up on wall-clock time.
         */
        @Test
        fun `survives a monotonic reset by taking a fresh anchor`() {
            val before = ClockAnchor(wallMs = T0, monotonicMs = 500_000)
            val runner = Runner(workoutOf(single("pullups")), start = before.wallAt(500_000))
            runner.advance()

            val afterReboot = ClockAnchor(wallMs = T0 + 600_000, monotonicMs = 1_200)
            assertTrue(before.wallAt(1_500) < runner.snapshot.clock.phaseStartedAt)
            runner.now = afterReboot.wallAt(1_500)
            runner.send(RunnerCommand.TimeUpdate(runner.snapshot.visit))

            assertEquals(2, runner.index)
            assertEquals(
                600.0,
                runner
                    .entry<SessionEntry.Reps>("pullups")
                    ?.sets
                    ?.single()
                    ?.restTakenSec,
            )
        }

        @Test
        fun `writes times as JS's toISOString does`() {
            assertEquals("2026-07-27T09:00:00.000Z", isoInstant(T0))
            assertEquals("2026-07-27T09:00:01.007Z", isoInstant(T0 + 1007))
        }
    }
}
