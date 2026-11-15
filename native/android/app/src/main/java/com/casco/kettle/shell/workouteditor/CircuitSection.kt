package com.casco.kettle.shell.workouteditor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.MAX_ROUNDS
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.WorkoutBlock
import com.casco.kettle.domain.canRemoveMember
import com.casco.kettle.domain.withId
import com.casco.kettle.domain.withRounds
import com.casco.kettle.domain.withoutMember
import com.casco.kettle.shell.R
import com.casco.kettle.shell.form.DurationButton
import com.casco.kettle.shell.form.FieldKind
import com.casco.kettle.shell.form.FieldOptions
import com.casco.kettle.shell.form.FormField
import com.casco.kettle.shell.form.StepButton
import com.casco.kettle.shell.list.ListRowMinHeight
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.list.TextAction
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.TypeIcon

/**
 * One circuit (#440), flat as the other blocks: its grip and "Each exercise once per round", then,
 * under a rule, its exercises with × only, "+ Add exercise to circuit" and its picker in place, the
 * rounds and the two rests, and its id beside "Remove circuit". No reordering inside: an exercise is
 * removed and added again, at the end, to move it. One can go only while one is spare, since two is
 * the least a circuit is.
 */
@Composable
internal fun CircuitSection(
    circuit: WorkoutBlock.Circuit,
    context: BlockContext,
    place: BlockPlace,
    actions: CircuitActions,
    picker: @Composable () -> Unit,
) {
    val colors = KettleTheme.colors
    Column {
        Header(place)
        Column(
            Modifier
                .padding(start = Spacing.one)
                .drawBehind { drawRect(colors.textSecondary, size = Size(RULE.toPx(), size.height)) }
                .padding(start = Spacing.two),
        ) {
            circuit.members.forEachIndexed { index, member ->
                // A member whose exercise is gone draws nothing, as RN did; it stays in the circuit.
                val exercise = context.exercise(member.exerciseId) ?: return@forEachIndexed
                MemberRow(
                    exercise,
                    context.units,
                    enabled = circuit.canRemoveMember,
                ) { actions.change(circuit.withoutMember(index)) }
            }
            ListRowSeparator()
            AddToCircuit(actions)
            if (actions.picking) picker()
            ListRowSeparator()
            Rounds(circuit, actions.change)
            Setting(stringResource(R.string.workoutEditor_restBetweenExercises)) { label ->
                DurationButton(label, circuit.restBetweenExercisesSec ?: 0.0) {
                    actions.change(circuit.copy(restBetweenExercisesSec = it))
                }
            }
            Setting(stringResource(R.string.workoutEditor_restBetweenRounds)) { label ->
                DurationButton(
                    label,
                    circuit.restBetweenRoundsSec ?: 0.0,
                ) { actions.change(circuit.copy(restBetweenRoundsSec = it)) }
            }
            ListRowSeparator()
            IdAndRemove(circuit, actions)
            Text(
                stringResource(R.string.workoutEditor_circuitHelp),
                Modifier.padding(bottom = Spacing.two),
                style = KettleType.small,
                color = colors.textSecondary,
            )
        }
    }
}

/** The circuit's own row: its grip, "Circuit", and "Each exercise once per round". */
@Composable
private fun Header(place: BlockPlace) {
    val colors = KettleTheme.colors
    val name = stringResource(R.string.workoutEditor_circuit)
    ListRowSeparator()
    Row(
        Modifier.fillMaxWidth().heightIn(min = ListRowMinHeight),
        horizontalArrangement = Arrangement.spacedBy(Spacing.one),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Grip(name, place)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = KettleType.heading, color = colors.text)
            Text(
                stringResource(R.string.workoutEditor_circuitSubtitle),
                style = KettleType.small,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun MemberRow(
    exercise: Exercise,
    units: UnitSystem,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    val colors = KettleTheme.colors
    ListRowSeparator()
    Row(
        Modifier.fillMaxWidth().heightIn(min = ListRowMinHeight),
        horizontalArrangement = Arrangement.spacedBy(Spacing.two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(exercise.name, style = KettleType.heading, color = colors.text)
            Text(memberTarget(exercise.config, units), style = KettleType.small, color = colors.textSecondary)
        }
        RemoveButton(
            stringResource(R.string.workoutEditor_removeFromCircuitAccessibility, exercise.name),
            enabled = enabled,
            onClick = onRemove,
        )
    }
}

/** "+ Add exercise to circuit", reading "Close" while its picker is open under it. */
@Composable
private fun AddToCircuit(actions: CircuitActions) {
    val toggle = actions.togglePicker
    // Its own control rather than a TextAction, so the expanded state sits on what TalkBack focuses.
    Text(
        stringResource(if (actions.picking) R.string.common_close else R.string.workoutEditor_addToCircuit),
        Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = toggle)
            .semantics {
                if (actions.picking) collapse { true.also { toggle() } } else expand { true.also { toggle() } }
            }.wrapContentHeight(Alignment.CenterVertically),
        style = KettleType.smallMedium,
        color = KettleTheme.colors.text,
    )
}

/** Rounds on a stepper held to 1..the schema's ceiling, the value between its buttons. */
@Composable
private fun Rounds(
    circuit: WorkoutBlock.Circuit,
    onChange: (WorkoutBlock.Circuit) -> Unit,
) = Setting(stringResource(R.string.workoutEditor_rounds)) { label ->
    // The buttons draw their own outlines, as everywhere else they step; a box round them doubled it.
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton(
            R.drawable.icon_remove,
            stringResource(R.string.common_decrease, label),
            circuit.rounds > 1,
            "${circuit.rounds}",
        ) {
            onChange(circuit.withRounds(circuit.rounds - 1))
        }
        Text(
            "${circuit.rounds}",
            Modifier.widthIn(min = 40.dp),
            style = KettleType.heading,
            color = KettleTheme.colors.text,
            textAlign = TextAlign.Center,
        )
        StepButton(
            R.drawable.icon_add,
            stringResource(R.string.common_increase, label),
            circuit.rounds < MAX_ROUNDS,
            "${circuit.rounds}",
        ) {
            onChange(circuit.withRounds(circuit.rounds + 1))
        }
    }
}

/** A setting's row: its name on the left, its control on the right. */
@Composable
private fun Setting(
    label: String,
    control: @Composable (String) -> Unit,
) = Row(
    Modifier.fillMaxWidth().heightIn(min = SETTING_HEIGHT).padding(end = Spacing.one),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(label, Modifier.weight(1f), style = KettleType.default, color = KettleTheme.colors.text)
    control(label)
}

/**
 * The id as a button, "id: finisher ✎", opening its field, and "Remove circuit" beside it. Only a
 * program's changes need an id, which the help under it says.
 */
@Composable
private fun IdAndRemove(
    circuit: WorkoutBlock.Circuit,
    actions: CircuitActions,
) {
    val colors = KettleTheme.colors
    // Saveable: the circuit is a lazy list item, disposed while scrolled out of view.
    var editing by rememberSaveable { mutableStateOf(false) }
    val id = circuit.id
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .clickable(role = Role.Button) { editing = !editing }
                .semantics {
                    if (editing) collapse { true.also { editing = false } } else expand { true.also { editing = true } }
                },
            horizontalArrangement = Arrangement.spacedBy(Spacing.one),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                id?.let { stringResource(R.string.workoutEditor_idLabel, it) }
                    ?: stringResource(R.string.workoutEditor_addId),
                Modifier.weight(1f, fill = false),
                style = KettleType.code,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TypeIcon(R.drawable.icon_edit, KettleType.small, colors.textSecondary)
        }
        TextAction(
            stringResource(R.string.workoutEditor_removeCircuit),
            KettleType.smallMedium,
            colors.text,
            onClick = actions.remove,
        )
    }
    if (editing) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        FormField(
            id.orEmpty(),
            { actions.change(circuit.withId(it)) },
            name = stringResource(R.string.workoutEditor_idField),
            placeholder = stringResource(R.string.workoutEditor_blockIdInputPlaceholder),
            FieldOptions(kind = FieldKind.Text, minHeight = 44.dp, focus = focus),
        )
    }
}

private val RULE = 2.dp
private val SETTING_HEIGHT = 52.dp
