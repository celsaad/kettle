package com.casco.kettle.shell.nav

import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AccelerateInterpolator
import android.window.SplashScreenView
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * The system splash, held until [release]: native Home's first layout after the files are read lets it
 * go ([SplashExit], #235). One per activity.
 *
 * Ported from expo-splash-screen's `SplashScreenManager` (57.0.9), which held it while the app was RN
 * and is kept as it was, so the launch looks the same: the hold, the fade, and the crash workaround.
 * The hold is a pre-draw listener on the content view rather than androidx's `setKeepOnScreenCondition`,
 * which Expo found doesn't hold below API 33.
 */
class SplashHold private constructor(
    private val content: View,
) : ViewTreeObserver.OnPreDrawListener {
    private var held = true

    /** Lets the splash go at the next frame. Again is nothing. */
    fun release() {
        held = false
    }

    override fun onPreDraw(): Boolean {
        if (held) return false
        content.viewTreeObserver.removeOnPreDrawListener(this)
        return true
    }

    companion object {
        /** Expo's fade, `SplashScreenOptions.duration`. */
        private const val FADE_MS = 400L

        /** Installs [activity]'s splash and holds it. Before `super.onCreate`, as the splash theme needs. */
        fun install(activity: ComponentActivity): SplashHold {
            val splash = activity.installSplashScreen()
            val hold = hold(activity.findViewById(android.R.id.content))
            splash.setOnExitAnimationListener { provider ->
                val view = provider.view
                view
                    .animate()
                    .setDuration(FADE_MS)
                    .alpha(0f)
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction {
                        // The platform's own view directly on 31+, as Expo does: the provider's remove()
                        // reapplies the theme's system bar appearance over what the app set.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            (view as SplashScreenView).remove()
                        } else {
                            provider.remove()
                        }
                    }.start()
            }
            if (Build.VERSION.SDK_INT in Build.VERSION_CODES.S..Build.VERSION_CODES.TIRAMISU) {
                clearExitOnFirstStop(activity)
            }
            return hold
        }

        /** The hold alone, on [content], for a test to hold without a splash theme. */
        internal fun hold(content: View): SplashHold =
            SplashHold(content).also(content.viewTreeObserver::addOnPreDrawListener)

        /**
         * On API 31 to 33 the exit listener can fire after the activity stops, on a `SurfaceControl`
         * already released, and crash (issuetracker 242118185): backgrounding the app during the
         * splash. Dropping the listener at the first stop is Expo's mitigation.
         */
        private fun clearExitOnFirstStop(activity: ComponentActivity) =
            activity.lifecycle.addObserver(
                object : DefaultLifecycleObserver {
                    override fun onStop(owner: LifecycleOwner) {
                        // Always true here (the caller checks 31-33); repeated because lint's NewApi
                        // doesn't follow the caller's range into this callback.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            runCatching { activity.splashScreen.clearOnExitAnimationListener() }
                        }
                        activity.lifecycle.removeObserver(this)
                    }
                },
            )
    }
}
