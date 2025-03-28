package org.gnit.lucenekmp.jdkport


/**
 * A simple mimic of Java's java.lang.ref.ReferenceQueue.
 *
 * This implementation is a minimal, non‑blocking version that only mimics the API.
 * It stores enqueued references in an internal list and ignores the concurrency,
 * waiting, and GC‑integration that the real Java version provides.
 */
open class ReferenceQueue<T> {

    // Internal queue for storing enqueued references.
    private val queue = mutableListOf<Reference<T>>()

    /**
     * Enqueues the given reference.
     *
     * In this mimic, the reference is simply added to an internal list.
     *
     * @return true if the reference was added; false otherwise.
     */
    open fun enqueue(ref: Reference<T>): Boolean {
        queue.add(ref)
        return true
    }

    /**
     * Polls the queue to see if a reference object is available.
     * If one is available, it is removed from the queue and returned.
     * Otherwise, returns null.
     */
    open fun poll(): Reference<T>? {
        return if (queue.isNotEmpty()) queue.removeAt(0) else null
    }

    /**
     * Removes the next reference object from this queue, blocking until one becomes available
     * or the specified timeout (in milliseconds) expires.
     *
     * In this mimic, blocking is not supported. The timeout value is ignored, and the method
     * simply returns whatever [poll] returns.
     *
     * @param timeout the maximum time to wait in milliseconds (ignored)
     * @return a reference object if available, otherwise null.
     */
    open fun remove(timeout: Long): Reference<T>? {
        return poll()
    }

    /**
     * Removes the next reference object from this queue, blocking indefinitely until one becomes available.
     *
     * In this mimic, blocking is not supported; it simply returns [poll].
     *
     * @return a reference object if available, otherwise null.
     */
    open fun remove(): Reference<T>? = poll()

    /**
     * Iterates over the queue and invokes the given [action] on each reference.
     *
     * This is intended for diagnostic purposes.
     */
    open fun forEach(action: (Reference<T>) -> Unit) {
        queue.forEach(action)
    }

    companion object {
        /**
         * A dummy ReferenceQueue representing a null queue.
         */
        val NULL: ReferenceQueue<Any?> = object : ReferenceQueue<Any?>() {
            override fun enqueue(ref: Reference<Any?>): Boolean = false
        }

        /**
         * A dummy ReferenceQueue representing an enqueued state.
         */
        val ENQUEUED: ReferenceQueue<Any?> = NULL
    }
}