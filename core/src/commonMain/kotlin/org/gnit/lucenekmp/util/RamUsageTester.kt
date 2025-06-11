package org.gnit.lucenekmp.util

/** Simplified version of Lucene's RamUsageTester. */
object RamUsageTester {
    fun ramUsed(obj: Any?): Long {
        return RamUsageEstimator.sizeOfObject(obj)
    }
}
