package org.gnit.lucenekmp.store

import okio.IOException
import kotlin.math.min


/**
 * Extension of IndexInput, computing checksum as it goes. Callers can retrieve the checksum via
 * [.getChecksum].
 */
abstract class ChecksumIndexInput
/**
 * resourceDescription should be a non-null, opaque string describing this resource; it's returned
 * from [.toString].
 */
protected constructor(resourceDescription: String) : IndexInput(resourceDescription) {
    /* This buffer is used when skipping bytes in skipBytes(). Skipping bytes
       * still requires reading in the bytes we skip in order to update the checksum.
       * The reason we need to use an instance member instead of sharing a single
       * static instance across threads is that multiple instances invoking skipBytes()
       * concurrently on different threads can clobber the contents of a shared buffer,
       * corrupting the checksum. See LUCENE-5583 for additional context.
       */
    private var skipBuffer: ByteArray?
        get() {
            return skipBuffer
        }
        set(value) {
            skipBuffer = value
        }

    abstract val checksum: Long

    /**
     * {@inheritDoc}
     *
     *
     * [ChecksumIndexInput] can only seek forward and seeks are expensive since they imply to
     * read bytes in-between the current position and the target position in order to update the
     * checksum.
     */
    @Throws(IOException::class)
    override fun seek(pos: Long) {
        val curFP: Long = filePointer
        val skip = pos - curFP
        check(skip >= 0) { this::class.qualifiedName + " cannot seek backwards (pos=" + pos + " getFilePointer()=" + curFP + ")" }
        skipByReading(skip)
    }

    /**
     * Skip over `numBytes` bytes. The contract on this method is that it should have the
     * same behavior as reading the same number of bytes into a buffer and discarding its content.
     * Negative values of `numBytes` are not supported.
     */
    @Throws(IOException::class)
    private fun skipByReading(numBytes: Long) {
        if (skipBuffer == null) {
            skipBuffer = ByteArray(SKIP_BUFFER_SIZE)
        }
        require(skipBuffer!!.size == SKIP_BUFFER_SIZE)
        var skipped: Long = 0
        while (skipped < numBytes) {
            val step = min(SKIP_BUFFER_SIZE.toLong(), numBytes - skipped).toInt()
            readBytes(skipBuffer!!, 0, step, false)
            skipped += step.toLong()
        }
    }

    companion object {
        private const val SKIP_BUFFER_SIZE = 1024
    }
}
