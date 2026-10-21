package com.casco.kettle.shell

import android.content.Intent
import android.os.Bundle
import com.casco.kettle.brownfield.BrownfieldActivity
import com.casco.kettle.brownfield.ReactNativeHostManager
import com.casco.kettle.brownfield.showReactNativeFragment
import com.casco.kettle.shell.rn.Js
import com.casco.kettle.shell.rn.withViewAction
import expo.modules.ExpoModulesPackage
import expo.modules.core.interfaces.ReactActivityLifecycleListener
import expo.modules.splashscreen.SplashScreenManager

/**
 * The app's entry point, running today's whole RN app as one surface (docs/native-migration-plan.md,
 * M2). It does what the prebuilt app's `ReactActivity` did, because `BrownfieldActivity` is only an
 * `AppCompatActivity`: it registers the splash, hands Expo modules their activity lifecycle (edge to
 * edge, expo-linking's URLs, expo-notifications' taps), forwards new intents to React Native, and
 * backgrounds the app on a last back rather than finishing it.
 */
class MainActivity : BrownfieldActivity() {
    private lateinit var lifecycleListeners: List<ReactActivityLifecycleListener>

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super, as in the prebuilt app: it installs the splash theme's exit. JS hides it with
        // expo-splash-screen's own hideAsync, exactly as before.
        SplashScreenManager.registerOnActivity(this)
        // null, as React Native's own activities pass: the RN fragment is rebuilt from the running
        // runtime, and a restored copy would sit under it.
        super.onCreate(null)
        intent.withViewAction()

        lifecycleListeners = ExpoModulesPackage.packageList.flatMap { it.createReactActivityLifecycleListeners(this) }
        showReactNativeFragment()
        Js.attach()
        lifecycleListeners.forEach {
            it.onCreate(this, null)
            it.onContentChanged(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val link = intent.withViewAction()
        setIntent(link)
        lifecycleListeners.forEach { it.onNewIntent(link) }
        ReactNativeHostManager.shared.getReactHost()?.onNewIntent(link)
    }

    override fun onResume() {
        super.onResume()
        lifecycleListeners.forEach { it.onResume(this) }
    }

    override fun onPause() {
        lifecycleListeners.forEach { it.onPause(this) }
        super.onPause()
    }

    override fun onUserLeaveHint() {
        lifecycleListeners.forEach { it.onUserLeaveHint(this) }
        super.onUserLeaveHint()
    }

    override fun onDestroy() {
        lifecycleListeners.forEach { it.onDestroy(this) }
        super.onDestroy()
    }

    /**
     * JS has nowhere left to go back to. The prebuilt app's `ReactActivity` moves the task to the
     * back here rather than finishing, so the next launch finds the app where it was left;
     * `BrownfieldActivity` would finish, and the router would come back at its root.
     */
    override fun invokeDefaultOnBackPressed() {
        if (!moveTaskToBack(false)) finish()
    }
}
