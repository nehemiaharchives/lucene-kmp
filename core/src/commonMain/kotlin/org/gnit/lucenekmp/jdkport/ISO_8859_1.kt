package org.gnit.lucenekmp.jdkport

import kotlin.math.min

class ISO_8859_1 : Charset("ISO-8859-1", StandardCharsets.aliases_ISO_8859_1()) {
    override fun contains(cs: Charset): Boolean {
        // LATIN1 contains only itself.
        return cs === this
    }

    override fun newDecoder(): CharsetDecoder {
        return Decoder(this)
    }

    override fun newEncoder(): CharsetEncoder {
        return Encoder(this)
    }

    class Decoder constructor(cs: Charset) : CharsetDecoder(cs, 1.0f, 1.0f) {
        fun decodeArrayLoop(
            src: ByteBuffer,
            dst: CharBuffer
        ): CoderResult {
            val sa: ByteArray = src.array()
            val soff: Int = src.arrayOffset()
            var sp: Int = soff + src.position
            val sl: Int = soff + src.limit

            val da: CharArray = dst.array()
            val doff: Int = dst.arrayOffset()
            var dp: Int = doff + dst.position()
            val dl: Int = doff + dst.limit

            val decodeLen = min(sl - sp, dl - dp)
            JLA.inflateBytesToChars(sa, sp, da, dp, decodeLen)
            sp += decodeLen
            dp += decodeLen
            src.position(sp - soff)
            dst.position = dp - doff
            if (sl - sp > dl - dp) {
                return CoderResult.OVERFLOW
            }
            return CoderResult.UNDERFLOW
        }

        fun decodeBufferLoop(
            src: ByteBuffer,
            dst: CharBuffer
        ): CoderResult {
            var mark: Int = src.position
            try {
                while (src.hasRemaining()) {
                    val b: Byte = src.get()
                    if (!dst.hasRemaining()) return CoderResult.OVERFLOW
                    dst.put((b.toInt() and 0xff).toChar())
                    mark++
                }
                return CoderResult.UNDERFLOW
            } finally {
                src.position(mark)
            }
        }

        override fun decodeLoop(
            src: ByteBuffer,
            dst: CharBuffer
        ): CoderResult {
            if (src.hasArray() && dst.hasArray()) return decodeArrayLoop(src, dst)
            else return decodeBufferLoop(src, dst)
        }

        companion object {
            private val JLA: JavaLangAccess = JavaLangAccess
        }
    }

    class Encoder constructor(cs: Charset) : CharsetEncoder(cs, 1.0f, 1.0f) {
        override fun canEncode(c: Char): Boolean {
            return c <= '\u00FF'
        }

        override fun isLegalReplacement(repl: ByteArray): Boolean {
            return true // we accept any byte value
        }

        private val sgp: Surrogate.Parser = Surrogate.Parser()

        fun encodeArrayLoop(
            src: CharBuffer,
            dst: ByteBuffer
        ): CoderResult {
            val sa: CharArray = src.array()
            val soff: Int = src.arrayOffset()
            var sp: Int = soff + src.position()
            val sl: Int = soff + src.limit
            require(sp <= sl)
            sp = (if (sp <= sl) sp else sl)
            val da: ByteArray = dst.array()
            val doff: Int = dst.arrayOffset()
            var dp: Int = doff + dst.position
            val dl: Int = doff + dst.limit
            require(dp <= dl)
            dp = (if (dp <= dl) dp else dl)
            val dlen = dl - dp
            val slen = sl - sp
            val len = if (dlen < slen) dlen else slen
            try {
                val ret = encodeISOArray(sa, sp, da, dp, len)
                sp = sp + ret
                dp = dp + ret
                if (ret != len) {
                    if (sgp.parse(sa[sp], sa, sp, sl) < 0) return sgp.error()!!
                    return sgp.unmappableResult()
                }
                if (len < slen) return CoderResult.OVERFLOW
                return CoderResult.UNDERFLOW
            } finally {
                src.position = sp - soff
                dst.position(dp - doff)
            }
        }

        fun encodeBufferLoop(
            src: CharBuffer,
            dst: ByteBuffer
        ): CoderResult {
            var mark: Int = src.position()
            try {
                while (src.hasRemaining()) {
                    val c: Char = src.get()
                    if (c <= '\u00FF') {
                        if (!dst.hasRemaining()) return CoderResult.OVERFLOW
                        dst.put(c.code.toByte())
                        mark++
                        continue
                    }
                    if (sgp.parse(c, src) < 0) return sgp.error()
                    return sgp.unmappableResult()
                }
                return CoderResult.UNDERFLOW
            } finally {
                src.position = mark
            }
        }

        override fun encodeLoop(
            src: CharBuffer,
            dst: ByteBuffer
        ): CoderResult {
            if (src.hasArray() && dst.hasArray()) return encodeArrayLoop(src, dst)
            else return encodeBufferLoop(src, dst)
        }

        companion object {
            // Method possible replaced with a compiler intrinsic.
            private fun encodeISOArray(
                sa: CharArray, sp: Int,
                da: ByteArray, dp: Int, len: Int
            ): Int {
                if (len <= 0) {
                    return 0
                }
                encodeISOArrayCheck(sa, sp, da, dp, len)
                return implEncodeISOArray(sa, sp, da, dp, len)
            }

            private fun implEncodeISOArray(
                sa: CharArray, sp: Int,
                da: ByteArray, dp: Int, len: Int
            ): Int {
                var sp = sp
                var dp = dp
                var i = 0
                while (i < len) {
                    val c = sa[sp++]
                    if (c > '\u00FF') break
                    da[dp++] = c.code.toByte()
                    i++
                }
                return i
            }

            private fun encodeISOArrayCheck(
                sa: CharArray, sp: Int,
                da: ByteArray, dp: Int, len: Int
            ) {

                // implement if needed
                /*Preconditions.checkIndex<java.lang.ArrayIndexOutOfBoundsException>(
                    sp,
                    sa!!.size,
                    Preconditions.AIOOBE_FORMATTER
                )
                Preconditions.checkIndex<java.lang.ArrayIndexOutOfBoundsException>(
                    dp,
                    da!!.size,
                    Preconditions.AIOOBE_FORMATTER
                )

                Preconditions.checkIndex<java.lang.ArrayIndexOutOfBoundsException>(
                    sp + len - 1,
                    sa.size,
                    Preconditions.AIOOBE_FORMATTER
                )
                Preconditions.checkIndex<java.lang.ArrayIndexOutOfBoundsException>(
                    dp + len - 1,
                    da.size,
                    Preconditions.AIOOBE_FORMATTER
                )*/
            }
        }
    }
}
