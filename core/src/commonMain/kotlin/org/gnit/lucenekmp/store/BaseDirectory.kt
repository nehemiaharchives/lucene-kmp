package org.gnit.lucenekmp.store

import okio.IOException
import kotlin.concurrent.Volatile

/**
 * Base implementation for a concrete [Directory] that uses a [LockFactory] for locking.
 *
 * @lucene.experimental
 */
abstract class BaseDirectory protected constructor(lockFactory: LockFactory) : Directory() {
    @Volatile
    protected var isOpen: Boolean = true

    /** Holds the LockFactory instance (implements locking for this Directory instance).  */
    protected val lockFactory: LockFactory

    /** Sole constructor.  */
    init {
        if (lockFactory == null) {
            throw NullPointerException("LockFactory must not be null, use an explicit instance!")
        }
        this.lockFactory = lockFactory
    }

    @Throws(IOException::class)
    override fun obtainLock(name: String): Lock {
        return lockFactory.obtainLock(this, name)
    }

    @Throws(AlreadyClosedException::class)
    override fun ensureOpen() {
        if (!isOpen) {
            throw AlreadyClosedException("this Directory is closed")
        }
    }

    override fun toString(): String {
        return super.toString() + " lockFactory=" + lockFactory
    }
}
