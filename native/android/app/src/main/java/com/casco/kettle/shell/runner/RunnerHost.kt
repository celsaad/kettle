package com.casco.kettle.shell.runner

import android.content.Context
import androidx.core.content.ContextCompat
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The running session, for the life of the process (#297): what the runner's screen observes and
 * sends commands to, whichever activity it's in, since [RunnerService] outlives any of them. One
 * session at a time: a runner opened while one runs attaches to it and its start is ignored.
 */
object RunnerHost {
    private val views = MutableStateFlow<RunnerView>(RunnerView.Idle)

    /** Changes only on transitions (#295). */
    val view: StateFlow<RunnerView> = views.asStateFlow()

    /** The service's, while it runs one. */
    @Volatile internal var session: RunnerSession? = null

    @Volatile private var runnerVisible = false

    /**
     * The service wrote the running session, on the main thread: native screens read the log again,
     * so Home's next-up and History follow (#297). The current activity's, like `JsBridge.onRoute`.
     */
    var onSessionWritten: (() -> Unit)? = null

    val active: RunnerView.Active? get() = views.value as? RunnerView.Active

    /**
     * Starts [start] in the service, which counts in and resolves it again itself, or attaches to
     * the session already running and ignores [start]. Called from the visible runner, which is what
     * lets a foreground service start (#293). [anyway] is "Start anyway" on a workout too long to run
     * in full.
     */
    fun open(
        context: Context,
        start: RunnerStart?,
        anyway: Boolean = false,
    ) {
        if (active != null) return
        // A completion nobody dismissed is behind this start, not part of it.
        dismiss()
        ContextCompat.startForegroundService(context, RunnerService.startIntent(context, start, anyway))
    }

    fun send(command: RunnerCommand) {
        session?.send(command)
    }

    /** Leaves during the count-in, which stops the service with nothing written; false once it runs. */
    fun leave(): Boolean = session?.leave() ?: false

    /** What the runner's screen reports as it comes and goes (#306), held for a session started after. */
    fun setRunnerVisible(visible: Boolean) {
        runnerVisible = visible
        session?.setRunnerVisible(visible)
    }

    /** The completion screen's Done: the session is behind us. */
    fun dismiss() {
        if (views.value is RunnerView.Completed) views.value = RunnerView.Idle
    }

    internal val visible: Boolean get() = runnerVisible

    internal fun publish(view: RunnerView) {
        views.value = view
    }
}
