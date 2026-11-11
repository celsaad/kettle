package com.casco.kettle.shell.runner

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.casco.kettle.domain.PreviousSet
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerSnapshot
import com.casco.kettle.domain.RunnerState
import com.casco.kettle.domain.StepPreview
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.beatsPersonalBest
import com.casco.kettle.domain.canAddSet
import com.casco.kettle.domain.canDropSet
import com.casco.kettle.domain.elapsedSec
import com.casco.kettle.domain.previousSet
import com.casco.kettle.domain.remainingSec
import com.casco.kettle.domain.swapCandidates
import com.casco.kettle.domain.upcomingPreview
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.RunnerColors

/** What the number pad is opened for: its label, the value it starts from, and where the value goes. */
data class PadRequest(
    val label: String,
    val seed: Double,
    val allowDecimal: Boolean,
    val unit: String?,
    val confirm: (Double) -> Unit,
)

/** What a step screen shows beside the step: `:shared`'s selectors over the snapshot and the log. */
data class StepReadings(
    val previous: PreviousSet?,
    val beatsBest: Boolean,
    val next: StepPreview?,
    val canAddSet: Boolean,
    val canDropSet: Boolean,
    val canSwap: Boolean,
)

/** What a step screen can do: send the service a command, or open something over itself. */
class StepControls(
    val send: (RunnerCommand) -> Unit,
    val open: (Overlay) -> Unit,
    val openPad: (PadRequest) -> Unit,
    val confirmFinish: () -> Unit,
    val adopt: (exerciseId: String, weightKg: Double) -> Unit,
)

/**
 * A step screen's whole input, read at [now], the screen's own second, which only draws: the service
 * owns the time and what it logs.
 */
class StepContext(
    val snapshot: RunnerSnapshot,
    val now: Long,
    val units: UnitSystem,
    val readings: StepReadings,
    val controls: StepControls,
) {
    val elapsed: Long get() = elapsedSec(snapshot.clock, now)
    val remaining: Double get() = remainingSec(snapshot.clock, now)
    val paused: Boolean get() = snapshot.clock.paused

    fun send(command: RunnerCommand) = controls.send(command)

    /** Log, Done or Skip, aimed at the step on screen so a tap landing as it ends by itself does nothing. */
    fun advance() = send(RunnerCommand.Advance(snapshot.visit))
}

/** [view] read for its step screen. "Last time" and the PR marker read the log as it stood. */
internal fun stepContext(
    view: RunnerView.Running,
    environment: RunnerEnvironment,
    now: Long,
    controls: StepControls,
): StepContext {
    val snapshot = view.snapshot
    val state = RunnerState(snapshot, view.session)
    val prior = environment.priorSessions
    val readings =
        StepReadings(
            previous = previousSet(snapshot, prior),
            beatsBest = beatsPersonalBest(state, prior, now),
            next = upcomingPreview(snapshot.steps, snapshot.stepIndex),
            canAddSet = canAddSet(snapshot),
            canDropSet = canDropSet(state),
            canSwap = swapCandidates(snapshot, view.plan.exercises).isNotEmpty(),
        )
    return StepContext(snapshot, now, environment.units, readings, controls)
}

/**
 * "Set 2 of 4", its −/+ (hidden in a circuit, where the count is the block's rounds; − disabled rather
 * than hidden at the floor, so the row doesn't reflow mid-set) and SWAP, each its own 48dp target.
 * No gap between them: −'s circle sits at its target's end and +'s at its start, so the pair's slack
 * falls outside it and spaces them from the label and SWAP rather than from each other.
 */
@Composable
internal fun SetCountRow(
    label: String,
    context: StepContext,
) {
    val readings = context.readings
    val sets = stringResource(R.string.session_sets_label)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f, fill = false), style = KettleType.small, color = RunnerColors.textSecondary)
        if (readings.canAddSet) {
            SymbolButton(R.drawable.icon_remove, stringResource(R.string.common_decrease, sets), {
                context.send(RunnerCommand.DropSet)
            }, readings.canDropSet, SymbolLook.Small.copy(placement = Placement.END))
            SymbolButton(R.drawable.icon_add, stringResource(R.string.common_increase, sets), {
                context.send(RunnerCommand.AddSet)
            }, look = SymbolLook.Small.copy(placement = Placement.START))
        }
        if (readings.canSwap) {
            PillControl(stringResource(R.string.session_swap_action)) { context.controls.open(Overlay.SWAP_PICKER) }
        }
    }
}
