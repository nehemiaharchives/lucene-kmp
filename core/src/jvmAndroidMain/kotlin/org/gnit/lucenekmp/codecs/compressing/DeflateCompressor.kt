package org.gnit.lucenekmp.codecs.compressing

import okio.IOException
import org.gnit.lucenekmp.store.ByteBuffersDataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.ArrayUtil
import java.util.zip.Deflater

actual class DeflateCompressor actual constructor(actual val level: Int) : Compressor() {
    private val compressor: Deflater = Deflater(level, true)
    actual var compressed: ByteArray = ByteArray(64)
    actual var closed: Boolean = false

    @Throws(IOException::class)
    actual override fun compress(buffersInput: ByteBuffersDataInput, out: DataOutput) {
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
    }

    @Throws(IOException::class)
    actual override fun close() {
        if (!closed) {
            compressor.end()
            closed = true
        }
    }
}