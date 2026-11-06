package com.casco.kettle.shell.runner

/**
 * What the runner's number pad holds (§3.4 of #294's inventory): [seed] until a key is typed, then a
 * fresh value rather than one appended to, since the reason to open the pad at all is that the value
 * on screen is far from the one wanted. The length cap keeps a stuck finger from producing a number
 * that breaks the layout.
 */
data class PadEntry(
    val seed: Double,
    val allowDecimal: Boolean,
    private val typed: String = "",
) {
    val display: String get() = typed.ifEmpty { seed.plain() }

    /** What Set confirms: null for a lone point or a negative, which leaves the button disabled. */
    val value: Double? get() =
        (if (typed.isEmpty()) seed else typed.toDoubleOrNull())?.takeIf {
            it.isFinite() &&
                it >= 0
        }

    fun type(key: Char): PadEntry =
        when {
            typed.length >= MAX_CHARS -> this
            key == '.' && (!allowDecimal || '.' in typed) -> this
            key == '.' || key.isDigit() -> copy(typed = typed + key)
            else -> this
        }

    fun backspace(): PadEntry = copy(typed = typed.dropLast(1))
}

/** A whole number without its ".0", as JS's `String(n)` writes one. */
internal fun Double.plain(): String = if (this % 1.0 == 0.0 && isFinite()) toLong().toString() else toString()

private const val MAX_CHARS = 6
