package com.casco.kettle.shell

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import com.casco.kettle.brownfield.BrownfieldActivity
import com.casco.kettle.brownfield.ReactNativeHostManager
import com.casco.kettle.brownfield.showReactNativeFragment
import com.casco.kettle.shell.rn.ExpoActivityListeners
import com.casco.kettle.shell.rn.JsBridge
import com.casco.kettle.shell.rn.withViewAction
import com.facebook.react.modules.core.PermissionAwareActivity
import com.facebook.react.modules.core.PermissionListener
import expo.modules.splashscreen.SplashScreenManager

/**
 * The app's entry point, running today's whole RN app as one surface (docs/native-migration-plan.md,
 * M2). `BrownfieldActivity` is only an `AppCompatActivity`, so this does what the prebuilt app's
 * `ReactActivity` and Expo's delegate wrapper did for it: the splash, Expo modules' activity lifecycle
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
        showReactNativeFragment()
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
     * JS has nowhere left to go back to. On Android 12 and later the prebuilt app left this to the
     * platform, which moves a root task to the back rather than finishing it; it did the same itself
     * on 11 and earlier. Calling the platform's back here would re-enter RN's own back callback (see
     * `BrownfieldActivity`), so this moves the task directly, and `BrownfieldActivity`'s `finish()`,
     * which would bring the router back at its root on the next launch, is never reached. What the
     * platform adds on 12+ is the system's back animation.
     */
    override fun invokeDefaultOnBackPressed() {
        if (!moveTaskToBack(false)) finish()
    }
}
