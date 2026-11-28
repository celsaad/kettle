package com.casco.kettle.domain

/**
 * The YAML inside a chat reply (#569): an assistant answers with prose around a fenced block, and sharing
 * or pasting the whole reply handed Import the prose too, refused as invalid YAML. When the text holds
 * exactly one fenced block, of any language, and it's tagged yaml or yml or not at all, its contents;
 * else null, and the text is reviewed as it came: none, a block in another language, or several, which
 * could be a plan and its alternative, or an example before the library.
 *
 * Fences as CommonMark reads them: a line of three or more backticks or tildes, indented three spaces at
 * most, opens a block whose info string's first word is its language; the block closes on a line of the
 * same character at least as long, or at the end of the text. One pass over the lines, so a 512 KB paste
 * of unclosed fences costs no more than reading it, on the main thread where Import calls it.
 */
fun fencedYaml(text: String): String? {
    val lines = text.split('\n')
    val blocks = mutableListOf<Pair<String, String>>()
    var i = 0
    while (i < lines.size) {
        val open = fenceOf(lines[i])
        if (open == null) {
            i++
            continue
        }
        val start = i + 1
        var end = start
        while (end < lines.size && !closes(lines[end], open)) end++
        val language = open.info.substringBefore(' ').lowercase()
        // An unclosed block runs to the end, less the empty line after the text's last line end.
        val last = if (end == lines.size && lines.last().isEmpty()) end - 1 else end
        blocks += language to lines.subList(start, maxOf(start, last)).joinToString("") { "$it\n" }
        i = end + 1
    }
    val (language, body) = blocks.singleOrNull() ?: return null
    return body.takeIf { language in YAML_TAGS }
}

private class Fence(
    val char: Char,
    val length: Int,
    val info: String,
)

/** [line] as an opening fence, or null. A backtick fence's info string has no backtick, as CommonMark has it. */
private fun fenceOf(line: String): Fence? {
    val body = line.trimEnd('\r')
    val indent = body.length - body.trimStart(' ').length
    val fence = body.trimStart(' ')
    val char = fence.firstOrNull()?.takeIf { indent <= MAX_INDENT && (it == '`' || it == '~') } ?: return null
    val length = fence.takeWhile { it == char }.length
    val info = fence.drop(length).trim()
    return Fence(char, length, info).takeIf { length >= MIN_FENCE && !(char == '`' && '`' in info) }
}

/** Whether [line] closes [open]: its character, at least as long, nothing after but spaces. */
private fun closes(
    line: String,
    open: Fence,
): Boolean {
    val body = line.trimEnd('\r', ' ', '\t')
    val indent = body.length - body.trimStart(' ').length
    val fence = body.trimStart(' ')
    return indent <= MAX_INDENT && fence.length >= open.length && fence.all { it == open.char }
}

private val YAML_TAGS = setOf("", "yaml", "yml")
private const val MIN_FENCE = 3
private const val MAX_INDENT = 3
