package com.casco.kettle.domain

/**
 * [value] as `JSON.stringify` writes a string, character for character, wherever a Kotlin string has
 * to come out exactly as RN's would: `supporter.json`, which both sides read, and [recordKey], which
 * `conformance/records.yaml` holds to RN's.
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
