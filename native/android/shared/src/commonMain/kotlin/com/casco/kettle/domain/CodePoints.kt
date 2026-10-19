package com.casco.kettle.domain

// Common Kotlin has no code-point API, so the surrogate-pair arithmetic is spelled out.
private const val SUPPLEMENTARY_FIRST = 0x10000
private const val SURROGATE_BITS = 10

/**
 * The code points of [value], as JS's `Array.from` gives them: a surrogate pair is one, and a lone
 * surrogate stays one unit of its own.
 */
internal fun codePoints(value: String): IntArray {
    val points = ArrayList<Int>(value.length)
    var i = 0
    while (i < value.length) {
        val high = value[i]
        val low = value.getOrNull(i + 1)
        if (high.isHighSurrogate() && low != null && low.isLowSurrogate()) {
            val offset = (high - Char.MIN_HIGH_SURROGATE shl SURROGATE_BITS) + (low - Char.MIN_LOW_SURROGATE)
            points.add(SUPPLEMENTARY_FIRST + offset)
            i += 2
        } else {
            points.add(high.code)
            i += 1
        }
    }
    return points.toIntArray()
}
