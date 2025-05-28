package org.gnit.lucenekmp.codecs.lucene90.blocktree

import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.util.compress.LowercaseAsciiCompression
import okio.IOException

/** Compression algorithm used for suffixes of a block of terms.  */
enum class CompressionAlgorithm(val code: Int) {
    NO_COMPRESSION(0x00) {
        @Throws(IOException::class)
        override fun read(`in`: DataInput, out: ByteArray, len: Int) {
            `in`.readBytes(out, 0, len)
        }
    },

    LOWERCASE_ASCII(0x01) {
        @Throws(IOException::class)
        override fun read(`in`: DataInput, out: ByteArray, len: Int) {
            LowercaseAsciiCompression.decompress(`in`, out, len)
        }
    },

    LZ4(0x02) {
        @Throws(IOException::class)
        override fun read(`in`: DataInput, out: ByteArray, len: Int) {
            org.gnit.lucenekmp.util.compress.LZ4.decompress(`in`, len, out, 0)
        }
    };

    @Throws(IOException::class)
    abstract fun read(`in`: DataInput, out: ByteArray, len: Int)

    companion object {
        private val BY_CODE = kotlin.arrayOfNulls<CompressionAlgorithm>(3)

        init {
            for (alg in entries) {
                BY_CODE[alg.code] = alg
            }
        }

        /** Look up a [CompressionAlgorithm] by its [CompressionAlgorithm.code].  */
        fun byCode(code: Int): CompressionAlgorithm {
            require(!(code < 0 || code >= BY_CODE.size)) { "Illegal code for a compression algorithm: $code" }
            return BY_CODE[code]!!
        }
    }
}
