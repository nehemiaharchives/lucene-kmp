package org.gnit.lucenekmp.codecs.compressing

import okio.IOException
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.store.ByteBuffersDataInput
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.compress.LZ4
import org.gnit.lucenekmp.util.compress.LZ4.FastCompressionHashTable
import org.gnit.lucenekmp.util.compress.LZ4.HighCompressionHashTable

/**
 * A compression mode. Tells how much effort should be spent on compression and decompression of
 * stored fields.
 *
 * @lucene.experimental
 */
abstract class CompressionMode
/** Sole constructor.  */
protected constructor() {
    /** Create a new [Compressor] instance.  */
    abstract fun newCompressor(): Compressor

    /** Create a new [Decompressor] instance.  */
    abstract fun newDecompressor(): Decompressor

    private class LZ4FastCompressor : Compressor() {
        private val ht: FastCompressionHashTable = FastCompressionHashTable()

        @Throws(IOException::class)
        override fun compress(buffersInput: ByteBuffersDataInput, out: DataOutput) {
            val len = buffersInput.length().toInt()
            val bytes = ByteArray(len)
            buffersInput.readBytes(bytes, 0, len)
            LZ4.compress(bytes, 0, len, out, ht)
        }

        @Throws(IOException::class)
        override fun close() {
            // no-op
        }
    }

    private class LZ4HighCompressor : Compressor() {
        private val ht: HighCompressionHashTable = HighCompressionHashTable()

        @Throws(IOException::class)
        override fun compress(buffersInput: ByteBuffersDataInput, out: DataOutput) {
            val len = buffersInput.length().toInt()
            val bytes = ByteArray(len)
            buffersInput.readBytes(bytes, 0, len)
            LZ4.compress(bytes, 0, len, out, ht)
        }

        @Throws(IOException::class)
        override fun close() {
            // no-op
        }
    }

    companion object {
        /**
         * A compression mode that trades compression ratio for speed. Although the compression ratio
         * might remain high, compression and decompression are very fast. Use this mode with indices that
         * have a high update rate but should be able to load documents from disk quickly.
         */
        val FAST: CompressionMode = object : CompressionMode() {
            override fun newCompressor(): Compressor {
                return LZ4FastCompressor()
            }

            override fun newDecompressor(): Decompressor {
                return LZ4_DECOMPRESSOR
            }

            override fun toString(): String {
                return "FAST"
            }
        }

        /**
         * A compression mode that trades speed for compression ratio. Although compression and
         * decompression might be slow, this compression mode should provide a good compression ratio.
         * This mode might be interesting if/when your index size is much bigger than your OS cache.
         */
        val HIGH_COMPRESSION: CompressionMode = object : CompressionMode() {
            override fun newCompressor(): Compressor {
                // notes:
                // 3 is the highest level that doesn't have lazy match evaluation
                // 6 is the default, higher than that is just a waste of cpu
                return DeflateCompressor(6)
            }

            override fun newDecompressor(): Decompressor {
                return DeflateDecompressor()
            }

            override fun toString(): String {
                return "HIGH_COMPRESSION"
            }
        }

        /**
         * This compression mode is similar to [.FAST] but it spends more time compressing in order
         * to improve the compression ratio. This compression mode is best used with indices that have a
         * low update rate but should be able to load documents from disk quickly.
         */
        val FAST_DECOMPRESSION: CompressionMode = object : CompressionMode() {
            override fun newCompressor(): Compressor {
                return LZ4HighCompressor()
            }

            override fun newDecompressor(): Decompressor {
                return LZ4_DECOMPRESSOR
            }

            override fun toString(): String {
                return "FAST_DECOMPRESSION"
            }
        }

        private val LZ4_DECOMPRESSOR: Decompressor = object : Decompressor() {
            @Throws(IOException::class)
            override fun decompress(
                `in`: DataInput, originalLength: Int, offset: Int, length: Int, bytes: BytesRef
            ) {
                require(offset + length <= originalLength)
                // add 7 padding bytes, this is not necessary but can help decompression run faster
                if (bytes.bytes.size < originalLength + 7) {
                    bytes.bytes = ByteArray(ArrayUtil.oversize(originalLength + 7, 1))
                }
                val decompressedLength: Int = LZ4.decompress(`in`, offset + length, bytes.bytes, 0)
                if (decompressedLength > originalLength) {
                    throw CorruptIndexException(
                        "Corrupted: lengths mismatch: $decompressedLength > $originalLength", `in`
                    )
                }
                bytes.offset = offset
                bytes.length = length
            }

            override fun clone(): Decompressor {
                return this
            }
        }
    }
}

expect class DeflateDecompressor() : Decompressor {

    var compressed: ByteArray/* = ByteArray(0)*/

    @Throws(IOException::class)
    override fun decompress(
        `in`: DataInput,
        originalLength: Int,
        offset: Int,
        length: Int,
        bytes: BytesRef
    ) /*{
            require(offset + length <= originalLength)
            if (length == 0) {
                bytes.length = 0
                return
            }
            val compressedLength: Int = `in`.readVInt()
            // pad with extra "dummy byte": see javadocs for using Inflater(true)
            // we do it for compliance, but it's unnecessary for years in zlib.
            val paddedLength = compressedLength + 1
            compressed = ArrayUtil.growNoCopy(compressed, paddedLength)
            `in`.readBytes(compressed, 0, compressedLength)
            compressed[compressedLength] = 0 // explicitly set dummy byte to 0

            val decompressor: java.util.zip.Inflater = java.util.zip.Inflater(true)
            try {
                // extra "dummy byte"
                decompressor.setInput(compressed, 0, paddedLength)

                bytes.length = 0
                bytes.offset = bytes.length
                bytes.bytes = ArrayUtil.growNoCopy(bytes.bytes, originalLength)
                try {
                    bytes.length = decompressor.inflate(bytes.bytes, bytes.length, originalLength)
                } catch (e: java.util.zip.DataFormatException) {
                    throw IOException(e)
                }
                if (!decompressor.finished()) {
                    throw CorruptIndexException(
                        ("Invalid decoder state: needsInput="
                                + decompressor.needsInput()
                                + ", needsDict="
                                + decompressor.needsDictionary()),
                        `in`
                    )
                }
            } finally {
                decompressor.end()
            }
            if (bytes.length != originalLength) {
                throw CorruptIndexException(
                    "Lengths mismatch: " + bytes.length + " != " + originalLength, `in`
                )
            }
            bytes.offset = offset
            bytes.length = length
        }

        */

    override fun clone(): Decompressor /*{
        return DeflateDecompressor()
    }*/
}

expect class DeflateCompressor : Compressor {
    constructor(level: Int)

    val level: Int
    //val compressor: java.util.zip.Deflater
    var compressed: ByteArray
    var closed: Boolean/* = false*/

    /*init {
        //compressor = java.util.zip.Deflater(level, true)
        compressed = ByteArray(64)
    }*/

    @Throws(IOException::class)
     override fun compress(buffersInput: ByteBuffersDataInput, out: DataOutput) /*{
        val len = buffersInput.length() as Int

        val bytes = ByteArray(len)
        buffersInput.readBytes(bytes, 0, len)
        compressor.reset()
        compressor.setInput(bytes, 0, len)
        compressor.finish()

        if (compressor.needsInput()) {
            // no output
            require(len == 0) { len }
            out.writeVInt(0)
            return
        }

        var totalCount = 0
        while (true) {
            val count: Int =
                compressor.deflate(compressed, totalCount, compressed.size - totalCount)
            totalCount += count
            require(totalCount <= compressed.size)
            if (compressor.finished()) {
                break
            } else {
                compressed = ArrayUtil.grow(compressed)
            }
        }

        out.writeVInt(totalCount)
        out.writeBytes(compressed, totalCount)
    }*/

    @Throws(IOException::class)
    override fun close() /*{
        if (closed == false) {
            compressor.end()
            closed = true
        }
    }*/
}