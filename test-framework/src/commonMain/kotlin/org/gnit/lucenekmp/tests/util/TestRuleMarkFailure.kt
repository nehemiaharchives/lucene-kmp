package org.gnit.lucenekmp.tests.util

import kotlin.concurrent.Volatile


// TODO in java lucene this class is deeply integrate with junit class TestRule which is not translatable into kmp, so for now we leave it no-op
class TestRuleMarkFailure {

    private val chained: Array<TestRuleMarkFailure> = arrayOf()

    @Volatile
    private var failures = false

    /** Taints this object and any chained as having failures.  */
    fun markFailed() {
        failures = true
        for (next in chained) {
            next.markFailed()
        }
    }

    /** Check if this object had any marked failures.  */
    fun hadFailures(): Boolean {
        return failures
    }

    /** Check if this object was successful (the opposite of {@link #hadFailures()}). */
    fun wasSuccessful(): Boolean {
        return !hadFailures()
    }
}