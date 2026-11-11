package com.casco.kettle.shell

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.shell.files.DiskFiles
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LibraryAdopt
import com.casco.kettle.shell.files.LibraryGate
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.files.StoragePaths
import com.casco.kettle.shell.files.adoptTargetWeight
import com.casco.kettle.shell.nav.LocalNavigator
import com.casco.kettle.shell.nav.LocalSplashExit
import com.casco.kettle.shell.nav.LocalTabReselects
import com.casco.kettle.shell.nav.ModalSheet
import com.casco.kettle.shell.nav.Navigator
import com.casco.kettle.shell.nav.Route
import com.casco.kettle.shell.nav.ShellLayout
import com.casco.kettle.shell.nav.SplashHold
import com.casco.kettle.shell.nav.Tab
import com.casco.kettle.shell.reminder.reconcileReminder
import com.casco.kettle.shell.runner.LaunchRecovery
import com.casco.kettle.shell.runner.RunnerHost
import com.casco.kettle.shell.runner.RunnerService
import com.casco.kettle.shell.runner.runnerStateDir
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.PreferredKettleTheme
import com.casco.kettle.shell.theme.preferredColors
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * The app's entry point: the shell's tabs, sheet and runner (`ShellLayout`), routed by its own
 * [Navigator], with links parsed here, under the system splash it holds until Home is drawn
 * ([SplashHold]) and drawn edge to edge.
 */
class MainActivity : ComponentActivity() {
    private var sheet: ModalSheet? = null

    /** Lazy, since `filesDir` needs the context the activity is given after construction. */
    private val paths by lazy { StoragePaths(filesDir) }

    private val latestFiles by lazy {
        LatestFiles(
            DiskFiles(paths),
            lifecycleScope,
            onRead = { log, preferences -> reconcileReminder(log, preferences) },
            prepared = { LibraryGate.prepared(this) },
        )
    }

    private val navigator =
        Navigator(
            onReselect = { tabReselects.tryEmit(it) },
            // The runner writes its session as it goes, and the screens it covered read it on the way back.
            onRunnerClosed = { latestFiles.reread() },
        )

    /** What [LocalTabReselects] carries. One slot, since a tap's scroll replaces the one before it. */
    private val tabReselects =
        MutableSharedFlow<Tab>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override fun onCreate(savedInstanceState: Bundle?) {
        // First, so the library's read overlaps the activity's own start, up to the wait below.
        LibraryGate.start(this)
        // Before super: it installs the splash theme's exit.
        val splash = SplashHold.install(this)
        // null, as the RN app passed it, so no view or fragment state is restored: the back stack is
        // restored by hand below. Passing savedInstanceState would change what survives process death
        // and rotation, which is a change of its own, not part of removing RN (#357).
        super.onCreate(null)
        // Edge to edge as the RN app drew it (its `WindowUtil`, which Expo turned on), which androidx's
        // defaults match: a transparent status bar; from API 29 a transparent navigation bar with the
        // platform's contrast enforced (the system's scrim on 3-button nav, none on gesture nav); below
        // 29, RN's own scrims, 0xE6FFFFFF light and 0x801B1B1B dark. SystemBars.kt sets the icons. Without
        // it the window isn't edge to edge below API 35, and every statusBarsPadding() and the sheet's
        // status-bar inset would read a different window.
        enableEdgeToEdge()
        connectAdoptToLibrary()
        // The runner's recovery reads the library, so the launch's seed and repair come first (#221).
        LibraryGate.awaitPrepared(this)
        recoverRunner(paths)
        rereadOnStartAndStop(latestFiles)
        val preferences = paths.preferences
        val layout =
            ShellLayout(
                this,
                navigator,
                theme = { content ->
                    CompositionLocalProvider(
                        LocalLatestFiles provides latestFiles,
                        LocalTabReselects provides tabReselects,
                        LocalNavigator provides navigator,
                        LocalSplashExit provides splash::release,
                    ) {
                        PreferredKettleTheme(
                            preferences,
                            latest = latestFiles.preferences,
                            reread = navigator.barShown,
                            runnerShown = navigator.runnerShown,
                            splashExits = splash.exits,
                        ) {
                            // The chrome the shell draws itself follows the scheme Compose draws, so
                            // Settings' Appearance repaints the × under the finger. Colors from here,
                            // not the file, which the write may not have reached yet.
                            val colors = KettleTheme.colors
                            LaunchedEffect(colors) { sheet?.repaint(colors) }
                            content()
                        }
                    }
                },
            ) { content, shell ->
                ModalSheet(
                    content,
                    shell,
                    navigator,
                    colors = { preferredColors(preferences, resources.configuration) },
                    // Through the dispatcher, as hardware back goes, so a screen's own back handler hears it.
                    requestBack = onBackPressedDispatcher::onBackPressed,
                )
            }
        val sheet = layout.sheet
        navigator.moves = sheet
        // The shell's back, then the sheet's after it, which comes first while the sheet is up: the
        // predictive shrink is the sheet's. A screen's own handler, the runner's, composes later still.
        onBackPressedDispatcher.addCallback(this) { if (!navigator.back()) leave() }
        onBackPressedDispatcher.addCallback(this, sheet.backCallback)
        listenForWrites(latestFiles)
        hearNavigateReceiver(navigator)
        setContentView(layout)
        // The stack this activity's predecessor saved, when it was made again (a font or display size
        // change, the process brought back).
        val saved = savedInstanceState?.getStringArrayList(BACK_STACK)
        if (saved != null) navigator.restore(saved)
        // Any launch with the service alive attaches, the shade notification's tap or not (#297): a Recents
        // swipe leaves the session running, and the relaunch comes back to it with no prompt. Before the
        // link, which a running session then drops. A restored stack's link was opened the first time.
        attachRunner(navigator)
        if (saved == null) navigator.openLink(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(BACK_STACK, ArrayList(navigator.saved()))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.takeOpenRunner()) attachRunner(navigator)
        navigator.openLink(intent)
    }
}

/**
 * Back with nowhere left to go: the task moves to the back rather than finishing, as the platform does
 * for a root task on Android 12 and later, so the next launch comes back to where it was. What the
 * platform adds on 12+ is the system's back animation.
 */
private fun Activity.leave() {
    if (!moveTaskToBack(false)) finish()
}

/**
 * A `kettle://` link in [intent], parsed natively (#309); one to nowhere is dropped. Not when Recents
 * relaunches it: the system hands back the intent the task started with, and a `kettle://session` link
 * would start the workout it already ran.
 */
private fun Navigator.openLink(intent: Intent) {
    val replayed = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
    val link = intent.dataString?.takeUnless { replayed } ?: return
    // Taken off, so a recreated activity, which is handed the same intent, doesn't open it again.
    intent.data = null
    val route = Route.fromLink(link)
    if (route != null) navigate(route) else Log.w("MainActivity", "No route at $link; the link goes nowhere")
}

/**
 * After the library is ready and before any screen can start a session (#307): with no service
 * running, a session the process died under puts up the Resume / Finish prompt, and what the dead
 * service left in the shade goes. Two small reads, like the library gate's, plus, once per install,
 * a read of every session file to end what RN's runner left running (#308).
 *
 * Once per process: a dead service's leftovers can only meet a fresh process, and an activity made
 * again later could land while a service is starting and not yet showing, which a clear would stop.
 */
private fun Activity.recoverRunner(paths: StoragePaths) {
    if (runnerRecovered) return
    runnerRecovered = true
    val app = applicationContext
    // Beside the state dir, not in it: session-check clears that one, and the sweep is once per install.
    val swept = noBackupFilesDir.resolve("rn-sessions-swept")
    LaunchRecovery.recoverAtLaunch(runnerStateDir(noBackupFilesDir), paths.sessions, swept) {
        RunnerService.clearAfterDeath(app)
    }
}

/** Where [MainActivity] saves its [Navigator]'s back stack. */
private const val BACK_STACK = "com.casco.kettle.shell.BACK_STACK"

/** Whether this process has run [recoverRunner]: on the main thread only, as onCreate is. */
private var runnerRecovered = false

/** The files read again as this activity starts and stops. */
private fun LifecycleOwner.rereadOnStartAndStop(files: LatestFiles) =
    lifecycle.addObserver(
        object : DefaultLifecycleObserver {
            // The cold start's read, and a read on every return. A new activity over the same
            // runtime reads too, since it may have missed a hand-back while there was none to hear it.
            override fun onStart(owner: LifecycleOwner) = files.reread()

            // And on leaving: the runner writes its session the moment it starts, and someone who
            // logs one and goes home from the runner never hands the tabs back. Without this the
            // rest-day alarm stays where the previous session put it, and can fire on a day they
            // trained (#236).
            override fun onStop(owner: LifecycleOwner) = files.reread()
        },
    )

/** Whether [this] is the runner's shade notification's tap, taken off the intent so it acts once. */
private fun Intent.takeOpenRunner(): Boolean {
    val opens = getBooleanExtra(RunnerService.EXTRA_OPEN_RUNNER, false)
    removeExtra(RunnerService.EXTRA_OPEN_RUNNER)
    return opens
}

/**
 * Back to the running session (#297), on a launch with the service alive or the shade notification's
 * tap: through the one way in, which attaches whatever the start. Nothing when no session is running,
 * and the navigator drops it while the runner already shows.
 */
private fun attachRunner(navigator: Navigator) {
    val active = RunnerHost.active ?: return
    navigator.openRunner(active.start ?: RunnerStart.Adhoc)
}

/** The runner's adopt (#220), on the application's context: a write may outlive the activity that asked. */
private fun Activity.connectAdoptToLibrary() {
    val app = applicationContext
    LibraryAdopt.adoptTargetWeight = { id, kg -> app.adoptTargetWeight(id, kg) }
}

/**
 * What this activity hears of the files native writes outside a screen, until a newer one takes over:
 * a library the runner's adopt wrote ([LibraryAdopt]) and a session the runner's service wrote
 * ([RunnerHost], #297) each reread [files].
 */
private fun ComponentActivity.listenForWrites(files: LatestFiles) {
    val reread: () -> Unit = files::reread
    LibraryAdopt.onLibraryWritten = reread
    RunnerHost.onSessionWritten = reread
    lifecycle.addObserver(
        object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (LibraryAdopt.onLibraryWritten !== reread) return
                LibraryAdopt.onLibraryWritten = null
                RunnerHost.onSessionWritten = null
            }
        },
    )
}

/**
 * The debug and benchmark builds' `NavigateReceiver` steers this activity's [navigator], until a newer
 * one takes over.
 */
private fun ComponentActivity.hearNavigateReceiver(navigator: Navigator) {
    Navigator.current = navigator
    lifecycle.addObserver(
        object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (Navigator.current === navigator) Navigator.current = null
            }
        },
    )
}
