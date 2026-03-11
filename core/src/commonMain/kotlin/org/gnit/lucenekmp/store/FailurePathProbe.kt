package org.gnit.lucenekmp.store

/**
 * Probe interface for tests that need to identify precise directory failure paths without relying on
 * stack-trace inspection, which is very expensive on Kotlin/Native.
 */
interface FailurePathProbe {
    var commitStage: String?
    var rollbackStage: String?
    var mergeStage: String?
    var deleteStage: String?
    var flushStage: String?
    var termVectorsStage: String?
    var isInTermVectorsFinishDocument: Boolean
    var isDeletingFile: Boolean
    var isSyncingMetaData: Boolean
    var isWritingGlobalFieldMap: Boolean

    companion object {
        fun find(dir: Directory): FailurePathProbe? {
            var current: Directory = dir
            while (true) {
                if (current is FailurePathProbe) {
                    return current
                }
                if (current is FilterDirectory) {
                    current = current.getDelegate()
                    continue
                }
                return null
            }
        }
    }
}
