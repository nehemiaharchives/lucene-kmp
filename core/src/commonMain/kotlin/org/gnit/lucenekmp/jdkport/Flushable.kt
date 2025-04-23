package org.gnit.lucenekmp.jdkport

import kotlinx.io.IOException

/**
 * port of java.io.Flushable
 *
 * A `Flushable` is a destination of data that can be flushed.  The
 * flush method is invoked to write any buffered output to the underlying
 * stream.
 *
 * @since 1.5
 */
interface Flushable {
    /**
     * Flushes this stream by writing any buffered output to the underlying
     * stream.
     *
     * @throws IOException If an I/O error occurs
     */
    @Throws(IOException::class)
    fun flush()
}
