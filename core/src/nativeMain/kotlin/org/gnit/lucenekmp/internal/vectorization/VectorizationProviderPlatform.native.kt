package org.gnit.lucenekmp.internal.vectorization

import kotlin.experimental.ExperimentalNativeApi
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.currentThreadId

private const val CHECKPOINT_CLASS_NAME = "org.gnit.lucenekmp.index.IndexFileDeleter"
private const val CHECKPOINT_METHOD_NAME = "checkpoint"
private const val READ_ONLY_CLONE_METHOD_NAME = "getReadOnlyClone"
private val checkpointHintLock = ReentrantLock()
private val checkpointHintDepthByThread = mutableMapOf<Long, Int>()
private val readOnlyCloneHintDepthByThread = mutableMapOf<Long, Int>()

internal actual inline fun <T> withCheckpointCallPathHint(block: () -> T): T {
    val threadId = currentThreadId()
    checkpointHintLock.lock()
    try {
        checkpointHintDepthByThread[threadId] = (checkpointHintDepthByThread[threadId] ?: 0) + 1
    } finally {
        checkpointHintLock.unlock()
    }
    try {
        return block()
    } finally {
        checkpointHintLock.lock()
        try {
            val nextDepth = (checkpointHintDepthByThread[threadId] ?: 1) - 1
            if (nextDepth <= 0) {
                checkpointHintDepthByThread.remove(threadId)
            } else {
                checkpointHintDepthByThread[threadId] = nextDepth
            }
        } finally {
            checkpointHintLock.unlock()
        }
    }
}

internal actual inline fun <T> withReadOnlyCloneCallPathHint(block: () -> T): T {
    val threadId = currentThreadId()
    checkpointHintLock.lock()
    try {
        readOnlyCloneHintDepthByThread[threadId] = (readOnlyCloneHintDepthByThread[threadId] ?: 0) + 1
    } finally {
        checkpointHintLock.unlock()
    }
    try {
        return block()
    } finally {
        checkpointHintLock.lock()
        try {
            val nextDepth = (readOnlyCloneHintDepthByThread[threadId] ?: 1) - 1
            if (nextDepth <= 0) {
                readOnlyCloneHintDepthByThread.remove(threadId)
            } else {
                readOnlyCloneHintDepthByThread[threadId] = nextDepth
            }
        } finally {
            checkpointHintLock.unlock()
        }
    }
}

internal actual fun currentStackTraceHasClassMethodFastPath(
    className: String,
    methodName: String
): Boolean? {
    if (className != CHECKPOINT_CLASS_NAME || methodName != CHECKPOINT_METHOD_NAME) {
        return null
    }
    val threadId = currentThreadId()
    checkpointHintLock.lock()
    return try {
        checkpointHintDepthByThread[threadId]?.let { it > 0 } ?: false
    } finally {
        checkpointHintLock.unlock()
    }
}

internal actual fun currentStackTraceHasAnyMethodFastPath(methodNames: Set<String>): Boolean? {
    if (methodNames.size != 1 || READ_ONLY_CLONE_METHOD_NAME !in methodNames) {
        return null
    }
    val threadId = currentThreadId()
    checkpointHintLock.lock()
    return try {
        readOnlyCloneHintDepthByThread[threadId]?.let { it > 0 } ?: false
    } finally {
        checkpointHintLock.unlock()
    }
}

@OptIn(ExperimentalNativeApi::class)
internal actual fun currentStackTraceHasClassMethodInternal(className: String, methodName: String): Boolean {
    val stack = Throwable().getStackTrace()
    return stack.any { frame ->
        nativeFrameHasClassMethod(frame, className, methodName)
    }
}

@OptIn(ExperimentalNativeApi::class)
internal actual fun currentStackTraceHasAnyMethodInternal(methodNames: Set<String>): Boolean {
    val stack = Throwable().getStackTrace()
    return stack.any { frame ->
        nativeFrameHasAnyMethod(frame, methodNames)
    }
}

@OptIn(ExperimentalNativeApi::class)
internal actual fun currentStackTraceHasClassInternal(className: String): Boolean {
    val stack = Throwable().getStackTrace()
    return stack.any { frame ->
        nativeFrameHasClass(frame, className)
    }
}

@OptIn(ExperimentalNativeApi::class)
internal actual fun hasValidVectorizationCallerPlatform(validCallers: Set<String>): Boolean {
    val stack = Throwable().getStackTrace()
    return stack.any { frame ->
        validCallers.any { frame.contains(it) }
    }
}

private fun nativeFrameHasClassMethod(frame: String, className: String, methodName: String): Boolean {
    val kfunPrefix = "kfun:"
    val kfunStart = frame.indexOf(kfunPrefix)
    if (kfunStart == -1) {
        return false
    }
    val signatureStart = kfunStart + kfunPrefix.length
    val classSep = frame.indexOf('#', signatureStart)
    if (classSep <= signatureStart) {
        return false
    }
    if (!frame.regionMatches(signatureStart, className, 0, className.length)) {
        return false
    }
    val methodStart = classSep + 1
    if (methodStart + methodName.length > frame.length) {
        return false
    }
    if (!frame.regionMatches(methodStart, methodName, 0, methodName.length)) {
        return false
    }
    val methodEnd = methodStart + methodName.length
    return methodEnd == frame.length ||
        frame[methodEnd] == '(' ||
        frame[methodEnd] == '-' ||
        frame[methodEnd] == ' ' ||
        frame[methodEnd] == '#'
}

private fun nativeFrameHasAnyMethod(frame: String, methodNames: Set<String>): Boolean {
    val kfunPrefix = "kfun:"
    val kfunStart = frame.indexOf(kfunPrefix)
    if (kfunStart == -1) {
        return false
    }
    val signatureStart = kfunStart + kfunPrefix.length
    val classSep = frame.indexOf('#', signatureStart)
    if (classSep <= signatureStart) {
        return false
    }
    val methodStart = classSep + 1
    for (methodName in methodNames) {
        if (methodStart + methodName.length > frame.length) {
            continue
        }
        if (!frame.regionMatches(methodStart, methodName, 0, methodName.length)) {
            continue
        }
        val methodEnd = methodStart + methodName.length
        if (methodEnd == frame.length ||
            frame[methodEnd] == '(' ||
            frame[methodEnd] == '-' ||
            frame[methodEnd] == ' ' ||
            frame[methodEnd] == '#'
        ) {
            return true
        }
    }
    return false
}

private fun nativeFrameHasClass(frame: String, className: String): Boolean {
    val kfunPrefix = "kfun:"
    val kfunStart = frame.indexOf(kfunPrefix)
    if (kfunStart == -1) {
        return false
    }
    val signatureStart = kfunStart + kfunPrefix.length
    val classSep = frame.indexOf('#', signatureStart)
    if (classSep <= signatureStart) {
        return false
    }
    return frame.regionMatches(signatureStart, className, 0, className.length)
}
