package com.casco.kettle.domain

/**
 * The YAML inside a chat reply (#569): an assistant answers with prose around one fenced block,
 * "```yaml" to "```", and sharing or pasting the whole reply handed Import the prose too, refused as
 * invalid YAML. Exactly one block gives its contents; none, or several, which could be anything, gives
 * null, and the text is reviewed as it came.
 */
fun fencedYaml(text: String): String? =
    FENCE
        .findAll(text)
        .toList()
        .singleOrNull()
        ?.groupValues
        ?.get(1)

/** An opening fence of three or more backticks, tagged yaml or yml or not at all, to its closing one. */
private val FENCE =
    Regex("""(?m)^[ \t]*```+[ \t]*(?:ya?ml)?[ \t]*\r?\n(.*?)^[ \t]*```+[ \t]*$""", RegexOption.DOT_MATCHES_ALL)
