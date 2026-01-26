package org.gnit.lucenekmp.jdkport

import okio.IOException

/**
 * A channel that can write bytes.
 *
 * Only one write operation upon a writable channel may be in progress at any given time. If one
 * thread initiates a write operation upon a channel then any other thread that attempts to initiate
 * another write operation will block until the first operation is complete. Whether or not other
 * kinds of I/O operations may proceed concurrently with a write operation depends upon the type of
 * the channel.
 *
 * @since 1.4
 */
@Ported(from = "java.nio.channels.WritableByteChannel")
interface WritableByteChannel : Channel {
    /**
     * Writes a sequence of bytes to this channel from the given buffer.
     *
     * An attempt is made to write up to r bytes to the channel, where r is the number of bytes
     * remaining in the buffer, that is, src.remaining(), at the moment this method is invoked.
     *
     * Suppose that a byte sequence of length n is written, where 0 <= n <= r. This byte sequence
     * will be transferred from the buffer starting at index p, where p is the buffer's position at
     * the moment this method is invoked; the index of the last byte written will be p + n - 1. Upon
     * return the buffer's position will be equal to p + n; its limit will not have changed.
     *
     * Unless otherwise specified, a write operation will return only after writing all of the r
     * requested bytes. Some types of channels, depending upon their state, may write only some of
     * the bytes or possibly none at all. A socket channel in non-blocking mode, for example, cannot
     * write any more bytes than are free in the socket's output buffer.
     *
     * This method may be invoked at any time. If another thread has already initiated a write
     * operation upon this channel, however, then an invocation of this method will block until the
     * first operation is complete.
     *
     * @param src The buffer from which bytes are to be retrieved
     *
     * @return The number of bytes written, possibly zero
     *
     * @throws NonWritableChannelException If this channel was not opened for writing
     * @throws ClosedChannelException If this channel is closed
     * @throws AsynchronousCloseException If another thread closes this channel while the write
     * operation is in progress
     * @throws ClosedByInterruptException If another thread interrupts the current thread while the
     * write operation is in progress, thereby closing the channel and setting the current thread's
     * interrupt status
     * @throws IOException If some other I/O error occurs
     */
    @Throws(IOException::class)
    fun write(src: ByteBuffer): Int
}
