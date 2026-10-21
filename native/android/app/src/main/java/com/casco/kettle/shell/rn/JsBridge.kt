package com.casco.kettle.shell.rn

import android.util.Log
import com.casco.kettle.brownfield.ReactNativeHostManager
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
     * rather than throws if that ever stops being true, since nothing sends yet.
     *
     * The channel holds again as the old runtime is torn down, not when the new one starts: a reload
     * runs before-destroy listeners before it creates the new instance, so nothing is sent into a dead
     * runtime in between, and the reset can't land after the new runtime's "ready".
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
        host.addBeforeDestroyListener(channel::onReload)
        watchingReloads = true
    }

    private const val TAG = "JsBridge"
}
