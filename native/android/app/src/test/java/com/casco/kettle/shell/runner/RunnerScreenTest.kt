package com.casco.kettle.shell.runner

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.CircuitMember
import com.casco.kettle.domain.CountIn
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.RepsSetLog
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerPlan
import com.casco.kettle.domain.RunnerState
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.WorkoutBlock
import com.casco.kettle.domain.buildSteps
import com.casco.kettle.domain.reduceRunner
import com.casco.kettle.domain.startRunner
import com.casco.kettle.shell.theme.KettleTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The native runner's screens (#306), case for case from `session.test.tsx` and the `session-*` suites,
 * driven in `pt` so a hardcoded English string fails, with views built by `:shared`'s own engine. The
 * engine's rules are its tests; these are what the screen shows and sends.
 */
@RunWith(RobolectricTestRunner::class)
// A phone's size: the runner's screens don't scroll, as RN's don't, so on Robolectric's default
// 320x470dp the controls at the bottom are drawn off the screen and take no taps.
@Config(qualifiers = "pt-w411dp-h891dp")
class RunnerScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val squat = Exercise("squat", "Agachamento", ExerciseConfig.Reps(2, 5, null, 60.0, 90.0))
    private val plank = Exercise("plank", "Prancha", ExerciseConfig.TimedHold(2, 20.0, 30.0, 30.0))

    /** Back-to-back sets, so the step after its first is its second, where a swap is offered again. */
    private val hang = Exercise("hang", "Suspensão", ExerciseConfig.TimedHold(2, 20.0, null, 0.0))
    private val maxHold = Exercise("maxhold", "Suspensão máxima", ExerciseConfig.TimedHold(1, null, null, 30.0))
    private val fixedHold = Exercise("fixedhold", "Isometria fixa", ExerciseConfig.TimedHold(1, 30.0, null, 30.0))
    private val everyMinute = Exercise("emom", "Swing", ExerciseConfig.Emom(60.0, 5.0, 10))
    private val grinder = Exercise("amrap", "Moedor", ExerciseConfig.Amrap(600.0))
    private val row = Exercise("row", "Remo", ExerciseConfig.Cardio())
    private val burpees = Exercise("burpees", "Burpees", ExerciseConfig.Hiit(20.0, 10.0, 2))
    private val rest = Exercise("rest", "Descanso", ExerciseConfig.Rest(60.0))
    private val extras =
        listOf("Búlgaro", "Terra", "Supino", "Remada", "Flexão", "Barra", "Rosca", "Elevação").mapIndexed {
            index,
            name,
            ->
            Exercise("extra$index", name, ExerciseConfig.Reps(3, 8, restSec = 60.0))
        }
    private val exercises =
        listOf(squat, plank, hang, maxHold, fixedHold, everyMinute, grinder, row, burpees, rest) + extras

    private var entry by mutableStateOf<RunnerEntry>(RunnerEntry.Loading)
    private var prior by mutableStateOf<List<Session>>(emptyList())
    private var soundsOn = true
    private val sent = mutableListOf<RunnerCommand>()
    private var left = 0
    private var done = 0
    private var startedAnyway = 0
    private val adopted = mutableListOf<Pair<String, Double>>()

    private fun plan(vararg blocks: WorkoutBlock): RunnerPlan.Runnable {
        val workout = Workout("w", "Pernas", blocks.toList())
        return RunnerPlan.Runnable(workout, exercises, buildSteps(workout, exercises))
    }

    private fun block(exercise: Exercise) = WorkoutBlock.ExerciseBlock(exercise.id)

    private fun started(plan: RunnerPlan.Runnable) = startRunner(plan, null, now = T0).state

    private fun RunnerState.after(vararg commands: RunnerCommand): RunnerState =
        commands.fold(this) { state, command -> reduceRunner(state, command, T0).state }

    /** The step on screen logged or skipped, aimed at it as a tap would be. */
    private fun RunnerState.advanced(): RunnerState = after(RunnerCommand.Advance(snapshot.visit))

    private fun running(
        plan: RunnerPlan.Runnable,
        state: RunnerState = started(plan),
    ) = RunnerEntry.Attach(RunnerView.Running(plan, state.snapshot, state.session))

    private var shown = false
    private var fontScale by mutableStateOf(1f)

    /** The first call sets the content; a later one only changes what it shows, as a new view would. */
    private fun show(next: RunnerEntry) {
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

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun button(label: String) = compose.onNode((hasText(label) or hasContentDescription(label)) and isButton)

    private fun dialogButton(label: String) = compose.onNode(hasText(label) and hasAnyAncestor(isDialog()))

    // before the first step

    @Test
    fun `a rest week says so, and Close leaves`() {
        show(RunnerEntry.Before(RunnerPlan.RestWeek))
        compose.onNodeWithText("Dia de descanso").assertExists()
        button("Fechar").performClick()
        assertEquals(1, left)
    }

    @Test
    fun `a workout with nothing to run says so before any count-in`() {
        show(RunnerEntry.Before(RunnerPlan.NoSteps(Workout("w", "Pernas", emptyList()))))
        compose.onNodeWithText("Nada para executar").assertExists()
        compose.onNodeWithText("Pernas", substring = true).assertExists()
        button("Fechar").performClick()
        assertEquals(1, left)
    }

    @Test
    fun `a workout too long to run offers Start anyway or Close`() {
        show(RunnerEntry.Before(RunnerPlan.TooLong(plan(block(squat)))))
        compose.onNodeWithText("Longo demais para fazer inteiro").assertExists()
        button("Começar mesmo assim").performClick()
        button("Fechar").performClick()
        assertEquals(1, startedAnyway)
        assertEquals(1, left)
    }

    @Test
    fun `a missing workout draws nothing`() {
        show(RunnerEntry.Before(RunnerPlan.Missing))
        compose.onNodeWithText("Fechar").assertDoesNotExist()
    }

    @Test
    fun `the count-in shows the name and the first-session hint with no history and sounds on`() {
        show(RunnerEntry.Attach(RunnerView.CountingIn(plan(block(squat)), null, CountIn(T0))))
        compose.onNodeWithText("PREPARE-SE").assertExists()
        compose.onNodeWithText("Pernas").assertExists()
        compose.onNodeWithText("3").assertExists()
        compose.onNodeWithText("Pode largar o celular", substring = true).assertExists()
    }

    @Test
    fun `the first-session hint is gone once anything is logged, or with sounds off`() {
        prior = listOf(finished("old", 50.0))
        show(RunnerEntry.Attach(RunnerView.CountingIn(plan(block(squat)), null, CountIn(T0))))
        compose.onNodeWithText("Pode largar o celular", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the first-session hint is gone with sounds off`() {
        soundsOn = false
        show(RunnerEntry.Attach(RunnerView.CountingIn(plan(block(squat)), null, CountIn(T0))))
        compose.onNodeWithText("Pode largar o celular", substring = true).assertDoesNotExist()
    }

    @Test
    fun `back during the count-in leaves silently`() {
        show(RunnerEntry.Attach(RunnerView.CountingIn(plan(block(squat)), null, CountIn(T0))))
        back()
        assertEquals(1, left)
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun `back on a screen before the count-in leaves`() {
        show(RunnerEntry.Before(RunnerPlan.RestWeek))
        back()
        assertEquals(1, left)
    }

    // the step screens

    @Test
    fun `a reps step shows its set and target, and its steppers send the reps`() {
        show(running(plan(block(squat))))
        // Twice: the Next card previews its second set.
        assertEquals(2, compose.onAllNodesWithText("Agachamento").fetchSemanticsNodes().size)
        compose.onNodeWithText("Série 1 de 2 · meta 5").assertExists()
        button("Aumentar Repetições").performClick()
        assertEquals(RunnerCommand.SetReps(6), sent.last())
        button("Registrar série → Descanso").performClick()
        assertEquals(RunnerCommand.Advance(FIRST_VISIT), sent.last())
    }

    @Test
    fun `RPE marks the selected option`() {
        show(running(plan(block(squat))))
        button("RPE 8").assertIsSelected()
        button("RPE 9").assertIsNotSelected().performClick()
        assertEquals(RunnerCommand.SetRpe(9.0), sent.last())
    }

    @Test
    fun `the set row's controls are 48dp targets, and − is disabled at the floor rather than hidden`() {
        show(running(plan(block(squat))))
        button("Diminuir séries").assertIsEnabled().assertHeightIsAtLeast(48.dp)
        button("Aumentar séries").assertHeightIsAtLeast(48.dp)
        button("TROCAR").assertHeightIsAtLeast(48.dp)
        val floor = started(plan(block(squat))).after(RunnerCommand.DropSet)
        show(running(plan(block(squat)), floor))
        button("Diminuir séries").assertIsNotEnabled()
    }

    @Test
    fun `inside a circuit the set row has no −, + or swap`() {
        val circuit = WorkoutBlock.Circuit(rounds = 2, members = listOf(CircuitMember("squat"), CircuitMember("plank")))
        show(running(plan(circuit)))
        button("Aumentar séries").assertDoesNotExist()
        button("TROCAR").assertDoesNotExist()
    }

    @Test
    fun `the circuit crumb is one node`() {
        val circuit = WorkoutBlock.Circuit(rounds = 2, members = listOf(CircuitMember("squat"), CircuitMember("plank")))
        show(running(plan(circuit)))
        compose.onNode(hasContentDescription("Circuito, rodada 1 de 2, exercício 1 de 2")).assertExists()
    }

    @Test
    fun `last time shows and adopts its load, with the PR badge when this set beats it`() {
        prior = listOf(finished("old", 50.0))
        show(running(plan(block(squat))))
        compose.onNodeWithText("Da última vez · 50 kg × 5 repetições").assertExists()
        // The target of 60 kg beats a best of 50.
        compose.onNodeWithText("RECORDE").assertExists()
        button("Usar a carga da última vez, 50 quilogramas, e salvar como novo alvo").performClick()
        assertEquals(RunnerCommand.SetWeight(50.0), sent.last())
        assertEquals(listOf("squat" to 50.0), adopted)
    }

    @Test
    fun `a hold has Pause and Done, and its Prev is labelled`() {
        show(running(plan(block(plank))))
        assertEquals(2, compose.onAllNodesWithText("Prancha").fetchSemanticsNodes().size)
        button("Pausar").performClick()
        button("Etapa anterior").performClick()
        button("Série concluída →").performClick()
        assertEquals(listOf(RunnerCommand.TogglePause, RunnerCommand.Prev, RunnerCommand.Advance(FIRST_VISIT)), sent)
    }

    @Test
    fun `a countdown interval skips quietly, and a count-up one is done with the filled button`() {
        show(running(plan(block(burpees))))
        button("Pular →").assertExists()
        show(running(plan(block(row))))
        button("Concluído →").assertExists()
    }

    @Test
    fun `a rest has +30s and Skip, and no Pause`() {
        val resting = started(plan(block(squat))).advanced()
        show(running(plan(block(squat)), resting))
        compose.onNodeWithText("de 1:30 restantes").assertExists()
        button("Pausar").assertDoesNotExist()
        button("+30s").performClick()
        assertEquals(RunnerCommand.AddRestSeconds(30.0), sent.last())
        button("Pular descanso →").performClick()
        assertEquals(RunnerCommand.Advance(resting.snapshot.visit), sent.last())
    }

    // the number pad

    @Test
    fun `the pad replaces the value, and Set sends it and closes`() {
        show(running(plan(block(squat))))
        button("Reps concluídas: 5. Toque para inserir um valor exato.").performClick()
        button("1").performClick()
        button("2").performClick()
        compose.onNodeWithText("12").assertExists()
        button("Definir").performClick()
        assertEquals(RunnerCommand.SetReps(12), sent.last())
        button("Definir").assertDoesNotExist()
    }

    @Test
    fun `the load pad takes a decimal, in display units`() {
        show(running(plan(block(squat))))
        button("Carga: 60 quilogramas. Toque para inserir um valor exato.").performClick()
        listOf("6", "2", ".", "5").forEach { button(it).performClick() }
        button("Definir").performClick()
        assertEquals(RunnerCommand.SetWeight(62.5), sent.last())
    }

    // overlays and back

    @Test
    fun `back closes the pad first, then asks to finish`() {
        show(running(plan(block(squat))))
        button("Reps concluídas: 5. Toque para inserir um valor exato.").performClick()
        back()
        button("Definir").assertDoesNotExist()
        compose.onNode(isDialog()).assertDoesNotExist()
        back()
        compose.onNodeWithText("Encerrar o treino?").assertExists()
        dialogButton("Encerrar").performClick()
        assertEquals(RunnerCommand.Finish, sent.last())
    }

    @Test
    fun `back closes Coming up before the swap picker opens a dialog`() {
        show(running(plan(block(squat))))
        button("Pernas").performClick()
        compose.onNodeWithText("A SEGUIR").assertExists()
        back()
        compose.onNodeWithText("A SEGUIR").assertDoesNotExist()
        button("TROCAR").performClick()
        compose.onNodeWithText("TROCAR EXERCÍCIO").assertExists()
        back()
        compose.onNodeWithText("TROCAR EXERCÍCIO").assertDoesNotExist()
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun `Finish in the header asks first, and Cancel keeps the session`() {
        show(running(plan(block(squat))))
        button("Encerrar").performClick()
        dialogButton("Cancelar").performClick()
        assertTrue(sent.none { it == RunnerCommand.Finish })
    }

    @Test
    fun `an overlay closes itself when its step ends, and the body is hidden from a reader meanwhile`() {
        val plan = plan(block(hang))
        val start = started(plan)
        show(running(plan, start))
        button("TROCAR").performClick()
        compose.onNodeWithText("No lugar do que resta de Suspensão").assertExists()
        // Neither the name nor the Next card's preview of it is reachable under the picker.
        assertEquals(0, compose.onAllNodesWithText("Suspensão").fetchSemanticsNodes().size)
        // The hold ends under it, and the next set, where a swap is offered too, doesn't reopen it.
        entry = running(plan, start.advanced())
        compose.waitForIdle()
        compose.onNodeWithText("TROCAR EXERCÍCIO").assertDoesNotExist()
        button("TROCAR").assertExists()
    }

    @Test
    fun `Coming up and the pad close themselves on a step change too`() {
        val plan = plan(block(squat))
        val start = started(plan)
        show(running(plan, start))
        button("Pernas").performClick()
        entry = running(plan, start.advanced())
        compose.waitForIdle()
        compose.onNodeWithText("A SEGUIR").assertDoesNotExist()
        compose.onNodeWithText("de 1:30 restantes").assertExists()
    }

    @Test
    fun `Coming up shows the step on screen and ends the workout`() {
        show(running(plan(block(squat))))
        button("Pernas").performClick()
        compose.onNodeWithText("AGORA").assertExists()
        compose.onNodeWithText("FIM DO TREINO").assertExists()
        button("Fechar").performClick()
        compose.onNodeWithText("A SEGUIR").assertDoesNotExist()
    }

    @Test
    fun `the swap picker searches above eight candidates, ignoring accents, and sends the pick`() {
        show(running(plan(block(squat))))
        button("TROCAR").performClick()
        compose.onNode(hasContentDescription("Buscar exercícios")).performTextInput("bulgaro")
        button("Búlgaro").performClick()
        assertEquals(RunnerCommand.Swap(extras[0]), sent.last())
        compose.onNodeWithText("TROCAR EXERCÍCIO").assertDoesNotExist()
    }

    // ad hoc

    @Test
    fun `an ad-hoc session parks with Add and an unconfirmed Finish`() {
        val adhoc = RunnerPlan.Runnable(null, exercises, emptyList())
        show(running(adhoc))
        compose.onNodeWithText("Nada na fila").assertExists()
        button("Encerrar").performClick()
        assertEquals(RunnerCommand.Finish, sent.last())
        compose.onNode(isDialog()).assertDoesNotExist()
        button("Adicionar exercício").performClick()
        compose.onNodeWithText("ADICIONAR EXERCÍCIO").assertExists()
        compose.onNode(hasContentDescription("Buscar exercícios")).performTextInput("Remo")
        button("Remo").performClick()
        assertEquals(RunnerCommand.AddExercise(row), sent.last())
    }

    @Test
    fun `an ad-hoc session's add picker never offers rest`() {
        show(running(RunnerPlan.Runnable(null, exercises, emptyList())))
        button("Adicionar exercício").performClick()
        compose.onNode(hasContentDescription("Buscar exercícios")).performTextInput("Descanso")
        button("Descanso").assertDoesNotExist()
    }

    // completion

    @Test
    fun `completion names the workout, lists what it beat with the 1RM note once, and Done hands back`() {
        prior = listOf(finished("old", 50.0))
        val session = finished("s", 60.0)
        show(RunnerEntry.Complete(RunnerView.Completed(plan(block(squat)), session, FinishBackupState.OK)))
        compose.onNodeWithText("Treino concluído").assertExists()
        compose.onNodeWithText("Pernas").assertExists()
        compose.onNodeWithText("Mais pesado até hoje · 60 kg × 5 repetições").assertExists()
        compose.onNodeWithText("1RM estimado a partir da sua melhor série, pela fórmula de Epley.").assertExists()
        compose.onNodeWithText("Veja em Configurações", substring = true).assertDoesNotExist()
        button("Concluir").performClick()
        assertEquals(1, done)
    }

    @Test
    fun `completion warns in one line when the backup failed`() {
        show(
            RunnerEntry.Complete(
                RunnerView.Completed(plan(block(squat)), finished("s", 60.0), FinishBackupState.FAILED),
            ),
        )
        compose.onNodeWithText("Veja em Configurações", substring = true).assertExists()
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun `completion of an ad-hoc session uses the stand-in name, and back is Done`() {
        show(
            RunnerEntry.Complete(
                RunnerView.Completed(
                    RunnerPlan.Runnable(null, exercises, emptyList()),
                    finished("s", 60.0),
                    FinishBackupState.NONE,
                ),
            ),
        )
        compose.onNodeWithText("Sessão avulsa").assertExists()
        back()
        assertEquals(1, done)
    }

    // what the review found unpinned

    private val isProgressBar = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    @Test
    fun `block progress draws nothing below two blocks, and an ad-hoc session counts what was added`() {
        show(running(plan(block(squat))))
        compose.onNode(isProgressBar).assertDoesNotExist()
        show(running(plan(block(squat), block(plank))))
        compose.onNode(isProgressBar).assertExists()
        val adhoc = RunnerPlan.Runnable(null, exercises, emptyList())
        val two = started(adhoc).after(RunnerCommand.AddExercise(squat), RunnerCommand.AddExercise(plank))
        show(running(adhoc, two))
        compose.onNode(isProgressBar).assertExists()
    }

    @Test
    fun `the Next card's drawing goes before the card does as text grows`() {
        val drawing = hasContentDescription("Prancha: antebraços", substring = true)
        show(running(plan(block(plank))))
        compose.onNode(drawing).assertExists()
        fontScale = 1.3f
        show(running(plan(block(plank))))
        compose.onNode(drawing).assertDoesNotExist()
        compose.onNodeWithText("PRÓXIMO").assertExists()
        fontScale = 1.6f
        show(running(plan(block(plank))))
        compose.onNodeWithText("PRÓXIMO").assertDoesNotExist()
    }

    @Test
    fun `a hold's bar marks the minimum only on a real range, and a max-effort hold has no bar`() {
        show(running(plan(block(plank))))
        compose.onNode(hasTestTag(BAR_TAG)).assertExists()
        compose.onNode(hasTestTag(MARKER_TAG), useUnmergedTree = true).assertExists()
        show(running(plan(block(fixedHold))))
        compose.onNode(hasTestTag(BAR_TAG)).assertExists()
        compose.onNode(hasTestTag(MARKER_TAG), useUnmergedTree = true).assertDoesNotExist()
        show(running(plan(block(maxHold))))
        compose.onNode(hasTestTag(BAR_TAG)).assertDoesNotExist()
        compose.onNodeWithText("sem meta · contando para cima").assertExists()
    }

    @Test
    fun `an EMOM says its minute, and its stepper sends the minute's reps`() {
        show(running(plan(block(everyMinute))))
        compose.onNodeWithText("EMOM").assertExists()
        compose.onNodeWithText("Minuto 1 de 5").assertExists()
        compose.onNodeWithText("REPS NESTE MINUTO").assertExists()
        button("Aumentar REPS NESTE MINUTO").performClick()
        assertEquals(RunnerCommand.SetReps(11), sent.last())
    }

    @Test
    fun `an AMRAP has rounds and extra-reps steppers`() {
        show(running(plan(block(grinder))))
        button("Aumentar RODADAS").performClick()
        assertEquals(RunnerCommand.SetRounds(1), sent.last())
        button("Aumentar +REPS").performClick()
        assertEquals(RunnerCommand.SetExtraReps(1), sent.last())
    }

    @Test
    fun `Coming up's NOW row counts a rest down, a hold up, and shows no clock for reps`() {
        val reps = plan(block(squat))
        show(running(reps, started(reps).advanced()))
        button("Pernas").performClick()
        compose.onNodeWithText("AGORA · DESCANSO").assertExists()
        // The NOW row's alone: the rest screen's own clock is hidden from a reader under the sheet.
        compose.onNodeWithText("1:30").assertExists()
        button("Fechar").performClick()
        show(running(plan(block(plank))))
        button("Pernas").performClick()
        compose.onNodeWithText("0:00").assertExists()
        button("Fechar").performClick()
        show(running(reps))
        button("Pernas").performClick()
        compose.onNodeWithText("AGORA").assertExists()
        compose.onNodeWithText(":", substring = true).assertDoesNotExist()
    }

    @Test
    fun `Coming up closes from its backdrop`() {
        show(running(plan(block(squat))))
        button("Pernas").performClick()
        compose.onRoot().performTouchInput { click(Offset(centerX, 4f)) }
        compose.onNodeWithText("A SEGUIR").assertDoesNotExist()
    }

    // from #317's review

    @Test
    fun `the pad closes itself when its step ends`() {
        val plan = plan(block(squat))
        val start = started(plan)
        show(running(plan, start))
        button("Reps concluídas: 5. Toque para inserir um valor exato.").performClick()
        button("Definir").assertExists()
        entry = running(plan, start.advanced())
        compose.waitForIdle()
        button("Definir").assertDoesNotExist()
    }

    @Test
    fun `the body is hidden from a reader under Coming up`() {
        show(running(plan(block(squat))))
        button("Pernas").performClick()
        compose.onNodeWithText("A SEGUIR").assertExists()
        compose.onNodeWithText("Série 1 de 2 · meta 5").assertDoesNotExist()
    }

    @Test
    fun `an ad-hoc session's Coming up has no end`() {
        val adhoc = RunnerPlan.Runnable(null, exercises, emptyList())
        show(running(adhoc, started(adhoc).after(RunnerCommand.AddExercise(squat))))
        button("Sessão avulsa").performClick()
        compose.onNodeWithText("A SEGUIR").assertExists()
        compose.onNodeWithText("FIM DO TREINO").assertDoesNotExist()
    }

    @Test
    fun `back closes the add picker before asking to finish`() {
        val adhoc = RunnerPlan.Runnable(null, exercises, emptyList())
        show(running(adhoc, started(adhoc).after(RunnerCommand.AddExercise(squat))))
        button("ADICIONAR").performClick()
        compose.onNodeWithText("ADICIONAR EXERCÍCIO").assertExists()
        back()
        compose.onNodeWithText("ADICIONAR EXERCÍCIO").assertDoesNotExist()
        compose.onNode(isDialog()).assertDoesNotExist()
        back()
        compose.onNodeWithText("Encerrar o treino?").assertExists()
    }

    @Test
    fun `a digit key is named by its own text, and only backspace carries a label`() {
        show(running(plan(block(squat))))
        button("Reps concluídas: 5. Toque para inserir um valor exato.").performClick()
        compose.onNode(hasText("1") and isButton).assert(
            SemanticsMatcher("no label") { SemanticsProperties.ContentDescription !in it.config },
        )
        compose.onNode(hasContentDescription("Excluir") and isButton).assertExists()
    }

    // announcements

    @Test
    fun `each step announces once in the reader's language, a hold its end and a rest its configured seconds`() {
        val resources = compose.activity.resources
        val hold = buildSteps(Workout("w", "W", listOf(block(plank))), exercises).first() as RunnerStep.Hold
        assertEquals("Prancha, série 1 de 2, segure 30 segundos", resources.announcement(hold))
        val resting = started(plan(block(squat))).advanced().after(RunnerCommand.AddRestSeconds(30.0))
        assertEquals("Descanso, 90 segundos", resources.announcement(resting.snapshot.step!!))
    }

    private fun finished(
        id: String,
        kg: Double,
    ) = Session(
        1,
        id,
        "w",
        null,
        null,
        null,
        "2026-08-01T10:00:00.000Z",
        "2026-08-01T10:30:00.000Z",
        listOf(SessionEntry.Reps("squat", listOf(RepsSetLog(5, kg, restTakenSec = 90.0)))),
    )

    private companion object {
        const val T0 = 1_000_000_000L

        /** The first step's: seeding it is its first visit. */
        const val FIRST_VISIT = 1L
    }
}
