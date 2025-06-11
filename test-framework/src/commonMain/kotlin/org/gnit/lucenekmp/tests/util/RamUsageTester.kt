package org.gnit.lucenekmp.tests.util

import org.gnit.lucenekmp.util.RamUsageEstimator

/**
 * Utility to estimate RAM usage of objects for testing.
 * This is a simplified version of Lucene's RamUsageTester.
 */
object RamUsageTester {
    /**
     * Estimate the memory usage of the given object by delegating to
     * [RamUsageEstimator.sizeOfObject].
     */
    fun ramUsed(obj: Any?): Long {
        return RamUsageEstimator.sizeOfObject(obj)
    }
}
