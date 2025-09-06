package org.gnit.lucenekmp.codecs.lucene90

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.reinterpret
import okio.IOException
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
import platform.zlib.deflateReset
import platform.zlib.deflateSetDictionary
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
    private fun deflateChunk(bytes: ByteArray, off: Int, len: Int, out: DataOutput, stream: z_stream) {
        if (len == 0) {
            out.writeVInt(0)
            return
        }

        memScoped {
            stream.avail_in = len.convert()
            stream.next_in = bytes.refTo(off).getPointer(this).reinterpret()

            var totalCount = 0
            while (true) {
                stream.avail_out = (compressed.size - totalCount).convert()
                stream.next_out = compressed.refTo(totalCount).getPointer(this).reinterpret()

                deflate(stream.ptr, Z_FINISH)

                val produced = compressed.size - totalCount - stream.avail_out.toInt()
                totalCount += produced

                if (stream.avail_out.toInt() > 0) {
                    // Finished this chunk
                    break
                } else {
                    // Need more room
                    compressed = ArrayUtil.grow(compressed)
                }
            }

            out.writeVInt(totalCount)
            out.writeBytes(compressed, totalCount)
        }
    }

    // Standalone compression (no preset dictionary)
    @OptIn(ExperimentalForeignApi::class)
    @Throws(IOException::class)
    actual fun doCompress(bytes: ByteArray, off: Int, len: Int, out: DataOutput) {
        if (len == 0) {
            out.writeVInt(0)
            return
        }
        memScoped {
            val stream = alloc<z_stream>()
            // Raw deflate (JVM Deflater with nowrap=true)
            val windowBits = -15
            val memLevel = 8

            deflateInit2(stream.ptr, level, Z_DEFLATED, windowBits, memLevel, Z_DEFAULT_STRATEGY)
            try {
                deflateChunk(bytes, off, len, out, stream)
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

        // Read the dictionary prefix
        buffersInput.readBytes(buffer, 0, dictLength)

        memScoped {
            val stream = alloc<z_stream>()
            val windowBits = -15 // raw deflate
            val memLevel = 8

            deflateInit2(stream.ptr, level, Z_DEFLATED, windowBits, memLevel, Z_DEFAULT_STRATEGY)
            try {
                // 1) Compress dictionary (no preset dict)
                deflateChunk(buffer, 0, dictLength, out, stream)

                // 2) Compress sub-blocks using the preset dictionary
                var start = dictLength
                while (start < len) {
                    deflateReset(stream.ptr)
                    if (dictLength > 0) {
                        deflateSetDictionary(
                            stream.ptr,
                            buffer.refTo(0).getPointer(this).reinterpret(),
                            dictLength.convert()
                        )
                    }

                    val blockSize = min(blockLength, len - start)
                    buffersInput.readBytes(buffer, dictLength, blockSize)
                    deflateChunk(buffer, dictLength, blockSize, out, stream)
                    start += blockSize
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