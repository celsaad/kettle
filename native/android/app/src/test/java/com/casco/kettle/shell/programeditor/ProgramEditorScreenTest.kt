package com.casco.kettle.shell.programeditor

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.OverrideValue
import com.casco.kettle.domain.Program
import com.casco.kettle.domain.ProgramOverride
import com.casco.kettle.domain.ProgramWeek
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * The program editor (#243), case for case from RN's `program-editor.test.tsx` and
 * `program-override-editor.test.tsx`, on the harness recipe (#193): in `pt`, so a hardcoded English
 * string fails, with the files faked behind `StoredFiles`. Each asserts on the library written.
 *
 * The rules under the screen are `:shared`'s (`ProgramEditingTest`, `OverrideEditingTest`); these
 * cover the wiring. A screen tall enough that the `LazyColumn` composes every card, so a second
 * week's controls are found by index rather than scrolled to.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-w400dp-h4000dp")
class ProgramEditorScreenTest : ProgramEditorHarness() {
    @Test
    fun `refuses a blank name`() {
        val files = show()

        press("Salvar")

        named("O nome é obrigatório.").assertExists()
        assertEquals(emptyList<Library>(), files.written)
        assertEquals(0, closed)
    }

    // A zero-week program is a dead end downstream: Home has no next week to offer.
    @Test
    fun `refuses a program with no weeks`() {
        val files = draftWith(0)

        press("Salvar")

        named("Adicione pelo menos uma semana.").assertExists()
        assertEquals(emptyList<Library>(), files.written)
    }

    @Test
    fun `refuses the same week twice when neither has a day`() {
        val files = draftWith(1)
        // A second day, its label cleared: two days of week 1 with none, the `week + day` address colliding.
        press("+ Adicionar dia")
        type("Rótulo do dia", "")

        press("Salvar")

        named(
            "A semana 1 está repetida — defina um dia diferente para uma delas ou remova a duplicada.",
        ).assertExists()
        assertEquals(emptyList<Library>(), files.written)
    }

    @Test
    fun `allows the same week number when the days differ`() {
        val files = draftWith(1)
        type("Rótulo do dia", "Monday")
        press("+ Adicionar dia")
        type("Rótulo do dia", "Thursday")

        press("Salvar")

        assertEquals(
            listOf(
                ProgramWeek.Training(1, "Monday", "push-day"),
                ProgramWeek.Training(1, "Thursday", "push-day"),
            ),
            files.savedWeeks(),
        )
        assertEquals(1, closed)
    }

    @Test
    fun `refuses a name that yields no id`() {
        val files = draftWith(1, "🏋️")

        press("Salvar")

        named("Não foi possível gerar um id a partir desse nome.").assertExists()
        assertEquals(emptyList<Library>(), files.written)
    }

    // Sessions record `programId`, so re-deriving it on a rename would orphan their history.
    @Test
    fun `keeps an existing id when the program is renamed`() {
        val files = show("base", Files(programs = listOf(base())))

        type("Nome", "Winter block")
        press("Salvar")

        val saved =
            files.written
                .last()
                .programs
                .single()
        assertEquals("base", saved.id)
        assertEquals("Winter block", saved.name)
    }

    @Test
    fun `says why a write failed, and stays open`() {
        val files = Files().apply { failure = IOException("disk full") }
        show(files = files)
        type("Nome", "Base")
        press("+ Adicionar semana")

        press("Salvar")

        named("Não foi possível salvar: disk full").assertExists()
        assertEquals(0, closed)
    }

    // RN takes a while to dismiss the sheet; a second tap in between must not write or close again.
    @Test
    fun `writes and closes once, however often Save is tapped`() {
        val files = draftWith(1)

        press("Salvar")
        press("Salvar")

        assertEquals(1, files.written.size)
        assertEquals(1, closed)
    }

    // The window RN's slow dismissal leaves: a Delete confirmed while the Save's write is in flight.
    @Test
    fun `writes and closes once when Delete is confirmed while a Save is landing`() {
        val io = Steps()
        val files = show("base", Files(programs = listOf(base())), io)

        press("Salvar")
        press("Excluir programa")
        compose.pressDialogButton("Excluir")
        repeat(SETTLE_ROUNDS) {
            io.run()
            compose.waitForIdle()
        }

        assertEquals(1, files.written.size)
        assertEquals(1, closed)
        assertEquals(0, deleted)
    }

    // AGENTS.md's 44dp floor, which RN's 28px week stepper and 26px rounds stepper fell under.
    @Test
    fun `gives every control on a week card a 44dp target`() {
        show("base", Files(programs = listOf(base(exerciseOverride("pull-ups", "sets" to 5.0)))))
        press("+ Adicionar alteração")
        press("finisher")

        for (label in listOf(
            "Ações da semana 1",
            "Mover Dia 1 para cima",
            "Mover Dia 1 para baixo",
            "Diminuir Rodadas",
            "Remover alteração",
        )) {
            compose
                .onAllNodes(
                    labelled(label) and isControl,
                )[0]
                .assertWidthIsAtLeast(44.dp)
                .assertHeightIsAtLeast(44.dp)
        }
        for (label in listOf("Dia de descanso", "Push day", "Pull-ups: sets → 5", "Remover dia", "Semana 1")) {
            compose.onAllNodes(labelled(label) and isControl)[0].assertHeightIsAtLeast(44.dp)
        }
    }

    @Test
    fun `defaults a new week to the first workout`() {
        draftWith(1)

        named("Push day").assertExists()
    }

    // Three forms have shipped drawing a key path where a label belonged, so this checks the class.
    @Test
    fun `renders no i18next key paths on a week card`() {
        draftWith(1)

        compose.onAllNodes(hasTextMatching(KEY_PATH)).assertCountEquals(0)
    }

    @Test
    fun `actually translates its week-card labels`() {
        draftWith(1)

        named("Alterações para este dia").assertExists()
        named("Rótulo do dia").assertExists()
        named("Segue de cima para baixo").assertExists()
        absent("Changes for this day")
    }

    @Test
    fun `changes a week to another workout`() {
        val files = draftWith(1)

        press("Push day")
        press("Pull day")
        press("Salvar")

        assertEquals("pull-day", (files.savedWeeks().single() as ProgramWeek.Training).workoutId)
    }

    @Test
    fun `announces the workout picker's state, and the chosen workout's`() {
        draftWith(1)

        press("Push day")

        compose
            .onAllNodes(hasText("Push day") and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .assertCountEquals(1)
        compose
            .onAllNodes(hasText("Pull day") and SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
            .assertCountEquals(1)
    }

    @Test
    fun `explains itself instead of offering an unusable add button`() {
        show(files = Files(workouts = emptyList()))

        compose.onNode(hasText("+ Adicionar semana") and isControl).assertIsNotEnabled()
        named("Nenhum treino ainda — crie um em Treinos primeiro e volte para adicionar semanas aqui.").assertExists()
        named("0 semanas").assertExists()
    }

    @Test
    fun `deletes a program once confirmed, past the detail sheet`() {
        val files = show("base", Files(programs = listOf(base())))

        press("Excluir programa")
        assertEquals(emptyList<Library>(), files.written)
        compose.pressDialogButton("Excluir")

        assertEquals(emptyList<Program>(), files.written.last().programs)
        // Not back onto Program detail, which would find nothing at the deleted id.
        assertEquals(1, deleted)
        assertEquals(0, closed)
    }

    @Test
    fun `deletes nothing when the confirm is cancelled`() {
        val files = show("base", Files(programs = listOf(base())))

        press("Excluir programa")
        compose.pressDialogButton("Cancelar")

        assertEquals(emptyList<Library>(), files.written)
        assertEquals(0, deleted)
    }

    @Test
    fun `offers no delete for a new program`() {
        show()

        absent("Excluir programa")
    }

    // rest days

    @Test
    fun `hides the workout picker and the overrides editor once a week is rest`() {
        draftWith(1)
        named("Push day").assertExists()

        press("Dia de descanso")

        absent("Push day")
        absent("Alterações para este dia")
        // The label and notes mean the same on either kind of day.
        named("Rótulo do dia").assertExists()
        named("Notas · opcional").assertExists()
    }

    @Test
    fun `persists a rest week with no workout on it`() {
        val files = draftWith(2)

        // The new week is the one open.
        press("Dia de descanso")
        press("Salvar")

        assertEquals(
            listOf(ProgramWeek.Training(1, workoutId = "push-day"), ProgramWeek.RestDay(2)),
            files.savedWeeks(),
        )
    }

    @Test
    fun `refuses a program of nothing but rest days, which could never queue anything`() {
        val files = draftWith(1)

        press("Dia de descanso")
        press("Salvar")

        named("Pelo menos uma semana precisa ter um treino.").assertExists()
        assertEquals(emptyList<Library>(), files.written)
    }

    @Test
    fun `gives a week its workout back when rest is switched off again`() {
        val files = draftWith(1)

        press("Dia de descanso")
        press("Treino")
        press("Salvar")

        assertEquals("push-day", (files.savedWeeks().single() as ProgramWeek.Training).workoutId)
    }

    /** Two choices, the one in force selected: what a screen reader reads back. */
    @Test
    fun `announces what the day runs as a choice, with its state`() {
        draftWith(1)
        val rest = hasText("Dia de descanso") and isControl
        val workout = hasText("Treino") and isControl

        compose.onNode(rest).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
        compose.onNode(workout).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        press("Dia de descanso")
        compose.onNode(rest).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    }

    @Test
    fun `explains itself when the chosen workout has nothing overridable`() {
        show("base", Files(programs = listOf(base(workoutId = "empty"))))

        named("Este treino ainda não tem nada para alterar em um dia.").assertExists()
        compose.onNode(hasText("+ Adicionar alteração") and isControl).assertIsNotEnabled()
    }

    // With no workout resolved there is nothing to explain yet, only something not done: no hint.
    @Test
    fun `offers no override, and no hint, while the week's workout resolves to nothing`() {
        show("base", Files(programs = listOf(base(workoutId = "gone"))))

        compose.onNode(hasText("+ Adicionar alteração") and isControl).assertIsNotEnabled()
        absent("Este treino ainda não tem nada para alterar em um dia.")
    }

    // Dips is eligible through the circuit; the circuit is offered by the id an override names it by.
    @Test
    fun `offers only the exercises and id-tagged circuits the workout contains`() {
        show("base", Files(programs = listOf(base())))

        press("+ Adicionar alteração")

        named("Pull-ups").assertExists()
        named("Dips").assertExists()
        named("finisher").assertExists()
        absent("Rows")
    }

    @Test
    fun `labels config fields in the user language, not with their keys`() {
        show("base", Files(programs = listOf(base())))

        press("+ Adicionar alteração")
        press("Pull-ups")

        named("Séries").assertExists()
        compose.onAllNodes(hasTextMatching(KEY_PATH)).assertCountEquals(0)
    }

    // overrides: what they write

    // A patch, not the whole config: writing every field would freeze the week against later edits.
    @Test
    fun `emits only the changed key, in the snake_case the YAML uses`() {
        val files = show("base", Files(programs = listOf(base())))

        press("+ Adicionar alteração")
        press("Pull-ups")
        type("Séries", "5")
        press("Adicionar alteração")
        press("Salvar")

        assertEquals(listOf(exerciseOverride("pull-ups", "sets" to 5.0)), files.savedOverrides())
    }

    @Test
    fun `emits a block override keyed by the circuit id`() {
        val files = show("base", Files(programs = listOf(base())))

        press("+ Adicionar alteração")
        press("finisher")
        press("Aumentar Rodadas")
        press("Adicionar alteração")
        press("Salvar")

        assertEquals(
            listOf(ProgramOverride.BlockOverride("finisher", mapOf("rounds" to OverrideValue.Numeric(4.0)))),
            files.savedOverrides(),
        )
    }

    // From base + patch, or reopening shows the old numbers and a Save quietly discards the patch.
    @Test
    fun `seeds an edit from the effective value, and replaces in place`() {
        val files = show("base", Files(programs = listOf(base(exerciseOverride("pull-ups", "sets" to 5.0)))))

        press("Pull-ups: sets → 5")
        named("Salvar alteração").assertExists()
        compose.onNode(hasContentDescription("Séries") and hasText("5")).assertExists()
        type("Séries", "6")
        press("Salvar alteração")
        press("Salvar")

        assertEquals(listOf(exerciseOverride("pull-ups", "sets" to 6.0)), files.savedOverrides())
    }

    @Test
    fun `removes an override`() {
        val files = show("base", Files(programs = listOf(base(exerciseOverride("pull-ups", "sets" to 5.0)))))

        press("Remover alteração")
        press("Salvar")

        assertEquals(null, (files.savedWeeks().single() as ProgramWeek.Training).overrides)
    }

    // Reachable by importing a library that drops the exercise: the row has nothing to seed from.
    @Test
    fun `will not open an override whose target has gone`() {
        show("base", Files(programs = listOf(base(exerciseOverride("ghost", "sets" to 5.0)))))

        compose.onNode(hasText("ghost: sets → 5") and isControl).assertIsNotEnabled()
        named("não aplicado — nada nesta semana corresponde").assertExists()
    }

    // overrides: validation

    // The merge now refuses an invalid patch and keeps the base, so unvalidated it would do nothing.
    @Test
    fun `refuses to emit an override the schema would reject, and says why`() {
        val files = show("base", Files(programs = listOf(base())))

        press("+ Adicionar alteração")
        press("Pull-ups")
        type("Séries", "0")
        press("Adicionar alteração")

        named("Séries precisa ser pelo menos 1.").assertExists()
        press("Salvar")
        assertEquals(null, files.savedOverrides())
    }

    @Test
    fun `refuses a count past the ceiling too`() {
        show("base", Files(programs = listOf(base())))

        press("+ Adicionar alteração")
        press("Pull-ups")
        type("Séries", "501")
        press("Adicionar alteração")

        named("Séries pode ser no máximo 500.").assertExists()
    }

    @Test
    fun `adds alongside an existing override rather than replacing it`() {
        val existing = exerciseOverride("pull-ups", "sets" to 5.0)
        val files = show("base", Files(programs = listOf(base(existing))))

        press("+ Adicionar alteração")
        press("Dips")
        type("Séries", "2")
        press("Adicionar alteração")
        press("Salvar")

        assertEquals(listOf(existing, exerciseOverride("dips", "sets" to 2.0)), files.savedOverrides())
    }

    @Test
    fun `clears the message once the field is edited again, and then saves`() {
        val files = show("base", Files(programs = listOf(base())))

        press("+ Adicionar alteração")
        press("Pull-ups")
        type("Séries", "0")
        press("Adicionar alteração")
        type("Séries", "5")

        absent("Séries precisa ser pelo menos 1.")
        press("Adicionar alteração")
        press("Salvar")
        assertEquals(listOf(exerciseOverride("pull-ups", "sets" to 5.0)), files.savedOverrides())
    }

    @Test
    fun `refuses a negative circuit rest rather than saving one the merge will drop`() {
        show("base", Files(programs = listOf(base())))

        press("+ Adicionar alteração")
        press("finisher")
        type("Descanso/exercício (seg)", "-5")
        press("Adicionar alteração")

        named("Descanso/exercício (seg) precisa ser pelo menos 0.").assertExists()
    }

    // The rows stay up behind the panel, so a message must not outlive the target it named.
    @Test
    fun `drops a stale error when the panel moves to another target`() {
        val overrides = arrayOf(exerciseOverride("pull-ups", "sets" to 5.0), exerciseOverride("dips", "sets" to 3.0))
        show("base", Files(programs = listOf(base(*overrides))))

        press("Pull-ups: sets → 5")
        type("Séries", "0")
        press("Salvar alteração")
        named("Séries precisa ser pelo menos 1.").assertExists()
        // Through Cancel, since the rows are inert while the panel is open.
        press("Cancelar")
        press("Dips: sets → 3")

        absent("Séries precisa ser pelo menos 1.")
    }

    // Both of a row's controls change the list the open edit points into.
    @Test
    fun `holds the rows still while one is being edited`() {
        val overrides = arrayOf(exerciseOverride("pull-ups", "sets" to 5.0), exerciseOverride("dips", "sets" to 3.0))
        show("base", Files(programs = listOf(base(*overrides))))

        press("Pull-ups: sets → 5")

        compose.onAllNodes(hasContentDescription("Remover alteração") and isControl)[0].assertIsNotEnabled()
        compose.onNode(hasText("Dips: sets → 3") and isControl).assertIsNotEnabled()
        press("Cancelar")
        compose.onNode(hasText("Dips: sets → 3") and isControl).assertIsEnabled()
    }

    // A file written before the merge was validated still imports, and must be fixable here.
    @Test
    fun `lists a refused override as not applied, and opens it showing what was authored`() {
        val files = show("base", Files(programs = listOf(base(exerciseOverride("pull-ups", "sets" to 0.0)))))

        named("não aplicado — este valor não é permitido").assertExists()
        press("Pull-ups: sets → 0")
        compose.onNode(hasContentDescription("Séries") and hasText("0")).assertExists()
        type("Séries", "6")
        press("Salvar alteração")
        press("Salvar")

        assertEquals(listOf(exerciseOverride("pull-ups", "sets" to 6.0)), files.savedOverrides())
    }

    @Test
    fun `marks a misspelled key as not applied, on its own line`() {
        show("base", Files(programs = listOf(base(exerciseOverride("pull-ups", "reps" to 12.0)))))

        named("Pull-ups: reps → 12 · não aplicado — não existe essa configuração para alterar").assertExists()
    }

    // overrides: what a confirm keeps, and what it doesn't invent

    // Spread over the raw block, `exercises: 2` replaced the member list with a number.
    @Test
    fun `opens a circuit override carrying a structural key`() {
        val structural = ProgramOverride.BlockOverride("finisher", mapOf("exercises" to OverrideValue.Numeric(2.0)))
        show("base", Files(programs = listOf(base(structural))))

        press("Circuito (finisher): exercises → 2 · não aplicado — não existe essa configuração para alterar")

        named("Circuito (finisher)").assertExists()
        named("Salvar alteração").assertExists()
    }

    // The panel seeds an absent rest as 0, which diffed against the circuit's none as a change.
    @Test
    fun `does not invent a rest override on a circuit that declares none`() {
        val files = show("base", Files(programs = listOf(base(workoutId = "bare-day"))))

        press("+ Adicionar alteração")
        press("bare")
        press("Aumentar Rodadas")
        press("Adicionar alteração")
        press("Salvar")

        assertEquals(
            listOf(ProgramOverride.BlockOverride("bare", mapOf("rounds" to OverrideValue.Numeric(4.0)))),
            files.savedOverrides(),
        )
    }

    // A cleared rest means "no rest", which is 0, not "unchanged", which emitted nothing.
    @Test
    fun `clears a rest the circuit does declare, rather than saving an empty override`() {
        val files = show("base", Files(programs = listOf(base())))

        press("+ Adicionar alteração")
        press("finisher")
        type("Descanso/exercício (seg)", "")
        press("Adicionar alteração")
        press("Salvar")

        assertEquals(listOf(finisherRest(0.0)), files.savedOverrides())
    }

    @Test
    fun `still saves a circuit patch the schema accepts`() {
        val files = show("base", Files(programs = listOf(base())))

        press("+ Adicionar alteração")
        press("finisher")
        type("Descanso/exercício (seg)", "0")
        press("Adicionar alteração")
        press("Salvar")

        assertEquals(listOf(finisherRest(0.0)), files.savedOverrides())
    }

    // The form has nowhere to show `reps` on a reps exercise; dropping it on Save would erase it.
    @Test
    fun `keeps a key the form cannot show instead of erasing it on save`() {
        val files = show("base", Files(programs = listOf(base(exerciseOverride("pull-ups", "reps" to 12.0)))))

        press("Pull-ups: reps → 12 · não aplicado — não existe essa configuração para alterar")
        type("Séries", "5")
        press("Salvar alteração")
        press("Salvar")

        assertEquals(listOf(exerciseOverride("pull-ups", "reps" to 12.0, "sets" to 5.0)), files.savedOverrides())
    }

    // Back to the base value: an override with no keys would be a blank row that changes nothing.
    @Test
    fun `removes an override that ends up with nothing in it rather than saving a blank row`() {
        val files = show("base", Files(programs = listOf(base(exerciseOverride("dips", "sets" to 5.0)))))

        press("Dips: sets → 5")
        type("Séries", "4")
        press("Salvar alteração")
        press("Salvar")

        assertEquals(null, files.savedOverrides())
    }

    @Test
    fun `saves against the override it opened`() {
        val overrides = arrayOf(exerciseOverride("pull-ups", "sets" to 5.0), exerciseOverride("dips", "sets" to 3.0))
        val files = show("base", Files(programs = listOf(base(*overrides))))

        press("Dips: sets → 3")
        type("Séries", "6")
        press("Salvar alteração")
        press("Salvar")

        assertEquals(listOf(overrides[0], exerciseOverride("dips", "sets" to 6.0)), files.savedOverrides())
    }

    /**
     * RN's "does not throw when the list shrinks underneath the open panel" has no native path to it:
     * the rows are inert while the panel is up, and everything else that changes a week's overrides
     * (a rest day, another workout, removing the week) closes that week's panel first. This pins that.
     */
    @Test
    fun `closes the panel whenever the week's overrides can change under it`() {
        show("base", Files(programs = listOf(base(exerciseOverride("pull-ups", "sets" to 5.0)))))

        // A rest day, round trip: the open edit goes, with the overrides it was editing.
        press("Pull-ups: sets → 5")
        press("Dia de descanso")
        press("Treino")
        absent("Salvar alteração")

        // Another workout, while choosing what to patch in the old one.
        press("+ Adicionar alteração")
        named("finisher").assertExists()
        press("Push day")
        press("Pull day")
        absent("finisher")
        named("+ Adicionar alteração").assertExists()
    }
}
