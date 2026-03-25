package org.gnit.lucenekmp.util

import kotlinx.coroutines.CancellationException

/**
 * Thrown by lucene on detecting that Thread.interrupt() had been called. Unlike Java's
 * InterruptedException, this exception is not checked..
 */
class ThreadInterruptedException : RuntimeException {
    constructor(ie: CancellationException) : super(ie)

    constructor(cause: Throwable) : super(cause)
}
