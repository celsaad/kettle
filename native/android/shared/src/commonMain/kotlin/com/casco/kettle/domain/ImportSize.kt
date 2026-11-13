package com.casco.kettle.domain

/**
 * The most an import takes, in UTF-8 bytes, whether picked or pasted: many times any library written
 * by hand or by an assistant, the seed included. A library from outside is parsed on the main thread,
 * and once merged it is parsed again at every launch, where parsing grows with the square of a long
 * unbroken scalar's length. It bounds one import, not the library it is merged into: what keeps that
 * readable is the library writer's own refusal past [MAX_YAML_CODE_POINTS].
 */
const val MAX_IMPORT_BYTES = 512 * 1024

/** Whether [text] is past [MAX_IMPORT_BYTES] as UTF-8, counted without encoding it and stopping there. */
fun exceedsImportSize(text: String): Boolean {
    var bytes = 0L
    var i = 0
    while (i < text.length) {
        val c = text[i]
        // A surrogate pair is one code point, four bytes, over two chars.
        val pair = c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()
        bytes +=
            when {
                c.code < UTF8_ONE_BYTE -> 1
                c.code < UTF8_TWO_BYTES -> 2
                pair -> SUPPLEMENTARY_BYTES
                else -> BMP_BYTES
            }
        if (bytes > MAX_IMPORT_BYTES) return true
        i += if (pair) 2 else 1
    }
    return false
}

private const val UTF8_ONE_BYTE = 0x80
private const val UTF8_TWO_BYTES = 0x800

/** What the rest of the Basic Multilingual Plane takes as UTF-8, and what a code point past it takes. */
private const val BMP_BYTES = 3
private const val SUPPLEMENTARY_BYTES = 4
