package com.casco.kettle.domain

/**
 * [text], already trimmed, as JavaScript's `Number` reads it, or null where that is `NaN` or infinite:
 * what RN validated with. Not `toDoubleOrNull`, which also takes a `d` or `f` suffix and a hex float.
 */
internal fun jsNumberOrNull(text: String): Double? {
    val radix = RADIX_PREFIXES[text.getOrNull(1)?.lowercaseChar()]?.takeIf { text.startsWith('0') }
    // Unsigned after the prefix: `toLongOrNull` would take "0x-1", which JS refuses.
    val digits = text.drop(2).takeIf { it.firstOrNull()?.isLetterOrDigit() == true }
    val value =
        when {
            radix != null -> digits?.toLongOrNull(radix)?.toDouble()
            JS_DECIMAL.matches(text) -> text.toDouble()
            else -> null
        }
    return value?.takeIf { it.isFinite() }
}

/** A JS decimal literal with a sign: digits with an optional fraction, or a fraction alone, then an exponent. */
private val JS_DECIMAL = Regex("""[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?""")

/** `0x`, `0o` and `0b`, which `Number` reads unsigned. */
private val RADIX_PREFIXES = mapOf('x' to HEX, 'o' to OCTAL, 'b' to BINARY)

private const val HEX = 16
private const val OCTAL = 8
private const val BINARY = 2
