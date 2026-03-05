package org.gnit.lucenekmp.internal.vectorization

internal actual fun hasValidVectorizationCallerPlatform(validCallers: Set<String>): Boolean {
    val trace = Throwable().stackTraceToString()
    return validCallers.any { trace.contains(it) }
}
