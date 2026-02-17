package org.gnit.lucenekmp.tests.index

import org.gnit.lucenekmp.index.ConcurrentMergeScheduler

/** A [ConcurrentMergeScheduler] that ignores allowed exceptions. */
abstract class SuppressingConcurrentMergeScheduler : ConcurrentMergeScheduler() {
    override fun handleMergeException(exc: Throwable) {
        var current: Throwable? = exc
        while (true) {
            if (current != null && isOK(current)) {
                return
            }
            current = current?.cause
            if (current == null) {
                super.handleMergeException(exc)
                return
            }
        }
    }

    protected abstract fun isOK(t: Throwable): Boolean
}
