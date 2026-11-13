package com.casco.kettle.shell.runner

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerPlan
import com.casco.kettle.domain.RunnerState
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.WorkoutBlock
import com.casco.kettle.domain.buildSteps
import com.casco.kettle.domain.lastLogged
import com.casco.kettle.domain.reduceRunner
import com.casco.kettle.domain.startRunner
import com.casco.kettle.shell.theme.KettleTheme
import org.junit.Rule

/**
 * What the runner's screen tests share: the fixtures, a screen shown in `pt` at a phone's size, and the
 * commands and hand-backs it sent. Each suite is a subclass, so no one class outgrows a read.
 */
abstract class RunnerScreenHarness {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    protected val squat = Exercise("squat", "Agachamento", ExerciseConfig.Reps(2, 5, null, 60.0, 90.0))
    protected val plank = Exercise("plank", "Prancha", ExerciseConfig.TimedHold(2, 20.0, 30.0, 30.0))

    /** Back-to-back sets, so the step after its first is its second, where a swap is offered again. */
    protected val hang = Exercise("hang", "Suspensão", ExerciseConfig.TimedHold(2, 20.0, null, 0.0))
    protected val maxHold = Exercise("maxhold", "Suspensão máxima", ExerciseConfig.TimedHold(1, null, null, 30.0))
    protected val fixedHold = Exercise("fixedhold", "Isometria fixa", ExerciseConfig.TimedHold(1, 30.0, null, 30.0))
    protected val everyMinute = Exercise("emom", "Swing", ExerciseConfig.Emom(60.0, 5.0, 10))
    protected val grinder = Exercise("amrap", "Moedor", ExerciseConfig.Amrap(600.0))
    protected val row = Exercise("row", "Remo", ExerciseConfig.Cardio())
    protected val burpees = Exercise("burpees", "Burpees", ExerciseConfig.Hiit(20.0, 10.0, 2))
    protected val rest = Exercise("rest", "Descanso", ExerciseConfig.Rest(60.0))
    protected val extras =
        listOf("Búlgaro", "Terra", "Supino", "Remada", "Flexão", "Barra", "Rosca", "Elevação").mapIndexed {
            index,
            name,
            ->
            Exercise("extra$index", name, ExerciseConfig.Reps(3, 8, restSec = 60.0))
        }

    /** A rep range with a top, which "Try" needs: every set at 12 last time suggests the next load. */
    protected val bench = Exercise("bench", "Supino", ExerciseConfig.Reps(2, 8, 12, 60.0, 90.0))
    protected val exercises =
        listOf(squat, plank, hang, maxHold, fixedHold, everyMinute, grinder, row, burpees, rest, bench) + extras

    protected var entry by mutableStateOf<RunnerEntry>(RunnerEntry.Loading)
    protected var prior by mutableStateOf<List<Session>>(emptyList())
    protected var soundsOn = true
    protected val sent = mutableListOf<RunnerCommand>()
    protected var left = 0
    protected var done = 0
    protected var startedAnyway = 0
    protected val adopted = mutableListOf<Pair<String, Double>>()

    protected fun plan(vararg blocks: WorkoutBlock): RunnerPlan.Runnable {
        val workout = Workout("w", "Pernas", blocks.toList())
        return RunnerPlan.Runnable(workout, exercises, buildSteps(workout, exercises))
    }

    protected fun block(exercise: Exercise) = WorkoutBlock.ExerciseBlock(exercise.id)

    protected fun started(plan: RunnerPlan.Runnable) = startRunner(plan, null, now = T0).state

    protected fun RunnerState.after(vararg commands: RunnerCommand): RunnerState =
        commands.fold(this) { state, command -> reduceRunner(state, command, T0).state }

    /** The step on screen logged or skipped, aimed at it as a tap would be. */
    protected fun RunnerState.advanced(): RunnerState = after(RunnerCommand.Advance(snapshot.visit))

    /** [state] as the session shows it, Undo's record included unless [restored] drops it, as a rebuild does. */
    protected fun running(
        plan: RunnerPlan.Runnable,
        state: RunnerState = started(plan),
        restored: Boolean = false,
    ) = RunnerEntry.Attach(
        RunnerView.Running(plan, state.snapshot, state.session, logged = lastLogged(state).takeUnless { restored }),
    )

    protected var shown = false
    protected var fontScale by mutableStateOf(1f)

    /** The first call sets the content; a later one only changes what it shows, as a new view would. */
    protected fun show(next: RunnerEntry) {
        entry = next
        if (shown) {
            compose.waitForIdle()
            return
        }
        shown = true
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Screen()
            }
        }
    }

    @Composable
    private fun Screen() {
        KettleTheme(darkTheme = true) {
            RunnerScreen(
                entry,
                RunnerEnvironment(prior, UnitSystem.METRIC, soundsOn),
                T0,
                RunnerActions(
                    send = { sent += it },
                    startAnyway = { startedAnyway += 1 },
                    leave = { left += 1 },
                    done = { done += 1 },
                    adopt = { id, kg -> adopted += id to kg },
                ),
            )
        }
    }

    protected fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    protected val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    protected fun button(label: String) = compose.onNode((hasText(label) or hasContentDescription(label)) and isButton)

    protected fun SemanticsNodeInteraction.assertIsTarget() = assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)

    protected companion object {
        const val T0 = 1_000_000_000L

        /** The first step's: seeding it is its first visit. */
        const val FIRST_VISIT = 1L
    }
}
