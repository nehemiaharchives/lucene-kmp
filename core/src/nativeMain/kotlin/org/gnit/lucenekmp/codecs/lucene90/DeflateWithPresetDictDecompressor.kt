package org.gnit.lucenekmp.codecs.lucene90

import kotlinx.cinterop.*
import kotlinx.io.IOException
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.codecs.compressing.Decompressor
import platform.zlib.*
import kotlin.math.min

actual class DeflateWithPresetDictDecompressor actual constructor() : Decompressor() {
    actual var compressed: ByteArray = ByteArray(0)

    @OptIn(ExperimentalForeignApi::class)
    @Throws(IOException::class)
    actual fun doDecompress(`in`: DataInput, bytes: BytesRef) {
        memScoped {
            val compressedLength: Int = `in`.readVInt()
            if (compressedLength == 0) {
                return
            }

            // pad with extra "dummy byte": see javadocs for using Inflater(true)
            val paddedLength = compressedLength + 1
            compressed = ArrayUtil.growNoCopy(compressed, paddedLength)
            `in`.readBytes(compressed, 0, compressedLength)
            compressed[compressedLength] = 0 // explicitly set dummy byte to 0

            val stream = alloc<z_stream>()
            stream.avail_in = paddedLength.convert()
            stream.next_in = compressed.refTo(0).getPointer(memScope).reinterpret()

            val initResult = inflateInit2(stream.ptr, 15 + 32)
            if (initResult != Z_OK) {
                throw IOException("Failed to initialize zlib inflater: $initResult")
            }

            try {
                val bufferSize = 8192 // You can adjust the buffer size as needed
                val buffer = ByteArray(bufferSize)
                var totalInflated = 0

                stream.avail_out = bufferSize.convert()
                stream.next_out = buffer.refTo(0).getPointer(memScope).reinterpret()

                var result = inflate(stream.ptr, Z_NO_FLUSH)

                while (result == Z_OK) {
                    val inflatedBytes = bufferSize - stream.avail_out.toInt()
                    if (inflatedBytes > 0) {
                        bytes.bytes = ArrayUtil.grow(bytes.bytes, bytes.length + inflatedBytes)
                        buffer.copyInto(bytes.bytes, bytes.length, 0, inflatedBytes)
                        bytes.length += inflatedBytes
                        totalInflated += inflatedBytes
                    }

                    stream.avail_out = bufferSize.convert()
                    stream.next_out = buffer.refTo(0).getPointer(memScope).reinterpret()
                    result = inflate(stream.ptr, Z_NO_FLUSH)
                }

                if (result != Z_STREAM_END) {
                    if (result == Z_DATA_ERROR) {
                        throw IOException("Malformed data stream")
                    } else {
                        throw IOException("Failed to inflate data: $result")
                    }
                }

                val inflatedBytes = bufferSize - stream.avail_out.toInt()
                if (inflatedBytes > 0) {
                    bytes.bytes = ArrayUtil.grow(bytes.bytes, bytes.length + inflatedBytes)
                    buffer.copyInto(bytes.bytes, bytes.length, 0, inflatedBytes)
                    bytes.length += inflatedBytes
                    totalInflated += inflatedBytes
                }

                if (!finished(stream.ptr)) {
                    throw CorruptIndexException(
                        ("Invalid decoder state: needsInput=" +
                                needsInput(stream.ptr) +
                                ", needsDict=" +
                                needsDictionary(stream.ptr)),
                        `in`
                    )
                }
            } finally {
                inflateEnd(stream.ptr)
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun needsInput(stream: CPointer<z_stream>): Boolean {
        return stream.pointed.avail_in == 0u
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun needsDictionary(stream: CPointer<z_stream>): Boolean {
        return stream.pointed.state != null && stream.pointed.data_type == 2
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun finished(stream: CPointer<z_stream>): Boolean {
        return stream.pointed.avail_in == 0u && stream.pointed.avail_out != 0u
    }

    @OptIn(ExperimentalForeignApi::class)
    @Throws(IOException::class)
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

        val dictLength: Int = `in`.readVInt()
        val blockLength: Int = `in`.readVInt()
        bytes.bytes = ArrayUtil.growNoCopy(bytes.bytes, dictLength)
        bytes.length = 0
        bytes.offset = bytes.length

        memScoped {
            val stream = alloc<z_stream>()

            try {
                // Read the dictionary
                doDecompress(`in`, bytes)
                if (dictLength != bytes.length) {
                    throw CorruptIndexException("Unexpected dict length", `in`)
                }

                var offsetInBlock = dictLength
                var offsetInBytesRef = offset

                // Skip unneeded blocks
                while (offsetInBlock + blockLength < offset) {
                    val compressedLength: Int = `in`.readVInt()
                    `in`.skipBytes(compressedLength.toLong())
                    offsetInBlock += blockLength
                    offsetInBytesRef -= blockLength
                }

                // Read blocks that intersect with the interval we need
                while (offsetInBlock < offset + length) {
                    bytes.bytes = ArrayUtil.grow(bytes.bytes, bytes.length + blockLength)

                    val initResult = inflateInit2(stream.ptr, 15 + 32)
                    if (initResult != Z_OK) {
                        throw IOException("Failed to initialize zlib inflater: $initResult")
                    }

                    stream.reset()
                    val setDictResult = inflateSetDictionary(
                        stream.ptr,
                        bytes.bytes.refTo(0).getPointer(memScope).reinterpret(),
                        dictLength.convert<UInt>()
                    )
                    if (setDictResult != Z_OK) {
                        throw IOException("Failed to set dictionary for zlib inflater: $setDictResult")
                    }

                    doDecompress(`in`, bytes)
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

    actual override fun clone(): Decompressor {
        return DeflateWithPresetDictDecompressor()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun z_stream.reset() {
    avail_in = 0u
    next_in = null
    avail_out = 0u
    next_out = null
    msg = null
    state = null
    data_type = 0
    adler = 0u
    total_in = 0u
    total_out = 0u
}