package org.gnit.lucenekmp.jdkport


/**
 * A simple mimic of java.lang.ref.Reference.
 *
 * This version only stores a reference and an associated queue, and provides basic
 * operations like [get], [clear], and [enqueue]. It ignores the underlying GC, native methods,
 * and thread-handling logic.
 */
open class Reference<T> protected constructor(
    protected var referent: T?,
    open var queue: ReferenceQueue<T>? = (ReferenceQueue.NULL as ReferenceQueue<T>?)
) {

    constructor(referent: T) : this(referent, null)

    /**
     * Returns the referent or null if it has been cleared.
     */
    open fun get(): T? = referent

    /**
     * Checks whether the referent equals the given [obj].
     */
    open fun refersTo(obj: T): Boolean = referent == obj

    /**
     * Clears this reference. The default implementation simply clears the referent.
     */
    open fun clear() {
        clear0()
    }

    // Internal clear operation.
    private fun clear0() {
        referent = null
    }

    /**
     * Clears this reference and enqueues it in its associated [ReferenceQueue], if any.
     *
     * In this mimic, enqueueing does not actually add it to any queue.
     */
    open fun enqueue(): Boolean {
        clear0() // mimic clearing behavior before enqueueing
        return queue?.enqueue(this) ?: false
    }

    companion object {
        /**
         * A no-op mimic of the reachability fence.
         *
         * In Java this method prevents the object from being GC'd too early.
         * In this mimic, it does nothing.
         */
        inline fun reachabilityFence(ref: Any) { /* no-op */ }
    }
}
