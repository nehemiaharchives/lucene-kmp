package org.gnit.lucenekmp.codecs.lucene90

import kotlinx.cinterop.*
import okio.IOException
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.codecs.compressing.Decompressor
import platform.zlib.*

actual class DeflateWithPresetDictDecompressor actual constructor() : Decompressor() {
    actual var compressed: ByteArray = ByteArray(0)

    @OptIn(ExperimentalForeignApi::class)
    @Throws(IOException::class)
    actual fun doDecompress(`in`: DataInput, bytes: BytesRef) {
        memScoped {
            val compressedLength = `in`.readVInt()
            if (compressedLength == 0) return

            compressed = ArrayUtil.growNoCopy(compressed, compressedLength)
            `in`.readBytes(compressed, 0, compressedLength)

            val stream = alloc<z_stream>()
            stream.avail_in = compressedLength.convert()
            stream.next_in = compressed.refTo(0).getPointer(memScope).reinterpret<UByteVar>()

            val init = inflateInit2(stream.ptr, -15)
            if (init != Z_OK) throw IOException("Failed to initialize zlib inflater: $init")

            try {
                val bufSize = 8192
                val tmp = ByteArray(bufSize)
                while (true) {
                    stream.avail_out = bufSize.convert()
                    stream.next_out = tmp.refTo(0).getPointer(memScope).reinterpret<UByteVar>()
                    val res = inflate(stream.ptr, Z_NO_FLUSH)
                    val produced = bufSize - stream.avail_out.toInt()
                    if (produced > 0) {
                        bytes.bytes = ArrayUtil.grow(bytes.bytes, bytes.length + produced)
                        tmp.copyInto(bytes.bytes, bytes.length, 0, produced)
                        bytes.length += produced
                    }
                    when (res) {
                        Z_OK -> continue
                        Z_STREAM_END -> break
                        Z_NEED_DICT -> throw IOException("Unexpected dictionary required for dictionary block")
                        Z_DATA_ERROR -> throw IOException("Malformed data stream")
                        else -> throw IOException("Failed to inflate data: $res")
                    }
                }
            } finally {
                inflateEnd(stream.ptr)
            }
        }
    }

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

        val dictLength = `in`.readVInt()
        val blockLength = `in`.readVInt()
        bytes.bytes = ArrayUtil.growNoCopy(bytes.bytes, dictLength)
        bytes.length = 0
        bytes.offset = 0

        memScoped {
            // Read dictionary (no preset dict)
            doDecompress(`in`, bytes)
            if (dictLength != bytes.length) throw CorruptIndexException("Unexpected dict length", `in`)

            var offsetInBlock = dictLength
            var offsetInBytesRef = offset

            // Skip blocks before offset
            while (offsetInBlock + blockLength < offset) {
                val clen = `in`.readVInt()
                `in`.skipBytes(clen.toLong())
                offsetInBlock += blockLength
                offsetInBytesRef -= blockLength
            }

            // Decompress needed blocks with preset dictionary
            val stream = alloc<z_stream>()
            val init = inflateInit2(stream.ptr, -15)
            if (init != Z_OK) throw IOException("Failed to initialize zlib inflater: $init")

            try {
                while (offsetInBlock < offset + length) {
                    val clen = `in`.readVInt()
                    if (clen == 0) {
                        offsetInBlock += blockLength
                        continue
                    }

                    compressed = ArrayUtil.growNoCopy(compressed, clen)
                    `in`.readBytes(compressed, 0, clen)

                    inflateReset(stream.ptr)
                    if (dictLength > 0) {
                        val setRes = inflateSetDictionary(
                            stream.ptr,
                            bytes.bytes.refTo(0).getPointer(memScope).reinterpret<UByteVar>(),
                            dictLength.convert()
                        )
                        if (setRes != Z_OK) throw IOException("Failed to set dictionary for zlib inflater: $setRes")
                    }

                    stream.avail_in = clen.convert()
                    stream.next_in = compressed.refTo(0).getPointer(memScope).reinterpret<UByteVar>()

                    while (true) {
                        if (bytes.bytes.size - bytes.length == 0) {
                            bytes.bytes = ArrayUtil.grow(bytes.bytes)
                        }
                        val outCap = bytes.bytes.size - bytes.length
                        stream.avail_out = outCap.convert()
                        stream.next_out = bytes.bytes.refTo(bytes.length).getPointer(memScope).reinterpret<UByteVar>()

                        val res = inflate(stream.ptr, Z_NO_FLUSH)
                        val produced = outCap - stream.avail_out.toInt()
                        if (produced > 0) bytes.length += produced

                        when (res) {
                            Z_OK -> continue
                            Z_STREAM_END -> break
                            Z_DATA_ERROR -> throw IOException("Malformed data stream")
                            else -> throw IOException("Failed to inflate data: $res")
                        }
                    }

                    offsetInBlock += blockLength
                }

                bytes.offset = offsetInBytesRef
                bytes.length = length
                require(bytes.isValid())
            } finally {
                inflateEnd(stream.ptr)
            }
        }
    }

    actual override fun clone(): Decompressor = DeflateWithPresetDictDecompressor()
}

