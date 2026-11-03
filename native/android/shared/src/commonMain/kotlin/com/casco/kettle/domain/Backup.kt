package com.casco.kettle.domain

/**
 * Prefixed because the destination is a folder of the user's own files, not an app sandbox. The history
 * export shares its archive under the same name, so a backup and an export arrive as the same file.
 */
const val LIBRARY_BACKUP_NAME = "kettle-library.yaml"
const val HISTORY_BACKUP_NAME = "kettle-history.yaml"

/**
 * The last path segment of a SAF tree URI, decoded: `primary:Documents/Kettle` becomes
 * `Documents/Kettle`, as RN's `backupFolderLabel` did until the preferences flip (#238). Only for the
 * user to recognise; nothing reads it back, and it's their own path, so it renders verbatim. A tree
 * URI's document id is `<volume>:<path>`, so the cut is at the colon after `/tree/`, never the scheme's
 * own. With no volume marker there's no path to show, and the decoded URI is the honest answer; one
 * that won't decode is answered as it is.
 */
fun backupFolderLabel(folderUri: String): String {
    val decoded = decodeUriComponent(folderUri) ?: return folderUri
    // JS's slice(-1) where there's no /tree/: the last character, which a volume marker can't be in.
    val tree = decoded.indexOf("/tree/").let { if (it < 0) decoded.takeLast(1) else decoded.substring(it) }
    val afterVolume = Regex(":([^:]*)$").find(tree)?.groupValues?.get(1)
    return afterVolume?.trim('/')?.ifEmpty { null } ?: decoded
}

/**
 * `decodeURIComponent`, or null where it would throw: a `%` without two hex digits after it, or escapes
 * that aren't UTF-8. Each run of escapes decodes as one, since a character can take several bytes.
 */
private fun decodeUriComponent(text: String): String? {
    if (STRAY_PERCENT.containsMatchIn(text)) return null
    return try {
        ESCAPE_RUN.replace(text) { run ->
            run.value
                .chunked(ESCAPE_LENGTH)
                .map { it.drop(1).toInt(HEX).toByte() }
                .toByteArray()
                .decodeToString(throwOnInvalidSequence = true)
        }
    } catch (_: CharacterCodingException) {
        null
    }
}

private val STRAY_PERCENT = Regex("%(?![0-9A-Fa-f]{2})")
private val ESCAPE_RUN = Regex("(%[0-9A-Fa-f]{2})+")
private const val ESCAPE_LENGTH = 3

/**
 * What the last backup wrote, as file name → document URI, `backup-targets.json`: on a provider whose
 * document ids aren't names (Drive's), it's the only way to find those documents again. Anything that
 * isn't a record of strings reads as nothing remembered, which costs one more pair of files at worst.
 */
fun parseBackupTargets(text: String): Map<String, String> {
    val fields = (loadYaml(text) as? YamlLoad.Loaded)?.value as? Map<*, *>
    val strings = fields?.entries?.takeIf { entries -> entries.all { it.key is String && it.value is String } }
    return strings?.associate { (key, value) -> key as String to value as String }.orEmpty()
}

/** The record as RN's `JSON.stringify` wrote it, so an upgrade reads what the last RN backup remembered. */
fun serializeBackupTargets(targets: Map<String, String>): String =
    targets.entries.joinToString(",", "{", "}") { (name, uri) -> "${jsonString(name)}:${jsonString(uri)}" }

private const val HEX = 16
