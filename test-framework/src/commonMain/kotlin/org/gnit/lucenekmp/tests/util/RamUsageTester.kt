package org.gnit.lucenekmp.tests.util

import org.gnit.lucenekmp.util.RamUsageEstimator

object RamUsageTester {
    fun ramUsed(o: Any?): Long {
        return RamUsageEstimator.sizeOfObject(o)
    }
}
