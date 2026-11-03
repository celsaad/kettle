package com.casco.kettle.domain

/** Settings → Appearance: pin a scheme, or follow the device. */
enum class ThemePreference { LIGHT, DARK, SYSTEM }

/** `preferences.json`, the app-owned settings file; `src/domain/preferences.ts` holds the reasoning. */
data class Preferences(
    val unitSystem: UnitSystem,
    val themePreference: ThemePreference,
    val restDayReminder: Boolean,
    val sessionSounds: Boolean,
    val backupFolderUri: String?,
)

/**
 * What a `preferences.json` says, or null where it says nothing usable: `parsePreferences` in
 * `src/domain/preferences.ts`, held to it by `conformance/preferences.yaml`.
 *
 * Read with the YAML loader, since RN writes the file with `JSON.stringify` and that output is YAML
 * 1.2. One wrong field refuses the whole file, as zod's object does, and keys it doesn't know are
 * ignored, as zod strips them.
 */
fun parsePreferences(text: String): Preferences? {
    val fields = (loadYaml(text) as? YamlLoad.Loaded)?.value as? Map<*, *>
    return fields?.let(::preferencesOf)
}

/**
 * [stored], or where there's none, what RN's store defaulted to: the device's [units] (the only layer
 * that can make that call), the system's scheme, the reminder off and the sounds on. A change starts
 * from this and writes all of it, so the first setting changed also writes down the rest.
 */
fun preferencesOrDefault(
    stored: Preferences?,
    units: UnitSystem,
) = stored
    ?: Preferences(
        units,
        ThemePreference.SYSTEM,
        restDayReminder = false,
        sessionSounds = true,
        backupFolderUri = null,
    )

/**
 * The file as RN's `JSON.stringify` wrote it, every field, so RN's read and an older build's read what
 * native Settings writes (#237). The whole file on every write, never just the field that changed.
 */
fun serializePreferences(preferences: Preferences): String =
    listOf(
        "unitSystem" to jsonString(preferences.unitSystem.name.lowercase()),
        THEME to jsonString(preferences.themePreference.name.lowercase()),
        REMINDER to preferences.restDayReminder.toString(),
        SOUNDS to preferences.sessionSounds.toString(),
        BACKUP to (preferences.backupFolderUri?.let(::jsonString) ?: "null"),
    ).joinToString(",", "{", "}") { (key, value) -> "${jsonString(key)}:$value" }

private fun preferencesOf(fields: Map<*, *>): Preferences? {
    val unitSystem = UNIT_SYSTEMS[fields["unitSystem"]]
    if (unitSystem == null || !fields.defaultedFieldsValid()) return null
    return Preferences(
        unitSystem = unitSystem,
        themePreference = THEMES[fields[THEME]] ?: ThemePreference.SYSTEM,
        restDayReminder = fields[REMINDER] as? Boolean ?: false,
        sessionSounds = fields[SOUNDS] as? Boolean ?: true,
        backupFolderUri = fields[BACKUP] as String?,
    )
}

/**
 * Every field but the unit system is defaulted, because each arrived after the file was already being
 * written. A default fills in an absent field only: present, it has to be the right type, and null is
 * allowed in the backup folder alone, which is nullable as well.
 */
private fun Map<*, *>.defaultedFieldsValid(): Boolean =
    listOf(
        !keys.contains(THEME) || THEMES.containsKey(this[THEME]),
        !keys.contains(REMINDER) || this[REMINDER] is Boolean,
        !keys.contains(SOUNDS) || this[SOUNDS] is Boolean,
        this[BACKUP] == null || this[BACKUP] is String,
    ).all { it }

private const val THEME = "themePreference"
private const val REMINDER = "restDayReminder"
private const val SOUNDS = "sessionSounds"
private const val BACKUP = "backupFolderUri"

private val UNIT_SYSTEMS = mapOf("metric" to UnitSystem.METRIC, "imperial" to UnitSystem.IMPERIAL)

private val THEMES = ThemePreference.entries.associateBy { it.name.lowercase() }
