package com.casco.kettle.domain

/**
 * A key as JavaScript's `String(key)` spells it. Every number is a JS number first, so an integer past
 * 2^53 rounds to the nearest double before it is spelled, as js-yaml's does.
 */
internal fun jsKey(key: Any?): String = if (key is Number) jsNumber(key.toDouble()) else key.toString()

/**
 * ECMAScript's `Number::toString`: the fewest digits that read back as the same double, the closest to
 * it where several do, laid out the JS way. Kotlin's `toString` can't stand in for it. Its layout
 * differs (an exponent below 1e-3 and from 1e7, where JS has one below 1e-6 and from 1e21), and its
 * digits are not always JS's: JDK 17 prints `1.2345e21` as `1.2344999999999999E21`, and where two
 * 17-digit strings read back the same it doesn't always pick the closer.
 */
private fun jsNumber(number: Double): String =
    when {
        number.isNaN() -> "NaN"
        number == 0.0 -> "0"
        number.isInfinite() -> if (number > 0) "Infinity" else "-Infinity"
        number < 0 -> "-" + jsNumber(-number)
        else -> jsPositive(number)
    }

private fun jsPositive(number: Double): String {
    val (digits, point) = exactDecimal(number)
    // Rounding the exact digits gives the closest candidate of each length, and all of them read back.
    val (shortest, shortestPoint) =
        (1..digits.length).firstNotNullOf { length ->
            rounded(digits, point, length).takeIf { (d, p) -> "0.${d}e$p".toDouble() == number }
        }
    return jsLayout(shortest, shortestPoint)
}

private const val MANTISSA_BITS = 52
private const val EXPONENT_MASK = 0x7FF
private const val EXPONENT_BIAS = 1075
private const val RADIX = 10

/**
 * A positive finite double's value, exactly, as 0.<digits> × 10^point. It is mantissa × 2^exponent, and
 * a negative power of two is a power of five over the same power of ten, so it always terminates.
 */
private fun exactDecimal(number: Double): Pair<String, Int> {
    val bits = number.toRawBits()
    val field = ((bits ushr MANTISSA_BITS) and EXPONENT_MASK.toLong()).toInt()
    val fraction = bits and ((1L shl MANTISSA_BITS) - 1)
    val mantissa = if (field == 0) fraction else fraction or (1L shl MANTISSA_BITS)
    val exponent = maxOf(field, 1) - EXPONENT_BIAS
    // Little-endian decimal digits, multiplied up in place.
    val value =
        mantissa
            .toString()
            .reversed()
            .map { it - '0' }
            .toMutableList()
    repeat(kotlin.math.abs(exponent)) { times(value, if (exponent > 0) 2 else RADIX / 2) }
    val digits = value.reversed().joinToString("")
    return digits.trimEnd('0') to digits.length - maxOf(-exponent, 0)
}

private fun times(
    value: MutableList<Int>,
    factor: Int,
) {
    var carry = 0
    for (index in value.indices) {
        val product = value[index] * factor + carry
        value[index] = product % RADIX
        carry = product / RADIX
    }
    if (carry > 0) value.add(carry)
}

/**
 * [digits] rounded to [length] of them, the nearest, and the even one of two equally near, as V8 picks
 * between two equally short candidates. A carry out of the first digit moves [point] up one.
 */
private fun rounded(
    digits: String,
    point: Int,
    length: Int,
): Pair<String, Int> {
    val kept = digits.take(length)
    val stem = kept.trimEnd('9')
    return when {
        length == digits.length -> digits to point

        digits[length] < '5' -> kept.trimEnd('0') to point

        // The exact digits carry no trailing zeros, so a final 5 is exactly halfway.
        digits.length == length + 1 && digits[length] == '5' && (kept.last() - '0') % 2 == 0 -> kept to point

        stem.isEmpty() -> "1" to point + 1

        else -> (stem.dropLast(1) + (stem.last() + 1)) to point
    }
}

// JS writes a number without an exponent from 1e-6 up to, not including, 1e21.
private const val JS_PLAIN_POINT_MIN = -5
private const val JS_PLAIN_POINT_MAX = 21

private fun jsLayout(
    digits: String,
    point: Int,
): String =
    when {
        point in digits.length..JS_PLAIN_POINT_MAX -> {
            digits + "0".repeat(point - digits.length)
        }

        point in 1..JS_PLAIN_POINT_MAX -> {
            digits.substring(0, point) + "." + digits.substring(point)
        }

        point in JS_PLAIN_POINT_MIN..0 -> {
            "0." + "0".repeat(-point) + digits
        }

        else -> {
            val exponent = point - 1
            val fraction = if (digits.length > 1) "." + digits.substring(1) else ""
            digits[0] + fraction + "e" + (if (exponent < 0) "-" else "+") + kotlin.math.abs(exponent)
        }
    }
