package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.Cloneable
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A convenient class which offers a semi-immutable object wrapper implementation which allows one
 * to set the value of an object exactly once, and retrieve it many times. If [.set]
 * is called more than once, [AlreadySetException] is thrown and the operation will fail.
 *
 * @lucene.experimental
 */
class SetOnce<T : Any> : Cloneable<SetOnce<T>> {
    /** Thrown when [SetOnce.set] is called more than once.  */
    class AlreadySetException : IllegalStateException("The object cannot be set twice!")

    @OptIn(ExperimentalAtomicApi::class)
    private val ref = AtomicReference<T?>(null)

    /**
     * A default constructor which does not set the internal object, and allows setting it by calling
     * [.set].
     */
    constructor()

    /**
     * Creates a new instance with the internal object set to the given object. Note that any calls to
     * [.set] afterwards will result in [AlreadySetException]
     *
     * @throws AlreadySetException if called more than once
     * @see .set
     */
    @OptIn(ExperimentalAtomicApi::class)
    constructor(obj: T) {
        ref.store(obj)
    }

    /** Sets the given object. If the object has already been set, an exception is thrown.  */
    fun set(obj: T) {
        if (!trySet(obj)) {
            throw AlreadySetException()
        }
    }

    /**
     * Sets the given object if none was set before.
     *
     * @return true if object was set successfully, false otherwise
     */
    @OptIn(ExperimentalAtomicApi::class)
    fun trySet(obj: T): Boolean {
        return ref.compareAndSet(null, obj)
    }

    /** Returns the object set by [.set], or null if not set.  */
    @OptIn(ExperimentalAtomicApi::class)
    fun get(): T? = ref.load()

    @OptIn(ExperimentalAtomicApi::class)
    override fun clone(): SetOnce<T> {
        return SetOnce(ref.load()!!)
    }
}
