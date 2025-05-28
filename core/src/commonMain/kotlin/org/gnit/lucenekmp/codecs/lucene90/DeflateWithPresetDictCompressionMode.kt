package org.gnit.lucenekmp.codecs.lucene90


import org.gnit.lucenekmp.codecs.compressing.CompressionMode
import org.gnit.lucenekmp.codecs.compressing.Compressor
import org.gnit.lucenekmp.codecs.compressing.Decompressor
//import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.store.ByteBuffersDataInput
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
//import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import okio.IOException
//import org.gnit.lucenekmp.codecs.lucene90.DeflateWithPresetDictCompressionMode.Companion.DICT_SIZE_FACTOR
//import org.gnit.lucenekmp.codecs.lucene90.DeflateWithPresetDictCompressionMode.Companion.NUM_SUB_BLOCKS
//import org.gnit.lucenekmp.jdkport.DataFormatException
//import kotlin.math.min

/**
 * A compression mode that trades speed for compression ratio. Although compression and
 * decompression might be slow, this compression mode should provide a good compression ratio. This
 * mode might be interesting if/when your index size is much bigger than your OS cache.
 *
 * @lucene.internal
 */
class DeflateWithPresetDictCompressionMode
/** Sole constructor.  */
    : CompressionMode() {
    override fun newCompressor(): Compressor {
        // notes:
        // 3 is the highest level that doesn't have lazy match evaluation
        // 6 is the default, higher than that is just a waste of cpu
        return DeflateWithPresetDictCompressor(6)
    }

    override fun newDecompressor(): Decompressor {
        return DeflateWithPresetDictDecompressor()
    }

    override fun toString(): String {
        return "BEST_COMPRESSION"
    }

    companion object {
        // Shoot for 10 sub blocks
        const val NUM_SUB_BLOCKS = 10

        // And a dictionary whose size is about 6x smaller than sub blocks
        const val DICT_SIZE_FACTOR = 6
    }
}

expect class DeflateWithPresetDictDecompressor : Decompressor {
    constructor()

    var compressed: ByteArray

    /*init {
        compressed = ByteArray(0)
    }*/

    @Throws(IOException::class)
    fun doDecompress(`in`: DataInput, bytes: BytesRef) /*{

        val decompressor: java.util.zip.Inflater = java.util.zip.Inflater(true)

        val compressedLength: Int = `in`.readVInt()
        if (compressedLength == 0) {
            return
        }
        // pad with extra "dummy byte": see javadocs for using Inflater(true)
        // we do it for compliance, but it's unnecessary for years in zlib.
        val paddedLength = compressedLength + 1
        compressed = ArrayUtil.growNoCopy(compressed, paddedLength)
        `in`.readBytes(compressed, 0, compressedLength)
        compressed[compressedLength] = 0 // explicitly set dummy byte to 0

        // extra "dummy byte"
        decompressor.setInput(compressed, 0, paddedLength)
        try {
            bytes.length +=
                decompressor.inflate(bytes.bytes, bytes.length, bytes.bytes.length - bytes.length)
        } catch (e: DataFormatException) {
            throw IOException(e)
        }
        if (decompressor.finished() == false) {
            throw CorruptIndexException(
                ("Invalid decoder state: needsInput="
                        + decompressor.needsInput()
                        + ", needsDict="
                        + decompressor.needsDictionary()),
                `in`
            )
        }
    }*/

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
        val dictLength: Int = `in`.readVInt()
        val blockLength: Int = `in`.readVInt()
        bytes.bytes = ArrayUtil.growNoCopy(bytes.bytes, dictLength)
        bytes.length = 0
        bytes.offset = bytes.length

        val decompressor: java.util.zip.Inflater = java.util.zip.Inflater(true)
        try {
            // Read the dictionary
            doDecompress(`in`, decompressor, bytes)
            if (dictLength != bytes.length) {
                throw CorruptIndexException("Unexpected dict length", `in`)
            }

            var offsetInBlock = dictLength
            var offsetInBytesRef = offset

            // Skip unneeded blocks
            while (offsetInBlock + blockLength < offset) {
                val compressedLength: Int = `in`.readVInt()
                `in`.skipBytes(compressedLength)
                offsetInBlock += blockLength
                offsetInBytesRef -= blockLength
            }

            // Read blocks that intersect with the interval we need
            while (offsetInBlock < offset + length) {
                bytes.bytes = ArrayUtil.grow(bytes.bytes, bytes.length + blockLength)
                decompressor.reset()
                decompressor.setDictionary(bytes.bytes, 0, dictLength)
                doDecompress(`in`, decompressor, bytes)
                offsetInBlock += blockLength
            }

            bytes.offset = offsetInBytesRef
            bytes.length = length
            require(bytes.isValid())
        } finally {
            decompressor.end()
        }
    }*/

    override fun clone(): Decompressor /*{
        return DeflateWithPresetDictDecompressor()
    }*/
}

expect class DeflateWithPresetDictCompressor : Compressor {
    constructor(level: Int)
    /*val compressor: java.util.zip.Deflater*/
    var compressed: ByteArray
    var closed: Boolean /*= false*/
    var buffer: ByteArray

    /*init {
        compressor = java.util.zip.Deflater(level, true)
        compressed = ByteArray(64)
        buffer = BytesRef.EMPTY_BYTES
    }*/

    @Throws(IOException::class)
    fun doCompress(bytes: ByteArray, off: Int, len: Int, out: DataOutput) /*{
        if (len == 0) {
            out.writeVInt(0)
            return
        }
        compressor.setInput(bytes, off, len)
        compressor.finish()
        check(!compressor.needsInput())

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
    override fun compress(buffersInput: ByteBuffersDataInput, out: DataOutput) /*{
        val len = (buffersInput.length() - buffersInput.position()) as Int
        val dictLength = len / (NUM_SUB_BLOCKS * DICT_SIZE_FACTOR)
        val blockLength = (len - dictLength + NUM_SUB_BLOCKS - 1) / NUM_SUB_BLOCKS
        out.writeVInt(dictLength)
        out.writeVInt(blockLength)

        // Compress the dictionary first
        compressor.reset()
        buffer = ArrayUtil.growNoCopy(buffer, dictLength + blockLength)
        buffersInput.readBytes(buffer, 0, dictLength)
        doCompress(buffer, 0, dictLength, out)

        // And then sub blocks
        var start = dictLength
        while (start < len) {
            compressor.reset()
            compressor.setDictionary(buffer, 0, dictLength)
            val l = min(blockLength, len - start)
            buffersInput.readBytes(buffer, dictLength, l)
            doCompress(buffer, dictLength, l, out)
            start += blockLength
        }
    }*/

    @Throws(IOException::class)
    override fun close() /*{
        if (closed == false) {
            compressor.end()
            closed = true
        }
    }*/
}