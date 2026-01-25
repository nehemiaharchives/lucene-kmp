package org.gnit.lucenekmp.tests.mockfile

import okio.IOException
import org.gnit.lucenekmp.jdkport.OutputStream

/**
 * A `FilterOutputStream2` contains another `OutputStream`, which it uses as its basic
 * source of data, possibly transforming the data along the way or providing additional
 * functionality.
 *
 *
 * Note: unlike [FilterOutputStream] this class delegates every method by default. This
 * means to transform `write` calls, you need to override multiple methods. On the other hand,
 * it is less trappy: a simple implementation that just overrides `close` will not force bytes
 * to be written one-at-a-time.
 */
abstract class FilterOutputStream2(
    /** The underlying `OutputStream` instance.  */
    protected val delegate: OutputStream
) : OutputStream() {

    @Throws(IOException::class)
    override fun write(b: ByteArray) {
        delegate.write(b)
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        delegate.write(b, off, len)
    }

    @Throws(IOException::class)
    override fun flush() {
        delegate.flush()
    }

    override fun close() {
        delegate.close()
    }

    @Throws(IOException::class)
    override fun write(b: Int) {
        delegate.write(b)
    }
}
