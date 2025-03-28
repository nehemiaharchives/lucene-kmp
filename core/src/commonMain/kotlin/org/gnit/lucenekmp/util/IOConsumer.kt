package org.gnit.lucenekmp.util

import kotlinx.io.IOException

/** An I/O operation with a single input that can throw an IOException. (Functional interface) */
fun interface IOConsumer<T> {
    /** Performs this operation on the given [input]. */
    @Throws(IOException::class)
    fun accept(input: T)
}