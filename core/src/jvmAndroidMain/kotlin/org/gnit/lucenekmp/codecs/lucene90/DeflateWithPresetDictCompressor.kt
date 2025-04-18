package org.gnit.lucenekmp.codecs.lucene90

import kotlinx.io.IOException
import org.gnit.lucenekmp.codecs.lucene90.DeflateWithPresetDictCompressionMode.Companion.NUM_SUB_BLOCKS
import org.gnit.lucenekmp.codecs.lucene90.DeflateWithPresetDictCompressionMode.Companion.DICT_SIZE_FACTOR
import org.gnit.lucenekmp.codecs.compressing.Compressor
import org.gnit.lucenekmp.store.ByteBuffersDataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.Throws
import kotlin.math.min

actual class DeflateWithPresetDictCompressor : Compressor{

    val compressor: java.util.zip.Deflater
    actual var compressed: ByteArray
    actual var closed: Boolean = false
    actual var buffer: ByteArray

    actual constructor(level: Int) {
        compressor = java.util.zip.Deflater(level, true)
        compressed = ByteArray(64)
        buffer = BytesRef.EMPTY_BYTES
    }

    @Throws(IOException::class)
    actual fun doCompress(bytes: ByteArray, off: Int, len: Int, out: DataOutput) {
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
    }

    @Throws(IOException::class)
    actual override fun compress(buffersInput: ByteBuffersDataInput, out: DataOutput) {
        val len = (buffersInput.length() - buffersInput.position()).toInt()
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
    }

    @Throws(IOException::class)
    actual override fun close() {
        if (!closed) {
            compressor.end()
            closed = true
        }
    }
}