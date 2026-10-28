package com.casco.kettle.shell.rn

/** The files an RN store reads, by the name src/state/files-changed.ts knows each one by. */
enum class FileKind(
    val wire: String,
) {
    LIBRARY("library"),
    SESSIONS("sessions"),
    PREFERENCES("preferences"),

    /** Support's tip count, which RN Settings shows under the sheet. Goes with RN Settings in M5. */
    SUPPORTER("supporter"),
    ;

    companion object {
        fun fromWire(wire: String): FileKind? = entries.firstOrNull { it.wire == wire }
    }
}
