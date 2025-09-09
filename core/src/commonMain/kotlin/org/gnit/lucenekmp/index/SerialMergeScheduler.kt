package org.gnit.lucenekmp.index

import okio.IOException

/** A [MergeScheduler] that simply does each merge sequentially, using the current thread. */
class SerialMergeScheduler : MergeScheduler() {

    /**
     * Just do the merges in sequence. We do this "synchronized" so that even if the application is
     * using multiple threads, only one merge may run at a time.
     */
    @Throws(IOException::class)
    override suspend fun merge(mergeSource: MergeSource, trigger: MergeTrigger) {
        synchronized(this) {
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

