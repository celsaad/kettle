package com.casco.kettle.shell.runner

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.WorkStep
import com.casco.kettle.shell.R
import com.casco.kettle.shell.nav.routeParams
import com.casco.kettle.shell.nav.runnerStartFrom
import com.casco.kettle.shell.rn.JsBridge
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/** What `shellRoutes` registers at the native runner's path while the flip is on. */
@Composable
fun RunnerPlaceholderRoute(params: Map<String, String>) {
    val context = LocalContext.current
    val view by RunnerHost.view.collectAsState()
    // The alert's gate and the state file's flush both hang on this (#305); #306's screen reports it too.
    LifecycleStartEffect(Unit) {
        RunnerHost.setRunnerVisible(true)
        onStopOrDispose { RunnerHost.setRunnerVisible(false) }
    }
    RunnerPlaceholder(
        params,
        view,
        start = { RunnerHost.open(context, it) },
        send = RunnerHost::send,
        onDone = {
            // Leaving the count-in stops the service with nothing written; a running session goes on
            // in the service, which is what this stand-in exists to drive.
            RunnerHost.leave()
            RunnerHost.dismiss()
            JsBridge.runnerDone()
        },
    )
}

/**
 * The native runner's stand-in (#302), on `.dev` alone: proof of the way in and out, plus, until #306's
 * screen replaces it, the controls that drive #305's service on a device. It reads its start back from
 * the route and shows it as that start's params, one `key=value` line each, so route-check can match
 * them exactly and a start lost on the way shows as nothing. Its control labels are debug text, not
 * copy: no user ever sees this screen.
 */
@Composable
internal fun RunnerPlaceholder(
    params: Map<String, String>,
    view: RunnerView,
    start: (RunnerStart?) -> Unit,
    send: (RunnerCommand) -> Unit,
    onDone: () -> Unit,
) {
    val colors = KettleTheme.colors
    val routeStart = remember(params) { runnerStartFrom(params) }
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(Spacing.three), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.two)) {
            for ((key, value) in routeStart?.routeParams().orEmpty()) {
                Text("$key=$value", style = KettleType.code, color = colors.textSecondary)
            }
            Text(describe(view), style = KettleType.code, color = colors.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.two)) {
                val running = view as? RunnerView.Running
                DebugButton("Start", Modifier.weight(1f)) { start(routeStart) }
                DebugButton(
                    "Log/Next",
                    Modifier.weight(1f),
                ) { running?.let { send(RunnerCommand.Advance(it.snapshot.visit)) } }
                DebugButton("Pause", Modifier.weight(1f)) { send(RunnerCommand.TogglePause) }
                DebugButton("Finish", Modifier.weight(1f)) { send(RunnerCommand.Finish) }
            }
            DebugButton(
                stringResource(R.string.common_done),
                Modifier.padding(top = Spacing.three).fillMaxWidth(),
                onDone,
            )
        }
    }
}

/** The view as one line of debug text: enough to see a session move on a device. */
private fun describe(view: RunnerView): String =
    when (view) {
        RunnerView.Idle -> {
            "idle"
        }

        is RunnerView.CountingIn -> {
            "counting in"
        }

        is RunnerView.Running -> {
            val snapshot = view.snapshot
            val step = snapshot.step
            val name = (step as? WorkStep)?.exerciseName ?: step?.let { "rest" } ?: "parked"
            val paused = if (snapshot.clock.paused) " paused" else ""
            "step ${snapshot.stepIndex + 1}/${snapshot.steps.size} $name, ${view.session.entries.size} entries$paused"
        }

        is RunnerView.Completed -> {
            "complete, ${view.session.entries.size} entries, backup ${view.backup.name.lowercase()}"
        }
    }

@Composable
private fun DebugButton(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = KettleTheme.colors
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(DoneShape)
            .border(1.dp, colors.border, DoneShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = KettleType.heading, color = colors.accentText)
    }
}

private val DoneShape = RoundedCornerShape(12.dp)
