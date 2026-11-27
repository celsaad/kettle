package com.casco.kettle.domain

/**
 * [value] as `JSON.stringify` writes a string, character for character, wherever a string has to come
 * out exactly as files already on the phone have it: the JSON files the app before the native rewrite
 * wrote and this one still reads (preferences, `supporter.json`, backups), and [recordKey], whose
 * form `conformance/records.yaml` pins.
 */
internal fun jsonString(value: String): String =
    buildString {
        append('"')
        for (char in value) {
            when {
                char == '"' -> append("\\\"")
                char == '\\' -> append("\\\\")
                char == '\n' -> append("\\n")
                char == '\r' -> append("\\r")
                char == '\t' -> append("\\t")
                char == '\b' -> append("\\b")
                char == '\u000C' -> append("\\f")
                char < ' ' -> append("\\u").append(char.code.toString(HEX).padStart(UNICODE_DIGITS, '0'))
                else -> append(char)
            }
        }
        append('"')
    }

private const val HEX = 16
private const val UNICODE_DIGITS = 4
