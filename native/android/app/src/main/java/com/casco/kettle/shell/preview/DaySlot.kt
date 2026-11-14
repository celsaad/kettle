package com.casco.kettle.shell.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Program
import com.casco.kettle.domain.ProgramWeek
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.SessionParams
import com.casco.kettle.domain.WorkStep
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.findProgramWeek
import com.casco.kettle.domain.overrideNote
import com.casco.kettle.shell.R
import com.casco.kettle.shell.programdetail.overrideLines
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * The program day a preview is for (#439): its program, found or not, and its week as the file has
 * it, found the way the runner's resolver finds it, so the note and changes shown are the ones that
 * run. [library] reads the changes' names.
 */
internal class DaySlot(
    val program: Program?,
    val week: Long,
    val day: String?,
    val programWeek: ProgramWeek?,
    val library: Library,
)

internal fun daySlot(
    params: SessionParams.ProgramSlot,
    library: Library,
): DaySlot {
    val program = library.programs.firstOrNull { it.id == params.programId }
    return DaySlot(
        program,
        params.week,
        params.day,
        program?.let { findProgramWeek(it, params.week, params.day) },
        library,
    )
}

/**
 * Under the header, for a program day: the day's note in full, then what the program changes in it,
 * one line per change in Program detail's format, and that Edit reaches the workout, not the change.
 */
@Composable
internal fun DayNotes(
    slot: DaySlot,
    workout: Workout,
) {
    val colors = KettleTheme.colors
    val week = slot.programWeek ?: return
    // User data, verbatim, and in full: the preview is where a day's note is read.
    week.notes?.takeIf { it.isNotBlank() }?.let {
        Text(it, Modifier.padding(top = Spacing.two), style = KettleType.default, color = colors.text)
    }
    val overrides = (week as? ProgramWeek.Training)?.overrides.orEmpty()
    if (overrides.isEmpty()) return
    Column(Modifier.padding(top = Spacing.three), verticalArrangement = Arrangement.spacedBy(Spacing.half)) {
        Text(
            stringResource(R.string.workoutPreview_changesTitle),
            Modifier.semantics { heading() },
            style = KettleType.heading,
            color = colors.text,
        )
        overrides.forEach { override ->
            overrideLines(overrideNote(override, slot.library, workout)).forEach {
                Text(it, style = KettleType.small, color = colors.text)
            }
        }
        Text(
            stringResource(R.string.workoutPreview_changesHint),
            Modifier.padding(top = Spacing.half),
            style = KettleType.small,
            color = colors.textSecondary,
        )
    }
}

/**
 * Each exercise's rest between its sets, by member key: the rest step after its first set. Keys are per
 * block, and a circuit's rests carry one of their own, so neither a Rest block nor a circuit's rest
 * lands on an exercise; the outline only asks for an exercise outside a circuit.
 */
internal fun restsBetweenSets(steps: List<RunnerStep>): Map<String, Double> =
    steps
        .zipWithNext()
        .mapNotNull { (work, rest) ->
            if (work is WorkStep && rest is RunnerStep.Rest && rest.memberKey == work.memberKey) {
                work.memberKey to rest.seconds
            } else {
                null
            }
        }.distinctBy { it.first }
        .toMap()
