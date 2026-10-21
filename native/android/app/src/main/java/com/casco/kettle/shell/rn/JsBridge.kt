package com.casco.kettle.shell.rn

import android.util.Log
import com.casco.kettle.brownfield.ReactNativeHostManager
import com.facebook.react.ReactInstanceEventListener
import com.facebook.react.bridge.ReactContext
import expo.modules.brownfield.BrownfieldMessaging

/**
 * The one channel to JS, for the life of the process: the JS runtime outlives any activity (the M0
 * spike's Q2), so the channel does too. Nothing sends on it yet: M3's navigation is the first sender,
 * and the hold-until-ready contract is in place and tested before it arrives.
 */
object JsBridge {
    val channel = JsChannel(BrownfieldMessaging::sendMessage)

    private var listening = false
    private var watchingReloads = false

    /**
     * Listens to JS, and to the runtime being replaced. Idempotent, since every new activity calls it.
     *
     * The listener needs no host, so it is always in place and "ready" can't go unheard. Watching
     * reloads does: it's called once the first surface has opened, which creates the host, and logs
     * rather than throws if that ever stops being true, since nothing sends yet. A new runtime is
     * announced on the UI thread as it starts, long before it can say "ready" itself, which waits on
     * the fonts and the stores' hydration.
     */
    fun attach() {
        if (!listening) {
            BrownfieldMessaging.addListener(channel::receive)
            listening = true
        }
        if (watchingReloads) return
        val host = ReactNativeHostManager.shared.getReactHost()
        if (host == null) {
            Log.w(TAG, "No React host yet; a reload won't re-hold messages until the next activity")
            return
        }
        host.addReactInstanceEventListener(
            object : ReactInstanceEventListener {
                override fun onReactContextInitialized(context: ReactContext) = channel.onReload()
            },
        )
        watchingReloads = true
    }

    private const val TAG = "JsBridge"
}
