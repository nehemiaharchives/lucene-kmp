package org.gnit.lucenekmp.util

import kotlinx.io.IOException

/**
 * Boolean supplier that is allowed to throw an IOException.
 *
 * @see java.util.function.BooleanSupplier
 */
fun interface IOBooleanSupplier {
    /**
     * Gets the boolean result.
     *
     * @return the result
     * @throws IOException if supplying the result throws an [IOException]
     */
    @Throws(IOException::class)
    fun get(): Boolean
}
