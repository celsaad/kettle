package com.casco.kettle.shell

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.casco.kettle.brownfield.BrownfieldActivity
import com.casco.kettle.brownfield.ReactNativeHostManager
import com.casco.kettle.brownfield.ReactNativeViewFactory
import com.casco.kettle.brownfield.setUpNativeBackHandling
import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.shell.files.DiskFiles
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LibraryGate
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.files.StoragePaths
import com.casco.kettle.shell.files.adoptTargetWeight
import com.casco.kettle.shell.nav.LocalSplashExit
import com.casco.kettle.shell.nav.LocalTabReselects
import com.casco.kettle.shell.nav.ModalSheet
import com.casco.kettle.shell.nav.RouteReport
import com.casco.kettle.shell.nav.ShellLayout
import com.casco.kettle.shell.nav.Tab
import com.casco.kettle.shell.nav.TabNavigator
import com.casco.kettle.shell.nav.nativeRunnerDrawn
import com.casco.kettle.shell.nav.openRunner
import com.casco.kettle.shell.nav.shellRoutes
import com.casco.kettle.shell.reminder.reconcileReminder
import com.casco.kettle.shell.reminder.retireExpoReminder
import com.casco.kettle.shell.rn.ExpoActivityListeners
import com.casco.kettle.shell.rn.JsBridge
import com.casco.kettle.shell.rn.RnFrames
import com.casco.kettle.shell.rn.withViewAction
import com.casco.kettle.shell.runner.LaunchRecovery
import com.casco.kettle.shell.runner.RunnerHost
import com.casco.kettle.shell.runner.RunnerService
import com.casco.kettle.shell.runner.runnerStateDir
import com.casco.kettle.shell.settings.backUpFinishedSession
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.PreferredKettleTheme
import com.casco.kettle.shell.theme.preferredColors
import com.facebook.react.modules.core.PermissionAwareActivity
import com.facebook.react.modules.core.PermissionListener
import expo.modules.brownfield.BrownfieldState
import expo.modules.splashscreen.SplashScreenManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * The app's entry point: today's RN app as one surface (docs/native-migration-plan.md, M2), inside the
 * shell's own tab bar and back stack (M3, `ShellLayout`). `BrownfieldActivity` is only an
 * `AppCompatActivity`, so this does what the prebuilt app's `ReactActivity` and Expo's delegate
 * wrapper did for it: the splash, Expo modules' activity lifecycle
 * (edge to edge, expo-linking's URLs, expo-notifications' taps), and the host's share of intents,
 * activity results, permission prompts, configuration and focus. Without the results the backup-folder
 * picker and the share sheet never resolve; without the permission interface every prompt answers
 * "denied" unasked; without the configuration a dark-mode switch never reaches JS, since `uiMode` is
 * in `configChanges`.
 */
class MainActivity :
    BrownfieldActivity(),
    PermissionAwareActivity {
    private lateinit var expoListeners: ExpoActivityListeners
    private var permissionListener: PermissionListener? = null
    private var permissionResult: (() -> Unit)? = null

    private val reactHost get() = ReactNativeHostManager.shared.getReactHost()

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
        TabNavigator(
            shellRoutes,
            navigate = { url ->
                sheet?.steeredByBar()
                JsBridge.navigate(url)
            },
            reread = { latestFiles.reread() },
            onReselect = { tabReselects.tryEmit(it) },
        )

    /** What [LocalTabReselects] carries. One slot, since a tap's scroll replaces the one before it. */
    private val tabReselects =
        MutableSharedFlow<Tab>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override fun onCreate(savedInstanceState: Bundle?) {
        // First, so the library's read overlaps everything up to RN's start, which waits for it.
        LibraryGate.start(this)
        // Before super, as in the prebuilt app: it installs the splash theme's exit.
        holdSplash()
        // null, as the prebuilt app's MainActivity passes: the RN fragment is rebuilt from the running
        // runtime, and a restored copy would sit under it.
        super.onCreate(null)
        // Before the surface opens: RN reads the intent's link then, and only under ACTION_VIEW.
        intent.withViewAction()

        expoListeners = ExpoActivityListeners(this).also(lifecycle::addObserver)
        tellJsWhatTheShellDraws()
        retireExpoReminder()
        handJsItsWrites()
        // RN's library load only parses from the library flip (#245), so native seeds and repairs first
        // (#221).
        LibraryGate.awaitPrepared(this)
        recoverRunner(paths)
        // What showReactNativeFragment does, minus the fragment: the surface is
        // one view for the whole app, which ShellLayout places.
        ReactNativeHostManager.shared.initialize(application)
        val surface = ReactNativeViewFactory.createFrameLayout(this, this, "main")
        setUpNativeBackHandling()
        rereadOnStartAndStop(latestFiles)
        val preferences = paths.preferences
        val layout =
            ShellLayout(
                this,
                surface,
                navigator,
                shellRoutes,
                theme = { content ->
                    CompositionLocalProvider(
                        LocalLatestFiles provides latestFiles,
                        LocalTabReselects provides tabReselects,
                        LocalSplashExit provides SplashScreenManager::hide,
                    ) {
                        PreferredKettleTheme(
                            preferences,
                            latest = latestFiles.preferences,
                            reread = navigator.barShown,
                            runnerShown = navigator.runnerShown,
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
                    surface,
                    navigator,
                    colors = { preferredColors(preferences, resources.configuration) },
                    // The ×'s and the grabber's back is hardware back's, which the sheet's callback hands RN.
                    requestBack = onBackPressedDispatcher::onBackPressed,
                )
            }
        val sheet = layout.sheet.also { this.sheet = it }
        // After RN's own, so it comes first while the sheet is up: the predictive shrink is the sheet's.
        onBackPressedDispatcher.addCallback(this, sheet.backCallback)
        // Any launch with the service alive attaches, the shade notification's tap or not (#297): a Recents
        // swipe leaves the session running, and the relaunch comes back to it with no prompt.
        listenToJs(sheet, latestFiles) { attachRunner(navigator.runnerShown) }
        setContentView(layout)
        JsBridge.attach()
        expoListeners.onContentShown()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val link = intent.withViewAction()
        setIntent(link)
        expoListeners.onNewIntent(link)
        reactHost?.onNewIntent(link)
        // This activity's surface is up, so its router hears the push at once.
        if (link.takeOpenRunner()) attachRunner(navigator.runnerShown)
    }

    override fun onResume() {
        super.onResume()
        // RN's own delegate holds a permission result until the activity resumes, which is when JS can
        // act on it.
        permissionResult?.invoke()
        permissionResult = null
    }

    override fun onUserLeaveHint() {
        expoListeners.onUserLeaveHint()
        super.onUserLeaveHint()
    }

    @Deprecated("Deprecated in ComponentActivity; RN and Expo still deliver results through it")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        reactHost?.onActivityResult(this, requestCode, resultCode, data)
    }

    override fun requestPermissions(
        permissions: Array<String>,
        requestCode: Int,
        listener: PermissionListener?,
    ) {
        permissionListener = listener
        requestPermissions(permissions, requestCode)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionResult = {
            if (permissionListener?.onRequestPermissionsResult(requestCode, permissions, grantResults) == true) {
                permissionListener = null
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        reactHost?.onConfigurationChanged(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        reactHost?.onWindowFocusChange(hasFocus)
    }

    /**
     * JS has nowhere left to go back to: it has dismissed everything above the tabs, and RN's tab back
     * is off inside the shell. So the tabs' back is the shell's, Workouts first as it always was.
     *
     * Then the app leaves. On Android 12 and later the prebuilt app left this to the platform, which
     * moves a root task to the back rather than finishing it; it did the same itself on 11 and
     * earlier. Calling the platform's back here would re-enter RN's own back callback (see
     * `BrownfieldActivity`), so this moves the task directly, and `BrownfieldActivity`'s `finish()`,
     * which would bring the router back at its root on the next launch, is never reached. What the
     * platform adds on 12+ is the system's back animation.
     */
    override fun invokeDefaultOnBackPressed() {
        if (navigator.back()) return
        if (!moveTaskToBack(false)) finish()
    }
}

/**
 * After the library is ready and before any screen can start a session (#307): with no service
 * running, a session the process died under puts up the Resume / Finish prompt, and what the dead
 * service left in the shade goes. Two small reads, like the library gate's.
 *
 * Once per process: a dead service's leftovers can only meet a fresh process, and an activity made
 * again later could land while a service is starting and not yet showing, which a clear would stop.
 */
private fun Activity.recoverRunner(paths: StoragePaths) {
    if (!nativeRunnerDrawn || runnerRecovered) return
    runnerRecovered = true
    val app = applicationContext
    LaunchRecovery.recoverAtLaunch(runnerStateDir(noBackupFilesDir), paths.sessions) {
        RunnerService.clearAfterDeath(app)
    }
}

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
 * tap: through the one way in, which attaches and ignores the start. Nothing when the runner already
 * shows ([runnerShown]), or when no session is running.
 */
private fun attachRunner(runnerShown: Boolean) {
    val active = RunnerHost.active
    if (active != null && !runnerShown) openRunner(active.start ?: RunnerStart.Adhoc)
}

/**
 * What the shell draws in RN's place, set before the surface opens so RN's first frame already knows.
 * `nativeTabBar` is read by isHosted in src/hooks/brownfield-module.ts, so RN draws no bar of its own;
 * `nativeRunner` by drawsNativeRunner there, so the native runner's stub guards only where native
 * draws over it (#302). The two share no code, so change both.
 */
private fun tellJsWhatTheShellDraws() {
    BrownfieldState.set("nativeTabBar", true)
    BrownfieldState.set("nativeRunner", nativeRunnerDrawn)
}

/**
 * The writes JS asks native for (#220), on the application's context: a backup the user's folder takes
 * seconds over, or an adopt, may outlive the activity that asked.
 */
private fun Activity.handJsItsWrites() {
    val app = applicationContext
    JsBridge.backUpFinishedSession = { app.backUpFinishedSession() }
    JsBridge.adoptTargetWeight = { id, kg -> app.adoptTargetWeight(id, kg) }
}

/**
 * Native Home lets the splash go (#235), and RN's first content mustn't first: that is what
 * expo-splash-screen's preventAutoHide stood for, which JS no longer calls.
 */
private fun Activity.holdSplash() {
    SplashScreenManager.registerOnActivity(this)
    SplashScreenManager.preventAutoHideCalled = true
}

/**
 * What this activity hears from JS, until a newer one takes over: the runtime and [JsBridge] outlive
 * it. The tab bar's reports go through [sheet], which passes them on at the frame RN draws them, and
 * a library native wrote at the runner's asking rereads [files], as does a session the native
 * runner's service wrote ([RunnerHost], #297).
 */
private fun ComponentActivity.listenToJs(
    sheet: ModalSheet,
    files: LatestFiles,
    firstRoute: () -> Unit,
) {
    // The first report is this activity's surface mounted. JS outlives the activity and has long said
    // it's ready, so a push sent before then reaches a router that isn't there yet and is lost: that is
    // when a notification tap that recreated the activity attaches ([firstRoute]).
    var heard = false
    val onRoute: (RouteReport) -> Unit = { report ->
        sheet.onRoute(report)
        if (!heard) {
            heard = true
            firstRoute()
        }
    }
    JsBridge.onRoute = onRoute
    JsBridge.onRouteWillChange = sheet::onRouteWillChange
    JsBridge.wantsPicture = sheet::wantsPicture
    JsBridge.onLibraryWritten = files::reread
    RunnerHost.onSessionWritten = files::reread
    RnFrames.onCapture = sheet::capture
    lifecycle.addObserver(
        object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (JsBridge.onRoute !== onRoute) return
                JsBridge.onRoute = null
                JsBridge.onRouteWillChange = null
                JsBridge.wantsPicture = null
                JsBridge.onLibraryWritten = null
                RunnerHost.onSessionWritten = null
                RnFrames.onCapture = null
            }
        },
    )
}
