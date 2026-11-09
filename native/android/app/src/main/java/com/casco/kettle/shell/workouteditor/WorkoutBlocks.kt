package com.casco.kettle.shell.workouteditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.WorkoutBlock
import com.casco.kettle.domain.canRemoveMember
import com.casco.kettle.domain.jsNumber
import com.casco.kettle.domain.restSeconds
import com.casco.kettle.domain.withId
import com.casco.kettle.domain.withRounds
import com.casco.kettle.domain.withoutMember
import com.casco.kettle.shell.R
import com.casco.kettle.shell.form.FieldKind
import com.casco.kettle.shell.form.FieldOptions
import com.casco.kettle.shell.form.FormField
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.library.ExerciseBadge
import com.casco.kettle.shell.library.exerciseSummary
import com.casco.kettle.shell.theme.Chevron
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/** The library's exercises, to draw each block's by its id, and the reader's units to show them in. */
internal class BlockContext(
    val exercises: List<Exercise>,
    val units: UnitSystem,
) {
    fun exercise(id: String) = exercises.firstOrNull { it.id == id }
}

/**
 * Where a block stands, for its grip: [index] of [total], and the moves a screen reader offers.
 * [handle] is the drag, from the reorderable item the block is drawn in.
 */
internal class BlockPlace(
    val index: Int,
    val total: Int,
    val handle: Modifier,
    val moveBy: (Int) -> Unit,
)

/**
 * The ⣿ a block is dragged by, and its screen reader's way to move it, since TalkBack can't drag.
 * "Move up" and "Move down" are custom actions, each left off at its end of the list rather than
 * offered and refused; "Position n of m" is its state, which TalkBack reads on focus and again when a
 * move changes it, so the move is announced. The block's key is stable, so focus stays on the block
 * that moved, and a second "Move up" moves it again rather than its new neighbour, as RN's slot keys
 * did (`docs/research/compose-reorder.md`).
 *
 * No role: Compose has none for a handle, and Button would tell TalkBack a double-tap does something
 * here. The custom actions are what TalkBack announces it by. 48dp, not RN's 44, as the research
 * planned, since the grip is the row's one reorder control.
 */
@Composable
internal fun Grip(
    name: String,
    place: BlockPlace,
) {
    val label = stringResource(R.string.workoutEditor_reorderAccessibility, name)
    val position = stringResource(R.string.workoutEditor_positionOf, place.index + 1, place.total)
    val moveUp = stringResource(R.string.workoutEditor_moveUp)
    val moveDown = stringResource(R.string.workoutEditor_moveDown)
    Box(
        place.handle
            .sizeIn(minWidth = GripSize, minHeight = GripSize)
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = position
                customActions =
                    listOfNotNull(
                        CustomAccessibilityAction(moveUp) { true.also { place.moveBy(-1) } }.takeIf { place.index > 0 },
                        CustomAccessibilityAction(moveDown) { true.also { place.moveBy(1) } }
                            .takeIf { place.index < place.total - 1 },
                    )
            },
        contentAlignment = Alignment.Center,
    ) { Text("⣿", style = KettleType.heading, color = KettleTheme.colors.textSecondary) }
}

/** The ✕ that removes [label]'s thing: icon-only, so named, and a 44dp target round a small glyph. */
@Composable
internal fun RemoveButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) = Box(
    Modifier
        .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
        .clip(RoundedCornerShape(8.dp))
        // Disabled rather than silently doing nothing: TalkBack then says it's unavailable.
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label }
        .alpha(if (enabled) 1f else DISABLED_ALPHA),
    contentAlignment = Alignment.Center,
) { Text("✕", Modifier.clearAndSetSemantics {}, color = KettleTheme.colors.textSecondary) }

/**
 * One circuit, as RN's `WorkoutCircuitBlock`: its members, its optional id, and its rounds and rests.
 * A member can go only while one is spare; below two it isn't a circuit, and the way there is to
 * remove the block.
 */
@Composable
internal fun CircuitCard(
    circuit: WorkoutBlock.Circuit,
    context: BlockContext,
    place: BlockPlace,
    onChange: (WorkoutBlock.Circuit) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = KettleTheme.colors
    val circuitName = stringResource(R.string.workoutEditor_circuit)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(BlockShape)
            .background(colors.backgroundElement)
            .border(1.dp, colors.border, BlockShape)
            .padding(start = Spacing.one, end = Spacing.one, top = Spacing.two, bottom = Spacing.two + 6.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.two),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.two), verticalAlignment = Alignment.CenterVertically) {
            Grip(circuitName, place)
            Text(circuitName, Modifier.weight(1f), style = KettleType.heading, color = colors.text)
            RemoveButton(stringResource(R.string.workoutEditor_removeCircuitAccessibility), onClick = onRemove)
        }
        Column(Modifier.padding(start = Spacing.two)) {
            circuit.members.forEachIndexed { index, member ->
                // RN drew nothing for a member whose exercise is gone; it stays in the circuit.
                val exercise = context.exercise(member.exerciseId) ?: return@forEachIndexed
                MemberRow(index, exercise, context.units, enabled = circuit.canRemoveMember) {
                    onChange(circuit.withoutMember(index))
                }
            }
        }
        Column(Modifier.padding(horizontal = Spacing.two), verticalArrangement = Arrangement.spacedBy(Spacing.two)) {
            BlockIdField(circuit, onChange)
            CircuitConfig(circuit, onChange)
            Text(
                stringResource(
                    R.string.format_circuitShape,
                    jsNumber(circuit.restBetweenExercisesSec ?: 0.0),
                    pluralText(R.plurals.format_round, R.string.format_round_zero, circuit.rounds.toInt()),
                    jsNumber(circuit.restBetweenRoundsSec ?: 0.0),
                ),
                style = KettleType.small,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun MemberRow(
    index: Int,
    exercise: Exercise,
    units: UnitSystem,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    val colors = KettleTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${index + 1}",
            Modifier.widthIn(min = 16.dp),
            style = KettleType.small,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Column(Modifier.weight(1f)) {
            Text(exercise.name, style = KettleType.smallMedium, color = colors.text)
            Text(exerciseSummary(exercise.config, units), style = KettleType.small, color = colors.textSecondary)
        }
        ExerciseBadge(exercise.config)
        RemoveButton(
            stringResource(R.string.workoutEditor_removeFromCircuitAccessibility, exercise.name),
            enabled = enabled,
            onClick = onRemove,
        )
    }
}

/**
 * The circuit's id, behind a disclosure: only a program's week overrides need one, so most circuits
 * never open it. Opening it puts the keyboard on the field, as RN's `autoFocus` did.
 */
@Composable
private fun BlockIdField(
    circuit: WorkoutBlock.Circuit,
    onChange: (WorkoutBlock.Circuit) -> Unit,
) {
    val colors = KettleTheme.colors
    // Saveable: the circuit is a lazy list item, disposed while scrolled out of view.
    var open by rememberSaveable { mutableStateOf(false) }
    val id = circuit.id
    Row(
        Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 44.dp)
            .clickable(role = Role.Button) { open = !open }
            .semantics {
                if (open) collapse { true.also { open = false } } else expand { true.also { open = true } }
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.one),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (id != null) {
                stringResource(R.string.workoutEditor_blockIdWithValue, id)
            } else {
                stringResource(R.string.workoutEditor_blockIdPlaceholder)
            },
            Modifier.weight(1f),
            style = KettleType.small,
            color = colors.textSecondary,
        )
        Chevron(expanded = open)
    }
    if (open) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        Text(stringResource(R.string.workoutEditor_blockIdHint), style = KettleType.small, color = colors.textSecondary)
        FormField(
            id.orEmpty(),
            { onChange(circuit.withId(it)) },
            name = stringResource(R.string.workoutEditor_blockIdPlaceholder),
            placeholder = stringResource(R.string.workoutEditor_blockIdInputPlaceholder),
            FieldOptions(kind = FieldKind.Text, minHeight = 44.dp, focus = focus),
        )
    }
}

/** Rounds on a stepper held to 1..the schema's ceiling, and the two rests, never below zero. */
@Composable
private fun CircuitConfig(
    circuit: WorkoutBlock.Circuit,
    onChange: (WorkoutBlock.Circuit) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.two), verticalAlignment = Alignment.Bottom) {
        ConfigColumn(stringResource(R.string.workoutEditor_rounds)) {
            val rounds = stringResource(R.string.workoutEditor_rounds)
            StepButton("−", stringResource(R.string.common_decrease, rounds)) {
                onChange(circuit.withRounds(circuit.rounds - 1))
            }
            Text(
                "${circuit.rounds}",
                Modifier.weight(1f),
                style = KettleType.smallMedium,
                color = KettleTheme.colors.text,
                textAlign = TextAlign.Center,
            )
            StepButton("+", stringResource(R.string.common_increase, rounds)) {
                onChange(circuit.withRounds(circuit.rounds + 1))
            }
        }
        RestField(stringResource(R.string.workoutEditor_restPerExercise), circuit.restBetweenExercisesSec) {
            onChange(circuit.copy(restBetweenExercisesSec = it))
        }
        RestField(stringResource(R.string.workoutEditor_restPerRound), circuit.restBetweenRoundsSec) {
            onChange(circuit.copy(restBetweenRoundsSec = it))
        }
    }
}

@Composable
private fun RowScope.ConfigColumn(
    label: String,
    content: @Composable RowScope.() -> Unit,
) = Column(Modifier.weight(1f)) {
    Text(
        label,
        Modifier.padding(bottom = Spacing.one),
        style = KettleType.small,
        color = KettleTheme.colors.textSecondary,
    )
    Row(verticalAlignment = Alignment.CenterVertically, content = content)
}

/** A − or + on a stepper: named by [label], since its glyph says nothing, in a 44dp target. */
@Composable
internal fun StepButton(
    glyph: String,
    label: String,
    onClick: () -> Unit,
) {
    val colors = KettleTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
            .clip(shape)
            .border(1.dp, colors.border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Text(glyph, Modifier.clearAndSetSemantics {}, color = colors.textSecondary) }
}

/**
 * A rest in seconds, shown as RN showed it, the number itself with 0 for none, and read as RN read
 * it: whatever was typed, as seconds, never below zero.
 */
@Composable
private fun RowScope.RestField(
    label: String,
    seconds: Double?,
    onChange: (Double) -> Unit,
) = Column(Modifier.weight(1f)) {
    Text(
        label,
        Modifier.padding(bottom = Spacing.one),
        style = KettleType.small,
        color = KettleTheme.colors.textSecondary,
    )
    FormField(
        jsNumber(seconds ?: 0.0),
        { onChange(restSeconds(it)) },
        name = label,
        placeholder = "",
        FieldOptions(minHeight = 44.dp),
    )
}

internal val BlockRadius = 16.dp
internal val BlockShape = RoundedCornerShape(BlockRadius)
private val GripSize = 48.dp
private const val DISABLED_ALPHA = 0.4f
