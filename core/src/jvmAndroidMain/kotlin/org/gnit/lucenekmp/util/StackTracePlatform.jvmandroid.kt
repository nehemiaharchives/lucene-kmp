package org.gnit.lucenekmp.util

actual fun <T> withCheckpointCallPathHint(block: () -> T): T {
    return block()
}

actual fun <T> withReadOnlyCloneCallPathHint(block: () -> T): T {
    return block()
}

actual fun <T> withDocumentsWriterPerThreadFlushCallPathHint(block: () -> T): T {
    return block()
}

actual fun <T> withIndexingChainFlushCallPathHint(block: () -> T): T {
    return block()
}

actual fun <T> withIndexingChainCallPathHint(block: () -> T): T {
    return block()
}

actual fun <T> withMockDirectoryWrapperDeleteFileCallPathHint(block: () -> T): T {
    return block()
}

actual fun <T> withPerFieldInvertCallPathHint(block: () -> T): T {
    return block()
}

internal actual fun currentStackTraceHasClassMethodFastPath(
    className: String,
    methodName: String
): Boolean? = null

internal actual fun currentStackTraceHasAnyMethodFastPath(methodNames: Set<String>): Boolean? = null

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
