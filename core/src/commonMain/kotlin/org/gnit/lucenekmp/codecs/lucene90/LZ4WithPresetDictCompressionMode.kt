package org.gnit.lucenekmp.codecs.lucene90

import org.gnit.lucenekmp.codecs.compressing.CompressionMode
import org.gnit.lucenekmp.codecs.compressing.Compressor
import org.gnit.lucenekmp.codecs.compressing.Decompressor
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.store.ByteBuffersDataInput
import org.gnit.lucenekmp.store.ByteBuffersDataOutput
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.compress.LZ4
import org.gnit.lucenekmp.util.compress.LZ4.FastCompressionHashTable
import okio.IOException
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.System
import kotlin.math.min

/**
 * A compression mode that compromises on the compression ratio to provide fast compression and
 * decompression.
 *
 * @lucene.internal
 */
class LZ4WithPresetDictCompressionMode
/** Sole constructor.  */
    : CompressionMode() {
    override fun newCompressor(): Compressor {
        return LZ4WithPresetDictCompressor()
    }

    override fun newDecompressor(): Decompressor {
        return LZ4WithPresetDictDecompressor()
    }

    override fun toString(): String {
        return "BEST_SPEED"
    }

    private class LZ4WithPresetDictDecompressor : Decompressor() {
        private var compressedLengths: IntArray
        private var buffer: ByteArray

        init {
            compressedLengths = IntArray(0)
            buffer = ByteArray(0)
        }

        @Throws(IOException::class)
        fun readCompressedLengths(
            `in`: DataInput, originalLength: Int, dictLength: Int, blockLength: Int
        ): Int {
            `in`.readVInt() // compressed length of the dictionary, unused
            var totalLength = dictLength
            var i = 0
            compressedLengths = ArrayUtil.growNoCopy(compressedLengths, originalLength / blockLength + 1)
            while (totalLength < originalLength) {
                compressedLengths[i++] = `in`.readVInt()
                totalLength += blockLength
            }
            return i
        }

        @Throws(IOException::class)
        override fun decompress(
            `in`: DataInput,
            originalLength: Int,
            offset: Int,
            length: Int,
            bytes: BytesRef
        ) {
            require(offset + length <= originalLength)

            if (length == 0) {
                bytes.length = 0
                return
            }

            val dictLength: Int = `in`.readVInt()
            val blockLength: Int = `in`.readVInt()

            val numBlocks = readCompressedLengths(`in`, originalLength, dictLength, blockLength)

            buffer = ArrayUtil.growNoCopy(buffer, dictLength + blockLength)
            bytes.length = 0
            // Read the dictionary
            if (LZ4.decompress(`in`, dictLength, buffer, 0) != dictLength) {
                throw CorruptIndexException("Illegal dict length", `in`)
            }

            var offsetInBlock = dictLength
            var offsetInBytesRef = offset
            if (offset >= dictLength) {
                offsetInBytesRef -= dictLength

                // Skip unneeded blocks
                var numBytesToSkip = 0
                var i = 0
                while (i < numBlocks && offsetInBlock + blockLength < offset) {
                    val compressedBlockLength = compressedLengths[i]
                    numBytesToSkip += compressedBlockLength
                    offsetInBlock += blockLength
                    offsetInBytesRef -= blockLength
                    ++i
                }
                `in`.skipBytes(numBytesToSkip.toLong())
            } else {
                // The dictionary contains some bytes we need, copy its content to the BytesRef
                bytes.bytes = ArrayUtil.growNoCopy(bytes.bytes, dictLength)
                System.arraycopy(buffer, 0, bytes.bytes, 0, dictLength)
                bytes.length = dictLength
            }

            // Read blocks that intersect with the interval we need
            if (offsetInBlock < offset + length) {
                bytes.bytes = ArrayUtil.grow(bytes.bytes, bytes.length + offset + length - offsetInBlock)
            }
            while (offsetInBlock < offset + length) {
                val bytesToDecompress = min(blockLength, offset + length - offsetInBlock)
                LZ4.decompress(`in`, bytesToDecompress, buffer, dictLength)
                System.arraycopy(buffer, dictLength, bytes.bytes, bytes.length, bytesToDecompress)
                bytes.length += bytesToDecompress
                offsetInBlock += blockLength
            }

            bytes.offset = offsetInBytesRef
            bytes.length = length
            require(bytes.isValid())
        }

        override fun clone(): Decompressor {
            return LZ4WithPresetDictDecompressor()
        }
    }

    private class LZ4WithPresetDictCompressor : Compressor() {
        val compressed: ByteBuffersDataOutput = ByteBuffersDataOutput.newResettableInstance()
        val hashTable: FastCompressionHashTable = FastCompressionHashTable()
        var buffer: ByteArray

        init {
            buffer = BytesRef.EMPTY_BYTES
        }

        @Throws(IOException::class)
        fun doCompress(bytes: ByteArray, dictLen: Int, len: Int, out: DataOutput) {
            val prevCompressedSize: Long = compressed.size()
            LZ4.compressWithDictionary(bytes, 0, dictLen, len, compressed, hashTable)
            // Write the number of compressed bytes
            out.writeVInt(Math.toIntExact(compressed.size() - prevCompressedSize))
        }

        @Throws(IOException::class)
        override fun compress(buffersInput: ByteBuffersDataInput, out: DataOutput) {
            val len = (buffersInput.length() - buffersInput.position()).toInt()
            val dictLength: Int = min(LZ4.MAX_DISTANCE, len / (NUM_SUB_BLOCKS * DICT_SIZE_FACTOR))
            val blockLength = (len - dictLength + NUM_SUB_BLOCKS - 1) / NUM_SUB_BLOCKS
            buffer = ArrayUtil.growNoCopy(buffer, dictLength + blockLength)
            out.writeVInt(dictLength)
            out.writeVInt(blockLength)

            compressed.reset()
            // Compress the dictionary first
            buffersInput.readBytes(buffer, 0, dictLength)
            doCompress(buffer, 0, dictLength, out)

            // And then sub blocks
            var start = dictLength
            while (start < len) {
                val l = min(blockLength, len - start)
                buffersInput.readBytes(buffer, dictLength, l)
                doCompress(buffer, dictLength, l, out)
                start += blockLength
            }

            // We only wrote lengths so far, now write compressed data
            compressed.copyTo(out)
        }

        @Throws(IOException::class)
        override fun close() {
            // no-op
        }
    }

    companion object {
        // Shoot for 10 sub blocks
        private const val NUM_SUB_BLOCKS = 10

        // And a dictionary whose size is about 2x smaller than sub blocks
        private const val DICT_SIZE_FACTOR = 2
    }
}
