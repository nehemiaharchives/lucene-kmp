package org.gnit.lucenekmp.util


/**
 * An object with this interface is a wrapper around another object (e.g., a filter with a
 * delegate). The method [.unwrap] can be called to get the wrapped object
 *
 * @lucene.internal
 */
interface Unwrappable<T> {
    /** Unwraps this instance  */
    fun unwrap(): T

    companion object {
        /** Unwraps all `Unwrappable`s around the given object.  */
        fun <T> unwrapAll(o: T): T {
            var o = o
            while (o is Unwrappable<*>) {
                o = (o as Unwrappable<T>).unwrap()
            }
            return o
        }
    }
}
