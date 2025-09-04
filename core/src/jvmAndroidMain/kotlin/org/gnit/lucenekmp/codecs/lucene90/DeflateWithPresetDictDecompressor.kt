package org.gnit.lucenekmp.codecs.lucene90

import okio.IOException
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.codecs.compressing.Decompressor
import java.util.zip.DataFormatException
import java.util.zip.Inflater

actual class DeflateWithPresetDictDecompressor actual constructor() : Decompressor() {
    actual var compressed: ByteArray = ByteArray(0)

    @Throws(IOException::class)
    actual fun doDecompress(`in`: DataInput, bytes: BytesRef) {
        val decompressor = Inflater(true)
        try {
            val compressedLength: Int = `in`.readVInt()
            if (compressedLength == 0) {
                return
            }
            compressed = ArrayUtil.growNoCopy(compressed, compressedLength)
            `in`.readBytes(compressed, 0, compressedLength)
            decompressor.setInput(compressed, 0, compressedLength)
            try {
                bytes.length +=
                    decompressor.inflate(bytes.bytes, bytes.length, bytes.bytes.size - bytes.length)
            } catch (e: DataFormatException) {
                throw IOException(e)
            }
            if (!decompressor.finished()) {
                throw CorruptIndexException(
                    ("Invalid decoder state: needsInput=" +
                            decompressor.needsInput() +
                            ", needsDict=" +
                            decompressor.needsDictionary()),
                    `in`
                )
            }
        } finally {
            decompressor.end()
        }
    }

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

        val decompressor = Inflater(true)
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
                decompressor.reset()
                decompressor.setDictionary(bytes.bytes, 0, dictLength)
                doDecompress(`in`, bytes)
                offsetInBlock += blockLength
            }

            bytes.offset = offsetInBytesRef
            bytes.length = length
            require(bytes.isValid())
        } finally {
            decompressor.end()
        }
    }

    actual override fun clone(): Decompressor {
        return DeflateWithPresetDictDecompressor()
    }
}