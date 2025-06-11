package org.gnit.lucenekmp.tests.util

import org.gnit.lucenekmp.util.RamUsageEstimator

/**
 * Simplified RAM usage tester that delegates to [RamUsageEstimator].
 */
class RamUsageTester {
    companion object {
        /**
         * Estimate the RAM usage by the given object.
         */
        fun ramUsed(obj: Any?): Long {
            return RamUsageEstimator.sizeOfObject(obj)
        }
    }
}
