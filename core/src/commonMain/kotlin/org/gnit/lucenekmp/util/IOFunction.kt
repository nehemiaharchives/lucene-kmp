package org.gnit.lucenekmp.util

import kotlinx.io.IOException

/** A Function that may throw an IOException (functional interface). */
fun interface IOFunction<T, R> {
    /** Applies this function to the given argument [t]. */
    @Throws(IOException::class)
    fun apply(t: T): R
}
