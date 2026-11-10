package com.casco.kettle.shell.programeditor

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.CircuitMember
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.OverrideValue
import com.casco.kettle.domain.Program
import com.casco.kettle.domain.ProgramOverride
import com.casco.kettle.domain.ProgramWeek
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.WorkoutBlock
import com.casco.kettle.shell.files.FakeStoredFiles
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.theme.KettleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.Executor

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
class ProgramEditorScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var closed = 0
    private var deleted = 0

    /** [io] holds the file work until it's run, for a test that needs a write caught mid-way. */
    private fun show(
        id: String? = null,
        files: Files = Files(),
        io: Steps? = null,
    ): Files {
        val dispatcher = io?.asCoroutineDispatcher() ?: Dispatchers.Unconfined
        val latest = LatestFiles(files, CoroutineScope(Dispatchers.Unconfined), io = dispatcher).apply { reread() }
        io?.run()
        compose.setContent {
            KettleTheme(darkTheme = false) {
                ProgramEditorScreen(
                    id,
                    latest,
                    ProgramEditorActions(
                        close = { closed += 1 },
                        deleted = { deleted += 1 },
                    ),
                )
            }
        }
        return files
    }

    private val isControl =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button) or
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)

    private fun labelled(label: String) = hasText(label) or hasContentDescription(label)

    /** A node showing or named [label]; a field's label and the field it names are both one. */
    private fun named(label: String) = compose.onAllNodes(labelled(label)).onFirst()

    private fun press(
        label: String,
        index: Int = 0,
    ) = compose.onAllNodes(labelled(label) and isControl)[index].performClick()

    private fun type(
        name: String,
        text: String,
        index: Int = 0,
    ) = compose.onAllNodes(hasContentDescription(name))[index].performTextReplacement(text)

    /** jest's `pressAlertButton`: a button inside the dialog, never a same-named one on the screen under it. */
    private fun ComposeContentTestRule.pressDialogButton(label: String) =
        onNode(hasText(label) and hasAnyAncestor(isDialog())).performClick()

    /** Names the draft and adds [count] weeks: the shortest path to a program Save takes. */
    private fun draftWith(
        count: Int,
        name: String = "Base",
    ): Files {
        val files = show()
        type("Nome", name)
        repeat(count) { press("+ Adicionar semana") }
        return files
    }

    private fun Files.savedWeeks() =
        written
            .last()
            .programs
            .single()
            .weeks

    private fun absent(text: String) = compose.onAllNodesWithText(text).assertCountEquals(0)

    // validation

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
        val files = draftWith(2)
        // Both weeks now read 1, which is the `week + day` address colliding.
        press("Diminuir Semana", index = 1)

        press("Salvar")

        named(
            "A semana 1 está repetida — defina um dia diferente para uma delas ou remova a duplicada.",
        ).assertExists()
        assertEquals(emptyList<Library>(), files.written)
    }

    // The point of `day`: two sessions in one week. Refusing it would break multi-session weeks.
    @Test
    fun `allows the same week number when the days differ`() {
        val files = draftWith(2)
        press("Diminuir Semana", index = 1)
        type("Dia · opcional", "Monday", index = 0)
        type("Dia · opcional", "Thursday", index = 1)

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
        press("+ Adicionar ajuste")
        press("finisher")

        for (label in listOf(
            "Diminuir Semana",
            "Aumentar Semana",
            "Remover semana 1",
            "Diminuir Rodadas",
            "Remover ajuste",
        )) {
            compose
                .onAllNodes(
                    labelled(label) and isControl,
                )[0]
                .assertWidthIsAtLeast(44.dp)
                .assertHeightIsAtLeast(44.dp)
        }
        for (label in listOf("Dia de descanso", "Push day", "Pull-ups: sets → 5")) {
            compose.onAllNodes(labelled(label) and isControl)[0].assertHeightIsAtLeast(44.dp)
        }
    }

    // weeks

    // Counting would call the re-added week 2 and collide; max+1 keeps the numbering monotonic.
    @Test
    fun `numbers a new week past the highest already there, not by count`() {
        val files = draftWith(2)
        repeat(3) { press("Aumentar Semana", index = 1) }
        press("Remover semana 1")

        press("+ Adicionar semana")
        press("Salvar")

        assertEquals(listOf(5L, 6L), files.savedWeeks().map { it.week })
    }

    // Not left blank: an unset workout is a Save refusal, so the common path needs no picker at all.
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

        named("Ajustes · opcional").assertExists()
        named("Semana").assertExists()
        absent("Overrides · optional")
    }

    // 1-based throughout: the schema's `positive()` and week resolution both assume it.
    @Test
    fun `does not let a week number fall below one`() {
        val files = draftWith(1)

        repeat(3) { press("Diminuir Semana") }
        press("Salvar")

        assertEquals(1L, files.savedWeeks().single().week)
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
    fun `removes a week`() {
        draftWith(2)

        press("Remover semana 1")

        named("1 semana").assertExists()
    }

    // Every week needs a workout and there are none, so an added week could only be refused.
    @Test
    fun `explains itself instead of offering an unusable add button`() {
        show(files = Files(workouts = emptyList()))

        compose.onNode(hasText("+ Adicionar semana") and isControl).assertIsNotEnabled()
        named("Nenhum treino ainda — crie um em Treinos primeiro e volte para adicionar semanas aqui.").assertExists()
        named("0 semanas").assertExists()
    }

    // Two cards at week 1, told apart by day, are one week.
    @Test
    fun `counts weeks rather than week entries`() {
        draftWith(2)
        press("Diminuir Semana", index = 1)
        type("Dia · opcional", "Day 1", index = 0)
        type("Dia · opcional", "Day 2", index = 1)

        named("1 semana").assertExists()
    }

    // No in-use guard: nothing in the library points at a program, so a delete can't orphan anything.
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
        named("Treino").assertExists()

        press("Dia de descanso")

        absent("Treino")
        absent("Ajustes · opcional")
        // Week, day and notes mean the same on either kind of week.
        named("Semana").assertExists()
        named("Notas · opcional").assertExists()
    }

    @Test
    fun `persists a rest week with no workout on it`() {
        val files = draftWith(2)

        press("Dia de descanso", index = 1)
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
        press("Dia de descanso")
        press("Salvar")

        assertEquals("push-day", (files.savedWeeks().single() as ProgramWeek.Training).workoutId)
    }

    @Test
    fun `announces itself as a switch, with its state`() {
        draftWith(1)
        val switch = hasText("Dia de descanso") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)

        compose
            .onNode(
                switch,
            ).assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        press("Dia de descanso")
        compose
            .onNode(
                switch,
            ).assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
    }

    // overrides: what the panel offers

    @Test
    fun `explains itself when the chosen workout has nothing overridable`() {
        show("base", Files(programs = listOf(base(workoutId = "empty"))))

        named("Este treino ainda não tem nada para ajustar.").assertExists()
        compose.onNode(hasText("+ Adicionar ajuste") and isControl).assertIsNotEnabled()
    }

    // With no workout resolved there is nothing to explain yet, only something not done: no hint.
    @Test
    fun `offers no override, and no hint, while the week's workout resolves to nothing`() {
        show("base", Files(programs = listOf(base(workoutId = "gone"))))

        compose.onNode(hasText("+ Adicionar ajuste") and isControl).assertIsNotEnabled()
        absent("Este treino ainda não tem nada para ajustar.")
    }

    // Dips is eligible through the circuit; the circuit is offered by the id an override names it by.
    @Test
    fun `offers only the exercises and id-tagged circuits the workout contains`() {
        show("base", Files(programs = listOf(base())))

        press("+ Adicionar ajuste")

        named("Pull-ups").assertExists()
        named("Dips").assertExists()
        named("finisher").assertExists()
        absent("Rows")
    }

    @Test
    fun `labels config fields in the user language, not with their keys`() {
        show("base", Files(programs = listOf(base())))

        press("+ Adicionar ajuste")
        press("Pull-ups")

        named("Séries").assertExists()
        compose.onAllNodes(hasTextMatching(KEY_PATH)).assertCountEquals(0)
    }

    // overrides: what they write

    // A patch, not the whole config: writing every field would freeze the week against later edits.
    @Test
    fun `emits only the changed key, in the snake_case the YAML uses`() {
        val files = show("base", Files(programs = listOf(base())))

        press("+ Adicionar ajuste")
        press("Pull-ups")
        type("Séries", "5")
        press("Adicionar ajuste")
        press("Salvar")

        assertEquals(listOf(exerciseOverride("pull-ups", "sets" to 5.0)), files.savedOverrides())
    }

    @Test
    fun `emits a block override keyed by the circuit id`() {
        val files = show("base", Files(programs = listOf(base())))

        press("+ Adicionar ajuste")
        press("finisher")
        press("Aumentar Rodadas")
        press("Adicionar ajuste")
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
        named("Salvar ajuste").assertExists()
        compose.onNode(hasContentDescription("Séries") and hasText("5")).assertExists()
        type("Séries", "6")
        press("Salvar ajuste")
        press("Salvar")

        assertEquals(listOf(exerciseOverride("pull-ups", "sets" to 6.0)), files.savedOverrides())
    }

    @Test
    fun `removes an override`() {
        val files = show("base", Files(programs = listOf(base(exerciseOverride("pull-ups", "sets" to 5.0)))))

        press("Remover ajuste")
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

        press("+ Adicionar ajuste")
        press("Pull-ups")
        type("Séries", "0")
        press("Adicionar ajuste")

        named("Séries precisa ser pelo menos 1.").assertExists()
        press("Salvar")
        assertEquals(null, files.savedOverrides())
    }

    @Test
    fun `refuses a count past the ceiling too`() {
        show("base", Files(programs = listOf(base())))

        press("+ Adicionar ajuste")
        press("Pull-ups")
        type("Séries", "501")
        press("Adicionar ajuste")

        named("Séries pode ser no máximo 500.").assertExists()
    }

    @Test
    fun `adds alongside an existing override rather than replacing it`() {
        val existing = exerciseOverride("pull-ups", "sets" to 5.0)
        val files = show("base", Files(programs = listOf(base(existing))))

        press("+ Adicionar ajuste")
        press("Dips")
        type("Séries", "2")
        press("Adicionar ajuste")
        press("Salvar")

        assertEquals(listOf(existing, exerciseOverride("dips", "sets" to 2.0)), files.savedOverrides())
    }

    @Test
    fun `clears the message once the field is edited again, and then saves`() {
        val files = show("base", Files(programs = listOf(base())))

        press("+ Adicionar ajuste")
        press("Pull-ups")
        type("Séries", "0")
        press("Adicionar ajuste")
        type("Séries", "5")

        absent("Séries precisa ser pelo menos 1.")
        press("Adicionar ajuste")
        press("Salvar")
        assertEquals(listOf(exerciseOverride("pull-ups", "sets" to 5.0)), files.savedOverrides())
    }

    @Test
    fun `refuses a negative circuit rest rather than saving one the merge will drop`() {
        show("base", Files(programs = listOf(base())))

        press("+ Adicionar ajuste")
        press("finisher")
        type("Descanso/exercício (seg)", "-5")
        press("Adicionar ajuste")

        named("Descanso/exercício (seg) precisa ser pelo menos 0.").assertExists()
    }

    // The rows stay up behind the panel, so a message must not outlive the target it named.
    @Test
    fun `drops a stale error when the panel moves to another target`() {
        val overrides = arrayOf(exerciseOverride("pull-ups", "sets" to 5.0), exerciseOverride("dips", "sets" to 3.0))
        show("base", Files(programs = listOf(base(*overrides))))

        press("Pull-ups: sets → 5")
        type("Séries", "0")
        press("Salvar ajuste")
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

        compose.onAllNodes(hasContentDescription("Remover ajuste") and isControl)[0].assertIsNotEnabled()
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
        press("Salvar ajuste")
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
        named("Salvar ajuste").assertExists()
    }

    // The panel seeds an absent rest as 0, which diffed against the circuit's none as a change.
    @Test
    fun `does not invent a rest override on a circuit that declares none`() {
        val files = show("base", Files(programs = listOf(base(workoutId = "bare-day"))))

        press("+ Adicionar ajuste")
        press("bare")
        press("Aumentar Rodadas")
        press("Adicionar ajuste")
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

        press("+ Adicionar ajuste")
        press("finisher")
        type("Descanso/exercício (seg)", "")
        press("Adicionar ajuste")
        press("Salvar")

        assertEquals(listOf(finisherRest(0.0)), files.savedOverrides())
    }

    @Test
    fun `still saves a circuit patch the schema accepts`() {
        val files = show("base", Files(programs = listOf(base())))

        press("+ Adicionar ajuste")
        press("finisher")
        type("Descanso/exercício (seg)", "0")
        press("Adicionar ajuste")
        press("Salvar")

        assertEquals(listOf(finisherRest(0.0)), files.savedOverrides())
    }

    // The form has nowhere to show `reps` on a reps exercise; dropping it on Save would erase it.
    @Test
    fun `keeps a key the form cannot show instead of erasing it on save`() {
        val files = show("base", Files(programs = listOf(base(exerciseOverride("pull-ups", "reps" to 12.0)))))

        press("Pull-ups: reps → 12 · não aplicado — não existe essa configuração para alterar")
        type("Séries", "5")
        press("Salvar ajuste")
        press("Salvar")

        assertEquals(listOf(exerciseOverride("pull-ups", "reps" to 12.0, "sets" to 5.0)), files.savedOverrides())
    }

    // Back to the base value: an override with no keys would be a blank row that changes nothing.
    @Test
    fun `removes an override that ends up with nothing in it rather than saving a blank row`() {
        val files = show("base", Files(programs = listOf(base(exerciseOverride("dips", "sets" to 5.0)))))

        press("Dips: sets → 5")
        type("Séries", "4")
        press("Salvar ajuste")
        press("Salvar")

        assertEquals(null, files.savedOverrides())
    }

    @Test
    fun `saves against the override it opened`() {
        val overrides = arrayOf(exerciseOverride("pull-ups", "sets" to 5.0), exerciseOverride("dips", "sets" to 3.0))
        val files = show("base", Files(programs = listOf(base(*overrides))))

        press("Dips: sets → 3")
        type("Séries", "6")
        press("Salvar ajuste")
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
        press("Dia de descanso")
        absent("Salvar ajuste")

        // Another workout, while choosing what to patch in the old one.
        press("+ Adicionar ajuste")
        named("finisher").assertExists()
        press("Push day")
        press("Pull day")
        absent("finisher")
        named("+ Adicionar ajuste").assertExists()
    }

    private fun Files.savedOverrides() = (savedWeeks().single() as ProgramWeek.Training).overrides

    private class Files(
        private val workouts: List<Workout> = listOf(pushDay, pullDay, empty, bareDay),
        private val programs: List<Program> = emptyList(),
    ) : FakeStoredFiles() {
        private val library = Library(1, listOf(pullUps, dips, rows), workouts, programs)
        val written = mutableListOf<Library>()

        /** Thrown by the next write, as a full disk would. */
        var failure: IOException? = null

        override fun library() = written.lastOrNull() ?: library

        override fun writeLibrary(library: Library) {
            failure?.let { throw it }
            written += library
        }
    }
}

private val pullUps = Exercise("pull-ups", "Pull-ups", ExerciseConfig.Reps(sets = 4, targetRepsMin = 6, restSec = 90.0))
private val dips = Exercise("dips", "Dips", ExerciseConfig.Reps(sets = 4, targetRepsMin = 6, restSec = 90.0))
private val rows = Exercise("rows", "Rows", ExerciseConfig.Reps(sets = 3, targetRepsMin = 8, restSec = 60.0))

private val finisher =
    WorkoutBlock.Circuit(
        id = "finisher",
        rounds = 3,
        restBetweenExercisesSec = 15.0,
        restBetweenRoundsSec = 60.0,
        members = listOf(CircuitMember("pull-ups"), CircuitMember("dips")),
    )
private val pushDay = Workout("push-day", "Push day", listOf(WorkoutBlock.ExerciseBlock("pull-ups"), finisher))
private val pullDay = Workout("pull-day", "Pull day", listOf(WorkoutBlock.ExerciseBlock("rows")))
private val empty = Workout("empty", "Empty", emptyList())

/** A circuit with no rests of its own, which the panel still shows as 0. */
private val bareDay =
    Workout(
        "bare-day",
        "Bare day",
        listOf(
            WorkoutBlock.Circuit(
                id = "bare",
                rounds = 3,
                members = listOf(CircuitMember("pull-ups"), CircuitMember("dips")),
            ),
        ),
    )

private fun finisherRest(seconds: Double) =
    ProgramOverride.BlockOverride("finisher", mapOf("rest_between_exercises_sec" to OverrideValue.Numeric(seconds)))

private fun base(
    vararg overrides: ProgramOverride,
    workoutId: String = "push-day",
) = Program(
    "base",
    "Base",
    listOf(ProgramWeek.Training(1, workoutId = workoutId, overrides = overrides.toList().ifEmpty { null })),
)

private fun exerciseOverride(
    id: String,
    vararg config: Pair<String, Double>,
) = ProgramOverride.ExerciseOverride(id, config.associate { (key, value) -> key to OverrideValue.Numeric(value) })

private const val SETTLE_ROUNDS = 3

/** A dispatcher that runs nothing until told to, so a test holds a write mid-way. */
private class Steps : Executor {
    private val queued = ArrayDeque<Runnable>()

    override fun execute(task: Runnable) {
        queued += task
    }

    fun run() {
        while (queued.isNotEmpty()) queued.removeFirst().run()
    }
}

/** A text that reads like an i18next key path, "programEditor.week". */
private val KEY_PATH = Regex("^[a-z][a-zA-Z]*\\.[a-zA-Z.]+$")

private fun hasTextMatching(pattern: Regex) =
    SemanticsMatcher("text matches $pattern") { node ->
        node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { pattern.matches(it.text) }
    }
