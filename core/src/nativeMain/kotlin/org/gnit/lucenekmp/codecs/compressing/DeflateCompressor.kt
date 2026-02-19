package org.gnit.lucenekmp.codecs.compressing

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.reinterpret
import okio.IOException
import org.gnit.lucenekmp.store.ByteBuffersDataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.ArrayUtil
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.deflate
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2
import platform.zlib.z_stream

actual class DeflateCompressor actual constructor(actual val level: Int) : Compressor() {
    actual var compressed: ByteArray = ByteArray(64)
    actual var closed: Boolean = false

    @OptIn(ExperimentalForeignApi::class)
    actual override fun compress(buffersInput: ByteBuffersDataInput, out: DataOutput) {
        val lenLong = buffersInput.length()
        require(lenLong in 0L..Int.MAX_VALUE.toLong()) {
            "input length out of Int range: $lenLong"
        }
        val len = lenLong.toInt()

        val bytes = ByteArray(len)
        buffersInput.readBytes(bytes, 0, len)

        if (len == 0) {
            // No output
            out.writeVInt(0)
            return
        }

        memScoped {
            val stream = alloc<z_stream>()
            stream.avail_in = len.convert()
            stream.next_in = bytes.refTo(0).getPointer(memScope).reinterpret()

            // Raw DEFLATE stream to match Deflater(level, true) on JVM.
            val windowBits = -15
            val memLevel = 8

            deflateInit2(stream.ptr, level, Z_DEFLATED, windowBits, memLevel, Z_DEFAULT_STRATEGY)

            try {
                var totalCount = 0

                while (true) {
                    stream.avail_out = (compressed.size - totalCount).convert()
                    stream.next_out = compressed.refTo(totalCount).getPointer(memScope).reinterpret()

                    val result = deflate(stream.ptr, Z_FINISH)
                    val compressedBytes = compressed.size - totalCount - stream.avail_out.toInt()
                    totalCount += compressedBytes

                    if (stream.avail_out.toInt() > 0) {
                        // Compression is finished
                        break
                    } else {
                        // Need more space
                        compressed = ArrayUtil.grow(compressed)
                    }
                }

                out.writeVInt(totalCount)
                out.writeBytes(compressed, totalCount)
            } finally {
                deflateEnd(stream.ptr)
            }
        }
    }

    @Throws(IOException::class)
    actual override fun close() {
        if (!closed) {
            closed = true
        }
    }
}
