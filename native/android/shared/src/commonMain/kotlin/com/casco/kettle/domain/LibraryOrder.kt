package com.casco.kettle.domain

/**
 * [items] in their own order, the library file's, except that those whose id is in [leading] come
 * first, in [leading]'s order (#488): the program Today runs at the top of Programs, and its workouts
 * at the top of Workouts. The decision log's sorting entry is why nothing else moves. A search ranks
 * this list, and its ranking is stable, so the leading rows still lead among equally good matches.
 */
fun <T> leadingFirst(
    items: List<T>,
    leading: Collection<String>,
    idOf: (T) -> String,
): List<T> {
    if (leading.isEmpty()) return items
    val rank = leading.withIndex().associate { (index, id) -> id to index }
    // A partition rather than a lookup, so every row survives: two rows the file gives one id both
    // stay, in the file's order, where a map by id would keep one and drop the other.
    val (first, rest) = items.partition { idOf(it) in rank }
    return first.sortedBy { rank.getValue(idOf(it)) } + rest
}
