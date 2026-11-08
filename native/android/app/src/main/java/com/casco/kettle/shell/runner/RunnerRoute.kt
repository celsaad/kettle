package com.casco.kettle.shell.runner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.preferencesOrDefault
import com.casco.kettle.domain.resolveRunnerPlan
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.i18n.deviceUnitSystem
import com.casco.kettle.shell.nav.runnerStartFrom
import com.casco.kettle.shell.rn.JsBridge
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

/** What `shellRoutes` registers at the native runner's path while the flip is on (#302, #306). */
@Composable
fun RunnerRoute(params: Map<String, String>) {
    val context = LocalContext.current
    val files = LocalLatestFiles.current
    val view by RunnerHost.view.collectAsState()
    val start = remember(params) { runnerStartFrom(params) }
    val plan = remember(start, files.library) { files.library?.let { resolveRunnerPlan(start, it) } }
    // Saved, so a configuration change mid-wait can't ask the service a second time.
    var opened by rememberSaveable { mutableStateOf(false) }
    // Whether this route ever showed the service's session, started here or attached to. After Done or a
    // leave the view is Idle again while RN pops the route: that mustn't start a session for a route
    // that attached, nor time out into a second hand-back.
    var answered by rememberSaveable { mutableStateOf(false) }
    val entry = runnerEntry(view, plan, opened, answered)
    SideEffect { if (entry is RunnerEntry.Attach || entry is RunnerEntry.Complete) answered = true }
    val notifications =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Never blocking: a refusal only costs the shade's notification and the step alerts.
        }
    val ask = {
        opened = true
        if (context.needsNotificationPermission()) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(entry) {
        when (entry) {
            RunnerEntry.Start -> {
                ask()
                RunnerHost.open(context, start)
            }

            // The service refused the start, which publishes nothing (the library changed under it, or
            // it couldn't read the file): leave rather than wait on a blank screen.
            RunnerEntry.Waiting -> {
                delay(SERVICE_ANSWER_MS)
                if (!answered && RunnerHost.view.value == RunnerView.Idle) leave()
            }

            else -> {}
        }
    }
    // The alert's gate and the state file's flush both hang on this (#305).
    LifecycleStartEffect(Unit) {
        RunnerHost.setRunnerVisible(true)
        onStopOrDispose { RunnerHost.setRunnerVisible(false) }
    }
    // Set when this screen sends Finish, so the completion it leads to doesn't buzz as an advance would.
    val finishing = remember { AtomicBoolean(false) }
    RunnerWindow(keepAwake = entry is RunnerEntry.Attach || entry is RunnerEntry.Complete, finishing)
    val locale = LocalConfiguration.current.locales[0]
    val preferences = preferencesOrDefault(files.preferences, deviceUnitSystem(locale))
    RunnerScreen(
        entry,
        RunnerEnvironment(
            priorSessions = files.sessionLog?.logged.orEmpty(),
            units = preferences.unitSystem,
            soundsOn = preferences.sessionSounds,
        ),
        rememberSecond(view),
        routeActions(finishing) { anyway ->
            ask()
            RunnerHost.open(context, start, anyway)
        },
    )
}

/** The shell's half of [RunnerActions]. [start] asks the service, "Start anyway" with true. */
private fun routeActions(
    finishing: AtomicBoolean,
    start: (anyway: Boolean) -> Unit,
) = RunnerActions(
    send = { command ->
        if (command == RunnerCommand.Finish) finishing.set(true)
        RunnerHost.send(command)
    },
    startAnyway = { start(true) },
    leave = ::leave,
    done = {
        RunnerHost.dismiss()
        JsBridge.runnerDone()
    },
    adopt = JsBridge::adoptLoad,
)

/** Leaving before a session runs: the service stops with nothing written, and RN takes back. */
private fun leave() {
    RunnerHost.leave()
    RunnerHost.dismiss()
    JsBridge.runnerDone()
}

/**
 * The window and the device while a session is up: the screen stays on from the count-in through
 * completion; one medium haptic on each advance, whatever the sounds setting (#301 is separate); and a
 * screen reader hears each new step once (§3.10).
 */
@Composable
private fun RunnerWindow(
    keepAwake: Boolean,
    finishing: AtomicBoolean,
) {
    val view = LocalView.current
    DisposableEffect(view, keepAwake) {
        view.keepScreenOn = keepAwake
        onDispose { view.keepScreenOn = false }
    }
    val lifecycle by rememberUpdatedState(LocalLifecycleOwner.current.lifecycle)
    LaunchedEffect(view) {
        // Collected here rather than read in composition, which pauses while the app is stopped and then
        // sees every advance since at once: an advance made while nobody watches buzzes nobody.
        var last: Pair<String, Int>? = null
        var spoken: String? = null
        RunnerHost.view.collect { shown ->
            // The advance that ends the workout buzzes like any other; a Finish doesn't, as RN's didn't.
            if (shown is RunnerView.Completed && last != null) {
                val watching = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                if (watching && !finishing.get()) view.performHapticFeedback(ADVANCE_HAPTIC)
                last = null
            }
            val running = shown as? RunnerView.Running ?: return@collect
            val snapshot = running.snapshot
            val position = snapshot.sessionId to snapshot.stepIndex
            val before = last
            last = position
            val watching = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (watching && advanced(before, position)) {
                view.performHapticFeedback(ADVANCE_HAPTIC)
            }
            val step = snapshot.step ?: return@collect
            val sentence = view.resources.announcement(step)
            if (sentence != spoken && view.context.screenReaderOn()) {
                spoken = sentence
                // Deprecated from API 36 for live regions, which speak a node's own changing text; this
                // sentence is on no node, and a hidden one for it would be one more stop to swipe past.
                @Suppress("DEPRECATION")
                view.announceForAccessibility(sentence)
            }
        }
    }
}

/** The same session's step index went up: an advance, not a Prev, a swap or a new session. */
private fun advanced(
    before: Pair<String, Int>?,
    after: Pair<String, Int>,
): Boolean = before != null && before.first == after.first && after.second > before.second

/**
 * The wall-clock time, moved on at each of the step's own second boundaries, so the seconds drawn turn
 * over when the engine's floor does. Draw-only: the service owns what is logged and when a step ends.
 */
@Composable
private fun rememberSecond(view: RunnerView): Long {
    val offset =
        when (view) {
            is RunnerView.Running -> view.snapshot.clock.let { it.phaseStartedAt + it.pausedMs }
            is RunnerView.CountingIn -> view.countIn.startedAt
            else -> 0L
        }
    val ticking = view is RunnerView.Active
    return produceState(RunnerHost.now(), offset, ticking) {
        value = RunnerHost.now()
        while (ticking) {
            val now = RunnerHost.now()
            value = now
            delay(MS_PER_SECOND - Math.floorMod(now - offset, MS_PER_SECOND))
        }
    }.value
}

private fun Context.needsNotificationPermission() =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

private fun Context.screenReaderOn(): Boolean =
    (getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.isTouchExplorationEnabled == true

/** CONFIRM is the medium tick from 30; below it, the closest the platform has. */
private val ADVANCE_HAPTIC =
    if (Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.R
    ) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.VIRTUAL_KEY
    }

private const val SERVICE_ANSWER_MS = 5000L
