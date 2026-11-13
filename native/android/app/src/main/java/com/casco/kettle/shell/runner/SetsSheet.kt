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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.casco.kettle.domain.RunnerState
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.WorkStep
import com.casco.kettle.domain.canDropSet
import com.casco.kettle.domain.holdTarget
import com.casco.kettle.domain.repsTarget
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.list.ListRow
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.TypeIcon

// The Sets sheet (#410): this exercise's sets this session, logged, now and planned, where a set is
// added or the last planned one removed. The same AddSet and DropSet as the −/+ it replaces, and their
// rules (no circuit, never below what's logged and on screen), so nothing new reaches the engine.

/** One set in the sheet, numbered within its member. */
internal sealed interface SetRow {
    val number: Int

    data class Logged(
        override val number: Int,
        val set: PreviousSet,
    ) : SetRow

    data class Now(
        override val number: Int,
    ) : SetRow

    /** Still to do; [added] when it's past the count the session was planned with. */
    data class Planned(
        override val number: Int,
        val added: Boolean,
    ) : SetRow
}

internal data class SetsSheetModel(
    val rows: List<SetRow>,
    /**
     * The count the session was planned with, null when the plan holds no such member: an ad-hoc
     * session's, or one added mid-session, has no workout to say "still says" of, and nothing is
     * "added" to it.
     */
    val planned: Long?,
    /** Remove on the last row: the engine's DropSet rule, never below the sets logged and on screen. */
    val canRemoveLast: Boolean,
)

/**
 * The step on screen's member as the sheet lists it: its logged sets from the session file (under a
 * swap's key and the one before it alike, so the numbering matches the header's), the one on screen,
 * then the ones still planned. Null off a reps or hold step.
 */
internal fun setsSheet(view: RunnerView.Running): SetsSheetModel? {
    val snapshot = view.snapshot
    val step = snapshot.step
    if (step !is RunnerStep.Reps && step !is RunnerStep.Hold) return null
    val logged = loggedSets(view, baseKey(step.memberKey))
    val ahead = snapshot.steps.drop(snapshot.stepIndex).count { it is WorkStep && it.memberKey == step.memberKey }
    val planned = plannedSets(view, step)
    val rows =
        logged.mapIndexed { i, set -> SetRow.Logged(i + 1, set) } +
            (1..ahead).map { k ->
                val number = logged.size + k
                if (k == 1) SetRow.Now(number) else SetRow.Planned(number, added = planned != null && number > planned)
            }
    return SetsSheetModel(rows, planned, canRemoveLast = canDropSet(RunnerState(snapshot, view.session)))
}

/**
 * [step]'s member's set count as the plan built it, before any Sets edit (which rewrite the live step's
 * `setTotal`): looked up by the member's base key, since a swap renames what's left `<key>~swap<n>`.
 * Null when the plan has no such member (an ad-hoc session, an exercise added mid-session).
 */
internal fun plannedSets(
    view: RunnerView.Running,
    step: WorkStep,
): Long? {
    val base = baseKey(step.memberKey)
    return view.plan.steps
        .filterIsInstance<WorkStep>()
        .firstOrNull { it.memberKey == base }
        ?.setTotal
}

/** The key a member was planned under: a swap only ever appends `~swap<n>` to it. */
private fun baseKey(memberKey: String): String = memberKey.substringBefore(SWAP_MARK)

/** Every set logged under [base] or a swap of it, in the order the entries were started. */
private fun loggedSets(
    view: RunnerView.Running,
    base: String,
): List<PreviousSet> =
    view.snapshot.entryIndex
        .filterKeys { it == base || it.startsWith(base + SWAP_MARK) }
        .values
        .sorted()
        .flatMap { index ->
            when (val entry = view.session.entries.getOrNull(index)) {
                is SessionEntry.Reps -> entry.sets.map { PreviousSet.Reps(it.reps, it.weightKg) }
                is SessionEntry.TimedHold -> entry.sets.map { PreviousSet.Hold(it.holdSec) }
                else -> emptyList()
            }
        }

private const val SWAP_MARK = "~swap"

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
    val planned = model.planned
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
                    if (planned != null) {
                        pluralText(
                            R.plurals.session_setsSheet_summary,
                            R.string.session_setsSheet_summary_zero,
                            total,
                            "$planned",
                        )
                    } else {
                        pluralText(R.plurals.session_setsSheet_count, R.string.session_setsSheet_count_zero, total)
                    },
                )
            }
            FilledPill(stringResource(R.string.session_done), onClose)
        }
        Column(Modifier.padding(top = Spacing.two)) {
            model.rows.forEachIndexed { index, row ->
                if (index > 0) ListRowSeparator()
                SetRowView(step, row, context, removable = model.canRemoveLast && index == model.rows.lastIndex)
            }
        }
        OutlineButton(
            stringResource(R.string.session_setsSheet_add),
            { context.send(RunnerCommand.AddSet) },
            Modifier.fillMaxWidth().padding(top = Spacing.one),
            look = ButtonLook.Compact,
        )
        if (planned != null) {
            Secondary(
                pluralText(R.plurals.session_setsSheet_footer, R.string.session_setsSheet_footer_zero, planned.toInt()),
                Modifier.padding(top = Spacing.two),
            )
        }
    }
}

/** A list row, as every list in the app is: the dot, what the set is and where it stands, and Remove. */
@Composable
private fun SetRowView(
    step: WorkStep,
    row: SetRow,
    context: StepContext,
    removable: Boolean,
) {
    val resources = LocalResources.current
    val (main, status) =
        when (row) {
            is SetRow.Logged -> {
                resources.loggedText(row.set, context) to R.string.session_setsSheet_logged
            }

            is SetRow.Now -> {
                resources.plannedText(step, context) to R.string.session_setsSheet_now
            }

            is SetRow.Planned -> {
                resources.plannedText(step, context) to
                    if (row.added) R.string.session_setsSheet_added else R.string.session_setsSheet_planned
            }
        }
    ListRow {
        Row(
            Modifier.weight(1f).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.three),
        ) {
            SetDot(row)
            Column {
                Text(main, style = KettleType.default, color = runnerColors.text)
                Text(stringResource(status), style = KettleType.small, color = runnerColors.textSecondary)
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

/**
 * The set's number in a dot: filled once logged, ringed while on screen, outlined while planned. The
 * circle grows with the number: at a fixed 28dp a 2× number filled it and sat off centre. Its line is
 * centred and trimmed to the glyphs, so the number sits in the middle at any size.
 */
@Composable
private fun SetDot(row: SetRow) {
    val fill: Color?
    val ink: Color
    val edge: Color?
    when (row) {
        is SetRow.Logged -> {
            fill = runnerColors.text
            ink = runnerColors.background
            edge = null
        }

        is SetRow.Now -> {
            fill = null
            ink = runnerColors.text
            edge = runnerColors.text
        }

        is SetRow.Planned -> {
            fill = null
            ink = runnerColors.textSecondary
            edge = runnerColors.outline
        }
    }
    val side = with(LocalDensity.current) { maxOf(DOT, DotNumber.fontSize.toDp() + DOT_PAD * 2) }
    Box(
        Modifier
            .size(side)
            .clip(CircleShape)
            .then(if (fill != null) Modifier.background(fill) else Modifier)
            .then(if (edge != null) Modifier.border(DOT_EDGE, edge, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text("${row.number}", style = DotNumber, color = ink, textAlign = TextAlign.Center)
    }
}

/** What a logged set was: "10 × 48.5 lb", "8 reps" for bodyweight (a bare number read as nothing), "45s". */
private fun Resources.loggedText(
    set: PreviousSet,
    context: StepContext,
): String =
    when (set) {
        is PreviousSet.Reps -> {
            val kg = set.weightKg?.takeIf { it > 0 }
            if (kg == null) {
                reps(set.reps)
            } else {
                getString(R.string.session_setsSheet_loadedSet, "${set.reps}", weightText(kg, context.units))
            }
        }

        is PreviousSet.Hold -> {
            seconds(set.holdSec)
        }
    }

/** A set still to do: its load and rep range for reps ("48.5 lb · 8–12"), its target for a hold. */
private fun Resources.plannedText(
    step: WorkStep,
    context: StepContext,
): String =
    if (step is RunnerStep.Reps) {
        val kg =
            context.snapshot.inputs.weightKg
                .takeIf { it > 0 }
        listOfNotNull(kg?.let { weightText(it, context.units) }, repsTargetText(repsTarget(step))).joinToString(" · ")
    } else {
        (step as? RunnerStep.Hold)?.let(::holdTarget)?.let { holdTargetText(it) }.orEmpty()
    }

private val DOT = 28.dp
private val DOT_EDGE = 1.5.dp
private val DOT_PAD = 7.dp
private val DotNumber =
    KettleType.smallMedium.copy(
        lineHeight = KettleType.smallMedium.fontSize,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    )
