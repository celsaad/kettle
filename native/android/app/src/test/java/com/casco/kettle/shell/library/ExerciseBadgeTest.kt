package com.casco.kettle.shell.library

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.SessionEntry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The type tag, in `pt`: its words are translated, then upper-cased, and the acronyms stay as they are. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class ExerciseBadgeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a hold, a rest and a cardio tag read in Portuguese`() {
        compose.setContent {
            ExerciseBadge(ExerciseConfig.TimedHold(2, 20.0, 30.0, 30.0))
            ExerciseBadge(ExerciseConfig.Rest(60.0))
            ExerciseBadge(SessionEntry.Cardio("row", 600.0, null))
            ExerciseBadge(ExerciseConfig.Amrap(600.0))
        }
        compose.onNodeWithText("ISOMETRIA").assertExists()
        compose.onNodeWithText("DESCANSO").assertExists()
        compose.onNodeWithText("CARDIO").assertExists()
        compose.onNodeWithText("AMRAP").assertExists()
        compose.onNodeWithText("HOLD").assertDoesNotExist()
    }
}
