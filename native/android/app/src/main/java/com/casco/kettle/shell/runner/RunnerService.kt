package com.casco.kettle.shell.runner

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.casco.kettle.domain.ClockAnchor
import com.casco.kettle.domain.RunnerPlan
import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.resolveRunnerPlan
import com.casco.kettle.shell.MainActivity
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.DiskFiles
import com.casco.kettle.shell.files.StoragePaths
import com.casco.kettle.shell.nav.routeParams
import com.casco.kettle.shell.nav.runnerStartFrom
import com.casco.kettle.shell.settings.backUpFinishedSession
import com.casco.kettle.storage.writeSessionFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The running session's host (#305): a `health` foreground service that holds the clock through
 * backgrounding, the lock screen and Doze, plays every cue, and is the only writer of the running
 * session's file and its state file (#295).
 *
 * - **Started** from the visible runner before the count-in, which it owns (#297), and resolves the
 *   start again itself, so the screen and the service can't run different plans.
 * - **`START_NOT_STICKY`**, and nothing stops it on a Recents swipe: no `stopWithTask`, and
 *   [onTaskRemoved] is left alone, so the session keeps running (#297). Killed, it isn't restarted:
 *   the state file is the truth, read at the next launch (#307).
 * - **The clock** is `elapsedRealtime()` through one [ClockAnchor], so a clock change can't move a step,
 *   plus a partial wake lock for the session, which an FGS keeps through Doze (#293).
 *
 * Declared in the debug manifest alone until the flip release (#308), so a Play build is unchanged.
 */
class RunnerService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var loop: RunnerLoop
    private lateinit var cues: RunnerCues
    private lateinit var anchor: ClockAnchor
    private val main = Handler(Looper.getMainLooper())
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val paths by lazy { StoragePaths(filesDir) }
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile private var session: RunnerSession? = null
    private var foreground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createRunnerChannels(this)
        thread = HandlerThread("runner").also { it.start() }
        loop = HandlerLoop(Handler(thread.looper))
        cues = RunnerCues(this, loop) { DiskFiles(paths).preferences()?.sessionSounds ?: true }
        anchor = ClockAnchor(System.currentTimeMillis(), SystemClock.elapsedRealtime())
        scope.launch { RunnerHost.view.collect(::show) }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // At once, whatever comes next: a service started in the foreground that doesn't say so within
        // seconds is an ANR, and one that stops first is a crash.
        if (!enterForeground(placeholder())) return START_NOT_STICKY
        if (session == null && intent?.action == ACTION_START) {
            val start = runnerStartFrom(intent.params())
            val anyway = intent.getBooleanExtra(EXTRA_ANYWAY, false)
            loop.post { begin(start, anyway) }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        session = null
        RunnerHost.session = null
        scope.cancel()
        loop.post { cues.close() }
        thread.quitSafely()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    /** On the runner's thread: the plan read from the library as it is now, then the count-in. */
    private fun begin(
        start: RunnerStart?,
        anyway: Boolean,
    ) {
        val plan =
            when (val resolved = DiskFiles(paths).library()?.let { resolveRunnerPlan(start, it) }) {
                is RunnerPlan.Runnable -> resolved
                is RunnerPlan.TooLong -> resolved.plan.takeIf { anyway }
                else -> null
            }
        if (plan == null) {
            // The screen resolved it to something it couldn't run either, or the library changed under it.
            Log.w(TAG, "Nothing to run for that start; stopping")
            main.post(::stop)
            return
        }
        val created =
            RunnerSession(
                plan,
                start,
                loop,
                now = { anchor.wallAt(SystemClock.elapsedRealtime()) },
                effects(),
                RunnerHost::publish,
            )
        session = created
        RunnerHost.session = created
        created.setRunnerVisible(RunnerHost.visible)
        if (wakeLock?.isHeld != true) wakeLock = sessionWakeLock()
        cues.whenLoaded(SOUNDS_TIMEOUT_MS, created::begin)
    }

    private fun effects(): RunnerEffects {
        val notifications = NotificationManagerCompat.from(this)
        return RunnerEffects(
            writeSession = { writeSessionFile(paths.sessions, it) },
            onSessionWritten = { main.post { RunnerHost.onSessionWritten?.invoke() } },
            stateFile = RunnerStateFile(runnerStateFile(noBackupFilesDir), loop),
            sounds = cues,
            alerts =
                StepAlerts(
                    post = {
                        alert,
                        silent,
                        ->
                        notifyIfAllowed(ALERT_ID, stepEndNotification(this, alert, silent, openRunner()))
                    },
                    cancel = { notifications.cancel(ALERT_ID) },
                ),
            backUp = {
                val app = applicationContext
                background.launch { session?.backedUp(app.backUpFinishedSession()) }
            },
        )
    }

    /** The service's half of each view: the shade while a session is active, and stopping once it isn't. */
    private fun show(view: RunnerView) {
        // The flow replays what it last held, which until this service's session begins may be the last
        // one's completion: not this service's to act on.
        if (session == null) return
        when (view) {
            is RunnerView.Active -> {
                notifyIfAllowed(ONGOING_ID, ongoingNotification(this, ongoingContent(resources, view), openRunner()))
            }

            // The shade's notification and the wake lock go with the session; the service waits only for
            // the finish backup, whose answer the completion screen warns from.
            is RunnerView.Completed -> {
                leaveForeground()
                wakeLock?.takeIf { it.isHeld }?.release()
                if (view.backup != FinishBackup.PENDING) stop()
            }

            RunnerView.Idle -> {
                stop()
            }
        }
    }

    private fun enterForeground(notification: Notification): Boolean {
        val type =
            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            ) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            } else {
                0
            }
        return try {
            ServiceCompat.startForeground(this, ONGOING_ID, notification, type)
            foreground = true
            true
        } catch (
            @Suppress("TooGenericExceptionCaught") e: RuntimeException,
        ) {
            // ForegroundServiceStartNotAllowedException and its kin: started from somewhere the platform
            // won't allow. Nothing has been written yet, so stopping costs nothing.
            Log.w(TAG, "Couldn't enter the foreground; stopping", e)
            stopSelf()
            false
        }
    }

    private fun leaveForeground() {
        if (!foreground) return
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foreground = false
    }

    private fun stop() {
        leaveForeground()
        stopSelf()
    }

    companion object {
        /** On MainActivity's intent: open the runner, attached to the running session. */
        const val EXTRA_OPEN_RUNNER = "com.casco.kettle.shell.OPEN_RUNNER"

        private const val ACTION_START = "com.casco.kettle.shell.runner.START"
        private const val EXTRA_ANYWAY = "anyway"
        private const val EXTRA_PARAM_PREFIX = "param."

        /** The start as the runner's route carries it, so one reader parses both. */
        fun startIntent(
            context: Context,
            start: RunnerStart?,
            anyway: Boolean,
        ): Intent =
            Intent(context, RunnerService::class.java).setAction(ACTION_START).putExtra(EXTRA_ANYWAY, anyway).apply {
                for ((key, value) in start?.routeParams().orEmpty()) putExtra(EXTRA_PARAM_PREFIX + key, value)
            }

        private fun Intent.params(): Map<String, String> =
            extras
                ?.keySet()
                .orEmpty()
                .filter { it.startsWith(EXTRA_PARAM_PREFIX) }
                .associate { it.removePrefix(EXTRA_PARAM_PREFIX) to getStringExtra(it).orEmpty() }
    }
}

/** What the shade shows for the instant before the plan has resolved. */
private fun Context.placeholder(): Notification {
    val content =
        RunnerHost.active?.let { ongoingContent(resources, it) }
            ?: OngoingContent(
                applicationInfo.loadLabel(packageManager).toString(),
                getString(R.string.session_notification_getReady),
                null,
            )
    return ongoingNotification(this, content, openRunner())
}

/** Back to the running session: MainActivity, with the extra that attaches (#297). */
private fun Context.openRunner(): PendingIntent =
    PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).putExtra(RunnerService.EXTRA_OPEN_RUNNER, true),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

/** Posts [notification], or nothing where notifications are denied: the session still runs and cues (#296). */
private fun Context.notifyIfAllowed(
    id: Int,
    notification: Notification,
) {
    val allowed =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    if (allowed) NotificationManagerCompat.from(this).notify(id, notification)
}

/** The session's partial wake lock, held. A ceiling rather than none, so a bug can't hold it forever. */
private fun Context.sessionWakeLock(): PowerManager.WakeLock =
    getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kettle:runner")
        .also { it.acquire(WAKE_LOCK_CEILING_MS) }

// Ids apart from the rest-day reminder's, which is tagged with its own channel anyway.
private const val ONGOING_ID = 3051
private const val ALERT_ID = 3052
private const val SOUNDS_TIMEOUT_MS = 1000L
private const val WAKE_LOCK_CEILING_MS = 6 * 60 * 60 * 1000L
private const val TAG = "RunnerService"
