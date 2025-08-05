package org.gnit.lucenekmp.internal.tests

import org.gnit.lucenekmp.index.ConcurrentMergeScheduler

/**
 * Access to [org.apache.lucene.index.ConcurrentMergeScheduler] internals exposed to the test
 * framework.
 *
 * @lucene.internal
 */
fun interface ConcurrentMergeSchedulerAccess {
    fun setSuppressExceptions(cms: ConcurrentMergeScheduler)
}
