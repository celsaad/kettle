package com.casco.kettle.shell.runner

import android.content.Context
import androidx.core.content.ContextCompat
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
        ContextCompat.startForegroundService(context, RunnerService.startIntent(context, start, anyway))
    }

    fun send(command: RunnerCommand) {
        session?.send(command)
    }

    /** Leaves during the count-in, which stops the service with nothing written; nothing once it runs. */
    fun leave() {
        session?.leave()
    }

    /** What the runner's screen reports as it comes and goes (#306), held for a session started after. */
    fun setRunnerVisible(visible: Boolean) {
        runnerVisible = visible
        session?.setRunnerVisible(visible)
    }

    /** The completion screen's Done: the session is behind us. */
    fun dismiss() {
        views.update { if (it is RunnerView.Completed) RunnerView.Idle else it }
    }

    internal val visible: Boolean get() = runnerVisible

    /**
     * The service's clock, anchored on `elapsedRealtime()` (`ClockAnchor`), which the screen draws its
     * seconds from too: on the plain wall clock, a network time correction mid-session showed a rest at
     * 0:00 for as long as the correction before it ended, or ended it with time still showing. The wall
     * clock while no service runs, when there's nothing timed to draw.
     */
    @Volatile var now: () -> Long = System::currentTimeMillis
        internal set

    /**
     * A completion only ever follows a session that's active or already complete, so one arriving while
     * the host is Idle is stale: the finish backup's answer, republishing the completion with its
     * outcome after Done dismissed it. Kept, it put the old completion back, and the next runner opened
     * on that rather than on its own start (#317's review; the likely cause of a Done that stuck).
     */
    internal fun publish(view: RunnerView) {
        // In one step: this runs on the runner's thread and dismiss() on the main one, and a separate read
        // and write let a Done land between them and the stale completion back in.
        views.update { if (view is RunnerView.Completed && it == RunnerView.Idle) it else view }
    }
}
