package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.Job

/**
 * A synchronizer that may be exclusively owned by a thread.  This
 * class provides a basis for creating locks and related synchronizers
 * that may entail a notion of ownership.  The
 * `AbstractOwnableSynchronizer` class itself does not manage or
 * use this information. However, subclasses and tools may use
 * appropriately maintained values to help control and monitor access
 * and provide diagnostics.
 *
 * @since 1.6
 * @author Doug Lea
 */
abstract class AbstractOwnableSynchronizer

/**
 * Empty constructor for use by subclasses.
 */
protected constructor() {
    /**
     * The current owner of exclusive mode synchronization.
     */
    private var exclusiveOwnerThread: /*java.lang.Thread*/Job? = null

    /**
     * Sets the thread that currently owns exclusive access.
     * A `null` argument indicates that no thread owns access.
     * This method does not otherwise impose any synchronization or
     * `volatile` field accesses.
     * @param thread the owner thread
     */
    protected fun setExclusiveOwnerThread(thread: /*java.lang.Thread*/ Job?) {
        exclusiveOwnerThread = thread
    }

    /**
     * Returns the thread last set by `setExclusiveOwnerThread`,
     * or `null` if never set.  This method does not otherwise
     * impose any synchronization or `volatile` field accesses.
     * @return the owner thread
     */
    protected fun getExclusiveOwnerThread(): /*java.lang.Thread*/Job? {
        return exclusiveOwnerThread
    }
}
