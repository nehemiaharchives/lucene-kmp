package org.gnit.lucenekmp.internal.vectorization

import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
internal actual fun hasValidVectorizationCallerPlatform(validCallers: Set<String>): Boolean {
    val stack = Throwable().getStackTrace()
    return stack.any { frame ->
        validCallers.any { frame.contains(it) }
    }
}
