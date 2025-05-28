package org.gnit.lucenekmp.util.bkd


import okio.IOException
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.jdkport.reverseBytes
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.BytesRef

/**
 * Writes points to disk in a fixed-with format.
 *
 * @lucene.internal
 */
open class OfflinePointWriter(
    val config: BKDConfig,
    val tempDir: Directory,
    tempFileNamePrefix: String,
    desc: String,
    val expectedCount: Long
) : PointWriter {
    val out: IndexOutput = tempDir.createTempOutput(tempFileNamePrefix, "bkd_$desc", IOContext.DEFAULT)
    val name: String = out.name
    var count: Long = 0
    private var closed = false

    @Throws(IOException::class)
    override fun append(packedValue: ByteArray, docID: Int) {
        require(closed == false) { "Point writer is already closed" }
        require(
            packedValue.size == config.packedBytesLength()
        ) {
            ("[packedValue] must have length ["
                    + config.packedBytesLength()
                    + "] but was ["
                    + packedValue.size
                    + "]")
        }

        out.writeBytes(packedValue, 0, packedValue.size)
        // write bytes in big-endian order for comparing in lexicographically order
        out.writeInt(Int.reverseBytes(docID))
        count++
        require(
            expectedCount == 0L || count <= expectedCount
        ) { "expectedCount=$expectedCount vs count=$count" }
    }

    @Throws(IOException::class)
    override fun append(pointValue: PointValue) {
        require(closed == false) { "Point writer is already closed" }
        val packedValueDocID: BytesRef = pointValue.packedValueDocIDBytes()
        require(
            packedValueDocID.length == config.bytesPerDoc()
        ) {
            ("[packedValue and docID] must have length ["
                    + (config.bytesPerDoc())
                    + "] but was ["
                    + packedValueDocID.length
                    + "]")
        }
        out.writeBytes(packedValueDocID.bytes, packedValueDocID.offset, packedValueDocID.length)
        count++
        require(
            expectedCount == 0L || count <= expectedCount
        ) { "expectedCount=$expectedCount vs count=$count" }
    }

    @Throws(IOException::class)
    override fun getReader(start: Long, length: Long): PointReader {
        val buffer = ByteArray(config.bytesPerDoc())
        return getReader(start, length, buffer)
    }

    @Throws(IOException::class)
    fun getReader(start: Long, length: Long, reusableBuffer: ByteArray): OfflinePointReader {
        require(closed) { "point writer is still open and trying to get a reader" }
        require(start + length <= count) { "start=$start length=$length count=$count" }
        require(expectedCount == 0L || count == expectedCount)
        return OfflinePointReader(config, tempDir, name, start, length, reusableBuffer)
    }

    override fun count(): Long {
        return count
    }

    @Throws(IOException::class)
    override fun close() {
        if (closed == false) {
            try {
                CodecUtil.writeFooter(out)
            } finally {
                out.close()
                closed = true
            }
        }
    }

    @Throws(IOException::class)
    override fun destroy() {
        tempDir.deleteFile(name)
    }

    override fun toString(): String {
        return "OfflinePointWriter(count=$count tempFileName=$name)"
    }
}
