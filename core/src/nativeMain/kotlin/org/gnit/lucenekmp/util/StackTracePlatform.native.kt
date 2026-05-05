package org.gnit.lucenekmp.util

import kotlin.experimental.ExperimentalNativeApi
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.currentThreadId

private const val CHECKPOINT_CLASS_NAME = "org.gnit.lucenekmp.index.IndexFileDeleter"
private const val CHECKPOINT_METHOD_NAME = "checkpoint"
private const val DOCUMENTS_WRITER_PER_THREAD_CLASS_NAME = "org.gnit.lucenekmp.index.DocumentsWriterPerThread"
private const val DOCUMENTS_WRITER_PER_THREAD_FLUSH_METHOD_NAME = "flush"
private const val INDEXING_CHAIN_CLASS_NAME = "org.gnit.lucenekmp.index.IndexingChain"
private const val INDEXING_CHAIN_FLUSH_METHOD_NAME = "flush"
private const val INDEXING_CHAIN_ABORT_METHOD_NAME = "abort"
private const val INDEXING_CHAIN_FINISH_DOCUMENT_METHOD_NAME = "finishDocument"
private const val STORED_FIELDS_WRITER_CLASS_NAME = "org.gnit.lucenekmp.codecs.StoredFieldsWriter"
private const val STORED_FIELDS_WRITER_FINISH_DOCUMENT_METHOD_NAME = "finishDocument"
private const val PERSISTENT_SNAPSHOT_DELETION_POLICY_CLASS_NAME =
    "org.gnit.lucenekmp.index.PersistentSnapshotDeletionPolicy"
private const val PERSISTENT_SNAPSHOT_DELETION_POLICY_PERSIST_METHOD_NAME = "persist"
private const val READ_ONLY_CLONE_METHOD_NAME = "getReadOnlyClone"
private const val MOCK_DIRECTORY_WRAPPER_CLASS_NAME = "org.gnit.lucenekmp.tests.store.MockDirectoryWrapper"
private const val MOCK_DIRECTORY_WRAPPER_DELETE_FILE_METHOD_NAME = "deleteFile"
private const val PER_FIELD_CLASS_NAME = "org.gnit.lucenekmp.index.IndexingChain\$PerField"
private const val PER_FIELD_INVERT_METHOD_NAME = "invert"
private val checkpointHintLock = ReentrantLock()
private val checkpointHintDepthByThread = mutableMapOf<Long, Int>()
private val documentsWriterPerThreadFlushHintDepthByThread = mutableMapOf<Long, Int>()
private val indexingChainFlushHintDepthByThread = mutableMapOf<Long, Int>()
private val indexingChainCallHintDepthByThread = mutableMapOf<Long, Int>()
private val readOnlyCloneHintDepthByThread = mutableMapOf<Long, Int>()
private val mockDirectoryWrapperDeleteFileHintDepthByThread = mutableMapOf<Long, Int>()
private val perFieldInvertHintDepthByThread = mutableMapOf<Long, Int>()

actual fun <T> withCheckpointCallPathHint(block: () -> T): T {
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

actual fun <T> withReadOnlyCloneCallPathHint(block: () -> T): T {
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

actual fun <T> withDocumentsWriterPerThreadFlushCallPathHint(block: () -> T): T {
    val threadId = currentThreadId()
    checkpointHintLock.lock()
    try {
        documentsWriterPerThreadFlushHintDepthByThread[threadId] =
            (documentsWriterPerThreadFlushHintDepthByThread[threadId] ?: 0) + 1
    } finally {
        checkpointHintLock.unlock()
    }
    try {
        return block()
    } finally {
        checkpointHintLock.lock()
        try {
            val nextDepth = (documentsWriterPerThreadFlushHintDepthByThread[threadId] ?: 1) - 1
            if (nextDepth <= 0) {
                documentsWriterPerThreadFlushHintDepthByThread.remove(threadId)
            } else {
                documentsWriterPerThreadFlushHintDepthByThread[threadId] = nextDepth
            }
        } finally {
            checkpointHintLock.unlock()
        }
    }
}

actual fun <T> withIndexingChainFlushCallPathHint(block: () -> T): T {
    val threadId = currentThreadId()
    checkpointHintLock.lock()
    try {
        indexingChainFlushHintDepthByThread[threadId] =
            (indexingChainFlushHintDepthByThread[threadId] ?: 0) + 1
    } finally {
        checkpointHintLock.unlock()
    }
    try {
        return block()
    } finally {
        checkpointHintLock.lock()
        try {
            val nextDepth = (indexingChainFlushHintDepthByThread[threadId] ?: 1) - 1
            if (nextDepth <= 0) {
                indexingChainFlushHintDepthByThread.remove(threadId)
            } else {
                indexingChainFlushHintDepthByThread[threadId] = nextDepth
            }
        } finally {
            checkpointHintLock.unlock()
        }
    }
}

actual fun <T> withIndexingChainCallPathHint(block: () -> T): T {
    val threadId = currentThreadId()
    checkpointHintLock.lock()
    try {
        indexingChainCallHintDepthByThread[threadId] = (indexingChainCallHintDepthByThread[threadId] ?: 0) + 1
    } finally {
        checkpointHintLock.unlock()
    }
    try {
        return block()
    } finally {
        checkpointHintLock.lock()
        try {
            val nextDepth = (indexingChainCallHintDepthByThread[threadId] ?: 1) - 1
            if (nextDepth <= 0) {
                indexingChainCallHintDepthByThread.remove(threadId)
            } else {
                indexingChainCallHintDepthByThread[threadId] = nextDepth
            }
        } finally {
            checkpointHintLock.unlock()
        }
    }
}

actual fun <T> withMockDirectoryWrapperDeleteFileCallPathHint(block: () -> T): T {
    val threadId = currentThreadId()
    checkpointHintLock.lock()
    try {
        mockDirectoryWrapperDeleteFileHintDepthByThread[threadId] =
            (mockDirectoryWrapperDeleteFileHintDepthByThread[threadId] ?: 0) + 1
    } finally {
        checkpointHintLock.unlock()
    }
    try {
        return block()
    } finally {
        checkpointHintLock.lock()
        try {
            val nextDepth = (mockDirectoryWrapperDeleteFileHintDepthByThread[threadId] ?: 1) - 1
            if (nextDepth <= 0) {
                mockDirectoryWrapperDeleteFileHintDepthByThread.remove(threadId)
            } else {
                mockDirectoryWrapperDeleteFileHintDepthByThread[threadId] = nextDepth
            }
        } finally {
            checkpointHintLock.unlock()
        }
    }
}

actual fun <T> withPerFieldInvertCallPathHint(block: () -> T): T {
    val threadId = currentThreadId()
    checkpointHintLock.lock()
    try {
        perFieldInvertHintDepthByThread[threadId] = (perFieldInvertHintDepthByThread[threadId] ?: 0) + 1
    } finally {
        checkpointHintLock.unlock()
    }
    try {
        return block()
    } finally {
        checkpointHintLock.lock()
        try {
            val nextDepth = (perFieldInvertHintDepthByThread[threadId] ?: 1) - 1
            if (nextDepth <= 0) {
                perFieldInvertHintDepthByThread.remove(threadId)
            } else {
                perFieldInvertHintDepthByThread[threadId] = nextDepth
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
    val threadId = currentThreadId()
    checkpointHintLock.lock()
    return try {
        when {
            className == CHECKPOINT_CLASS_NAME && methodName == CHECKPOINT_METHOD_NAME ->
                checkpointHintDepthByThread[threadId]?.let { it > 0 } ?: false
            className == DOCUMENTS_WRITER_PER_THREAD_CLASS_NAME &&
                methodName == DOCUMENTS_WRITER_PER_THREAD_FLUSH_METHOD_NAME ->
                documentsWriterPerThreadFlushHintDepthByThread[threadId]?.let { it > 0 } ?: false
            className == INDEXING_CHAIN_CLASS_NAME &&
                methodName == INDEXING_CHAIN_FLUSH_METHOD_NAME ->
                indexingChainFlushHintDepthByThread[threadId]?.let { it > 0 } ?: false
            className == MOCK_DIRECTORY_WRAPPER_CLASS_NAME &&
                methodName == MOCK_DIRECTORY_WRAPPER_DELETE_FILE_METHOD_NAME ->
                mockDirectoryWrapperDeleteFileHintDepthByThread[threadId]?.let { it > 0 } ?: false
            className == PER_FIELD_CLASS_NAME &&
                methodName == PER_FIELD_INVERT_METHOD_NAME ->
                perFieldInvertHintDepthByThread[threadId]?.let { it > 0 } ?: false
            className == PERSISTENT_SNAPSHOT_DELETION_POLICY_CLASS_NAME &&
                methodName == PERSISTENT_SNAPSHOT_DELETION_POLICY_PERSIST_METHOD_NAME ->
                hasCurrentCallPathHint(className, methodName)
            else -> null
        }
    } finally {
        checkpointHintLock.unlock()
    }
}

internal actual fun currentStackTraceHasAnyMethodFastPath(methodNames: Set<String>): Boolean? {
    // Check if any method name matches our indexed hints
    if (methodNames.contains("abort") || methodNames.contains("finishDocument")) {
        val threadId = currentThreadId()
        checkpointHintLock.lock()
        return try {
            indexingChainCallHintDepthByThread[threadId]?.let { it > 0 } ?: false
        } finally {
            checkpointHintLock.unlock()
        }
    }
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
