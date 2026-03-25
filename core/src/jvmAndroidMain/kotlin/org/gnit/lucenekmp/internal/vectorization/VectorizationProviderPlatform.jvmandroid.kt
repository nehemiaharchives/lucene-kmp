package org.gnit.lucenekmp.internal.vectorization

internal actual fun hasValidVectorizationCallerPlatform(validCallers: Set<String>): Boolean {
    return Throwable().stackTrace.drop(1).any { frame ->
        validCallers.any { caller -> frame.className.contains(caller) || frame.methodName.contains(caller) }
    }
}
