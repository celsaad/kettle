package com.casco.kettle.shell.rn

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import expo.modules.ExpoModulesPackage

/**
 * Expo modules' activity hooks (edge to edge, expo-linking's URLs, expo-notifications' taps), which
 * Expo's `ReactActivityDelegateWrapper` dispatches for a `ReactActivity` and nothing dispatches for a
 * `BrownfieldActivity`. Resume, pause and destroy follow the activity's lifecycle, which fires them in
 * the wrapper's order: after `onResume`, before `onPause` and `onDestroy`.
 */
class ExpoActivityListeners(
    private val activity: Activity,
) : DefaultLifecycleObserver {
    private val listeners =
        ExpoModulesPackage.packageList.flatMap { it.createReactActivityLifecycleListeners(activity) }

    /** Once the RN surface is the activity's content. */
    fun onContentShown() =
        listeners.forEach {
            it.onCreate(activity, null)
            it.onContentChanged(activity)
        }

    fun onNewIntent(intent: Intent) = listeners.forEach { it.onNewIntent(intent) }

    fun onUserLeaveHint() = listeners.forEach { it.onUserLeaveHint(activity) }

    override fun onResume(owner: LifecycleOwner) = listeners.forEach { it.onResume(activity) }

    override fun onPause(owner: LifecycleOwner) = listeners.forEach { it.onPause(activity) }

    override fun onDestroy(owner: LifecycleOwner) = listeners.forEach { it.onDestroy(activity) }
}
