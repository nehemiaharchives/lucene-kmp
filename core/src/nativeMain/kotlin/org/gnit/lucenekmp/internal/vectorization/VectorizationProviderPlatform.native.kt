package org.gnit.lucenekmp.internal.vectorization

import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
internal actual fun hasValidVectorizationCallerPlatform(validCallers: Set<String>): Boolean {
    val stack = Throwable().getStackTrace()
    // Try to match against known valid callers first.
    val matched = stack.any { frame ->
        validCallers.any { caller ->
            frame.contains(caller.replace('.', '/')) ||
            frame.contains("${caller.replace('.', '/')}.kt")
        }
    }
    if (matched) return true
    // Under some environments (e.g., Alpine Linux with gcompat), Kotlin/Native
    // stack traces contain only raw addresses and exported ELF symbol names
    // (e.g. "__cxa_demangle") with no Kotlin file/class paths. In that case
    // we cannot match against validCallers at all, so accept the call.
    // A real Kotlin source reference always includes ".kt:" followed by a line number.
    val hasKotlinSource = stack.any { frame -> frame.contains(".kt:") }
    if (!hasKotlinSource) return true
    return false
}
