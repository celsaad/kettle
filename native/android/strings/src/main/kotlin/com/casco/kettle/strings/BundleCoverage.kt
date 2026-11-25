package com.casco.kettle.strings

import com.ibm.icu.text.PluralRules
import com.ibm.icu.util.ULocale

/**
 * The plural categories CLDR gives [language], as ICU ships them: the rules Android's own resource
 * lookup selects a `quantity` by, though a device carries the CLDR of its own release rather than
 * this one, which `BundleCoverageTest` pins for the languages shipped. `en` has two, `pt` three, `ja`
 * one.
 */
internal fun pluralCategories(language: String): Set<String> =
    PluralRules.forLocale(ULocale.forLanguageTag(language)).keywords

/**
 * Languages whose CLDR rule files 0 under `one` where that reads wrong, and which therefore carry a
 * `_zero` beside every `_one`. Portuguese puts 0 and 1 in `one`, so `format.session` rendered
 * "0 sessão" where Brazilian Portuguese says "0 sessões"; `pluralText` reads the `_zero` before it asks
 * the plural rule, so the fix lives in the bundle and every plural needs it. A list rather than a rule
 * derived from CLDR, because French files 0 under `one` too and there "0 séance" is correct.
 */
private val ZERO_READS_PLURAL = setOf("pt")

/**
 * Every translation carries exactly English's keys, so a key missing from one fails the build rather
 * than rendering in English through the fallback, which reads as untranslated copy and not as a bug.
 * A plural is held to its own language's categories rather than English's spelling:
 *
 * - **Required** is the categories English distinguishes, narrowed to the ones the language has
 *   (`one`/`other` for `pt`, just `other` for `ja`), plus `zero` where [ZERO_READS_PLURAL] says so.
 * - **Permitted** is everything CLDR gives the language, plus that `zero`. `pt` also has `many`, which
 *   only applies from a million upward, a count no set or rep total reaches, so it is allowed and
 *   never demanded.
 *
 * Anything past permitted is dead: a key dropped from English leaves translations nothing renders,
 * and a `_one` in `ja` never renders, since every count there resolves to `other`.
 */
internal fun checkCoverage(
    language: String,
    entries: List<Entry>,
    english: List<Entry>,
) {
    val categories = pluralCategories(language)
    val zero = if (language in ZERO_READS_PLURAL) setOf(ZERO) else emptySet()
    val required = keys(english, pluralCategories(ENGLISH).intersect(categories) + zero)
    val permitted = keys(english, categories + zero)
    val actual = keys(entries)

    val missing = (required - actual).sorted()
    require(missing.isEmpty()) {
        "$language is missing ${missing.joinToString()} (English's keys, in its own plural categories)"
    }
    val extra = (actual - permitted).sorted()
    require(extra.isEmpty()) {
        "$language has ${extra.joinToString()}, which English doesn't have or $language can't select"
    }
}

/** [entries]' flat keys, each plural spelled out in [categories], or in its own forms when null. */
private fun keys(
    entries: List<Entry>,
    categories: Set<String>? = null,
): Set<String> =
    entries
        .flatMap { entry ->
            when (entry) {
                is StringEntry -> listOf(entry.key)
                is PluralEntry -> (categories ?: entry.forms.keys).map { "${entry.key}_$it" }
            }
        }.toSet()

/**
 * Sentence case, as every board draws a label (#493). The sweep that removed the letter-spaced caps
 * was listed by hand and missed every key with a placeholder, so the rule is held here instead: a
 * run of three or more capitals is an acronym on this list or a bug. Two letters pass on purpose
 * (PR, OK).
 */
private val ACRONYMS = setOf("AMRAP", "EMOM", "HIIT", "JSON", "YAML")

/**
 * A placeholder becomes a space, so a name like `{{URL}}` isn't read as copy, and nothing it sat
 * between runs together. A word's edges are cased letters only: Japanese glues Latin to kana
 * (`ほかのHIIT種目`), and an edge of any letter would let a shouted word hide inside it.
 */
private val SHOUTED = Regex("""(?<![\p{Lu}\p{Ll}\p{Lt}])\p{Lu}{3,}(?![\p{Lu}\p{Ll}\p{Lt}])""")

private val ANY_PLACEHOLDER = Regex("""\{\{[^}]*}}""")

internal fun checkCasing(
    language: String,
    entries: List<Entry>,
) {
    for (entry in entries) {
        val values =
            when (entry) {
                is StringEntry -> listOf(entry.key to entry.value)
                is PluralEntry -> entry.forms.map { (category, value) -> "${entry.key}_$category" to value }
            }
        for ((key, value) in values) {
            val shouted =
                SHOUTED
                    .findAll(
                        value.replace(ANY_PLACEHOLDER, " "),
                    ).map { it.value }
                    .filterNot(ACRONYMS::contains)
            require(shouted.none()) { "$language $key writes ${shouted.first()} in capitals, and it isn't an acronym" }
        }
    }
}
