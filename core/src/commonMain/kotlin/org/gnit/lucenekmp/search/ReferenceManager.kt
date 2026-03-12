package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.store.AlreadyClosedException
import kotlin.concurrent.Volatile

/**
 * Utility class to safely share instances of a certain type across multiple threads, while
 * periodically refreshing them. This class ensures each reference is closed only once all threads
 * have finished using it. It is recommended to consult the documentation of [ReferenceManager]
 * implementations for their [maybeRefresh] semantics.
 *
 * @param G the concrete type that will be [acquire] acquired and [release]
 * released.
 * @lucene.experimental
 */
abstract class ReferenceManager<G> : AutoCloseable {
    @Volatile
    protected var current: G? = null

    private val refreshLock = ReentrantLock()
    private val swapLock = ReentrantLock()

    private val refreshListeners = mutableListOf<RefreshListener>()

    private fun ensureOpen() {
        if (current == null) {
            throw AlreadyClosedException(REFERENCE_MANAGER_IS_CLOSED_MSG)
        }
    }

    @Throws(IOException::class)
    private fun swapReference(newReference: G?) {
        swapLock.lock()
        try {
            ensureOpen()
            val oldReference = current
            current = newReference
            if (oldReference != null) {
                release(oldReference)
            }
        } finally {
            swapLock.unlock()
        }
    }

    /**
     * Decrement reference counting on the given reference.
     *
     * @throws IOException if reference decrement on the given resource failed.
     */
    @Throws(IOException::class)
    protected abstract fun decRef(reference: G)

    /**
     * Refresh the given reference if needed. Returns `null` if no refresh was needed, otherwise
     * a new refreshed reference.
     *
     * @throws AlreadyClosedException if the reference manager has been [close] closed.
     * @throws IOException if the refresh operation failed
     */
    @Throws(IOException::class)
    protected abstract fun refreshIfNeeded(referenceToRefresh: G): G?

    /**
     * Try to increment reference counting on the given reference. Return true if the operation was
     * successful.
     *
     * @throws AlreadyClosedException if the reference manager has been [close] closed.
     */
    @Throws(IOException::class)
    protected abstract fun tryIncRef(reference: G): Boolean

    /**
     * Obtain the current reference. You must match every call to acquire with one call to
     * [release]; it's best to do so in a finally clause, and set the reference to `null` to
     * prevent accidental usage after it has been released.
     *
     * @throws AlreadyClosedException if the reference manager has been [close] closed.
     */
    @Throws(IOException::class)
    fun acquire(): G {
        while (true) {
            val ref = current ?: throw AlreadyClosedException(REFERENCE_MANAGER_IS_CLOSED_MSG)
            if (tryIncRef(ref)) {
                return ref
            }
            if (getRefCount(ref) == 0 && current === ref) {
                throw IllegalStateException(
                    "The managed reference has already closed - this is likely a bug when the reference count is modified outside of the ReferenceManager"
                )
            }
        }
    }

    /**
     * Closes this ReferenceManager to prevent future [acquire] acquiring. A reference
     * manager should be closed if the reference to the managed resource should be disposed or the
     * application using the [ReferenceManager] is shutting down. The managed resource might not
     * be released immediately, if the [ReferenceManager] user is holding on to a previously
     * [acquire] acquired reference. The resource will be released once when the last
     * reference is [release] released. Those references can still be used as if the
     * manager was still active.
     *
     * Applications should not [acquire] acquire new references from this manager once
     * this method has been called. [acquire] Acquiring a resource on a closed [ReferenceManager]
     * will throw an [AlreadyClosedException].
     *
     * @throws IOException if the underlying reader of the current reference could not be closed
     */
    override fun close() {
        swapLock.lock()
        try {
            if (current != null) {
                // make sure we can call this more than once
                // closeable javadoc says:
                // if this is already closed then invoking this method has no effect.
                swapReference(null)
                afterClose()
            }
        } finally {
            swapLock.unlock()
        }
    }

    /** Returns the current reference count of the given reference. */
    protected abstract fun getRefCount(reference: G): Int

    /**
     * Called after close(), so subclass can free any resources.
     *
     * @throws IOException if the after close operation in a sub-class throws an [IOException]
     */
    @Throws(IOException::class)
    protected open fun afterClose() {}

    @Throws(IOException::class)
    private fun doMaybeRefresh() {
        // it's ok to call lock() here (blocking) because we're supposed to get here
        // from either maybeRefresh() or maybeRefreshBlocking(), after the lock has
        // already been obtained. Doing that protects us from an accidental bug
        // where this method will be called outside the scope of refreshLock.
        // Per ReentrantLock's javadoc, calling lock() by the same thread more than
        // once is ok, as long as unlock() is called a matching number of times.
        refreshLock.lock()
        var refreshed = false
        try {
            val reference = acquire()
            try {
                notifyRefreshListenersBefore()
                val newReference = refreshIfNeeded(reference)
                if (newReference != null) {
                    check(newReference !== reference) {
                        "refreshIfNeeded should return null if refresh wasn't needed"
                    }
                    try {
                        swapReference(newReference)
                        refreshed = true
                    } finally {
                        if (!refreshed) {
                            release(newReference)
                        }
                    }
                }
            } finally {
                release(reference)
                notifyRefreshListenersRefreshed(refreshed)
            }
            afterMaybeRefresh()
        } finally {
            refreshLock.unlock()
        }
    }

    /**
     * You must call this (or [maybeRefreshBlocking]), periodically, if you want that [ ][acquire] will return refreshed instances.
     *
     * <b>Threads</b>: it's fine for more than one thread to call this at once. Only the first
     * thread will attempt the refresh; subsequent threads will see that another thread is already
     * handling refresh and will return immediately. Note that this means if another thread is
     * already refreshing then subsequent threads will return right away without waiting for the
     * refresh to complete.
     *
     * If this method returns true it means the calling thread either refreshed or that there were
     * no changes to refresh. If it returns false it means another thread is currently refreshing.
     *
     * @throws IOException if refreshing the resource causes an [IOException]
     * @throws AlreadyClosedException if the reference manager has been [close] closed.
     */
    @Throws(IOException::class)
    fun maybeRefresh(): Boolean {
        ensureOpen()

        // Ensure only 1 thread does refresh at once; other threads just return immediately:
        val doTryRefresh = refreshLock.tryLock()
        if (doTryRefresh) {
            try {
                doMaybeRefresh()
            } finally {
                refreshLock.unlock()
            }
        }

        return doTryRefresh
    }

    /**
     * You must call this (or [maybeRefresh]), periodically, if you want that [ ][acquire] will return refreshed instances.
     *
     * <b>Threads</b>: unlike [maybeRefresh], if another thread is currently refreshing,
     * this method blocks until that thread completes. It is useful if you want to guarantee that the
     * next call to [acquire] will return a refreshed instance. Otherwise, consider using the
     * non-blocking [maybeRefresh].
     *
     * @throws IOException if refreshing the resource causes an [IOException]
     * @throws AlreadyClosedException if the reference manager has been [close] closed.
     */
    @Throws(IOException::class)
    fun maybeRefreshBlocking() {
        ensureOpen()

        // Ensure only 1 thread does refresh at once
        refreshLock.lock()
        try {
            doMaybeRefresh()
        } finally {
            refreshLock.unlock()
        }
    }

    /**
     * Called after a refresh was attempted, regardless of whether a new reference was in fact
     * created.
     *
     * @throws IOException if a low level I/O exception occurs
     */
    @Throws(IOException::class)
    protected open fun afterMaybeRefresh() {}

    /**
     * Release the reference previously obtained via [acquire].
     *
     * <b>NOTE:</b> it's safe to call this after [close].
     *
     * @throws IOException if the release operation on the given resource throws an [IOException]
     */
    @Throws(IOException::class)
    fun release(reference: G) {
        decRef(reference)
    }

    @Throws(IOException::class)
    private fun notifyRefreshListenersBefore() {
        for (refreshListener in refreshListeners) {
            refreshListener.beforeRefresh()
        }
    }

    @Throws(IOException::class)
    private fun notifyRefreshListenersRefreshed(didRefresh: Boolean) {
        for (refreshListener in refreshListeners) {
            refreshListener.afterRefresh(didRefresh)
        }
    }

    /** Adds a listener, to be notified when a reference is refreshed/swapped. */
    fun addListener(listener: RefreshListener) {
        refreshListeners.add(listener)
    }

    /** Removes a listener added by [addListener]. */
    fun removeListener(listener: RefreshListener) {
        refreshListeners.remove(listener)
    }

    interface RefreshListener {
        @Throws(IOException::class)
        fun beforeRefresh()

        @Throws(IOException::class)
        fun afterRefresh(didRefresh: Boolean)
    }

    companion object {
        private const val REFERENCE_MANAGER_IS_CLOSED_MSG = "this ReferenceManager is closed"
    }
}
