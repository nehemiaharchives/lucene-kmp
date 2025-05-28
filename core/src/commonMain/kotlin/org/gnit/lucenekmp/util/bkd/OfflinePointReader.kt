package org.gnit.lucenekmp.util.bkd

import okio.EOFException
import okio.IOException
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.util.BitUtil
import org.gnit.lucenekmp.util.BytesRef

/**
 * Reads points from disk in a fixed-with format, previously written with [ ].
 *
 * @lucene.internal
 */
class OfflinePointReader(
    private val config: BKDConfig,
    tempDir: Directory,
    tempFileName: String,
    start: Long,
    length: Long,
    reusableBuffer: ByteArray
) : PointReader {
    var countLeft: Long
    var `in`: IndexInput? = null
    var onHeapBuffer: ByteArray
    var offset: Int = 0
    private var checked = false
    private var pointsInBuffer = 0
    private val maxPointOnHeap: Int

    // File name we are reading
    val name: String
    private val pointValue: OfflinePointValue

    init {
        require(
            (start + length) * config.bytesPerDoc() + CodecUtil.footerLength() <= tempDir.fileLength(tempFileName)
        ) {
            ("requested slice is beyond the length of this file: start="
                    + start
                    + " length="
                    + length
                    + " bytesPerDoc="
                    + config.bytesPerDoc()
                    + " fileLength="
                    + tempDir.fileLength(tempFileName)
                    + " tempFileName="
                    + tempFileName)
        }
        requireNotNull(reusableBuffer) { "[reusableBuffer] cannot be null" }
        require(reusableBuffer.size >= config.bytesPerDoc()) { "Length of [reusableBuffer] must be bigger than " + config.bytesPerDoc() }

        this.maxPointOnHeap = reusableBuffer.size / config.bytesPerDoc()
        // Best-effort checksumming:
        `in` = if (start == 0L
            && (length * config.bytesPerDoc()
                    == tempDir.fileLength(tempFileName) - CodecUtil.footerLength())
        ) {
            // If we are going to read the entire file, e.g. because BKDWriter is now
            // partitioning it, we open with checksums:
            tempDir.openChecksumInput(tempFileName)
        } else {
            // Since we are going to seek somewhere in the middle of a possibly huge
            // file, and not read all bytes from there, don't use ChecksumIndexInput here.
            // This is typically fine, because this same file will later be read fully,
            // at another level of the BKDWriter recursion
            tempDir.openInput(tempFileName, IOContext.READONCE)
        }

        name = tempFileName

        val seekFP = start * config.bytesPerDoc()
        `in`?.seek(seekFP)
        countLeft = length
        this.onHeapBuffer = reusableBuffer
        this.pointValue = OfflinePointValue(config, onHeapBuffer)
    }

    @Throws(IOException::class)
    override fun next(): Boolean {
        if (this.pointsInBuffer == 0) {
            if (countLeft >= 0) {
                if (countLeft == 0L) {
                    return false
                }
            }
            try {
                if (countLeft > maxPointOnHeap) {
                    `in`?.readBytes(onHeapBuffer, 0, maxPointOnHeap * config.bytesPerDoc())
                    pointsInBuffer = maxPointOnHeap - 1
                    countLeft -= maxPointOnHeap.toLong()
                } else {
                    `in`?.readBytes(onHeapBuffer, 0, countLeft.toInt() * config.bytesPerDoc())
                    pointsInBuffer = Math.toIntExact(countLeft - 1)
                    countLeft = 0
                }
                this.offset = 0
            } catch (eofe: EOFException) {
                require(countLeft == -1L)
                return false
            }
        } else {
            this.pointsInBuffer--
            this.offset += config.bytesPerDoc()
        }
        return true
    }

    override fun pointValue(): PointValue {
        pointValue.setOffset(offset)
        return pointValue
    }

    @Throws(IOException::class)
    override fun close() {
        try {
            if (countLeft == 0L && `in` is ChecksumIndexInput && checked == false) {
                // System.out.println("NOW CHECK: " + name);
                checked = true
                CodecUtil.checkFooter(`in`!! as ChecksumIndexInput)
            }
        } finally {
            `in`!!.close()
        }
    }

    /** Reusable implementation for a point value offline  */
    internal class OfflinePointValue(config: BKDConfig, value: ByteArray) : PointValue {
        val packedValue: BytesRef
        val packedValueDocID: BytesRef = BytesRef(value, 0, config.bytesPerDoc())
        val packedValueLength: Int = config.packedBytesLength()

        init {
            this.packedValue = BytesRef(value, 0, packedValueLength)
        }

        /** Sets a new value by changing the offset.  */
        fun setOffset(offset: Int) {
            packedValue.offset = offset
            packedValueDocID.offset = offset
        }

        override fun packedValue(): BytesRef {
            return packedValue
        }

        override fun docID(): Int {
            val position: Int = packedValueDocID.offset + packedValueLength
            return BitUtil.VH_BE_INT.get(packedValueDocID.bytes, position)
        }

        override fun packedValueDocIDBytes(): BytesRef {
            return packedValueDocID
        }
    }
}
