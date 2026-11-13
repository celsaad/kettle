package com.casco.kettle.shell.runner

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.casco.kettle.domain.CircuitMember
import com.casco.kettle.domain.CountIn
import com.casco.kettle.domain.RepsSetLog
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerPlan
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.WorkoutBlock
import com.casco.kettle.domain.buildSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class RunnerScreenTest : RunnerScreenHarness() {
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
    fun `effort starts empty, a pick is marked, and picking it again clears it`() {
        // Named by their own numbers, and radio buttons to a reader, which isSelectable alone wouldn't pin.
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        val effort = { value: String -> compose.onNode(hasText(value) and isSelectable() and radio) }
        show(running(plan(block(squat))))
        compose.onNodeWithText("Esforço · opcional").assertExists()
        for (value in listOf("6", "7", "8", "9", "10")) effort(value).assertIsNotSelected().assertIsTarget()
        effort("9").performClick()
        assertEquals(RunnerCommand.SetRpe(9.0), sent.last())

        show(running(plan(block(squat)), started(plan(block(squat))).after(RunnerCommand.SetRpe(9.0))))
        effort("9").assertIsSelected()
        effort("8").assertIsNotSelected()
        effort("9").performClick()
        assertEquals(RunnerCommand.SetRpe(null), sent.last())
    }

    @Test
    fun `the action row's Sets and Swap are separate 48dp targets`() {
        show(running(plan(block(squat))))
        val sets = button("Séries").assertIsTarget().getUnclippedBoundsInRoot()
        val swap = button("Trocar").assertIsTarget().getUnclippedBoundsInRoot()
        // No point is inside two targets (#263).
        assertTrue("Sets $sets overlaps Swap $swap", sets.right <= swap.left)
    }

    @Test
    fun `inside a circuit the action row has no Sets or Swap`() {
        val circuit = WorkoutBlock.Circuit(rounds = 2, members = listOf(CircuitMember("squat"), CircuitMember("plank")))
        show(running(plan(circuit)))
        button("Séries").assertDoesNotExist()
        button("Trocar").assertDoesNotExist()
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
        // The library takes it only once the set is logged with it (#410), not on the tap.
        assertEquals(emptyList<Pair<String, Double>>(), adopted)
        show(running(plan(block(squat)), started(plan(block(squat))).after(RunnerCommand.SetWeight(50.0))))
        button("Registrar série → Descanso").performClick()
        assertEquals(listOf("squat" to 50.0), adopted)
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
    fun `back closes the pad first, then asks to finish, aimed at the set it was asked on`() {
        val plan = plan(block(squat))
        val secondSet = started(plan).advanced().advanced()
        show(running(plan, secondSet))
        button("Reps concluídas: 5. Toque para inserir um valor exato.").performClick()
        back()
        button("Definir").assertDoesNotExist()
        compose.onNode(isDialog()).assertDoesNotExist()
        back()
        compose.onNodeWithText("Encerrar o treino?").assertExists()
        dialogButton("Encerrar").performClick()
        assertEquals(RunnerCommand.Finish(secondSet.snapshot.visit), sent.last())
    }

    @Test
    fun `back closes Coming up before the swap picker opens a dialog`() {
        show(running(plan(block(squat))))
        button("Pernas").performClick()
        compose.onNodeWithText("A SEGUIR").assertExists()
        back()
        compose.onNodeWithText("A SEGUIR").assertDoesNotExist()
        button("Trocar").performClick()
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
        assertTrue(sent.none { it is RunnerCommand.Finish })
    }

    @Test
    fun `an overlay closes itself when its step ends, and the body is hidden from a reader meanwhile`() {
        val plan = plan(block(hang))
        val start = started(plan)
        show(running(plan, start))
        button("Trocar").performClick()
        compose.onNodeWithText("No lugar do que resta de Suspensão").assertExists()
        // Neither the name nor the Next card's preview of it is reachable under the picker.
        assertEquals(0, compose.onAllNodesWithText("Suspensão").fetchSemanticsNodes().size)
        // The hold ends under it, and the next set, where a swap is offered too, doesn't reopen it.
        entry = running(plan, start.advanced())
        compose.waitForIdle()
        compose.onNodeWithText("TROCAR EXERCÍCIO").assertDoesNotExist()
        button("Trocar").assertExists()
    }

    @Test
    fun `Coming up closes itself on a step change too`() {
        val plan = plan(block(squat))
        val start = started(plan)
        show(running(plan, start))
        button("Pernas").performClick()
        compose.onNodeWithText("A SEGUIR").assertExists()
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
        button("Trocar").performClick()
        button("Terra").assertExists()
        compose.onNode(hasContentDescription("Buscar exercícios")).performTextInput("bulgaro")
        button("Terra").assertDoesNotExist()
        button("Búlgaro").performClick()
        assertEquals(RunnerCommand.Swap(extras[0]), sent.last())
        compose.onNodeWithText("TROCAR EXERCÍCIO").assertDoesNotExist()
    }

    // ad hoc

    @Test
    fun `an ad-hoc session parks with Add and an unconfirmed Finish`() {
        val adhoc = RunnerPlan.Runnable(null, exercises, emptyList())
        val parked = started(adhoc)
        show(running(adhoc, parked))
        compose.onNodeWithText("Nada na fila").assertExists()
        button("Encerrar").performClick()
        assertEquals(RunnerCommand.Finish(parked.snapshot.visit), sent.last())
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
        compose.onNodeWithText("Reps · série 1 de 2").assertExists()
        button("Pernas").performClick()
        compose.onNodeWithText("A SEGUIR").assertExists()
        compose.onNodeWithText("Reps · série 1 de 2").assertDoesNotExist()
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

    // #267's review: the keys became a sealed type, so each kind is pressed through to the value set.
    @Test
    fun `the pad's keys type, delete, and set the value`() {
        show(running(plan(block(squat))))
        button("Reps concluídas: 5. Toque para inserir um valor exato.").performClick()
        button("Excluir").performClick()
        button("1").performClick()
        button("2").performClick()
        button("3").performClick()
        button("Excluir").performClick()
        button("Definir").performClick()

        // 5, ⌫ → empty; 1 2 3 → 123; ⌫ → 12.
        assertEquals(RunnerCommand.SetReps(12), sent.last())
    }

    @Test
    fun `the Finish dialog closes itself when the step it was asked on ends`() {
        // #317's review: a rest ending under the dialog seeded the next set, and Finish logged it.
        val plan = plan(block(squat))
        val resting = started(plan).advanced()
        show(running(plan, resting))
        back()
        compose.onNodeWithText("Encerrar o treino?").assertExists()
        entry = running(plan, resting.advanced())
        compose.waitForIdle()
        compose.onNodeWithText("Encerrar o treino?").assertDoesNotExist()
        assertTrue(sent.none { it is RunnerCommand.Finish })
    }

    @Test
    fun `a tap on the pad's own empty space keeps it open, with what was typed`() {
        // #317's review: the sheet let taps it didn't handle fall through to the backdrop, which closed it.
        show(running(plan(block(squat))))
        button("Reps concluídas: 5. Toque para inserir um valor exato.").performClick()
        button("1").performClick()
        button("2").performClick()
        compose.onNodeWithText("12").performClick()
        button("Definir").assertExists()
        compose.onNodeWithText("12").assertExists()
    }

    // announcements

    @Test
    fun `a step's announcement is in the reader's language, a hold's its end and a rest's its configured seconds`() {
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
}
