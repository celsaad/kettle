package com.casco.kettle.shell.runner

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.casco.kettle.domain.LoggedUnit
import com.casco.kettle.domain.PreviousSet
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerSnapshot
import com.casco.kettle.domain.RunnerState
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.StepPreview
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.beatsPersonalBest
import com.casco.kettle.domain.canAddSet
import com.casco.kettle.domain.canDropSet
import com.casco.kettle.domain.elapsedSec
import com.casco.kettle.domain.offeredLoadKg
import com.casco.kettle.domain.previousSet
import com.casco.kettle.domain.progressionLoadKg
import com.casco.kettle.domain.remainingSec
import com.casco.kettle.domain.swapCandidates
import com.casco.kettle.domain.upcomingPreview
import com.casco.kettle.shell.R

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
    /** "Try 53.5 lb" (#410): the load double progression suggests, null when there's none worth a tap. */
    val tryKg: Double? = null,
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
    /** What Undo would take back, null when it has nothing to: [RunnerView.Running.logged]. */
    val logged: LoggedUnit? = null,
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
            tryKg =
                offeredLoadKg(
                    (snapshot.step as? RunnerStep.Reps)?.let {
                        progressionLoadKg(
                            it,
                            prior,
                            environment.units,
                            view.plan.loadsSetByProgram,
                            plannedSets(view, it),
                        )
                    },
                    snapshot.inputs.weightKg,
                    environment.units,
                ),
            beatsBest = beatsPersonalBest(state, prior, now),
            next = upcomingPreview(snapshot.steps, snapshot.stepIndex),
            canAddSet = canAddSet(snapshot),
            canDropSet = canDropSet(state),
            canSwap = swapCandidates(snapshot, view.plan.exercises).isNotEmpty(),
        )
    return StepContext(snapshot, now, environment.units, readings, controls, view.logged)
}

/**
 * The header's action row (#409), borderless: Sets, which opens the Sets sheet (#410; not in a circuit,
 * where the count is the block's rounds), then Swap. Each its own 48dp target. Nothing to offer draws
 * nothing.
 */
@Composable
internal fun ActionRow(context: StepContext) {
    val readings = context.readings
    if (!readings.canAddSet && !readings.canSwap) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (readings.canAddSet) SetsButton { context.controls.open(Overlay.SETS) }
        if (readings.canSwap) {
            TextControl(
                stringResource(R.string.session_swap_action),
                { context.controls.open(Overlay.SWAP_PICKER) },
                style = SlotLabel.copy(color = runnerColors.textSecondary),
            )
        }
    }
}
