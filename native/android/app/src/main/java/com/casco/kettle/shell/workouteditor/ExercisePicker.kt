package com.casco.kettle.shell.workouteditor

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.toggledMember
import com.casco.kettle.shell.R
import com.casco.kettle.shell.exerciseeditor.NewExerciseForm
import com.casco.kettle.shell.form.dashedBorder
import com.casco.kettle.shell.library.ExerciseBadge
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/** What a picker is open for: one exercise to add as a block, or the members of a new circuit. */
internal enum class PickerMode { Block, Circuit }

/**
 * Which picker is open, the circuit members picked so far, and whether the quick-add form is up: the
 * pickers' own state, apart from the draft's, as RN's lived in each mounted `ExercisePicker`.
 */
internal class Pickers {
    var open by mutableStateOf<PickerMode?>(null)
        private set
    var selection by mutableStateOf(emptyList<String>())
        private set
    var quickAdd by mutableStateOf(false)
        private set

    /** Opens [mode]'s picker, or closes it if it was open, starting it empty either way. */
    fun toggle(mode: PickerMode) {
        open = if (open == mode) null else mode
        selection = emptyList()
        quickAdd = false
    }

    fun close() {
        open?.let(::toggle)
    }

    fun toggleMember(exerciseId: String) {
        selection = toggledMember(selection, exerciseId)
    }

    fun showQuickAdd(shown: Boolean) {
        quickAdd = shown
    }
}

/**
 * The editor's exercise picker, as RN's `ExercisePicker`, in its two modes. A row adds a block in
 * [PickerMode.Block]; in [PickerMode.Circuit] it toggles a member, and a confirm builds the circuit
 * from two or more. No search, as RN's had none: the feature freeze ports it as it is.
 *
 * The quick-add form replaces the list while it is up, so the workout being built isn't left for the
 * exercise editor. It stays up until the create's write lands, so a refused one keeps its values.
 */
@Composable
internal fun ExercisePicker(
    pickers: Pickers,
    mode: PickerMode,
    context: BlockContext,
    actions: PickerActions,
) {
    val colors = KettleTheme.colors
    Column(Modifier.padding(top = Spacing.two), verticalArrangement = Arrangement.spacedBy(Spacing.one + 2.dp)) {
        if (pickers.quickAdd) {
            NewExerciseForm(context.units, onCreate = actions.create, onCancel = { pickers.showQuickAdd(false) })
            return@Column
        }
        if (mode == PickerMode.Circuit) {
            Text(
                stringResource(R.string.workoutEditor_selectAtLeast2),
                style = KettleType.small,
                color = colors.textSecondary,
            )
        }
        NewExerciseButton { pickers.showQuickAdd(true) }
        for (exercise in context.exercises) {
            when (mode) {
                PickerMode.Block -> {
                    PickerRow(exercise, Modifier.clickable(role = Role.Button) { actions.pick(exercise.id) })
                }

                PickerMode.Circuit -> {
                    val checked = exercise.id in pickers.selection
                    // A checkbox, not a button: a press here toggles membership rather than acting.
                    PickerRow(
                        exercise,
                        Modifier.toggleable(checked, role = Role.Checkbox) { pickers.toggleMember(exercise.id) },
                        checked = checked,
                    )
                }
            }
        }
        if (mode == PickerMode.Circuit) ConfirmCircuitButton(pickers.selection.size, actions.confirm)
    }
}

@Composable
private fun NewExerciseButton(onClick: () -> Unit) {
    val colors = KettleTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .dashedBorder(1.dp, colors.border, 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.workoutEditor_newExercise),
            style = KettleType.smallMedium,
            color = colors.textSecondary,
        )
    }
}

/** Builds the circuit, unavailable until [count] reaches two, and saying so to a screen reader. */
@Composable
private fun ConfirmCircuitButton(
    count: Int,
    onClick: () -> Unit,
) {
    val colors = KettleTheme.colors
    val ready = count >= 2
    Box(
        Modifier
            .padding(top = Spacing.one)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.accent)
            .clickable(enabled = ready, role = Role.Button, onClick = onClick)
            .alpha(if (ready) 1f else DISABLED_ALPHA),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.workoutEditor_addCircuitCount, count),
            style = KettleType.heading,
            color = colors.onAccent,
        )
    }
}

/** What the picker's controls do to the draft, as one parameter, so the draft stays the screen's. */
internal class PickerActions(
    val pick: (String) -> Unit,
    val confirm: () -> Unit,
    val create: (Exercise) -> Unit,
)

/** One exercise to pick: its name and type, and in circuit mode a checkbox, outlined in the accent when ticked. */
@Composable
private fun PickerRow(
    exercise: Exercise,
    // The row's control, a button or a checkbox: the modifier Compose's convention names `modifier`.
    modifier: Modifier = Modifier,
    checked: Boolean? = null,
) {
    val colors = KettleTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(shape)
            .then(modifier)
            .background(colors.backgroundElement)
            .border(1.dp, if (checked == true) colors.accent else colors.border, shape)
            .padding(Spacing.two),
        horizontalArrangement = Arrangement.spacedBy(Spacing.two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (checked != null) CheckBox(checked)
        Text(exercise.name, Modifier.weight(1f), style = KettleType.smallMedium, color = colors.text)
        ExerciseBadge(exercise.config)
    }
}

/** The tick's box. Decorative: the row is the checkbox, and says so. */
@Composable
private fun CheckBox(checked: Boolean) {
    val colors = KettleTheme.colors
    val shape = RoundedCornerShape(5.dp)
    Box(
        Modifier
            .clearAndSetSemantics {}
            .size(20.dp)
            .clip(shape)
            .background(if (checked) colors.accent else colors.backgroundElement)
            .border(1.5.dp, if (checked) colors.accent else colors.border, shape),
        contentAlignment = Alignment.Center,
    ) { if (checked) Text("✓", style = KettleType.small, color = colors.onAccent) }
}

private const val DISABLED_ALPHA = 0.4f
