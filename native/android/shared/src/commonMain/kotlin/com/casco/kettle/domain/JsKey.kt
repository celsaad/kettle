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
    val exact = exactDecimal(number)
    // Rounding the exact digits gives the closest candidate of each length, and all of them read back.
    val shortest =
        (1..exact.digits.length).firstNotNullOf { length ->
            exact.rounded(length).takeIf { "0.${it.digits}e${it.point}".toDouble() == number }
        }
    return shortest.jsLayout()
}

/** The number 0.<digits> × 10^point, with no leading or trailing zero in [digits]. */
private data class Decimal(
    val digits: String,
    val point: Int,
)

private const val MANTISSA_BITS = 52
private const val EXPONENT_MASK = 0x7FF
private const val EXPONENT_BIAS = 1075

// The expansion is held in base-10^9 limbs and multiplied by 2^30 or 5^13 a step, the largest powers
// whose product with a limb still fits a Long. One digit and one factor of 2 or 5 a step cost ~0.7 ms
// for a subnormal, which a hostile file of 20,000 such keys turned into 15 s.
private const val LIMB = 1_000_000_000L
private const val LIMB_DIGITS = 9
private const val TWOS_PER_STEP = 30
private const val FIVES_PER_STEP = 13

/**
 * A positive finite double's value, exactly. It is mantissa × 2^exponent, and a negative power of two
 * is a power of five over the same power of ten, so the expansion always terminates.
 */
private fun exactDecimal(number: Double): Decimal {
    val bits = number.toRawBits()
    val field = ((bits ushr MANTISSA_BITS) and EXPONENT_MASK.toLong()).toInt()
    val fraction = bits and ((1L shl MANTISSA_BITS) - 1)
    val mantissa = if (field == 0) fraction else fraction or (1L shl MANTISSA_BITS)
    val exponent = maxOf(field, 1) - EXPONENT_BIAS
    // Little-endian limbs, multiplied up in place.
    val limbs = mutableListOf(mantissa % LIMB, mantissa / LIMB)
    var remaining = kotlin.math.abs(exponent)
    while (remaining > 0) {
        val step = minOf(remaining, if (exponent > 0) TWOS_PER_STEP else FIVES_PER_STEP)
        multiplyLimbs(limbs, if (exponent > 0) 1L shl step else power(BASE_FIVE, step))
        remaining -= step
    }
    val text =
        limbs.asReversed().dropWhile { it == 0L }.let { high ->
            high.first().toString() + high.drop(1).joinToString("") { it.toString().padStart(LIMB_DIGITS, '0') }
        }
    return Decimal(text.trimEnd('0'), text.length - maxOf(-exponent, 0))
}

private const val BASE_FIVE = 5L

private fun power(
    base: Long,
    exponent: Int,
): Long = (1..exponent).fold(1L) { product, _ -> product * base }

private fun multiplyLimbs(
    limbs: MutableList<Long>,
    factor: Long,
) {
    var carry = 0L
    for (index in limbs.indices) {
        val product = limbs[index] * factor + carry
        limbs[index] = product % LIMB
        carry = product / LIMB
    }
    while (carry > 0) {
        limbs.add(carry % LIMB)
        carry /= LIMB
    }
}

/**
 * Rounded to [length] digits: the nearest, and the even one of two equally near, as V8 picks between
 * two equally short candidates. A carry out of the first digit moves the point up one.
 */
private fun Decimal.rounded(length: Int): Decimal {
    val kept = digits.take(length)
    val stem = kept.trimEnd('9')
    return when {
        length == digits.length -> this

        digits[length] < '5' -> Decimal(kept.trimEnd('0'), point)

        // The digits carry no trailing zeros, so a final 5 is exactly halfway.
        digits.length == length + 1 && digits[length] == '5' && (kept.last() - '0') % 2 == 0 -> Decimal(kept, point)

        stem.isEmpty() -> Decimal("1", point + 1)

        else -> Decimal(stem.dropLast(1) + (stem.last() + 1), point)
    }
}

// JS writes a number without an exponent from 1e-6 up to, not including, 1e21.
private const val JS_PLAIN_POINT_MIN = -5
private const val JS_PLAIN_POINT_MAX = 21

private fun Decimal.jsLayout(): String =
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
