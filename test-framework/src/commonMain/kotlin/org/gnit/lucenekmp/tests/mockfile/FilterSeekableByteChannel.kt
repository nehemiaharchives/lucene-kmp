package org.gnit.lucenekmp.tests.mockfile

import okio.IOException
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.SeekableByteChannel

/**
 * A `FilterSeekableByteChannel` contains another `SeekableByteChannel`, which it uses
 * as its basic source of data, possibly transforming the data along the way or providing additional
 * functionality.
 */
open class FilterSeekableByteChannel(
    /** The underlying `SeekableByteChannel` instance. */
    protected val delegate: SeekableByteChannel
) : SeekableByteChannel {

    override fun isOpen(): Boolean {
        return delegate.isOpen()
    }

    @Throws(IOException::class)
    override fun close() {
        delegate.close()
    }

    @Throws(IOException::class)
    override fun read(dst: ByteBuffer): Int {
        return delegate.read(dst)
    }

    @Throws(IOException::class)
    override fun write(src: ByteBuffer): Int {
        return delegate.write(src)
    }

    @Throws(IOException::class)
    override fun position(): Long {
        return delegate.position()
    }

    @Throws(IOException::class)
    override fun position(newPosition: Long): SeekableByteChannel {
        delegate.position(newPosition)
        return this
    }

    @Throws(IOException::class)
    override fun size(): Long {
        return delegate.size()
    }

    @Throws(IOException::class)
    override fun truncate(size: Long): SeekableByteChannel {
        delegate.truncate(size)
        return this
    }
}
