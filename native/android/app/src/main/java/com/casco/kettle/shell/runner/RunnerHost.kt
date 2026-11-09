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

    /** The service's, while it runs one. Set only by [adopt], cleared only by [release]. */
    @Volatile internal var session: RunnerSession? = null

    /**
     * From [open] sending the start until the session it made first shows, or the service gives up
     * (#318): the ~100 ms, up to the sounds' timeout, when nothing the screen can see says a session
     * is coming, so [active] can't be what refuses a second start. Under [lock], since the main thread
     * and the runner's both move it.
     */
    private var pending = PendingStart.NONE

    private val lock = Any()

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
     * in full. Refused while a start is already on its way, whoever asked for it (#318).
     */
    fun open(
        context: Context,
        start: RunnerStart?,
        anyway: Boolean = false,
    ) {
        synchronized(lock) {
            if (pending != PendingStart.NONE || active != null || LaunchRecovery.state.value != null) return
            pending = PendingStart.SENT
        }
        runCatching { ContextCompat.startForegroundService(context, RunnerService.startIntent(context, start, anyway)) }
            .onFailure { startEnded() }
            .getOrThrow()
    }

    fun send(command: RunnerCommand) {
        session?.send(command)
    }

    /**
     * Leaves during the count-in, which stops the service with nothing written; nothing once it runs.
     * A leave before the service has a session is held for it (#318). False only when the session is
     * already running: the screen then stays on it, rather than handing back to RN with it unseen.
     */
    fun leave(): Boolean {
        val current =
            synchronized(lock) {
                if (pending == PendingStart.SENT || pending == PendingStart.LEFT) {
                    pending = PendingStart.LEFT
                    return true
                }
                session
            }
        return current?.leave() ?: true
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
     * The service's new session, made the one the screen talks to and the start it answers over; a
     * leave held for it is applied now. False, adopting nothing, while one is already held: checked
     * on the runner's thread, which is the only one that adopts, so two starts can't begin two (#318).
     */
    internal fun adopt(created: RunnerSession): Boolean {
        val heldLeave =
            synchronized(lock) {
                if (session != null || active != null) return false
                session = created
                val held = pending == PendingStart.LEFT
                // Still starting until it first shows: a resume, which [open] didn't send, has none.
                if (pending != PendingStart.NONE) pending = PendingStart.ADOPTED
                held
            }
        if (heldLeave) created.leave()
        return true
    }

    /** [owned] is over with its service: no longer the session, unless a newer one already is. */
    internal fun release(owned: RunnerSession?) {
        synchronized(lock) { if (session === owned) session = null }
    }

    /** The start [open] sent came to nothing (no plan, no foreground) or the service is gone. */
    internal fun startEnded() {
        synchronized(lock) { pending = PendingStart.NONE }
    }

    /**
     * What [owner]'s session publishes, while it's still the session, with an Idle also calling [stop]:
     * a leave before the count-in has shown anything publishes Idle over Idle, which the service's
     * collector never sees, so without this the service stayed up behind Home with nothing to run.
     */
    internal fun publisher(
        owner: () -> RunnerSession?,
        stop: () -> Unit,
    ): (RunnerView) -> Unit =
        { view ->
            if (session === owner()) {
                // Its first view, whatever it is, is the start answered: [active] or Idle say the rest.
                synchronized(lock) { if (pending == PendingStart.ADOPTED) pending = PendingStart.NONE }
                publish(view)
                if (view == RunnerView.Idle) stop()
            }
        }

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
     * on that rather than on its own start (#317's review; the likely cause of a Done that stuck). Only
     * that answer, which carries an outcome: a completion still pending can follow Idle, when a resume
     * after process death finds the last step ran out during the kill (#307).
     */
    internal fun publish(view: RunnerView) {
        // In one step: this runs on the runner's thread and dismiss() on the main one, and a separate read
        // and write let a Done land between them and the stale completion back in.
        views.update { if (view.isBackupAnswer() && it == RunnerView.Idle) it else view }
    }
}

/** Where [RunnerHost.open]'s start is before its session shows (#318). */
private enum class PendingStart {
    NONE,

    /** Sent to the service, which has no session for it yet. */
    SENT,

    /** Sent, then left before the service had a session: [RunnerHost.adopt] leaves the one it's given. */
    LEFT,

    /** The service's session, which a leave now goes to, hasn't shown anything yet. */
    ADOPTED,
}

/** The finish backup's republished completion, the one kind that can only ever follow the session. */
private fun RunnerView.isBackupAnswer() = this is RunnerView.Completed && backup != FinishBackupState.PENDING
