package org.gnit.lucenekmp.tests.mockfile

import okio.IOException
import org.gnit.lucenekmp.jdkport.InputStream

/**
 * A `FilterInputStream2` contains another `InputStream`, which it uses as its basic
 * source of data, possibly transforming the data along the way or providing additional
 * functionality.
 *
 *
 * Note: unlike [FilterInputStream] this class delegates every method by default. This
 * means to transform `read` calls, you need to override multiple methods. On the other hand,
 * it is less trappy: a simple implementation that just overrides `close` will not force bytes
 * to be read one-at-a-time.
 */
class FilterInputStream2(
    /** The underlying `InputStream` instance.  */
    protected val delegate: InputStream
) : InputStream() {

    @Throws(IOException::class)
    override fun read(): Int {
        return delegate.read()
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray): Int {
        return delegate.read(b)
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        return delegate.read(b, off, len)
    }

    @Throws(IOException::class)
    override fun skip(n: Long): Long {
        return delegate.skip(n)
    }

    @Throws(IOException::class)
    override fun available(): Int {
        return delegate.available()
    }

    override fun close() {
        delegate.close()
    }

    /*@Synchronized*/
    override fun mark(readlimit: Int) {
        delegate.mark(readlimit)
    }

    /*@Synchronized*/
    @Throws(IOException::class)
    override fun reset() {
        delegate.reset()
    }

    override fun markSupported(): Boolean {
        return delegate.markSupported()
    }
}
