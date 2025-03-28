package org.gnit.lucenekmp.jdkport

/**
 * port of java.lang.ref.WeakReference
 */
open class WeakReference<T> : Reference<T?> {
    /**
     * Creates a new weak reference that refers to the given object.  The new
     * reference is not registered with any queue.
     *
     * @param referent object the new weak reference will refer to
     */
    constructor(referent: T?) : super(referent)

    /**
     * Creates a new weak reference that refers to the given object and is
     * registered with the given queue.
     *
     * @param referent object the new weak reference will refer to
     * @param q the queue with which the reference is to be registered,
     * or `null` if registration is not required
     */
    constructor(referent: T?, q: ReferenceQueue<T?>?) : super(referent, q)
}
