package com.casco.kettle.shell.exerciseeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseDraft
import com.casco.kettle.domain.ExerciseProblem
import com.casco.kettle.domain.ExerciseSubmit
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.submitExercise
import com.casco.kettle.shell.R
import com.casco.kettle.shell.form.EditorButtons
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * The exercise form's mini version, as RN's `new-exercise-form.tsx`: embedded in the workout editor's
 * pickers rather than opening the editor, since the workout being built is unsaved, and leaving it
 * would lose it. Built on the editor's own [ExerciseFormFields] and `submitExercise`, so the checks
 * the editor runs are the checks this runs; RN's copy had fallen behind on both.
 *
 * Only ever creates, so the type stays open and there is no stored weight to guard. Writes nothing:
 * [onCreate] gets the built exercise, and the caller saves it with the workout.
 */
@Composable
fun NewExerciseForm(
    units: UnitSystem,
    onCreate: (Exercise) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = KettleTheme.colors
    var draft by remember { mutableStateOf(ExerciseDraft.New) }
    var problem by remember { mutableStateOf<ExerciseProblem?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .border(1.dp, colors.border, CardShape)
            .padding(Spacing.two + 4.dp),
    ) {
        ExerciseFormFields(draft, { draft = it }, units, typeLocked = false, compact = true)
        ExerciseNotesField(draft, { draft = it }, compact = true)
        problem?.let {
            Text(
                exerciseProblemText(it),
                Modifier.padding(top = Spacing.two - 2.dp),
                style = KettleType.small,
                color = colors.accentText,
            )
        }
        EditorButtons(
            stringResource(R.string.workoutEditor_createAndAdd),
            onCancel = onCancel,
            onPrimary = {
                when (val submitted = submitExercise(draft, null, units)) {
                    is ExerciseSubmit.Refused -> problem = submitted.problem
                    is ExerciseSubmit.Built -> onCreate(submitted.exercise)
                }
            },
            compact = true,
        )
    }
}

private val CardShape = RoundedCornerShape(14.dp)
