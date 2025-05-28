package org.gnit.lucenekmp.codecs.compressing


import okio.IOException
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import java.util.zip.DataFormatException
import java.util.zip.Inflater

actual class DeflateDecompressor : Decompressor() {
    actual var compressed: ByteArray = ByteArray(0)

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
        val compressedLength: Int = `in`.readVInt()
        // pad with extra "dummy byte": see javadocs for using Inflater(true)
        val paddedLength = compressedLength + 1
        compressed = ArrayUtil.growNoCopy(compressed, paddedLength)
        `in`.readBytes(compressed, 0, compressedLength)
        compressed[compressedLength] = 0 // explicitly set dummy byte to 0

        val decompressor = Inflater(true)
        try {
            // extra "dummy byte"
            decompressor.setInput(compressed, 0, paddedLength)

            bytes.length = 0
            bytes.offset = bytes.length
            bytes.bytes = ArrayUtil.growNoCopy(bytes.bytes, originalLength)
            try {
                bytes.length = decompressor.inflate(bytes.bytes, bytes.length, originalLength)
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
        if (bytes.length != originalLength) {
            throw CorruptIndexException(
                "Lengths mismatch: ${bytes.length} != $originalLength", `in`
            )
        }
        bytes.offset = offset
        bytes.length = length
    }

    actual override fun clone(): Decompressor {
        return DeflateDecompressor()
    }
}
