package com.casco.kettle.shell.rn

import com.casco.kettle.brownfield.ReactNativeHostManager
import com.facebook.react.ReactInstanceEventListener
import com.facebook.react.bridge.ReactContext
import expo.modules.brownfield.BrownfieldMessaging

/**
 * The one channel to JS, for the life of the process: the JS runtime outlives any activity (the M0
 * spike's Q2), so the channel does too.
 */
object Js {
    val channel = JsChannel(BrownfieldMessaging::sendMessage)

    private var attached = false

    /**
     * Listens to JS, and to the runtime being replaced. Called once the first surface has opened, so
     * the host exists. A new runtime is announced on the UI thread as it starts, long before it can
     * say "ready" itself: that waits on fonts and three file reads. Idempotent, since every new
     * activity calls it.
     */
    fun attach() {
        if (attached) return
        val host = ReactNativeHostManager.shared.getReactHost() ?: return
        attached = true
        BrownfieldMessaging.addListener(channel::receive)
        host.addReactInstanceEventListener(
            object : ReactInstanceEventListener {
                override fun onReactContextInitialized(context: ReactContext) = channel.onReload()
            },
        )
    }
}
