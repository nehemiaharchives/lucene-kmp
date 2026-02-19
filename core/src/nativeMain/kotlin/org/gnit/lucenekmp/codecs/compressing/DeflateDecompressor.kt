package org.gnit.lucenekmp.codecs.compressing

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.reinterpret
import okio.IOException
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import platform.zlib.Z_FINISH
import platform.zlib.Z_OK
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.z_stream

actual class DeflateDecompressor : Decompressor() {
    actual var compressed: ByteArray = ByteArray(0)

    @OptIn(ExperimentalForeignApi::class)
    actual override fun decompress(
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

        val compressedLength: Int = `in`.readVInt()
        val paddedLength = compressedLength + 1
        compressed = ArrayUtil.growNoCopy(compressed, paddedLength)
        `in`.readBytes(compressed, 0, compressedLength)
        compressed[compressedLength] = 0

        memScoped {
            val stream = alloc<z_stream>()
            stream.avail_in = paddedLength.convert()
            stream.next_in = compressed.refTo(0).getPointer(memScope).reinterpret()

            // Raw DEFLATE stream to match Inflater(true) on JVM.
            val initResult = inflateInit2(stream.ptr, -15)
            if (initResult != Z_OK) {
                throw IOException("Failed to initialize zlib inflater: $initResult")
            }

            try {
                bytes.bytes = ArrayUtil.growNoCopy(bytes.bytes, originalLength)
                stream.avail_out = originalLength.convert()
                stream.next_out = bytes.bytes.refTo(0).getPointer(memScope).reinterpret()

                val result = inflate(stream.ptr, Z_FINISH)
                bytes.length = (originalLength - stream.avail_out.toInt())

                if (bytes.length != originalLength) {
                    throw CorruptIndexException(
                        "Lengths mismatch: ${bytes.length} != $originalLength", `in`
                    )
                }

                bytes.offset = offset
                bytes.length = length
            } finally {
                inflateEnd(stream.ptr)
            }
        }
    }

    actual override fun clone(): Decompressor {
        return DeflateDecompressor()
    }
}
