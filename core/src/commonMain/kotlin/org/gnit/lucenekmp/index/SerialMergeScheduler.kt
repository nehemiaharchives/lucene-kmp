package org.gnit.lucenekmp.index

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.github.oshai.kotlinlogging.KotlinLogging

/** A [MergeScheduler] that simply does each merge sequentially, using the current thread. */
open class SerialMergeScheduler : MergeScheduler() {

    private val mergeMutex = Mutex()
    private val logger = KotlinLogging.logger {}

    override suspend fun merge(mergeSource: MergeSource, trigger: MergeTrigger) {
        mergeMutex.withLock {
            logger.debug { "SerialMergeScheduler: merge start trigger=$trigger" }
            while (true) {
                val merge = mergeSource.nextMerge ?: break
                logger.debug { "SerialMergeScheduler: merge next ${merge.segString()} maxNumSegments=${merge.maxNumSegments}" }
                mergeSource.merge(merge)
            }
            logger.debug { "SerialMergeScheduler: merge end trigger=$trigger" }
        }
    }

    override fun close() {
        // no-op
    }
}
