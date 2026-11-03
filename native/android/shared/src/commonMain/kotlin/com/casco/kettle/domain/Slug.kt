package com.casco.kettle.domain

/**
 * A stable id from a user-typed name, as `slugify` in `src/domain/slug.ts`, which holds the reasoning:
 * the same name gives the same id in any language and however it was typed. Empty for a name with no
 * letter or digit in it, which the caller refuses as "could not derive an id".
 */
fun slugify(name: String): String = nameWords(foldName(name)).joinToString("-")

/**
 * The folding half of `src/domain/slug.ts`, which holds the reasoning: Latin diacritics stripped
 * through an NFD → strip → NFC round-trip, so a name compares the same however it was typed, and
 * only U+0300–U+036F, so Indic and Thai vowel signs survive as the letters they are.
 */
internal fun foldName(name: String): String =
    name
        .toNfd()
        .replace(COMBINING_DIACRITICS, "")
        .toNfc()
        .lowercase()

/** The letter-or-digit tokens of an already folded name; a token may carry marks after its first character. */
internal fun nameWords(folded: String): List<String> = NAME_WORD.findAll(folded).map { it.value }.toList()

private val COMBINING_DIACRITICS = Regex("[\\u0300-\\u036f]")
private val NAME_WORD = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}\\p{M}]*")
