package org.gnit.lucenekmp.codecs.lucene90

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.reinterpret
import kotlinx.io.IOException
import org.gnit.lucenekmp.codecs.compressing.Compressor
import org.gnit.lucenekmp.store.ByteBuffersDataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.deflate
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2
import platform.zlib.deflateSetDictionary
import platform.zlib.deflateReset
import platform.zlib.z_stream
import kotlin.math.min
import org.gnit.lucenekmp.codecs.lucene90.DeflateWithPresetDictCompressionMode.Companion.DICT_SIZE_FACTOR
import org.gnit.lucenekmp.codecs.lucene90.DeflateWithPresetDictCompressionMode.Companion.NUM_SUB_BLOCKS

actual class DeflateWithPresetDictCompressor actual constructor(level: Int) : Compressor() {
    private val level: Int = level
    actual var compressed: ByteArray = ByteArray(64)
    actual var closed: Boolean = false
    actual var buffer: ByteArray = BytesRef.EMPTY_BYTES

    @OptIn(ExperimentalForeignApi::class)
    @Throws(IOException::class)
    actual fun doCompress(bytes: ByteArray, off: Int, len: Int, out: DataOutput) {
        if (len == 0) {
            out.writeVInt(0)
            return
        }

        memScoped {
            val stream = alloc<z_stream>()
            stream.avail_in = len.convert()
            stream.next_in = bytes.refTo(off).getPointer(memScope).reinterpret()

            // Initialize with windowBits=15 for raw deflate format (true parameter in JVM)
            val windowBits = 15
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

    @OptIn(ExperimentalForeignApi::class)
    @Throws(IOException::class)
    actual override fun compress(buffersInput: ByteBuffersDataInput, out: DataOutput) {
        val len = (buffersInput.length() - buffersInput.position()).toInt()
        val dictLength = len / (NUM_SUB_BLOCKS * DICT_SIZE_FACTOR)
        val blockLength = (len - dictLength + NUM_SUB_BLOCKS - 1) / NUM_SUB_BLOCKS
        out.writeVInt(dictLength)
        out.writeVInt(blockLength)

        buffer = ArrayUtil.growNoCopy(buffer, dictLength + blockLength)

        // Read the dictionary
        buffersInput.readBytes(buffer, 0, dictLength)

        // Compress the dictionary first
        memScoped {
            val stream = alloc<z_stream>()
            val windowBits = 15
            val memLevel = 8

            deflateInit2(stream.ptr, level, Z_DEFLATED, windowBits, memLevel, Z_DEFAULT_STRATEGY)

            try {
                // Compress dictionary
                doCompress(buffer, 0, dictLength, out)

                // And then sub blocks
                var start = dictLength
                while (start < len) {
                    deflateReset(stream.ptr)
                    deflateSetDictionary(
                        stream.ptr,
                        buffer.refTo(0).getPointer(memScope).reinterpret(),
                        dictLength.convert()
                    )

                    val blockSize = min(blockLength, len - start)
                    buffersInput.readBytes(buffer, dictLength, blockSize)
                    doCompress(buffer, dictLength, blockSize, out)
                    start += blockLength
                }
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