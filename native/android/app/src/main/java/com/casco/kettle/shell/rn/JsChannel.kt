package com.casco.kettle.shell.rn

/**
 * Native's messages to JS, held until JS says it is listening. The bridge drops anything sent
 * before then, silently (a build fact from the M0 spike), so nothing native sends may assume JS
 * is up.
 *
 * Synchronized because the two sides arrive on different threads: JS's messages on the JS thread,
 * native's sends on the main one.
 */
class JsChannel(
    private val deliver: (Map<String, Any?>) -> Unit,
) {
    private val held = mutableListOf<Map<String, Any?>>()
    private var ready = false

    @Synchronized
    fun send(message: Map<String, Any?>) {
        if (ready) deliver(message) else held += message
    }

    /** A message from JS. Only "ready" means anything to the channel itself. */
    @Synchronized
    fun receive(message: Map<String, Any?>) {
        if (message["type"] != READY) return
        ready = true
        held.forEach(deliver)
        held.clear()
    }

    /** The JS runtime was replaced: its successor listens only once it has said "ready" again. */
    @Synchronized
    fun onReload() {
        ready = false
    }

    private companion object {
        // What src/hooks/safe-brownfield.ts sends. The two sides share no code, so change both.
        const val READY = "ready"
    }
}
