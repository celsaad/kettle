package com.casco.kettle.domain

import java.text.Normalizer

internal actual fun String.toNfd(): String = Normalizer.normalize(this, Normalizer.Form.NFD)

internal actual fun String.toNfc(): String = Normalizer.normalize(this, Normalizer.Form.NFC)
