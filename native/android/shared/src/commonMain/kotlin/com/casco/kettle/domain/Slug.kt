package com.casco.kettle.domain

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
