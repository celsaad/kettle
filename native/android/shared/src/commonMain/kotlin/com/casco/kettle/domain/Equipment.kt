package com.casco.kettle.domain

// An exercise's equipment (#393): the items it needs at hand, as the file spells them. The vocabulary
// of known items is data, `content/equipment.json`, which the app reads; nothing here holds a copy,
// so the parser accepts any spelling and only matching against that list says which are known.

/**
 * What [value] matches a known item by: trimmed as the schema trims it, lowercased, and every space
 * and hyphen made `_`, so `Pull-up bar` and `pull_up_bar` are one item. No synonyms and no plurals:
 * `dumbbells` and `DB` are the user's own words, kept as written.
 */
fun equipmentMatchKey(value: String): String =
    trimEquipment(value)
        .lowercase()
        .map { if (it == ' ' || it == '-') '_' else it }
        .joinToString("")

/** The known item [value] is, or null where it's the user's own words: [known] is the vocabulary's keys. */
fun knownEquipment(
    value: String,
    known: Set<String>,
): String? = equipmentMatchKey(value).takeIf { it in known }

/** [items] with each later one that matches an earlier one dropped: the first spelling wins. */
internal fun distinctEquipment(items: List<String>): List<String> = items.distinctBy(::equipmentMatchKey)

/**
 * Everything [workout]'s exercises need, combined: each item once, matched as [equipmentMatchKey]
 * matches it and spelled as it first appears, in the order the workout first reaches it. A circuit's
 * first round visits every member in turn, so that order is its members' order. A workout's
 * equipment is never stored, only derived, so it can't disagree with its exercises'; a block naming
 * an exercise the library doesn't have contributes nothing.
 */
fun workoutEquipment(
    library: Library,
    workout: Workout,
): List<String> {
    val byId = library.exercises.associateBy { it.id }
    val inOrder =
        workout.blocks.flatMap { block ->
            when (block) {
                is WorkoutBlock.ExerciseBlock -> listOf(block.exerciseId)
                is WorkoutBlock.Circuit -> block.members.map { it.exerciseId }
            }
        }
    return distinctEquipment(inOrder.flatMap { byId[it]?.equipment.orEmpty() })
}

/**
 * [value] without the whitespace JavaScript's `String.prototype.trim` removes, spelled out so the
 * format doesn't depend on a platform's idea of whitespace: Kotlin's `trim()` keeps U+FEFF and drops
 * U+001C–U+001F, both unlike JS. The set is ECMAScript's WhiteSpace and LineTerminator.
 */
internal fun trimEquipment(value: String): String = value.trim { it in JS_WHITESPACE }

private const val JS_WHITESPACE =
    "\t\n\u000B\u000C\r \u00A0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009" +
        "\u200A\u2028\u2029\u202F\u205F\u3000\uFEFF"
