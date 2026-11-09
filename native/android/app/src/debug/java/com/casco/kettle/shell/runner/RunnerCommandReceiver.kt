package com.casco.kettle.shell.runner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.casco.kettle.domain.RunnerCommand
import org.json.JSONObject

/**
 * Lets adb drive a running session, for `scripts/session-check.sh` (#315): the commands a tap would
 * send, with the screen off and the phone locked, and a report of where the session is.
 *
 *     adb shell am broadcast --es cmd report \
 *         -n com.casco.kettle.dev/com.casco.kettle.shell.runner.RunnerCommandReceiver
 *
 * `cmd` is one of advance, prev, pause, add30, finish, visible (with `--ez value`) or report. The
 * answer comes back as the broadcast's result data, which `am broadcast` prints. There's no Done or
 * leave: each also closes the runner's route, which only the screen can, so the script presses Back,
 * which is Done on the completion and leaves a count-in. Dismissing from here alone left an empty
 * runner on top that dropped every navigate after it.
 *
 * Nothing opens a session from here: a foreground service may only start from the visible runner, so
 * the script opens one with a navigate message, as a tap on Start would. And Advance takes its visit
 * from the screen, unless `--el visit` names one: then the engine refuses a stale one, as it would a
 * late tap, which is what lets the script check it does.
 *
 * Debug only, and behind the same permission as `NavigateReceiver`.
 */
class RunnerCommandReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        resultData =
            when (val cmd = intent.getStringExtra("cmd")) {
                "advance" -> advance(intent)
                "prev" -> send(RunnerCommand.Prev)
                "pause" -> send(RunnerCommand.TogglePause)
                "add30" -> send(RunnerCommand.AddRestSeconds(REST_BUMP_SEC))
                "finish" -> send(RunnerCommand.Finish)
                "visible" -> RunnerHost.setRunnerVisible(intent.getBooleanExtra("value", true)).let { "ok" }
                "report" -> report()
                else -> "unknown cmd: $cmd"
            }
    }

    private fun advance(intent: Intent): String {
        val running = RunnerHost.active as? RunnerView.Running ?: return "refused: not running"
        val visit = if (intent.hasExtra("visit")) intent.getLongExtra("visit", 0) else running.snapshot.visit
        return send(RunnerCommand.Advance(visit))
    }

    private fun send(command: RunnerCommand): String {
        if (RunnerHost.session == null) return "refused: no session"
        RunnerHost.send(command)
        return "ok"
    }

    private fun report(): String {
        val json = JSONObject()
        when (val view = RunnerHost.view.value) {
            RunnerView.Idle -> {
                json.put("view", "Idle")
            }

            is RunnerView.CountingIn -> {
                json.put("view", "CountingIn")
            }

            is RunnerView.Running -> {
                val snapshot = view.snapshot
                json
                    .put("view", "Running")
                    .put("sessionId", snapshot.sessionId)
                    .put("stepIndex", snapshot.stepIndex)
                    .put("step", snapshot.step?.let { it::class.simpleName } ?: "None")
                    .put("visit", snapshot.visit)
                    .put("paused", snapshot.clock.paused)
            }

            is RunnerView.Completed -> {
                json.put("view", "Completed").put("backup", view.backup.name)
            }
        }
        return json
            .put("visible", RunnerHost.visible)
            .put("recovery", LaunchRecovery.state.value?.let { it::class.simpleName } ?: "None")
            .toString()
    }
}

private const val REST_BUMP_SEC = 30.0
