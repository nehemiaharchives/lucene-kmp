package org.gnit.lucenekmp.store

import kotlinx.coroutines.runBlocking
import okio.IOException

/**
 * A [rate limiting][RateLimiter] [IndexOutput]
 *
 * @lucene.internal
 */
class RateLimitedIndexOutput(
    private val rateLimiter: RateLimiter,
    out: IndexOutput
) : FilterIndexOutput("RateLimitedIndexOutput($out)", out.name, out) {

    /** How many bytes we've written since we last called rateLimiter.pause.  */
    private var bytesSinceLastPause: Long = 0

    /**
     * Cached here to not always have to call RateLimiter#getMinPauseCheckBytes() which does volatile
     * read.
     */
    private var currentMinPauseCheckBytes: Long

    init {
        this.currentMinPauseCheckBytes = rateLimiter.minPauseCheckBytes
    }

    @Throws(IOException::class)
    override fun writeByte(b: Byte) {
        bytesSinceLastPause++
        checkRate()
        out.writeByte(b)
    }

    @Throws(IOException::class)
    override fun writeBytes(b: ByteArray, offset: Int, length: Int) {
        bytesSinceLastPause += length.toLong()
        checkRate()
        // The bytes array slice is written without pauses.
        // This can cause instant write rate to breach rate limit if there have
        // been no writes for enough time to keep the average write rate within limit.
        // See https://issues.apache.org/jira/browse/LUCENE-10448
        out.writeBytes(b, offset, length)
    }

    @Throws(IOException::class)
    override fun writeInt(i: Int) {
        bytesSinceLastPause += Int.SIZE_BYTES.toLong()
        checkRate()
        out.writeInt(i)
    }

    @Throws(IOException::class)
    override fun writeShort(i: Short) {
        bytesSinceLastPause += Short.SIZE_BYTES.toLong()
        checkRate()
        out.writeShort(i)
    }

    @Throws(IOException::class)
    override fun writeLong(i: Long) {
        bytesSinceLastPause += Long.SIZE_BYTES.toLong()
        checkRate()
        out.writeLong(i)
    }

    @Throws(IOException::class)
    private fun checkRate() {
        if (bytesSinceLastPause > currentMinPauseCheckBytes) {

            runBlocking {
                rateLimiter.pause(bytesSinceLastPause)
            }

            bytesSinceLastPause = 0
            currentMinPauseCheckBytes = rateLimiter.minPauseCheckBytes
        }
    }
}
