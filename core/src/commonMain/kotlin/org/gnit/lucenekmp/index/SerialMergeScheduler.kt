package org.gnit.lucenekmp.index

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A [MergeScheduler] that simply does each merge sequentially, using the current thread. */
class SerialMergeScheduler : MergeScheduler() {

    private val mergeMutex = Mutex()

    override suspend fun merge(mergeSource: MergeSource, trigger: MergeTrigger) {
        mergeMutex.withLock {
            while (true) {
                val merge = mergeSource.nextMerge ?: break
                mergeSource.merge(merge)
            }
        }
    }

    override fun close() {
        // no-op
    }
}

