package com.casco.kettle.shell.runner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.WorkStep
import com.casco.kettle.domain.endProgress
import com.casco.kettle.domain.swapChoices
import com.casco.kettle.shell.theme.MaxContentWidth
import com.casco.kettle.shell.theme.RunnerBarIcons
import com.casco.kettle.shell.theme.Spacing

/** What the runner reads besides the service's view: the log as it stands, and two preferences. */
class RunnerEnvironment(
    /** The logged sessions. The running one has no end yet, which every selector here skips. */
    val priorSessions: List<Session>,
    val units: UnitSystem,
    val soundsOn: Boolean,
)

/**
 * What the runner asks of the shell, as parameters so a test hears each. [leave] is leaving before a
 * session runs, which writes nothing; [done] is the completion screen's Done. Both hand back to RN.
 */
class RunnerActions(
    val send: (RunnerCommand) -> Unit,
    val startAnyway: () -> Unit,
    val leave: () -> Unit,
    val done: () -> Unit,
    val adopt: (exerciseId: String, weightKg: Double) -> Unit,
)

/**
 * The native runner (#306): every screen, from what its route decided ([entry]) at [now], the screen's
 * own second. It reads only the service's view and sends commands; nothing here keeps the session.
 */
@Composable
fun RunnerScreen(
    entry: RunnerEntry,
    environment: RunnerEnvironment,
    now: Long,
    actions: RunnerActions,
) {
    var overlays by remember { mutableStateOf(RunnerOverlays()) }
    var pad by remember { mutableStateOf<PadRequest?>(null) }
    val running = (entry as? RunnerEntry.Attach)?.view as? RunnerView.Running
    // Bound to the step they opened on: once the visit moves, by a tap or by the step's own clock,
    // they are gone, and stay gone if Prev comes back to it.
    // The swap picker too once there's nothing left to swap to: it draws nothing then, and left marked
    // open it would keep the body hidden from a reader and take the next back for a sheet nobody sees.
    val open = running?.let { overlays.on(it.snapshot.visit).withoutDeadSwap(it) } ?: RunnerOverlays()
    // The visit an EMOM minute was called done on (#417): blue until the minute turns, which moves the
    // visit. The engine never hears it. Saved with the screen, and the visit is restored with the
    // snapshot, so a minute rested out before a process death comes back still resting.
    var restedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    val resting = restedAt != null && restedAt == running?.snapshot?.visit
    SideEffect {
        if (open != overlays) overlays = open
    }
    // Read when asked, not when composed: a lambda holding an earlier composition's view would bind the
    // dialog to a visit already left, and it would close as it opened.
    val current by rememberUpdatedState(running)
    val currentActions by rememberUpdatedState(actions)
    val adopting = remember { AdoptingSend({ currentActions }, { current }) }
    val send = adopting::send
    // End early is an overlay like the rest (#417): it closes once its step is left, so a rest ending
    // under it can't have Save and end log the next step's seeded target as a set (#317's review).
    val askToFinish: () -> Unit = {
        current?.snapshot?.visit?.let { overlays = overlays.open(Overlay.END, it) }
    }
    val close = { overlay: Overlay -> overlays = overlays.close(overlay) }
    RunnerBack(entry, { visit -> overlays.on(visit).open }, close, askToFinish, actions)
    val controlsFor = { view: RunnerView.Running ->
        StepControls(
            send = send,
            open = { overlays = overlays.open(it, view.snapshot.visit) },
            openPad = {
                pad = it
                overlays = overlays.open(Overlay.PAD, view.snapshot.visit)
            },
            confirmFinish = askToFinish,
            adopt = { id, kg -> adopting.pickLoad(id, kg, view.snapshot.visit) },
            // The one reading the field is drawn from, so the header and the colour can't disagree.
            minute = MinuteRest(resting) { restedAt = view.snapshot.visit },
        )
    }
    val field = runnerField(entry, now, resting)
    RunnerBarIcons(field.darkBarIcons)
    Box(
        Modifier.fillMaxSize().background(field.palette.background).safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        CompositionLocalProvider(LocalRunnerPalette provides field.palette) {
            Box(Modifier.widthIn(max = MaxContentWidth).fillMaxSize().padding(horizontal = Spacing.three)) {
                RunnerBody(entry, environment, now, actions) { view ->
                    Frame(view, stepContext(view, environment, now, controlsFor(view)), open.open)
                }
            }
        }
        // The sheets are surfaces of their own, drawn on pace's whatever field is under them: a pad
        // turning orange as its rest ran out under it would be the field changing something it doesn't own.
        if (running != null) {
            CompositionLocalProvider(LocalRunnerPalette provides RunnerField.PACE.palette) {
                Overlays(stepContext(running, environment, now, controlsFor(running)), running, open.open, pad, close)
            }
        }
    }
}

/** The screen [entry] calls for. A running session's is [frame], which owns the overlays' state. */
@Composable
private fun RunnerBody(
    entry: RunnerEntry,
    environment: RunnerEnvironment,
    now: Long,
    actions: RunnerActions,
    frame: @Composable (RunnerView.Running) -> Unit,
) {
    when (entry) {
        RunnerEntry.Loading, RunnerEntry.Start, RunnerEntry.Waiting -> {}

        is RunnerEntry.Before -> {
            Before(entry.plan, actions)
        }

        is RunnerEntry.Complete -> {
            Completion(entry.view, environment, actions.done)
        }

        is RunnerEntry.Attach -> {
            when (val view = entry.view) {
                is RunnerView.CountingIn -> CountIn(view, environment, now)
                is RunnerView.Running -> frame(view)
            }
        }
    }
}

/**
 * Back, as [backAction] decides it (#297). [openOn] and the entry are read when back lands, not when
 * this composed: a handler holding an earlier composition's overlays asked "Finish session?" with the
 * pad still open.
 */
@Composable
private fun RunnerBack(
    entry: RunnerEntry,
    openOn: (visit: Long) -> Set<Overlay>,
    close: (Overlay) -> Unit,
    confirmFinish: () -> Unit,
    actions: RunnerActions,
) {
    val current by rememberUpdatedState(entry)
    BackHandler {
        val visit = ((current as? RunnerEntry.Attach)?.view as? RunnerView.Running)?.snapshot?.visit
        when (val action = backAction(current, visit?.let(openOn).orEmpty())) {
            is BackAction.Close -> close(action.overlay)
            BackAction.ConfirmFinish -> confirmFinish()
            BackAction.Leave -> actions.leave()
            BackAction.Done -> actions.done()
        }
    }
}

/** The pad, Coming up, the pickers and End early, over the body, which [Frame] hides from accessibility meanwhile. */
@Composable
private fun Overlays(
    context: StepContext,
    view: RunnerView.Running,
    open: Set<Overlay>,
    pad: PadRequest?,
    close: (Overlay) -> Unit,
) {
    val snapshot = view.snapshot
    val step = snapshot.step
    if (Overlay.UPCOMING in open && step != null) {
        ComingUp(view, !snapshot.adhoc) { close(Overlay.UPCOMING) }
    }
    if (Overlay.ADD_PICKER in open) {
        ExercisePicker(addCandidates(view.plan.exercises), { close(Overlay.ADD_PICKER) }) {
            close(Overlay.ADD_PICKER)
            context.send(RunnerCommand.AddExercise(it))
        }
    }
    val swaps = swapChoices(snapshot, view.plan.exercises)
    if (Overlay.SWAP_PICKER in open && step is WorkStep && swaps != null) {
        SwapSheet(swaps, context.units, { close(Overlay.SWAP_PICKER) }) {
            close(Overlay.SWAP_PICKER)
            context.send(RunnerCommand.Swap(it))
        }
    }
    if (Overlay.SETS in open && step is WorkStep) {
        setsSheet(view)?.let { model ->
            SetsSheet(step, model, context) { close(Overlay.SETS) }
        }
    }
    if (Overlay.PAD in open && pad != null) {
        NumberPad(pad) { close(Overlay.PAD) }
    }
    if (Overlay.END in open) {
        EndSheet(endProgress(snapshot), { close(Overlay.END) }) {
            close(Overlay.END)
            // Aimed at the step it was asked on: should that step end before this lands, the engine
            // ends the session without logging the one that replaced it (#319).
            context.send(RunnerCommand.Finish(snapshot.visit))
        }
    }
}

private fun RunnerOverlays.withoutDeadSwap(view: RunnerView.Running): RunnerOverlays {
    val swappable = view.snapshot.step is WorkStep && swapChoices(view.snapshot, view.plan.exercises) != null
    return if (swappable) this else close(Overlay.SWAP_PICKER)
}

/** Every exercise but the built-in rest: what an ad-hoc session can add. */
private fun addCandidates(exercises: List<Exercise>) = exercises.filter { it.config !is ExerciseConfig.Rest }
