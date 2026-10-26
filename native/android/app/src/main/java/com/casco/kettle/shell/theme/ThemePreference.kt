package com.casco.kettle.shell.theme

import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Settings → Appearance, as RN stores it in `preferences.json`. RN owns that file until M5, so native
 * only ever reads it (docs/native-migration-plan.md).
 */
enum class ThemePreference(
    private val stored: String,
) {
    Light("light"),
    Dark("dark"),
    System("system"),
    ;

    companion object {
        /**
         * What RN's `loadPreferences` would answer from [file]. Anything it refuses, it answers by
         * following the device, and so does this: the whole file, not just this field, has to pass
         * `preferencesSchema` (src/domain/preferences.ts), or the bar would pin a scheme the app around
         * it doesn't. The two share no code, so a field added there is added here.
         */
        fun read(file: File): ThemePreference {
            val json =
                try {
                    JSONObject(file.readText())
                } catch (_: IOException) {
                    null
                } catch (_: JSONException) {
                    null
                }
            // An absent field is defaulted in the schema: it arrived after the file was already written.
            return fromStored(json?.takeIf { it.passesSchema() }?.opt(THEME)) ?: System
        }

        private fun fromStored(value: Any?): ThemePreference? = entries.firstOrNull { it.stored == value }

        private const val THEME = "themePreference"
        private val unitSystems = setOf("metric", "imperial")

        // zod's object, which ignores keys it doesn't know. Every field but unitSystem is defaulted, so
        // it may be absent; present, it has to be the right type, and null is only a backup folder's.
        private fun JSONObject.passesSchema(): Boolean =
            listOf(
                opt("unitSystem") in unitSystems,
                !has(THEME) || fromStored(get(THEME)) != null,
                optional<Boolean>("restDayReminder"),
                optional<Boolean>("sessionSounds"),
                isNull("backupFolderUri") || get("backupFolderUri") is String,
            ).all { it }

        private inline fun <reified T> JSONObject.optional(key: String): Boolean = !has(key) || get(key) is T
    }
}
