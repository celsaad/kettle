package com.casco.kettle.shell

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.casco.kettle.brownfield.BrownfieldActivity
import com.casco.kettle.brownfield.ReactNativeHostManager
import com.casco.kettle.brownfield.ReactNativeViewFactory
import com.casco.kettle.brownfield.setUpNativeBackHandling
import com.casco.kettle.shell.files.DiskFiles
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.nav.ModalSheet
import com.casco.kettle.shell.nav.ShellLayout
import com.casco.kettle.shell.nav.TabNavigator
import com.casco.kettle.shell.nav.shellRoutes
import com.casco.kettle.shell.rn.ExpoActivityListeners
import com.casco.kettle.shell.rn.JsBridge
import com.casco.kettle.shell.rn.RnFrames
import com.casco.kettle.shell.rn.withViewAction
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.preferredColors
import com.casco.kettle.shell.theme.rememberPreferredDarkTheme
import com.facebook.react.modules.core.PermissionAwareActivity
import com.facebook.react.modules.core.PermissionListener
import expo.modules.brownfield.BrownfieldState
import expo.modules.splashscreen.SplashScreenManager
import java.io.File

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
    private val latestFiles by lazy { LatestFiles(DiskFiles(File(filesDir, STORAGE)), lifecycleScope) }

    private val navigator =
        TabNavigator(
            shellRoutes,
            navigate = { url ->
                sheet?.steeredByBar()
                JsBridge.navigate(url)
            },
            reread = { latestFiles.reread() },
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super, as in the prebuilt app: it installs the splash theme's exit. JS hides it with
        // expo-splash-screen's own hideAsync, exactly as before.
        SplashScreenManager.registerOnActivity(this)
        // null, as the prebuilt app's MainActivity passes: the RN fragment is rebuilt from the running
        // runtime, and a restored copy would sit under it.
        super.onCreate(null)
        // Before the surface opens: RN reads the intent's link then, and only under ACTION_VIEW.
        intent.withViewAction()

        expoListeners = ExpoActivityListeners(this).also(lifecycle::addObserver)
        // Read by hostedTabOptions in src/hooks/safe-brownfield.ts, so RN's first frame already has no
        // bar of its own. The two share no code, so change both.
        BrownfieldState.set(NATIVE_TAB_BAR, true)
        // What showReactNativeFragment does, minus the fragment: the surface is
        // one view for the whole app, which ShellLayout places.
        ReactNativeHostManager.shared.initialize(application)
        val surface = ReactNativeViewFactory.createFrameLayout(this, this, "main")
        setUpNativeBackHandling()
        val preferences = File(filesDir, PREFERENCES)
        // The cold start's read. A new activity over the same runtime reads too, since it may have
        // missed a hand-back while there was none to hear it.
        latestFiles.reread()
        val layout =
            ShellLayout(
                this,
                surface,
                navigator,
                shellRoutes,
                theme = { content ->
                    CompositionLocalProvider(LocalLatestFiles provides latestFiles) {
                        KettleTheme(rememberPreferredDarkTheme(preferences, reread = navigator.barShown), content)
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
        // The tab bar's reports go through the sheet, which passes them on at the frame RN draws them.
        JsBridge.onRoute = sheet::onRoute
        JsBridge.onRouteWillChange = sheet::onRouteWillChange
        JsBridge.wantsPicture = sheet::wantsPicture
        RnFrames.onCapture = sheet::capture
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                // The runtime and JsBridge outlive this activity, and a newer one may have taken over.
                override fun onDestroy(owner: LifecycleOwner) {
                    if (JsBridge.onRoute != sheet::onRoute) return
                    JsBridge.onRoute = null
                    JsBridge.onRouteWillChange = null
                    JsBridge.wantsPicture = null
                    RnFrames.onCapture = null
                }
            },
        )
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

    private companion object {
        const val NATIVE_TAB_BAR = "nativeTabBar"

        // expo-file-system's Paths.document is filesDir; src/storage/paths.ts names the rest. The two
        // share no code, so change both.
        const val STORAGE = "exercise-tracker"
        const val PREFERENCES = "$STORAGE/preferences.json"
    }
}
