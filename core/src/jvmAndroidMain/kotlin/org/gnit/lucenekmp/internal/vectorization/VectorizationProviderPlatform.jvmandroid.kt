package org.gnit.lucenekmp.internal.vectorization

internal actual fun currentStackTraceHasClassMethodInternal(className: String, methodName: String): Boolean {
    return Throwable().stackTrace.drop(1).any { frame ->
        frame.className == className && frame.methodName == methodName
    }
}

internal actual fun currentStackTraceHasAnyMethodInternal(methodNames: Set<String>): Boolean {
    return Throwable().stackTrace.drop(1).any { frame ->
        frame.methodName in methodNames
    }
}

internal actual fun currentStackTraceHasClassInternal(className: String): Boolean {
    return Throwable().stackTrace.drop(1).any { frame ->
        frame.className == className
    }
}

internal actual fun hasValidVectorizationCallerPlatform(validCallers: Set<String>): Boolean {
    return Throwable().stackTrace.drop(1).any { frame ->
        validCallers.any { caller -> frame.className.contains(caller) || frame.methodName.contains(caller) }
    }
}
