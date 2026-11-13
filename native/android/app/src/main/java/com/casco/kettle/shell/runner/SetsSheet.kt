package com.casco.kettle.shell.runner

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.PreviousSet
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.WorkStep
import com.casco.kettle.domain.holdTarget
import com.casco.kettle.domain.jsNumber
import com.casco.kettle.domain.repsTarget
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.TypeIcon

// The Sets sheet (#410): this exercise's sets this session, logged, now and planned, where a set is
// added or the last planned one removed. The same AddSet and DropSet as the −/+ it replaces, and their
// rules (no circuit, never below what's logged and on screen), so nothing new reaches the engine.

/** Where a set stands. [ADDED] is a planned set past the count the session was planned with. */
internal enum class SetStatus { LOGGED, NOW, PLANNED, ADDED }

internal data class SetRow(
    val number: Int,
    val status: SetStatus,
    /** What was logged, for a [SetStatus.LOGGED] row only. */
    val logged: PreviousSet? = null,
)

internal data class SetsSheetModel(
    val rows: List<SetRow>,
    /** The count the session was planned with, which the footer says the workout still holds. */
    val planned: Long,
    /**
     * Remove on the last row: the engine's DropSet rule, which never goes below the sets logged and the
     * one on screen, so the last row is a planned set whenever it holds.
     */
    val canRemoveLast: Boolean,
)

/**
 * The step on screen's member as the sheet lists it: its logged sets from the session file, the one on
 * screen, then the ones still planned. Null off a reps or hold step.
 */
internal fun setsSheet(
    view: RunnerView.Running,
    canDropSet: Boolean,
): SetsSheetModel? {
    val snapshot = view.snapshot
    val step = snapshot.step as? WorkStep
    if (step !is RunnerStep.Reps && step !is RunnerStep.Hold) return null
    val logged = loggedSets(view, step.memberKey)
    val ahead = snapshot.steps.drop(snapshot.stepIndex).count { it is WorkStep && it.memberKey == step.memberKey }
    val planned = plannedSets(view, step)
    val rows =
        logged.mapIndexed { i, set -> SetRow(i + 1, SetStatus.LOGGED, set) } +
            (1..ahead).map { k ->
                val number = logged.size + k
                val status =
                    when {
                        k == 1 -> SetStatus.NOW
                        number > planned -> SetStatus.ADDED
                        else -> SetStatus.PLANNED
                    }
                SetRow(number, status)
            }
    return SetsSheetModel(rows, planned, canRemoveLast = canDropSet)
}

/**
 * [step]'s member's set count as the plan built it, before any Sets edit (which rewrite the live step's
 * `setTotal`); the live count for an ad-hoc session, whose plan starts empty.
 */
internal fun plannedSets(
    view: RunnerView.Running,
    step: WorkStep,
): Long =
    view.plan.steps
        .filterIsInstance<WorkStep>()
        .firstOrNull { it.memberKey == step.memberKey }
        ?.setTotal ?: step.setTotal

private fun loggedSets(
    view: RunnerView.Running,
    memberKey: String,
): List<PreviousSet> =
    when (val entry = view.snapshot.entryIndex[memberKey]?.let(view.session.entries::getOrNull)) {
        is SessionEntry.Reps -> entry.sets.map { PreviousSet.Reps(it.reps, it.weightKg) }
        is SessionEntry.TimedHold -> entry.sets.map { PreviousSet.Hold(it.holdSec) }
        else -> emptyList()
    }

/** "Sets ⌄" in the header's action row: opens the sheet. Named by its word. */
@Composable
internal fun SetsButton(open: () -> Unit) {
    Row(
        Modifier
            .sizeIn(minWidth = MinTarget, minHeight = MinTarget)
            .clickable(role = Role.Button, onClick = open)
            .padding(end = Spacing.two),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.one),
    ) {
        Text(stringResource(R.string.session_header_sets), style = SlotLabel, color = runnerColors.textSecondary)
        TypeIcon(R.drawable.icon_expand, KettleType.small, runnerColors.textSecondary)
    }
}

/** The sheet itself, as the canvas draws it: the name and counts with Done, the rows, Add, the footer. */
@Composable
internal fun SetsSheet(
    step: WorkStep,
    model: SetsSheetModel,
    context: StepContext,
    onClose: () -> Unit,
) {
    val total = model.rows.size
    OverlaySheet(null, onClose, step.exerciseName) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.two)) {
            Column(Modifier.weight(1f)) {
                Text(
                    step.exerciseName,
                    Modifier.semantics { heading() },
                    style = KettleType.heading,
                    color = runnerColors.text,
                )
                Secondary(
                    pluralText(
                        R.plurals.session_setsSheet_summary,
                        R.string.session_setsSheet_summary_zero,
                        total,
                        "${model.planned}",
                    ),
                )
            }
            OutlinedPill(stringResource(R.string.session_done), onClose, filled = true)
        }
        Column(Modifier.padding(top = Spacing.two)) {
            model.rows.forEachIndexed { index, row ->
                ListHairline()
                SetRowView(step, row, context, removable = model.canRemoveLast && index == model.rows.lastIndex)
            }
        }
        OutlineButton(
            stringResource(R.string.session_setsSheet_add),
            { context.send(RunnerCommand.AddSet) },
            Modifier.fillMaxWidth().padding(top = Spacing.one),
            look = ButtonLook.Compact,
        )
        Secondary(
            pluralText(
                R.plurals.session_setsSheet_footer,
                R.string.session_setsSheet_footer_zero,
                model.planned.toInt(),
            ),
            Modifier.padding(top = Spacing.two),
        )
    }
}

@Composable
private fun SetRowView(
    step: WorkStep,
    row: SetRow,
    context: StepContext,
    removable: Boolean,
) {
    val resources = LocalResources.current
    val main =
        when (val set = row.logged) {
            is PreviousSet.Reps -> {
                val kg = set.weightKg?.takeIf { it > 0 }
                // "8 reps" for bodyweight: a bare number on its own row read as nothing in particular.
                if (kg == null) {
                    resources.reps(set.reps)
                } else {
                    stringResource(
                        R.string.session_setsSheet_loadedSet,
                        "${set.reps}",
                        resources.weightText(kg, context.units),
                    )
                }
            }

            is PreviousSet.Hold -> {
                resources.holdSeconds(set.holdSec)
            }

            null -> {
                resources.plannedText(step, context)
            }
        }
    val status =
        stringResource(
            when (row.status) {
                SetStatus.LOGGED -> R.string.session_setsSheet_logged
                SetStatus.NOW -> R.string.session_setsSheet_now
                SetStatus.PLANNED -> R.string.session_setsSheet_planned
                SetStatus.ADDED -> R.string.session_setsSheet_added
            },
        )
    Row(
        Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.three),
    ) {
        Row(
            Modifier.weight(1f).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.three),
        ) {
            SetDot(row)
            Column {
                Text(main, style = KettleType.default, color = runnerColors.text)
                Text(status, style = KettleType.small, color = runnerColors.textSecondary)
            }
        }
        if (removable) {
            OutlinedPill(
                stringResource(R.string.session_setsSheet_remove),
                { context.send(RunnerCommand.DropSet) },
            )
        }
    }
}

/** The set's number in a dot: filled once logged, ringed while on screen, outlined while planned. */
@Composable
private fun SetDot(row: SetRow) {
    val (fill, ink, edge) =
        when (row.status) {
            SetStatus.LOGGED -> Triple(runnerColors.text, runnerColors.background, null)
            SetStatus.NOW -> Triple(null, runnerColors.text, runnerColors.text)
            else -> Triple(null, runnerColors.textSecondary, runnerColors.outline)
        }
    // The circle grows with the number: at a fixed 28dp a 2× number filled it and sat off centre. Its
    // line is centred and trimmed to the glyphs, so the number sits in the middle at any size.
    val style = DotNumber
    val side = with(LocalDensity.current) { maxOf(DOT, style.fontSize.toDp() + DOT_PAD * 2) }
    Box(
        Modifier
            .size(side)
            .clip(CircleShape)
            .then(if (fill != null) Modifier.background(fill) else Modifier)
            .then(if (edge != null) Modifier.border(DOT_EDGE, edge, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text("${row.number}", style = style, color = ink, textAlign = TextAlign.Center)
    }
}

/** A hairline between rows, the list's own: no card, as the runner's lists are rows. */
@Composable
private fun ListHairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(runnerColors.border))
}

private fun Resources.holdSeconds(sec: Double): String = getString(R.string.format_seconds, jsNumber(sec))

/** A set still to do: its load and rep range for reps ("48.5 lb · 8–12"), its target for a hold. */
private fun Resources.plannedText(
    step: WorkStep,
    context: StepContext,
): String =
    when (step) {
        is RunnerStep.Reps -> {
            val kg =
                context.snapshot.inputs.weightKg
                    .takeIf { it > 0 }
            listOfNotNull(
                kg?.let { weightText(it, context.units) },
                repsTargetText(repsTarget(step)),
            ).joinToString(" · ")
        }

        is RunnerStep.Hold -> {
            holdTarget(step)?.let { holdTargetText(it) }.orEmpty()
        }

        else -> {
            ""
        }
    }

private val ROW_HEIGHT = 56.dp
private val DOT = 28.dp
private val DOT_EDGE = 1.5.dp
private val DOT_PAD = 7.dp
private val DotNumber =
    KettleType.smallMedium.copy(
        lineHeight = KettleType.smallMedium.fontSize,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    )
