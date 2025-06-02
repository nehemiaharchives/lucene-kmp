package org.gnit.lucenekmp.util

import okio.IOException
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.get
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * Manages reference counting for a given object. Extensions can override [.release] to do
 * custom logic when reference counting hits 0.
 */
open class RefCount<T>(protected val `object`: T?) {
    @OptIn(ExperimentalAtomicApi::class)
    private val refCount: AtomicInteger = AtomicInteger(1)

    /**
     * Called when reference counting hits 0. By default this method does nothing, but extensions can
     * override to e.g. release resources attached to object that is managed by this class.
     */
    @Throws(IOException::class)
    protected open fun release() {
    }

    /**
     * Decrements the reference counting of this object. When reference counting hits 0, calls [ ][.release].
     */
    @OptIn(ExperimentalAtomicApi::class)
    @Throws(IOException::class)
    fun decRef() {
        val rc: Int = refCount.decrementAndFetch()
        if (rc == 0) {
            var success = false
            try {
                release()
                success = true
            } finally {
                if (!success) {
                    // Put reference back on failure
                    refCount.incrementAndFetch()
                }
            }
        } else check(rc >= 0) { "too many decRef calls: refCount is $rc after decrement" }
    }

    fun get(): T? {
        return `object`
    }

    /** Returns the current reference count.  */
    @OptIn(ExperimentalAtomicApi::class)
    fun getRefCount(): Int {
        return refCount.get()
    }

    /**
     * Increments the reference count. Calls to this method must be matched with calls to [ ][.decRef].
     */
    @OptIn(ExperimentalAtomicApi::class)
    fun incRef() {
        refCount.incrementAndFetch()
    }
}
