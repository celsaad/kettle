package com.casco.kettle.shell.programeditor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.casco.kettle.domain.ProgramWeek
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The program editor's weeks and days (#438): the week menu's moves, a week's days, the label a new day
 * gets, and Save's refusal pinned under it. In `pt`, on the shared harness.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-w400dp-h4000dp")
class ProgramEditorWeeksTest : ProgramEditorHarness() {
    /** Deleting a week closes the gap (agreed on #438), and a new one goes past the highest left. */
    @Test
    fun `deletes a week from its menu, closing the gap, and adds the next one after the last`() {
        val files = draftWith(3)
        press("Ações da semana 1")
        compose.onNodeWithText("Excluir semana").performClick()

        press("+ Adicionar semana")
        press("Salvar")

        assertEquals(listOf(1L, 2L, 3L), files.savedWeeks().map { it.week })
    }

    /** Up from its menu: the two weeks trade numbers, so week 1 now runs what week 2 did. */
    @Test
    fun `moves a week up from its menu`() {
        val files = draftWith(2)
        press("Push day")
        press("Pull day")
        press("Ações da semana 2")
        compose.onNodeWithText("Mover para cima").performClick()

        press("Salvar")

        assertEquals(
            listOf(1L to "pull-day", 2L to "push-day"),
            files.savedWeeks().map { it.week to (it as ProgramWeek.Training).workoutId },
        )
    }

    /** Right after it, as agreed on #438. */
    @Test
    fun `duplicates a week right after it`() {
        val files = draftWith(1)
        type("Rótulo do dia", "Mon")
        press("Duplicar semana 1")

        press("Salvar")

        assertEquals(listOf(1L to "Mon", 2L to "Mon"), files.savedWeeks().map { it.week to it.day })
    }

    /** Day 1–3, Day 1 removed: the next "+ Add day" is Day 1 again, never a second Day 3 Save refuses. */
    @Test
    fun `labels a new day with one its week doesn't have yet`() {
        val files = draftWith(1)
        press("+ Adicionar dia")
        press("+ Adicionar dia")
        compose.onNode(hasText("Dia 1") and isControl).performClick()
        press("Remover dia")

        press("+ Adicionar dia")
        press("Salvar")

        assertEquals(listOf("Dia 2", "Dia 3", "Dia 1"), files.savedWeeks().map { it.day })
    }

    /** A week's only day goes with its week, and the weeks after it close the gap, as the menu's delete does. */
    @Test
    fun `removing a week's only day removes the week and closes the gap`() {
        val files = draftWith(3)
        press("Semana 2")
        press("Remover dia")

        press("Salvar")

        assertEquals(listOf(1L, 2L), files.savedWeeks().map { it.week })
    }

    /** Save is pinned at the top, so its refusal is too: at the foot of a long program nobody would see it. */
    @Test
    // A phone's height, not the class's tall one: on a 4000dp screen the foot of the list shows anyway.
    @Config(qualifiers = "pt")
    fun `shows Save's refusal under the pinned Save`() {
        // One open day is taller than a phone, so the foot of the list is off screen.
        draftWith(1, name = "")

        press("Salvar")

        named("O nome é obrigatório.").assertIsDisplayed()
    }

    @Test
    fun `offers no move past either end of the week`() {
        draftWith(1)
        press("+ Adicionar dia")

        compose.onNode(hasContentDescription("Mover Dia 2 para baixo") and isControl).assertIsNotEnabled()
        compose.onNode(hasContentDescription("Mover Dia 2 para cima") and isControl).assertIsEnabled()
    }

    @Test
    fun `removes a day`() {
        draftWith(1)
        press("+ Adicionar dia")

        press("Remover dia")

        named("1 dia").assertExists()
    }

    // Two cards at week 1, told apart by day, are one week.
    // Two days of week 1, told apart by label, are one week.
    @Test
    fun `counts weeks rather than week entries`() {
        draftWith(1)
        press("+ Adicionar dia")

        named("1 semana").assertExists()
        named("2 dias").assertExists()
    }
}
