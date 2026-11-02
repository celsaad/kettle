package com.casco.kettle.domain

/**
 * [text], already trimmed, as JavaScript's `Number` reads it, or null where that is `NaN` or infinite:
 * what RN validated with. Not `toDoubleOrNull`, which also takes a `d` or `f` suffix and a hex float.
 */
internal fun jsNumberOrNull(text: String): Double? {
    val radix = RADIX_PREFIXES[text.getOrNull(1)?.lowercaseChar()]?.takeIf { text.startsWith('0') }
    val value =
        when {
            radix != null -> radixValue(text.drop(2), radix)
            JS_DECIMAL.matches(text) -> text.toDouble()
            else -> null
        }
    return value?.takeIf { it.isFinite() }
}

/**
 * [digits] in [radix], unsigned and ASCII only, as `Number` reads them: summed into a double, so a
 * value past [Long]'s range is still a number. `toLongOrNull` would take a sign and any Unicode digit.
 */
private fun radixValue(
    digits: String,
    radix: Int,
): Double? =
    digits
        .takeIf { it.isNotEmpty() && it.all { c -> c.code < ASCII_END && c.digitToIntOrNull(radix) != null } }
        ?.fold(0.0) { sum, c -> sum * radix + c.digitToInt(radix) }

/** A JS decimal literal with a sign: digits with an optional fraction, or a fraction alone, then an exponent. */
private val JS_DECIMAL = Regex("""[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?""")

/** `0x`, `0o` and `0b`, which `Number` reads unsigned. */
private val RADIX_PREFIXES = mapOf('x' to HEX, 'o' to OCTAL, 'b' to BINARY)

private const val HEX = 16
private const val OCTAL = 8
private const val BINARY = 2
private const val ASCII_END = 128
