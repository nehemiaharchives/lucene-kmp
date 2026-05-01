package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.currentThreadId

private val currentCallPathHintsByThread = mutableMapOf<Long, MutableList<String>>()
private val currentCallPathHintsLock = ReentrantLock()

fun currentStackTraceHasClassMethod(className: String, methodName: String): Boolean {
    currentStackTraceHasClassMethodFastPath(className, methodName)?.let { return it }
    if (hasCurrentCallPathHint(className, methodName)) {
        return true
    }
    return currentStackTraceHasClassMethodInternal(className, methodName)
}

fun currentStackTraceHasAnyMethod(methodNames: Set<String>): Boolean {
    currentStackTraceHasAnyMethodFastPath(methodNames)?.let { return it }
    if (hasCurrentCallPathHintAnyMethod(methodNames)) {
        return true
    }
    return currentStackTraceHasAnyMethodInternal(methodNames)
}

fun currentStackTraceHasClass(className: String): Boolean {
    return currentStackTraceHasClassInternal(className)
}

fun <T> withCurrentCallPathHint(className: String, methodName: String, block: () -> T): T {
    val threadId = currentThreadId()
    val hintKey = "$className#$methodName"
    currentCallPathHintsLock.lock()
    try {
        currentCallPathHintsByThread.getOrPut(threadId) { mutableListOf() }.add(hintKey)
    } finally {
        currentCallPathHintsLock.unlock()
    }
    try {
        return block()
    } finally {
        currentCallPathHintsLock.lock()
        try {
            val hints = currentCallPathHintsByThread[threadId]
            if (hints != null) {
                hints.removeAt(hints.lastIndex)
                if (hints.isEmpty()) {
                    currentCallPathHintsByThread.remove(threadId)
                }
            }
        } finally {
            currentCallPathHintsLock.unlock()
        }
    }
}

expect fun <T> withCheckpointCallPathHint(block: () -> T): T

expect fun <T> withReadOnlyCloneCallPathHint(block: () -> T): T

expect fun <T> withDocumentsWriterPerThreadFlushCallPathHint(block: () -> T): T

expect fun <T> withIndexingChainFlushCallPathHint(block: () -> T): T

internal expect fun currentStackTraceHasClassMethodFastPath(className: String, methodName: String): Boolean?

internal expect fun currentStackTraceHasAnyMethodFastPath(methodNames: Set<String>): Boolean?

internal fun hasCurrentCallPathHint(className: String, methodName: String): Boolean {
    val threadId = currentThreadId()
    val hintKey = "$className#$methodName"
    currentCallPathHintsLock.lock()
    return try {
        currentCallPathHintsByThread[threadId]?.contains(hintKey) == true
    } finally {
        currentCallPathHintsLock.unlock()
    }
}

internal fun hasCurrentCallPathHintAnyMethod(methodNames: Set<String>): Boolean {
    val threadId = currentThreadId()
    currentCallPathHintsLock.lock()
    return try {
        val hints = currentCallPathHintsByThread[threadId] ?: return false
        hints.any { hint ->
            val methodSep = hint.lastIndexOf('#')
            methodSep != -1 && hint.substring(methodSep + 1) in methodNames
        }
    } finally {
        currentCallPathHintsLock.unlock()
    }
}

internal expect fun currentStackTraceHasClassMethodInternal(className: String, methodName: String): Boolean

internal expect fun currentStackTraceHasAnyMethodInternal(methodNames: Set<String>): Boolean

internal expect fun currentStackTraceHasClassInternal(className: String): Boolean
