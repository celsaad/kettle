package com.casco.kettle.shell.rn

/**
 * How a native screen opens the next route (M4): on top of what shows, or in its place. RN's router
 * does either without dismissing anything first, unlike `navigate`, since the screen it opens from is
 * the one back returns to. [wire] is the message type src/hooks/safe-brownfield.ts listens for. The two
 * share no code, so change both.
 */
enum class Stacking(
    val wire: String,
) {
    /** History → session editor, Stats → exercise progress. */
    Push("push"),

    /** The program guide's hand-off to Import. */
    Replace("replace"),
    ;

    /** Path and params apart, so RN's router joins them and nothing here has to encode a query. */
    fun message(
        pathname: String,
        params: Map<String, String> = emptyMap(),
    ): Map<String, Any?> = mapOf("type" to wire, "pathname" to pathname, "params" to params)
}
