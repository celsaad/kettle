package com.casco.kettle.shell.home

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.casco.kettle.domain.ExerciseType
import com.casco.kettle.domain.WorkoutShape
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [workoutShapeText], case for case from `formatWorkoutShape`'s in RN's `format.test.ts`, which went
 * with RN Home (#235). In `pt`, so a hardcoded English word fails.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class WorkoutShapeTextTest {
    @get:Rule
    val compose = createComposeRule()

    private fun assertReads(
        shape: WorkoutShape,
        expected: String,
    ) {
        compose.setContent { Text(workoutShapeText(shape)) }
        compose.onNodeWithText(expected).assertExists()
    }

    // The bug RN's replaced: a count interpolated straight into "N blocks" read "1 blocks".
    @Test
    fun `says one block for a single-block workout`() {
        assertReads(WorkoutShape(1, listOf(ExerciseType.Reps)), "1 bloco · repetições")
    }

    @Test
    fun `lists a single type bare`() {
        assertReads(WorkoutShape(3, listOf(ExerciseType.Reps)), "3 blocos · repetições")
    }

    @Test
    fun `lists several types as mixed, in the order they came`() {
        assertReads(
            WorkoutShape(4, listOf(ExerciseType.TimedHold, ExerciseType.Reps)),
            "4 blocos · misto: isometria + repetições",
        )
    }

    @Test
    fun `says rest only when nothing but rest is configured`() {
        assertReads(WorkoutShape(2, emptyList()), "2 blocos · só descanso")
    }
}
