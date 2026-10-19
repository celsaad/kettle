package com.casco.kettle.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * Name search, ported from `src/domain/search.ts`, whose header holds the rules and
 * the reasoning; `conformance/search.yaml` is what this is held to. The one thing the port has to
 * add is code points: a Kotlin `String` indexes UTF-16 units as JS does, but the TS iterates with
 * `for…of` and `Array.from`, which walk code points, so the typo checks here do the same.
 */

private const val TYPO_MIN_LENGTH = 4

/** The shortest start of a name word a typo is checked against; `search.ts` says why. */
private const val TYPO_MIN_PREFIX = 5

private const val TIER_WORD_START = 0
private const val TIER_INSIDE_WORD = 1
private const val TIER_TYPO = 2

private const val HIRAGANA_FIRST = 0x3041
private const val HIRAGANA_LAST = 0x3096
private const val KATAKANA_OFFSET = 0x60

/** Every hiragana is in the BMP, so folding one `Char` at a time can't split a surrogate pair. */
private fun foldKana(value: String): String =
    buildString(value.length) {
        for (char in value) {
            append(if (char.code in HIRAGANA_FIRST..HIRAGANA_LAST) Char(char.code + KATAKANA_OFFSET) else char)
        }
    }

private fun wordsOf(value: String): List<String> = nameWords(foldKana(foldName(value)))

/**
 * Explicit ranges, never a script class, so no platform's Unicode tables can change the answer. They
 * are the same four as `search.ts`, so the numbers are the spec rather than constants to name.
 */
@Suppress("MagicNumber")
private val TYPO_RANGES: List<IntRange> =
    listOf(
        0x61..0x7a, // a–z
        0xdf..0x24f, // Latin-1 lowercase, Latin Extended-A and -B
        0x370..0x3ff, // Greek
        0x400..0x52f, // Cyrillic and its supplement
    )

private fun forgivesTypos(word: IntArray): Boolean =
    word.size >= TYPO_MIN_LENGTH && word.all { code -> TYPO_RANGES.any { code in it } }

/** Optimal string alignment distance: Levenshtein plus a swap of two neighbours as one edit. */
private fun editDistance(
    a: IntArray,
    b: IntArray,
): Int {
    val d =
        Array(a.size + 1) { i ->
            IntArray(b.size + 1) { j ->
                if (i == 0) {
                    j
                } else if (j == 0) {
                    i
                } else {
                    0
                }
            }
        }
    for (i in 1..a.size) {
        for (j in 1..b.size) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = min(min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost)
            val swapped = i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]
            if (swapped) {
                d[i][j] = min(d[i][j], d[i - 2][j - 2] + 1)
            }
        }
    }
    return d[a.size][b.size]
}

/**
 * Starts one shorter and one longer than the query are tried too, since a dropped or doubled letter
 * shifts everything after it.
 */
private fun withinOneTypo(
    query: IntArray,
    word: IntArray,
): Boolean {
    if (abs(word.size - query.size) <= 1 && editDistance(query, word) <= 1) return true
    val shortest = max(query.size - 1, TYPO_MIN_PREFIX)
    val longest = min(query.size + 1, word.size - 1)
    return (shortest..longest).any { length -> editDistance(query, word.copyOf(length)) <= 1 }
}

private fun tierOf(
    token: String,
    words: List<String>,
): Int? =
    when {
        words.any { it.startsWith(token) } -> {
            TIER_WORD_START
        }

        words.any { it.contains(token) } -> {
            TIER_INSIDE_WORD
        }

        // Code points only once both substring checks have failed, as the TS builds them.
        else -> {
            codePoints(token)
                .takeIf(::forgivesTypos)
                ?.let { tokenPoints -> TIER_TYPO.takeIf { words.any { withinOneTypo(tokenPoints, codePoints(it)) } } }
        }
    }

/** A name ranks by its worst word, so one typo drops it below every clean match. */
private fun matchTier(
    tokens: List<String>,
    name: String,
): Int? {
    val words = wordsOf(name)
    var worst = TIER_WORD_START
    for (token in tokens) {
        val tier = tierOf(token, words) ?: return null
        worst = max(worst, tier)
    }
    return worst
}

private class Match<T>(
    val item: T,
    val tier: Int,
)

/** Every matching item with its tier, in the original order; `null` when the query has no words. */
private fun <T> matches(
    items: List<T>,
    query: String,
    nameOf: (T) -> String,
): List<Match<T>>? {
    val tokens = wordsOf(query)
    if (tokens.isEmpty()) return null
    return items.mapNotNull { item -> matchTier(tokens, nameOf(item))?.let { Match(item, it) } }
}

/** The items whose name matches, best matches first and the original order kept within a tier. */
fun <T> rankByName(
    items: List<T>,
    query: String,
    nameOf: (T) -> String,
): List<T> =
    // `sortedBy` is stable, which is what keeps the original order within a tier, so this needs no
    // index tie-break like the one the TS sorts on.
    matches(items, query, nameOf)?.sortedBy { it.tier }?.map { it.item } ?: items

/** The same match as `rankByName`, in the original order — for a list whose order means something. */
fun <T> filterByName(
    items: List<T>,
    query: String,
    nameOf: (T) -> String,
): List<T> = matches(items, query, nameOf)?.map { it.item } ?: items
