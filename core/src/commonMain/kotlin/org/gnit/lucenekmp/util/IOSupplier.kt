package org.gnit.lucenekmp.util

import kotlinx.io.IOException

/**
 * This is a result supplier that is allowed to throw an IOException.
 *
 * @see java.util.function.Supplier
 *
 * @param <T> the suppliers result type.
</T> */
fun interface IOSupplier<T> {
    /**
     * Gets the result.
     *
     * @return the result
     * @throws IOException if producing the result throws an [IOException]
     */
    @Throws(IOException::class)
    fun get(): T
}
