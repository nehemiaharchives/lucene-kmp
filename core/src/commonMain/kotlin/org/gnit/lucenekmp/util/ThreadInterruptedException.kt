package org.gnit.lucenekmp.util

/**
 * Thrown by lucene on detecting that Thread.interrupt() had been called. Unlike Java's
 * InterruptedException, this exception is not checked..
 */
class ThreadInterruptedException(ie: InterruptedException) : RuntimeException(ie)
