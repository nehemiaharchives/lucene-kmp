package org.gnit.lucenekmp.internal.vectorization

import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
internal actual fun hasValidVectorizationCallerPlatform(validCallers: Set<String>): Boolean {
    val stack = Throwable().getStackTrace()
    val callerFrame = stack.getOrNull(3) ?: return false
    return validCallers.any { callerFrame.contains(it) }
}
