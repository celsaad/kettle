package com.casco.kettle.domain

import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.decomposedStringWithCanonicalMapping
import platform.Foundation.precomposedStringWithCanonicalMapping

// Foundation's NFD and NFC, as the JVM's are java.text's. `create(string:)` is Beta interop, and the
// usual `this as NSString` is a cast the compiler warns can never succeed, though it does.

@OptIn(BetaInteropApi::class)
internal actual fun String.toNfd(): String = NSString.create(string = this).decomposedStringWithCanonicalMapping

@OptIn(BetaInteropApi::class)
internal actual fun String.toNfc(): String = NSString.create(string = this).precomposedStringWithCanonicalMapping
