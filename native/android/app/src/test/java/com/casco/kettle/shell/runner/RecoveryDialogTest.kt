package com.casco.kettle.shell.runner

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.casco.kettle.domain.RunnerRecovery
import com.casco.kettle.shell.theme.KettleTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The launch's Resume / Finish prompt after process death (#307), in `pt`, so a hardcoded string shows. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class RecoveryDialogTest {
    @get:Rule
    val compose = createComposeRule()

    private val start = started(PULLUPS).state
    private val prompt = RunnerRecovery.Prompt(start.snapshot, start.session)
    private val finishOnly = RunnerRecovery.FinishOnly(start.session, T0)
    private val taps = mutableListOf<String>()
    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun show(
        recovery: RunnerRecovery.Finishable,
        failure: String? = null,
    ) = compose.setContent {
        KettleTheme(darkTheme = false) {
            RecoveryDialog(recovery, "Treino da manhã", failure, onResume = { taps += "resume" }) { taps += "finish" }
        }
    }

    @Test
    fun `asks Resume or Finish, naming the workout verbatim`() {
        show(prompt)

        compose.onNodeWithText("Treino interrompido").assertExists()
        compose.onNodeWithText("Treino da manhã", substring = true).assertExists()
        compose.onNode(hasText("Continuar") and isButton).performClick()
        compose.onNode(hasText("Encerrar") and isButton).performClick()
        assertEquals(listOf("resume", "finish"), taps)
    }

    @Test
    fun `offers Finish only where the session can't be resumed`() {
        show(finishOnly)

        compose.onNodeWithText("Continuar").assertDoesNotExist()
        compose.onNode(hasText("Encerrar") and isButton).performClick()
        assertEquals(listOf("finish"), taps)
    }

    @Test
    fun `says why the disk refused a Finish`() {
        show(prompt, failure = "disk full")

        compose.onNodeWithText("Não foi possível salvar: disk full", substring = true).assertExists()
    }
}
