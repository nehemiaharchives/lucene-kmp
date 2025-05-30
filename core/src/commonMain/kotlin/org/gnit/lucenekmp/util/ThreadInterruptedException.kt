package org.gnit.lucenekmp.util

import kotlinx.coroutines.CancellationException

/**
 * Thrown by lucene on detecting that Thread.interrupt() had been called. Unlike Java's
 * InterruptedException, this exception is not checked..
 */
class ThreadInterruptedException(ie: CancellationException) : RuntimeException(ie)
