package org.gnit.lucenekmp.jdkport

import kotlin.math.min

class US_ASCII : Charset("US-ASCII", StandardCharsets.aliases_US_ASCII()) {
    override fun contains(cs: Charset): Boolean {
        return cs === this
    }

    override fun newDecoder(): CharsetDecoder {
        return Decoder(this)
    }

    override fun newEncoder(): CharsetEncoder {
        return Encoder(this)
    }

    class Decoder constructor(cs: Charset) : CharsetDecoder(cs, 1.0f, 1.0f) {
        fun decodeArrayLoop(src: ByteBuffer, dst: CharBuffer): CoderResult {
            val sa = src.array()
            val soff = src.arrayOffset()
            var sp = soff + src.position
            val sl = soff + src.limit

            val da = dst.array()
            val doff = dst.arrayOffset()
            var dp = doff + dst.position()
            val dl = doff + dst.limit

            val len = min(sl - sp, dl - dp)
            val asciiLen = JavaLangAccess.decodeASCII(sa, sp, da, dp, len)
            sp += asciiLen
            dp += asciiLen
            src.position(sp - soff)
            dst.position = dp - doff
            if (asciiLen < len) {
                return CoderResult.unmappableForLength(1)
            }
            if (sl - sp > dl - dp) {
                return CoderResult.OVERFLOW
            }
            return CoderResult.UNDERFLOW
        }

        fun decodeBufferLoop(src: ByteBuffer, dst: CharBuffer): CoderResult {
            var mark = src.position
            try {
                while (src.hasRemaining()) {
                    val b = src.get()
                    if (b < 0) {
                        return CoderResult.unmappableForLength(1)
                    }
                    if (!dst.hasRemaining()) return CoderResult.OVERFLOW
                    dst.put(b.toInt().toChar())
                    mark++
                }
                return CoderResult.UNDERFLOW
            } finally {
                src.position(mark)
            }
        }

        override fun decodeLoop(src: ByteBuffer, dst: CharBuffer): CoderResult {
            return if (src.hasArray() && dst.hasArray()) decodeArrayLoop(src, dst) else decodeBufferLoop(src, dst)
        }
    }

    class Encoder constructor(cs: Charset) : CharsetEncoder(cs, 1.0f, 1.0f) {
        private val sgp = Surrogate.Parser()

        override fun canEncode(c: Char): Boolean {
            return c.code <= 0x7F
        }

        fun encodeArrayLoop(src: CharBuffer, dst: ByteBuffer): CoderResult {
            val sa = src.array()
            val soff = src.arrayOffset()
            var sp = soff + src.position()
            val sl = soff + src.limit
            val da = dst.array()
            val doff = dst.arrayOffset()
            var dp = doff + dst.position
            val dl = doff + dst.limit

            val dlen = dl - dp
            val slen = sl - sp
            val len = if (dlen < slen) dlen else slen
            val asciiLen = JavaLangAccess.encodeASCII(sa, sp, da, dp, len)
            sp += asciiLen
            dp += asciiLen
            if (asciiLen < len) {
                if (sgp.parse(sa[sp], sa, sp, sl) < 0) return sgp.error()!!
                return sgp.unmappableResult()
            }
            src.position = sp - soff
            dst.position(dp - doff)
            if (len < slen) return CoderResult.OVERFLOW
            return CoderResult.UNDERFLOW
        }

        fun encodeBufferLoop(src: CharBuffer, dst: ByteBuffer): CoderResult {
            var mark = src.position()
            try {
                while (src.hasRemaining()) {
                    val c = src.get()
                    if (c.code > 0x7F) {
                        if (sgp.parse(c, src) < 0) return sgp.error()!!
                        return sgp.unmappableResult()
                    }
                    if (!dst.hasRemaining()) return CoderResult.OVERFLOW
                    dst.put(c.code.toByte())
                    mark++
                }
                return CoderResult.UNDERFLOW
            } finally {
                src.position = mark
            }
        }

        override fun encodeLoop(src: CharBuffer, dst: ByteBuffer): CoderResult {
            return if (src.hasArray() && dst.hasArray()) encodeArrayLoop(src, dst) else encodeBufferLoop(src, dst)
        }
    }
}
